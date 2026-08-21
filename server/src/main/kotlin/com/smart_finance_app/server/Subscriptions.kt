package com.smart_finance_app.server

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.Invoice
import com.stripe.model.PaymentMethod
import com.stripe.model.Subscription
import com.stripe.model.billingportal.Session as BillingPortalSession
import com.stripe.param.billingportal.SessionCreateParams as BillingPortalSessionCreateParams
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.InvoiceListParams
import com.stripe.param.PaymentMethodListParams
import com.stripe.param.checkout.SessionCreateParams
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID


@Serializable
data class CheckoutSessionResponse(val checkoutUrl: String) {
}

@Serializable
data class SubscriptionStatusResponse(val status: String)

@Serializable
data class PaymentDetailsResponse(val subscriptionStatus: String, val card: PaymentCardResponse?)

@Serializable
data class PaymentCardResponse(
    val brand: String,
    val last4: String,
    val expMonth: Long,
    val expYear: Long
)

@Serializable
data class CustomerPortalResponse(val portalUrl: String)

@Serializable
data class BillingInvoiceResponse(
    val id: String,
    val number: String?,
    val status: String?,
    val amountPaid: Double,
    val currency: String,
    val createdAt: String,
    val hostedInvoiceUrl: String?
)

@Serializable
data class BillingAddressResponse(
    val name: String?,
    val email: String?,
    val line1: String?,
    val line2: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String?
)
private object StripeConfig {
    val secretKey: String get() = System.getenv("STRIPE_SECRET_KEY")
        ?: error("Missing environment variable: STRIPE_SECRET_KEY")

    val webhookSecret: String get() = System.getenv("STRIPE_WEBHOOK_SECRET")
        ?: error("Missing environment variable: STRIPE_WEBHOOK_SECRET")

    val proPriceId: String get() = System.getenv("STRIPE_BASIC_PRICE_ID")
        ?: error("Missing environment variable: STRIPE_BASIC_PRICE_ID")

    val successUrl: String get() = System.getenv("STRIPE_SUCCESS_URL")
        ?: "http://localhost:8080/subscription/success"

    val cancelUrl: String get() = System.getenv("STRIPE_CANCEL_URL")
        ?: "http://localhost:8080/subscription/cancel"

    val portalReturnUrl: String get() = System.getenv("STRIPE_PORTAL_RETURN_URL")
        ?: "http://localhost:8080/subscription/success"
}

fun Route.subscriptionRoutes() {
    Stripe.apiKey = StripeConfig.secretKey

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

            val customerId = user.stripeCustomerId ?: createStripeCustomer(userId, user.name, user.email).also {
                saveStripeCustomerId(userId, it)
            }

            val session = Session.create(
                SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(StripeConfig.successUrl)
                    .setCancelUrl(StripeConfig.cancelUrl)
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                    .setClientReferenceId(userId.toString())
                    .putMetadata("userId", userId.toString())
                    .addLineItem(
                        SessionCreateParams.LineItem.builder()
                            .setPrice(StripeConfig.proPriceId)
                            .setQuantity(1L)
                            .build()
                    ).build()
            )

            call.respond(CheckoutSessionResponse(checkoutUrl = session.url))
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

            val card = getDefaultCardForCustomer(user.stripeCustomerId)

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

            val portalSession = BillingPortalSession.create(
                BillingPortalSessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(StripeConfig.portalReturnUrl)
                    .build()
            )

            call.respond(CustomerPortalResponse(portalUrl = portalSession.url))
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
                val params = InvoiceListParams.builder()
                    .setCustomer(stripeCustomerId)
                    .setLimit(10L)
                    .build()

                Invoice.list(params).data.map { invoice ->
                    BillingInvoiceResponse(
                        id = invoice.id,
                        number = invoice.number,
                        status = invoice.status,
                        amountPaid = invoice.amountPaid / 100.0,
                        currency = invoice.currency.uppercase(),
                        createdAt = Instant.ofEpochSecond(invoice.created).toString(),
                        hostedInvoiceUrl = invoice.hostedInvoiceUrl
                    )
                }
            }.getOrElse {
                call.respond(
                    HttpStatusCode.BadGateway,
                    ErrorResponse("Could not load invoices")
                )
                return@get
            }

            call.respond(invoices)
        }

        get("/api/subscriptions/billing-information") {
            val userId = call.principal<JWTPrincipal>()?.userIdOrNull()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))

            val stripeCustomerId = getStripeCustomerId(userId)

            if (stripeCustomerId == null) {
                call.respond(
                    BillingAddressResponse(
                        name = null,
                        email = null,
                        line1 = null,
                        line2 = null,
                        city = null,
                        state = null,
                        postalCode = null,
                        country = null
                    )
                )
                return@get
            }

            val customer = Customer.retrieve(stripeCustomerId)
            val address = customer.address

            call.respond(
                BillingAddressResponse(
                    name = customer.name,
                    email = customer.email,
                    line1 = address?.line1,
                    line2 = address?.line2,
                    city = address?.city,
                    state = address?.state,
                    postalCode = address?.postalCode,
                    country = address?.country
                )
            )
        }
    }

    post("/api/stripe/webhook") {
        val payload = call.receiveText()
        val signature = call.request.headers["Stripe-Signature"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing Stripe signature"))

        val event = runCatching {
            Webhook.constructEvent(payload, signature, StripeConfig.webhookSecret)
        }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid Stripe webhook"))
        }

        when (event.type) {
            "checkout.session.completed" -> {
                val session = event.dataObjectDeserializer.getObject().orElse(null) as? Session
                val userId = session?.metadata?.get("userId")?.let { UUID.fromString(it) }

                if (userId != null) {
                    markSubscriptionBasic(
                        userId = userId,
                        customerId = session.customer,
                        subscriptionId = session.subscription
                    )
                }
            }

            "customer.subscription.deleted" -> {
                val subscription = event.dataObjectDeserializer.getObject().orElse(null) as? Subscription
                if (subscription != null) {
                    markSubscriptionFreeByBySubscriptionId(subscription.id)
                }
            }

            "invoice.payment_failed" -> {
                val subscription = event.dataObjectDeserializer.getObject().orElse(null)
            }
        }

        call.respond(HttpStatusCode.OK)
    }

    get("/subscription/success") {
        call.respondText(
            """
        <html>
            <body style="font-family:sans-serif;text-align:center;padding:40px">
                <h2>Subscription activated</h2>
                <p>Your Basic subscription is now active.</p>
                <p>You can close this window and return to Smart Finance App.</p>
            </body>
        </html>
        """.trimIndent(),
            ContentType.Text.Html
        )
    }

    get("/subscription/cancel") {
        call.respondText(
            """
        <html>
            <body style="font-family:sans-serif;text-align:center;padding:40px">
                <h2>Checkout cancelled</h2>
                <p>Your subscription was not changed.</p>
                <p>You can close this window and return to Smart Finance App.</p>
            </body>
        </html>
        """.trimIndent(),
            ContentType.Text.Html
        )
    }
}

private data class SubscriptionUser(
    val name: String,
    val email: String,
    val stripeCustomerId: String?
)

private fun getSubscriptionUser(userId: UUID): SubscriptionUser? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT full_name, email, stripe_customer_id
                FROM users WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                if (!result.next()) null else SubscriptionUser(
                    name = result.getString("full_name"),
                    email = result.getString("email"),
                    stripeCustomerId = result.getString("stripe_customer_id")
                )
            }
        }
    }

private fun getSubscriptionStatus(userId: UUID): String =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
        "SELECT subscription_status FROM users WHERE id = ?"
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                if (result.next()) result.getString("subscription_status") else "free"
            }
        }
    }

private fun createStripeCustomer(userId: UUID, name: String, email: String): String {
    val customer = Customer.create(
        CustomerCreateParams.builder()
            .setName(name)
            .setEmail(email)
            .putMetadata("userId", userId.toString())
            .build()
    )

    return customer.id
}

private fun saveStripeCustomerId(userId: UUID, customerId: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE users SET stripe_customer_id = ? WHERE id = ?
                """.trimIndent()
            ).use {
                it.setString(1, customerId)
                it.setObject(2, userId)
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

private fun markSubscriptionBasic(userId: UUID, customerId: String?, subscriptionId: String?) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE users
                    SET subscription_status = 'basic',
                        stripe_customer_id = COALESCE(?, stripe_customer_id),
                        stripe_subscription_id = ?,
                        subscription_updated_at = now()
                    WHERE id = ?
                """.trimIndent()
            ).use {
                it.setString(1, customerId)
                it.setString(2, subscriptionId)
                it.setObject(3, userId)
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

private fun markSubscriptionFreeByBySubscriptionId(subscriptionId: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE users SET subscription_status = 'free', subscription_updated_at = now()
                    WHERE stripe_subscription_id = ?
                """.trimIndent()
            ).use {
                it.setString(1, subscriptionId)
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

private fun getDefaultCardForCustomer(customerId: String): PaymentCardResponse? {
    val paymentMethods = PaymentMethod.list(
        PaymentMethodListParams.builder()
            .setCustomer(customerId)
            .setType(PaymentMethodListParams.Type.CARD)
            .setLimit(1L)
            .build()
    )

    val paymentMethod = paymentMethods.data.firstOrNull() ?: return null
    val card = paymentMethod.card ?: return null

    return PaymentCardResponse(
        brand = card.brand ?: "card",
        last4 = card.last4 ?: "----",
        expMonth = card.expMonth,
        expYear = card.expYear
    )
}

private fun getStripeCustomerId(userId: UUID): String? {
    return Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT stripe_customer_id
                FROM users
                WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                if (result.next()) {
                    result.getString("stripe_customer_id")
                } else {
                    null
                }
            }
        }
    }
}

/**
 * Extracts the authenticated app user ID from the JWT principal.
 *
 * Returns null if the token does not contain a valid UUID userId claim.
 */
private fun JWTPrincipal.userIdOrNull(): UUID? {
    val userIdValue = payload
        .getClaim("userId")
        .asString()

    return runCatching {
        UUID.fromString(userIdValue)
    }.getOrNull()
}