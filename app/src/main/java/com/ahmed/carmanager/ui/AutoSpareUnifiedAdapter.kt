package com.ahmed.carmanager.ui

import android.net.Uri
import com.ahmed.carmanager.data.local.model.VehicleEntity
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Vehicle-aware Auto Spare adapter.
 *
 * The owner never needs to know Auto Spare's naming. We resolve the saved car to the store's live
 * brand/model catalog using all known Egyptian/store aliases, then search model/category pages.
 * No store alias is written back to Room and no fitment is claimed solely from a similar part name.
 */
internal object AutoSpareUnifiedAdapter {
    private const val BASE = "https://autospare.com.eg"
    private const val CONNECT_TIMEOUT_MS = 3_500
    private const val READ_TIMEOUT_MS = 4_500
    private const val ROUTE_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_DIRECT_ROUTE_PROBES = 5
    private const val MAX_MODEL_ROUTES = 2
    private const val MAX_PAGES_PER_ROUTE = 2
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Mobile Safari/537.36 CarManager/1.2"

    internal data class ResolvedModelRoute(
        val url: String,
        val displayAlias: String,
        val score: Int
    )

    private data class RouteCacheEntry(
        val resolvedAt: Long,
        val routes: List<ResolvedModelRoute>
    )

    private val routeCache = ConcurrentHashMap<String, RouteCacheEntry>()

    fun search(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity
    ): List<PartsPriceOffer> {
        val cleanPart = identity.sanitizePartQuery(PartQueryText.normalizeSeparators(part)).trim()
        if (cleanPart.isBlank()) return emptyList()

        val routes = resolveModelRoutes(vehicle, identity)
        if (routes.isEmpty()) {
            throw IllegalArgumentException("لم يتم العثور على مسمى مطابق للمركبة داخل كتالوج Auto Spare الحالي.")
        }

        val wanted = matchTokens(cleanPart)
        val offers = mutableListOf<PartsPriceOffer>()
        var lastFailure: Throwable? = null

        routes.take(MAX_MODEL_ROUTES).forEach { route ->
            val modelHtml = runCatching { httpGet(route.url) }
                .onFailure { lastFailure = it }
                .getOrNull() ?: return@forEach

            val categoryUrls = discoverCategoryUrls(modelHtml, route.url, cleanPart)
            val routeOffers = mutableListOf<PartsPriceOffer>()

            // 1) Strongest path: model-specific category exposed by the live store page.
            categoryUrls.take(2).forEach { categoryUrl ->
                val html = runCatching { httpGet(categoryUrl) }
                    .onFailure { lastFailure = it }
                    .getOrNull() ?: return@forEach
                routeOffers += parseProductAnchors(
                    html = html,
                    pageUrl = categoryUrl,
                    partTokens = wanted,
                    fitmentNote = "من كتالوج Auto Spare الخاص بمسمى ${route.displayAlias}; راجع OEM والمواصفات قبل الشراء."
                )
            }

            // 2) Model catalog fallback. Some store categories are incomplete or use another label.
            if (routeOffers.isEmpty()) {
                for (page in 1..MAX_PAGES_PER_ROUTE) {
                    val url = withPage(route.url, page)
                    val html = if (page == 1) modelHtml else runCatching { httpGet(url) }
                        .onFailure { lastFailure = it }
                        .getOrNull() ?: break
                    routeOffers += parseProductAnchors(
                        html = html,
                        pageUrl = url,
                        partTokens = wanted,
                        fitmentNote = "من كتالوج Auto Spare الخاص بمسمى ${route.displayAlias}; راجع OEM والمواصفات قبل الشراء."
                    )
                    if (routeOffers.size >= 12 || (page == 1 && !looksPaginated(html))) break
                }
            }

            // 3) Safe brand-category fallback. Auto Spare may list a part (notably spark plugs)
            // only under the brand category. These results are accepted only when their title
            // independently matches the selected vehicle identity; unrelated cars are discarded.
            if (routeOffers.isEmpty()) {
                brandCategoryFallbackUrls(route.url, cleanPart).take(2).forEach { categoryUrl ->
                    val html = runCatching { httpGet(categoryUrl) }
                        .onFailure { lastFailure = it }
                        .getOrNull() ?: return@forEach
                    val candidates = parseProductAnchors(
                        html = html,
                        pageUrl = categoryUrl,
                        partTokens = wanted,
                        fitmentNote = "من قسم الماركة في Auto Spare وتمت مطابقته مع السيارة المحفوظة؛ راجع OEM قبل الشراء."
                    )
                    routeOffers += candidates.mapNotNull { offer ->
                        val match = identity.classifyProductTitle(offer.title)
                        if (!identity.acceptsStrict(match)) null
                        else offer.copy(
                            vehicleMatch = match.level,
                            matchedVehicleAlias = match.matchedAlias,
                            fitmentNote = "النتيجة من قسم الماركة واجتازت فلتر هوية ${identity.canonicalName}. راجع OEM قبل الشراء."
                        )
                    }
                }
            }

            offers += routeOffers.map { offer ->
                if (offer.vehicleMatch != PartsVehicleMatch.UNKNOWN) offer
                else offer.copy(
                    vehicleMatch = PartsVehicleMatch.VEHICLE_CATALOG,
                    matchedVehicleAlias = route.displayAlias,
                    fitmentNote = "النتيجة من كتالوج Auto Spare المرتبط تلقائيًا بسيارتك باسم ${route.displayAlias}. راجع رقم OEM والمواصفات قبل الشراء."
                )
            }
        }

        if (offers.isEmpty()) lastFailure?.let { throw it }
        return offers
            .distinctBy { "${normalize(it.title)}|${it.priceEgp.toLong()}|${it.sourceUrl}" }
            .take(24)
    }

    /**
     * Safe landing link for the "open source" action. Known store aliases get a direct model route;
     * unknown cars fall back to Auto Spare's brands page rather than a wrong model URL.
     */
    fun providerLandingUrl(vehicle: VehicleEntity): String {
        val profiles = StorefrontVehicleAliasCatalog.providerProfiles(PartsPriceEngine.AUTO_SPARE_ID, vehicle)
        val strong = profiles.firstOrNull { profile ->
            profile.confidence >= 90 && (profile.compatibleYears == null || vehicle.year in profile.compatibleYears)
        }
        if (strong != null) {
            val brand = strong.brandAliases.firstOrNull { containsArabic(it) } ?: strong.brandAliases.firstOrNull()
            val model = strong.modelAliases.firstOrNull { containsArabic(it) } ?: strong.modelAliases.firstOrNull()
            if (!brand.isNullOrBlank() && !model.isNullOrBlank()) return modelRoute(brand, model)
        }
        return "$BASE/brands"
    }

    internal fun searchAliasesForStore(vehicle: VehicleEntity): List<String> =
        StorefrontVehicleAliasCatalog.providerProfiles(PartsPriceEngine.AUTO_SPARE_ID, vehicle)
            .filter { it.compatibleYears == null || vehicle.year in it.compatibleYears }
            .sortedByDescending { it.confidence }
            .flatMap { it.modelAliases }
            .distinctBy(::normalize)

    internal fun categoryIntent(part: String): Set<String> {
        val p = normalize(part)
        val result = linkedSetOf<String>()
        fun add(vararg values: String) { result += values.map(::normalize) }
        when {
            listOf("بوجيه", "شمعة", "شمعه", "موبينه", "موبينة", "كويل", "coil", "spark plug").any { p.contains(normalize(it)) } ->
                add("البوجيهات والمباين", "بوجيهات", "مباين", "اشعال", "إشعال")
            listOf("تيل", "طنابير", "فرامل", "ماستر", "abs", "brake").any { p.contains(normalize(it)) } ->
                add("الفرامل", "فرامل")
            listOf("فلتر", "filter").any { p.contains(normalize(it)) } ->
                add("الفلاتر", "فلتر")
            listOf("مساعد", "مقص", "جلب", "كوبلن", "تيش", "بارات", "عفشه", "عفشة", "بطاح", "shock", "suspension").any { p.contains(normalize(it)) } ->
                add("العفشة", "عفشه", "تعليق")
            listOf("سير", "شداد", "بلي", "كاتينه", "كاتينة", "belt", "timing").any { p.contains(normalize(it)) } ->
                add("السيور والبلي", "سيور", "بلي")
            listOf("ردياتير", "رادياتير", "ثرموستات", "طرمبه مياه", "طرمبة مياه", "تبريد", "cooling", "radiator").any { p.contains(normalize(it)) } ->
                add("دورة تبريد المحرك", "تبريد")
            listOf("تكييف", "كمبروسر", "سربنتينه", "سربنتينة", "ac", "air condition").any { p.contains(normalize(it)) } ->
                add("دورة التكييف", "تكييف")
            listOf("بنزين", "وقود", "طرمبه بنزين", "طرمبة بنزين", "fuel").any { p.contains(normalize(it)) } ->
                add("نظام الوقود", "وقود", "بنزين")
            listOf("فتيس", "دبرياج", "كلتش", "gearbox", "clutch", "transmission").any { p.contains(normalize(it)) } ->
                add("فتيس ودبرياج", "فتيس", "دبرياج")
            listOf("دينامو", "مارش", "فيوز", "كهرباء", "لمبه", "لمبة", "اضاءه", "إضاءة", "electric").any { p.contains(normalize(it)) } ->
                add("كهرباء وإضاءة", "كهرباء", "اضاءه")
            listOf("زيت", "سائل", "oil", "fluid").any { p.contains(normalize(it)) } ->
                add("الزيوت والسوائل", "زيوت", "سوائل")
            listOf("شكمان", "عادم", "exhaust").any { p.contains(normalize(it)) } ->
                add("نظام الشكمان", "شكمان", "عادم")
        }
        return result
    }

    private fun categoryRouteSlugs(part: String): List<String> {
        val p = normalize(part)
        return when {
            listOf("بوجيه", "شمعة", "شمعه", "موبينه", "موبينة", "كويل", "coil", "spark plug").any { p.contains(normalize(it)) } ->
                listOf("البوجيهات والمباين", "بوجيهات")
            listOf("تيل", "طنابير", "فرامل", "ماستر", "abs", "brake").any { p.contains(normalize(it)) } -> listOf("الفرامل")
            listOf("فلتر", "filter").any { p.contains(normalize(it)) } -> listOf("الفلاتر-1", "الفلاتر")
            listOf("مساعد", "مقص", "جلب", "كوبلن", "تيش", "بارات", "عفشه", "عفشة", "بطاح", "shock", "suspension").any { p.contains(normalize(it)) } -> listOf("العفشة")
            listOf("سير", "شداد", "بلي", "كاتينه", "كاتينة", "belt", "timing").any { p.contains(normalize(it)) } -> listOf("السيور-والبلي")
            listOf("ردياتير", "رادياتير", "ثرموستات", "طرمبه مياه", "طرمبة مياه", "تبريد", "cooling", "radiator").any { p.contains(normalize(it)) } -> listOf("دورة-تبريد-المحرك")
            listOf("تكييف", "كمبروسر", "سربنتينه", "سربنتينة", "ac", "air condition").any { p.contains(normalize(it)) } -> listOf("دورة-التكييف")
            listOf("فتيس", "دبرياج", "كلتش", "gearbox", "clutch", "transmission").any { p.contains(normalize(it)) } -> listOf("فتيس-ودبرياج")
            listOf("شكمان", "عادم", "exhaust").any { p.contains(normalize(it)) } -> listOf("نظام-الشكمان")
            else -> emptyList()
        }
    }

    private fun resolveModelRoutes(
        vehicle: VehicleEntity,
        identity: VehicleMarketIdentity
    ): List<ResolvedModelRoute> {
        val cacheKey = listOf(
            vehicle.brand,
            vehicle.model,
            vehicle.year.toString(),
            vehicle.generationCode.orEmpty()
        ).joinToString("|") { normalize(it) }
        routeCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.resolvedAt < ROUTE_CACHE_TTL_MS }
            ?.let { return it.routes }

        val profiles = StorefrontVehicleAliasCatalog.providerProfiles(PartsPriceEngine.AUTO_SPARE_ID, vehicle)
            .filter { it.compatibleYears == null || vehicle.year in it.compatibleYears }
            .sortedByDescending { it.confidence }

        val direct = directRouteCandidates(profiles)
        val verified = mutableListOf<ResolvedModelRoute>()
        direct.take(MAX_DIRECT_ROUTE_PROBES).forEach { candidate ->
            if (verified.size >= MAX_MODEL_ROUTES) return@forEach
            val html = runCatching { httpGet(candidate.url) }.getOrNull() ?: return@forEach
            if (looksLikeVehicleCatalog(html, candidate.displayAlias)) verified += candidate
        }

        val routes = if (verified.isNotEmpty()) verified else discoverFromLiveCatalog(vehicle, identity, profiles)
        val deduped = routes
            .distinctBy { canonicalUrl(it.url) }
            .sortedByDescending { it.score }
            .take(MAX_MODEL_ROUTES)
        if (deduped.isNotEmpty()) routeCache[cacheKey] = RouteCacheEntry(System.currentTimeMillis(), deduped)
        return deduped
    }

    private fun directRouteCandidates(profiles: List<StorefrontVehicleAliasProfile>): List<ResolvedModelRoute> {
        val out = mutableListOf<ResolvedModelRoute>()
        profiles.forEach { profile ->
            val brands = profile.brandAliases.sortedByDescending { if (containsArabic(it)) 1 else 0 }
            val models = profile.modelAliases.sortedByDescending { if (containsArabic(it)) 1 else 0 }
            brands.take(1).forEach { brand ->
                models.forEachIndexed { index, model ->
                    out += ResolvedModelRoute(modelRoute(brand, model), model, profile.confidence - index)
                }
            }
        }
        return out.distinctBy { canonicalUrl(it.url) }
    }

    private fun discoverFromLiveCatalog(
        vehicle: VehicleEntity,
        identity: VehicleMarketIdentity,
        profiles: List<StorefrontVehicleAliasProfile>
    ): List<ResolvedModelRoute> {
        val brandsHtml = runCatching { httpGet("$BASE/brands") }.getOrNull() ?: return emptyList()
        val brandAliases = (
            profiles.flatMap { it.brandAliases } +
                StorefrontVehicleAliasCatalog.egyptianBrandAliases(vehicle.brand) + vehicle.brand
            ).distinctBy(::normalize)

        val brandLinks = extractAnchors(brandsHtml)
            .filter { (_, href) -> brandPathDepth(href) == 1 }
            .map { (label, href) -> Triple(label, absoluteUrl("$BASE/brands", href), aliasScore("$label ${Uri.decode(href)}", brandAliases)) }
            .filter { it.third > 0 }
            .sortedByDescending { it.third }
            .take(2)

        val wantedModelAliases = (
            profiles.flatMap { it.modelAliases } + identity.displayAliases + identity.searchAliases + vehicle.model
            ).map { it.replace(Regex("(?<!\\d)(?:19|20)\\d{2}(?!\\d)"), " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .distinctBy(::normalize)

        val found = mutableListOf<ResolvedModelRoute>()
        brandLinks.forEach { (_, brandUrl, _) ->
            val html = runCatching { httpGet(brandUrl) }.getOrNull() ?: return@forEach
            extractAnchors(html)
                .filter { (_, href) -> brandPathDepth(href) >= 2 }
                .mapNotNull { (label, href) ->
                    val decoded = "$label ${Uri.decode(href)}"
                    val yearScore = yearCompatibilityScore(decoded, vehicle.year)
                    if (yearScore <= -80) return@mapNotNull null
                    val nameScore = aliasScore(decoded, wantedModelAliases)
                    if (nameScore <= 0) return@mapNotNull null
                    ResolvedModelRoute(
                        url = absoluteUrl(brandUrl, href.substringBefore('?')),
                        displayAlias = cleanLabel(label).ifBlank { lastPathLabel(href) },
                        score = nameScore + yearScore
                    )
                }
                .sortedByDescending { it.score }
                .take(MAX_MODEL_ROUTES)
                .forEach(found::add)
        }
        return found
    }

    private fun discoverCategoryUrls(modelHtml: String, modelUrl: String, part: String): List<String> {
        val intents = categoryIntent(part)
        if (intents.isEmpty()) return emptyList()
        return extractAnchors(modelHtml)
            .filter { (_, href) -> Uri.decode(href).contains("/parts/", ignoreCase = true) }
            .map { (label, href) ->
                val haystack = normalize("$label ${Uri.decode(href)}")
                val score = intents.count { intent -> haystack.contains(intent) } * 30 +
                    (intents.maxOfOrNull { intent -> if (haystack.contains(intent)) intent.length else 0 } ?: 0)
                absoluteUrl(modelUrl, href.substringBefore('?')) to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
    }

    private fun brandCategoryFallbackUrls(modelUrl: String, part: String): List<String> {
        val encodedBrand = modelUrl.substringAfter("/brands/", "").substringBefore('/').substringBefore('?')
        if (encodedBrand.isBlank()) return emptyList()
        return categoryRouteSlugs(part)
            .map { slug -> "$BASE/parts/$encodedBrand/${encodePathSegment(slug)}" }
            .distinct()
    }

    private fun parseProductAnchors(
        html: String,
        pageUrl: String,
        partTokens: Set<String>,
        fitmentNote: String
    ): List<PartsPriceOffer> {
        if (html.isBlank()) return emptyList()
        val result = mutableListOf<PartsPriceOffer>()
        val anchorRegex = Regex("""(?is)<a\b[^>]*href\s*=\s*["']([^"']*/products/[^"']*)["'][^>]*>(.*?)</a>""")

        for (match in anchorRegex.findAll(html)) {
            val href = decodeHtml(match.groupValues[1]).trim()
            if (href.isBlank()) continue
            val from = (match.range.first - 260).coerceAtLeast(0)
            val to = min(html.length, match.range.last + 2000)
            val snippet = html.substring(from, to)
            val anchorText = htmlToText(match.groupValues[2])
            val headingText = Regex("""(?is)<h[1-6][^>]*>(.*?)</h[1-6]>""")
                .find(snippet)?.groupValues?.getOrNull(1)?.let(::htmlToText).orEmpty()
            val imageAlt = Regex("""(?is)\balt\s*=\s*["']([^"']{3,220})["']""")
                .find(snippet)?.groupValues?.getOrNull(1)?.let(::decodeHtml).orEmpty()
            val candidates = listOf(anchorText, headingText, imageAlt)
                .map(::cleanLabel)
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
                providerId = PartsPriceEngine.AUTO_SPARE_ID,
                storeName = "Auto Spare",
                title = title,
                priceEgp = price,
                availability = availability,
                checkedAt = System.currentTimeMillis(),
                sourceUrl = absoluteUrl(pageUrl, href),
                fitmentNote = fitmentNote
            )
        }
        return result
    }

    private fun parsePrice(snippet: String): Double? {
        val plain = htmlToText(snippet)
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

    private fun extractAnchors(html: String): List<Pair<String, String>> {
        val regex = Regex("""(?is)<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""")
        return regex.findAll(html).mapNotNull { match ->
            val href = decodeHtml(match.groupValues[1]).trim()
            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:", true)) return@mapNotNull null
            cleanLabel(htmlToText(match.groupValues[2])) to href
        }.toList()
    }

    private fun aliasScore(text: String, aliases: List<String>): Int {
        val haystack = normalize(text)
        var best = 0
        aliases.forEachIndexed { index, alias ->
            val needle = normalize(alias)
            if (needle.isBlank()) return@forEachIndexed
            val score = when {
                haystack == needle -> 120
                haystack.contains(needle) -> 90
                needle.contains(haystack) && haystack.length >= 4 -> 55
                else -> tokenOverlapScore(haystack, needle)
            } - index.coerceAtMost(20)
            if (score > best) best = score
        }
        return best
    }

    private fun tokenOverlapScore(a: String, b: String): Int {
        val at = a.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.toSet()
        val bt = b.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.toSet()
        if (at.isEmpty() || bt.isEmpty()) return 0
        val common = at.intersect(bt).size
        return if (common == 0) 0 else common * 22
    }

    private fun yearCompatibilityScore(text: String, year: Int): Int {
        val ranges = Regex("(?<!\\d)((?:19|20)\\d{2})\\s*[-–—/]\\s*((?:19|20)\\d{2})(?!\\d)")
            .findAll(text)
            .mapNotNull { match ->
                val a = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val b = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                minOf(a, b)..maxOf(a, b)
            }.toList()
        if (ranges.isNotEmpty()) return if (ranges.any { year in it }) 45 else -100
        val years = Regex("(?<!\\d)(?:19|20)\\d{2}(?!\\d)").findAll(text).mapNotNull { it.value.toIntOrNull() }.toSet()
        return when {
            year in years -> 30
            years.isNotEmpty() && years.none { kotlin.math.abs(it - year) <= 1 } -> -25
            else -> 0
        }
    }

    private fun looksLikeVehicleCatalog(html: String, alias: String): Boolean {
        val normalized = normalize(htmlToText(html.take(250_000)))
        val aliasTokens = normalize(alias).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        val aliasSeen = aliasTokens.isEmpty() || aliasTokens.any { normalized.contains(it) }
        val hasCatalogLinks = html.contains("/products/", true) || html.contains("/parts/", true)
        return aliasSeen && hasCatalogLinks
    }

    private fun brandPathDepth(href: String): Int {
        val decoded = Uri.decode(href).substringBefore('?').trimEnd('/')
        val marker = "/brands/"
        val index = decoded.indexOf(marker, ignoreCase = true)
        if (index < 0) return 0
        val tail = decoded.substring(index + marker.length).trim('/')
        return if (tail.isBlank()) 0 else tail.split('/').filter { it.isNotBlank() }.size
    }

    private fun lastPathLabel(href: String): String = Uri.decode(href.substringBefore('?'))
        .trimEnd('/').substringAfterLast('/').replace('-', ' ').trim()

    private fun modelRoute(brand: String, model: String): String =
        "$BASE/brands/${encodePathSegment(brand)}/${encodePathSegment(slug(model))}"

    private fun slug(value: String): String = value.trim().replace(Regex("\\s+"), "-")

    private fun encodePathSegment(value: String): String = Uri.encode(value, "-_.~")

    private fun withPage(url: String, page: Int): String = when {
        page <= 1 -> url
        '?' in url -> "$url&page=$page"
        else -> "$url?page=$page"
    }

    private fun canonicalUrl(value: String): String = value.substringBefore('?').trimEnd('/').lowercase(Locale.ROOT)

    private fun matchTokens(text: String): Set<String> {
        val generic = setOf("طقم", "قطعه", "قطعة", "اصلي", "اصلى", "original")
        return normalize(text).split(Regex("[^\\p{L}\\p{N}]+"))
            .map(::stripArabicArticle)
            .filter { it.length >= 2 && it !in generic }
            .toSet()
    }

    private fun matchScore(title: String, wanted: Set<String>): Int {
        if (wanted.isEmpty()) return 1
        val tokens = normalize(title).split(Regex("[^\\p{L}\\p{N}]+"))
            .map(::stripArabicArticle).filter { it.isNotBlank() }.toSet()
        return wanted.count { wantedToken ->
            wantedToken in tokens || tokens.any { token -> token.contains(wantedToken) || wantedToken.contains(token) }
        }
    }

    private fun stripArabicArticle(token: String): String =
        if (token.startsWith("ال") && token.length > 4) token.removePrefix("ال") else token

    private fun containsArabic(value: String): Boolean = value.any { it in '\u0600'..'\u06FF' }

    private fun normalize(value: String): String {
        val noMarks = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return noMarks.lowercase(Locale.ROOT)
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
            .replace(Regex("[-_]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    private fun absoluteUrl(pageUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val page = URL(pageUrl)
        return when {
            href.startsWith("/") -> "${page.protocol}://${page.host}$href"
            else -> "${page.protocol}://${page.host}/${href.trimStart('/')}"
        }
    }

    private fun cleanLabel(value: String): String =
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
        out = Regex("&#(\\d+);").replace(out) { match ->
            match.groupValues[1].toIntOrNull()?.let { code -> runCatching { code.toChar().toString() }.getOrDefault(match.value) } ?: match.value
        }
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { code -> runCatching { code.toChar().toString() }.getOrDefault(match.value) } ?: match.value
        }
        return out
    }

    private fun looksPaginated(html: String): Boolean =
        html.contains("page=2", true) || html.contains("التالي", true)

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
}
