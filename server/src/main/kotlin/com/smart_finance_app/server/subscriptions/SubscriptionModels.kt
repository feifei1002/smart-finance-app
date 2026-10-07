package com.smart_finance_app.server.subscriptions

import kotlinx.serialization.Serializable

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

internal data class SubscriptionUser(
    val name: String,
    val email: String,
    val stripeCustomerId: String?
)