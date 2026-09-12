package com.ahmed.carmanager.notifications

import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.ReminderRule
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenanceUrgency

internal enum class MaintenanceAlertLevel { DUE_SOON, OVERDUE }

internal data class MaintenanceAlert(
    val key: String,
    val title: String,
    val body: String,
    val level: MaintenanceAlertLevel,
    /** Changes when the plan or alert state changes, so edited thresholds can notify immediately. */
    val fingerprint: String,
    /** Prevents a periodic worker from turning one due item into notification spam. */
    val repeatAfterMs: Long
)

/**
 * Pure policy for maintenance notifications.
 *
 * It deliberately reuses [MaintenanceAdvisor], which is also used by the in-app maintenance screens.
 * That keeps the notification threshold and the visible "قريبًا / مستحقة" state identical.
 */
internal object MaintenanceNotificationPolicy {
    const val DAY_MS = 86_400_000L
    private const val DUE_SOON_REPEAT_MS = 7L * DAY_MS
    private const val OVERDUE_REPEAT_MS = DAY_MS

    fun alertsFor(
        vehicle: VehicleEntity,
        plans: List<MaintenancePlanEntity>,
        now: Long = System.currentTimeMillis()
    ): List<MaintenanceAlert> {
        val vehicleName = vehicle.displayName?.takeIf { it.isNotBlank() }
            ?: listOf(vehicle.brand, vehicle.model).filter { it.isNotBlank() }.joinToString(" ")

        return plans.asSequence()
            .filter { it.isActive && !it.isDeleted }
            .mapNotNull { plan ->
                val status = MaintenanceAdvisor.statusFor(vehicle, plan, now)
                if (status.urgency == MaintenanceUrgency.UPCOMING) return@mapNotNull null

                val level = if (status.urgency == MaintenanceUrgency.OVERDUE) {
                    MaintenanceAlertLevel.OVERDUE
                } else {
                    MaintenanceAlertLevel.DUE_SOON
                }
                val detail = if (level == MaintenanceAlertLevel.OVERDUE) {
                    overdueDetail(status.overdueByKm, status.overdueByDays)
                } else {
                    dueSoonDetail(status.remainingKm, status.remainingDays, status.forecastRemainingDays, plan.reminderRule)
                }
                val threshold = thresholdDetail(plan)

                MaintenanceAlert(
                    key = "maintenance:${vehicle.vehicleId}:${plan.id}",
                    title = if (level == MaintenanceAlertLevel.OVERDUE) {
                        "صيانة مستحقة — $vehicleName"
                    } else {
                        "موعد صيانة يقترب — $vehicleName"
                    },
                    body = listOf(plan.titleAr, detail, threshold)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" • "),
                    level = level,
                    fingerprint = "${level.name}:${plan.updatedAt}:${plan.warningBeforeKm}:${plan.warningBeforeDays}:${plan.reminderRule}",
                    repeatAfterMs = if (level == MaintenanceAlertLevel.OVERDUE) OVERDUE_REPEAT_MS else DUE_SOON_REPEAT_MS
                )
            }
            .sortedWith(
                compareByDescending<MaintenanceAlert> { it.level == MaintenanceAlertLevel.OVERDUE }
                    .thenBy { it.title }
                    .thenBy { it.key }
            )
            .toList()
    }

    private fun overdueDetail(overdueKm: Double?, overdueDays: Long?): String = when {
        overdueKm != null && overdueKm > 0.0 && overdueDays != null && overdueDays > 0L ->
            "متأخرة ${formatKm(overdueKm)} كم و$overdueDays يوم"
        overdueKm != null && overdueKm > 0.0 -> "متأخرة ${formatKm(overdueKm)} كم"
        overdueDays != null && overdueDays > 0L -> "متأخرة $overdueDays يوم"
        else -> "مستحقة الآن"
    }

    private fun dueSoonDetail(
        remainingKm: Double?,
        remainingDays: Long?,
        forecastRemainingDays: Long?,
        rule: ReminderRule
    ): String {
        val kmText = remainingKm?.takeIf { it >= 0.0 }?.let { "متبقي ${formatKm(it)} كم" }
        val dayText = remainingDays?.takeIf { it >= 0L }?.let { daysText(it) }
        return when (rule) {
            ReminderRule.ODOMETER_ONLY -> kmText ?: forecastRemainingDays?.takeIf { it >= 0L }?.let { "متوقع خلال $it يوم" } ?: "موعدها يقترب"
            ReminderRule.DATE_ONLY -> dayText ?: "موعدها يقترب"
            ReminderRule.WHICHEVER_COMES_FIRST -> listOfNotNull(kmText, dayText).joinToString(" أو ").ifBlank {
                forecastRemainingDays?.takeIf { it >= 0L }?.let { "متوقع خلال $it يوم" } ?: "موعدها يقترب"
            }
        }
    }

    private fun thresholdDetail(plan: MaintenancePlanEntity): String {
        val km = if (plan.reminderRule != ReminderRule.DATE_ONLY) {
            plan.warningBeforeKm?.takeIf { it >= 0.0 }?.let { "قبل ${formatKm(it)} كم" }
        } else null
        val days = if (plan.reminderRule != ReminderRule.ODOMETER_ONLY) {
            plan.warningBeforeDays?.takeIf { it >= 0 }?.let { "قبل $it يوم" }
        } else null
        return listOfNotNull(km, days).joinToString(" أو ").takeIf { it.isNotBlank() }?.let { "التنبيه مضبوط $it" }.orEmpty()
    }

    private fun daysText(days: Long): String = when (days) {
        0L -> "موعدها اليوم"
        1L -> "متبقي يوم"
        2L -> "متبقي يومان"
        else -> "متبقي $days يوم"
    }

    private fun formatKm(value: Double): String = value.coerceAtLeast(0.0).toLong().toString()
}
