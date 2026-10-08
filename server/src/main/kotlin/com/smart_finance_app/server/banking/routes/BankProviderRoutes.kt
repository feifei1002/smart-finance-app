package com.smart_finance_app.server.banking.routes

import com.smart_finance_app.server.ErrorResponse
import com.smart_finance_app.server.banking.providers.fetchTrueLayerProvidersFromDB
import com.smart_finance_app.server.banking.providers.groupBankProviders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.bankProviderRoutes() {
    get("/api/banking/providers") {
        val providers = runCatching {
            fetchTrueLayerProvidersFromDB()
        }.getOrElse { exception ->
            call.application.environment.log.error("Could not load TrueLayer providers", exception)

            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("Could not load bank providers. Please try again.")
            )
            return@get
        }

        call.respond(groupBankProviders(providers))
    }
}