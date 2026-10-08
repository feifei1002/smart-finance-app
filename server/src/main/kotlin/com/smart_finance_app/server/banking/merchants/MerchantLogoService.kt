package com.smart_finance_app.server.banking.merchants

import com.smart_finance_app.server.Database
import com.smart_finance_app.server.banking.config.bankingGson
import com.smart_finance_app.server.banking.config.bankingHttpClient
import com.smart_finance_app.server.banking.models.TransactionResponse
import okhttp3.Request
import java.net.URLEncoder
import kotlin.use

// ── Logo.dev config ─────────────────────────────────────────────────────────
private object LogoDevConfig {
    val secretKey: String?
        get() = System.getenv("LOGO_DEV_SECRET_KEY")

    val publishableKey: String?
        get() = System.getenv("LOGO_DEV_PUBLISHABLE_KEY")
}

private data class LogoDevSearchResult(
    val name: String,
    val domain: String
)

private data class CachedMerchantLogo(val logoUrl: String?)

internal fun resolveMerchantLogoUrl(merchantName: String): String? {
    val key = normaliseMerchantKey(merchantName)
    if (key.isBlank()) return null

    getCachedMerchantLogo(key)?.let {
        return it.logoUrl
    }

    val resolved = runCatching {
        fetchMerchantLogoFromLogoDev(merchantName)
    }.getOrNull()

    saveMerchantLogoCache(
        merchantKey = key,
        merchantName = merchantName,
        domain = resolved?.first,
        logoUrl = resolved?.second,
        lookupStatus = if (resolved?.second != null) "found" else "not_found"
    )

    return resolved?.second
}
private fun getCachedMerchantLogo(merchantKey: String): CachedMerchantLogo? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT logo_url FROM merchant_logos WHERE merchant_key = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, merchantKey)
            statement.executeQuery().use { result ->
                if (result.next()) {
                    CachedMerchantLogo(result.getString("logo_url"))
                } else {
                    null
                }
            }
        }
    }

private fun saveMerchantLogoCache(merchantKey: String, merchantName: String, domain: String?,
                                  logoUrl: String?, lookupStatus: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    INSERT INTO merchant_logos
                        (merchant_key, merchant_name, domain, logo_url, lookup_status, last_lookup_at)
                    VALUES (?, ?, ?, ?, ?, now()) ON CONFLICT (merchant_key)
                    DO UPDATE SET
                        merchant_name = EXCLUDED.merchant_name,
                        domain = EXCLUDED.domain,
                        logo_url = EXCLUDED.logo_url,
                        lookup_status = EXCLUDED.lookup_status,
                        last_lookup_at = now()
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, merchantKey)
                statement.setString(2, merchantName)
                statement.setString(3, domain)
                statement.setString(4, logoUrl)
                statement.setString(5, lookupStatus)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun fetchMerchantLogoFromLogoDev(merchantName: String): Pair<String, String>? {
    val secretKey = LogoDevConfig.secretKey ?: return null
    val publishableKey = LogoDevConfig.publishableKey ?: return null

    val searchUrl =  "https://api.logo.dev/search?q=${URLEncoder.encode(merchantName, "UTF-8")}&strategy=match"

    val searchRequest = Request.Builder()
        .url(searchUrl)
        .header("Authorization", "Bearer $secretKey")
        .get()
        .build()

    val responseBody = bankingHttpClient.newCall(searchRequest).execute().use { response ->
        val body = response.body?.string() ?: return null
        if (!response.isSuccessful) return null
        body
    }

    val results = bankingGson.fromJson(responseBody, Array<LogoDevSearchResult>::class.java)

    val match = results.firstOrNull { result ->
        val merchantKey = normaliseMerchantKey(merchantName)
        val resultKey = normaliseMerchantKey("${result.name} ${result.domain}")

        merchantKey.split("-")
            .filter { it.length >= 3 }
            .any { token -> token in resultKey }
    } ?: results.firstOrNull()
    ?: return null

    val domain = match.domain

    val logoUrl = buildString {
        append("https://img.logo.dev/")
        append(domain)
        append("?token=${URLEncoder.encode(publishableKey, "UTF-8")}")
        append("&size=64")
        append("&format=png")
        append("&fallback=404")
    }

    return domain to logoUrl
}

private fun normaliseMerchantKey(value: String): String =
    value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

private fun cleanMerchantNameForLogoLookup(value: String): String {
    return value
        .replace("WWW.", "", ignoreCase = true)
        .replace(".COM", "", ignoreCase = true)
        .replace(" CARD PAYMENT", "", ignoreCase = true)
        .replace(" CONTACTLESS", "", ignoreCase = true)
        .trim()
}

internal fun merchantNameForLogoLookup(transaction: TransactionResponse): String? {
    val candidate = transaction.merchantName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: transaction.description.trim().takeIf { it.isNotBlank() }

    if (candidate == null) return null

    val text = candidate.lowercase()

    val notMerchant =
        text.startsWith("mr ") ||
                text.startsWith("mrs ") ||
                text.startsWith("ms ") ||
                text.startsWith("miss ") ||
                text.contains("returned direct debit") ||
                text.contains("atm") ||
                text.contains("save the change")

    return if (notMerchant) null else cleanMerchantNameForLogoLookup(candidate)
}