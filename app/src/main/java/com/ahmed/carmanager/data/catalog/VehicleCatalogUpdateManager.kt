package com.ahmed.carmanager.data.catalog

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ahmed.carmanager.ui.VehicleCatalogAlias
import com.ahmed.carmanager.ui.VehicleCatalogAliasRelation
import com.ahmed.carmanager.ui.VehicleCatalogEngine
import com.ahmed.carmanager.ui.VehicleCatalogEvidence
import com.ahmed.carmanager.ui.VehicleCatalogRuntimeState
import com.ahmed.carmanager.ui.VehicleCatalogRuntimeStore
import com.ahmed.carmanager.ui.VehicleCatalogSourceKind
import com.ahmed.carmanager.ui.VehicleCatalogTransmission
import com.ahmed.carmanager.ui.VehicleSelectionCatalog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.TimeUnit

/**
 * Downloads the vehicle knowledge catalog independently from APK releases.
 *
 * Update safety is intentionally conservative:
 * 1) the APK seed is always available offline,
 * 2) a remote version is staged first,
 * 3) every make/model/generation and every alias/engine/transmission must have provenance,
 * 4) the staged copy is validated and read back before activation,
 * 5) the previous known-good copy is retained for rollback.
 */
internal class VehicleCatalogUpdateManager(
    context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val activeFile = File(appContext.filesDir, ACTIVE_FILE)
    private val previousFile = File(appContext.filesDir, PREVIOUS_FILE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        initializeFromDisk()
        schedulePeriodicUpdates()
        scope.launch { refreshIfNeeded(force = false) }
    }

    fun initializeFromDisk() {
        val active = readSnapshot(activeFile)
        val previous = if (active == null) readSnapshot(previousFile) else null
        val loaded = active ?: previous
        if (active == null && previous != null) runCatching { previousFile.copyTo(activeFile, overwrite = true) }
        if (loaded != null) {
            VehicleCatalogRuntimeStore.applyRemote(
                loaded.makes,
                loaded.state.copy(status = VehicleCatalogRuntimeState.Status.CACHED)
            )
        }
    }

    fun schedulePeriodicUpdates() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<VehicleCatalogUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    suspend fun refreshIfNeeded(force: Boolean = false): VehicleCatalogRuntimeState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (!force && now - lastCheck < CHECK_TTL_MS) return@withContext VehicleCatalogRuntimeStore.state.value

        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        VehicleCatalogRuntimeStore.updateState {
            it.copy(status = VehicleCatalogRuntimeState.Status.CHECKING, lastCheckedAtEpochMs = now, message = null)
        }

        try {
            val manifest = firestore.collection("vehicleCatalog")
                .document("manifest")
                .get(Source.SERVER)
                .await()
            if (!manifest.exists()) {
                val current = VehicleCatalogRuntimeStore.state.value
                val state = current.copy(
                    lastCheckedAtEpochMs = now,
                    status = if (current.activeVersion > 0) VehicleCatalogRuntimeState.Status.CACHED else VehicleCatalogRuntimeState.Status.SEED_ONLY,
                    message = "القاعدة المدمجة فعالة — لا توجد حزمة تحديث منشورة بعد"
                )
                VehicleCatalogRuntimeStore.updateState { state }
                return@withContext state
            }

            val schemaVersion = manifest.getLong("schemaVersion")?.toInt() ?: 1
            require(schemaVersion in 1..SUPPORTED_SCHEMA_VERSION) { "Unsupported vehicle catalog schema: $schemaVersion" }

            val activeVersion = manifest.getLong("activeVersion") ?: 0L
            val packId = manifest.getString("packId")?.trim().orEmpty().ifBlank { "v$activeVersion" }
            val publishedAt = epochMs(manifest.get("publishedAt"))
            val minAppVersionCode = manifest.getLong("minAppVersionCode") ?: 0L
            require(minAppVersionCode <= currentAppVersionCode()) { "Catalog requires a newer app (min=$minAppVersionCode, current=${currentAppVersionCode()})" }

            val current = VehicleCatalogRuntimeStore.state.value
            val forceRollback = manifest.getBoolean("forceRollback") == true
            val revokedVersions = (manifest.get("revokedVersions") as? List<*>)
                ?.mapNotNull { (it as? Number)?.toLong() ?: it?.toString()?.toLongOrNull() }
                .orEmpty()
            val currentRevoked = current.activeVersion in revokedVersions
            if (!forceRollback && !currentRevoked && activeVersion <= current.activeVersion && activeFile.exists()) {
                val state = current.copy(
                    lastCheckedAtEpochMs = now,
                    status = VehicleCatalogRuntimeState.Status.UP_TO_DATE,
                    message = "قاعدة السيارات محدثة"
                )
                VehicleCatalogRuntimeStore.updateState { state }
                return@withContext state
            }

            val sourceRegistry = parseEvidenceList(manifest.get("sources"), emptyMap()).associateBy { it.sourceId }
            val docs = firestore.collection("vehicleCatalogVersions")
                .document(packId)
                .collection("makes")
                .get(Source.SERVER)
                .await()
            require(!docs.isEmpty) { "Vehicle catalog pack has no make documents" }

            val makes = docs.documents.mapNotNull { doc ->
                val payload = doc.data.orEmpty() + mapOf("id" to (doc.getString("id") ?: doc.id))
                parseMake(payload, sourceRegistry)
            }
            validateSnapshot(makes)

            val state = VehicleCatalogRuntimeState(
                activeVersion = activeVersion,
                schemaVersion = schemaVersion,
                packId = packId,
                publishedAtEpochMs = publishedAt,
                lastCheckedAtEpochMs = now,
                sourceCount = collectSourceIds(makes).size,
                remoteMakeCount = makes.size,
                status = VehicleCatalogRuntimeState.Status.UPDATED,
                message = manifest.getString("changeNote") ?: "تم تحديث قاعدة السيارات"
            )
            val snapshot = CatalogSnapshot(makes, state)
            writeSnapshotAtomically(snapshot)
            VehicleCatalogRuntimeStore.applyRemote(makes, state)
            prefs.edit().putLong(KEY_ACTIVE_VERSION, activeVersion).apply()
            state
        } catch (error: Throwable) {
            Log.w("VehicleCatalogUpdate", "Catalog refresh failed: ${error::class.java.simpleName}: ${error.message}")
            val current = VehicleCatalogRuntimeStore.state.value
            val safe = current.copy(
                lastCheckedAtEpochMs = now,
                status = if (current.activeVersion > 0) VehicleCatalogRuntimeState.Status.CACHED else VehicleCatalogRuntimeState.Status.SEED_ONLY,
                message = catalogFailureMessage(error)
            )
            VehicleCatalogRuntimeStore.updateState { safe }
            safe
        }
    }

    private fun currentAppVersionCode(): Long = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    private fun catalogFailureMessage(error: Throwable): String {
        val type = error::class.java.simpleName.lowercase()
        val detail = error.message.orEmpty().lowercase()
        val reason = when {
            "newer app" in detail -> "توجد حزمة أحدث لكنها تحتاج إصدارًا أحدث من التطبيق"
            "schema" in detail -> "حزمة التحديث الخارجية بصيغة غير مدعومة حاليًا"
            "no make documents" in detail || "empty vehicle catalog" in detail -> "لا توجد حزمة سيارات خارجية مكتملة منشورة حاليًا"
            "permission" in detail || "denied" in detail -> "خدمة التحديث الخارجية غير متاحة لهذا الإصدار حاليًا"
            "network" in type || "unavailable" in detail || "timeout" in detail || "timed out" in detail -> "تعذر الوصول إلى خدمة التحديث الآن"
            else -> "تعذر التحقق من حزمة التحديث الخارجية الآن"
        }
        return "تعذر تحديث قاعدة السيارات الآن — السبب: $reason. قاعدة السيارات المدمجة/المحفوظة ما زالت فعالة ولم تُفقد أي بيانات. استخدم التطبيق بشكل طبيعي؛ سيحاول CarManager لاحقًا ويمكنك الضغط على تحديث للمحاولة يدويًا."
    }

    private fun validateSnapshot(makes: List<VehicleSelectionCatalog.Make>) {
        require(makes.isNotEmpty()) { "Empty vehicle catalog" }
        require(makes.distinctBy { norm(it.id) }.size == makes.size) { "Duplicate make ids" }
        makes.forEach { make ->
            require(make.id.isNotBlank() && make.enName.isNotBlank()) { "Invalid make" }
            require(make.evidence.isNotEmpty()) { "Make ${make.id} has no provenance" }
            require(make.confidence in 0..100) { "Invalid make confidence" }
            require(make.models.distinctBy { norm(it.id) }.size == make.models.size) { "Duplicate model ids for ${make.id}" }
            make.models.forEach { model ->
                require(model.id.isNotBlank() && model.enName.isNotBlank()) { "Invalid model in ${make.id}" }
                require(model.evidence.isNotEmpty()) { "Model ${make.id}/${model.id} has no provenance" }
                require(model.confidence in 0..100) { "Invalid model confidence" }
                model.generations.forEach { generation ->
                    require(generation.fromYear in 1886..2100 && generation.toYear in generation.fromYear..2100) { "Invalid generation years" }
                    require(generation.evidence.isNotEmpty()) { "Generation ${make.id}/${model.id}/${generation.label} has no provenance" }
                    require(generation.confidence in 0..100) { "Invalid generation confidence" }
                    generation.marketAliases.forEach(::validateAlias)
                    generation.engines.forEach(::validateEngine)
                    generation.transmissions.forEach(::validateTransmission)
                }
                model.marketAliases.forEach(::validateAlias)
            }
        }
    }

    private fun validateAlias(alias: VehicleCatalogAlias) {
        require(alias.name.isNotBlank()) { "Blank alias" }
        require(alias.confidence in 0..100) { "Invalid alias confidence" }
        require(alias.evidence.isNotEmpty()) { "Alias ${alias.name} has no provenance" }
    }

    private fun validateEngine(engine: VehicleCatalogEngine) {
        require(engine.id.isNotBlank() && engine.name.isNotBlank()) { "Invalid engine" }
        require(engine.confidence in 0..100) { "Invalid engine confidence" }
        require(engine.capacityCc == null || engine.capacityCc in 100..20000) { "Invalid engine capacity" }
        require(engine.evidence.isNotEmpty()) { "Engine ${engine.id} has no provenance" }
    }

    private fun validateTransmission(transmission: VehicleCatalogTransmission) {
        require(transmission.id.isNotBlank() && transmission.name.isNotBlank()) { "Invalid transmission" }
        require(transmission.confidence in 0..100) { "Invalid transmission confidence" }
        require(transmission.gears == null || transmission.gears in 1..20) { "Invalid gear count" }
        require(transmission.evidence.isNotEmpty()) { "Transmission ${transmission.id} has no provenance" }
    }

    private fun parseMake(raw: Map<String, Any?>, registry: Map<String, VehicleCatalogEvidence>): VehicleSelectionCatalog.Make? {
        val id = string(raw["id"]).ifBlank { return null }
        val evidence = evidenceFor(raw, registry, emptyList())
        return VehicleSelectionCatalog.Make(
            id = id,
            arName = string(raw["arName"]).ifBlank { string(raw["enName"]) },
            enName = string(raw["enName"]).ifBlank { id },
            models = listOfMaps(raw["models"]).mapNotNull { parseModel(it, registry, evidence) },
            confidence = factConfidence(raw, evidence, 80),
            evidence = evidence
        )
    }

    private fun parseModel(
        raw: Map<String, Any?>,
        registry: Map<String, VehicleCatalogEvidence>,
        inheritedEvidence: List<VehicleCatalogEvidence>
    ): VehicleSelectionCatalog.Model? {
        val id = string(raw["id"]).ifBlank { return null }
        val evidence = evidenceFor(raw, registry, inheritedEvidence)
        return VehicleSelectionCatalog.Model(
            id = id,
            arName = string(raw["arName"]).ifBlank { string(raw["enName"]) },
            enName = string(raw["enName"]).ifBlank { id },
            fromYear = nullableInt(raw["fromYear"]),
            toYear = nullableInt(raw["toYear"]),
            generations = listOfMaps(raw["generations"]).mapNotNull { parseGeneration(it, registry, evidence) },
            trimHints = stringList(raw["trimHints"]),
            aliases = stringList(raw["aliases"]),
            marketAliases = listOfMaps(raw["marketAliases"]).mapNotNull { parseAlias(it, registry, evidence) },
            confidence = factConfidence(raw, evidence, 80),
            evidence = evidence
        )
    }

    private fun parseGeneration(
        raw: Map<String, Any?>,
        registry: Map<String, VehicleCatalogEvidence>,
        inheritedEvidence: List<VehicleCatalogEvidence>
    ): VehicleSelectionCatalog.Generation? {
        val from = nullableInt(raw["fromYear"]) ?: return null
        val to = nullableInt(raw["toYear"]) ?: from
        val evidence = evidenceFor(raw, registry, inheritedEvidence)
        return VehicleSelectionCatalog.Generation(
            code = string(raw["code"]).ifBlank { null },
            label = string(raw["label"]).ifBlank { string(raw["code"]).ifBlank { "$from–$to" } },
            fromYear = from,
            toYear = to,
            marketNames = stringList(raw["marketNames"]),
            trimHints = stringList(raw["trimHints"]),
            marketAliases = listOfMaps(raw["marketAliases"]).mapNotNull { parseAlias(it, registry, evidence) },
            engines = listOfMaps(raw["engines"]).mapNotNull { parseEngine(it, registry, evidence) },
            transmissions = listOfMaps(raw["transmissions"]).mapNotNull { parseTransmission(it, registry, evidence) },
            confidence = factConfidence(raw, evidence, 80),
            evidence = evidence
        )
    }

    private fun parseAlias(
        raw: Map<String, Any?>,
        registry: Map<String, VehicleCatalogEvidence>,
        inheritedEvidence: List<VehicleCatalogEvidence>
    ): VehicleCatalogAlias? {
        val name = string(raw["name"]).ifBlank { return null }
        return VehicleCatalogAlias(
            name = name,
            relation = runCatching { VehicleCatalogAliasRelation.valueOf(string(raw["relation"]).uppercase()) }
                .getOrDefault(VehicleCatalogAliasRelation.MARKET_NAME),
            market = string(raw["market"]).ifBlank { null },
            language = string(raw["language"]).ifBlank { null },
            fromYear = nullableInt(raw["fromYear"]),
            toYear = nullableInt(raw["toYear"]),
            confidence = factConfidence(raw, evidenceFor(raw, registry, inheritedEvidence), 75),
            evidence = evidenceFor(raw, registry, inheritedEvidence)
        )
    }

    private fun parseEngine(
        raw: Map<String, Any?>,
        registry: Map<String, VehicleCatalogEvidence>,
        inheritedEvidence: List<VehicleCatalogEvidence>
    ): VehicleCatalogEngine? {
        val name = string(raw["name"]).ifBlank { return null }
        return VehicleCatalogEngine(
            id = string(raw["id"]).ifBlank { string(raw["code"]).ifBlank { name } },
            name = name,
            code = string(raw["code"]).ifBlank { null },
            capacityCc = nullableInt(raw["capacityCc"]),
            fuel = string(raw["fuel"]).ifBlank { null },
            fromYear = nullableInt(raw["fromYear"]),
            toYear = nullableInt(raw["toYear"]),
            markets = stringList(raw["markets"]),
            confidence = factConfidence(raw, evidenceFor(raw, registry, inheritedEvidence), 75),
            evidence = evidenceFor(raw, registry, inheritedEvidence)
        )
    }

    private fun parseTransmission(
        raw: Map<String, Any?>,
        registry: Map<String, VehicleCatalogEvidence>,
        inheritedEvidence: List<VehicleCatalogEvidence>
    ): VehicleCatalogTransmission? {
        val name = string(raw["name"]).ifBlank { return null }
        return VehicleCatalogTransmission(
            id = string(raw["id"]).ifBlank { string(raw["code"]).ifBlank { name } },
            name = name,
            code = string(raw["code"]).ifBlank { null },
            type = string(raw["type"]).ifBlank { null },
            gears = nullableInt(raw["gears"]),
            fromYear = nullableInt(raw["fromYear"]),
            toYear = nullableInt(raw["toYear"]),
            markets = stringList(raw["markets"]),
            confidence = factConfidence(raw, evidenceFor(raw, registry, inheritedEvidence), 75),
            evidence = evidenceFor(raw, registry, inheritedEvidence)
        )
    }

    private fun evidenceFor(
        raw: Map<String, Any?>,
        registry: Map<String, VehicleCatalogEvidence>,
        inherited: List<VehicleCatalogEvidence>
    ): List<VehicleCatalogEvidence> {
        val direct = parseEvidenceList(raw["evidence"], registry)
        val refs = stringList(raw["sourceIds"]).mapNotNull(registry::get)
        return (direct + refs).distinctBy { it.sourceId }.ifEmpty { inherited }
    }

    private fun parseEvidenceList(value: Any?, registry: Map<String, VehicleCatalogEvidence>): List<VehicleCatalogEvidence> =
        listOfMaps(value).mapNotNull { raw ->
            val sourceId = string(raw["sourceId"])
            if (raw.keys.all { it == "sourceId" } && sourceId.isNotBlank()) return@mapNotNull registry[sourceId]
            val title = string(raw["title"]).ifBlank { registry[sourceId]?.title.orEmpty() }
            if (title.isBlank()) return@mapNotNull null
            val kind = runCatching { VehicleCatalogSourceKind.valueOf(string(raw["kind"]).uppercase()) }
                .getOrDefault(VehicleCatalogSourceKind.UNKNOWN)
            VehicleCatalogEvidence(
                sourceId = sourceId.ifBlank { slug(title) },
                title = title,
                publisher = string(raw["publisher"]).ifBlank { null },
                url = string(raw["url"]).ifBlank { null },
                market = string(raw["market"]).ifBlank { null },
                kind = kind,
                confidence = nullableInt(raw["confidence"])?.coerceIn(0, 100) ?: defaultSourceConfidence(kind),
                retrievedAtEpochMs = epochMs(raw["retrievedAt"]),
                note = string(raw["note"]).ifBlank { null }
            )
        }

    private fun factConfidence(raw: Map<String, Any?>, evidence: List<VehicleCatalogEvidence>, fallback: Int): Int {
        val requested = nullableInt(raw["confidence"])?.coerceIn(0, 100) ?: fallback.coerceIn(0, 100)
        if (evidence.isEmpty()) return requested
        val publishers = evidence.map { (it.publisher ?: it.sourceId).lowercase() }.distinct().size
        val corroborationBonus = ((publishers - 1).coerceAtLeast(0) * 3).coerceAtMost(9)
        val sourceCeiling = (evidence.maxOf { it.safeConfidence } + corroborationBonus).coerceAtMost(100)
        return minOf(requested, sourceCeiling)
    }

    private fun defaultSourceConfidence(kind: VehicleCatalogSourceKind): Int = when (kind) {
        VehicleCatalogSourceKind.OEM -> 99
        VehicleCatalogSourceKind.OFFICIAL_DISTRIBUTOR -> 95
        VehicleCatalogSourceKind.OFFICIAL_MARKET -> 94
        VehicleCatalogSourceKind.INDUSTRY_DATABASE -> 90
        VehicleCatalogSourceKind.PARTS_CATALOG -> 86
        VehicleCatalogSourceKind.VERIFIED_RETAILER -> 76
        VehicleCatalogSourceKind.CARMANAGER_CURATED -> 70
        VehicleCatalogSourceKind.COMMUNITY -> 55
        VehicleCatalogSourceKind.UNKNOWN -> 45
    }

    private fun collectSourceIds(makes: List<VehicleSelectionCatalog.Make>): Set<String> = buildSet {
        fun evidence(list: List<VehicleCatalogEvidence>) = list.forEach { add(it.sourceId) }
        makes.forEach { make ->
            evidence(make.evidence)
            make.models.forEach { model ->
                evidence(model.evidence)
                model.marketAliases.forEach { evidence(it.evidence) }
                model.generations.forEach { generation ->
                    evidence(generation.evidence)
                    generation.marketAliases.forEach { evidence(it.evidence) }
                    generation.engines.forEach { evidence(it.evidence) }
                    generation.transmissions.forEach { evidence(it.evidence) }
                }
            }
        }
    }

    private fun writeSnapshotAtomically(snapshot: CatalogSnapshot) {
        val staging = File(appContext.filesDir, "$ACTIVE_FILE.staging")
        ObjectOutputStream(staging.outputStream().buffered()).use { it.writeObject(snapshot) }
        require(readSnapshot(staging) != null) { "Staged catalog failed read-back validation" }
        if (activeFile.exists()) activeFile.copyTo(previousFile, overwrite = true)
        if (!staging.renameTo(activeFile)) {
            staging.copyTo(activeFile, overwrite = true)
            staging.delete()
        }
    }

    private fun readSnapshot(file: File): CatalogSnapshot? = runCatching {
        if (!file.exists()) return null
        val value = ObjectInputStream(file.inputStream().buffered()).use { it.readObject() as CatalogSnapshot }
        validateSnapshot(value.makes)
        value
    }.getOrNull()

    private data class CatalogSnapshot(
        val makes: List<VehicleSelectionCatalog.Make>,
        val state: VehicleCatalogRuntimeState
    ) : Serializable

    companion object {
        private const val PREFS = "vehicle_catalog_updates_v1"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_ACTIVE_VERSION = "active_version"
        private const val ACTIVE_FILE = "vehicle_catalog_active_v1.bin"
        private const val PREVIOUS_FILE = "vehicle_catalog_previous_v1.bin"
        private const val WORK_NAME = "vehicle_catalog_update"
        private const val CHECK_TTL_MS = 6 * 60 * 60 * 1000L
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

internal class VehicleCatalogUpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val manager = VehicleCatalogUpdateManager(applicationContext)
        manager.initializeFromDisk()
        val state = manager.refreshIfNeeded(force = true)
        return if (state.status == VehicleCatalogRuntimeState.Status.FAILED) Result.retry() else Result.success()
    }
}

private fun listOfMaps(value: Any?): List<Map<String, Any?>> = (value as? List<*>)
    ?.mapNotNull { @Suppress("UNCHECKED_CAST") (it as? Map<String, Any?>) }
    .orEmpty()

private fun stringList(value: Any?): List<String> = (value as? List<*>)
    ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
    .orEmpty()

private fun string(value: Any?): String = value?.toString()?.trim().orEmpty()
private fun int(value: Any?, default: Int): Int = nullableInt(value)?.coerceIn(0, 100) ?: default
private fun nullableInt(value: Any?): Int? = when (value) {
    is Number -> value.toInt()
    is String -> value.trim().toIntOrNull()
    else -> null
}

private fun epochMs(value: Any?): Long? = when (value) {
    is Timestamp -> value.toDate().time
    is Number -> value.toLong()
    is java.util.Date -> value.time
    else -> null
}

private fun slug(value: String): String = value.lowercase()
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .ifBlank { "source-${value.hashCode().toUInt()}" }

private fun norm(value: String): String = value.trim().lowercase()
