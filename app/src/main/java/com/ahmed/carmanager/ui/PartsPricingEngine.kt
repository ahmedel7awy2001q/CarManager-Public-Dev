package com.ahmed.carmanager.ui

import android.net.Uri
import com.ahmed.carmanager.data.local.model.VehicleEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

internal enum class PartsOfferAvailability(val label: String) {
    IN_STOCK("متوفر"),
    OUT_OF_STOCK("غير متوفر"),
    UNKNOWN("التوفر غير مؤكد")
}

internal enum class PartsVehicleMatch(val label: String) {
    VEHICLE_CATALOG("ضمن كتالوج سيارتك"),
    EXACT_GENERATION("مطابق لمسمى/جيل سيارتك"),
    MODEL_AND_YEAR("مطابق للموديل والسنة"),
    MODEL_FAMILY("مطابق لعائلة الموديل"),
    UNKNOWN("التوافق يحتاج مراجعة")
}

internal data class PartsPriceOffer(
    val providerId: String,
    val storeName: String,
    val title: String,
    val priceEgp: Double,
    val availability: PartsOfferAvailability,
    val checkedAt: Long,
    val sourceUrl: String,
    val sourceTypeLabel: String = "سعر ويب متحقق",
    val fitmentNote: String? = null,
    val vehicleMatch: PartsVehicleMatch = PartsVehicleMatch.UNKNOWN,
    val matchedVehicleAlias: String? = null,
    val fitmentConfidence: Int? = null,
    val fitmentReason: String? = null,
    val fitmentSignals: List<String> = emptyList()
)

internal data class PartsPriceSearchResult(
    val requestedPart: String = "",
    val offers: List<PartsPriceOffer> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val checkedAt: Long? = null
)

internal data class PartsProviderCapability(
    val id: String,
    val name: String,
    val homeUrl: String,
    val priceInApp: Boolean,
    val stockInApp: Boolean,
    val description: String
)

/**
 * Core price providers used by the unified parts-search orchestrator.
 *
 * The selected VehicleEntity is always converted to VehicleMarketIdentity before querying a store.
 * The user therefore types the part only; make/model/year/local store aliases are generated here.
 */
internal object PartsPriceEngine {
    const val AUTO_SPARE_ID = "autospare-eg"
    const val TAWFIQIA_ID = "tawfiqia-eg"
    const val ZAIT_FILTERS_ID = "zait-filters-eg"
    const val FIT_FIX_ID = "fit-fix-eg"
    const val ESTERAAD_ID = "esteraad-gdeed-eg"
    const val FETEHA_ID = "feteha-bros-eg"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Mobile Safari/537.36 CarManager/1.2"
    private const val CONNECT_TIMEOUT_MS = 3_500
    private const val READ_TIMEOUT_MS = 4_500
    private const val MAX_PROVIDER_QUERIES = 2

    val builtInCapabilities: List<PartsProviderCapability> = listOf(
        PartsProviderCapability(
            AUTO_SPARE_ID,
            "Auto Spare",
            "https://autospare.com.eg/",
            priceInApp = true,
            stockInApp = true,
            description = "بحث مباشر في كتالوج السيارة مع فهم مسميات السوق والمتجر"
        ),
        PartsProviderCapability(
            TAWFIQIA_ID,
            "Tawfiqia",
            "https://tawfiqia.com/ar/shop",
            priceInApp = true,
            stockInApp = true,
            description = "كتالوج ويب يبحث تلقائيًا بمسميات السيارة المحفوظة"
        ),
        PartsProviderCapability(
            ZAIT_FILTERS_ID,
            "Zait & Filters",
            "https://zaitandfilters.com/store",
            priceInApp = true,
            stockInApp = true,
            description = "متجر مصري بصفحات منتجات وأسعار فعلية؛ يتحقق التطبيق من السعر والتوافق قبل العرض"
        ),
        PartsProviderCapability(
            FIT_FIX_ID,
            "Fit & Fix",
            "https://www.fitandfix.com/ar",
            priceInApp = false,
            stockInApp = false,
            description = "متجر خارجي للإطارات والبطاريات والزيوت وقطع الغيار"
        ),
        PartsProviderCapability(
            ESTERAAD_ID,
            "إستيراد و جديد",
            "https://www.esteraadwegdeed.com/",
            priceInApp = false,
            stockInApp = false,
            description = "متجر قطع غيار أصلية؛ البحث يفتح الموقع مباشرة"
        ),
        PartsProviderCapability(
            FETEHA_ID,
            "فتيحة إخوان",
            "https://fetehabross.com/",
            priceInApp = false,
            stockInApp = false,
            description = "مصدر متخصص؛ الأسعار داخل التطبيق غير مفعلة دون واجهة موثوقة"
        )
    )

    suspend fun search(
        vehicle: VehicleEntity,
        rawPart: String,
        enabledProviderIds: Set<String>,
        identity: VehicleMarketIdentity = VehicleMarketIdentityResolver.resolve(vehicle)
    ): PartsPriceSearchResult = withContext(Dispatchers.IO) {
        val part = identity.sanitizePartQuery(cleanPartName(rawPart)).trim()
        if (part.isBlank()) {
            return@withContext PartsPriceSearchResult(
                requestedPart = rawPart,
                errors = mapOf("search" to "اكتب اسم القطعة أو رقم OEM أولًا."),
                checkedAt = System.currentTimeMillis()
            )
        }

        val providerResults = coroutineScope {
            val autoSpare = async {
                if (AUTO_SPARE_ID !in enabledProviderIds) Result.success(emptyList<PartsPriceOffer>())
                else providerResult { AutoSpareUnifiedAdapter.search(vehicle, part, identity) }
            }
            val tawfiqia = async {
                if (TAWFIQIA_ID !in enabledProviderIds) Result.success(emptyList<PartsPriceOffer>())
                else providerResult { fetchTawfiqia(vehicle, part, identity) }
            }
            val zait = async {
                if (ZAIT_FILTERS_ID !in enabledProviderIds) Result.success(emptyList<PartsPriceOffer>())
                else providerResult { fetchZaitAndFilters(vehicle, part, identity) }
            }
            listOf(
                AUTO_SPARE_ID to autoSpare.await(),
                TAWFIQIA_ID to tawfiqia.await(),
                ZAIT_FILTERS_ID to zait.await()
            )
        }

        val offers = mutableListOf<PartsPriceOffer>()
        val errors = linkedMapOf<String, String>()
        providerResults.forEach { (providerId, result) ->
            result.onSuccess { offers += it }
                .onFailure { errors[providerId] = friendlyNetworkError(it) }
        }

        PartsPriceSearchResult(
            requestedPart = part,
            offers = offers
                .distinctBy { "${it.providerId}|${normalizeForMatch(it.title)}|${it.priceEgp.toLong()}|${it.sourceUrl}" }
                .sortedWith(
                    compareBy<PartsPriceOffer> {
                        when (it.vehicleMatch) {
                            PartsVehicleMatch.VEHICLE_CATALOG -> 0
                            PartsVehicleMatch.EXACT_GENERATION -> 1
                            PartsVehicleMatch.MODEL_AND_YEAR -> 2
                            PartsVehicleMatch.MODEL_FAMILY -> 3
                            PartsVehicleMatch.UNKNOWN -> 4
                        }
                    }.thenBy {
                        when (it.availability) {
                            PartsOfferAvailability.IN_STOCK -> 0
                            PartsOfferAvailability.UNKNOWN -> 1
                            PartsOfferAvailability.OUT_OF_STOCK -> 2
                        }
                    }.thenBy { it.priceEgp }
                ),
            errors = errors,
            checkedAt = System.currentTimeMillis()
        )
    }

    fun providerSearchUrl(
        providerId: String,
        vehicle: VehicleEntity,
        rawPart: String,
        identity: VehicleMarketIdentity = VehicleMarketIdentityResolver.resolve(vehicle)
    ): String? {
        val part = identity.sanitizePartQuery(cleanPartName(rawPart)).trim()
        return when (providerId) {
            AUTO_SPARE_ID -> AutoSpareUnifiedAdapter.providerLandingUrl(vehicle)
            TAWFIQIA_ID -> tawfiqiaSearchUrl(part, identity)
            ZAIT_FILTERS_ID -> zaitAndFiltersSearchUrl(part, identity)
            FIT_FIX_ID -> "https://www.fitandfix.com/ar"
            ESTERAAD_ID -> "https://www.esteraadwegdeed.com/"
            FETEHA_ID -> "https://fetehabross.com/"
            else -> null
        }
    }

    fun broadSearchText(
        vehicle: VehicleEntity,
        rawPart: String,
        identity: VehicleMarketIdentity = VehicleMarketIdentityResolver.resolve(vehicle)
    ): String {
        val part = identity.sanitizePartQuery(cleanPartName(rawPart))
        return identity.searchQueries(part, maxQueries = 1).firstOrNull()
            ?: listOf(part, identity.canonicalName).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun fetchTawfiqia(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity
    ): List<PartsPriceOffer> = fetchSearchProvider(
        vehicle = vehicle,
        part = part,
        identity = identity,
        providerId = TAWFIQIA_ID,
        storeName = "Tawfiqia",
        productPathHints = listOf("/product-detail/", "/product/"),
        urlForQuery = { query -> "https://tawfiqia.com/ar/shop?search=${Uri.encode(query)}" }
    )

    private fun fetchZaitAndFilters(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity
    ): List<PartsPriceOffer> = fetchSearchProvider(
        vehicle = vehicle,
        part = part,
        identity = identity,
        providerId = ZAIT_FILTERS_ID,
        storeName = "Zait & Filters",
        productPathHints = listOf("/products/", "/product/"),
        urlForQuery = { query -> "https://zaitandfilters.com/store?search=${Uri.encode(query)}" }
    )

    private fun fetchSearchProvider(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity,
        providerId: String,
        storeName: String,
        productPathHints: List<String>,
        urlForQuery: (String) -> String
    ): List<PartsPriceOffer> {
        val queries = identity.searchQueries(part, maxQueries = MAX_PROVIDER_QUERIES)
            .ifEmpty { listOf(listOf(part, identity.canonicalName).filter { it.isNotBlank() }.joinToString(" ")) }
        val wanted = matchTokens(part)
        val all = mutableListOf<PartsPriceOffer>()
        var lastFailure: Throwable? = null

        for (query in queries.take(MAX_PROVIDER_QUERIES)) {
            val url = urlForQuery(query)
            runCatching { httpGet(url) }
                .onSuccess { html ->
                    val parsed = parseProductAnchors(
                        html = html,
                        pageUrl = url,
                        providerId = providerId,
                        storeName = storeName,
                        productPathHints = productPathHints,
                        partTokens = wanted,
                        fitmentNote = "نتيجة بحث مرتبطة تلقائيًا بسيارتك؛ طابق OEM والمواصفات قبل الشراء."
                    )
                    all += parsed.mapNotNull { offer ->
                        val match = identity.classifyProductTitle(offer.title)
                        if (!identity.acceptsStrict(match)) null
                        else offer.copy(
                            vehicleMatch = match.level,
                            matchedVehicleAlias = match.matchedAlias,
                            fitmentNote = "النتيجة اجتازت فلتر السيارة المحفوظة (${identity.canonicalName}). راجع OEM قبل الشراء."
                        )
                    }
                }
                .onFailure { lastFailure = it }
            if (all.size >= 12) break
        }

        if (all.isEmpty()) lastFailure?.let { throw it }
        return all.distinctBy { "${normalizeForMatch(it.title)}|${it.priceEgp.toLong()}|${it.sourceUrl}" }.take(20)
    }

    private fun tawfiqiaSearchUrl(part: String, identity: VehicleMarketIdentity): String {
        val q = identity.searchQueries(part, maxQueries = 1).firstOrNull()
            ?: listOf(part, identity.canonicalName).filter { it.isNotBlank() }.joinToString(" ")
        return "https://tawfiqia.com/ar/shop?search=${Uri.encode(q)}"
    }

    private fun zaitAndFiltersSearchUrl(part: String, identity: VehicleMarketIdentity): String {
        val q = identity.searchQueries(part, maxQueries = 1).firstOrNull()
            ?: listOf(part, identity.canonicalName).filter { it.isNotBlank() }.joinToString(" ")
        return "https://zaitandfilters.com/store?search=${Uri.encode(q)}"
    }

    private fun parseProductAnchors(
        html: String,
        pageUrl: String,
        providerId: String,
        storeName: String,
        productPathHints: List<String>,
        partTokens: Set<String>,
        fitmentNote: String
    ): List<PartsPriceOffer> {
        if (html.isBlank()) return emptyList()
        val result = mutableListOf<PartsPriceOffer>()
        val anchorRegex = Regex("""(?is)<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""")

        for (match in anchorRegex.findAll(html)) {
            val href = decodeHtml(match.groupValues[1]).trim()
            if (href.isBlank() || productPathHints.none { href.contains(it, ignoreCase = true) }) continue
            val from = (match.range.first - 240).coerceAtLeast(0)
            val to = min(html.length, match.range.last + 1900)
            val snippet = html.substring(from, to)
            val anchorText = htmlToText(match.groupValues[2])
            val headingText = Regex("""(?is)<h[1-6][^>]*>(.*?)</h[1-6]>""")
                .find(snippet)?.groupValues?.getOrNull(1)?.let(::htmlToText).orEmpty()
            val imageAlt = Regex("""(?is)\balt\s*=\s*["']([^"']{3,220})["']""")
                .find(snippet)?.groupValues?.getOrNull(1)?.let(::decodeHtml).orEmpty()
            val candidates = listOf(anchorText, headingText, imageAlt)
                .map(::cleanTitle)
                .filter { it.length in 3..240 }
            val title = candidates.maxByOrNull { matchScore(it, partTokens) } ?: continue
            if (partTokens.isNotEmpty() && matchScore(title, partTokens) <= 0) continue
            val price = parsePrice(snippet) ?: continue
            val plain = htmlToText(snippet)
            val availability = when {
                listOf("غير متوفر", "نفذت الكمية", "out of stock").any { plain.contains(it, ignoreCase = true) } -> PartsOfferAvailability.OUT_OF_STOCK
                listOf("أضف", "اضف", "متوفر", "in stock").any { plain.contains(it, ignoreCase = true) } -> PartsOfferAvailability.IN_STOCK
                else -> PartsOfferAvailability.UNKNOWN
            }
            result += PartsPriceOffer(
                providerId = providerId,
                storeName = storeName,
                title = title,
                priceEgp = price,
                availability = availability,
                checkedAt = System.currentTimeMillis(),
                sourceUrl = absoluteUrl(pageUrl, href),
                fitmentNote = fitmentNote
            )
        }

        // Some storefronts render the visible product title outside the product anchor.
        if (result.isEmpty()) {
            val headingRegex = Regex("""(?is)<h[1-6][^>]*>(.*?)</h[1-6]>""")
            for (heading in headingRegex.findAll(html)) {
                val title = cleanTitle(htmlToText(heading.groupValues[1]))
                if (title.length !in 3..240 || matchScore(title, partTokens) <= 0) continue
                val end = min(html.length, heading.range.last + 1000)
                val snippet = html.substring(heading.range.first, end)
                val price = parsePrice(snippet) ?: continue
                result += PartsPriceOffer(
                    providerId = providerId,
                    storeName = storeName,
                    title = title,
                    priceEgp = price,
                    availability = PartsOfferAvailability.UNKNOWN,
                    checkedAt = System.currentTimeMillis(),
                    sourceUrl = pageUrl,
                    fitmentNote = fitmentNote
                )
            }
        }
        return result
    }

    private fun parsePrice(htmlSnippet: String): Double? {
        val plain = htmlToText(htmlSnippet)
        val patterns = listOf(
            Regex("""(?i)(?:EGP|LE)\s*([0-9][0-9,\s]*(?:\.[0-9]+)?)"""),
            Regex("""([0-9][0-9,\s]*(?:\.[0-9]+)?)\s*(?:جنيه|جنية|ج\.م)""", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { pattern ->
            pattern.findAll(plain).forEach { match ->
                val value = match.groupValues[1].replace(",", "").replace(" ", "").toDoubleOrNull()
                if (value != null && value in 10.0..10_000_000.0) return value
            }
        }
        return null
    }

    private fun cleanPartName(raw: String): String {
        val normalized = PartQueryText.normalizeSeparators(raw)
        val stop = setOf(
            "تغيير", "استبدال", "فحص", "خدمة", "دورية", "الدورية",
            "السيارة", "للسيارة", "استخدام", "شاق"
        ).mapTo(linkedSetOf(), ::normalizeForMatch)
        return normalized.split(Regex("\\s+"))
            .filter { it.isNotBlank() && normalizeForMatch(it) !in stop }
            .joinToString(" ")
            .trim()
    }

    private fun matchTokens(text: String): Set<String> {
        val generic = setOf("طقم", "قطعه", "قطعة", "اصلي", "اصلى", "original")
        return normalizeForMatch(text)
            .split(Regex("[^\\p{L}\\p{N}-]+"))
            .map(::stripArabicArticle)
            .filter { it.length >= 2 && it !in generic }
            .toSet()
    }

    private fun matchScore(title: String, wanted: Set<String>): Int {
        if (wanted.isEmpty()) return 1
        val titleTokens = normalizeForMatch(title)
            .split(Regex("[^\\p{L}\\p{N}-]+"))
            .map(::stripArabicArticle)
            .toSet()
        return wanted.count { wantedToken ->
            wantedToken in titleTokens || titleTokens.any { token -> token.contains(wantedToken) || wantedToken.contains(token) }
        }
    }

    private fun stripArabicArticle(token: String): String =
        if (token.startsWith("ال") && token.length > 4) token.removePrefix("ال") else token

    private fun normalizeForMatch(text: String): String {
        val noDiacritics = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return noDiacritics.lowercase(Locale.ROOT)
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
            .replace(Regex("\\s+"), " ").trim()
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "ar-EG,ar;q=0.9,en;q=0.7")
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun absoluteUrl(pageUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val page = URL(pageUrl)
        return when {
            href.startsWith("/") -> "${page.protocol}://${page.host}$href"
            else -> "${page.protocol}://${page.host}/${href.trimStart('/')}"
        }
    }

    private fun cleanTitle(value: String): String =
        value.replace(Regex("\\s+"), " ").trim().removePrefix("Image:").trim()

    private fun htmlToText(html: String): String = decodeHtml(
        html.replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(?:div|p|li|h[1-6])>"), "\n")
            .replace(Regex("(?s)<[^>]+>"), " ")
    ).replace(Regex("[\\t\\r ]+"), " ").replace(Regex("\\n\\s+"), "\n").trim()

    private fun decodeHtml(value: String): String {
        var out = value
            .replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
        out = Regex("&#(\\d+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { code -> runCatching { code.toChar().toString() }.getOrDefault(m.value) } ?: m.value
        }
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { code -> runCatching { code.toChar().toString() }.getOrDefault(m.value) } ?: m.value
        }
        return out
    }

    private inline fun <T> providerResult(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private fun friendlyNetworkError(t: Throwable): String = when (t) {
        is IllegalArgumentException -> t.message ?: "المصدر لم يجد مسمى مناسبًا لهذه المركبة حاليًا."
        else -> "تعذر جلب الأسعار من هذا المصدر الآن. يمكنك فتح المتجر مباشرة."
    }
}
