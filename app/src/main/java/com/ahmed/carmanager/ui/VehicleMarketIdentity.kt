package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity
import java.text.Normalizer
import java.util.Locale

/**
 * Market identity for a saved vehicle.
 *
 * The user saves one vehicle name, while Egyptian spare-parts stores may use a local trade name,
 * a generation/chassis code, or a different spelling. This resolver keeps those aliases together
 * and gives the parts search a single vehicle identity to search and validate against.
 *
 * Important: aliases are used to FILTER results, not to claim OEM fitment. Final fitment must still
 * be confirmed by OEM/part number when the seller provides one.
 */
internal data class VehicleMarketIdentity(
    val canonicalName: String,
    val displayAliases: List<String>,
    val searchAliases: List<String>,
    val modelMarkers: Set<String>,
    val exactGenerationMarkers: Set<String>,
    val blockedGenerationMarkers: Set<String>,
    val compatibleModelYears: IntRange?,
    val removableVehicleTokens: Set<String>,
    val hasKnownGeneration: Boolean = false,
    val learnedAliases: List<String> = emptyList(),
    val aliasProfiles: List<VehicleAliasProfile> = emptyList()
) {
    fun searchQueries(part: String, maxQueries: Int = 8): List<String> {
        val cleanPart = sanitizePartQuery(part).ifBlank { part.trim() }
        val candidates = linkedSetOf<String>()

        // Do not spend all query slots on spelling variants of the local name. Take strong aliases
        // from different relationship classes first so a Captiva search can also try Baojun 530,
        // and a Cerato BD search can try Forte/K3 market names without losing the saved identity.
        aliasProfiles
            .sortedWith(compareByDescending<VehicleAliasProfile> { it.confidence }.thenBy { it.relation.ordinal })
            .take(6)
            .forEach { profile ->
                if (profile.name.isNotBlank()) candidates += "$cleanPart ${profile.name}".trim()
            }
        searchAliases.forEach { alias ->
            if (alias.isNotBlank()) candidates += "$cleanPart $alias".trim()
        }
        if (candidates.isEmpty()) candidates += listOf(cleanPart, canonicalName).filter { it.isNotBlank() }.joinToString(" ")
        return candidates.filter { it.isNotBlank() }.take(maxQueries)
    }

    fun sanitizePartQuery(raw: String): String {
        val removable = removableVehicleTokens
        return PartQueryText.normalizeSeparators(raw)
            .split(Regex("\\s+"))
            .filter { token ->
                val normalized = normalizeVehicleText(token)
                normalized.isNotBlank() && normalized !in removable
            }
            .joinToString(" ")
            .trim()
            .ifBlank { raw.trim() }
    }

    fun acceptsStrict(match: VehicleTitleMatch): Boolean {
        if (!match.accepted) return false
        return when (match.level) {
            PartsVehicleMatch.VEHICLE_CATALOG,
            PartsVehicleMatch.EXACT_GENERATION,
            PartsVehicleMatch.MODEL_AND_YEAR -> true
            // For a known generation, a bare family name such as "Cerato" is too broad and can
            // mix TD/YD/BD parts. Unknown models retain the family fallback so the system remains
            // useful while aliases are learned from Egyptian listings.
            PartsVehicleMatch.MODEL_FAMILY -> !hasKnownGeneration
            PartsVehicleMatch.UNKNOWN -> false
        }
    }

    fun classifyProductTitle(title: String): VehicleTitleMatch {
        val normalized = normalizeVehicleText(title)
        if (normalized.isBlank()) return VehicleTitleMatch.NONE

        val hasModel = modelMarkers.any { marker -> containsMarker(normalized, marker) }
        if (!hasModel) return VehicleTitleMatch.NONE

        val exact = exactGenerationMarkers.firstOrNull { marker -> containsMarker(normalized, marker) }
        val blocked = blockedGenerationMarkers.firstOrNull { marker -> containsMarker(normalized, marker) }
        if (blocked != null && exact == null) return VehicleTitleMatch.NONE

        val mentionedYears = YEAR_REGEX.findAll(normalized)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1980..2100 }
            .toSet()

        if (mentionedYears.isNotEmpty() && compatibleModelYears != null) {
            val hasCompatibleYear = mentionedYears.any { it in compatibleModelYears }
            if (!hasCompatibleYear && exact == null) return VehicleTitleMatch.NONE
        }

        if (exact != null) {
            return VehicleTitleMatch(
                accepted = true,
                level = PartsVehicleMatch.EXACT_GENERATION,
                matchedAlias = displayMarker(exact)
            )
        }
        if (mentionedYears.isNotEmpty() && compatibleModelYears != null) {
            return VehicleTitleMatch(
                accepted = true,
                level = PartsVehicleMatch.MODEL_AND_YEAR,
                matchedAlias = modelMarkers.firstOrNull { containsMarker(normalized, it) }?.let(::displayMarker)
            )
        }
        return VehicleTitleMatch(
            accepted = true,
            level = PartsVehicleMatch.MODEL_FAMILY,
            matchedAlias = modelMarkers.firstOrNull { containsMarker(normalized, it) }?.let(::displayMarker)
        )
    }

    private fun displayMarker(marker: String): String = marker.uppercase(Locale.ROOT)
}

internal data class VehicleTitleMatch(
    val accepted: Boolean,
    val level: PartsVehicleMatch,
    val matchedAlias: String? = null
) {
    companion object {
        val NONE = VehicleTitleMatch(false, PartsVehicleMatch.UNKNOWN, null)
    }
}

internal object VehicleMarketIdentityResolver {
    private data class Rule(
        val brandKeys: Set<String>,
        val modelKeys: Set<String>,
        val years: IntRange,
        val canonicalModel: String,
        val aliases: List<String>,
        val modelMarkers: Set<String>,
        val exactMarkers: Set<String>,
        val blockedMarkers: Set<String> = emptySet()
    )

    /**
     * High-confidence Egyptian-market generation aliases. Unknown cars still get strict fallback
     * matching using the brand/model/year saved by the user, so they never fall back to "all cars".
     * The catalog is deliberately data-driven so more local names can be added without DB changes.
     */
    private val rules = listOf(
        Rule(
            brandKeys = setOf("kia", "كيا"),
            modelKeys = setOf("cerato", "سيراتو", "grand cerato", "جراند سيراتو"),
            years = 2018..2022,
            canonicalModel = "Grand Cerato / Cerato BD",
            aliases = listOf(
                "Kia Grand Cerato", "كيا جراند سيراتو",
                "Kia Cerato BD", "كيا سيراتو BD",
                "Cerato BD", "سيراتو BD",
                "Grand Cerato", "جراند سيراتو",
                "Cerato", "سيراتو"
            ),
            modelMarkers = setOf("cerato", "سيراتو"),
            exactMarkers = setOf(
                "grand cerato", "جراند سيراتو", "cerato bd", "سيراتو bd"
            ),
            blockedMarkers = setOf(
                "cerato td", "سيراتو td", "cerato yd", "سيراتو yd",
                "cerato k3", "سيراتو k3", "cerato ld", "سيراتو ld"
            )
        ),
        Rule(
            brandKeys = setOf("kia", "كيا"),
            modelKeys = setOf("cerato", "سيراتو", "k3"),
            years = 2014..2018,
            canonicalModel = "Cerato K3 / YD",
            aliases = listOf("Kia Cerato K3", "كيا سيراتو K3", "Cerato K3", "سيراتو K3", "Cerato YD", "سيراتو YD", "Cerato", "سيراتو"),
            modelMarkers = setOf("cerato", "سيراتو", "k3"),
            exactMarkers = setOf("cerato k3", "سيراتو k3", "cerato yd", "سيراتو yd"),
            blockedMarkers = setOf("cerato bd", "سيراتو bd", "grand cerato", "جراند سيراتو", "cerato td", "سيراتو td")
        ),
        Rule(
            brandKeys = setOf("kia", "كيا"),
            modelKeys = setOf("cerato", "سيراتو"),
            years = 2009..2013,
            canonicalModel = "Cerato TD",
            aliases = listOf("Kia Cerato TD", "كيا سيراتو TD", "Cerato TD", "سيراتو TD", "Cerato", "سيراتو"),
            modelMarkers = setOf("cerato", "سيراتو"),
            exactMarkers = setOf("cerato td", "سيراتو td"),
            blockedMarkers = setOf("cerato bd", "سيراتو bd", "cerato yd", "سيراتو yd", "cerato k3", "سيراتو k3")
        ),
        Rule(
            brandKeys = setOf("hyundai", "هيونداي", "هيونداى"),
            modelKeys = setOf("elantra", "النترا", "إلنترا"),
            years = 2012..2016,
            canonicalModel = "Elantra MD",
            aliases = listOf("Hyundai Elantra MD", "هيونداي النترا MD", "Elantra MD", "النترا MD", "Elantra", "النترا"),
            modelMarkers = setOf("elantra", "النترا"),
            exactMarkers = setOf("elantra md", "النترا md"),
            blockedMarkers = setOf("elantra ad", "النترا ad", "elantra cn7", "النترا cn7", "elantra hd", "النترا hd")
        ),
        Rule(
            brandKeys = setOf("hyundai", "هيونداي", "هيونداى"),
            modelKeys = setOf("elantra", "النترا", "إلنترا"),
            years = 2017..2020,
            canonicalModel = "Elantra AD",
            aliases = listOf("Hyundai Elantra AD", "هيونداي النترا AD", "Elantra AD", "النترا AD", "Elantra", "النترا"),
            modelMarkers = setOf("elantra", "النترا"),
            exactMarkers = setOf("elantra ad", "النترا ad"),
            blockedMarkers = setOf("elantra md", "النترا md", "elantra cn7", "النترا cn7", "elantra hd", "النترا hd")
        ),
        Rule(
            brandKeys = setOf("hyundai", "هيونداي", "هيونداى"),
            modelKeys = setOf("elantra", "النترا", "إلنترا", "cn7"),
            years = 2021..2027,
            canonicalModel = "Elantra CN7",
            aliases = listOf("Hyundai Elantra CN7", "هيونداي النترا CN7", "Elantra CN7", "النترا CN7", "Elantra", "النترا"),
            modelMarkers = setOf("elantra", "النترا", "cn7"),
            exactMarkers = setOf("elantra cn7", "النترا cn7"),
            blockedMarkers = setOf("elantra md", "النترا md", "elantra ad", "النترا ad", "elantra hd", "النترا hd")
        ),
        Rule(
            brandKeys = setOf("nissan", "نيسان"),
            modelKeys = setOf("sunny", "صني", "صنى", "n17"),
            years = 2013..2027,
            canonicalModel = "Sunny N17",
            aliases = listOf("Nissan Sunny N17", "نيسان صني N17", "Sunny N17", "صني N17", "Sunny", "صني"),
            modelMarkers = setOf("sunny", "صني", "n17"),
            exactMarkers = setOf("sunny n17", "صني n17")
        ),
        Rule(
            brandKeys = setOf("toyota", "تويوتا"),
            modelKeys = setOf("corolla", "كورولا"),
            years = 2014..2019,
            canonicalModel = "Corolla E170",
            aliases = listOf("Toyota Corolla E170", "تويوتا كورولا E170", "Corolla E170", "كورولا E170", "Corolla", "كورولا"),
            modelMarkers = setOf("corolla", "كورولا"),
            exactMarkers = setOf("corolla e170", "كورولا e170"),
            blockedMarkers = setOf("corolla e210", "كورولا e210")
        ),
        Rule(
            brandKeys = setOf("toyota", "تويوتا"),
            modelKeys = setOf("corolla", "كورولا", "e210"),
            years = 2020..2027,
            canonicalModel = "Corolla E210",
            aliases = listOf("Toyota Corolla E210", "تويوتا كورولا E210", "Corolla E210", "كورولا E210", "Corolla", "كورولا"),
            modelMarkers = setOf("corolla", "كورولا"),
            exactMarkers = setOf("corolla e210", "كورولا e210"),
            blockedMarkers = setOf("corolla e170", "كورولا e170")
        ),
        Rule(
            brandKeys = setOf("kia", "كيا"),
            modelKeys = setOf("sportage", "سبورتاج"),
            years = 2016..2021,
            canonicalModel = "Sportage QL",
            aliases = listOf("Kia Sportage QL", "كيا سبورتاج QL", "Sportage QL", "سبورتاج QL", "Sportage", "سبورتاج"),
            modelMarkers = setOf("sportage", "سبورتاج"),
            exactMarkers = setOf("sportage ql", "سبورتاج ql"),
            blockedMarkers = setOf("sportage sl", "سبورتاج sl", "sportage nq5", "سبورتاج nq5")
        ),
        Rule(
            brandKeys = setOf("kia", "كيا"),
            modelKeys = setOf("sportage", "سبورتاج", "nq5"),
            years = 2022..2027,
            canonicalModel = "Sportage NQ5",
            aliases = listOf("Kia Sportage NQ5", "كيا سبورتاج NQ5", "Sportage NQ5", "سبورتاج NQ5", "Sportage", "سبورتاج"),
            modelMarkers = setOf("sportage", "سبورتاج", "nq5"),
            exactMarkers = setOf("sportage nq5", "سبورتاج nq5"),
            blockedMarkers = setOf("sportage ql", "سبورتاج ql", "sportage sl", "سبورتاج sl")
        ),
        Rule(
            brandKeys = setOf("hyundai", "هيونداي", "هيونداى"),
            modelKeys = setOf("tucson", "توسان"),
            years = 2016..2021,
            canonicalModel = "Tucson TL",
            aliases = listOf("Hyundai Tucson TL", "هيونداي توسان TL", "Tucson TL", "توسان TL", "Tucson", "توسان"),
            modelMarkers = setOf("tucson", "توسان"),
            exactMarkers = setOf("tucson tl", "توسان tl"),
            blockedMarkers = setOf("tucson nx4", "توسان nx4")
        ),
        Rule(
            brandKeys = setOf("hyundai", "هيونداي", "هيونداى"),
            modelKeys = setOf("tucson", "توسان", "nx4"),
            years = 2022..2027,
            canonicalModel = "Tucson NX4",
            aliases = listOf("Hyundai Tucson NX4", "هيونداي توسان NX4", "Tucson NX4", "توسان NX4", "Tucson", "توسان"),
            modelMarkers = setOf("tucson", "توسان", "nx4"),
            exactMarkers = setOf("tucson nx4", "توسان nx4"),
            blockedMarkers = setOf("tucson tl", "توسان tl")
        )
    )

    fun resolve(
        vehicle: VehicleEntity,
        learnedAliases: Collection<String> = emptyList(),
        learnedAliasProfiles: Collection<VehicleAliasProfile> = emptyList()
    ): VehicleMarketIdentity {
        val brand = normalizeVehicleText(vehicle.brand)
        val model = normalizeVehicleText(vehicle.model)
        val display = normalizeVehicleText(vehicle.displayName.orEmpty())
        val trim = normalizeVehicleText(vehicle.trim.orEmpty())

        val rule = rules.firstOrNull { rule ->
            vehicle.year in rule.years &&
                rule.brandKeys.map(::normalizeVehicleText).any { key -> brand.contains(key) } &&
                rule.modelKeys.map(::normalizeVehicleText).any { key -> model.contains(key) || display.contains(key) || trim.contains(key) }
        }

        val rawModel = vehicle.model.trim()
        val simplifiedRawModel = simplifyModelName(rawModel)
        val aliases = linkedSetOf<String>()
        if (rule != null) aliases += rule.aliases
        aliases += listOfNotNull(
            vehicle.displayName?.takeIf { it.isNotBlank() },
            listOf(vehicle.brand, rawModel).filter { it.isNotBlank() }.joinToString(" "),
            rawModel.takeIf { it.isNotBlank() },
            simplifiedRawModel.takeIf { it.isNotBlank() && !it.equals(rawModel, ignoreCase = true) }
        )
        val safeLearnedAliases = learnedAliases
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 2..80 }
            .distinctBy(::normalizeVehicleText)
            .take(12)
        aliases += safeLearnedAliases

        val yearAliases = linkedSetOf<String>()
        val preferredBase = rule?.aliases?.take(6).orEmpty().ifEmpty { aliases.take(4) }

        // Start with the strongest local trade/generation names, then explicitly try every model-year
        // in the same Egyptian-market generation. Example: a 2021 Grand Cerato also searches
        // Cerato 2018 / 2019 / 2020 / 2021 / 2022 and Cerato BD instead of relying on one spelling.
        preferredBase.take(2).forEach { alias ->
            yearAliases += "$alias ${vehicle.year}"
            yearAliases += alias
        }
        rule?.modelMarkers
            ?.map(::normalizeVehicleText)
            ?.filter { it.length >= 3 }
            ?.take(2)
            ?.forEach { marker ->
                rule.years.forEach { modelYear -> yearAliases += "$marker $modelYear" }
            }
        preferredBase.drop(2).forEach { alias ->
            yearAliases += alias
            yearAliases += "$alias ${vehicle.year}"
        }
        // Learned aliases come from repeated/year-qualified Egyptian listings and are kept per car.
        // They are searched early on later runs, without changing the Room schema.
        safeLearnedAliases.forEach { alias ->
            yearAliases += "$alias ${vehicle.year}"
            yearAliases += alias
        }

        val rawModelMarkers = linkedSetOf<String>()
        rawModelMarkers += normalizeVehicleText(rawModel)
        rawModelMarkers += normalizeVehicleText(simplifiedRawModel)
        rule?.modelMarkers?.mapTo(rawModelMarkers, ::normalizeVehicleText)
        rawModelMarkers.removeAll { it.isBlank() || it.length < 2 }

        val exactMarkers = linkedSetOf<String>()
        rule?.exactMarkers?.mapTo(exactMarkers, ::normalizeVehicleText)
        vehicle.generationCode?.trim()?.takeIf { it.length >= 2 }?.let { code ->
            exactMarkers += normalizeVehicleText(code)
            yearAliases += "$rawModel $code"
            yearAliases += "${vehicle.brand} $rawModel $code"
            aliases += "$rawModel $code"
        }
        safeLearnedAliases.mapTo(exactMarkers, ::normalizeVehicleText)
        val blockedMarkers = rule?.blockedMarkers?.mapTo(linkedSetOf(), ::normalizeVehicleText).orEmpty()

        val removableTokens = linkedSetOf<String>()
        listOf(vehicle.brand, rawModel, simplifiedRawModel, vehicle.displayName.orEmpty(), vehicle.trim.orEmpty())
            .plus(rule?.aliases.orEmpty())
            .plus(safeLearnedAliases)
            .forEach { text ->
                normalizeVehicleText(text)
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                    .filter { it.length >= 2 }
                    .forEach { removableTokens += it }
            }
        removableTokens += vehicle.year.toString()

        val canonicalModel = rule?.canonicalModel ?: rawModel
        val canonical = listOf(vehicle.brand.trim(), canonicalModel, vehicle.year.toString())
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val curatedProfiles = (
            VehicleAliasKnowledgeBase.profiles(vehicle) +
                VehicleSelectionCatalog.catalogAliasProfiles(
                    brand = vehicle.brand,
                    model = vehicle.model,
                    year = vehicle.year,
                    generationCode = vehicle.generationCode
                ) +
                StorefrontVehicleAliasCatalog.marketProfiles(vehicle)
            ).distinctBy { normalizeVehicleText(it.name) }.sortedByDescending { it.confidence }
        val learnedByName = learnedAliasProfiles.associateBy { normalizeVehicleText(it.name) }
        val learnedProfiles = safeLearnedAliases.map { alias ->
            learnedByName[normalizeVehicleText(alias)] ?: VehicleAliasProfile(
                name = alias,
                relation = VehicleAliasRelation.LEARNED_MARKET_ALIAS,
                market = "Egyptian listings",
                compatibleYears = rule?.years ?: (vehicle.year - 1)..(vehicle.year + 1),
                confidence = 76,
                evidence = "learned conservatively from repeated/year-qualified listings"
            )
        } + learnedAliasProfiles.filter { profile ->
            safeLearnedAliases.none { normalizeVehicleText(it) == normalizeVehicleText(profile.name) }
        }
        val aliasProfiles = (curatedProfiles + learnedProfiles)
            .distinctBy { normalizeVehicleText(it.name) }
            .sortedByDescending { it.confidence }

        // Rich profiles widen discovery using verified global/rebadge names while keeping strict
        // generation filtering. Sibling/platform names become candidates, but only pass strict
        // filtering when year/OEM/technical signals raise their fitment score.
        aliasProfiles.forEach { profile ->
            val normalizedAlias = normalizeVehicleText(profile.name)
            aliases += profile.name
            yearAliases += profile.name
            yearAliases += "${profile.name} ${vehicle.year}"
            rawModelMarkers += normalizedAlias
            if (profile.relation in setOf(VehicleAliasRelation.GENERATION_CODE, VehicleAliasRelation.GLOBAL_NAME, VehicleAliasRelation.REBADGED_SAME_VEHICLE)) {
                exactMarkers += normalizedAlias
            }
        }
        val curatedYearRanges = aliasProfiles
            .filter { it.relation !in setOf(VehicleAliasRelation.SIBLING_MODEL, VehicleAliasRelation.SHARED_PLATFORM) }
            .mapNotNull { it.compatibleYears }
        val resolvedYears = rule?.years ?: if (curatedYearRanges.isNotEmpty()) {
            curatedYearRanges.minOf { it.first }..curatedYearRanges.maxOf { it.last }
        } else (vehicle.year - 1)..(vehicle.year + 1)

        return VehicleMarketIdentity(
            canonicalName = canonical,
            displayAliases = aliases.distinct().take(16),
            searchAliases = yearAliases.distinct().take(36),
            modelMarkers = rawModelMarkers,
            exactGenerationMarkers = exactMarkers,
            blockedGenerationMarkers = blockedMarkers,
            compatibleModelYears = resolvedYears,
            removableVehicleTokens = removableTokens,
            hasKnownGeneration = rule != null || !vehicle.generationCode.isNullOrBlank() || curatedProfiles.any { it.relation == VehicleAliasRelation.GENERATION_CODE },
            learnedAliases = safeLearnedAliases,
            aliasProfiles = aliasProfiles
        )
    }

    private fun simplifyModelName(model: String): String {
        val noise = setOf("grand", "new", "all", "newshape", "جراند", "نيو", "الجديده", "الجديدة")
        return model.split(Regex("\\s+"))
            .filter { normalizeVehicleText(it) !in noise }
            .joinToString(" ")
            .trim()
    }
}

internal fun normalizeVehicleText(text: String): String {
    val noMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    return noMarks.lowercase(Locale.ROOT)
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun containsMarker(normalizedText: String, normalizedMarker: String): Boolean {
    if (normalizedMarker.isBlank()) return false
    if (' ' in normalizedMarker) return normalizedText.contains(normalizedMarker)
    val tokens = normalizedText.split(Regex("[^\\p{L}\\p{N}]+"))
    return normalizedMarker in tokens
}

private val YEAR_REGEX = Regex("(?<!\\d)(?:19|20)\\d{2}(?!\\d)")
