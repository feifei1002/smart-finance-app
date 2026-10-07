package com.smart_finance_app.server.subscriptions

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.common.userIdOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.subscriptionRoutes() {
    configureStripe()

    authenticate("auth-jwt") {
        get("/api/subscriptions/me") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

            call.respond(SubscriptionStatusResponse(getSubscriptionStatus(userId)))
        }

        post("/api/subscriptions/checkout") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))


            val user = getSubscriptionUser(userId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

            val customerId = user.stripeCustomerId ?: runCatching {
                createStripeCustomer(userId, user.name, user.email).also {
                    saveStripeCustomerId(userId, it)
                }
            }.getOrElse {
                call.respond(HttpStatusCode.BadGateway, ErrorResponse("Could not create Stripe customer"))
                return@post
            }

            val checkoutUrl = runCatching {
                createCheckoutSession(
                    userId = userId,
                    customerId = customerId
                )
            }.getOrElse {
                call.respond(HttpStatusCode.BadGateway, ErrorResponse("Could not create checkout session"))
                return@post
            }

            call.respond(CheckoutSessionResponse(checkoutUrl = checkoutUrl))
        }

        get("/api/subscriptions/payment-method") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))


            val user = getSubscriptionUser(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

            val status = getSubscriptionStatus(userId)

            if (status == "free" || user.stripeCustomerId.isNullOrBlank()) {
                call.respond(
                    PaymentDetailsResponse(subscriptionStatus = status, card = null)
                )
                return@get
            }

            val card = runCatching {
                getDefaultCardForCustomer(user.stripeCustomerId)
            }.getOrElse {
                call.respond(HttpStatusCode.BadGateway, ErrorResponse("Could not load payment method"))
                return@get
            }

            call.respond(
                PaymentDetailsResponse(subscriptionStatus = status, card = card)
            )
        }

        post("/api/subscriptions/customer-portal") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))


            val user = getSubscriptionUser(userId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))

            val customerId = user.stripeCustomerId
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("No Stripe customer exists for this user")
                )

            val portalUrl = runCatching {
                createCustomerPortalUrl(customerId)
            }.getOrElse {
                call.respond(HttpStatusCode.BadGateway, ErrorResponse("Could not create customer portal session"))
                return@post
            }

            call.respond(CustomerPortalResponse(portalUrl = portalUrl))
        }

        get("/api/subscriptions/invoices") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

            val stripeCustomerId = getStripeCustomerId(userId)

            if (stripeCustomerId == null) {
                call.respond(emptyList<BillingInvoiceResponse>())
                return@get
            }

            val invoices = runCatching {
                getBillingInvoices(stripeCustomerId)
            }.getOrElse {
                call.respond(HttpStatusCode.BadGateway, ErrorResponse("Could not load invoices"))
                return@get
            }

            call.respond(invoices)
        }

        get("/api/subscriptions/billing-information") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

            val stripeCustomerId = getStripeCustomerId(userId)

            val billingAddress = runCatching {
                getBillingAddress(stripeCustomerId)
            }.getOrElse {
                call.respond(HttpStatusCode.BadGateway, ErrorResponse("Could not load billing information"))
                return@get
            }

            call.respond(billingAddress)
        }
    }

    stripeWebhookRoutes()
    subscriptionPagesRoutes()
}