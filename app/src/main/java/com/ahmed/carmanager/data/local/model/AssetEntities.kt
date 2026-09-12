package com.ahmed.carmanager.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "parts", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("vehicleId"), Index(value = ["vehicleId", "status"])])
data class PartEntity(
    @PrimaryKey val id: String = newEntityId(), val vehicleId: String, val nameAr: String, val category: String,
    val brand: String? = null, val partNumber: String? = null, val purchaseDate: Long? = null, val installDate: Long? = null,
    val installOdometerKm: Double? = null, val cost: Double? = null, val expectedLifeKm: Double? = null, val expectedLifeMonths: Int? = null,
    val warrantyUntil: Long? = null, val supplier: String? = null, val status: ItemStatus = ItemStatus.ACTIVE, val notes: String? = null,
    val createdAt: Long = nowEpochMillis(), val updatedAt: Long = nowEpochMillis(), val isDeleted: Boolean = false
)

@Entity(tableName = "tires", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("vehicleId"), Index(value = ["vehicleId", "position"])])
data class TireEntity(
    @PrimaryKey val id: String = newEntityId(), val vehicleId: String, val brand: String? = null, val model: String? = null,
    val size: String? = null, val serialNumber: String? = null, val manufactureDateText: String? = null, val installDate: Long? = null,
    val installOdometerKm: Double? = null, val position: TirePosition = TirePosition.UNASSIGNED, val cost: Double? = null,
    val recommendedPressurePsi: Double? = null, val status: ItemStatus = ItemStatus.ACTIVE, val notes: String? = null,
    val createdAt: Long = nowEpochMillis(), val updatedAt: Long = nowEpochMillis(), val isDeleted: Boolean = false
)

@Entity(tableName = "tire_rotations", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("vehicleId"), Index(value = ["vehicleId", "rotationDate"])])
data class TireRotationEntity(
    @PrimaryKey val id: String = newEntityId(), val vehicleId: String, val rotationDate: Long, val odometerKm: Double,
    val descriptionAr: String, val notes: String? = null, val createdAt: Long = nowEpochMillis(), val updatedAt: Long = nowEpochMillis(), val isDeleted: Boolean = false
)

@Entity(tableName = "battery_records", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("vehicleId"), Index(value = ["vehicleId", "status"])])
data class BatteryRecordEntity(
    @PrimaryKey val id: String = newEntityId(), val vehicleId: String, val brand: String? = null, val model: String? = null,
    val capacityAh: Double? = null, val purchaseDate: Long? = null, val installDate: Long? = null, val installOdometerKm: Double? = null,
    val cost: Double? = null, val warrantyMonths: Int? = null, val expectedLifeMonths: Int? = null, val status: ItemStatus = ItemStatus.ACTIVE,
    val notes: String? = null, val createdAt: Long = nowEpochMillis(), val updatedAt: Long = nowEpochMillis(), val isDeleted: Boolean = false
)
