package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.TransmissionType
import com.ahmed.carmanager.data.local.model.VehicleEntity
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A richer identity graph for one saved vehicle. Names are not treated as equal: every alias has a
 * relationship and a confidence. This prevents a shared platform/sibling name from becoming an
 * automatic fitment claim while still allowing it to widen Egyptian-market discovery.
 */
internal enum class VehicleAliasRelation(val arLabel: String, val baseConfidence: Int) {
    LOCAL_MARKET_NAME("اسم السوق المحلي", 96),
    GLOBAL_NAME("اسم عالمي لنفس السيارة", 94),
    GENERATION_CODE("كود الجيل/الشاسيه", 98),
    REBADGED_SAME_VEHICLE("نفس السيارة بعلامة/سوق آخر", 88),
    SIBLING_MODEL("سيارة شقيقة", 72),
    SHARED_PLATFORM("منصة مشتركة فقط", 52),
    LEARNED_MARKET_ALIAS("مسمى متعلّم من السوق", 76)
}

internal data class VehicleAliasProfile(
    val name: String,
    val relation: VehicleAliasRelation,
    val market: String? = null,
    val compatibleYears: IntRange? = null,
    val confidence: Int = relation.baseConfidence,
    val evidence: String? = null
)

internal data class PartsFitmentAssessment(
    val confidence: Int,
    val accepted: Boolean,
    val reason: String,
    val signals: List<String> = emptyList()
)

/**
 * Curated high-confidence global identity expansions. The resolver still works for every unknown
 * car from its saved brand/model/year; this table only adds verified cross-market identities.
 * Learned aliases are stored per vehicle separately and never silently become OEM proof.
 */
internal object VehicleAliasKnowledgeBase {
    fun profiles(vehicle: VehicleEntity): List<VehicleAliasProfile> {
        val brand = normalizeVehicleText(vehicle.brand)
        val model = normalizeVehicleText(vehicle.model + " " + vehicle.displayName.orEmpty())
        val y = vehicle.year
        val out = mutableListOf<VehicleAliasProfile>()

        fun add(name: String, relation: VehicleAliasRelation, market: String? = null, years: IntRange? = null, confidence: Int = relation.baseConfidence, evidence: String? = null) {
            out += VehicleAliasProfile(name, relation, market, years, confidence, evidence)
        }

        // Kia Cerato/Forte/K3 fourth generation (BD). Market naming differs widely.
        if ((brand.contains("kia") || brand.contains("كيا")) && (model.contains("cerato") || model.contains("سيراتو") || model.contains("forte") || model.contains("k3")) && y in 2018..2024) {
            add("Kia Grand Cerato", VehicleAliasRelation.LOCAL_MARKET_NAME, "Egypt", 2018..2022)
            add("Kia Cerato BD", VehicleAliasRelation.GENERATION_CODE, "Global", 2018..2024)
            add("Kia Forte BD", VehicleAliasRelation.GLOBAL_NAME, "North America / selected markets", 2019..2024)
            add("Kia K3 BD", VehicleAliasRelation.GLOBAL_NAME, "Korea / selected markets", 2018..2024)
            add("Cerato BD", VehicleAliasRelation.GENERATION_CODE, "Global", 2018..2024)
            add("Forte BD", VehicleAliasRelation.GENERATION_CODE, "Global", 2019..2024)
        }

        // Chevrolet Captiva II is a rebadged Baojun 530 family; siblings must not be treated as
        // universal fitment because engine/trim/body differences exist between markets.
        if ((brand.contains("chevrolet") || brand.contains("شيفروليه") || brand.contains("شفروليه")) && model.contains("captiva") && y >= 2019) {
            add("Chevrolet Captiva", VehicleAliasRelation.LOCAL_MARKET_NAME, "Egypt / Middle East / LatAm", 2019..2027)
            add("Baojun 530", VehicleAliasRelation.REBADGED_SAME_VEHICLE, "China", 2018..2021, 90, "same vehicle family; verify engine/trim")
            add("Wuling Almaz", VehicleAliasRelation.SIBLING_MODEL, "Indonesia", 2019..2027, 78, "sibling/rebadge family; verify OEM")
            add("MG Hector", VehicleAliasRelation.SIBLING_MODEL, "India", 2019..2027, 76, "same architecture family; body/powertrain can differ")
            add("MG Hector Plus", VehicleAliasRelation.SIBLING_MODEL, "India", 2020..2027, 70, "related derivative; require OEM confirmation")
        }

        // Common Egyptian market generation codes.
        if ((brand.contains("hyundai") || brand.contains("هيوندا")) && (model.contains("elantra") || model.contains("النترا"))) {
            when (y) {
                in 2012..2016 -> add("Elantra MD", VehicleAliasRelation.GENERATION_CODE, "Global", 2011..2016)
                in 2017..2020 -> add("Elantra AD", VehicleAliasRelation.GENERATION_CODE, "Global", 2016..2020)
                in 2021..2027 -> add("Elantra CN7", VehicleAliasRelation.GENERATION_CODE, "Global", 2020..2027)
            }
        }
        if ((brand.contains("nissan") || brand.contains("نيسان")) && (model.contains("sunny") || model.contains("صني") || model.contains("صنى")) && y >= 2013) {
            add("Nissan Sunny N17", VehicleAliasRelation.GENERATION_CODE, "Egypt / Global", 2011..2027)
            add("Nissan Versa N17", VehicleAliasRelation.GLOBAL_NAME, "North America / selected markets", 2012..2019, 82)
            add("Nissan Almera N17", VehicleAliasRelation.GLOBAL_NAME, "selected markets", 2011..2019, 82)
        }
        if ((brand.contains("toyota") || brand.contains("تويوتا")) && (model.contains("corolla") || model.contains("كورولا"))) {
            if (y in 2014..2019) add("Corolla E170", VehicleAliasRelation.GENERATION_CODE, "Global", 2013..2019)
            if (y >= 2020) add("Corolla E210", VehicleAliasRelation.GENERATION_CODE, "Global", 2018..2027)
        }

        // The saved make/model is the authoritative user identity for storefront discovery. A
        // display name can be a nickname ("العربية", "سيارتي"...), so keep it as a useful alias
        // but never let it outrank the actual make/model or a verified generation code.
        val saved = listOf(vehicle.brand, vehicle.model).filter { it.isNotBlank() }.joinToString(" ")
        if (saved.isNotBlank()) add(saved, VehicleAliasRelation.LOCAL_MARKET_NAME, "Saved profile", (y - 1)..(y + 1), 100, "saved by user")
        vehicle.displayName?.takeIf { it.isNotBlank() }?.let { add(it, VehicleAliasRelation.LOCAL_MARKET_NAME, "Display label", (y - 1)..(y + 1), 90, "user display label; secondary search alias") }

        return out
            .filter { p -> p.compatibleYears == null || y in p.compatibleYears }
            .groupBy { normalizeVehicleText(it.name) }
            .values
            .mapNotNull { duplicates -> duplicates.maxByOrNull { it.confidence } }
            .sortedByDescending { it.confidence }
    }
}

internal object PartsFitmentEvaluator {
    private val oemLike = Regex("(?i)^[A-Z0-9][A-Z0-9._/-]{4,24}$")

    fun assess(
        vehicle: VehicleEntity,
        identity: VehicleMarketIdentity,
        offer: PartsPriceOffer,
        requestedPart: String
    ): PartsFitmentAssessment {
        val title = normalizeVehicleText(offer.title)
        val signals = mutableListOf<String>()
        var score = when (offer.vehicleMatch) {
            PartsVehicleMatch.VEHICLE_CATALOG -> 95
            PartsVehicleMatch.EXACT_GENERATION -> 91
            PartsVehicleMatch.MODEL_AND_YEAR -> 84
            PartsVehicleMatch.MODEL_FAMILY -> 64
            PartsVehicleMatch.UNKNOWN -> 25
        }

        val alias = identity.aliasProfiles
            .filter { title.contains(normalizeVehicleText(it.name)) }
            .maxByOrNull { it.confidence }
        if (alias != null) {
            score = maxOf(score, alias.confidence)
            signals += "${alias.relation.arLabel}: ${alias.name}${alias.market?.let { " ($it)" }.orEmpty()}"
            alias.evidence?.takeIf { it.isNotBlank() }?.let { signals += it }
        }

        var hardConflict = false
        identity.compatibleModelYears?.let { years ->
            val mentioned = Regex("(?<!\\d)(?:19|20)\\d{2}(?!\\d)").findAll(title).mapNotNull { it.value.toIntOrNull() }.toSet()
            if (mentioned.isNotEmpty()) {
                if (mentioned.any { it in years }) {
                    score += 4
                    signals += "السنة ضمن نطاق الجيل"
                } else {
                    score -= 42
                    hardConflict = true
                    signals += "تعارض سنة/جيل واضح"
                }
            }
        }

        generationSignal(vehicle, title)?.let { (delta, text) ->
            score += delta
            signals += text
        }
        engineSignal(vehicle, title)?.let { (delta, text) ->
            score += delta
            signals += text
            if (delta <= -20) hardConflict = true
        }
        transmissionSignal(vehicle, title)?.let { (delta, text) ->
            score += delta
            signals += text
            if (delta <= -20) hardConflict = true
        }

        val requested = requestedPart.trim().uppercase(Locale.ROOT).replace(" ", "")
        var oemConfirmed = false
        if (oemLike.matches(requested)) {
            val compactTitle = offer.title.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
            val compactOem = requested.replace(Regex("[^A-Z0-9]"), "")
            if (compactOem.length >= 5 && compactTitle.contains(compactOem)) {
                score = maxOf(score, 99)
                oemConfirmed = true
                signals += "رقم OEM/القطعة مطابق مباشرة"
            } else {
                signals += "رقم OEM المطلوب غير ظاهر في النتيجة"
                score -= 8
            }
        }

        // A sibling/shared-platform identity expands discovery only. It never receives exact-car
        // confidence without direct OEM proof, even when the marketing name is very similar.
        if (!oemConfirmed && alias != null) {
            score = when (alias.relation) {
                VehicleAliasRelation.SHARED_PLATFORM -> minOf(score, 60)
                VehicleAliasRelation.SIBLING_MODEL -> minOf(score, 76)
                VehicleAliasRelation.REBADGED_SAME_VEHICLE -> minOf(score, 90)
                else -> score
            }
        }
        if (hardConflict && !oemConfirmed) score = minOf(score, 45)

        score = score.coerceIn(0, 100)
        val accepted = when {
            oemConfirmed -> true
            hardConflict -> false
            offer.vehicleMatch == PartsVehicleMatch.UNKNOWN -> false
            else -> score >= 58
        }
        val reason = when {
            oemConfirmed -> "توافق شبه مؤكد — رقم OEM/القطعة مطابق مباشرة"
            hardConflict -> "غير موصى به — يوجد تعارض تقني في السنة أو المحرك أو الفتيس"
            score >= 95 -> "توافق مرتفع جدًا — السيارة والجيل متطابقان بقوة"
            score >= 85 -> "توافق مرتفع — الموديل والجيل/السنة متوافقان"
            score >= 72 -> "توافق جيد — الاسم/الجيل مناسب مع تفاصيل تحتاج مراجعة"
            score >= 58 -> "توافق محتمل — راجع OEM والمحرك/الفتيس قبل الشراء"
            else -> "ثقة منخفضة — لا تعتمد على النتيجة قبل تأكيد OEM"
        }
        return PartsFitmentAssessment(score, accepted, reason, signals.distinct().take(5))
    }

    private fun generationSignal(vehicle: VehicleEntity, title: String): Pair<Int, String>? {
        val code = vehicle.generationCode?.trim()?.takeIf { it.length >= 2 } ?: return null
        val normalizedCode = normalizeVehicleText(code)
        return if (title.contains(normalizedCode)) 10 to "كود الجيل مطابق: $code" else null
    }

    private fun engineSignal(vehicle: VehicleEntity, title: String): Pair<Int, String>? {
        vehicle.engineCode?.trim()?.takeIf { it.length >= 3 }?.let { code ->
            val compactTitle = title.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
            val compactCode = code.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
            if (compactCode.length >= 3 && compactTitle.contains(compactCode)) return 18 to "كود المحرك مطابق: $code"
        }
        vehicle.engineName?.trim()?.takeIf { it.length >= 3 }?.let { name ->
            if (title.contains(normalizeVehicleText(name))) return 8 to "عائلة المحرك مطابقة: $name"
        }
        val cc = vehicle.engineCapacityCc ?: return null
        val liters = cc / 1000.0
        val candidates = Regex("(?i)(?<!\\d)([0-9]\\.[0-9])\\s*[lL]?(?!\\d)").findAll(title).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        val ccCandidates = Regex("(?<!\\d)([1-9][0-9]{2,3})\\s*(?:cc|سي\\s*سي)?(?!\\d)", RegexOption.IGNORE_CASE).findAll(title).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        if (candidates.isEmpty() && ccCandidates.isEmpty()) return null
        val match = candidates.any { kotlin.math.abs(it - liters) <= 0.11 } || ccCandidates.any { kotlin.math.abs(it - cc) <= 120 }
        return if (match) 6 to "سعة المحرك متوافقة" else -28 to "سعة محرك مختلفة مذكورة في النتيجة"
    }

    private fun transmissionSignal(vehicle: VehicleEntity, title: String): Pair<Int, String>? {
        vehicle.transmissionCode?.trim()?.takeIf { it.length >= 3 }?.let { code ->
            val compactTitle = title.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
            val compactCode = code.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
            if (compactCode.length >= 3 && compactTitle.contains(compactCode)) return 16 to "كود الفتيس مطابق: $code"
        }
        vehicle.transmissionName?.trim()?.takeIf { it.length >= 2 }?.let { name ->
            if (title.contains(normalizeVehicleText(name))) return 7 to "عائلة الفتيس مطابقة: $name"
        }
        val t = title.lowercase(Locale.ROOT)
        val detected = when {
            Regex("\\b(cvt)\\b").containsMatchIn(t) -> "CVT"
            Regex("\\b(dct|dsg)\\b").containsMatchIn(t) -> "DCT"
            Regex("\\b(mt|manual)\\b").containsMatchIn(t) || t.contains("مانيوال") -> "MT"
            Regex("\\b(at|automatic)\\b").containsMatchIn(t) || t.contains("اوتومات") -> "AT"
            else -> return null
        }
        val expected = when (vehicle.transmissionType) {
            TransmissionType.MANUAL -> "MT"
            TransmissionType.CVT -> "CVT"
            TransmissionType.DCT -> "DCT"
            else -> "AT"
        }
        return if (detected == expected) 4 to "نوع الفتيس متوافق" else -24 to "نوع فتيس مختلف مذكور في النتيجة"
    }
}
