package com.smart_finance_app.server

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(CategoryServiceClient::class.java)

object CategoryServiceClient {
    private val EXACT_BRAND_LOOKUP = listOf(
        Regex("\\b(UBER EATS|DELIVEROO|JUST EAT|HAPPY MYMENU|PIZZA EXPRESS|ASDA EXPRESS|TESCO EXPRESS|SAINSBURY'S LOCAL)\\b", RegexOption.IGNORE_CASE) to "Food & Dining",
        Regex("\\b(TESCO|SAINSBURY|SAINSBURY'S|MARKS & SPENCER|MARKS&SPENCER|M&S|WAITROSE|MORRISONS|ALDI|LIDL|COSTCO|ASDA|SPAR|WHOLE FOODS|TAZAKI|CHUANGLEE|OSEYO|WANA HONG|WANAHONG|TIAN TIAN|COSTA|CAFFE NERO|GAIL|GAIL'S|PRET|PRET A MANGER|STARBUCKS|MCDONALD|KFC|WASABI|SHORYU|HAIDILAO|FLAT IRON|LOUNGERS|CHINATOWN BAKERY|MILLE|SHAKE SHACK|WINGSTOP|BURGER & LOBSTER|PAUL|EAT TOKYO|KINEYA|KIKI & MIUMIU|LA MARITXU|DOUGLAS BAKERY|BENTO|RAMEN|SUSHI|BAKERY|BISTRO|PUB|BAR|TAVERN|RESTAURANT|CAFÉ|CAFE|COFFEE|SUPERMARKET|GROCERY|GROCERIES|CASH & CARRY|CASH AND CARRY|RESTORANAS|KRCMA)\\b", RegexOption.IGNORE_CASE) to "Food & Dining",
        Regex("\\b(TFL|TRANSPORT FOR LONDON|TRANSPORT FOR WALES|EMT MADRID|RATP|TRAINLINE|RAILCARD|GRAB|FLIXBUS|REGIOJET|ARRIVA|BEE NETWORK|BKK|DOPRAVNI PODNIK|JUDU|LUX EXPRESS|DUBLIN EXPRESS|POSTAJA|TALLINNA|AVTOBUSNA|TRAINPAL|NEXTBIKE|UBER(?! EATS)|BOLT|METRO|BUS|BUSES|TRAIN|RAILWAY|RAIL|AIRLINE|AIRWAYS?|FLIGHT|TAXI|CAB|PARKING|TRANSIT|STATION)\\b", RegexOption.IGNORE_CASE) to "Transportation",
        Regex("\\b(JOHN LEWIS|SELFRIDGES|PRIMARK|HOLLAND & BARRETT|HOLLAND AND BARRETT|TK MAXX|AMAZON|SHEIN|PULL&BEAR|PULL AND BEAR|JELLYCAT|MOSS BROS|WHSMITH|BOOTS|WATERSTONES|MUJI|H&M|DR\\.MAX|ZEEMAN|UNIQLO|ZARA|ASOS|BACK MARKET|VISION EXPRESS|FREE PRINTS|SEA BARBER|SEABARBER|MOOMIN|SIZE\\?|MAC STRATFORD|PHARMACY|BARBER|SALON)\\b", RegexOption.IGNORE_CASE) to "Shopping & Personal",
        Regex("\\b(LEBARA|VOXI|OCTOPUS ENERGY|ROYAL GREENWICH|LOVESPACE|SAFESTORE|AIRWALLET|EDF|(?!THE )O2(?! VENUE)|EE|VODAFONE|BRITISH GAS|UTILITIES|COUNCIL|RENT|MOBILE|TELECOM)\\b", RegexOption.IGNORE_CASE) to "Bills & Housing",
        Regex("\\b(NETFLIX|SPOTIFY|BOOKING\\.COM|AGODA|TRIP\\.COM|NORDVPN|EXPRESSVPN|OPENAI|ANTHROPIC|GITHUB|LINKEDIN|UDEMY|THE GYM GROUP|GLL BETTER|TRADING 212|GOOGLE PLAY|NINTENDO|AXS TICKETS|TICKETMASTER|ODEON|CINEWORLD|VUE|SHOWCASE|VICTORIA AND ALBERT MUSEUM|NATIONAL MARITIME MUSEUM|MUSEUM|CASTLE|CATHEDRAL|PALACE|GUINNESS STOREHOUSE|LIVRARIA LELLO|CONWY VISITORS|AIRBNB|HILTON|IBIS|TRAVELODGE|HOTEL|HOSTEL|GUESTHOUSE|ZOO|COLOSSEUM|BLUETICKET|JEGYMESTER)\\b", RegexOption.IGNORE_CASE) to "Entertainment & Subscriptions"
    )

    private const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val BATCH_SIZE = 50

    // Categories the AI may return. Transfers and Income are decided by the
    // rules in TransactionCategoryService before anything reaches the AI.
    private val AI_CATEGORIES = listOf(
        "Food & Dining",
        "Shopping & Personal",
        "Bills & Housing",
        "Entertainment & Subscriptions",
        "Transportation",
        "Others"
    )

    private val SYSTEM_PROMPT = """
        You categorise card and bank transaction descriptions from UK and EU  bank accounts.
        Descriptions are often truncated or contain payment-processor prefixes
        (e.g. "SQ *", "SUMUP *", "ZTL*", "PAYPAL *", "CRV*", "AMZN MKTP").
        Use your knowledge of real merchants to pick the single best category:

        - Food & Dining: supermarkets, groceries, restaurants, cafes, bakeries, pubs, takeaways, food delivery
        - Shopping & Personal: clothing, retail, electronics, online marketplaces, pharmacies, hair and beauty
        - Bills & Housing: rent, council tax, energy, water, broadband, mobile phone plans, insurance, storage
        - Entertainment & Subscriptions: streaming and software subscriptions, games, cinema, events, museums, gyms, hotels and holiday lodging
        - Transportation: public transport, trains, buses, flights, taxis and ride-hailing, fuel, parking, car hire
        - Others: cash withdrawals, fees, charities, government payments, or anything you cannot identify with reasonable confidence

        Examples:
        "SQ *JOES COFFEE LONDON" -> Food & Dining
        "PAYPAL *STEAM GAMES" -> Entertainment & Subscriptions
        "AMZN MKTP UK" -> Shopping & Personal
        "SHELL KENSINGTON" -> Transportation
        "THAMES WATER" -> Bills & Housing
        "CASH WITHDRAWAL ATM" -> Others

        Do not guess from a person's name alone; use Others if the merchant is unknown.
        Return exactly one category for every id, using the record_categories tool.
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 5_000
        }
    }

    private val apiKey: String? = System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() }
    private val model: String =
        System.getenv("CATEGORY_AI_MODEL")?.takeIf { it.isNotBlank() } ?: "claude-haiku-5-5"

    fun classifyByRulesOnly(description: String): String? {
        for ((regex, category) in EXACT_BRAND_LOOKUP) {
            if (regex.containsMatchIn(description)) {
                return category
            }
        }

        return null
    }

    suspend fun classify(description: String): String =
        classifyBatch(listOf(description))[description] ?: "Others"

    suspend fun classifyBatch(descriptions: Collection<String>): Map<String, String> {
        val results = mutableMapOf<String, String>()
        val needsAi = mutableListOf<String>()

        for (description in descriptions.toSet()) {
            val ruleCategory = classifyByRulesOnly(description)
            if (ruleCategory != null) {
                results[description] = ruleCategory
            } else {
                needsAi.add(description)
            }
        }

        if (needsAi.isEmpty()) return results

        if (apiKey == null) {
            needsAi.forEach { results[it] = "Others" }
            return results
        }

        val chunkResults = coroutineScope {
            needsAi.chunked(BATCH_SIZE).map { chunk ->
                async {
                    val aiResults = try {
                        classifyWithAi(chunk)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn("AI category service failed for {} descriptions: {}", chunk.size, e.message)
                        emptyMap()
                    }
                    chunk.associateWith { aiResults[it] ?: "Others" }
                }
            }.awaitAll()
        }

        chunkResults.forEach { results.putAll(it) }
        return results
    }

    private suspend fun classifyWithAi(descriptions: List<String>): Map<String, String> {
        // Only the merchant/description text is sent: no user id, account or amount.
        val numberedList = descriptions
            .mapIndexed { index, description -> "$index: $description" }
            .joinToString("\n")

        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            putJsonObject("thinking") { put("type", "disabled") }
            put("system", SYSTEM_PROMPT)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "Categorise these transactions:\n$numberedList")
                }
            }
            putJsonArray("tools") {
                addJsonObject {
                    put("name", "record_categories")
                    put("description", "Record the category for each transaction id.")
                    putJsonObject("input_schema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("results") {
                                put("type", "array")
                                putJsonObject("items") {
                                    put("type", "object")
                                    putJsonObject("properties") {
                                        putJsonObject("id") { put("type", "integer") }
                                        putJsonObject("category") {
                                            put("type", "string")
                                            putJsonArray("enum") { AI_CATEGORIES.forEach { add(it) } }
                                        }
                                    }
                                    putJsonArray("required") { add("id"); add("category") }
                                }
                            }
                        }
                        putJsonArray("required") { add("results") }
                    }
                }
            }
            putJsonObject("tool_choice") {
                put("type", "tool")
                put("name", "record_categories")
            }
        }

        val response = client.post(ANTHROPIC_URL) {
            header("x-api-key", apiKey)
            header("anthropic-version", ANTHROPIC_VERSION)
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val responseText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Claude API returned ${response.status.value}: ${responseText.take(300)}")
        }

        val toolInput = json.parseToJsonElement(responseText).jsonObject["content"]
            ?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
            ?.get("input")?.jsonObject
            ?: error("Claude API response had no tool_use block")

        val results = mutableMapOf<String, String>()
        toolInput["results"]?.jsonArray?.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val index = item["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@forEach
            val category = item["category"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val description = descriptions.getOrNull(index) ?: return@forEach
            if (category in AI_CATEGORIES) {
                results[description] = category
            }
        }

        return results
    }
}