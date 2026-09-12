package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity

/**
 * Store-facing vehicle names that differ from the name saved by the owner.
 *
 * This catalog deliberately lives outside Room: changing a store label must never require a
 * database migration or rewrite the user's vehicle. The saved make/model remains authoritative;
 * these aliases are only used to widen discovery and to resolve store catalog routes.
 */
internal data class StorefrontVehicleAliasProfile(
    val providerId: String,
    val brandAliases: List<String>,
    val modelAliases: List<String>,
    val compatibleYears: IntRange? = null,
    val confidence: Int = 90
)

internal object StorefrontVehicleAliasCatalog {
    fun marketProfiles(vehicle: VehicleEntity): List<VehicleAliasProfile> {
        val y = vehicle.year
        val brand = normalizeVehicleText(vehicle.brand)
        val model = normalizeVehicleText("${vehicle.model} ${vehicle.displayName.orEmpty()}")
        val out = mutableListOf<VehicleAliasProfile>()

        fun add(
            name: String,
            relation: VehicleAliasRelation = VehicleAliasRelation.LOCAL_MARKET_NAME,
            years: IntRange? = null,
            confidence: Int = relation.baseConfidence,
            evidence: String
        ) {
            if (years == null || y in years) {
                out += VehicleAliasProfile(
                    name = name,
                    relation = relation,
                    market = "Egypt storefronts",
                    compatibleYears = years,
                    confidence = confidence,
                    evidence = evidence
                )
            }
        }

        // Auto Spare currently lists the Egyptian second-generation Logan as "نيو لوجان"
        // while many owners save it simply as Renault Logan. Keep both names searchable.
        if ((brand.contains("renault") || brand.contains("رينو")) &&
            (model.contains("logan") || model.contains("لوجان"))
        ) {
            if (y in 2015..2022) {
                add("Renault New Logan", years = 2015..2022, confidence = 98, evidence = "Auto Spare Egyptian catalog name")
                add("رينو نيو لوجان", years = 2015..2022, confidence = 98, evidence = "Auto Spare Egyptian catalog name")
                add("New Logan", years = 2015..2022, confidence = 96, evidence = "Egyptian market/store label")
                add("نيو لوجان", years = 2015..2022, confidence = 96, evidence = "Egyptian market/store label")
            }
            add("Renault Logan", years = 2004..2027, confidence = 100, evidence = "saved model family")
            add("رينو لوجان", years = 2004..2027, confidence = 96, evidence = "Arabic store/model family label")
            add("Logan", years = 2004..2027, confidence = 92, evidence = "model family label")
            add("لوجان", years = 2004..2027, confidence = 92, evidence = "Arabic model family label")
        }

        return out
            .groupBy { normalizeVehicleText(it.name) }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.confidence } }
            .sortedByDescending { it.confidence }
    }

    fun providerProfiles(providerId: String, vehicle: VehicleEntity): List<StorefrontVehicleAliasProfile> {
        val brand = normalizeVehicleText(vehicle.brand)
        val model = normalizeVehicleText("${vehicle.model} ${vehicle.displayName.orEmpty()}")
        val out = mutableListOf<StorefrontVehicleAliasProfile>()

        if (providerId == PartsPriceEngine.AUTO_SPARE_ID) {
            if ((brand.contains("renault") || brand.contains("رينو")) &&
                (model.contains("logan") || model.contains("لوجان"))
            ) {
                if (vehicle.year in 2015..2022) {
                    out += StorefrontVehicleAliasProfile(
                        providerId = providerId,
                        brandAliases = listOf("رينو", "Renault"),
                        modelAliases = listOf("نيو لوجان", "New Logan", "لوجان", "Logan"),
                        compatibleYears = 2015..2022,
                        confidence = 100
                    )
                } else {
                    out += StorefrontVehicleAliasProfile(
                        providerId = providerId,
                        brandAliases = listOf("رينو", "Renault"),
                        modelAliases = listOf("لوجان", "Logan"),
                        compatibleYears = 2004..2027,
                        confidence = 94
                    )
                }
            }

            // Preserve the already-supported Cerato routes while allowing the same resolver to
            // discover them from Auto Spare's live brand/model pages instead of hard-coded URLs.
            if ((brand.contains("kia") || brand.contains("كيا")) &&
                (model.contains("cerato") || model.contains("سيراتو") || model.contains("k3"))
            ) {
                val aliases = when (vehicle.year) {
                    in 2018..2022 -> listOf("جراند سيراتو", "Grand Cerato", "سيراتو", "Cerato")
                    in 2014..2018 -> listOf("K3", "سيراتو K3", "Cerato K3", "سيراتو", "Cerato")
                    in 2009..2013 -> listOf("سيراتو TD", "Cerato TD", "سيراتو", "Cerato")
                    else -> listOf("سيراتو", "Cerato")
                }
                out += StorefrontVehicleAliasProfile(
                    providerId = providerId,
                    brandAliases = listOf("كيا", "Kia"),
                    modelAliases = aliases,
                    compatibleYears = (vehicle.year - 2)..(vehicle.year + 2),
                    confidence = 96
                )
            }
        }

        if (out.isEmpty()) {
            out += StorefrontVehicleAliasProfile(
                providerId = providerId,
                brandAliases = listOf(vehicle.brand),
                modelAliases = listOfNotNull(
                    vehicle.model.takeIf { it.isNotBlank() },
                    vehicle.displayName?.takeIf { it.isNotBlank() }
                ),
                compatibleYears = (vehicle.year - 1)..(vehicle.year + 1),
                confidence = 70
            )
        }

        return out
    }

    /** Common Arabic brand spellings used by Egyptian storefronts. */
    fun egyptianBrandAliases(brand: String): List<String> {
        val key = normalizeVehicleText(brand)
        val aliases = when {
            key.contains("renault") || key.contains("رينو") -> listOf("رينو", "Renault")
            key.contains("kia") || key.contains("كيا") -> listOf("كيا", "Kia")
            key.contains("hyundai") || key.contains("هيوندا") -> listOf("هيونداي", "هيونداى", "Hyundai")
            key.contains("nissan") || key.contains("نيسان") -> listOf("نيسان", "Nissan")
            key.contains("toyota") || key.contains("تويوتا") -> listOf("تويوتا", "Toyota")
            key.contains("chevrolet") || key.contains("شيفرول") -> listOf("شيفروليه", "Chevrolet")
            key.contains("opel") || key.contains("اوبل") -> listOf("اوبل", "أوبل", "Opel")
            key.contains("fiat") || key.contains("فيات") -> listOf("فيات", "Fiat")
            key.contains("ford") || key.contains("فورد") -> listOf("فورد", "Ford")
            key.contains("honda") || key.contains("هوندا") -> listOf("هوندا", "Honda")
            key.contains("mitsubishi") || key.contains("ميتسوبيشي") -> listOf("ميتسوبيشي", "Mitsubishi")
            key.contains("mazda") || key.contains("مازدا") -> listOf("مازدا", "Mazda")
            key.contains("peugeot") || key.contains("بيجو") -> listOf("بيجو", "Peugeot")
            key.contains("citroen") || key.contains("سيتروين") || key.contains("ستروين") -> listOf("سيتروين", "ستروين", "Citroen")
            key.contains("volkswagen") || key.contains("فولكس") -> listOf("فولكس واجن", "Volkswagen")
            key.contains("skoda") || key.contains("سكودا") -> listOf("سكودا", "Skoda")
            key.contains("seat") || key.contains("سيات") -> listOf("سيات", "Seat")
            key == "mg" || key.contains("ام جي") -> listOf("ام جي", "MG")
            key.contains("chery") || key.contains("شيري") -> listOf("شيري", "Chery")
            key.contains("geely") || key.contains("جيلي") -> listOf("جيلي", "Geely")
            key.contains("suzuki") || key.contains("سوزوكي") -> listOf("سوزوكي", "Suzuki")
            key.contains("bmw") || key.contains("بي ام") -> listOf("بي ام دبليو", "BMW")
            key.contains("mercedes") || key.contains("مرسيدس") -> listOf("مرسيدس", "Mercedes")
            key.contains("audi") || key.contains("اودي") -> listOf("اودي", "Audi")
            key.contains("jeep") || key.contains("جيب") -> listOf("جيب", "Jeep")
            key.contains("subaru") || key.contains("سوبارو") -> listOf("سوبارو", "Subaru")
            key.contains("lada") || key.contains("لادا") -> listOf("لادا", "Lada")
            else -> listOf(brand)
        }
        return aliases.distinctBy(::normalizeVehicleText)
    }
}
