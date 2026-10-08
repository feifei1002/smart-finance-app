package com.smart_finance_app.server.subscriptions

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.checkout.Session
import com.stripe.param.CustomerCreateParams
import com.stripe.param.checkout.SessionCreateParams
import java.util.UUID

internal fun configureStripe() {
    Stripe.apiKey = StripeConfig.secretKey
}

internal fun createStripeCustomer(userId: UUID, name: String, email: String): String {
    val customer = Customer.create(
        CustomerCreateParams.builder()
            .setName(name)
            .setEmail(email)
            .putMetadata("userId", userId.toString())
            .build()
    )

    return customer.id
}

internal fun createCheckoutSession(
    userId: UUID,
    customerId: String
): String {
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
            )
            .build()
    )

    return session.url
}
