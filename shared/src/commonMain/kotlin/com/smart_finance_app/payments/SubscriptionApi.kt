package com.smart_finance_app.payments

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class CheckoutSessionResponse(val checkoutUrl: String)

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
    val number: String? = null,
    val status: String? = null,
    val amountPaid: Double,
    val currency: String,
    val createdAt: String,
    val hostedInvoiceUrl: String? = null
)

@Serializable
data class BillingAddressResponse(
    val name: String? = null,
    val email: String? = null,
    val line1: String? = null,
    val line2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null
)

sealed interface CheckoutResult {
    data class Success(val checkoutUrl: String): CheckoutResult
    data class Failure(val message: String): CheckoutResult
}

sealed interface SubscriptionStatusResult {
    data class Success(val status: String): SubscriptionStatusResult
    data class Failure(val message: String): SubscriptionStatusResult
}

sealed interface PaymentDetailsResult {
    data class Success(val details: PaymentDetailsResponse): PaymentDetailsResult
    data class Failure(val message: String): PaymentDetailsResult
}

sealed interface CustomerPortalResult {
    data class Success(val portalUrl: String): CustomerPortalResult
    data class Failure(val message: String): CustomerPortalResult
}

sealed interface BillingInvoicesResult {
    data class Success(val invoices: List<BillingInvoiceResponse>): BillingInvoicesResult
    data class Failure(val message: String): BillingInvoicesResult
}

sealed interface BillingAddressResult {
    data class Success(val address: BillingAddressResponse) : BillingAddressResult
    data class Failure(val message: String): BillingAddressResult
}

class SubscriptionApi(baseUrl: String, private val client: HttpClient) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun getStatus(token: String): SubscriptionStatusResult {
        return try {
            val response = client.get("$normalizedBaseUrl/api/subscriptions/me") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> SubscriptionStatusResult.Success(response.body<SubscriptionStatusResponse>().status)
                HttpStatusCode.Unauthorized -> SubscriptionStatusResult.Failure("Your session expired. Please sign in again.")
                else -> SubscriptionStatusResult.Failure("Could not load subscription status. Status: ${response.status.value}")

            }
        } catch (_: Exception) {
            SubscriptionStatusResult.Failure("Cannot connect to the server.")
        }
    }

    suspend fun createCheckoutSession(token: String): CheckoutResult {
        return try {
            val response = client.post("$normalizedBaseUrl/api/subscriptions/checkout") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> CheckoutResult.Success(response.body<CheckoutSessionResponse>().checkoutUrl)
                HttpStatusCode.Unauthorized -> CheckoutResult.Failure("Your session expired. Please sign in again.")
                else -> CheckoutResult.Failure("Could not start checkout. Status: ${response.status.value}")
            }
        } catch (_: Exception) {
            CheckoutResult.Failure("Cannot connect to the server.")
        }
    }

    suspend fun getPaymentDetails(token: String): PaymentDetailsResult {
        return try {
            val response = client.get("$normalizedBaseUrl/api/subscriptions/payment-method") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> PaymentDetailsResult.Success(response.body())
                HttpStatusCode.Unauthorized -> PaymentDetailsResult.Failure("Your session expired. Please sign in again.")
                else -> PaymentDetailsResult.Failure("Could not load payment details. Status: ${response.status.value}")
        }
    } catch (_: Exception) {
            PaymentDetailsResult.Failure("Cannot connect to the server")
        }
    }

    suspend fun createCustomerPortalSession(token: String): CustomerPortalResult {
        return try {
            val response = client.post("$normalizedBaseUrl/api/subscriptions/customer-portal") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> CustomerPortalResult.Success(response.body<CustomerPortalResponse>().portalUrl)
                HttpStatusCode.Unauthorized -> CustomerPortalResult.Failure("Your session expired. Please sign in again.")
                else -> CustomerPortalResult.Failure("Could not open payment settings. Status: ${response.status.value}")
            }
        } catch (_: Exception) {
            CustomerPortalResult.Failure("Cannot connect to the server")
        }
    }

    suspend fun getInvoices(token: String): BillingInvoicesResult {
        return try {
            val response = client.get("$normalizedBaseUrl/api/subscriptions/invoices") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> BillingInvoicesResult.Success(response.body())
                HttpStatusCode.Unauthorized -> BillingInvoicesResult.Failure("Your session expired. Please try again.")
                else -> BillingInvoicesResult.Failure("Could not load billing history. Status: ${response.status.value}")
            }
        } catch (_: Exception) {
            BillingInvoicesResult.Failure("Cannot connect to the server")
        }
    }

    suspend fun getBillingAddress(token: String): BillingAddressResult {
        return try {
            val response = client.get("$normalizedBaseUrl/api/subscriptions/billing-information") {
                bearerAuth(token)
            }

            when (response.status) {
                HttpStatusCode.OK -> BillingAddressResult.Success(response.body())
                HttpStatusCode.Unauthorized -> BillingAddressResult.Failure("Your session expired. Please sign in again.")
                else -> BillingAddressResult.Failure("Could not load billing information. Status: ${response.status.value}")
            }
        } catch (_: Exception) {
            BillingAddressResult.Failure("Cannot connect to the server")
        }
    }
}