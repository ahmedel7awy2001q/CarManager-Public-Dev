package com.ahmed.carmanager.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ownership_records",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index(value = ["vehicleId", "eventDate"])]
)
data class OwnershipRecordEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val eventType: OwnershipEventType,
    val eventDate: Long,
    val odometerKm: Double? = null,
    val price: Double? = null,
    val partyName: String? = null,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "odometer_records",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index(value = ["vehicleId", "recordedAt"]), Index("gpsDeviceId")]
)
data class OdometerRecordEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val odometerKm: Double,
    val source: OdometerSource,
    val recordedAt: Long = nowEpochMillis(),
    val gpsDeviceId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "maintenance_plans",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index(value = ["vehicleId", "isActive"])]
)
data class MaintenancePlanEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val titleAr: String,
    val category: String,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val reminderRule: ReminderRule = ReminderRule.WHICHEVER_COMES_FIRST,
    val warningBeforeKm: Double? = 1000.0,
    val warningBeforeDays: Int? = 30,
    val estimatedCost: Double? = null,
    val lastServiceOdometerKm: Double? = null,
    val lastServiceDate: Long? = null,
    val nextDueOdometerKm: Double? = null,
    val nextDueDate: Long? = null,
    val status: MaintenanceStatus = MaintenanceStatus.UPCOMING,
    val priority: Int = 0,
    val isActive: Boolean = true,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "maintenance_records",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index("maintenancePlanId"), Index(value = ["vehicleId", "serviceDate"]), Index(value = ["vehicleId", "odometerKm"])]
)
data class MaintenanceRecordEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val maintenancePlanId: String? = null,
    val titleAr: String,
    val category: String,
    val serviceDate: Long,
    val odometerKm: Double,
    val totalCost: Double = 0.0,
    val laborCost: Double? = null,
    val partsCost: Double? = null,
    val serviceCenter: String? = null,
    val technician: String? = null,
    val invoiceNumber: String? = null,
    val warrantyUntil: Long? = null,
    val nextDueOdometerKm: Double? = null,
    val nextDueDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "fuel_records",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index(value = ["vehicleId", "fuelDate"]), Index(value = ["vehicleId", "odometerKm"])]
)
data class FuelRecordEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val fuelDate: Long,
    val odometerKm: Double,
    val fuelType: FuelType,
    val stationName: String? = null,
    val pricePerLiter: Double,
    val amountPaid: Double,
    val liters: Double,
    val isFullTank: Boolean = false,
    val distanceSincePreviousKm: Double? = null,
    val consumptionLitersPer100Km: Double? = null,
    val kmPerLiter: Double? = null,
    val costPerKm: Double? = null,
    val estimatedRangeKm: Double? = null,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "trips",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index("gpsDeviceId"), Index(value = ["vehicleId", "startTime"])]
)
data class TripEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val gpsDeviceId: String? = null,
    val tripType: TripType = TripType.PERSONAL,
    val startTime: Long,
    val endTime: Long? = null,
    val startOdometerKm: Double? = null,
    val endOdometerKm: Double? = null,
    val distanceKm: Double = 0.0,
    val durationSeconds: Long? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val estimatedFuelLiters: Double? = null,
    val fuelCost: Double? = null,
    val estimatedOperatingCost: Double? = null,
    val isAutomatic: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["vehicleId"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index(value = ["vehicleId", "expenseDate"])]
)
data class ExpenseEntity(
    @PrimaryKey val id: String = newEntityId(),
    val vehicleId: String,
    val expenseDate: Long,
    val category: ExpenseCategory,
    val amount: Double,
    val odometerKm: Double? = null,
    val descriptionAr: String,
    val merchant: String? = null,
    val paymentMethod: String? = null,
    val notes: String? = null,
    val createdAt: Long = nowEpochMillis(),
    val updatedAt: Long = nowEpochMillis(),
    val isDeleted: Boolean = false
)
