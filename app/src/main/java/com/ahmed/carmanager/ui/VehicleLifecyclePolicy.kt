package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleStatus

internal object VehicleLifecyclePolicy {
    fun isOperational(vehicle: VehicleEntity): Boolean =
        !vehicle.isDeleted && (vehicle.status == VehicleStatus.ACTIVE || vehicle.status == VehicleStatus.SECONDARY)

    fun isHistorical(vehicle: VehicleEntity): Boolean =
        !vehicle.isDeleted && (vehicle.status == VehicleStatus.SOLD || vehicle.status == VehicleStatus.ARCHIVED)

    fun operational(vehicles: List<VehicleEntity>): List<VehicleEntity> = vehicles.filter(::isOperational)
    fun historical(vehicles: List<VehicleEntity>): List<VehicleEntity> = vehicles.filter(::isHistorical)
}
