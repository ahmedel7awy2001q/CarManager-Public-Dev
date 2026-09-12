package com.ahmed.carmanager.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fault_records", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("vehicleId"), Index(value = ["vehicleId", "reportedDate"]), Index(value = ["vehicleId", "status"])])
data class FaultRecordEntity(
    @PrimaryKey val id: String = newEntityId(), val vehicleId: String, val reportedDate: Long, val odometerKm: Double? = null,
    val symptomAr: String, val diagnosisAr: String? = null, val severity: FaultSeverity = FaultSeverity.MEDIUM, val status: FaultStatus = FaultStatus.OPEN,
    val resolvedDate: Long? = null, val linkedMaintenanceRecordId: String? = null, val repairCost: Double? = null, val notes: String? = null,
    val createdAt: Long = nowEpochMillis(), val updatedAt: Long = nowEpochMillis(), val isDeleted: Boolean = false
)

@Entity(tableName = "vehicle_documents", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("vehicleId"), Index(value = ["vehicleId", "expiryDate"])])
data class VehicleDocumentEntity(
    @PrimaryKey val id: String = newEntityId(), val vehicleId: String, val documentType: DocumentType, val documentNumber: String? = null,
    val issueDate: Long? = null, val expiryDate: Long? = null, val fileUri: String? = null, val notes: String? = null,
    val createdAt: Long = nowEpochMillis(), val updatedAt: Long = nowEpochMillis(), val isDeleted: Boolean = false
)

@Entity(tableName = "reminders", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("vehicleId"), Index(value = ["vehicleId", "isCompleted"]), Index("dueDate")])
data class ReminderEntity(
    @PrimaryKey val id: String = newEntityId(), val vehicleId: String, val titleAr: String, val rule: ReminderRule,
    val dueDate: Long? = null, val dueOdometerKm: Double? = null, val warningBeforeDays: Int? = null, val warningBeforeKm: Double? = null,
    val priority: Int = 0, val linkedEntityType: EntityType? = null, val linkedEntityId: String? = null, val isCompleted: Boolean = false,
    val completedAt: Long? = null, val notes: String? = null, val createdAt: Long = nowEpochMillis(), val updatedAt: Long = nowEpochMillis(), val isDeleted: Boolean = false
)

@Entity(tableName = "attachments", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("vehicleId"), Index(value = ["entityType", "entityId"])])
data class AttachmentEntity(
    @PrimaryKey val id: String = newEntityId(), val vehicleId: String, val entityType: EntityType, val entityId: String,
    val attachmentType: AttachmentType, val fileUri: String, val captionAr: String? = null,
    val createdAt: Long = nowEpochMillis(), val updatedAt: Long = nowEpochMillis(), val isDeleted: Boolean = false
)

@Entity(tableName = "monthly_snapshots", foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index(value = ["vehicleId", "year", "month"], unique = true)])
data class MonthlySnapshotEntity(
    @PrimaryKey val id: String = newEntityId(), val vehicleId: String, val year: Int, val month: Int,
    val openingOdometerKm: Double, val closingOdometerKm: Double, val distanceKm: Double, val fuelLiters: Double,
    val fuelCost: Double, val maintenanceCost: Double, val otherExpensesCost: Double, val totalCost: Double,
    val createdAt: Long = nowEpochMillis(), val updatedAt: Long = nowEpochMillis()
)
