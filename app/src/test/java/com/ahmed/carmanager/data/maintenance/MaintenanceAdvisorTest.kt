package com.ahmed.carmanager.data.maintenance

import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.ReminderRule
import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MaintenanceAdvisorTest {

    private val vehicle = VehicleEntity(
        brand = "Kia",
        model = "Grand Cerato",
        year = 2021,
        currentOdometerKm = 93_300.0
    )

    @Test
    fun cerato2021GetsDedicatedPreset() {
        assertEquals(MaintenancePreset.CERATO_2021_EGYPT, MaintenanceCatalog.presetFor(vehicle))
    }

    @Test
    fun nextBundleGroupsItemsWithinOneThousandKmAndSumsKnownCosts() {
        val plans = listOf(
            plan("زيت المحرك", 100_000.0, 4_000.0),
            plan("فلتر الهواء", 100_500.0, 1_200.0),
            plan("فحص الفرامل", 100_800.0, null),
            plan("الإطارات", 140_000.0, 16_000.0)
        )
        val bundle = MaintenanceAdvisor.nextBundle(vehicle, plans)
        assertNotNull(bundle)
        assertEquals(3, bundle!!.items.size)
        assertEquals(5_200.0, bundle.estimatedCost, 0.01)
        assertEquals(1, bundle.unknownCostCount)
        assertEquals(100_000.0, bundle.targetOdometerKm!!, 0.01)
    }

    @Test
    fun overdueItemsArePrioritizedTogether() {
        val plans = listOf(
            plan("بند متأخر 1", 92_000.0, 500.0),
            plan("بند متأخر 2", 93_000.0, 700.0),
            plan("بند بعيد", 120_000.0, 5_000.0)
        )
        val bundle = MaintenanceAdvisor.nextBundle(vehicle, plans)
        assertNotNull(bundle)
        assertTrue(bundle!!.hasOverdueItems)
        assertEquals(2, bundle.items.size)
        assertEquals(1_200.0, bundle.estimatedCost, 0.01)
    }

    @Test
    fun dateOnlyIgnoresOdometerEvenIfLegacyValueExists() {
        val now = utcDate(2026, Calendar.SEPTEMBER, 9)
        val plan = MaintenancePlanEntity(
            vehicleId = vehicle.vehicleId,
            titleAr = "بطارية",
            category = "الكهرباء",
            intervalMonths = 24,
            reminderRule = ReminderRule.DATE_ONLY,
            nextDueOdometerKm = 10_000.0,
            nextDueDate = now + 60L * 86_400_000L
        )
        val status = MaintenanceAdvisor.statusFor(vehicle, plan, now)
        assertNull(status.remainingKm)
        assertNotNull(status.remainingDays)
        assertEquals(MaintenanceUrgency.UPCOMING, status.urgency)
    }

    @Test
    fun odometerOnlyIgnoresExpiredLegacyDate() {
        val now = utcDate(2026, Calendar.SEPTEMBER, 9)
        val plan = MaintenancePlanEntity(
            vehicleId = vehicle.vehicleId,
            titleAr = "زيت",
            category = "المحرك",
            intervalKm = 10_000.0,
            reminderRule = ReminderRule.ODOMETER_ONLY,
            nextDueOdometerKm = 120_000.0,
            nextDueDate = now - 60L * 86_400_000L
        )
        val status = MaintenanceAdvisor.statusFor(vehicle, plan, now)
        assertNull(status.remainingDays)
        assertEquals(MaintenanceUrgency.UPCOMING, status.urgency)
    }

    @Test
    fun historicalServiceAt80kAutomaticallyInfersOverdueAt93k() {
        val now = utcDate(2026, Calendar.SEPTEMBER, 9)
        val lastService = utcDate(2026, Calendar.JULY, 1)
        val currentVehicle = vehicle.copy(currentOdometerKm = 93_000.0)
        val plan = MaintenancePlanEntity(
            vehicleId = currentVehicle.vehicleId,
            titleAr = "زيت المحرك",
            category = "المحرك",
            intervalKm = 10_000.0,
            intervalMonths = 12,
            reminderRule = ReminderRule.WHICHEVER_COMES_FIRST,
            lastServiceOdometerKm = 80_000.0,
            lastServiceDate = lastService,
            nextDueOdometerKm = null,
            nextDueDate = null
        )

        val status = MaintenanceAdvisor.statusFor(currentVehicle, plan, now)

        assertEquals(90_000.0, status.resolvedNextDueOdometerKm!!, 0.01)
        assertEquals(MaintenanceUrgency.OVERDUE, status.urgency)
        assertEquals(3_000.0, status.overdueByKm!!, 0.01)
        assertNotNull(status.usageProjection)
        assertEquals(13_000.0, status.usageProjection!!.observedKm, 0.01)
        assertEquals(70.0, status.usageProjection!!.observedDays, 0.01)
        assertTrue(status.usageProjection!!.annualKm in 67_000.0..69_000.0)
        assertNotNull(status.projectedOdometerDueDate)
        val expectedAround = utcDate(2026, Calendar.AUGUST, 24)
        assertTrue(kotlin.math.abs(status.projectedOdometerDueDate!! - expectedAround) <= 2L * 86_400_000L)
    }

    @Test
    fun historicalAnchorProjectsFutureOdometerDateFromObservedUsage() {
        val now = utcDate(2026, Calendar.SEPTEMBER, 9)
        val lastService = utcDate(2026, Calendar.JULY, 1)
        val currentVehicle = vehicle.copy(currentOdometerKm = 93_000.0)
        val plan = MaintenancePlanEntity(
            vehicleId = currentVehicle.vehicleId,
            titleAr = "فلتر الهواء",
            category = "الفلاتر",
            intervalKm = 20_000.0,
            reminderRule = ReminderRule.ODOMETER_ONLY,
            lastServiceOdometerKm = 80_000.0,
            lastServiceDate = lastService
        )

        val status = MaintenanceAdvisor.statusFor(currentVehicle, plan, now)

        assertEquals(100_000.0, status.resolvedNextDueOdometerKm!!, 0.01)
        assertEquals(7_000.0, status.remainingKm!!, 0.01)
        assertNotNull(status.forecastDueDate)
        val expectedAround = utcDate(2026, Calendar.OCTOBER, 17)
        assertTrue(kotlin.math.abs(status.forecastDueDate!! - expectedAround) <= 2L * 86_400_000L)
    }

    private fun plan(title: String, nextKm: Double, cost: Double?) = MaintenancePlanEntity(
        vehicleId = vehicle.vehicleId,
        titleAr = title,
        category = "اختبار",
        intervalKm = 10_000.0,
        estimatedCost = cost,
        nextDueOdometerKm = nextKm
    )

    private fun utcDate(year: Int, month: Int, day: Int): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
        clear()
        set(year, month, day, 0, 0, 0)
        timeInMillis
    }
}