package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.TransmissionType
import java.util.Locale

internal enum class TechnicalSuggestionSourceKind {
    CURATED_CATALOG,
    SAVED_PROFILE,
    USER_CONFIRMED
}

internal data class TechnicalSuggestionSource(
    val title: String,
    val detail: String,
    val kind: TechnicalSuggestionSourceKind = TechnicalSuggestionSourceKind.CURATED_CATALOG
)

/**
 * A candidate powertrain/generation identity for a vehicle. Null technical fields are intentional:
 * CarManager must prefer "unknown" over inventing a code. This object is designed so a future
 * online/official-catalog provider can feed the same UI without changing the form contract.
 */
internal data class VehicleTechnicalSuggestion(
    val generationCode: String? = null,
    val generationName: String? = null,
    val engineCapacityCc: Int? = null,
    val engineName: String? = null,
    val engineCode: String? = null,
    val fuelType: FuelType? = null,
    val tankCapacityLiters: Double? = null,
    val passengerCapacity: Int? = null,
    val transmissionType: TransmissionType? = null,
    val transmissionName: String? = null,
    val transmissionCode: String? = null,
    val confidence: Int,
    val reason: String,
    val sources: List<TechnicalSuggestionSource>
) {
    val confidenceLabel: String
        get() = when {
            confidence >= 95 -> "ثقة مرتفعة جدًا"
            confidence >= 85 -> "ثقة مرتفعة"
            confidence >= 70 -> "ثقة جيدة"
            confidence >= 55 -> "ثقة متوسطة"
            else -> "تحتاج مراجعة"
        }
}

/**
 * Offline-first technical resolver. It deliberately returns only curated, bounded rules and never
 * guesses missing codes. Rules can have multiple candidates for the same model/year when trim or
 * market can change the powertrain; the user sees every candidate and its confidence before saving.
 *
 * The current product priority is ordinary passenger cars, especially gasoline cars. When the
 * catalog can prove that one generation/year has exactly one engine and one transmission candidate,
 * CarManager also emits a combined suggestion so one tap can fill the useful powertrain identity
 * without making up a pairing when several variants exist.
 */
internal object VehicleTechnicalSuggestionEngine {
    fun suggest(
        brand: String,
        model: String,
        year: Int?,
        trim: String? = null,
        vin: String? = null
    ): List<VehicleTechnicalSuggestion> {
        val y = year ?: return emptyList()
        val b = norm(brand)
        val m = norm(model)
        val t = norm(trim.orEmpty())
        val out = mutableListOf<VehicleTechnicalSuggestion>()

        // Remote catalog facts are evaluated first. Only sourced records are converted to technical
        // suggestions; the bundled rules below remain the offline fallback.
        out += catalogSuggestions(brand, model, y)

        // Kia Cerato / Grand Cerato BD commonly sold in Egypt. Keep the rule year-bounded.
        if (matchesBrand(b, "kia", "كيا") && (m.contains("cerato") || m.contains("سيراتو")) && y in 2019..2024) {
            out += VehicleTechnicalSuggestion(
                generationCode = "BD",
                generationName = "Cerato / Forte BD",
                engineCapacityCc = 1591,
                engineName = "Gamma 1.6 MPI",
                engineCode = "G4FG",
                transmissionType = TransmissionType.AUTOMATIC,
                transmissionName = "6AT",
                transmissionCode = "A6GF1",
                confidence = if (m.contains("grand") || m.contains("جراند")) 96 else 92,
                reason = "الماركة والموديل والسنة تقع داخل جيل BD، ومواصفات 1.6 MPI/6AT هي التركيبة الشائعة لهذا الطراز في السوق المصري.",
                sources = listOf(
                    TechnicalSuggestionSource(
                        "قاعدة CarManager الفنية الموثقة",
                        "قاعدة محلية محددة بالجيل والسنة؛ لا تُنشئ أكوادًا عند غياب التحقق."
                    )
                )
            )
        }

        // MG 5 sold in Egypt: suggest only the high-level facts we can safely use in fitment.
        // Engine/transmission codes intentionally stay null until a verified catalog/VIN source exists.
        if (matchesBrand(b, "mg", "ام جي", "إم جي", "mgmotor") && (m == "5" || m.contains("mg5") || m.contains("ام جي 5") || m.contains("إم جي 5")) && y in 2020..2027) {
            out += VehicleTechnicalSuggestion(
                generationName = "MG 5 (Egypt market family)",
                engineCapacityCc = 1500,
                engineName = "1.5L naturally aspirated",
                engineCode = null,
                transmissionType = TransmissionType.CVT,
                transmissionName = "CVT",
                transmissionCode = null,
                confidence = 86,
                reason = "الموديل والسنة يطابقان عائلة MG 5 المنتشرة في مصر. تركنا أكواد المحرك والفتيس فارغة بدل التخمين لأنها قد تختلف حسب السوق والفئة.",
                sources = listOf(
                    TechnicalSuggestionSource(
                        "قاعدة CarManager للسوق المصري",
                        "مطابقة على مستوى الموديل/السنة/السعة/نوع ناقل الحركة؛ الأكواد التفصيلية تتطلب مصدرًا فنيًا أدق."
                    )
                )
            )
        }

        // Hyundai Elantra generations. Powertrain details are not forced because Egypt trims vary.
        if (matchesBrand(b, "hyundai", "هيونداي", "هيونداى") && (m.contains("elantra") || m.contains("النترا"))) {
            val gen = when (y) {
                in 2012..2016 -> "MD"
                in 2017..2020 -> "AD"
                in 2021..2027 -> "CN7"
                else -> null
            }
            if (gen != null) out += VehicleTechnicalSuggestion(
                generationCode = gen,
                generationName = "Elantra $gen",
                confidence = 93,
                reason = "الماركة والموديل والسنة تحدد الجيل بدرجة قوية، بينما المحرك والفتيس يظلان غير محددين لأنهما يختلفان حسب الفئة والسوق.",
                sources = listOf(TechnicalSuggestionSource("قاعدة CarManager لأكواد الأجيال", "مطابقة جيل محددة بنطاق سنة الإنتاج."))
            )
        }

        // Nissan Sunny N17 family sold for a long span in Egypt.
        if (matchesBrand(b, "nissan", "نيسان") && (m.contains("sunny") || m.contains("صني") || m.contains("صنى")) && y in 2013..2027) {
            out += VehicleTechnicalSuggestion(
                generationCode = "N17",
                generationName = "Sunny / Almera / Versa N17 family",
                confidence = 94,
                reason = "اسم الموديل والسنة يطابقان عائلة N17. تفاصيل المحرك/الفتيس لا تُملأ تلقائيًا قبل التحقق من الفئة.",
                sources = listOf(TechnicalSuggestionSource("قاعدة CarManager لأكواد الأجيال", "ربط Sunny باسم وكود الجيل العالمي N17."))
            )
        }

        // Toyota Corolla generation-only suggestion.
        if (matchesBrand(b, "toyota", "تويوتا") && (m.contains("corolla") || m.contains("كورولا"))) {
            val gen = when (y) {
                in 2014..2019 -> "E170"
                in 2020..2027 -> "E210"
                else -> null
            }
            if (gen != null) out += VehicleTechnicalSuggestion(
                generationCode = gen,
                generationName = "Corolla $gen",
                confidence = 92,
                reason = "السنة تحدد عائلة الجيل، أما المحرك والفتيس فيختلفان باختلاف السوق والفئة لذلك لا يتم تخمينهما.",
                sources = listOf(TechnicalSuggestionSource("قاعدة CarManager لأكواد الأجيال", "مطابقة الجيل حسب سنة الإنتاج."))
            )
        }

        // Chevrolet Captiva II sold by Chevrolet in Egypt. The Egyptian-market powertrain
        // is a 1.5L turbo paired with CVT across the documented 2020+ local range. Seat count
        // varies by trim (5/7), so it is only filled when the trim itself identifies the layout.
        if (matchesBrand(b, "chevrolet", "شيفروليه", "شفروليه") && m.contains("captiva") && y in 2020..2026) {
            val seats = when {
                t.contains("7seat") || t.contains("7 seat") || t.contains("7 مقاعد") || t.contains("7مقاعد") -> 7
                t.contains("5seat") || t.contains("5 seat") || t.contains("5 مقاعد") || t.contains("5مقاعد") -> 5
                else -> null
            }
            out += VehicleTechnicalSuggestion(
                generationName = "Captiva II / Baojun 530 family",
                engineCapacityCc = 1500,
                engineName = "1.5 Turbo",
                fuelType = FuelType.GASOLINE_92,
                tankCapacityLiters = 52.0,
                passengerCapacity = seats,
                transmissionType = TransmissionType.CVT,
                transmissionName = "CVT",
                transmissionCode = null,
                confidence = 96,
                reason = "مواصفة موثقة للسوق المصري: محرك 1.5 تيربو مع ناقل CVT وبنزين 92 وخزان 52 لتر. عدد المقاعد يختلف بين فئات 5 و7 مقاعد لذلك لا يتم تخمينه دون تحديد الفئة.",
                sources = listOf(
                    TechnicalSuggestionSource(
                        "Hatla2ee Egypt — Chevrolet Captiva 2020",
                        "مواصفات السوق المصري: 1500 Turbo • CVT • بنزين 92 • خزان 52 لتر • https://eg.hatla2ee.com/en/new-car/chevrolet/captiva/23708"
                    ),
                    TechnicalSuggestionSource(
                        "ContactCars Egypt — Chevrolet Captiva",
                        "مواصفات محلية تؤكد تقنية ناقل الحركة CVT وسعة 1500 تيربو وخزان 52 لتر • https://www.contactcars.com/ar/new-cars/chevrolet-captiva/602cda63dd12a1b44f6604e4"
                    )
                )
            )
        }

        // VIN presence raises the usefulness of a candidate, but this offline engine does not decode it.
        // Never pretend that a VIN was decoded when it was not.
        val vinBonus = vin?.trim()?.takeIf { it.length == 17 } != null
        return out
            .map { s ->
                if (!vinBonus) s else s.copy(
                    confidence = (s.confidence + 1).coerceAtMost(97),
                    reason = s.reason + " تم إدخال VIN وسيُستخدم كمفتاح تحقق عند توفر موفر فك شاسيه موثوق؛ لم يتم افتراض فكّه الآن."
                )
            }
            .distinctBy { listOf(it.generationCode, it.engineCode, it.transmissionCode, it.engineName, it.transmissionName, it.fuelType, it.tankCapacityLiters, it.passengerCapacity).joinToString("|") }
            .sortedWith(
                compareByDescending<VehicleTechnicalSuggestion> { it.rankingScore() }
                    .thenByDescending { it.confidence }
            )
    }

    private fun catalogSuggestions(brand: String, model: String, year: Int): List<VehicleTechnicalSuggestion> {
        val make = VehicleSelectionCatalog.findMake(brand) ?: return emptyList()
        val selectedModel = VehicleSelectionCatalog.findModel(make, model) ?: return emptyList()
        val generations = selectedModel.generationsFor(year).filter { it.evidence.isNotEmpty() }
        if (generations.isEmpty()) return emptyList()

        val result = mutableListOf<VehicleTechnicalSuggestion>()
        generations.forEach { generation ->
            val genSources = generation.evidence.toTechnicalSources()
            val matchingEngines = generation.engines.filter { yearAllowed(year, it.fromYear, it.toYear) }
            val matchingTransmissions = generation.transmissions.filter { yearAllowed(year, it.fromYear, it.toYear) }

            result += VehicleTechnicalSuggestion(
                generationCode = generation.code,
                generationName = generation.label,
                confidence = generation.confidence.coerceIn(0, 99),
                reason = "مطابقة من قاعدة السيارات القابلة للتحديث: الماركة والموديل والسنة تقع داخل هذا الجيل. مصدر المعلومة محفوظ ويمكن مراجعته.",
                sources = genSources
            )

            // Only create a one-tap combined powertrain suggestion when the catalog has exactly one
            // eligible engine and one eligible transmission for this generation/year. This avoids
            // manufacturing a pairing when several trims or markets are possible.
            if (matchingEngines.size == 1 && matchingTransmissions.size == 1) {
                val engine = matchingEngines.single()
                val transmission = matchingTransmissions.single()
                result += VehicleTechnicalSuggestion(
                    generationCode = generation.code,
                    generationName = generation.label,
                    engineCapacityCc = engine.capacityCc,
                    engineName = engine.name,
                    engineCode = engine.code,
                    transmissionType = transmission.type.toTransmissionType(),
                    transmissionName = transmission.name,
                    transmissionCode = transmission.code,
                    confidence = minOf(generation.confidence, engine.confidence, transmission.confidence).coerceIn(0, 99),
                    reason = "تكوين فني مجمع من قاعدة السيارات: لهذا الجيل والسنة يوجد محرك واحد وناقل حركة واحد موثقان في البيانات الحالية، لذلك يمكن عرضهما معًا دون تخمين ربط بين فئات متعددة.",
                    sources = (generation.evidence + engine.evidence + transmission.evidence).toTechnicalSources()
                )
            }

            matchingEngines.forEach { engine ->
                result += VehicleTechnicalSuggestion(
                    generationCode = generation.code,
                    generationName = generation.label,
                    engineCapacityCc = engine.capacityCc,
                    engineName = engine.name,
                    engineCode = engine.code,
                    confidence = minOf(generation.confidence, engine.confidence).coerceIn(0, 99),
                    reason = "محرك موثق لهذا الجيل/السنة في قاعدة السيارات المحدثة. لا يتم اعتباره تكوين السيارة النهائي قبل اختيار الفئة أو وجود دليل أقوى مثل VIN/OEM.",
                    sources = (engine.evidence.ifEmpty { generation.evidence }).toTechnicalSources()
                )
            }

            matchingTransmissions.forEach { transmission ->
                result += VehicleTechnicalSuggestion(
                    generationCode = generation.code,
                    generationName = generation.label,
                    transmissionType = transmission.type.toTransmissionType(),
                    transmissionName = transmission.name,
                    transmissionCode = transmission.code,
                    confidence = minOf(generation.confidence, transmission.confidence).coerceIn(0, 99),
                    reason = "ناقل حركة موثق لهذا الجيل/السنة في قاعدة السيارات المحدثة. يظل اختيار الفئة/السوق عامل تحقق قبل الحفظ النهائي.",
                    sources = (transmission.evidence.ifEmpty { generation.evidence }).toTechnicalSources()
                )
            }
        }
        return result
    }

    /**
     * Ranking is deliberately usefulness-aware, not confidence-only. A complete, still-high-confidence
     * gasoline passenger-car profile should be offered before a generation-only row when both are
     * valid. The bonus is bounded so a materially weaker candidate cannot beat a strong verified one.
     */
    private fun VehicleTechnicalSuggestion.rankingScore(): Int {
        var completeness = 0
        if (!generationCode.isNullOrBlank() || !generationName.isNullOrBlank()) completeness += 1
        if (engineCapacityCc != null || !engineName.isNullOrBlank()) completeness += 2
        if (!engineCode.isNullOrBlank()) completeness += 1
        if (transmissionType != null || !transmissionName.isNullOrBlank()) completeness += 2
        if (!transmissionCode.isNullOrBlank()) completeness += 1
        if (fuelType != null) completeness += 1
        if (tankCapacityLiters != null) completeness += 1
        if (passengerCapacity != null) completeness += 1
        return confidence + completeness.coerceAtMost(6) * 2
    }

    private fun List<VehicleCatalogEvidence>.toTechnicalSources(): List<TechnicalSuggestionSource> =
        distinctBy { it.sourceId }.take(4).map { evidence ->
            TechnicalSuggestionSource(
                title = evidence.title,
                detail = listOfNotNull(evidence.publisher, evidence.market, evidence.url, evidence.note).joinToString(" • ").ifBlank { "مصدر محفوظ بقاعدة السيارات" },
                kind = TechnicalSuggestionSourceKind.CURATED_CATALOG
            )
        }

    private fun yearAllowed(year: Int, from: Int?, to: Int?): Boolean =
        (from == null || year >= from) && (to == null || year <= to)

    private fun String?.toTransmissionType(): TransmissionType? = when (this?.trim()?.uppercase(Locale.ROOT)) {
        "AT", "AUTOMATIC", "AUTO" -> TransmissionType.AUTOMATIC
        "MT", "MANUAL" -> TransmissionType.MANUAL
        "CVT" -> TransmissionType.CVT
        "DCT", "DSG", "DUAL_CLUTCH" -> TransmissionType.DCT
        null, "" -> null
        else -> TransmissionType.OTHER
    }

    private fun norm(value: String): String = value.trim().lowercase(Locale.ROOT)
        .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        .replace(Regex("[^a-z0-9\u0600-\u06ff]+"), " ")
        .trim()

    private fun matchesBrand(value: String, vararg aliases: String): Boolean = aliases.any { a ->
        val n = norm(a)
        value == n || value.contains(n)
    }
}
