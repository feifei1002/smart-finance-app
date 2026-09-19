package com.smart_finance_app.currency

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class ExchangeRatesResponse(val rates: Map<String, Double>)

/**
 * Result of a currency conversion attempt.
 * Using a sealed type forces callers to explicitly handle the failure case
 * rather than silently treating unconverted amounts as converted ones.
 */
sealed interface ConversionResult {
    data class Success(val amount: Double) : ConversionResult
    data object RatesUnavailable : ConversionResult
    data class MissingCurrency(val currency: String) : ConversionResult
}

object ExchangeRateService {

    private var cachedRates: Map<String, Double> = emptyMap()
    private var lastFetchTime: Long = 0L
    private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour

    suspend fun getRates(client: HttpClient, baseUrl: String): Map<String, Double> {
        val now = Clock.System.now().toEpochMilliseconds()
        if (cachedRates.isNotEmpty() && (now - lastFetchTime) < CACHE_DURATION_MS) {
            return cachedRates
        }

        return try {
            val response = client.get("${baseUrl.trimEnd('/')}/api/exchange-rates")
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<ExchangeRatesResponse>()
                if (body.rates.isNotEmpty()) {
                    cachedRates   = body.rates
                    lastFetchTime = now
                    cachedRates
                } else {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Attempts to convert [amount] from [fromCurrency] to [toCurrency].
     * Returns [ConversionResult.Success] with the converted value, or
     * [ConversionResult.Failure] with the reason — never silently returns
     * the original amount when conversion was required but failed.
     *
     * Same-currency conversion always succeeds immediately.
     */
    fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        rates: Map<String, Double>
    ): ConversionResult {
        val from = fromCurrency.uppercase()
        val to = toCurrency.uppercase()

        if (from == to) return ConversionResult.Success(amount)
        if (rates.isEmpty()) return ConversionResult.RatesUnavailable

        val fromRate = rates[from] ?: return ConversionResult.MissingCurrency(from)
        val toRate = rates[to] ?: return ConversionResult.MissingCurrency(to)

        val amountInEur = amount / fromRate
        return ConversionResult.Success(amountInEur * toRate)
    }
}