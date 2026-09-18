package com.smart_finance_app.currency

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class ExchangeRatesResponse(val rates: Map<String, Double>)

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

    fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        rates: Map<String, Double>
    ): Double {
        if (fromCurrency.uppercase() == toCurrency.uppercase()) return amount
        if (rates.isEmpty()) return amount
        val fromRate = rates[fromCurrency.uppercase()] ?: return amount
        val toRate   = rates[toCurrency.uppercase()]   ?: return amount
        val inEUR    = amount / fromRate
        return inEUR * toRate
    }
}