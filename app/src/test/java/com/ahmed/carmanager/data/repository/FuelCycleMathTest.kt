package com.ahmed.carmanager.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelCycleMathTest {

    @Test
    fun firstFullTankIsAnchorAndDoesNotInventConsumption() {
        val result = FuelCycleMath.recalculate(
            listOf(row("first", 10_000.0, 40.0, 920.0, full = true))
        ).getValue("first")

        assertNull(result.distanceSincePreviousKm)
        assertNull(result.consumptionLitersPer100Km)
        assertNull(result.kmPerLiter)
        assertNull(result.costPerKm)
    }

    @Test
    fun partialRefillsAreIncludedUntilNextFullTankClosesCycle() {
        val result = FuelCycleMath.recalculate(
            listOf(
                row("anchor", 10_000.0, 40.0, 920.0, full = true),
                row("partial", 10_200.0, 20.0, 460.0, full = false),
                row("close", 10_400.0, 25.0, 575.0, full = true)
            )
        ).getValue("close")

        assertEquals(200.0, result.distanceSincePreviousKm!!, 0.0001)
        assertEquals(11.25, result.consumptionLitersPer100Km!!, 0.0001)
        assertEquals(100.0 / 11.25, result.kmPerLiter!!, 0.0001)
        assertEquals((460.0 + 575.0) / 400.0, result.costPerKm!!, 0.0001)
    }

    @Test
    fun consecutiveFullTanksUseOnlyClosingRefillForCycle() {
        val result = FuelCycleMath.recalculate(
            listOf(
                row("anchor", 20_000.0, 45.0, 1_000.0, full = true),
                row("close", 20_500.0, 50.0, 1_250.0, full = true)
            )
        ).getValue("close")

        assertEquals(10.0, result.consumptionLitersPer100Km!!, 0.0001)
        assertEquals(10.0, result.kmPerLiter!!, 0.0001)
        assertEquals(2.5, result.costPerKm!!, 0.0001)
    }

    @Test
    fun fuelPriceChangesAffectCostButNotLiterConsumptionFormula() {
        val result = FuelCycleMath.recalculate(
            listOf(
                row("anchor", 30_000.0, 35.0, 700.0, full = true),
                row("partial", 30_250.0, 20.0, 400.0, full = false),
                row("close", 30_500.0, 30.0, 900.0, full = true)
            )
        ).getValue("close")

        assertEquals(10.0, result.consumptionLitersPer100Km!!, 0.0001)
        assertEquals(1_300.0 / 500.0, result.costPerKm!!, 0.0001)
    }

    @Test
    fun removingPartialRefillChangesRebuiltCycleInsteadOfKeepingStaleConsumption() {
        val withPartial = FuelCycleMath.recalculate(
            listOf(
                row("anchor", 40_000.0, 40.0, 800.0, full = true),
                row("partial", 40_250.0, 20.0, 400.0, full = false),
                row("close", 40_500.0, 30.0, 600.0, full = true)
            )
        ).getValue("close")

        val afterDelete = FuelCycleMath.recalculate(
            listOf(
                row("anchor", 40_000.0, 40.0, 800.0, full = true),
                row("close", 40_500.0, 30.0, 600.0, full = true)
            )
        ).getValue("close")

        assertEquals(10.0, withPartial.consumptionLitersPer100Km!!, 0.0001)
        assertEquals(6.0, afterDelete.consumptionLitersPer100Km!!, 0.0001)
    }

    @Test
    fun fullTankRangeUsesVehicleTankCapacityWhilePartialUsesPurchasedLiters() {
        val fullRange = FuelCycleMath.estimatedRangeKm(
            isFullTank = true,
            purchasedLiters = 20.0,
            tankCapacityLiters = 50.0,
            consumptionLitersPer100Km = 10.0
        )
        val partialRange = FuelCycleMath.estimatedRangeKm(
            isFullTank = false,
            purchasedLiters = 20.0,
            tankCapacityLiters = 50.0,
            consumptionLitersPer100Km = 10.0
        )

        assertEquals(500.0, fullRange!!, 0.0001)
        assertEquals(200.0, partialRange!!, 0.0001)
    }

    @Test
    fun equalOdometerFullTanksDoNotProduceInfiniteConsumption() {
        val result = FuelCycleMath.recalculate(
            listOf(
                row("anchor", 50_000.0, 40.0, 900.0, full = true),
                row("close", 50_000.0, 5.0, 120.0, full = true)
            )
        ).getValue("close")

        assertNull(result.consumptionLitersPer100Km)
        assertNull(result.kmPerLiter)
        assertNull(result.costPerKm)
    }

    private fun row(
        key: String,
        odometerKm: Double,
        liters: Double,
        amountPaid: Double,
        full: Boolean
    ) = FuelCycleMath.Row(
        key = key,
        odometerKm = odometerKm,
        liters = liters,
        amountPaid = amountPaid,
        isFullTank = full
    )
}
