package com.ahmed.carmanager.domain

import kotlin.math.max

object CarCalculations {
    fun litersFromMoney(amount: Double, pricePerLiter: Double): Double? =
        if (amount > 0 && pricePerLiter > 0) amount / pricePerLiter else null

    fun estimatedRangeKm(liters: Double, consumptionLitersPer100Km: Double): Double? =
        if (liters >= 0 && consumptionLitersPer100Km > 0) liters / consumptionLitersPer100Km * 100.0 else null

    fun consumptionLitersPer100Km(liters: Double, distanceKm: Double): Double? =
        if (liters >= 0 && distanceKm > 0) liters / distanceKm * 100.0 else null

    fun costPerKm(cost: Double, distanceKm: Double): Double? =
        if (cost >= 0 && distanceKm > 0) cost / distanceKm else null

    fun nextDueOdometer(lastServiceKm: Double, intervalKm: Double?): Double? =
        intervalKm?.takeIf { it > 0 }?.let { lastServiceKm + it }

    fun remainingKm(currentKm: Double, dueKm: Double?): Double? = dueKm?.minus(currentKm)

    fun calibratedVehicleOdometer(gpsMileageKm: Double, calibrationGpsKm: Double, calibrationVehicleKm: Double): Double =
        calibrationVehicleKm + max(0.0, gpsMileageKm - calibrationGpsKm)
}
