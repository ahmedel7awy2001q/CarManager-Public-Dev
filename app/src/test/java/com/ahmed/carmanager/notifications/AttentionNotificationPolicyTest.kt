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
    fun maintenanceUsesUserSelectedKilometerWarning() {
        val plan = MaintenancePlanEntity(
            id = "oil",
            vehicleId = vehicle.vehicleId,
            titleAr = "زيت المحرك وفلتر الزيت",
            category = "engine",
            reminderRule = ReminderRule.ODOMETER_ONLY,
            nextDueOdometerKm = 11_000.0,
            warningBeforeKm = 600.0
        )

        val alert = alerts(plans = listOf(plan)).single { it.key.contains("maintenance") }

        assertEquals(AppAlertLevel.IMPORTANT, alert.level)
        assertTrue(alert.body.contains("500"))
        assertTrue(alert.body.contains("زيت المحرك"))
    }

    @Test
    fun maintenanceOutsideUserSelectedKilometerWarningStaysSilent() {
        val plan = MaintenancePlanEntity(
            id = "oil-later",
            vehicleId = vehicle.vehicleId,
            titleAr = "زيت المحرك",
            category = "engine",
            reminderRule = ReminderRule.ODOMETER_ONLY,
            nextDueOdometerKm = 11_000.0,
            warningBeforeKm = 400.0
        )

        assertTrue(alerts(plans = listOf(plan)).isEmpty())
    }

    @Test
    fun maintenanceUsesUserSelectedDateWarning() {
        val plan = MaintenancePlanEntity(
            id = "date-plan",
            vehicleId = vehicle.vehicleId,
            titleAr = "فحص دوري",
            category = "inspection",
            reminderRule = ReminderRule.DATE_ONLY,
            nextDueDate = now + 10L * AttentionNotificationPolicy.DAY_MS,
            warningBeforeDays = 14
        )

        val alert = alerts(plans = listOf(plan)).single()

        assertEquals(AppAlertLevel.IMPORTANT, alert.level)
        assertTrue(alert.body.contains("10 يوم"))
    }

    @Test
    fun highPriorityMaintenanceCreatesUrgentNotificationWhenDueSoon() {
        val plan = MaintenancePlanEntity(
            id = "brakes",
            vehicleId = vehicle.vehicleId,
            titleAr = "تيل الفرامل الأمامي",
            category = "brakes",
            reminderRule = ReminderRule.ODOMETER_ONLY,
            nextDueOdometerKm = 11_000.0,
            warningBeforeKm = 1_000.0,
            priority = 10
        )

        val alert = alerts(plans = listOf(plan)).single()

        assertEquals(AppAlertLevel.URGENT, alert.level)
        assertTrue(alert.body.contains("أولوية عالية"))
    }

    @Test
    fun overdueMaintenanceCreatesUrgentNotification() {
        val plan = MaintenancePlanEntity(
            id = "overdue",
            vehicleId = vehicle.vehicleId,
            titleAr = "زيت الفتيس",
            category = "transmission",
            reminderRule = ReminderRule.ODOMETER_ONLY,
            nextDueOdometerKm = 10_000.0,
            warningBeforeKm = 1_000.0
        )

        val alert = alerts(plans = listOf(plan)).single()

        assertEquals(AppAlertLevel.URGENT, alert.level)
        assertTrue(alert.body.contains("متأخر"))
    }

    @Test
    fun highOpenFaultIsImportantAndCriticalIsUrgent() {
        val high = FaultRecordEntity(
            id = "fault-high",
            vehicleId = vehicle.vehicleId,
            reportedDate = now,
            symptomAr = "صوت قوي من الفرامل",
            severity = FaultSeverity.HIGH,
            status = FaultStatus.OPEN
        )
        val critical = FaultRecordEntity(
            id = "fault-critical",
            vehicleId = vehicle.vehicleId,
            reportedDate = now,
            symptomAr = "ارتفاع حرارة المحرك",
            severity = FaultSeverity.CRITICAL,
            status = FaultStatus.OPEN
        )

        val alerts = alerts(faults = listOf(high, critical))

        assertEquals(AppAlertLevel.IMPORTANT, alerts.single { it.key.contains("fault-high") }.level)
        assertEquals(AppAlertLevel.URGENT, alerts.single { it.key.contains("fault-critical") }.level)
    }

    @Test
    fun resolvedFaultDoesNotNotify() {
        val fault = FaultRecordEntity(
            id = "fault-resolved",
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
    fun licenseInsuranceAndInspectionUsePriorityTiers() {
        val licenseSoon = VehicleDocumentEntity(
            id = "license-soon",
            vehicleId = vehicle.vehicleId,
            documentType = DocumentType.VEHICLE_LICENSE,
            expiryDate = now + 20L * AttentionNotificationPolicy.DAY_MS
        )
        val insuranceSoon = VehicleDocumentEntity(
            id = "insurance-soon",
            vehicleId = vehicle.vehicleId,
            documentType = DocumentType.INSURANCE,
            expiryDate = now + 20L * AttentionNotificationPolicy.DAY_MS
        )
        val inspectionWeek = VehicleDocumentEntity(
            id = "inspection-week",
            vehicleId = vehicle.vehicleId,
            documentType = DocumentType.INSPECTION,
            expiryDate = now + 5L * AttentionNotificationPolicy.DAY_MS
        )
        val expiredInsurance = VehicleDocumentEntity(
            id = "insurance-expired",
            vehicleId = vehicle.vehicleId,
            documentType = DocumentType.INSURANCE,
            expiryDate = now - AttentionNotificationPolicy.DAY_MS
        )

        val alerts = alerts(documents = listOf(licenseSoon, insuranceSoon, inspectionWeek, expiredInsurance))

        assertEquals(AppAlertLevel.IMPORTANT, alerts.single { it.key.contains("license-soon") }.level)
        assertEquals(AppAlertLevel.REMINDER, alerts.single { it.key.contains("insurance-soon") }.level)
        assertEquals(AppAlertLevel.IMPORTANT, alerts.single { it.key.contains("inspection-week") }.level)
        assertEquals(AppAlertLevel.URGENT, alerts.single { it.key.contains("insurance-expired") }.level)
    }

    @Test
    fun unrelatedDocumentTypesDoNotCreateExpiryNotifications() {
        val receipt = VehicleDocumentEntity(
            id = "receipt",
            vehicleId = vehicle.vehicleId,
            documentType = DocumentType.RECEIPT,
            expiryDate = now + 2L * AttentionNotificationPolicy.DAY_MS
        )

        assertTrue(alerts(documents = listOf(receipt)).isEmpty())
    }

    @Test
    fun manualReminderPriorityFiveIsImportantAndTenIsUrgent() {
        val important = ReminderEntity(
            id = "manual-important",
            vehicleId = vehicle.vehicleId,
            titleAr = "راجع ضغط الإطارات",
            rule = ReminderRule.ODOMETER_ONLY,
            dueOdometerKm = 50_000.0,
            priority = 5
        )
        val urgent = ReminderEntity(
            id = "manual-urgent",
            vehicleId = vehicle.vehicleId,
            titleAr = "راجع تسريب زيت",
            rule = ReminderRule.ODOMETER_ONLY,
            dueOdometerKm = 50_000.0,
            priority = 10
        )

        val alerts = alerts(reminders = listOf(important, urgent))

        assertEquals(AppAlertLevel.IMPORTANT, alerts.single { it.key.contains("manual-important") }.level)
        assertEquals(AppAlertLevel.URGENT, alerts.single { it.key.contains("manual-urgent") }.level)
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
