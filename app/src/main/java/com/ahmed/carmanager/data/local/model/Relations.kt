package com.ahmed.carmanager.data.local.model

import androidx.room.Embedded
import androidx.room.Relation

data class VehicleWithSummary(
    @Embedded val vehicle: VehicleEntity,
    @Relation(parentColumn = "vehicleId", entityColumn = "vehicleId") val maintenancePlans: List<MaintenancePlanEntity> = emptyList(),
    @Relation(parentColumn = "vehicleId", entityColumn = "vehicleId") val recentMaintenance: List<MaintenanceRecordEntity> = emptyList(),
    @Relation(parentColumn = "vehicleId", entityColumn = "vehicleId") val fuelRecords: List<FuelRecordEntity> = emptyList(),
    @Relation(parentColumn = "vehicleId", entityColumn = "vehicleId") val gpsDevices: List<GpsDeviceEntity> = emptyList(),
    @Relation(parentColumn = "vehicleId", entityColumn = "vehicleId") val reminders: List<ReminderEntity> = emptyList()
)
