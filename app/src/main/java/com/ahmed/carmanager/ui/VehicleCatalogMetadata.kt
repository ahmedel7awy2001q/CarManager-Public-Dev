package com.ahmed.carmanager.ui

/** Provenance is kept with catalog facts so CarManager can explain where a vehicle fact came from. */
internal enum class VehicleCatalogSourceKind {
    OEM,
    OFFICIAL_DISTRIBUTOR,
    OFFICIAL_MARKET,
    INDUSTRY_DATABASE,
    PARTS_CATALOG,
    VERIFIED_RETAILER,
    CARMANAGER_CURATED,
    COMMUNITY,
    UNKNOWN
}

internal data class VehicleCatalogEvidence(
    val sourceId: String,
    val title: String,
    val publisher: String? = null,
    val url: String? = null,
    val market: String? = null,
    val kind: VehicleCatalogSourceKind = VehicleCatalogSourceKind.UNKNOWN,
    val confidence: Int = 50,
    val retrievedAtEpochMs: Long? = null,
    val note: String? = null
) : java.io.Serializable {
    val safeConfidence: Int get() = confidence.coerceIn(0, 100)
}

internal enum class VehicleCatalogAliasRelation {
    LOCAL_NAME,
    GLOBAL_NAME,
    MARKET_NAME,
    GENERATION_CODE,
    REBADGED_SAME_VEHICLE,
    SIBLING_MODEL,
    SHARED_PLATFORM,
    SPELLING_VARIANT
}

internal data class VehicleCatalogAlias(
    val name: String,
    val relation: VehicleCatalogAliasRelation,
    val market: String? = null,
    val language: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val confidence: Int = 70,
    val evidence: List<VehicleCatalogEvidence> = emptyList()
) : java.io.Serializable

internal data class VehicleCatalogEngine(
    val id: String,
    val name: String,
    val code: String? = null,
    val capacityCc: Int? = null,
    val fuel: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val markets: List<String> = emptyList(),
    val confidence: Int = 70,
    val evidence: List<VehicleCatalogEvidence> = emptyList()
) : java.io.Serializable

internal data class VehicleCatalogTransmission(
    val id: String,
    val name: String,
    val code: String? = null,
    val type: String? = null,
    val gears: Int? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val markets: List<String> = emptyList(),
    val confidence: Int = 70,
    val evidence: List<VehicleCatalogEvidence> = emptyList()
) : java.io.Serializable

internal data class VehicleCatalogRuntimeState(
    val activeVersion: Long = 0,
    val schemaVersion: Int = 1,
    val packId: String? = null,
    val publishedAtEpochMs: Long? = null,
    val lastCheckedAtEpochMs: Long? = null,
    val sourceCount: Int = 0,
    val remoteMakeCount: Int = 0,
    val status: Status = Status.SEED_ONLY,
    val message: String? = null
) : java.io.Serializable {
    internal enum class Status { SEED_ONLY, CACHED, CHECKING, UPDATED, UP_TO_DATE, FAILED }
}
