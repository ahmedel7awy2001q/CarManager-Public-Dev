package com.ahmed.carmanager.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gps_devices",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)],
    indices = [
        Index("vehicleId"),
        Index(value = ["provider", "deviceIdentifier"]),
        Index(value = ["vehicleId", "isActive"]),
        Index(value = ["vehicleId", "provider", "updatedAt"]),
        Index(value = ["vehicleId", "deviceIdentifier", "updatedAt"])
    ]
)
data class GpsDeviceEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val provider: GpsProvider,
    val deviceName: String? = null,
    val imei: String? = null,
    val deviceIdentifier: String,
    val installedDate: Long? = null,
    val installedOdometerKm: Double? = null,
    val providerMileageAtInstallKm: Double? = null,
    val isActive: Boolean = true,
    val lastSyncAt: Long? = null,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "gps_calibrations",
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = GpsDeviceEntity::class, parentColumns = ["id"], childColumns = ["gpsDeviceId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index("vehicleId"),
        Index("gpsDeviceId"),
        Index(value = ["gpsDeviceId", "calibrationDate"]),
        Index(value = ["vehicleId", "gpsDeviceId", "calibrationDate"])
    ]
)
data class GpsCalibrationEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val gpsDeviceId: String,
    val calibrationDate: Long,
    val gpsMileageKm: Double,
    val vehicleOdometerKm: Double,
    val offsetKm: Double,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "gps_readings",
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = GpsDeviceEntity::class, parentColumns = ["id"], childColumns = ["gpsDeviceId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index("vehicleId"),
        Index("gpsDeviceId"),
        Index(value = ["gpsDeviceId", "timestamp"]),
        // Latest-reading and vehicle-window queries are among the hottest paths once GPS history
        // becomes large. Keeping vehicleId before timestamp lets SQLite satisfy both filtering and
        // ordering without scanning every reading belonging to the vehicle.
        Index(value = ["vehicleId", "timestamp"]),
        Index(value = ["vehicleId", "gpsDeviceId", "timestamp"])
    ]
)
data class GpsReadingEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val gpsDeviceId: String,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speedKmh: Double? = null,
    val headingDegrees: Double? = null,
    val accStatus: AccStatus = AccStatus.UNKNOWN,
    val gpsMileageKm: Double? = null,
    val todayMileageKm: Double? = null,
    val calculatedVehicleOdometerKm: Double? = null,
    val externalVoltage: Double? = null,
    val connectionStatus: GpsConnectionStatus = GpsConnectionStatus.UNKNOWN,
    val createdAt: Long = nowEpochMillis()
)
