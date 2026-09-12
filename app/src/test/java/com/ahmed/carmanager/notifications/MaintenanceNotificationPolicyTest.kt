package com.ahmed.carmanager.notifications

import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.ReminderRule
import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceNotificationPolicyTest {

    private val now = 1_800_000_000_000L

    @Test
    fun odometerAlertStartsAtConfiguredLeadDistance() {
        val plan = plan(
            rule = ReminderRule.ODOMETER_ONLY,
            nextKm = 10_000.0,
            warningKm = 1_000.0
        )

        val outsideWindow = vehicle(currentKm = 8_900.0)
        assertTrue(MaintenanceNotificationPolicy.alertsFor(outsideWindow, listOf(plan), now).isEmpty())

        val insideWindow = vehicle(currentKm = 9_100.0)
        val alert = MaintenanceNotificationPolicy.alertsFor(insideWindow, listOf(plan), now).single()
        assertEquals(MaintenanceAlertLevel.DUE_SOON, alert.level)
        assertTrue(alert.body.contains("متبقي 900 كم"))
        assertTrue(alert.body.contains("قبل 1000 كم"))
    }

    @Test
    fun dateAlertStartsAtConfiguredLeadDays() {
        val tenDaysAway = plan(
            rule = ReminderRule.DATE_ONLY,
            nextDate = now + 10L * MaintenanceNotificationPolicy.DAY_MS,
            warningDays = 7
        )
        assertTrue(MaintenanceNotificationPolicy.alertsFor(vehicle(), listOf(tenDaysAway), now).isEmpty())

        val fiveDaysAway = tenDaysAway.copy(
            id = "date-near",
            nextDueDate = now + 5L * MaintenanceNotificationPolicy.DAY_MS
        )
        val alert = MaintenanceNotificationPolicy.alertsFor(vehicle(), listOf(fiveDaysAway), now).single()
        assertEquals(MaintenanceAlertLevel.DUE_SOON, alert.level)
        assertTrue(alert.body.contains("متبقي 5 يوم"))
        assertTrue(alert.body.contains("قبل 7 يوم"))
    }

    @Test
    fun dueLaterTodayIsDueSoonNotOverdue() {
        val dueLaterToday = plan(
            rule = ReminderRule.DATE_ONLY,
            nextDate = now + 12L * 60L * 60L * 1_000L,
            warningDays = 1
        )

        val alert = MaintenanceNotificationPolicy.alertsFor(vehicle(), listOf(dueLaterToday), now).single()

        assertEquals(MaintenanceAlertLevel.DUE_SOON, alert.level)
        assertTrue(alert.body.contains("موعدها اليوم"))
    }

    @Test
    fun whicheverComesFirstTriggersWhenEitherDimensionEntersWindow() {
        val plan = plan(
            rule = ReminderRule.WHICHEVER_COMES_FIRST,
            nextKm = 50_000.0,
            warningKm = 500.0,
            nextDate = now + 3L * MaintenanceNotificationPolicy.DAY_MS,
            warningDays = 7
        )

        val alert = MaintenanceNotificationPolicy.alertsFor(vehicle(currentKm = 20_000.0), listOf(plan), now).single()

        assertEquals(MaintenanceAlertLevel.DUE_SOON, alert.level)
        assertTrue(alert.body.contains("متبقي 3 يوم"))
    }

    @Test
    fun inactiveOrDeletedPlansNeverNotify() {
        val base = plan(
            rule = ReminderRule.ODOMETER_ONLY,
            nextKm = 10_000.0,
            warningKm = 1_000.0
        )
        val vehicle = vehicle(currentKm = 9_900.0)

        assertTrue(MaintenanceNotificationPolicy.alertsFor(vehicle, listOf(base.copy(isActive = false)), now).isEmpty())
        assertTrue(MaintenanceNotificationPolicy.alertsFor(vehicle, listOf(base.copy(id = "deleted", isDeleted = true)), now).isEmpty())
    }

    @Test
    fun overdueAlertIsUrgentAndUsesDailyRepeatWindow() {
        val overdue = plan(
            rule = ReminderRule.ODOMETER_ONLY,
            nextKm = 10_000.0,
            warningKm = 1_000.0
        )

        val alert = MaintenanceNotificationPolicy.alertsFor(vehicle(currentKm = 10_250.0), listOf(overdue), now).single()

        assertEquals(MaintenanceAlertLevel.OVERDUE, alert.level)
        assertEquals(MaintenanceNotificationPolicy.DAY_MS, alert.repeatAfterMs)
        assertTrue(alert.body.contains("متأخرة 250 كم"))
    }

    private fun vehicle(currentKm: Double = 8_000.0) = VehicleEntity(
        vehicleId = "vehicle-1",
        brand = "Renault",
        model = "Logan",
        year = 2021,
        currentOdometerKm = currentKm
    )

    private fun plan(
        rule: ReminderRule,
        nextKm: Double? = null,
        warningKm: Double? = null,
        nextDate: Long? = null,
        warningDays: Int? = null
    ) = MaintenancePlanEntity(
        id = "plan-1",
        vehicleId = "vehicle-1",
        titleAr = "زيت المحرك",
        category = "المحرك",
        reminderRule = rule,
        nextDueOdometerKm = nextKm,
        warningBeforeKm = warningKm,
        nextDueDate = nextDate,
        warningBeforeDays = warningDays,
        updatedAt = now - 1_000L
    )
}
