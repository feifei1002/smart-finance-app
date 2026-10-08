package com.smart_finance_app.server.subscriptions

import com.stripe.model.Customer
import com.stripe.model.Invoice
import com.stripe.model.PaymentMethod
import com.stripe.model.billingportal.Session as BillingPortalSession
import com.stripe.param.InvoiceListParams
import com.stripe.param.PaymentMethodListParams
import com.stripe.param.billingportal.SessionCreateParams as BillingPortalSessionCreateParams
import java.time.Instant

internal fun getDefaultCardForCustomer(customerId: String): PaymentCardResponse? {
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

internal fun getBillingInvoices(customerId: String): List<BillingInvoiceResponse> {
    val params = InvoiceListParams.builder()
        .setCustomer(customerId)
        .setLimit(10L)
        .build()

    return Invoice.list(params).data.map { invoice ->
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
}

internal fun getBillingAddress(customerId: String?): BillingAddressResponse {
    if (customerId == null) {
        return emptyBillingAddress()
    }

    val customer = Customer.retrieve(customerId)
    val address = customer.address

    return BillingAddressResponse(
        name = customer.name,
        email = customer.email,
        line1 = address?.line1,
        line2 = address?.line2,
        city = address?.city,
        state = address?.state,
        postalCode = address?.postalCode,
        country = address?.country
    )
}

internal fun createCustomerPortalUrl(customerId: String): String {
    val portalSession = BillingPortalSession.create(
        BillingPortalSessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(StripeConfig.portalReturnUrl)
            .build()
    )

    return portalSession.url
}

private fun emptyBillingAddress(): BillingAddressResponse {
    return BillingAddressResponse(
        name = null,
        email = null,
        line1 = null,
        line2 = null,
        city = null,
        state = null,
        postalCode = null,
        country = null
    )
}