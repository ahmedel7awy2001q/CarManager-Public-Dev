package com.ahmed.carmanager.ui

import android.content.Context
import android.content.SharedPreferences
import com.ahmed.carmanager.data.local.model.VehicleEntity

/**
 * Persists the Egyptian-market search identity derived from each saved vehicle profile.
 *
 * Synchronization is intentionally idempotent. Vehicle rows can change for reasons unrelated to
 * fitment (odometer, status, photo, etc.); those changes must not cause repeated SharedPreferences
 * writes or rebuild the stored market profile when the effective identity is unchanged.
 */
internal object VehicleMarketProfileStore {
    private const val PREF_PREFIX = "cm_parts_market_v090_"
    private const val KEY_FITMENT_SIGNATURE = "vehicle_fitment_signature"
    private const val KEY_PROFILE_ALIASES = "profile_vehicle_aliases"
    private const val KEY_PROFILE_SEARCH_ALIASES = "profile_vehicle_search_aliases"
    private const val KEY_PROFILE_RELATIONSHIPS = "profile_vehicle_alias_relationships"
    private const val KEY_LEARNED_ALIASES = "learned_vehicle_aliases"
    private const val KEY_LEARNED_ALIAS_PROFILES = "learned_vehicle_alias_profiles"
    private const val KEY_SYNCED_LEARNED_FINGERPRINT = "synced_learned_fingerprint"
    private const val KEY_ENABLED_SOURCES = "enabled_sources"
    private const val KEY_ENABLED_SOURCES_CATALOG_VERSION = "enabled_sources_catalog_version"
    private const val PROVIDER_CATALOG_VERSION = 4
    private const val EMPTY_LEARNED_FINGERPRINT = "-"

    fun prefs(context: Context, vehicleId: String): SharedPreferences {
        val prefs = context.getSharedPreferences("$PREF_PREFIX$vehicleId", Context.MODE_PRIVATE)
        migrateProviderCatalog(prefs)
        return prefs
    }

    /**
     * Keep newly introduced Egyptian-market providers discoverable for existing vehicles without
     * resetting sources the user already disabled. Each catalog step adds only providers that did
     * not exist in the older catalog. Fresh profiles start with the complete current provider set.
     *
     * Before catalog versioning existed, some profiles could persist the historical Auto Spare
     * singleton as their entire enabled set. That exact legacy shape is repaired once; broader old
     * selections are left untouched so we do not reinterpret an intentional user choice.
     */
    private fun migrateProviderCatalog(prefs: SharedPreferences) {
        val currentVersion = prefs.getInt(KEY_ENABLED_SOURCES_CATALOG_VERSION, 0)
        if (currentVersion >= PROVIDER_CATALOG_VERSION) return

        val hadSavedSelection = prefs.contains(KEY_ENABLED_SOURCES)
        val enabled = if (hadSavedSelection) {
            prefs.getStringSet(KEY_ENABLED_SOURCES, emptySet()).orEmpty().toMutableSet()
        } else {
            ExpandedPartsPriceEngine.capabilities.mapTo(linkedSetOf()) { it.id }
        }

        // v1 predates catalog-version tracking. Repair only its known singleton shape instead of
        // blindly re-enabling legacy providers for everyone.
        val legacyAutoSpareOnly = currentVersion == 0 &&
            hadSavedSelection &&
            enabled == setOf(PartsPriceEngine.AUTO_SPARE_ID)
        if (legacyAutoSpareOnly) {
            enabled += PartsPriceEngine.TAWFIQIA_ID
            enabled += PartsPriceEngine.ZAIT_FILTERS_ID
        }

        // v2 introduced GearHead. v3 adds Dawaasa and Ajyad to pre-existing source selections.
        // Do not re-add an older provider once its catalog version has already been applied; that
        // preserves a user's later decision to disable it.
        if (currentVersion < 2) enabled += ExpandedPartsPriceEngine.GEARHEAD_ID
        if (currentVersion < 3) {
            enabled += ExpandedPartsPriceEngine.DAWAASA_ID
            enabled += ExpandedPartsPriceEngine.AJYAD_ID
        }

        prefs.edit()
            .putStringSet(KEY_ENABLED_SOURCES, enabled)
            .putInt(KEY_ENABLED_SOURCES_CATALOG_VERSION, PROVIDER_CATALOG_VERSION)
            .apply()
    }

    fun sync(context: Context, vehicle: VehicleEntity) {
        val prefs = prefs(context, vehicle.vehicleId)
        val signature = fitmentSignature(vehicle)
        val previousSignature = prefs.getString(KEY_FITMENT_SIGNATURE, null)
        val identityChanged = previousSignature != null && previousSignature != signature
        val currentLearnedFingerprint = if (identityChanged) EMPTY_LEARNED_FINGERPRINT else learnedFingerprint(prefs)

        // Odometer, photo, ownership status and other non-fitment edits can emit the same vehicle
        // many times. Return before decoding profiles or resolving the identity graph when neither
        // the technical identity nor the learned market aliases changed.
        if (
            !identityChanged &&
            previousSignature == signature &&
            prefs.getString(KEY_SYNCED_LEARNED_FINGERPRINT, null) == currentLearnedFingerprint &&
            prefs.contains(KEY_PROFILE_ALIASES) &&
            prefs.contains(KEY_PROFILE_SEARCH_ALIASES) &&
            prefs.contains(KEY_PROFILE_RELATIONSHIPS)
        ) return

        // Learned aliases belong to one concrete fitment identity. If that identity changed, do not
        // feed old aliases into the resolver for this synchronization pass.
        val learnedProfiles = if (identityChanged) emptyList() else learnedProfiles(context, vehicle.vehicleId)
        val legacyLearned = if (identityChanged) emptySet() else
            prefs.getStringSet(KEY_LEARNED_ALIASES, emptySet()).orEmpty()
        val learned = legacyLearned + learnedProfiles.map { it.name }
        val identity = VehicleMarketIdentityResolver.resolve(vehicle, learned, learnedProfiles)

        val desiredAliases = identity.displayAliases.toSet()
        val desiredSearchAliases = identity.searchAliases.toSet()
        val desiredRelationships = identity.aliasProfiles.map { profile ->
            listOf(
                profile.name,
                profile.relation.name,
                profile.market.orEmpty(),
                profile.compatibleYears?.first?.toString().orEmpty(),
                profile.compatibleYears?.last?.toString().orEmpty(),
                profile.confidence.toString(),
                profile.evidence.orEmpty()
            ).joinToString("\u001F")
        }.toSet()

        val unchanged = !identityChanged &&
            previousSignature == signature &&
            prefs.getStringSet(KEY_PROFILE_ALIASES, emptySet()).orEmpty() == desiredAliases &&
            prefs.getStringSet(KEY_PROFILE_SEARCH_ALIASES, emptySet()).orEmpty() == desiredSearchAliases &&
            prefs.getStringSet(KEY_PROFILE_RELATIONSHIPS, emptySet()).orEmpty() == desiredRelationships

        if (unchanged) {
            // Legacy installs may already contain identical profile data but not the new fingerprint.
            // Persist only this tiny marker so future vehicle emissions can take the fast path above.
            if (prefs.getString(KEY_SYNCED_LEARNED_FINGERPRINT, null) != currentLearnedFingerprint) {
                prefs.edit().putString(KEY_SYNCED_LEARNED_FINGERPRINT, currentLearnedFingerprint).apply()
            }
            return
        }

        prefs.edit().apply {
            if (identityChanged) {
                remove(KEY_LEARNED_ALIASES)
                remove(KEY_LEARNED_ALIAS_PROFILES)
            }
            putString(KEY_FITMENT_SIGNATURE, signature)
            putStringSet(KEY_PROFILE_ALIASES, desiredAliases)
            putStringSet(KEY_PROFILE_SEARCH_ALIASES, desiredSearchAliases)
            putStringSet(KEY_PROFILE_RELATIONSHIPS, desiredRelationships)
            putString(KEY_SYNCED_LEARNED_FINGERPRINT, currentLearnedFingerprint)
        }.apply()
    }

    fun learnedAliases(context: Context, vehicleId: String): Set<String> =
        prefs(context, vehicleId).getStringSet(KEY_LEARNED_ALIASES, emptySet())?.toSet().orEmpty()

    fun saveLearnedAliases(context: Context, vehicleId: String, aliases: Set<String>) {
        val safe = aliases.filter { it.isNotBlank() }.toSet()
        val p = prefs(context, vehicleId)
        if (p.getStringSet(KEY_LEARNED_ALIASES, emptySet()).orEmpty() == safe) return
        // Do not update KEY_SYNCED_LEARNED_FINGERPRINT here. Leaving the old marker intentionally
        // forces exactly one identity rebuild the next time sync() is called.
        p.edit().putStringSet(KEY_LEARNED_ALIASES, safe).apply()
    }

    fun learnedProfiles(context: Context, vehicleId: String): List<VehicleAliasProfile> =
        prefs(context, vehicleId).getStringSet(KEY_LEARNED_ALIAS_PROFILES, emptySet()).orEmpty()
            .mapNotNull(::decodeLearnedProfile)
            .distinctBy { normalizeVehicleText(it.name) }
            .sortedByDescending { it.confidence }

    fun saveLearnedProfiles(context: Context, vehicleId: String, profiles: Collection<VehicleAliasProfile>) {
        val safe = profiles
            .filter { it.name.isNotBlank() }
            .distinctBy { normalizeVehicleText(it.name) }
            .sortedByDescending { it.confidence }
            .take(16)
        val encodedProfiles = safe.map(::encodeLearnedProfile).toSet()
        val aliases = safe.map { it.name }.toSet()
        val p = prefs(context, vehicleId)
        if (
            p.getStringSet(KEY_LEARNED_ALIAS_PROFILES, emptySet()).orEmpty() == encodedProfiles &&
            p.getStringSet(KEY_LEARNED_ALIASES, emptySet()).orEmpty() == aliases
        ) return
        // The stored sync fingerprint intentionally remains unchanged. sync() will see that the
        // learned data changed, rebuild once, then store the new fingerprint.
        p.edit()
            .putStringSet(KEY_LEARNED_ALIAS_PROFILES, encodedProfiles)
            .putStringSet(KEY_LEARNED_ALIASES, aliases)
            .apply()
    }

    private fun learnedFingerprint(prefs: SharedPreferences): String {
        val aliases = prefs.getStringSet(KEY_LEARNED_ALIASES, emptySet()).orEmpty()
            .filter { it.isNotBlank() }
            .sorted()
        val profiles = prefs.getStringSet(KEY_LEARNED_ALIAS_PROFILES, emptySet()).orEmpty()
            .filter { it.isNotBlank() }
            .sorted()
        if (aliases.isEmpty() && profiles.isEmpty()) return EMPTY_LEARNED_FINGERPRINT
        return buildString {
            append(aliases.joinToString("\u001E"))
            append("\u001D")
            append(profiles.joinToString("\u001E"))
        }
    }

    private fun encodeLearnedProfile(profile: VehicleAliasProfile): String = listOf(
        profile.name,
        profile.relation.name,
        profile.market.orEmpty(),
        profile.compatibleYears?.first?.toString().orEmpty(),
        profile.compatibleYears?.last?.toString().orEmpty(),
        profile.confidence.toString(),
        profile.evidence.orEmpty()
    ).joinToString("\u001F")

    private fun decodeLearnedProfile(raw: String): VehicleAliasProfile? {
        val p = raw.split("\u001F")
        if (p.size < 7 || p[0].isBlank()) return null
        val relation = runCatching { VehicleAliasRelation.valueOf(p[1]) }
            .getOrDefault(VehicleAliasRelation.LEARNED_MARKET_ALIAS)
        val start = p[3].toIntOrNull()
        val end = p[4].toIntOrNull()
        return VehicleAliasProfile(
            name = p[0],
            relation = relation,
            market = p[2].ifBlank { null },
            compatibleYears = if (start != null && end != null) start..end else null,
            confidence = p[5].toIntOrNull()?.coerceIn(0, 100) ?: relation.baseConfidence,
            evidence = p[6].ifBlank { null }
        )
    }

    private fun fitmentSignature(vehicle: VehicleEntity): String = listOf(
        normalizeVehicleText(vehicle.brand),
        normalizeVehicleText(vehicle.model),
        vehicle.year.toString(),
        normalizeVehicleText(vehicle.trim.orEmpty()),
        normalizeVehicleText(vehicle.generationCode.orEmpty()),
        normalizeVehicleText(vehicle.engineCode.orEmpty()),
        vehicle.engineCapacityCc?.toString().orEmpty(),
        normalizeVehicleText(vehicle.transmissionCode.orEmpty()),
        vehicle.transmissionType.name
    ).joinToString("|")
}
