package com.smart_finance_app.server.subscriptions

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.subscriptionPagesRoutes() {
    get("/subscription/success") {
        call.respondText(
            """
        <html>
            <body style="font-family:sans-serif;text-align:center;padding:40px">
                <h2>Subscription activated</h2>
                <p>Your Basic subscription is now active.</p>
                <p>You can close this window and return to Smart Finance App.</p>
            </body>
        </html>
        """.trimIndent(),
            ContentType.Text.Html
        )
    }

    get("/subscription/cancel") {
        call.respondText(
            """
        <html>
            <body style="font-family:sans-serif;text-align:center;padding:40px">
                <h2>Checkout cancelled</h2>
                <p>Your subscription was not changed.</p>
                <p>You can close this window and return to Smart Finance App.</p>
            </body>
        </html>
        """.trimIndent(),
            ContentType.Text.Html
        )
    }
}