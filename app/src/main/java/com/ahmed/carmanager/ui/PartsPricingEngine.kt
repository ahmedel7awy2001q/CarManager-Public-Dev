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

internal object PartsPriceEngine {
    const val AUTO_SPARE_ID = "autospare-eg"
    const val TAWFIQIA_ID = "tawfiqia-eg"
    const val ZAIT_FILTERS_ID = "zait-filters-eg"
    const val FIT_FIX_ID = "fit-fix-eg"
    const val ESTERAAD_ID = "esteraad-gdeed-eg"
    const val FETEHA_ID = "feteha-bros-eg"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Mobile Safari/537.36 CarManager/0.9"

    // Storefront HTML scraping must never dominate the user-visible search latency. Sources still
    // run concurrently; these budgets cap the sequential work *inside* each legacy provider.
    private const val CONNECT_TIMEOUT_MS = 3_500
    private const val READ_TIMEOUT_MS = 4_500
    private const val MAX_AUTO_SPARE_PAGES = 2
    private const val MAX_TAWFIQIA_QUERIES = 2

    val builtInCapabilities: List<PartsProviderCapability> = listOf(
        PartsProviderCapability(
            AUTO_SPARE_ID,
            "Auto Spare",
            "https://autospare.com.eg/",
            priceInApp = true,
            stockInApp = true,
            description = "كتالوج ويب يمكن التحقق منه داخل التطبيق"
        ),
        PartsProviderCapability(
            TAWFIQIA_ID,
            "Tawfiqia",
            "https://tawfiqia.com/ar/shop",
            priceInApp = true,
            stockInApp = true,
            description = "كتالوج ويب يمكن التحقق منه داخل التطبيق"
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
        val part = cleanPartName(rawPart)
        if (part.isBlank()) return@withContext PartsPriceSearchResult(
            requestedPart = rawPart,
            errors = mapOf("search" to "اكتب اسم القطعة أولًا.")
        )

        val offers = mutableListOf<PartsPriceOffer>()
        val errors = linkedMapOf<String, String>()

        // Independent stores are queried concurrently so adding useful sources does not make
        // the user wait for each network timeout in sequence.
        val providerResults = coroutineScope {
            val autoSpare = async {
                if (AUTO_SPARE_ID !in enabledProviderIds) Result.success(emptyList<PartsPriceOffer>())
                else providerResult { fetchAutoSpare(vehicle, part, identity) }
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
        providerResults.forEach { (providerId, result) ->
            result.onSuccess { offers += it }
                .onFailure { errors[providerId] = friendlyNetworkError(it) }
        }

        val now = System.currentTimeMillis()
        PartsPriceSearchResult(
            requestedPart = part,
            offers = offers
                .distinctBy { "${it.providerId}|${normalizeForMatch(it.title)}|${it.priceEgp.toLong()}|${it.sourceUrl}" }
                .sortedWith(
                    compareBy<PartsPriceOffer> {
                        when (it.availability) {
                            PartsOfferAvailability.IN_STOCK -> 0
                            PartsOfferAvailability.UNKNOWN -> 1
                            PartsOfferAvailability.OUT_OF_STOCK -> 2
                        }
                    }.thenBy { it.priceEgp }
                ),
            errors = errors,
            checkedAt = now
        )
    }

    fun providerSearchUrl(
        providerId: String,
        vehicle: VehicleEntity,
        rawPart: String,
        identity: VehicleMarketIdentity = VehicleMarketIdentityResolver.resolve(vehicle)
    ): String? {
        val part = cleanPartName(rawPart)
        return when (providerId) {
            AUTO_SPARE_ID -> autoSpareCatalogUrl(vehicle, page = 1)
                ?: "https://autospare.com.eg/"
            TAWFIQIA_ID -> tawfiqiaSearchUrl(vehicle, part, identity)
            ZAIT_FILTERS_ID -> zaitAndFiltersSearchUrl(vehicle, part, identity)
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

    private fun fetchAutoSpare(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity
    ): List<PartsPriceOffer> {
        autoSpareCatalogUrl(vehicle, 1)
            ?: throw IllegalArgumentException("موديل المركبة غير مهيأ لكتالوج Auto Spare داخل التطبيق.")
        val tokens = matchTokens(part)
        val all = mutableListOf<PartsPriceOffer>()

        // Prefer a model-specific category page when its route is known. This avoids the old
        // over-specific Google site search and gives much better recall for filters/brakes/suspension.
        autoSpareCategoryUrl(vehicle, part)?.let { categoryUrl ->
            runCatching { httpGet(categoryUrl) }.getOrNull()?.let { html ->
                all += parseProductAnchors(
                    html = html,
                    pageUrl = categoryUrl,
                    providerId = AUTO_SPARE_ID,
                    storeName = "Auto Spare",
                    productPathHint = "/products/",
                    partTokens = tokens,
                    fitmentNote = "النتيجة من كتالوج موديل المتجر؛ طابق رقم OEM والمواصفات قبل الشراء."
                )
            }
        }

        if (all.isEmpty()) {
            for (page in 1..MAX_AUTO_SPARE_PAGES) {
                val url = autoSpareCatalogUrl(vehicle, page) ?: break
                val html = httpGet(url)
                all += parseProductAnchors(
                    html = html,
                    pageUrl = url,
                    providerId = AUTO_SPARE_ID,
                    storeName = "Auto Spare",
                    productPathHint = "/products/",
                    partTokens = tokens,
                    fitmentNote = "النتيجة من كتالوج موديل المتجر؛ طابق رقم OEM والمواصفات قبل الشراء."
                )
                if (all.size >= 12) break
                if (page == 1 && !looksPaginated(html)) break
            }
        }
        return all.distinctBy { "${normalizeForMatch(it.title)}|${it.priceEgp}|${it.sourceUrl}" }
            .take(20)
            .map { offer ->
                offer.copy(
                    vehicleMatch = PartsVehicleMatch.VEHICLE_CATALOG,
                    matchedVehicleAlias = identity.displayAliases.firstOrNull(),
                    fitmentNote = "النتيجة من كتالوج السيارة المحدد في المتجر. راجع رقم OEM قبل الشراء."
                )
            }
    }

    private fun fetchTawfiqia(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity
    ): List<PartsPriceOffer> {
        val queries = identity.searchQueries(part, maxQueries = MAX_TAWFIQIA_QUERIES)

        val tokens = matchTokens(part)
        val all = mutableListOf<PartsPriceOffer>()
        var lastFailure: Throwable? = null

        for (query in queries.take(MAX_TAWFIQIA_QUERIES)) {
            val url = "https://tawfiqia.com/ar/shop?search=${Uri.encode(query)}"
            runCatching { httpGet(url) }
                .onSuccess { html ->
                    val accepted = parseProductAnchors(
                        html = html,
                        pageUrl = url,
                        providerId = TAWFIQIA_ID,
                        storeName = "Tawfiqia",
                        productPathHint = "/product-detail/",
                        partTokens = tokens,
                        fitmentNote = "توافق المتجر إرشادي؛ طابق رقم القطعة/الموديل قبل الشراء."
                    ).mapNotNull { offer ->
                        val match = identity.classifyProductTitle(offer.title)
                        if (!identity.acceptsStrict(match)) null
                        else offer.copy(
                            vehicleMatch = match.level,
                            matchedVehicleAlias = match.matchedAlias,
                            fitmentNote = "النتيجة اجتازت فلتر السيارة المحفوظة (${identity.canonicalName}). راجع OEM قبل الشراء."
                        )
                    }
                    all += accepted
                }
                .onFailure { lastFailure = it }
            if (all.isNotEmpty()) break
        }

        if (all.isEmpty()) lastFailure?.let { throw it }
        return all
            .distinctBy { "${normalizeForMatch(it.title)}|${it.priceEgp}|${it.sourceUrl}" }
            .take(20)
    }

    private fun fetchZaitAndFilters(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity
    ): List<PartsPriceOffer> {
        val wanted = matchTokens(part)
        val urls = linkedSetOf<String>()
        zaitAndFiltersVehicleUrl(vehicle)?.let(urls::add)
        urls += zaitAndFiltersSearchUrl(vehicle, part, identity)
        val out = mutableListOf<PartsPriceOffer>()
        var lastFailure: Throwable? = null
        for (url in urls) {
            runCatching { httpGet(url) }
                .onSuccess { html ->
                    val accepted = parseProductAnchors(
                        html = html,
                        pageUrl = url,
                        providerId = ZAIT_FILTERS_ID,
                        storeName = "Zait & Filters",
                        productPathHint = "/products/",
                        partTokens = wanted,
                        fitmentNote = "سعر مباشر من صفحة منتج/كتالوج زيت أند فلترز؛ راجع OEM قبل الشراء."
                    ).mapNotNull { offer ->
                        val match = identity.classifyProductTitle(offer.title)
                        if (!identity.acceptsStrict(match)) null else offer.copy(
                            vehicleMatch = match.level,
                            matchedVehicleAlias = match.matchedAlias,
                            fitmentNote = "مطابقة تلقائية مع ${identity.canonicalName}. راجع رقم OEM قبل الشراء."
                        )
                    }
                    out += accepted
                }
                .onFailure { lastFailure = it }
            if (out.isNotEmpty()) break
        }
        if (out.isEmpty()) lastFailure?.let { throw it }
        return out.distinctBy { "${normalizeForMatch(it.title)}|${it.priceEgp}|${it.sourceUrl}" }.take(24)
    }

    private fun parseProductAnchors(
        html: String,
        pageUrl: String,
        providerId: String,
        storeName: String,
        productPathHint: String,
        partTokens: Set<String>,
        fitmentNote: String
    ): List<PartsPriceOffer> {
        if (html.isBlank()) return emptyList()
        val result = mutableListOf<PartsPriceOffer>()
        val anchorRegex = Regex(
            """(?is)<a\b[^>]*href\s*=\s*["']([^"']*${Regex.escape(productPathHint)}[^"']*)["'][^>]*>(.*?)</a>"""
        )

        for (match in anchorRegex.findAll(html)) {
            val href = decodeHtml(match.groupValues[1]).trim()
            if (href.isBlank()) continue

            val from = (match.range.first - 220).coerceAtLeast(0)
            val to = min(html.length, match.range.last + 1800)
            val snippet = html.substring(from, to)
            val anchorText = htmlToText(match.groupValues[2])
            val headingText = Regex("""(?is)<h[1-6][^>]*>(.*?)</h[1-6]>""")
                .find(snippet)?.groupValues?.getOrNull(1)?.let(::htmlToText)
            val imageAlt = Regex("""(?is)\balt\s*=\s*["']([^"']{3,180})["']""")
                .find(snippet)?.groupValues?.getOrNull(1)?.let(::decodeHtml)

            val candidates = listOf(anchorText, headingText.orEmpty(), imageAlt.orEmpty())
                .map { cleanTitle(it) }
                .filter { it.length in 3..220 }

            val title = candidates.maxByOrNull { matchScore(it, partTokens) } ?: continue
            val score = matchScore(title, partTokens)
            if (partTokens.isNotEmpty() && score <= 0) continue

            val price = parsePrice(snippet) ?: continue
            val plain = htmlToText(snippet)
            val availability = when {
                plain.contains("غير متوفر", ignoreCase = true) ||
                    plain.contains("نفذت الكمية", ignoreCase = true) ||
                    plain.contains("out of stock", ignoreCase = true) ->
                    PartsOfferAvailability.OUT_OF_STOCK
                plain.contains("أضف", ignoreCase = true) ||
                    plain.contains("اضف", ignoreCase = true) ||
                    plain.contains("متوفر", ignoreCase = true) ||
                    plain.contains("in stock", ignoreCase = true) ->
                    PartsOfferAvailability.IN_STOCK
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

        // Some stores render product cards without wrapping the visible title in the product anchor.
        // Fallback: parse headings plus a nearby currency amount, linking to the catalog page itself.
        if (result.isEmpty()) {
            val headingRegex = Regex("""(?is)<h[1-6][^>]*>(.*?)</h[1-6]>""")
            for (heading in headingRegex.findAll(html)) {
                val title = cleanTitle(htmlToText(heading.groupValues[1]))
                if (title.length !in 3..220 || matchScore(title, partTokens) <= 0) continue
                val end = min(html.length, heading.range.last + 1000)
                val snippet = html.substring(heading.range.first, end)
                val price = parsePrice(snippet) ?: continue
                val plain = htmlToText(snippet)
                val availability = when {
                    plain.contains("غير متوفر", true) || plain.contains("نفذت الكمية", true) ->
                        PartsOfferAvailability.OUT_OF_STOCK
                    plain.contains("أضف", true) || plain.contains("اضف", true) || plain.contains("متوفر", true) ->
                        PartsOfferAvailability.IN_STOCK
                    else -> PartsOfferAvailability.UNKNOWN
                }
                result += PartsPriceOffer(
                    providerId = providerId,
                    storeName = storeName,
                    title = title,
                    priceEgp = price,
                    availability = availability,
                    checkedAt = System.currentTimeMillis(),
                    sourceUrl = pageUrl,
                    fitmentNote = fitmentNote
                )
            }
        }
        return result
    }

    private fun parsePrice(htmlSnippet: String): Double? {
        val patterns = listOf(
            Regex("""(?i)(?:EGP|LE)\s*([0-9][0-9,\s]*(?:\.[0-9]+)?)"""),
            Regex("""([0-9][0-9,\s]*(?:\.[0-9]+)?)\s*(?:جنيه|جنية|ج\.م)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            for (match in pattern.findAll(htmlToText(htmlSnippet))) {
                val value = match.groupValues[1].replace(",", "").replace(" ", "").toDoubleOrNull()
                if (value != null && value in 10.0..10_000_000.0) return value
            }
        }
        return null
    }

    private fun autoSpareCategoryUrl(vehicle: VehicleEntity, part: String): String? {
        val brand = normalizeForMatch(vehicle.brand)
        val model = normalizeForMatch(vehicle.model)
        val isKia = brand.contains("kia") || brand.contains("كيا")
        val isCerato = model.contains("cerato") || model.contains("سيراتو")
        if (!isKia || !isCerato) return null

        val modelSlug = when {
            vehicle.year in 2018..2022 -> "جراند-سيراتو"
            vehicle.year in 2014..2018 -> "k3"
            vehicle.year in 2009..2013 -> "سيراتو-td"
            vehicle.year in 2004..2009 -> "سيراتو"
            else -> return null
        }
        val normalized = normalizeForMatch(part)
        val category = when {
            normalized.contains("فلتر") -> "الفلاتر-1"
            listOf("تيل", "طنابير", "فرامل", "ماستر", "abs").any { normalized.contains(it) } -> "الفرامل"
            listOf("مساعد", "مقص", "جلب", "كوبلن", "قاعده", "تيش", "بارات", "عفشه", "بطاح").any { normalized.contains(it) } -> "العفشة"
            listOf("سير", "شداد", "بلي").any { normalized.contains(it) } -> "السيور والبلي"
            else -> return null
        }
        return "https://autospare.com.eg/parts/${Uri.encode("كيا")}/${Uri.encode(modelSlug)}/${Uri.encode(category)}"
    }

    private fun autoSpareCatalogUrl(vehicle: VehicleEntity, page: Int): String? {
        val brand = normalizeForMatch(vehicle.brand)
        val model = normalizeForMatch(vehicle.model)
        val isKia = brand.contains("kia") || brand.contains("كيا")
        val isCerato = model.contains("cerato") || model.contains("سيراتو")
        if (!isKia || !isCerato) return null

        val slug = when {
            vehicle.year in 2018..2022 -> "جراند-سيراتو"
            vehicle.year in 2014..2018 -> "k3"
            vehicle.year in 2009..2013 -> "سيراتو-td"
            vehicle.year in 2004..2009 -> "سيراتو"
            else -> return null
        }
        return "https://autospare.com.eg/brands/${Uri.encode("كيا")}/${Uri.encode(slug)}?page=$page"
    }

    private fun tawfiqiaSearchUrl(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity
    ): String {
        val clean = identity.sanitizePartQuery(cleanPartName(part))
        val q = identity.searchQueries(clean, maxQueries = 1).firstOrNull()
            ?: listOf(clean, identity.canonicalName).filter { it.isNotBlank() }.joinToString(" ")
        return "https://tawfiqia.com/ar/shop?search=${Uri.encode(q)}"
    }

    private fun zaitAndFiltersSearchUrl(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity
    ): String {
        // The filtered store URL is preferable for known vehicles; the search query remains a
        // fallback for parts that are not visible on the first filtered catalog page.
        val clean = identity.sanitizePartQuery(cleanPartName(part))
        val q = identity.searchQueries(clean, maxQueries = 1).firstOrNull()
            ?: listOf(clean, vehicleModelAlias(vehicle), vehicle.year.toString()).filter { it.isNotBlank() }.joinToString(" ")
        return "https://zaitandfilters.com/store?search=${Uri.encode(q)}"
    }

    private fun zaitAndFiltersVehicleUrl(vehicle: VehicleEntity): String? {
        val brand = normalizeForMatch(vehicle.brand)
        val model = normalizeForMatch(vehicle.model)
        if (!(brand.contains("kia") || brand.contains("كيا"))) return null
        if (!(model.contains("cerato") || model.contains("سيراتو"))) return null
        val modelValue = when {
            vehicle.year in 2018..2022 -> "GRAND CERATO"
            vehicle.year in 2014..2018 -> "CERATO K3"
            else -> return null
        }
        return "https://zaitandfilters.com/store?make=KIA&model=${Uri.encode(modelValue)}"
    }

    private fun vehicleModelAlias(vehicle: VehicleEntity): String {
        val brand = normalizeForMatch(vehicle.brand)
        val model = normalizeForMatch(vehicle.model)
        if ((brand.contains("kia") || brand.contains("كيا")) &&
            (model.contains("cerato") || model.contains("سيراتو"))
        ) {
            return when {
                vehicle.year in 2018..2022 -> "كيا جراند سيراتو"
                vehicle.year in 2014..2018 -> "كيا سيراتو K3"
                vehicle.year in 2009..2013 -> "كيا سيراتو TD"
                vehicle.year in 2004..2009 -> "كيا سيراتو LD"
                else -> "كيا سيراتو"
            }
        }
        return listOf(vehicle.brand, vehicle.model).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun shortModelAlias(vehicle: VehicleEntity): String {
        val full = vehicleModelAlias(vehicle)
        return full.replace(vehicle.brand, "", ignoreCase = true).trim().ifBlank { vehicle.model }
    }

    private fun cleanPartName(raw: String): String {
        val stop = setOf(
            "تغيير", "استبدال", "فحص", "خدمة", "دورية", "الدورية",
            "المحرك", "للمحرك", "السيارة", "للسيارة", "استخدام", "شاق"
        )
        val stopNormalized = stop.map { normalizeForMatch(it) }.toSet()
        return raw.replace("—", " ")
            .replace("-", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && normalizeForMatch(it) !in stopNormalized }
            .joinToString(" ")
            .trim()
    }

    private fun matchTokens(text: String): Set<String> {
        val generic = setOf("كيا", "kia", "سيراتو", "cerato", "جراند", "grand", "طقم", "قطعه", "قطعة")
        return normalizeForMatch(text)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { stripArabicArticle(it) }
            .filter { it.length >= 2 && it !in generic }
            .toSet()
    }

    private fun matchScore(title: String, wanted: Set<String>): Int {
        if (wanted.isEmpty()) return 1
        val titleTokens = normalizeForMatch(title)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { stripArabicArticle(it) }
            .toSet()
        return wanted.count { w ->
            w in titleTokens || titleTokens.any { t -> t.contains(w) || w.contains(t) }
        }
    }

    private fun stripArabicArticle(token: String): String =
        if (token.startsWith("ال") && token.length > 4) token.removePrefix("ال") else token

    private fun normalizeForMatch(text: String): String {
        val noDiacritics = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noDiacritics.lowercase(Locale.ROOT)
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .replace(Regex("\\s+"), " ")
            .trim()
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

    private fun looksPaginated(html: String): Boolean =
        html.contains("page=2", ignoreCase = true) || html.contains("التالي", ignoreCase = true)

    private fun absoluteUrl(pageUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val page = URL(pageUrl)
        return when {
            href.startsWith("/") -> "${page.protocol}://${page.host}$href"
            else -> "${page.protocol}://${page.host}/${href.trimStart('/')}"
        }
    }

    private fun cleanTitle(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()
            .removePrefix("Image:")
            .trim()

    private fun htmlToText(html: String): String =
        decodeHtml(
            html.replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
                .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</(?:div|p|li|h[1-6])>"), "\n")
                .replace(Regex("(?s)<[^>]+>"), " ")
        ).replace(Regex("[\\t\\r ]+"), " ")
            .replace(Regex("\\n\\s+"), "\n")
            .trim()

    private fun decodeHtml(value: String): String {
        var out = value
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        val decimal = Regex("&#(\\d+);")
        out = decimal.replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { code ->
                runCatching { code.toChar().toString() }.getOrDefault(m.value)
            } ?: m.value
        }
        val hex = Regex("&#x([0-9a-fA-F]+);")
        out = hex.replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { code ->
                runCatching { code.toChar().toString() }.getOrDefault(m.value)
            } ?: m.value
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
        is IllegalArgumentException -> t.message ?: "المصدر غير مهيأ لهذه المركبة."
        else -> "تعذر جلب الأسعار الآن. يمكنك فتح المتجر مباشرة."
    }
}
