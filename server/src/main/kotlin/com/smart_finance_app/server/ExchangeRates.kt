package com.smart_finance_app.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class ExchangeRatesResponse(val rates: Map<String, Double>)

private val httpClient = HttpClient(CIO) {
    expectSuccess = false
}

fun Route.exchangeRatesRoutes() {
    get("/api/exchange-rates") {
        try {
            // Fetch from ECB
            val ecbRates = fetchECBRates()

            // Fetch TWD separately from open.er-api.com
            val twdRate = fetchTWDRate()

            val combined = ecbRates.toMutableMap()
            combined["EUR"] = 1.0
            if (twdRate != null) combined["TWD"] = twdRate

            if (combined.isEmpty()) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("Exchange rates unavailable")
                )
            } else {
                call.respond(HttpStatusCode.OK, ExchangeRatesResponse(combined))
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("Exchange rates unavailable")
            )
        }
    }
}

private suspend fun fetchECBRates(): Map<String, Double> {
    return try {
        val response = httpClient.get(
            "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
        )
        if (response.status == HttpStatusCode.OK) {
            parseECBXml(response.bodyAsText())
        } else emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }
}

private suspend fun fetchTWDRate(): Double? {
    return try {
        val response = httpClient.get("https://open.er-api.com/v6/latest/EUR")
        if (response.status != HttpStatusCode.OK) return null
        val body = response.bodyAsText()
        Regex(""""TWD"\s*:\s*([0-9.]+)""").find(body)
            ?.groupValues?.get(1)?.toDoubleOrNull()
    } catch (_: Exception) {
        null
    }
}

private fun parseECBXml(xml: String): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    val regex  = Regex("""currency='([A-Z]+)'\s+rate='([0-9.]+)'""")
    regex.findAll(xml).forEach { match ->
        val currency = match.groupValues[1]
        val rate     = match.groupValues[2].toDoubleOrNull()
        if (rate != null) result[currency] = rate
    }
    return result
}