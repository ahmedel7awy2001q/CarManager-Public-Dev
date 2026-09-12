package com.ahmed.carmanager.domain

import com.ahmed.carmanager.data.local.model.FuelRecordEntity
import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmartVehicleInsightsTest {
    private val vehicle = VehicleEntity(
        vehicleId = "car-1",
        ownerUserId = "user-1",
        brand = "Kia",
        model = "Cerato",
        year = 2021,
        currentOdometerKm = 100_000.0
    )

    @Test
    fun detectsRecentConsumptionIncreaseAgainstUsersOwnBaseline() {
        val now = System.currentTimeMillis()
        val valuesNewestFirst = listOf(13.5, 13.0, 10.0, 10.2, 9.8, 10.1)
        val fuel = valuesNewestFirst.mapIndexed { index, value ->
            FuelRecordEntity(
                id = "f$index",
                vehicleId = vehicle.vehicleId,
                fuelDate = now - index * 86_400_000L,
                odometerKm = 100_000.0 - index * 500,
                fuelType = FuelType.GASOLINE_95,
                pricePerLiter = 20.0,
                amountPaid = 1_000.0,
                liters = 50.0,
                isFullTank = true,
                consumptionLitersPer100Km = value
            )
        }

        val result = SmartVehicleInsights.analyze(
            vehicle = vehicle,
            plans = emptyList(),
            maintenance = emptyList(),
            fuel = fuel,
            expenses = emptyList(),
            trips = emptyList(),
            reminders = emptyList(),
            faults = emptyList(),
            documents = emptyList(),
            now = now
        )

        assertTrue((result.fuelDeviationPercent ?: 0.0) > 25.0)
        assertTrue(result.insights.any { it.key == "fuel_high" })
    }

    @Test
    fun confidenceNeverPretendsToBeMechanicalHealth() {
        val result = SmartVehicleInsights.analyze(
            vehicle = vehicle,
            plans = emptyList(),
            maintenance = emptyList(),
            fuel = emptyList(),
            expenses = emptyList(),
            trips = emptyList(),
            reminders = emptyList(),
            faults = emptyList(),
            documents = emptyList()
        )

        assertEquals(35, result.dataConfidence)
        assertTrue(result.insights.any { it.key == "starter" })
    }

    @Test
    fun comparesCurrentMonthSpendingWithPreviousMonth() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = nowCal.timeInMillis
        fun fuelAt(id: String, month: Int, amount: Double) = FuelRecordEntity(
            id = id,
            vehicleId = vehicle.vehicleId,
            fuelDate = Calendar.getInstance().apply { set(2026, month, 10, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis,
            odometerKm = 100_000.0,
            fuelType = FuelType.GASOLINE_95,
            pricePerLiter = 20.0,
            amountPaid = amount,
            liters = amount / 20.0
        )

        val result = SmartVehicleInsights.analyze(
            vehicle = vehicle,
            plans = emptyList(),
            maintenance = emptyList(),
            fuel = listOf(
                fuelAt("sep", Calendar.SEPTEMBER, 2_000.0),
                fuelAt("aug", Calendar.AUGUST, 1_000.0)
            ),
            expenses = emptyList(),
            trips = emptyList(),
            reminders = emptyList(),
            faults = emptyList(),
            documents = emptyList(),
            now = now
        )

        assertEquals(2_000.0, result.monthSpend, 0.001)
        assertEquals(1_000.0, result.previousMonthSpend, 0.001)
        assertEquals(100.0, result.spendChangePercent ?: 0.0, 0.001)
    }

    @Test
    fun deletedFuelDoesNotAffectCurrentMonthSpendOrConsumption() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = nowCal.timeInMillis
        val active = FuelRecordEntity(
            id = "active",
            vehicleId = vehicle.vehicleId,
            fuelDate = now,
            odometerKm = 100_000.0,
            fuelType = FuelType.GASOLINE_95,
            pricePerLiter = 20.0,
            amountPaid = 1_000.0,
            liters = 50.0,
            consumptionLitersPer100Km = 10.0
        )
        val deleted = active.copy(
            id = "deleted",
            amountPaid = 9_000.0,
            consumptionLitersPer100Km = 99.0,
            isDeleted = true
        )

        val result = SmartVehicleInsights.analyze(
            vehicle = vehicle,
            plans = emptyList(),
            maintenance = emptyList(),
            fuel = listOf(active, deleted),
            expenses = emptyList(),
            trips = emptyList(),
            reminders = emptyList(),
            faults = emptyList(),
            documents = emptyList(),
            now = now
        )

        assertEquals(1_000.0, result.monthSpend, 0.001)
        assertEquals(10.0, result.averageConsumptionL100 ?: 0.0, 0.001)
    }
}
