package com.ahmed.carmanager.data.gps

import androidx.room.withTransaction
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

sealed interface GpsSyncResult {
    data class Success(val updatedDevices: Int) : GpsSyncResult
    data class Error(val messageAr: String, val cause: Throwable? = null) : GpsSyncResult
}

class GpsSyncManager(
    private val database: CarDatabase,
    private val credentialStore: GpsCredentialStore,
    private val authRepository: AuthRepository
) {
    private val api = OpenGpsApi()
    private val gpsDao = database.gpsDao()
    private val vehicleDao = database.vehicleDao()
    private val odometerDao = database.odometerDao()

    suspend fun syncVehicle(vehicleId: String): GpsSyncResult = withContext(Dispatchers.IO) {
        runCatching {
            val uid = authRepository.currentUid() ?: return@runCatching GpsSyncResult.Error("سجّل الدخول أولًا لمزامنة GPS.")
            vehicleDao.getById(vehicleId, uid) ?: return@runCatching GpsSyncResult.Error("هذه المركبة غير موجودة في الحساب الحالي.")
            migrateLegacyCredentials(uid)
            val devices = gpsDao.getActiveDevices(vehicleId).filter { it.provider == GpsProvider.ITRACK || it.provider == GpsProvider.ETRACK }
            if (devices.isEmpty()) return@runCatching GpsSyncResult.Error("لا يوجد جهاز iTrack أو eTrack نشط لهذه المركبة.")
            var updated = 0
            val tokens = mutableMapOf<GpsProvider, String>()
            for (device in devices) {
                val credentials = credentialStore.get(uid, device.provider)
                    ?: return@runCatching GpsSyncResult.Error("أدخل بيانات دخول ${providerLabel(device.provider)} أولًا.")
                val token = tokens.getOrPutSuspend(device.provider) { api.authorize(device.provider, credentials) }
                val imei = device.imei?.takeIf { it.isNotBlank() } ?: device.deviceIdentifier
                val track = api.track(device.provider, token, imei)
                persistTrack(uid, device, track)
                updated++
            }
            pruneOldReadings(uid)
            GpsSyncResult.Success(updated)
        }.getOrElse { throwable -> GpsSyncResult.Error(throwable.message ?: "تعذر مزامنة GPS.", throwable) }
    }

    suspend fun syncAll(): GpsSyncResult = withContext(Dispatchers.IO) {
        runCatching {
            val uid = authRepository.currentUid() ?: return@runCatching GpsSyncResult.Success(0)
            migrateLegacyCredentials(uid)
            val devices = gpsDao.getAllActiveDevices()
                .filter { it.provider == GpsProvider.ITRACK || it.provider == GpsProvider.ETRACK }
                .filter { vehicleDao.getById(it.vehicleId, uid) != null }
            if (devices.isEmpty()) return@runCatching GpsSyncResult.Success(0)
            val tokens = mutableMapOf<GpsProvider, String>()
            var updated = 0
            for (device in devices) {
                val credentials = credentialStore.get(uid, device.provider) ?: continue
                val token = runCatching { tokens.getOrPutSuspend(device.provider) { api.authorize(device.provider, credentials) } }.getOrNull() ?: continue
                val imei = device.imei?.takeIf { it.isNotBlank() } ?: device.deviceIdentifier
                val track = runCatching { api.track(device.provider, token, imei) }.getOrNull() ?: continue
                runCatching { persistTrack(uid, device, track) }.onSuccess { updated++ }
            }
            pruneOldReadings(uid)
            GpsSyncResult.Success(updated)
        }.getOrElse { GpsSyncResult.Error(it.message ?: "تعذر مزامنة GPS.", it) }
    }

    private fun migrateLegacyCredentials(uid: String) {
        credentialStore.migrateLegacyTo(uid, GpsProvider.ITRACK)
        credentialStore.migrateLegacyTo(uid, GpsProvider.ETRACK)
    }

    private suspend fun persistTrack(uid: String, device: GpsDeviceEntity, track: OpenGpsTrack) {
        database.withTransaction {
            val vehicle = vehicleDao.getById(device.vehicleId, uid) ?: return@withTransaction
            val calibration = gpsDao.latestCalibration(device.vehicleId, device.id)
            val calculatedOdometer = track.mileageKm?.let { gpsKm ->
                when {
                    calibration != null -> calibration.vehicleOdometerKm + max(0.0, gpsKm - calibration.gpsMileageKm)
                    device.installedOdometerKm != null && device.providerMileageAtInstallKm != null ->
                        device.installedOdometerKm + max(0.0, gpsKm - device.providerMileageAtInstallKm)
                    else -> null
                }
            }
            val timestampMs = track.timestampSeconds * 1000L
            gpsDao.insertReading(
                GpsReadingEntity(
                    vehicleId = device.vehicleId,
                    gpsDeviceId = device.id,
                    timestamp = timestampMs,
                    latitude = track.latitude,
                    longitude = track.longitude,
                    speedKmh = track.speedKmh,
                    headingDegrees = track.courseDegrees,
                    accStatus = track.accStatus,
                    gpsMileageKm = track.mileageKm,
                    todayMileageKm = track.todayMileageKm,
                    calculatedVehicleOdometerKm = calculatedOdometer,
                    externalVoltage = track.externalVoltage,
                    connectionStatus = track.connectionStatus
                )
            )
            gpsDao.updateDevice(device.copy(lastSyncAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            if (calculatedOdometer != null && calculatedOdometer > vehicle.currentOdometerKm) {
                vehicleDao.update(vehicle.copy(currentOdometerKm = calculatedOdometer, updatedAt = System.currentTimeMillis()))
                odometerDao.insert(
                    OdometerRecordEntity(
                        vehicleId = vehicle.vehicleId,
                        odometerKm = calculatedOdometer,
                        source = OdometerSource.GPS,
                        recordedAt = timestampMs,
                        gpsDeviceId = device.id,
                        latitude = track.latitude,
                        longitude = track.longitude,
                        notes = "تحديث تلقائي من ${providerLabel(device.provider)}"
                    )
                )
            }
        }
    }

    private suspend fun pruneOldReadings(uid: String) {
        val cutoff = System.currentTimeMillis() - 180L * 24L * 60L * 60L * 1000L
        gpsDao.deleteReadingsOlderThanForOwner(cutoff, uid)
    }

    private fun providerLabel(provider: GpsProvider) = when (provider) {
        GpsProvider.ITRACK -> "iTrack"
        GpsProvider.ETRACK -> "eTrack"
        else -> "GPS"
    }

    private suspend fun <K, V> MutableMap<K, V>.getOrPutSuspend(key: K, supplier: suspend () -> V): V {
        this[key]?.let { return it }
        return supplier().also { this[key] = it }
    }
}
