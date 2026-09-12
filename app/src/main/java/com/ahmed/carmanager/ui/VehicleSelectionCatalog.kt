package com.ahmed.carmanager.ui

import java.util.Locale

/**
 * Offline-first vehicle selection seed used by the garage wizard.
 *
 * The catalog is intentionally versioned and conservative: it improves selection/fitment identity
 * without pretending to be a complete OEM catalog. Unknown vehicles always keep a manual-entry
 * escape hatch, while known generations can pre-fill a chassis/generation code before save.
 *
 * Future verified online catalogs can map into these same models without changing the UI contract.
 */
internal object VehicleSelectionCatalog {
    const val CATALOG_VERSION = 2

    data class Make(
        val id: String,
        val arName: String,
        val enName: String,
        val models: List<Model>,
        val confidence: Int = 80,
        val evidence: List<VehicleCatalogEvidence> = emptyList()
    ) : java.io.Serializable {
        val canonicalName: String get() = enName
        val displayName: String get() = "$arName • $enName"
        val searchText: String get() = normalize("$arName $enName $id")
    }

    data class Model(
        val id: String,
        val arName: String,
        val enName: String,
        val fromYear: Int? = null,
        val toYear: Int? = null,
        val generations: List<Generation> = emptyList(),
        val trimHints: List<String> = emptyList(),
        val aliases: List<String> = emptyList(),
        val marketAliases: List<VehicleCatalogAlias> = emptyList(),
        val confidence: Int = 80,
        val evidence: List<VehicleCatalogEvidence> = emptyList()
    ) : java.io.Serializable {
        val canonicalName: String get() = enName
        val displayName: String get() = if (arName.equals(enName, true)) enName else "$arName • $enName"
        val searchText: String get() = normalize("$arName $enName $id ${aliases.joinToString(" ")}")

        fun yearOptions(currentYear: Int = 2027): List<Int> {
            val min = fromYear ?: generations.minOfOrNull { it.fromYear } ?: 1990
            val max = minOf(toYear ?: generations.maxOfOrNull { it.toYear } ?: currentYear, currentYear)
            return if (min <= max) (max downTo min).toList() else emptyList()
        }

        fun generationsFor(year: Int?): List<Generation> = if (year == null) generations else generations.filter { year in it.fromYear..it.toYear }
    }

    data class Generation(
        val code: String? = null,
        val label: String,
        val fromYear: Int,
        val toYear: Int,
        val marketNames: List<String> = emptyList(),
        val trimHints: List<String> = emptyList(),
        val marketAliases: List<VehicleCatalogAlias> = emptyList(),
        val engines: List<VehicleCatalogEngine> = emptyList(),
        val transmissions: List<VehicleCatalogTransmission> = emptyList(),
        val confidence: Int = 80,
        val evidence: List<VehicleCatalogEvidence> = emptyList()
    ) : java.io.Serializable {
        val displayName: String get() = buildString {
            append(label)
            if (!code.isNullOrBlank() && !label.contains(code, true)) append(" • $code")
            append(" • $fromYear–$toYear")
        }
    }

    private fun model(
        id: String,
        ar: String,
        en: String,
        from: Int? = null,
        to: Int? = null,
        generations: List<Generation> = emptyList(),
        trims: List<String> = emptyList(),
        aliases: List<String> = emptyList()
    ) = Model(id, ar, en, from, to, generations, trims, aliases)

    private fun gen(code: String?, label: String, from: Int, to: Int, vararg marketNames: String) =
        Generation(code, label, from, to, marketNames.toList())

    private val bundledEvidence = VehicleCatalogEvidence(
        sourceId = "carmanager-bundled-seed-v$CATALOG_VERSION",
        title = "CarManager bundled vehicle seed",
        publisher = "CarManager",
        kind = VehicleCatalogSourceKind.CARMANAGER_CURATED,
        confidence = 65,
        note = "قاعدة مدمجة محافظة للعمل دون إنترنت؛ البيانات البعيدة الموثقة تتفوق عليها عند توفرها."
    )

    private fun withBundledProvenance(make: Make): Make = make.copy(
        evidence = make.evidence.ifEmpty { listOf(bundledEvidence) },
        confidence = minOf(make.confidence, bundledEvidence.safeConfidence),
        models = make.models.map { model ->
            model.copy(
                evidence = model.evidence.ifEmpty { listOf(bundledEvidence) },
                confidence = minOf(model.confidence, bundledEvidence.safeConfidence),
                generations = model.generations.map { generation ->
                    generation.copy(
                        evidence = generation.evidence.ifEmpty { listOf(bundledEvidence) },
                        confidence = minOf(generation.confidence, bundledEvidence.safeConfidence)
                    )
                }
            )
        }
    )

    private val seedMakes: List<Make> = listOf(
        Make("soueast", "ساوايست / سو إيست", "SouEast", listOf(
            model("dx3", "DX3", "DX3", 2017, 2025, aliases = listOf("Soueast DX3", "SouEast DX3", "ساوايست DX3", "سو إيست DX3")),
            model("dx5", "DX5", "DX5", 2019, 2025, aliases = listOf("Soueast DX5", "SouEast DX5")),
            model("dx7", "DX7", "DX7", 2015, 2025, aliases = listOf("Soueast DX7", "SouEast DX7")),
            model("dx8s", "DX8S", "DX8S", 2022, 2026, aliases = listOf("Soueast DX8S", "SouEast DX8S"))
        )),
        Make("kia", "كيا", "Kia", listOf(
            model("cerato", "سيراتو / جراند سيراتو", "Cerato", 2004, 2027, listOf(
                gen("LD", "Cerato LD", 2004, 2008),
                gen("TD", "Cerato TD", 2009, 2013),
                gen("YD", "Cerato / K3 YD", 2014, 2018, "K3"),
                gen("BD", "Grand Cerato / Cerato / Forte / K3", 2019, 2024, "Grand Cerato", "Forte", "K3")
            ), aliases = listOf("Grand Cerato", "جراند سيراتو", "K3", "Forte")) ,
            model("sportage", "سبورتاج", "Sportage", 2005, 2027),
            model("picanto", "بيكانتو", "Picanto", 2004, 2027),
            model("rio", "ريو", "Rio", 2000, 2027),
            model("carens", "كارينز", "Carens", 2000, 2027),
            model("sorento", "سورينتو", "Sorento", 2002, 2027),
            model("seltos", "سيلتوس", "Seltos", 2019, 2027)
        )),
        Make("hyundai", "هيونداي", "Hyundai", listOf(
            model("elantra", "إلنترا", "Elantra", 1995, 2027, listOf(
                gen("HD", "Elantra HD", 2007, 2011),
                gen("MD", "Elantra MD", 2012, 2016),
                gen("AD", "Elantra AD", 2017, 2020),
                gen("CN7", "Elantra CN7", 2021, 2027)
            ), aliases = listOf("Avante", "افانتي", "النترا")) ,
            model("accent", "أكسنت", "Accent", 1994, 2027),
            model("verna", "فيرنا", "Verna", 2003, 2020),
            model("tucson", "توسان", "Tucson", 2005, 2027),
            model("creta", "كريتا", "Creta", 2015, 2027),
            model("i10", "i10", "i10", 2008, 2027),
            model("i20", "i20", "i20", 2009, 2027)
        )),
        Make("chevrolet", "شيفروليه", "Chevrolet", listOf(
            model("captiva", "كابتيفا", "Captiva", 2007, 2027, listOf(
                gen(null, "Captiva I", 2007, 2018),
                gen(null, "Captiva II / Baojun 530 family", 2019, 2027, "Baojun 530", "Wuling Almaz", "MG Hector")
            ), aliases = listOf("Baojun 530", "Wuling Almaz", "MG Hector")) ,
            model("optra", "أوبترا", "Optra", 2004, 2027),
            model("aveo", "أفيو", "Aveo", 2003, 2020),
            model("cruze", "كروز", "Cruze", 2009, 2020),
            model("lanos", "لانوس", "Lanos", 1997, 2020),
            model("n300", "N300", "N300", 2010, 2027)
        )),
        Make("nissan", "نيسان", "Nissan", listOf(
            model("sunny", "صني", "Sunny", 1990, 2027, listOf(gen("N17", "Sunny / Almera / Versa N17", 2013, 2027, "Almera", "Versa")), aliases = listOf("Almera", "Versa", "صنى")),
            model("sentra", "سنترا", "Sentra", 1990, 2027),
            model("qashqai", "قشقاي", "Qashqai", 2007, 2027),
            model("juke", "جوك", "Juke", 2011, 2027),
            model("xtrail", "إكس-تريل", "X-Trail", 2001, 2027)
        )),
        Make("toyota", "تويوتا", "Toyota", listOf(
            model("corolla", "كورولا", "Corolla", 1990, 2027, listOf(
                gen("E170", "Corolla E170", 2014, 2019),
                gen("E210", "Corolla E210", 2020, 2027)
            )),
            model("yaris", "ياريس", "Yaris", 1999, 2027),
            model("fortuner", "فورتشنر", "Fortuner", 2005, 2027),
            model("rush", "راش", "Rush", 2006, 2027),
            model("chr", "C-HR", "C-HR", 2017, 2027)
        )),
        Make("mg", "إم جي", "MG", listOf(
            model("mg5", "MG 5", "MG 5", 2020, 2027),
            model("zs", "ZS", "ZS", 2018, 2027),
            model("hs", "HS", "HS", 2019, 2027),
            model("mg6", "MG 6", "MG 6", 2018, 2027),
            model("rx5", "RX5", "RX5", 2018, 2027)
        )),
        Make("renault", "رينو", "Renault", listOf(
            model("logan", "لوجان", "Logan", 2005, 2027),
            model("sandero", "سانديرو", "Sandero", 2008, 2027),
            model("duster", "داستر", "Duster", 2010, 2027),
            model("megane", "ميجان", "Megane", 1996, 2027),
            model("kadjar", "كادجار", "Kadjar", 2016, 2024)
        )),
        Make("peugeot", "بيجو", "Peugeot", listOf(
            model("301", "301", "301", 2013, 2027),
            model("2008", "2008", "2008", 2013, 2027),
            model("3008", "3008", "3008", 2009, 2027),
            model("408", "408", "408", 2011, 2027),
            model("508", "508", "508", 2011, 2027)
        )),
        Make("skoda", "سكودا", "Skoda", listOf(
            model("octavia", "أوكتافيا", "Octavia", 1997, 2027),
            model("kodiaq", "كودياك", "Kodiaq", 2017, 2027),
            model("karoq", "كاروك", "Karoq", 2018, 2027),
            model("superb", "سوبيرب", "Superb", 2002, 2027),
            model("fabia", "فابيا", "Fabia", 2000, 2027)
        )),
        Make("volkswagen", "فولكس فاجن", "Volkswagen", listOf(
            model("passat", "باسات", "Passat", 1990, 2027),
            model("tiguan", "تيجوان", "Tiguan", 2008, 2027),
            model("golf", "جولف", "Golf", 1990, 2027),
            model("jetta", "جيتا", "Jetta", 1990, 2027)
        )),
        Make("fiat", "فيات", "Fiat", listOf(
            model("tipo", "تيبو", "Tipo", 2016, 2027),
            model("500x", "500X", "500X", 2015, 2027),
            model("punto", "بونتو", "Punto", 1994, 2020)
        )),
        Make("opel", "أوبل", "Opel", listOf(
            model("astra", "أسترا", "Astra", 1992, 2027),
            model("insignia", "إنسيجنيا", "Insignia", 2009, 2022),
            model("corsa", "كورسا", "Corsa", 1993, 2027),
            model("crossland", "كروس لاند", "Crossland", 2017, 2027)
        )),
        Make("mitsubishi", "ميتسوبيشي", "Mitsubishi", listOf(
            model("lancer", "لانسر", "Lancer", 1990, 2018),
            model("eclipsecross", "إكليبس كروس", "Eclipse Cross", 2018, 2027),
            model("xpander", "إكسباندر", "Xpander", 2018, 2027)
        )),
        Make("suzuki", "سوزوكي", "Suzuki", listOf(
            model("swift", "سويفت", "Swift", 2005, 2027),
            model("ciaz", "سياز", "Ciaz", 2015, 2027),
            model("ertiga", "إرتيجا", "Ertiga", 2013, 2027),
            model("vitara", "فيتارا", "Vitara", 1990, 2027)
        )),
        Make("chery", "شيري", "Chery", listOf(
            model("arrizo5", "أريزو 5", "Arrizo 5", 2017, 2027),
            model("tiggo3", "تيجو 3", "Tiggo 3", 2015, 2027),
            model("tiggo7", "تيجو 7", "Tiggo 7", 2019, 2027),
            model("tiggo8", "تيجو 8", "Tiggo 8", 2020, 2027)
        )),
        Make("byd", "بي واي دي", "BYD", listOf(
            model("f3", "F3", "F3", 2006, 2027),
            model("l3", "L3", "L3", 2010, 2020)
        )),
        Make("geely", "جيلي", "Geely", listOf(
            model("emgrand", "إمجراند", "Emgrand", 2009, 2027),
            model("coolray", "كول راي", "Coolray", 2019, 2027)
        )),
        Make("honda", "هوندا", "Honda", listOf(
            model("civic", "سيفيك", "Civic", 1990, 2027),
            model("city", "سيتي", "City", 1996, 2027),
            model("crv", "CR-V", "CR-V", 1997, 2027)
        )),
        Make("mazda", "مازدا", "Mazda", listOf(
            model("mazda3", "مازدا 3", "Mazda 3", 2004, 2027),
            model("mazda6", "مازدا 6", "Mazda 6", 2003, 2027),
            model("cx3", "CX-3", "CX-3", 2016, 2027)
        ))
    ).map(::withBundledProvenance).sortedBy { it.enName }

    /**
     * Runtime catalog = bundled conservative seed + last validated remote catalog.
     * Remote data can change independently from the APK; a rejected/invalid pack never replaces
     * the last known-good snapshot.
     */
    val makes: List<Make>
        get() = VehicleCatalogRuntimeStore.mergedMakes(seedMakes)

    internal fun bundledSeedMakes(): List<Make> = seedMakes

    fun catalogAliasProfiles(brand: String, model: String, year: Int?, generationCode: String?): List<VehicleAliasProfile> {
        val make = findMake(brand) ?: return emptyList()
        val selectedModel = findModel(make, model) ?: return emptyList()
        val generation = generationFor(selectedModel, year, generationCode)
        val profiles = mutableListOf<VehicleAliasProfile>()

        selectedModel.aliases.forEach { alias ->
            profiles += VehicleAliasProfile(
                name = alias,
                relation = VehicleAliasRelation.GLOBAL_NAME,
                compatibleYears = selectedModel.fromYear?.let { f -> f..(selectedModel.toYear ?: year ?: f) },
                confidence = selectedModel.confidence.coerceIn(55, 95),
                evidence = selectedModel.evidence.firstOrNull()?.title ?: "vehicle catalog"
            )
        }
        selectedModel.marketAliases.forEach { alias -> profiles += alias.toFitmentProfile() }
        generation?.marketNames.orEmpty().forEach { alias ->
            profiles += VehicleAliasProfile(
                name = alias,
                relation = VehicleAliasRelation.GLOBAL_NAME,
                compatibleYears = generation?.let { it.fromYear..it.toYear },
                confidence = generation?.confidence?.coerceIn(60, 97) ?: 80,
                evidence = generation?.evidence?.firstOrNull()?.title ?: "vehicle catalog generation"
            )
        }
        generation?.marketAliases.orEmpty().forEach { alias -> profiles += alias.toFitmentProfile() }
        generation?.code?.takeIf { it.isNotBlank() }?.let { code ->
            profiles += VehicleAliasProfile(
                name = code,
                relation = VehicleAliasRelation.GENERATION_CODE,
                compatibleYears = generation.fromYear..generation.toYear,
                confidence = generation.confidence.coerceIn(70, 99),
                evidence = generation.evidence.firstOrNull()?.title ?: "vehicle catalog generation code"
            )
        }
        return profiles.distinctBy { normalizeVehicleText(it.name) }.sortedByDescending { it.confidence }
    }

    private fun VehicleCatalogAlias.toFitmentProfile(): VehicleAliasProfile {
        val fitmentRelation = when (relation) {
            VehicleCatalogAliasRelation.LOCAL_NAME -> VehicleAliasRelation.LOCAL_MARKET_NAME
            VehicleCatalogAliasRelation.GLOBAL_NAME -> VehicleAliasRelation.GLOBAL_NAME
            VehicleCatalogAliasRelation.MARKET_NAME -> VehicleAliasRelation.GLOBAL_NAME
            VehicleCatalogAliasRelation.GENERATION_CODE -> VehicleAliasRelation.GENERATION_CODE
            VehicleCatalogAliasRelation.REBADGED_SAME_VEHICLE -> VehicleAliasRelation.REBADGED_SAME_VEHICLE
            VehicleCatalogAliasRelation.SIBLING_MODEL -> VehicleAliasRelation.SIBLING_MODEL
            VehicleCatalogAliasRelation.SHARED_PLATFORM -> VehicleAliasRelation.SHARED_PLATFORM
            VehicleCatalogAliasRelation.SPELLING_VARIANT -> VehicleAliasRelation.LOCAL_MARKET_NAME
        }
        return VehicleAliasProfile(
            name = name,
            relation = fitmentRelation,
            market = market,
            compatibleYears = if (fromYear != null && toYear != null) fromYear..toYear else null,
            confidence = confidence.coerceIn(0, 100),
            evidence = evidence.joinToString("; ") { it.title }.ifBlank { "vehicle catalog" }
        )
    }

    fun findMake(value: String): Make? {
        val q = normalize(value)
        if (q.isBlank()) return null
        return makes.firstOrNull { make ->
            q == normalize(make.id) || q == normalize(make.arName) || q == normalize(make.enName) || make.searchText.contains(q)
        }
    }

    fun findModel(make: Make?, value: String): Model? {
        val q = normalize(value)
        if (make == null || q.isBlank()) return null
        return make.models.firstOrNull { model ->
            q == normalize(model.id) || q == normalize(model.arName) || q == normalize(model.enName) || model.searchText.contains(q)
        }
    }

    fun generationFor(model: Model?, year: Int?, code: String? = null): Generation? {
        if (model == null) return null
        val codeNorm = normalize(code.orEmpty())
        if (codeNorm.isNotBlank()) {
            model.generations.firstOrNull { normalize(it.code.orEmpty()) == codeNorm }?.let { return it }
        }
        val candidates = model.generationsFor(year)
        return candidates.singleOrNull()
    }

    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
        .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        .replace("ى", "ي")
        .replace(Regex("[^a-z0-9\u0600-\u06ff]+"), " ")
        .trim()
}
