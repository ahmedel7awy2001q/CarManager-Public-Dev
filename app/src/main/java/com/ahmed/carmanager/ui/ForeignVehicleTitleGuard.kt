package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity

/**
 * Rejects an offer only when the title explicitly names another vehicle brand and gives no signal
 * for the selected car. Generic titles (for example a bare NGK part number) stay available as
 * low-confidence price discovery results instead of being incorrectly removed.
 */
internal object ForeignVehicleTitleGuard {
    private data class BrandFamily(val key: String, val aliases: Set<String>)

    private val brandFamilies = listOf(
        family("renault", "renault", "رينو"),
        family("kia", "kia", "كيا"),
        family("hyundai", "hyundai", "هيونداي", "هيونداى"),
        family("nissan", "nissan", "نيسان"),
        family("toyota", "toyota", "تويوتا"),
        family("chevrolet", "chevrolet", "شيفروليه", "شفروليه"),
        family("opel", "opel", "اوبل", "أوبل"),
        family("fiat", "fiat", "فيات"),
        family("ford", "ford", "فورد"),
        family("honda", "honda", "هوندا"),
        family("jeep", "jeep", "جيب"),
        family("mitsubishi", "mitsubishi", "ميتسوبيشي"),
        family("mazda", "mazda", "مازدا"),
        family("peugeot", "peugeot", "بيجو"),
        family("citroen", "citroen", "سيتروين", "ستروين"),
        family("volkswagen", "volkswagen", "فولكس واجن", "فولكس"),
        family("skoda", "skoda", "سكودا"),
        family("seat", "seat", "سيات"),
        family("suzuki", "suzuki", "سوزوكي"),
        family("subaru", "subaru", "سوبارو"),
        family("lada", "lada", "لادا"),
        family("bmw", "bmw", "بي ام دبليو"),
        family("mercedes", "mercedes", "مرسيدس"),
        family("audi", "audi", "اودي", "أودي"),
        family("chery", "chery", "شيري"),
        family("geely", "geely", "جيلي"),
        family("mg", "mg", "ام جي", "إم جي"),
        family("byd", "byd", "بي واي دي"),
        family("baojun", "baojun", "باوجون"),
        family("wuling", "wuling", "وولينج")
    )

    fun isClearlyForeign(
        vehicle: VehicleEntity,
        identity: VehicleMarketIdentity,
        title: String
    ): Boolean {
        if (identity.classifyProductTitle(title).accepted) return false
        val normalizedTitle = normalizeVehicleText(title)
        if (normalizedTitle.isBlank()) return false

        val selectedNames = (
            StorefrontVehicleAliasCatalog.egyptianBrandAliases(vehicle.brand) + vehicle.brand
            ).mapTo(linkedSetOf(), ::normalizeVehicleText)

        val selectedFamilies = brandFamilies.filter { family ->
            family.aliases.any { alias -> selectedNames.any { selected -> containsAlias(selected, alias) || containsAlias(alias, selected) } }
        }
        val mentionsSelectedBrand = selectedFamilies.any { family -> family.aliases.any { containsAlias(normalizedTitle, it) } }
        if (mentionsSelectedBrand) return false

        return brandFamilies
            .filterNot { it in selectedFamilies }
            .any { family -> family.aliases.any { containsAlias(normalizedTitle, it) } }
    }

    private fun family(key: String, vararg aliases: String): BrandFamily = BrandFamily(
        key = key,
        aliases = aliases.mapTo(linkedSetOf(), ::normalizeVehicleText)
    )

    private fun containsAlias(text: String, alias: String): Boolean {
        if (alias.isBlank()) return false
        if (' ' in alias) return text.contains(alias)
        return alias in text.split(Regex("[^\\p{L}\\p{N}]+"))
    }
}
