package com.ahmed.carmanager.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory, last-known-good remote vehicle catalog.
 *
 * The APK always ships with [VehicleSelectionCatalog]'s seed. A validated remote snapshot is merged
 * on top, so an offline device still works and a bad remote update cannot erase bundled coverage.
 */
internal object VehicleCatalogRuntimeStore {
    private val lock = Any()
    @Volatile private var remoteMakes: List<VehicleSelectionCatalog.Make> = emptyList()
    @Volatile private var cachedSeed: List<VehicleSelectionCatalog.Make>? = null
    @Volatile private var cachedMergedMakes: List<VehicleSelectionCatalog.Make>? = null
    private val _state = MutableStateFlow(VehicleCatalogRuntimeState())
    val state: StateFlow<VehicleCatalogRuntimeState> = _state.asStateFlow()

    fun applyRemote(makes: List<VehicleSelectionCatalog.Make>, state: VehicleCatalogRuntimeState) {
        synchronized(lock) {
            remoteMakes = makes
            cachedSeed = null
            cachedMergedMakes = null
            _state.value = state.copy(remoteMakeCount = makes.size)
        }
    }

    fun updateState(transform: (VehicleCatalogRuntimeState) -> VehicleCatalogRuntimeState) {
        _state.value = transform(_state.value)
    }

    fun clearRemote(message: String? = null) {
        synchronized(lock) {
            remoteMakes = emptyList()
            cachedSeed = null
            cachedMergedMakes = null
            _state.value = VehicleCatalogRuntimeState(message = message)
        }
    }

    fun mergedMakes(seed: List<VehicleSelectionCatalog.Make>): List<VehicleSelectionCatalog.Make> {
        cachedMergedMakes?.let { cached -> if (cachedSeed === seed) return cached }
        return synchronized(lock) {
            cachedMergedMakes?.let { cached -> if (cachedSeed === seed) return@synchronized cached }
            val remote = remoteMakes
            val merged = if (remote.isEmpty()) {
                seed
            } else {
                val byId = linkedMapOf<String, VehicleSelectionCatalog.Make>()
                seed.forEach { byId[key(it.id)] = it }
                remote.forEach { incoming ->
                    val k = key(incoming.id)
                    byId[k] = byId[k]?.let { mergeMake(it, incoming) } ?: incoming
                }
                byId.values.sortedBy { it.enName.lowercase() }
            }
            cachedSeed = seed
            cachedMergedMakes = merged
            merged
        }
    }

    private fun mergeMake(seed: VehicleSelectionCatalog.Make, remote: VehicleSelectionCatalog.Make): VehicleSelectionCatalog.Make {
        val models = linkedMapOf<String, VehicleSelectionCatalog.Model>()
        seed.models.forEach { models[key(it.id)] = it }
        remote.models.forEach { m -> models[key(m.id)] = models[key(m.id)]?.let { mergeModel(it, m) } ?: m }
        val preferred = if (remote.confidence >= seed.confidence) remote else seed
        return seed.copy(
            arName = preferred.arName.ifBlank { seed.arName },
            enName = preferred.enName.ifBlank { seed.enName },
            models = models.values.sortedBy { it.enName.lowercase() },
            confidence = maxOf(seed.confidence, remote.confidence),
            evidence = mergeEvidence(seed.evidence, remote.evidence)
        )
    }

    private fun mergeModel(seed: VehicleSelectionCatalog.Model, remote: VehicleSelectionCatalog.Model): VehicleSelectionCatalog.Model {
        val generations = seed.generations.toMutableList()
        remote.generations.forEach { g ->
            val idx = generations.indexOfFirst { sameGeneration(it, g) }
            if (idx >= 0) generations[idx] = mergeGeneration(generations[idx], g) else generations += g
        }
        val preferred = if (remote.confidence >= seed.confidence) remote else seed
        return seed.copy(
            arName = preferred.arName.ifBlank { seed.arName },
            enName = preferred.enName.ifBlank { seed.enName },
            fromYear = preferred.fromYear ?: seed.fromYear,
            toYear = preferred.toYear ?: seed.toYear,
            generations = generations.sortedBy { it.fromYear },
            trimHints = (seed.trimHints + remote.trimHints).distinct(),
            aliases = (seed.aliases + remote.aliases).distinctBy(::key),
            marketAliases = mergeAliases(seed.marketAliases, remote.marketAliases),
            confidence = maxOf(seed.confidence, remote.confidence),
            evidence = mergeEvidence(seed.evidence, remote.evidence)
        )
    }

    private fun mergeGeneration(seed: VehicleSelectionCatalog.Generation, remote: VehicleSelectionCatalog.Generation): VehicleSelectionCatalog.Generation {
        val preferred = if (remote.confidence >= seed.confidence) remote else seed
        return seed.copy(
            code = preferred.code?.takeIf { it.isNotBlank() } ?: seed.code,
            label = preferred.label.ifBlank { seed.label },
            fromYear = preferred.fromYear,
            toYear = preferred.toYear,
            marketNames = (seed.marketNames + remote.marketNames).distinctBy(::key),
            trimHints = (seed.trimHints + remote.trimHints).distinct(),
            marketAliases = mergeAliases(seed.marketAliases, remote.marketAliases),
            engines = mergeEngines(seed.engines, remote.engines),
            transmissions = mergeTransmissions(seed.transmissions, remote.transmissions),
            confidence = maxOf(seed.confidence, remote.confidence),
            evidence = mergeEvidence(seed.evidence, remote.evidence)
        )
    }

    private fun sameGeneration(a: VehicleSelectionCatalog.Generation, b: VehicleSelectionCatalog.Generation): Boolean {
        val ac = key(a.code.orEmpty())
        val bc = key(b.code.orEmpty())
        if (ac.isNotBlank() && bc.isNotBlank()) return ac == bc
        return key(a.label) == key(b.label) || (a.fromYear == b.fromYear && a.toYear == b.toYear)
    }

    private fun mergeAliases(a: List<VehicleCatalogAlias>, b: List<VehicleCatalogAlias>): List<VehicleCatalogAlias> =
        (a + b).groupBy { key(it.name) }.values.map { group -> group.maxByOrNull { it.confidence }!! }
            .sortedByDescending { it.confidence }

    private fun mergeEngines(a: List<VehicleCatalogEngine>, b: List<VehicleCatalogEngine>): List<VehicleCatalogEngine> =
        (a + b).groupBy { key(it.id.ifBlank { it.code ?: it.name }) }.values.map { group -> group.maxByOrNull { it.confidence }!! }
            .sortedByDescending { it.confidence }

    private fun mergeTransmissions(a: List<VehicleCatalogTransmission>, b: List<VehicleCatalogTransmission>): List<VehicleCatalogTransmission> =
        (a + b).groupBy { key(it.id.ifBlank { it.code ?: it.name }) }.values.map { group -> group.maxByOrNull { it.confidence }!! }
            .sortedByDescending { it.confidence }

    private fun mergeEvidence(a: List<VehicleCatalogEvidence>, b: List<VehicleCatalogEvidence>): List<VehicleCatalogEvidence> =
        (a + b).distinctBy { it.sourceId.ifBlank { "${it.title}|${it.url}" } }.sortedByDescending { it.safeConfidence }

    private fun key(value: String): String = VehicleSelectionCatalog.normalize(value)
}
