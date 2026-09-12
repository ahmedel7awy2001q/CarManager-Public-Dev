package com.ahmed.carmanager.data.gps

import androidx.room.withTransaction
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.GpsDeviceEntity

/**
 * Registers this Android device as a local tracker for a vehicle.
 *
 * The stable deviceIdentifier is the identity.
 * PHONE / HEAD_UNIT is only the current role and may change without
 * creating a duplicate GPS device.
 */
class TrackerDeviceRegistry(
    private val database: CarDatabase,
    private val authRepository: AuthRepository,
    private val identityStore: TrackerDeviceIdentityStore,
    private val vehicleContextStore: VehicleContextStore
) {
    suspend fun ensureTrackerDevice(
        vehicleId: String
    ): Result<GpsDeviceEntity> = runCatching {
        require(vehicleId.isNotBlank()) {
            "معرف المركبة غير صالح."
        }

        val uid = authRepository.currentUid()
            ?: error("سجل الدخول أولا.")

        database.withTransaction {
            val vehicle = database.vehicleDao()
                .getById(vehicleId, uid)
                ?: error("المركبة غير موجودة أو لا تخص الحساب الحالي.")

            val identity = identityStore.identity()

            if (identity.role == TrackerDeviceRole.HEAD_UNIT) {
                val boundVehicleId =
                    vehicleContextStore.boundHeadUnitVehicleId()
                        ?: error("اربط شاشة السيارة بمركبة أولا.")

                require(boundVehicleId == vehicle.vehicleId) {
                    "شاشة السيارة مرتبطة بمركبة أخرى. غير الربط من إعدادات شاشة السيارة أولا."
                }
            }
            val gpsDao = database.gpsDao()

            val existing = gpsDao.getDeviceByIdentifier(
                vehicleId = vehicle.vehicleId,
                deviceIdentifier = identity.deviceIdentifier
            )

            if (existing != null) {
                val needsUpdate =
                    existing.provider != identity.provider ||
                    existing.deviceName != identity.displayName ||
                    !existing.isActive

                if (!needsUpdate) {
                    existing
                } else {
                    val updated = existing.copy(
                        provider = identity.provider,
                        deviceName = identity.displayName,
                        isActive = true,
                        updatedAt = System.currentTimeMillis()
                    )

                    gpsDao.updateDevice(updated)
                    updated
                }
            } else {
                val now = System.currentTimeMillis()

                val created = GpsDeviceEntity(
                    vehicleId = vehicle.vehicleId,
                    provider = identity.provider,
                    deviceName = identity.displayName,
                    imei = null,
                    deviceIdentifier = identity.deviceIdentifier,
                    installedDate = now,
                    installedOdometerKm = null,
                    providerMileageAtInstallKm = null,
                    isActive = true,
                    lastSyncAt = null,
                    notes = null,
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false
                )

                gpsDao.insertDevice(created)
                created
            }
        }
    }
}

