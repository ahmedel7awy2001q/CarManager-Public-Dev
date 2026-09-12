package com.ahmed.carmanager.data.maintenance

import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.FaultSeverity
import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.ReminderRule
import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenancePriorityEngineTest {

    private val vehicle = VehicleEntity(
        brand = "Kia",
        model = "Grand Cerato",
        year = 2021,
        currentOdometerKm = 93_300.0
    )

    @Test
    fun overdueSafetyItemIsCriticalWithClearReason() {
        val plan = plan("فحص منظومة الفرامل", 92_000.0, 1_000.0)
        val status = MaintenanceAdvisor.statusFor(vehicle, plan)
        val decision = MaintenancePriorityEngine.evaluate(status)

        assertEquals(MaintenancePriorityLevel.CRITICAL, decision.level)
        assertTrue(decision.reasonAr.contains("سلامة"))
        assertTrue(decision.reasonAr.contains("تجاوز"))
    }

    @Test
    fun dueSoonItemIsImportantAndMentionsRemainingDistance() {
        val plan = plan("زيت المحرك", 94_000.0, 4_000.0)
        val status = MaintenanceAdvisor.statusFor(vehicle, plan)
        val decision = MaintenancePriorityEngine.evaluate(status)

        assertEquals(MaintenancePriorityLevel.IMPORTANT, decision.level)
        assertTrue(decision.reasonAr.contains("كم"))
    }

    @Test
    fun relatedImportantFaultEscalatesMatchingMaintenanceToCritical() {
        val plan = plan("فحص المساعدين والعفشة", 100_000.0, 10_000.0)
        val status = MaintenanceAdvisor.statusFor(vehicle, plan)
        val fault = FaultRecordEntity(
            vehicleId = vehicle.vehicleId,
            reportedDate = System.currentTimeMillis(),
            symptomAr = "المساعدين الأمامي تحتاج فحص وتغيير",
            severity = FaultSeverity.HIGH
        )

        val decision = MaintenancePriorityEngine.evaluate(status, listOf(fault))

        assertEquals(MaintenancePriorityLevel.CRITICAL, decision.level)
        assertTrue(decision.reasonAr.contains("عطل"))
    }

    @Test
    fun prioritizedBundleOrdersItemsAndGroupsKnownCostByPriority() {
        val criticalPlan = plan("تيل الفرامل الأمامي", 92_000.0, 4_000.0)
        val importantPlan = plan("زيت المحرك", 94_000.0, 3_000.0)
        val deferPlan = plan("فلتر الهواء", 120_000.0, 1_200.0)

        val raw = MaintenanceBundle(
            items = listOf(
                MaintenanceAdvisor.statusFor(vehicle, deferPlan),
                MaintenanceAdvisor.statusFor(vehicle, importantPlan),
                MaintenanceAdvisor.statusFor(vehicle, criticalPlan)
            ),
            estimatedCost = 8_200.0,
            unknownCostCount = 0,
            targetOdometerKm = 92_000.0,
            targetDate = null,
            hasOverdueItems = true
        )

        val result = MaintenancePriorityEngine.prioritizeBundle(raw)

        assertEquals(MaintenancePriorityLevel.CRITICAL, result.items.first().decision.level)
        assertEquals(4_000.0, result.summary(MaintenancePriorityLevel.CRITICAL).estimatedCost, 0.01)
        assertEquals(3_000.0, result.summary(MaintenancePriorityLevel.IMPORTANT).estimatedCost, 0.01)
        assertEquals(1_200.0, result.summary(MaintenancePriorityLevel.CAN_DEFER).estimatedCost, 0.01)
    }

    private fun plan(title: String, nextKm: Double, cost: Double?) = MaintenancePlanEntity(
        vehicleId = vehicle.vehicleId,
        titleAr = title,
        category = "دورية",
        intervalKm = 10_000.0,
        reminderRule = ReminderRule.ODOMETER_ONLY,
        warningBeforeKm = 1_000.0,
        nextDueOdometerKm = nextKm,
        estimatedCost = cost
    )
}
