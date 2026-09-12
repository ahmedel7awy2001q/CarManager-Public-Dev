package com.ahmed.carmanager.ui

import kotlin.math.ceil
import kotlin.math.max

/** Pure, deterministic pricing engine so work-trip quotes can be unit-tested independently of Compose. */
internal data class TripPricingInput(
    val oneWayDistanceKm: Double,
    val roundTrip: Boolean,
    val passengerCount: Int,
    /** Total legal seats including the driver. */
    val totalSeatCapacity: Int?,
    val waitingHours: Double,
    val waitingRatePerHour: Double,
    val tolls: Double,
    val driverExpense: Double,
    val consumptionLitersPer100Km: Double?,
    val fuelPricePerLiter: Double?,
    val maintenancePerKm: Double,
    val includeMaintenance: Boolean,
    val annualFixedPerKm: Double,
    val includeAnnualFixed: Boolean,
    val depreciationPerKm: Double,
    val includeDepreciation: Boolean,
    val profitMarginPercent: Double,
    val marketOffer: Double? = null
)

internal data class TripPricingResult(
    val totalDistanceKm: Double,
    val passengerSeats: Int?,
    val capacityExceeded: Boolean,
    val requiredCars: Int,
    val waitingCost: Double,
    val fuelLiters: Double?,
    val fuelCost: Double?,
    val maintenanceShare: Double,
    val annualFixedShare: Double,
    val depreciationShare: Double,
    val directTripCost: Double,
    val trueTripCost: Double,
    val suggestedQuote: Double,
    val perPassengerQuote: Double?,
    val marketProfit: Double?
)

internal object TripPricingEngine {
    fun calculate(input: TripPricingInput): TripPricingResult {
        val oneWay = input.oneWayDistanceKm.safeNonNegative()
        val totalDistance = oneWay * if (input.roundTrip) 2.0 else 1.0
        val passengers = input.passengerCount.coerceAtLeast(0)
        val totalSeats = input.totalSeatCapacity?.takeIf { it > 0 }
        val passengerSeats = totalSeats?.let { max(0, it - 1) }
        val passengerSeatCount = passengerSeats ?: 0
        val capacityExceeded = passengerSeats != null && passengers > passengerSeatCount
        val requiredCars = if (capacityExceeded && passengerSeatCount > 0) {
            ceil(passengers.toDouble() / passengerSeatCount.toDouble()).toInt().coerceAtLeast(1)
        } else 1

        val waitingCost = input.waitingHours.safeNonNegative() * input.waitingRatePerHour.safeNonNegative()
        val consumption = input.consumptionLitersPer100Km?.takeIf { it.isFinite() && it > 0.0 }
        val fuelPrice = input.fuelPricePerLiter?.takeIf { it.isFinite() && it > 0.0 }
        val fuelLiters = consumption?.let { totalDistance * it / 100.0 }
        val fuelCost = if (fuelLiters != null && fuelPrice != null) fuelLiters * fuelPrice else null

        val maintenanceShare = if (input.includeMaintenance) totalDistance * input.maintenancePerKm.safeNonNegative() else 0.0
        val annualFixedShare = if (input.includeAnnualFixed) totalDistance * input.annualFixedPerKm.safeNonNegative() else 0.0
        val depreciationShare = if (input.includeDepreciation) totalDistance * input.depreciationPerKm.safeNonNegative() else 0.0

        val directTripCost = (fuelCost ?: 0.0) + input.tolls.safeNonNegative() + input.driverExpense.safeNonNegative() + waitingCost
        val trueTripCost = directTripCost + maintenanceShare + annualFixedShare + depreciationShare
        val margin = input.profitMarginPercent.takeIf { it.isFinite() }?.coerceIn(0.0, 500.0) ?: 0.0
        val suggestedQuote = trueTripCost * (1.0 + margin / 100.0)
        val perPassengerQuote = if (passengers > 0) suggestedQuote / passengers else null
        val marketProfit = input.marketOffer?.takeIf { it.isFinite() && it >= 0.0 }?.minus(trueTripCost)

        return TripPricingResult(
            totalDistanceKm = totalDistance,
            passengerSeats = passengerSeats,
            capacityExceeded = capacityExceeded,
            requiredCars = requiredCars,
            waitingCost = waitingCost,
            fuelLiters = fuelLiters,
            fuelCost = fuelCost,
            maintenanceShare = maintenanceShare,
            annualFixedShare = annualFixedShare,
            depreciationShare = depreciationShare,
            directTripCost = directTripCost,
            trueTripCost = trueTripCost,
            suggestedQuote = suggestedQuote,
            perPassengerQuote = perPassengerQuote,
            marketProfit = marketProfit
        )
    }
}

private fun Double.safeNonNegative(): Double = if (isFinite()) coerceAtLeast(0.0) else 0.0
