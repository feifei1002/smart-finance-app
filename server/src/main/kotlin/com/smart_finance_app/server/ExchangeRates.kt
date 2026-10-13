package com.smart_finance_app.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant

@Serializable
data class ExchangeRatesResponse(val rates: Map<String, Double>)

private val httpClient = HttpClient(CIO) {
    expectSuccess = false

    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 10_000
    }
}

private val exchangeLogger = org.slf4j.LoggerFactory.getLogger("ExchangeRates")

private val exchangeRateJson = Json {
    ignoreUnknownKeys = true
}

private val requiredCurrencies = setOf("EUR", "GBP", "USD", "PLN", "TWD")

private val exchangeRateCacheMutex = Mutex()
private var cachedExchangeRates: Map<String, Double> = emptyMap()
private var cachedExchangeRatesAt: Instant? = null

private val exchangeRateCacheTtl: Duration = Duration.ofHours(6)

fun Route.exchangeRatesRoutes() {
    get("/api/exchange-rates") {
        val rates = getExchangeRatesWithCache()

        if (!rates.keys.containsAll(requiredCurrencies)) {
            exchangeLogger.warn(
                "Exchange rates unavailable. Missing currencies: {}",
                requiredCurrencies - rates.keys
            )

            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("Exchange rates unavailable")
            )
            return@get
        }

        call.respond(HttpStatusCode.OK, ExchangeRatesResponse(rates))
    }
}


private suspend fun getExchangeRatesWithCache(): Map<String, Double> {
    return exchangeRateCacheMutex.withLock {
        val now = Instant.now()
        val cachedAt = cachedExchangeRatesAt

        val cacheIsFresh =
            cachedExchangeRates.isNotEmpty() &&
                    cachedAt != null &&
                    Duration.between(cachedAt, now) < exchangeRateCacheTtl

        if (cacheIsFresh) {
            return@withLock cachedExchangeRates
        }

        val freshRates = fetchExchangeRateApiRates()

        if (freshRates.keys.containsAll(requiredCurrencies)) {
            cachedExchangeRates = freshRates
            cachedExchangeRatesAt = now

            exchangeLogger.info("Exchange rates loaded: count={}", freshRates.size)
            return@withLock freshRates
        }

        if (cachedExchangeRates.isNotEmpty()) {
            exchangeLogger.warn("Using stale cached exchange rates")
            return@withLock cachedExchangeRates
        }

        emptyMap()
    }
}

private suspend fun fetchExchangeRateApiRates(): Map<String, Double> {
    return try {
        val response = httpClient.get("https://open.er-api.com/v6/latest/EUR")

        if (response.status != HttpStatusCode.OK) {
            exchangeLogger.warn("ExchangeRate API returned status={}", response.status)
            return emptyMap()
        }

        val body = response.bodyAsText()
        val json = exchangeRateJson.parseToJsonElement(body).jsonObject
        val ratesJson = json["rates"]?.jsonObject ?: return emptyMap()

        val rates = requiredCurrencies.mapNotNull { currency ->
            if (currency == "EUR") {
                currency to 1.0
            } else {
                ratesJson[currency]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?.let { currency to it }
            }
        }.toMap()

        rates
    } catch (exception: Exception) {
        exchangeLogger.warn("ExchangeRate API fetch failed", exception)
        emptyMap()
    }
}
