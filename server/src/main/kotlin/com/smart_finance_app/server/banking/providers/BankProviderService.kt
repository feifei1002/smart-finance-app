package com.smart_finance_app.server.banking.providers

import com.google.gson.reflect.TypeToken
import com.smart_finance_app.server.Database
import com.smart_finance_app.server.banking.config.bankingGson
import com.smart_finance_app.server.banking.models.BankProviderResponse
import com.smart_finance_app.server.banking.models.BankProviderVariantResponse
import com.smart_finance_app.server.banking.truelayer.TrueLayerProvider
import com.smart_finance_app.server.banking.truelayer.fetchTrueLayerProviders
import kotlin.use

// Bank Provider List Cache
private const val PROVIDER_CACHE_TTL_DAYS = 7L

private val trueLayerProviderListType = object : TypeToken<List<TrueLayerProvider>>() {}.type

private const val PROVIDER_CACHE_KEY = "truelayer-providers:all"

internal fun fetchTrueLayerProvidersFromDB(): List<TrueLayerProvider> {

    val cachedProviders = getCachedTrueLayerProviders(
        cacheKey = PROVIDER_CACHE_KEY,
        allowExpired = false
    )

    if (cachedProviders != null) {
        println("Using cached TrueLayer providers: ${cachedProviders.size}")
        return cachedProviders
    }

    return runCatching {
        val freshProviders = fetchTrueLayerProviders()
        saveCachedTrueLayerProviders(PROVIDER_CACHE_KEY, freshProviders)

        println("Fetched fresh TrueLayer providers: ${freshProviders.size}")
        freshProviders
    }.getOrElse { exception ->
        val expiredCache = getCachedTrueLayerProviders(
            cacheKey = PROVIDER_CACHE_KEY,
            allowExpired = true
        )

        if (expiredCache != null) {
            println("TrueLayer fetch failed, using expired provider cache: ${expiredCache.size}")
            expiredCache
        } else {
            throw exception
        }
    }
}

private fun getCachedTrueLayerProviders(
    cacheKey: String,
    allowExpired: Boolean
): List<TrueLayerProvider>? {
    val sql = if (allowExpired) {
        """
            SELECT providers_json FROM bank_provider_cache WHERE cache_key = ?
        """.trimIndent()
    } else {
        """
            SELECT providers_json FROM bank_provider_cache WHERE cache_key = ?
            AND fetched_at >= NOW() - INTERVAL '$PROVIDER_CACHE_TTL_DAYS days'
        """.trimIndent()
    }

    val providersJson = Database.dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, cacheKey)

            statement.executeQuery().use { result ->
                if (result.next()) {
                    result.getString("providers_json")
                } else {
                    null
                }
            }
        }
    }

    return providersJson?.let {
        bankingGson.fromJson(it, trueLayerProviderListType)
    }
}

private fun saveCachedTrueLayerProviders(
    cacheKey: String,
    providers: List<TrueLayerProvider>
) {
    val providersJson = bankingGson.toJson(providers)

    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                INSERT INTO bank_provider_cache (cache_key, providers_json, fetched_at)
                VALUES (?, ?::jsonb, NOW()) ON CONFLICT (cache_key) DO UPDATE SET
                    providers_json = EXCLUDED.providers_json,
                    fetched_at = NOW()
            """.trimIndent()
            ).use { statement ->
                statement.setString(1, cacheKey)
                statement.setString(2, providersJson)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

internal fun groupBankProviders(providers: List<TrueLayerProvider>): List<BankProviderResponse> {
    val providersWithMock = providers.toMutableList()

    return providersWithMock
        .groupBy { normalisedBankName(it) }
        .map { (bankName, group) ->
            val sortedVariants = group.sortedWith(
                compareBy<TrueLayerProvider> { providerVariantLabel(it) != "Personal" }
                    .thenBy { it.displayName }
            )

            val primary = sortedVariants.first()

            BankProviderResponse(
                id = primary.providerId,
                name = bankName,
                logoUrl = group.firstNotNullOfOrNull { it.logoUrl },
                variants = sortedVariants.map {
                    BankProviderVariantResponse(
                        id = it.providerId,
                        label = providerVariantLabel(it),
                        name = it.displayName
                    )
                }.distinctBy { it.label }
            )
        }
        .sortedWith(
            compareBy<BankProviderResponse> { it.id != "uk-cs-mock" }
                .thenBy { it.name }
        )
}

private fun normalisedBankName(provider: TrueLayerProvider): String {
    val cleanedName = provider.displayName
        .replace(Regex("\\s*\\((personal|business|corporate|commercial).*\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+-\\s+(personal|business|corporate|commercial).*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(personal|business|corporate|commercial)\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    return cleanedName.ifBlank {
        provider.providerId
            .removePrefix("ob-")
            .split("-")
            .filterNot { it in setOf("personal", "business", "corporate", "commercial") }
            .joinToString(" ") { token ->
                token.replaceFirstChar { char -> char.titlecase() }
            }
    }
}

private fun providerVariantLabel(provider: TrueLayerProvider): String {
    val value = "${provider.providerId} ${provider.displayName}".lowercase()

    return when {
        "business" in value -> "Business"
        "corporate" in value -> "Corporate"
        "commercial" in value -> "Commercial"
        else -> "Personal"
    }
}