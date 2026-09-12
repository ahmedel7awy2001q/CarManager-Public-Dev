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
import kotlin.math.ceil
import kotlin.math.min

/**
 * Multi-source Egyptian spare-parts search.
 *
 * Rules:
 * 1) Never manufacture a price or stock state.
 * 2) Part relevance is checked separately from vehicle fitment. A brake-fluid search must never
 *    become brake pads merely because both contain the word "brake/فرامل".
 * 3) A product reached through a vehicle-specific store query may be shown for price discovery even
 *    when the title omits the vehicle name, but its fitment confidence is deliberately capped and
 *    the UI must tell the user to confirm OEM/fitment.
 * 4) Stores are queried concurrently and each store gets a small query budget to avoid long UI
 *    stalls when one storefront is slow or blocks automated requests.
 */
internal object ExpandedPartsPriceEngine {
    const val DAWAASA_ID = "dawaasa-eg"
    const val AJYAD_ID = "ajyad-auto-eg"
    const val GEARHEAD_ID = "gearhead-eg"
    const val AMAZON_EG_ID = "amazon-eg"
    const val SPARE_ZONE_ID = "spare-zone-eg"
    const val KIA_EIT_ID = "kia-eit-eg"
    const val AUTO_CYCLE_ID = "autocycle-eg"
    const val DAWAR_ID = "dawar-eg"
    const val ALMOHANDES_ID = "almohandes-eg"
    const val SAK_ID = "sak-spare-parts-eg"
    const val EL_EBIARY_ID = "el-ebiary-eg"
    const val PUPPO_ID = "puppo-eg"
    const val JUMIA_EG_ID = "jumia-eg"
    const val DYNAMU_ID = "dynamu-eg"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Mobile Safari/537.36 CarManager/1.1"

    private const val CONNECT_TIMEOUT_MS = 3_500
    private const val READ_TIMEOUT_MS = 4_500
    private const val MAX_PROVIDER_QUERIES = 2

    val capabilities: List<PartsProviderCapability> = buildList {
        addAll(PartsPriceEngine.builtInCapabilities)
        add(PartsProviderCapability(DAWAASA_ID, "Dawaasa", "https://www.dawaasa.com/", true, true,
            "متجر مصري؛ يعرض التطبيق السعر فقط عندما يستطيع التحقق منه من صفحة فعلية"))
        add(PartsProviderCapability(AJYAD_ID, "Ajyad Auto", "https://ajyadauto.com/ar-eg/shop", true, true,
            "كتالوج قطع غيار مصري؛ يتحقق التطبيق من السعر المنشور قبل عرضه"))
        add(PartsProviderCapability(GEARHEAD_ID, "GearHead", "https://gearhead-eg.com/", true, true,
            "متجر قطع غيار مصري بمنتجات وأسعار منشورة"))
        add(PartsProviderCapability(AMAZON_EG_ID, "Amazon.eg", "https://www.amazon.eg/", false, false,
            "مصدر خارجي للمقارنة؛ لا يعتمد التطبيق سعرًا داخليًا دون تحقق ثابت"))
        add(PartsProviderCapability(SPARE_ZONE_ID, "Spare Zone", "https://sparezone-eg.com/", false, false,
            "سوق قطع غيار مصري؛ يفتح البحث الخارجي عند عدم توفر مسار سعر موثوق"))
        add(PartsProviderCapability(KIA_EIT_ID, "Kia Egypt / EIT", "https://www.kia.com/eg/", false, false,
            "مرجع رسمي للسيارة والقطع الأصلية"))
        add(PartsProviderCapability(AUTO_CYCLE_ID, "AutoCycle Egypt", "https://www.autocycle.com.eg/", false, false,
            "سوق قطع غيار داخل مصر"))
        add(PartsProviderCapability(DAWAR_ID, "Dawar", "https://www.dawar-app.com/", false, false,
            "منصة مصرية للبحث عن قطع الغيار والمحلات"))
        add(PartsProviderCapability(ALMOHANDES_ID, "Al Mohandes Auto Parts", "https://almohandesautoparts.com/", false, false,
            "متجر مصري واسع الماركات"))
        add(PartsProviderCapability(SAK_ID, "SAK Spare Parts", "https://sakspareparts.com/", false, false,
            "متجر مصري بمنتجات وأسعار منشورة"))
        add(PartsProviderCapability(EL_EBIARY_ID, "El Ebiary", "https://el-ebiary.com/", false, false,
            "متجر ومورد قطع غيار داخل مصر"))
        add(PartsProviderCapability(PUPPO_ID, "PUPPO Auto Parts", "https://www.puppo.store/", false, false,
            "متجر مصري لقطع غيار متعددة"))
        add(PartsProviderCapability(JUMIA_EG_ID, "Jumia Egypt", "https://www.jumia.com.eg/ar/automobile-replacement-parts/", false, false,
            "سوق إضافي للمقارنة"))
        add(PartsProviderCapability(DYNAMU_ID, "Dynamu", "https://dynamu.co/ar/home/", false, false,
            "منصة مصرية تجمع تجار قطع الغيار"))
    }.distinctBy { it.id }

    val priceCapableProviderIds: Set<String> = capabilities
        .filter { it.priceInApp }
        .mapTo(linkedSetOf()) { it.id }

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

        val builtInIds = PartsPriceEngine.builtInCapabilities.mapTo(linkedSetOf()) { it.id }
        val enabledBuiltIn = enabledProviderIds.intersect(builtInIds)

        val (base, extraResults) = coroutineScope {
            val baseDeferred = async {
                PartsPriceEngine.search(
                    vehicle = vehicle,
                    rawPart = part,
                    enabledProviderIds = enabledBuiltIn,
                    identity = identity
                )
            }
            val dawaasa = async {
                if (DAWAASA_ID !in enabledProviderIds) Result.success(emptyList())
                else providerResult { fetchStore(vehicle, part, identity, StoreDefinition.dawaasa) }
            }
            val ajyad = async {
                if (AJYAD_ID !in enabledProviderIds) Result.success(emptyList())
                else providerResult { fetchStore(vehicle, part, identity, StoreDefinition.ajyad) }
            }
            val gearHead = async {
                if (GEARHEAD_ID !in enabledProviderIds) Result.success(emptyList())
                else providerResult { fetchStore(vehicle, part, identity, StoreDefinition.gearHead) }
            }
            baseDeferred.await() to listOf(
                DAWAASA_ID to dawaasa.await(),
                AJYAD_ID to ajyad.await(),
                GEARHEAD_ID to gearHead.await()
            )
        }

        val allOffers = base.offers.toMutableList()
        val errors = base.errors.toMutableMap()
        extraResults.forEach { (providerId, result) ->
            result.onSuccess { allOffers += it }
                .onFailure { errors[providerId] = friendlyNetworkError(it) }
        }

        val accepted = allOffers.asSequence()
            .filter { it.priceEgp > 0.0 && it.sourceUrl.startsWith("http") }
            .mapNotNull { rawOffer ->
                val relevance = partRelevance(part, rawOffer.title) ?: return@mapNotNull null

                val strictVehicleOffer = if (rawOffer.vehicleMatch != PartsVehicleMatch.UNKNOWN) {
                    rawOffer
                } else {
                    val match = identity.classifyProductTitle(rawOffer.title)
                    if (identity.acceptsStrict(match)) {
                        rawOffer.copy(
                            vehicleMatch = match.level,
                            matchedVehicleAlias = match.matchedAlias
                        )
                    } else rawOffer
                }

                if (strictVehicleOffer.vehicleMatch != PartsVehicleMatch.UNKNOWN) {
                    val assessment = PartsFitmentEvaluator.assess(vehicle, identity, strictVehicleOffer, part)
                    if (!assessment.accepted) return@mapNotNull null
                    strictVehicleOffer.copy(
                        fitmentConfidence = min(assessment.confidence, relevance),
                        fitmentReason = assessment.reason,
                        fitmentSignals = assessment.signals,
                        fitmentNote = assessment.reason
                    )
                } else {
                    // The store was searched with the saved vehicle identity, but the returned title
                    // does not contain enough vehicle/generation evidence. Keep the *price discovery*
                    // result and visibly cap confidence instead of pretending fitment is verified.
                    val discoveryConfidence = min(55, relevance)
                    strictVehicleOffer.copy(
                        fitmentConfidence = discoveryConfidence,
                        fitmentReason = "السعر يطابق القطعة، لكن توافقها مع السيارة يحتاج تأكيد OEM/المواصفات",
                        fitmentSignals = listOf("بحث مرتبط بـ ${identity.canonicalName}", "توافق السيارة غير مؤكد من عنوان المنتج"),
                        fitmentNote = "سعر من نتيجة بحث مرتبطة بسيارتك؛ راجع رقم OEM والمواصفات قبل الشراء."
                    )
                }
            }
            .distinctBy { "${it.providerId}|${normalize(it.title)}|${it.priceEgp.toLong()}|${it.sourceUrl}" }
            .sortedWith(
                compareByDescending<PartsPriceOffer> { it.fitmentConfidence ?: 0 }
                    .thenBy {
                        when (it.availability) {
                            PartsOfferAvailability.IN_STOCK -> 0
                            PartsOfferAvailability.UNKNOWN -> 1
                            PartsOfferAvailability.OUT_OF_STOCK -> 2
                        }
                    }
                    .thenBy { it.priceEgp }
            )
            .toList()

        PartsPriceSearchResult(
            requestedPart = part,
            offers = accepted,
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
        val part = cleanPartName(rawPart)
        val q = searchText(vehicle, part, identity)
        return when (providerId) {
            DAWAASA_ID -> "https://www.dawaasa.com/search?q=${Uri.encode(q)}&type=product"
            AJYAD_ID -> "https://ajyadauto.com/ar-eg/shop?search=${Uri.encode(q)}"
            GEARHEAD_ID -> "https://gearhead-eg.com/?s=${Uri.encode(q)}&post_type=product"
            AMAZON_EG_ID -> "https://www.amazon.eg/s?k=${Uri.encode(q)}"
            SPARE_ZONE_ID -> "https://sparezone-eg.com/brand/all-parts"
            KIA_EIT_ID -> "https://www.kia.com/eg/"
            AUTO_CYCLE_ID -> "https://www.autocycle.com.eg/"
            DAWAR_ID -> "https://www.dawar-app.com/"
            ALMOHANDES_ID -> "https://almohandesautoparts.com/"
            SAK_ID -> "https://sakspareparts.com/?s=${Uri.encode(q)}&post_type=product"
            EL_EBIARY_ID -> "https://el-ebiary.com/?s=${Uri.encode(q)}&post_type=product"
            PUPPO_ID -> "https://www.puppo.store/"
            JUMIA_EG_ID -> "https://www.jumia.com.eg/catalog/?q=${Uri.encode(q)}"
            DYNAMU_ID -> "https://dynamu.co/ar/home/"
            else -> PartsPriceEngine.providerSearchUrl(providerId, vehicle, part, identity)
        }
    }

    fun broadSearchText(
        vehicle: VehicleEntity,
        rawPart: String,
        identity: VehicleMarketIdentity = VehicleMarketIdentityResolver.resolve(vehicle)
    ): String = searchText(vehicle, cleanPartName(rawPart), identity)

    private data class StoreDefinition(
        val id: String,
        val name: String,
        val urlFor: (String) -> String,
        val productHints: List<String>
    ) {
        companion object {
            val dawaasa = StoreDefinition(
                DAWAASA_ID,
                "Dawaasa",
                { q -> "https://www.dawaasa.com/search?q=${Uri.encode(q)}&type=product" },
                listOf("/products/", "/product/")
            )
            val ajyad = StoreDefinition(
                AJYAD_ID,
                "Ajyad Auto",
                { q -> "https://ajyadauto.com/ar-eg/shop?search=${Uri.encode(q)}" },
                listOf("/product/", "/part/", "/shop/")
            )
            val gearHead = StoreDefinition(
                GEARHEAD_ID,
                "GearHead",
                { q -> "https://gearhead-eg.com/?s=${Uri.encode(q)}&post_type=product" },
                listOf("/product/")
            )
        }
    }

    private fun fetchStore(
        vehicle: VehicleEntity,
        part: String,
        identity: VehicleMarketIdentity,
        store: StoreDefinition
    ): List<PartsPriceOffer> {
        val queries = identity.searchQueries(part, maxQueries = MAX_PROVIDER_QUERIES)
            .ifEmpty { listOf(searchText(vehicle, part, identity)) }
        val wanted = matchTokens(part)
        val out = mutableListOf<PartsPriceOffer>()
        var lastFailure: Throwable? = null

        for (query in queries.take(MAX_PROVIDER_QUERIES)) {
            val url = store.urlFor(query)
            runCatching { httpGet(url) }
                .onSuccess { html ->
                    out += parseProducts(
                        html = html,
                        pageUrl = url,
                        providerId = store.id,
                        storeName = store.name,
                        productHints = store.productHints,
                        wanted = wanted,
                        requestedPart = part
                    )
                }
                .onFailure { lastFailure = it }
            if (out.size >= 8) break
        }

        if (out.isEmpty()) lastFailure?.let { throw it }
        return out
            .distinctBy { "${normalize(it.title)}|${it.priceEgp.toLong()}|${it.sourceUrl}" }
            .take(16)
    }

    private fun parseProducts(
        html: String,
        pageUrl: String,
        providerId: String,
        storeName: String,
        productHints: List<String>,
        wanted: Set<String>,
        requestedPart: String
    ): List<PartsPriceOffer> {
        if (html.isBlank()) return emptyList()
        val result = mutableListOf<PartsPriceOffer>()
        val anchorRegex = Regex("""(?is)<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""")

        for (match in anchorRegex.findAll(html)) {
            val href = decodeHtml(match.groupValues[1]).trim()
            if (productHints.none { hint -> href.contains(hint, ignoreCase = true) }) continue

            val from = (match.range.first - 220).coerceAtLeast(0)
            val to = min(html.length, match.range.last + 1700)
            val snippet = html.substring(from, to)
            val anchorTitle = cleanTitle(htmlToText(match.groupValues[2]))
            val heading = Regex("""(?is)<h[1-6][^>]*>(.*?)</h[1-6]>""")
                .find(snippet)?.groupValues?.getOrNull(1)?.let(::htmlToText)?.let(::cleanTitle).orEmpty()
            val alt = Regex("""(?is)\balt\s*=\s*["']([^"']{3,220})["']""")
                .find(snippet)?.groupValues?.getOrNull(1)?.let(::decodeHtml)?.let(::cleanTitle).orEmpty()

            val title = listOf(anchorTitle, heading, alt)
                .filter { it.length in 3..240 }
                .maxByOrNull { title -> tokenScore(title, wanted) }
                ?: continue

            if (partRelevance(requestedPart, title) == null) continue
            val price = parsePrice(snippet) ?: continue
            if (price <= 0.0) continue

            result += PartsPriceOffer(
                providerId = providerId,
                storeName = storeName,
                title = title,
                priceEgp = price,
                availability = availabilityOf(snippet),
                checkedAt = System.currentTimeMillis(),
                sourceUrl = absoluteUrl(pageUrl, href),
                sourceTypeLabel = "سعر متجر متحقق",
                fitmentNote = "السعر متحقق؛ توافق القطعة مع السيارة يحتاج تأكيد OEM عند غياب بيانات التوافق من العنوان."
            )
        }

        // Storefronts sometimes render the title outside the link. Keep the real search page as
        // source rather than inventing a product URL.
        if (result.isEmpty()) {
            val headingRegex = Regex("""(?is)<h[1-6][^>]*>(.*?)</h[1-6]>""")
            for (heading in headingRegex.findAll(html)) {
                val title = cleanTitle(htmlToText(heading.groupValues[1]))
                if (title.length !in 3..240 || partRelevance(requestedPart, title) == null) continue
                val end = min(html.length, heading.range.last + 900)
                val snippet = html.substring(heading.range.first, end)
                val price = parsePrice(snippet) ?: continue
                result += PartsPriceOffer(
                    providerId = providerId,
                    storeName = storeName,
                    title = title,
                    priceEgp = price,
                    availability = availabilityOf(snippet),
                    checkedAt = System.currentTimeMillis(),
                    sourceUrl = pageUrl,
                    sourceTypeLabel = "سعر متجر متحقق",
                    fitmentNote = "السعر متحقق من صفحة البحث؛ راجع OEM قبل الشراء."
                )
            }
        }
        return result
    }

    private enum class PartFamily {
        BRAKE_FLUID, BRAKE_PAD, BRAKE_DISC,
        AIR_FILTER, OIL_FILTER, FUEL_FILTER, CABIN_FILTER,
        ENGINE_OIL, TRANSMISSION_FLUID, COOLANT,
        SPARK_PLUG, BATTERY, TIRE, BELT,
        UNKNOWN
    }

    /** Returns 0..100 when relevant, null when the title belongs to a different part family. */
    private fun partRelevance(query: String, title: String): Int? {
        val q = normalize(query)
        val t = normalize(title)
        if (q.isBlank() || t.isBlank()) return null

        val qFamily = detectFamily(q)
        val tFamily = detectFamily(t)
        if (qFamily != PartFamily.UNKNOWN) {
            if (tFamily != PartFamily.UNKNOWN && tFamily != qFamily) return null
            if (!familyEvidence(qFamily, t)) return null
        }

        val wanted = matchTokens(q)
        if (wanted.isEmpty()) return 60
        val titleTokens = tokenSet(t)
        val hits = wanted.count { wantedToken ->
            titleTokens.any { titleToken -> tokensEquivalent(wantedToken, titleToken) }
        }

        val minimumHits = when {
            wanted.size <= 1 -> 1
            wanted.size == 2 -> 2
            else -> ceil(wanted.size * .60).toInt()
        }
        if (hits < minimumHits && qFamily == PartFamily.UNKNOWN) return null

        val phraseBoost = if (t.contains(q)) 18 else 0
        val familyBoost = if (qFamily != PartFamily.UNKNOWN && familyEvidence(qFamily, t)) 24 else 0
        return (48 + hits * 12 + phraseBoost + familyBoost).coerceIn(0, 100)
    }

    private fun detectFamily(text: String): PartFamily {
        val n = normalize(text)
        fun any(vararg values: String) = values.any { n.contains(normalize(it)) }
        return when {
            any("سائل فرامل", "زيت فرامل", "brake fluid", "dot 3", "dot3", "dot 4", "dot4", "dot 5", "dot5") -> PartFamily.BRAKE_FLUID
            any("تيل فرامل", "تيل امامي", "تيل خلفي", "brake pad", "brake pads", "pads") -> PartFamily.BRAKE_PAD
            any("طنابير", "طنبوره", "brake disc", "brake rotor", "rotor") -> PartFamily.BRAKE_DISC
            any("فلتر هواء", "air filter") -> PartFamily.AIR_FILTER
            any("فلتر زيت", "oil filter") -> PartFamily.OIL_FILTER
            any("فلتر بنزين", "فلتر وقود", "fuel filter") -> PartFamily.FUEL_FILTER
            any("فلتر تكييف", "فلتر كابينه", "cabin filter", "ac filter") -> PartFamily.CABIN_FILTER
            any("زيت فتيس", "زيت ناقل", "transmission fluid", "gear oil", "atf", "cvt fluid") -> PartFamily.TRANSMISSION_FLUID
            any("زيت محرك", "engine oil", "motor oil") -> PartFamily.ENGINE_OIL
            any("مياه تبريد", "سائل تبريد", "coolant", "antifreeze", "anti freeze") -> PartFamily.COOLANT
            any("بوجيه", "بواجي", "spark plug", "spark plugs") -> PartFamily.SPARK_PLUG
            any("بطاريه", "battery") -> PartFamily.BATTERY
            any("اطار", "كاوتش", "tire", "tyre") -> PartFamily.TIRE
            any("سير مجموعه", "سير دينامو", "drive belt", "serpentine belt") -> PartFamily.BELT
            else -> PartFamily.UNKNOWN
        }
    }

    private fun familyEvidence(family: PartFamily, title: String): Boolean {
        val n = normalize(title)
        fun any(vararg values: String) = values.any { n.contains(normalize(it)) }
        return when (family) {
            PartFamily.BRAKE_FLUID -> any("سائل فرامل", "زيت فرامل", "brake fluid", "dot3", "dot 3", "dot4", "dot 4", "dot5", "dot 5")
            PartFamily.BRAKE_PAD -> any("تيل", "brake pad", "brake pads", "pads") && !any("fluid", "سائل")
            PartFamily.BRAKE_DISC -> any("طنابير", "طنبوره", "disc", "rotor")
            PartFamily.AIR_FILTER -> any("فلتر هواء", "air filter")
            PartFamily.OIL_FILTER -> any("فلتر زيت", "oil filter")
            PartFamily.FUEL_FILTER -> any("فلتر بنزين", "فلتر وقود", "fuel filter")
            PartFamily.CABIN_FILTER -> any("فلتر تكييف", "فلتر كابينه", "cabin filter", "ac filter")
            PartFamily.ENGINE_OIL -> any("زيت محرك", "engine oil", "motor oil")
            PartFamily.TRANSMISSION_FLUID -> any("زيت فتيس", "transmission fluid", "gear oil", "atf", "cvt fluid")
            PartFamily.COOLANT -> any("مياه تبريد", "سائل تبريد", "coolant", "antifreeze")
            PartFamily.SPARK_PLUG -> any("بوجيه", "بواجي", "spark plug")
            PartFamily.BATTERY -> any("بطاريه", "battery")
            PartFamily.TIRE -> any("اطار", "كاوتش", "tire", "tyre")
            PartFamily.BELT -> any("سير", "belt")
            PartFamily.UNKNOWN -> true
        }
    }

    private fun tokensEquivalent(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a))) return true
        val groups = listOf(
            setOf("فلتر", "filter"),
            setOf("هواء", "air"),
            setOf("زيت", "oil"),
            setOf("بنزين", "وقود", "fuel"),
            setOf("فرامل", "brake", "brakes"),
            setOf("سايل", "fluid"),
            setOf("تيل", "pad", "pads"),
            setOf("طنابير", "طنبوره", "disc", "rotor", "rotors"),
            setOf("بوجيه", "بواجي", "spark", "plug", "plugs"),
            setOf("فتيس", "ناقل", "transmission", "gear"),
            setOf("تبريد", "coolant", "antifreeze"),
            setOf("بطاريه", "battery"),
            setOf("اطار", "كاوتش", "tire", "tyre")
        )
        return groups.any { group -> a in group && b in group }
    }

    private fun availabilityOf(snippet: String): PartsOfferAvailability {
        val plain = normalize(htmlToText(snippet))
        return when {
            listOf("غير متوفر", "نفذت", "sold out", "out of stock").any { plain.contains(normalize(it)) } -> PartsOfferAvailability.OUT_OF_STOCK
            listOf("في المخزون", "متوفر", "in stock", "add to cart", "اضف الى السله", "أضف إلى السلة").any { plain.contains(normalize(it)) } -> PartsOfferAvailability.IN_STOCK
            else -> PartsOfferAvailability.UNKNOWN
        }
    }

    private fun parsePrice(snippet: String): Double? {
        val text = htmlToText(snippet)
        val patterns = listOf(
            Regex("""(?i)(?:LE|EGP)\s*([0-9][0-9.,\s]*)"""),
            Regex("""([0-9][0-9.,\s]*)\s*(?:EGP|LE|ج\.م|جنيه|جنية)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                val value = parseLocalizedPriceNumber(match.groupValues[1])
                if (value != null && value in 10.0..10_000_000.0) return value
            }
        }
        return null
    }

    private fun parseLocalizedPriceNumber(raw: String): Double? {
        var value = raw.replace(" ", "").trim().trim(',', '.')
        if (value.isBlank()) return null
        val dot = value.lastIndexOf('.')
        val comma = value.lastIndexOf(',')
        value = when {
            dot >= 0 && comma >= 0 && comma > dot -> value.replace(".", "").replace(',', '.')
            dot >= 0 && comma >= 0 -> value.replace(",", "")
            comma >= 0 -> {
                val decimals = value.length - comma - 1
                if (decimals in 1..2) value.replace(',', '.') else value.replace(",", "")
            }
            else -> value
        }
        return value.toDoubleOrNull()
    }

    private fun searchText(vehicle: VehicleEntity, part: String, identity: VehicleMarketIdentity): String {
        val clean = identity.sanitizePartQuery(part)
        return identity.searchQueries(clean, maxQueries = 1).firstOrNull()
            ?: listOf(clean, vehicle.brand, vehicle.model, vehicle.year.toString()).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun cleanPartName(raw: String): String {
        val stop = setOf(
            "تغيير", "استبدال", "فحص", "خدمة", "دورية", "الدورية",
            "السيارة", "للسيارة", "استخدام", "شاق"
        )
        val normalizedStop = stop.map(::normalize).toSet()
        return raw.replace('—', ' ').replace('-', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && normalize(it) !in normalizedStop }
            .joinToString(" ")
            .trim()
    }

    private fun matchTokens(text: String): Set<String> {
        val generic = setOf(
            "كيا", "kia", "سيراتو", "cerato", "جراند", "grand", "طقم", "قطعه", "قطعة",
            "المحرك", "motor", "engine"
        ).map(::normalize).map(::stripArabicArticle).toSet()
        return tokenSet(text).filter { it.length >= 2 && it !in generic }.toSet()
    }

    private fun tokenSet(text: String): Set<String> = normalize(text)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .map(::stripArabicArticle)
        .filter { it.isNotBlank() }
        .toSet()

    private fun tokenScore(title: String, wanted: Set<String>): Int {
        if (wanted.isEmpty()) return 1
        val titleTokens = tokenSet(title)
        return wanted.count { w -> titleTokens.any { t -> tokensEquivalent(w, t) } }
    }

    private fun stripArabicArticle(token: String): String =
        if (token.startsWith("ال") && token.length > 4) token.removePrefix("ال") else token

    private fun normalize(text: String): String {
        val noMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return noMarks.lowercase(Locale.ROOT)
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
            if (code !in 200..299) error("HTTP $code")
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

    private fun cleanTitle(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .removePrefix("Image:")
        .trim()

    private fun htmlToText(html: String): String = decodeHtml(
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
        out = Regex("&#(\\d+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { runCatching { it.toChar().toString() }.getOrDefault(m.value) } ?: m.value
        }
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { runCatching { it.toChar().toString() }.getOrDefault(m.value) } ?: m.value
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
        else -> "المصدر لم يستجب بسرعة كافية؛ يمكنك فتح المتجر مباشرة."
    }
}
