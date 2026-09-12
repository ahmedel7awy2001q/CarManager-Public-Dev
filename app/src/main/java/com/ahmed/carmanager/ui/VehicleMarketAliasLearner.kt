package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity
import java.util.Locale

/**
 * Learns compact generation/chassis aliases from already vehicle-filtered Egyptian listings.
 *
 * The learner is deliberately conservative. It only considers short alpha-numeric tokens close
 * to the saved model name (BD, N17, E210, CN7, ZRE141...), and promotes a token when either:
 *  - the same listing contains a year/range relevant to the saved vehicle generation; or
 *  - the token is repeated by at least two different providers in the same search result.
 *
 * Learned aliases are stored per vehicle by the UI. They improve later searches but never replace
 * OEM/part-number verification.
 */
internal data class LearnedVehicleAlias(
    val name: String,
    val confidence: Int,
    val providerCount: Int,
    val yearRelevant: Boolean,
    val evidence: String
)

internal object VehicleMarketAliasLearner {
    private val yearRangeRegex = Regex("(?<!\\d)((?:19|20)\\d{2})\\s*[-–—/]\\s*((?:19|20)\\d{2})(?!\\d)")
    private val yearRegex = Regex("(?<!\\d)(?:19|20)\\d{2}(?!\\d)")
    private val tokenRegex = Regex("[\\p{L}\\p{N}]+")
    private val codeRegex = Regex("(?i)^(?=.*[A-Z])(?=.*(?:\\d|^[A-Z]{2,3}$))[A-Z0-9]{2,7}$")

    // Common component/technology/brand tokens that are not vehicle-generation codes.
    private val blockedCodes = setOf(
        "OEM", "ABS", "ESP", "ECU", "PCV", "LED", "HID", "AC", "DC", "AT", "MT",
        "CVT", "DCT", "DSG", "MPI", "GDI", "TDI", "TSI", "RPM", "TPMS", "SUV",
        "EGP", "LE", "KIA", "BMW", "MG", "BYD", "API", "DOT", "SAE", "VIN"
    )

    fun infer(
        vehicle: VehicleEntity,
        identity: VehicleMarketIdentity,
        offers: List<PartsPriceOffer>
    ): Set<String> = inferProfiles(vehicle, identity, offers).mapTo(linkedSetOf()) { it.name }

    /**
     * Same conservative learner, but keeps evidence and confidence so every learned market name is
     * auditable instead of becoming an unqualified string.
     */
    fun inferProfiles(
        vehicle: VehicleEntity,
        identity: VehicleMarketIdentity,
        offers: List<PartsPriceOffer>
    ): List<LearnedVehicleAlias> {
        if (offers.isEmpty()) return emptyList()

        data class Evidence(
            val providers: MutableSet<String> = linkedSetOf(),
            var yearRelevant: Boolean = false
        )

        val evidence = linkedMapOf<String, Evidence>()
        val normalizedModelTokens = identity.modelMarkers
            .flatMap { marker -> marker.split(Regex("[^\\p{L}\\p{N}]+")) }
            .map(::normalizeVehicleText)
            .filter { it.length >= 2 }
            .toSet()

        offers.forEach { offer ->
            val match = identity.classifyProductTitle(offer.title)
            if (!identity.acceptsStrict(match) && match.level != PartsVehicleMatch.MODEL_FAMILY) return@forEach

            val rawTokens = tokenRegex.findAll(offer.title).map { it.value }.toList()
            if (rawTokens.isEmpty()) return@forEach
            val normalizedTokens = rawTokens.map(::normalizeVehicleText)
            val modelIndexes = normalizedTokens.indices.filter { idx ->
                val token = normalizedTokens[idx]
                normalizedModelTokens.any { marker -> token == marker || token.contains(marker) || marker.contains(token) }
            }
            if (modelIndexes.isEmpty()) return@forEach

            val yearRelevant = titleMentionsGenerationYear(offer.title, vehicle.year, identity.compatibleModelYears)
            rawTokens.forEachIndexed { idx, rawToken ->
                if (modelIndexes.minOf { kotlin.math.abs(it - idx) } > 3) return@forEachIndexed
                val code = rawToken.uppercase(Locale.ROOT)
                if (!isGenerationCode(code)) return@forEachIndexed
                if (normalizedModelTokens.contains(normalizeVehicleText(code))) return@forEachIndexed
                val e = evidence.getOrPut(code) { Evidence() }
                e.providers += offer.providerId
                e.yearRelevant = e.yearRelevant || yearRelevant
            }
        }

        val existing = (identity.displayAliases + identity.searchAliases + identity.learnedAliases)
            .map(::normalizeVehicleText)
            .toSet()
        val base = identity.displayAliases
            .filter { it.isNotBlank() }
            .minByOrNull { it.length }
            ?: identity.canonicalName.substringBeforeLast(" ${vehicle.year}").trim()

        return evidence
            .filter { (_, e) -> e.yearRelevant || e.providers.size >= 2 }
            .mapNotNull { (code, e) ->
                val alias = if (normalizeVehicleText(base).contains(normalizeVehicleText(code))) base else "$base $code"
                if (normalizeVehicleText(alias) in existing) return@mapNotNull null
                val confidence = when {
                    e.yearRelevant && e.providers.size >= 2 -> 88
                    e.yearRelevant -> 82
                    e.providers.size >= 3 -> 84
                    else -> 76
                }
                LearnedVehicleAlias(
                    name = alias,
                    confidence = confidence,
                    providerCount = e.providers.size,
                    yearRelevant = e.yearRelevant,
                    evidence = buildString {
                        append("تعلم من ")
                        append(e.providers.size)
                        append(if (e.providers.size == 1) " مصدر" else " مصادر")
                        if (e.yearRelevant) append(" مع سنة/جيل متوافق")
                    }
                )
            }
            .sortedByDescending { it.confidence }
            .take(8)
    }

    private fun isGenerationCode(code: String): Boolean {
        if (code in blockedCodes) return false
        if (code.length !in 2..7) return false
        if (code.all(Char::isDigit)) return false
        if (yearRegex.matches(code)) return false
        return codeRegex.matches(code)
    }

    private fun titleMentionsGenerationYear(
        title: String,
        vehicleYear: Int,
        generationYears: IntRange?
    ): Boolean {
        yearRangeRegex.findAll(title).forEach { match ->
            val a = match.groupValues[1].toIntOrNull() ?: return@forEach
            val b = match.groupValues[2].toIntOrNull() ?: return@forEach
            val range = if (a <= b) a..b else b..a
            if (vehicleYear in range) return true
            if (generationYears != null && range.any { it in generationYears }) return true
        }
        val years = yearRegex.findAll(title).mapNotNull { it.value.toIntOrNull() }.toSet()
        if (vehicleYear in years) return true
        return generationYears != null && years.any { it in generationYears }
    }
}
