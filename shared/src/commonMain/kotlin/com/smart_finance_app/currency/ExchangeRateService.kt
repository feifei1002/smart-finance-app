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
 * Supported: GBP, USD, EUR, TWD
 */
object ExchangeRateService {

    // Cached rates — keyed by currency code, value is how many units = 1 EUR
    private var cachedRates: Map<String, Double> = emptyMap()
    private var lastFetchTime: Long = 0L
    private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour

    // Fallback rates in case ECB is unreachable
    // These are approximate and should only be used as a last resort
    private val fallbackRates = mapOf(
        "EUR" to 1.0,
        "GBP" to 0.86,
        "USD" to 1.08,
        "TWD" to 34.5
    )

    /**
     * Returns exchange rates, fetching from ECB if cache is stale.
     * Always returns a non-empty map (falls back to hardcoded rates on failure).
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
                    fallbackRates
                }
            } else {
                fallbackRates
            }
        } catch (_: Exception) {
            fallbackRates
        }
    }

    /**
     * Converts an amount from one currency to another using cached rates.
     * All conversions go via EUR as the base.
     */
    fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        rates: Map<String, Double>
    ): Double {
        if (fromCurrency.uppercase() == toCurrency.uppercase()) return amount
        val fromRate = rates[fromCurrency.uppercase()] ?: return amount
        val toRate   = rates[toCurrency.uppercase()]   ?: return amount
        // Convert to EUR first, then to target currency
        val inEUR = amount / fromRate
        return inEUR * toRate
    }

    /**
     * Parses the ECB daily XML feed.
     * Format: <Cube currency='USD' rate='1.0823'/>
     */
    private fun parseECBRates(xml: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val regex = Regex("""currency='([A-Z]+)'\s+rate='([0-9.]+)'""")
        regex.findAll(xml).forEach { match ->
            val currency = match.groupValues[1]
            val rate     = match.groupValues[2].toDoubleOrNull()
            if (rate != null) result[currency] = rate
        }
        return result
    }
}