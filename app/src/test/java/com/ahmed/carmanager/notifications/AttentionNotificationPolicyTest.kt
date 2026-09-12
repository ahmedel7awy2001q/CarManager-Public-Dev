package com.ahmed.carmanager.notifications

import com.ahmed.carmanager.data.local.model.DocumentType
import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.FaultSeverity
import com.ahmed.carmanager.data.local.model.FaultStatus
import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.ReminderEntity
import com.ahmed.carmanager.data.local.model.ReminderRule
import com.ahmed.carmanager.data.local.model.VehicleDocumentEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionNotificationPolicyTest {
    private val now = 1_800_000_000_000L
    private val vehicle = VehicleEntity(
        vehicleId = "vehicle-1",
        displayName = "رينو لوجان 2021",
        brand = "Renault",
        model = "Logan",
        year = 2021,
        currentOdometerKm = 10_500.0
    )

    @Test
    fun dueSoonMaintenanceCreatesImportantNotification() {
        val plan = MaintenancePlanEntity(
            id = "oil",
            vehicleId = vehicle.vehicleId,
            titleAr = "زيت المحرك وفلتر الزيت",
            category = "engine",
            reminderRule = ReminderRule.ODOMETER_ONLY,
            nextDueOdometerKm = 11_000.0,
            warningBeforeKm = 1_000.0
        )

        val alerts = alerts(plans = listOf(plan))
        val alert = alerts.single { it.key.contains("maintenance") }

        assertEquals(AppAlertLevel.IMPORTANT, alert.level)
        assertTrue(alert.body.contains("500"))
        assertTrue(alert.body.contains("زيت المحرك"))
    }

    @Test
    fun overdueMaintenanceCreatesUrgentNotification() {
        val plan = MaintenancePlanEntity(
            id = "brakes",
            vehicleId = vehicle.vehicleId,
            titleAr = "تيل الفرامل الأمامي",
            category = "brakes",
            reminderRule = ReminderRule.ODOMETER_ONLY,
            nextDueOdometerKm = 10_000.0,
            warningBeforeKm = 1_000.0
        )

        val alert = alerts(plans = listOf(plan)).single()

        assertEquals(AppAlertLevel.URGENT, alert.level)
        assertTrue(alert.body.contains("متأخر"))
    }

    @Test
    fun criticalOpenFaultCreatesUrgentNotification() {
        val fault = FaultRecordEntity(
            id = "fault-1",
            vehicleId = vehicle.vehicleId,
            reportedDate = now,
            symptomAr = "ارتفاع حرارة المحرك",
            severity = FaultSeverity.CRITICAL,
            status = FaultStatus.OPEN
        )

        val alert = alerts(faults = listOf(fault)).single()

        assertEquals(AppAlertLevel.URGENT, alert.level)
        assertTrue(alert.body.contains("ارتفاع حرارة المحرك"))
    }

    @Test
    fun resolvedFaultDoesNotNotify() {
        val fault = FaultRecordEntity(
            id = "fault-2",
            vehicleId = vehicle.vehicleId,
            reportedDate = now,
            symptomAr = "صوت بالعفشة",
            severity = FaultSeverity.CRITICAL,
            status = FaultStatus.RESOLVED,
            resolvedDate = now
        )

        assertFalse(alerts(faults = listOf(fault)).any { it.key.contains("fault") })
    }

    @Test
    fun documentWithinThirtyDaysCreatesReminderAndExpiredBecomesUrgent() {
        val soon = VehicleDocumentEntity(
            id = "license-soon",
            vehicleId = vehicle.vehicleId,
            documentType = DocumentType.VEHICLE_LICENSE,
            expiryDate = now + 20L * AttentionNotificationPolicy.DAY_MS
        )
        val expired = VehicleDocumentEntity(
            id = "license-expired",
            vehicleId = vehicle.vehicleId,
            documentType = DocumentType.INSPECTION,
            expiryDate = now - AttentionNotificationPolicy.DAY_MS
        )

        val alerts = alerts(documents = listOf(soon, expired))

        assertEquals(AppAlertLevel.REMINDER, alerts.single { it.key.contains("license-soon") }.level)
        assertEquals(AppAlertLevel.URGENT, alerts.single { it.key.contains("license-expired") }.level)
    }

    @Test
    fun odometerReminderUsesDefaultFiveHundredKmWarning() {
        val reminder = ReminderEntity(
            id = "rotate-tires",
            vehicleId = vehicle.vehicleId,
            titleAr = "تدوير الإطارات",
            rule = ReminderRule.ODOMETER_ONLY,
            dueOdometerKm = 10_900.0
        )

        val alert = alerts(reminders = listOf(reminder)).single()

        assertEquals(AppAlertLevel.REMINDER, alert.level)
        assertTrue(alert.body.contains("400"))
    }

    @Test
    fun farFutureMaintenanceStaysSilent() {
        val plan = MaintenancePlanEntity(
            id = "future",
            vehicleId = vehicle.vehicleId,
            titleAr = "زيت الفتيس",
            category = "transmission",
            reminderRule = ReminderRule.ODOMETER_ONLY,
            nextDueOdometerKm = 30_000.0,
            warningBeforeKm = 1_000.0
        )

        assertTrue(alerts(plans = listOf(plan)).isEmpty())
    }

    private fun alerts(
        plans: List<MaintenancePlanEntity> = emptyList(),
        faults: List<FaultRecordEntity> = emptyList(),
        documents: List<VehicleDocumentEntity> = emptyList(),
        reminders: List<ReminderEntity> = emptyList()
    ): List<AppAlert> = AttentionNotificationPolicy.forVehicle(
        vehicle = vehicle,
        plans = plans,
        faults = faults,
        documents = documents,
        reminders = reminders,
        now = now
    )
}
