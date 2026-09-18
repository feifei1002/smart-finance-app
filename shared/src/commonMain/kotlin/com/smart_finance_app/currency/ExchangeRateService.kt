package com.smart_finance_app.currency

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock

/**
 * Fetches daily exchange rates from the European Central Bank.
 * Rates are EUR-based (1 EUR = X currency).
 * Free, no API key required, updated daily.
 *
 * Supported: GBP, USD, EUR, TWD, PLN
 *
 * If ECB is unreachable, returns an empty map — callers should handle
 * this by showing amounts in their original currency unchanged.
 */
object ExchangeRateService {

    private var cachedRates: Map<String, Double> = emptyMap()
    private var lastFetchTime: Long = 0L
    private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour

    /**
     * Returns exchange rates, fetching from ECB if cache is stale.
     * Returns an empty map if ECB is unreachable — never returns stale
     * hardcoded rates.
     */
    suspend fun getRates(client: HttpClient): Map<String, Double> {
        val now = Clock.System.now().toEpochMilliseconds()
        if (cachedRates.isNotEmpty() && (now - lastFetchTime) < CACHE_DURATION_MS) {
            return cachedRates
        }

        return try {
            val response = client.get(
                "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
            )
            if (response.status == HttpStatusCode.OK) {
                val parsed = parseECBRates(response.bodyAsText())
                if (parsed.isNotEmpty()) {
                    cachedRates = parsed + ("EUR" to 1.0)
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
     * Converts an amount from one currency to another using provided rates.
     * If rates are empty or the currency pair is not found, returns the
     * original amount unchanged — no guessing.
     */
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

    private fun parseECBRates(xml: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val regex  = Regex("""currency='([A-Z]+)'\s+rate='([0-9.]+)'""")
        regex.findAll(xml).forEach { match ->
            val currency = match.groupValues[1]
            val rate     = match.groupValues[2].toDoubleOrNull()
            if (rate != null) result[currency] = rate
        }
        return result
    }
}