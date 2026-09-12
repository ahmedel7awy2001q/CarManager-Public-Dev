package com.ahmed.carmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPricingEngineTest {
    @Test
    fun fullTrueCostAndMargin_areDeterministic() {
        val result = TripPricingEngine.calculate(
            TripPricingInput(
                oneWayDistanceKm = 250.0,
                roundTrip = true,
                passengerCount = 4,
                totalSeatCapacity = 5,
                waitingHours = 2.0,
                waitingRatePerHour = 100.0,
                tolls = 50.0,
                driverExpense = 250.0,
                consumptionLitersPer100Km = 10.0,
                fuelPricePerLiter = 24.0,
                maintenancePerKm = 1.09,
                includeMaintenance = true,
                annualFixedPerKm = 0.10,
                includeAnnualFixed = true,
                depreciationPerKm = 1.80,
                includeDepreciation = true,
                profitMarginPercent = 20.0,
                marketOffer = 3_000.0
            )
        )

        assertEquals(500.0, result.totalDistanceKm, 0.001)
        assertEquals(50.0, result.fuelLiters ?: 0.0, 0.001)
        assertEquals(1_200.0, result.fuelCost ?: 0.0, 0.001)
        assertEquals(545.0, result.maintenanceShare, 0.001)
        assertEquals(50.0, result.annualFixedShare, 0.001)
        assertEquals(900.0, result.depreciationShare, 0.001)
        assertEquals(3_195.0, result.trueTripCost, 0.001)
        assertEquals(3_834.0, result.suggestedQuote, 0.001)
        assertTrue((result.marketProfit ?: 0.0) < 0.0)
    }

    @Test
    fun capacity_countsDriverSeat_andReportsRequiredCars() {
        val result = TripPricingEngine.calculate(
            TripPricingInput(
                oneWayDistanceKm = 100.0,
                roundTrip = false,
                passengerCount = 10,
                totalSeatCapacity = 5,
                waitingHours = 0.0,
                waitingRatePerHour = 0.0,
                tolls = 0.0,
                driverExpense = 0.0,
                consumptionLitersPer100Km = 10.0,
                fuelPricePerLiter = 24.0,
                maintenancePerKm = 0.0,
                includeMaintenance = false,
                annualFixedPerKm = 0.0,
                includeAnnualFixed = false,
                depreciationPerKm = 0.0,
                includeDepreciation = false,
                profitMarginPercent = 0.0
            )
        )

        assertEquals(4, result.passengerSeats)
        assertTrue(result.capacityExceeded)
        assertEquals(3, result.requiredCars)
    }
}
