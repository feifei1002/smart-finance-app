package com.smart_finance_app.server

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable

@Serializable
private data class MLRequest(val description: String)

@Serializable
private data class MLResponse(val category: String)

object CategoryServiceClient {

    // =====================================================================
    // TIER 1: HIGH-PRECISION REGEX & BRAND RULE ENGINE (< 1ms Execution)
    // =====================================================================
    private val EXACT_BRAND_LOOKUP = listOf(
        Regex("\\b(UBER EATS|DELIVEROO|JUST EAT|HAPPY MYMENU|PIZZA EXPRESS|ASDA EXPRESS|TESCO EXPRESS|SAINSBURY'S LOCAL)\\b", RegexOption.IGNORE_CASE) to "Food & Dining",
        Regex("\\b(TESCO|SAINSBURY|SAINSBURY'S|MARKS & SPENCER|MARKS&SPENCER|M&S|WAITROSE|MORRISONS|ALDI|LIDL|COSTCO|ASDA|SPAR|WHOLE FOODS|TAZAKI|CHUANGLEE|OSEYO|WANA HONG|WANAHONG|TIAN TIAN|COSTA|CAFFE NERO|GAIL|GAIL'S|PRET|PRET A MANGER|STARBUCKS|MCDONALD|KFC|WASABI|SHORYU|HAIDILAO|FLAT IRON|LOUNGERS|CHINATOWN BAKERY|MILLE|SHAKE SHACK|WINGSTOP|BURGER & LOBSTER|PAUL|EAT TOKYO|KINEYA|KIKI & MIUMIU|LA MARITXU|DOUGLAS BAKERY|BENTO|RAMEN|SUSHI|BAKERY|BISTRO|PUB|BAR|TAVERN|RESTAURANT|CAFÉ|CAFE|COFFEE|SUPERMARKET|GROCERY|GROCERIES|CASH & CARRY|CASH AND CARRY|RESTORANAS|KRCMA)\\b", RegexOption.IGNORE_CASE) to "Food & Dining",
        Regex("\\b(TFL|TRANSPORT FOR LONDON|TRANSPORT FOR WALES|EMT MADRID|RATP|TRAINLINE|RAILCARD|GRAB|FLIXBUS|REGIOJET|ARRIVA|BEE NETWORK|BKK|DOPRAVNI PODNIK|JUDU|LUX EXPRESS|DUBLIN EXPRESS|POSTAJA|TALLINNA|AVTOBUSNA|TRAINPAL|NEXTBIKE|UBER(?! EATS)|BOLT|METRO|BUS|BUSES|TRAIN|RAILWAY|RAIL|AIRLINE|AIRWAYS?|FLIGHT|TAXI|CAB|PARKING|TRANSIT|STATION)\\b", RegexOption.IGNORE_CASE) to "Transportation",
        Regex("\\b(JOHN LEWIS|SELFRIDGES|PRIMARK|HOLLAND & BARRETT|HOLLAND AND BARRETT|TK MAXX|AMAZON|SHEIN|PULL&BEAR|PULL AND BEAR|JELLYCAT|MOSS BROS|WHSMITH|BOOTS|WATERSTONES|MUJI|H&M|DR\\.MAX|ZEEMAN|UNIQLO|ZARA|ASOS|BACK MARKET|VISION EXPRESS|FREE PRINTS|SEA BARBER|SEABARBER|MOOMIN|SIZE\\?|MAC STRATFORD|PHARMACY|BARBER|SALON)\\b", RegexOption.IGNORE_CASE) to "Shopping & Personal",
        Regex("\\b(LEBARA|VOXI|OCTOPUS ENERGY|ROYAL GREENWICH|LOVESPACE|SAFESTORE|AIRWALLET|EDF|(?!THE )O2(?! VENUE)|EE|VODAFONE|BRITISH GAS|UTILITIES|COUNCIL|RENT|MOBILE|TELECOM)\\b", RegexOption.IGNORE_CASE) to "Bills & Housing",
        Regex("\\b(NETFLIX|SPOTIFY|BOOKING\\.COM|AGODA|TRIP\\.COM|NORDVPN|EXPRESSVPN|OPENAI|ANTHROPIC|GITHUB|LINKEDIN|UDEMY|THE GYM GROUP|GLL BETTER|TRADING 212|GOOGLE PLAY|NINTENDO|AXS TICKETS|TICKETMASTER|ODEON|CINEWORLD|VUE|SHOWCASE|VICTORIA AND ALBERT MUSEUM|NATIONAL MARITIME MUSEUM|MUSEUM|CASTLE|CATHEDRAL|PALACE|GUINNESS STOREHOUSE|LIVRARIA LELLO|CONWY VISITORS|AIRBNB|HILTON|IBIS|TRAVELODGE|HOTEL|HOSTEL|GUESTHOUSE|ZOO|COLOSSEUM|BLUETICKET|JEGYMESTER)\\b", RegexOption.IGNORE_CASE) to "Entertainment & Subscriptions"
    )

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    private val mlServiceUrl = System.getenv("ML_SERVICE_URL") ?: "http://localhost:8000/classify/"

    suspend fun classify(description: String): String {
        // Step 1: Try the fast native Regex match
        for ((regex, category) in EXACT_BRAND_LOOKUP) {
            if (regex.containsMatchIn(description)) {
                return category
            }
        }

        // =====================================================================
        // TIER 2: DEBERTA ML MICROSERVICE FALLBACK
        // =====================================================================
        return try {
            val response: MLResponse = client.post(mlServiceUrl) {
                contentType(ContentType.Application.Json)
                setBody(MLRequest(description))
            }.body()

            response.category
        } catch (e: Exception) {
            // STOP SILENTLY FAILING: Print the exact reason to your backend terminal
            println("❌ ML Service Error for description '$description': ${e.message}")
            e.printStackTrace()

            "Miscellaneous" // Fallback category on failure
        }
    }
}
