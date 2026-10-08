package com.smart_finance_app.server.subscriptions

import com.smart_finance_app.server.ErrorResponse
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID

internal fun Route.stripeWebhookRoutes() {
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
                    markSubscriptionFreeBySubscriptionId(subscription.id)
                }
            }

            "invoice.payment_failed" -> {
                val invoice = event.dataObjectDeserializer.getObject().orElse(null) as? Invoice
                val customerId  = invoice?.customer

                if (!customerId.isNullOrBlank()) {
                    markSubscriptionFreeByCustomerId(customerId)
                }
            }
        }

        call.respond(HttpStatusCode.OK)
    }
}