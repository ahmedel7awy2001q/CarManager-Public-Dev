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
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenancePriorityEngine
import com.ahmed.carmanager.data.maintenance.MaintenancePriorityLevel
import com.ahmed.carmanager.data.maintenance.MaintenanceUrgency
import kotlin.math.ceil

/**
 * Single source of truth for background attention notifications.
 *
 * The same vehicle data that powers the in-app attention center is evaluated here without changing
 * Room data. Alerts are intentionally conservative: only actionable maintenance, important open
 * faults, expiring documents and explicit reminders leave the app as system notifications.
 */
internal enum class AppAlertLevel { URGENT, IMPORTANT, REMINDER }

internal data class AppAlert(
    val key: String,
    val title: String,
    val body: String,
    val level: AppAlertLevel,
    /** Stable state token used to notify immediately when the condition changes. */
    val fingerprint: String,
    /** Minimum time before repeating an unchanged alert. */
    val repeatAfterMs: Long
)

internal object AttentionNotificationPolicy {
    internal const val DAY_MS = 86_400_000L
    private const val URGENT_REPEAT_MS = DAY_MS
    private const val IMPORTANT_REPEAT_MS = 2L * DAY_MS
    private const val REMINDER_REPEAT_MS = 7L * DAY_MS

    fun forVehicle(
        vehicle: VehicleEntity,
        plans: List<MaintenancePlanEntity>,
        faults: List<FaultRecordEntity>,
        documents: List<VehicleDocumentEntity>,
        reminders: List<ReminderEntity>,
        now: Long = System.currentTimeMillis()
    ): List<AppAlert> {
        val vehicleName = vehicle.displayName?.takeIf { it.isNotBlank() }
            ?: listOf(vehicle.brand, vehicle.model).filter { it.isNotBlank() }.joinToString(" ")

        return buildList {
            addMaintenanceAlerts(vehicle, vehicleName, plans, faults, now)
            addFaultAlerts(vehicleName, faults)
            addDocumentAlerts(vehicleName, documents, now)
            addReminderAlerts(vehicle, vehicleName, reminders, now)
        }
            .distinctBy { it.key }
            .sortedWith(compareBy<AppAlert> { it.level.ordinal }.thenBy { it.title }.thenBy { it.key })
    }

    private fun MutableList<AppAlert>.addMaintenanceAlerts(
        vehicle: VehicleEntity,
        vehicleName: String,
        plans: List<MaintenancePlanEntity>,
        faults: List<FaultRecordEntity>,
        now: Long
    ) {
        plans.asSequence()
            .filter { it.isActive && !it.isDeleted }
            .forEach { plan ->
                val status = MaintenanceAdvisor.statusFor(vehicle, plan, now)
                if (status.urgency == MaintenanceUrgency.UPCOMING) return@forEach

                val decision = MaintenancePriorityEngine.evaluate(status, faults)
                val level = when {
                    status.urgency == MaintenanceUrgency.OVERDUE -> AppAlertLevel.URGENT
                    decision.level == MaintenancePriorityLevel.CRITICAL -> AppAlertLevel.URGENT
                    else -> AppAlertLevel.IMPORTANT
                }
                val detail = when (status.urgency) {
                    MaintenanceUrgency.OVERDUE -> overdueDetail(status.overdueByKm, status.overdueByDays)
                    MaintenanceUrgency.DUE_SOON -> dueSoonDetail(status.remainingKm, status.remainingDays, status.forecastRemainingDays)
                    MaintenanceUrgency.UPCOMING -> ""
                }
                val body = listOf(plan.titleAr, detail, decision.reasonAr)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" • ")

                add(
                    AppAlert(
                        key = "maintenance:${vehicle.vehicleId}:${plan.id}",
                        title = if (level == AppAlertLevel.URGENT) "صيانة تحتاج إجراء — $vehicleName" else "موعد صيانة يقترب — $vehicleName",
                        body = body,
                        level = level,
                        fingerprint = "${status.urgency}:${decision.level}",
                        repeatAfterMs = repeatFor(level)
                    )
                )
            }
    }

    private fun MutableList<AppAlert>.addFaultAlerts(
        vehicleName: String,
        faults: List<FaultRecordEntity>
    ) {
        faults.asSequence()
            .filter { fault ->
                !fault.isDeleted &&
                    fault.status != FaultStatus.RESOLVED &&
                    fault.status != FaultStatus.CLOSED &&
                    (fault.severity == FaultSeverity.HIGH || fault.severity == FaultSeverity.CRITICAL)
            }
            .forEach { fault ->
                val level = if (fault.severity == FaultSeverity.CRITICAL) AppAlertLevel.URGENT else AppAlertLevel.IMPORTANT
                val severity = if (fault.severity == FaultSeverity.CRITICAL) "حرج" else "مهم"
                val body = buildString {
                    append(fault.symptomAr)
                    append(" • عطل $severity مفتوح")
                    fault.diagnosisAr?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                }
                add(
                    AppAlert(
                        key = "fault:${fault.vehicleId}:${fault.id}",
                        title = "تنبيه على السيارة — $vehicleName",
                        body = body,
                        level = level,
                        fingerprint = "${fault.severity}:${fault.status}:${fault.updatedAt}",
                        repeatAfterMs = repeatFor(level)
                    )
                )
            }
    }

    private fun MutableList<AppAlert>.addDocumentAlerts(
        vehicleName: String,
        documents: List<VehicleDocumentEntity>,
        now: Long
    ) {
        documents.asSequence()
            .filter { !it.isDeleted && it.expiryDate != null }
            .forEach { document ->
                val expiry = document.expiryDate ?: return@forEach
                val remainingMs = expiry - now
                val days = if (remainingMs >= 0L) ceil(remainingMs.toDouble() / DAY_MS.toDouble()).toLong() else remainingMs / DAY_MS
                if (days > 30L) return@forEach

                val state = when {
                    remainingMs < 0L -> "expired"
                    days <= 7L -> "week"
                    else -> "month"
                }
                val level = when (state) {
                    "expired" -> AppAlertLevel.URGENT
                    "week" -> AppAlertLevel.IMPORTANT
                    else -> AppAlertLevel.REMINDER
                }
                val detail = when {
                    remainingMs < 0L -> "منتهي ويحتاج تجديدًا"
                    days == 0L -> "ينتهي اليوم"
                    days == 1L -> "ينتهي خلال يوم"
                    else -> "ينتهي خلال $days يوم"
                }
                add(
                    AppAlert(
                        key = "document:${document.vehicleId}:${document.id}",
                        title = "مستند يحتاج انتباه — $vehicleName",
                        body = "${documentName(document.documentType)} • $detail",
                        level = level,
                        fingerprint = state,
                        repeatAfterMs = repeatFor(level)
                    )
                )
            }
    }

    private fun MutableList<AppAlert>.addReminderAlerts(
        vehicle: VehicleEntity,
        vehicleName: String,
        reminders: List<ReminderEntity>,
        now: Long
    ) {
        reminders.asSequence()
            .filter { !it.isDeleted && !it.isCompleted }
            .forEach { reminder ->
                val dateWarningDays = (reminder.warningBeforeDays ?: 7).coerceAtLeast(0)
                val kmWarning = (reminder.warningBeforeKm ?: 500.0).coerceAtLeast(0.0)
                val dateDue = reminder.dueDate?.let { now >= it - dateWarningDays * DAY_MS } ?: false
                val kmDue = reminder.dueOdometerKm?.let { vehicle.currentOdometerKm >= it - kmWarning } ?: false
                val triggered = when (reminder.rule) {
                    ReminderRule.DATE_ONLY -> dateDue
                    ReminderRule.ODOMETER_ONLY -> kmDue
                    ReminderRule.WHICHEVER_COMES_FIRST -> dateDue || kmDue
                } || reminder.priority >= 2
                if (!triggered) return@forEach

                val dateOverdue = reminder.dueDate?.let { now > it } ?: false
                val kmOverdue = reminder.dueOdometerKm?.let { vehicle.currentOdometerKm > it } ?: false
                val overdue = when (reminder.rule) {
                    ReminderRule.DATE_ONLY -> dateOverdue
                    ReminderRule.ODOMETER_ONLY -> kmOverdue
                    ReminderRule.WHICHEVER_COMES_FIRST -> dateOverdue || kmOverdue
                }
                val level = when {
                    overdue && reminder.priority >= 2 -> AppAlertLevel.URGENT
                    overdue -> AppAlertLevel.IMPORTANT
                    reminder.priority >= 2 -> AppAlertLevel.IMPORTANT
                    else -> AppAlertLevel.REMINDER
                }
                val details = buildList {
                    if (overdue) add("متأخر ويحتاج إجراء")
                    else {
                        reminder.dueOdometerKm?.let { dueKm ->
                            val remaining = dueKm - vehicle.currentOdometerKm
                            if (remaining >= 0.0 && remaining <= kmWarning) add("متبقي ${formatKm(remaining)} كم")
                        }
                        reminder.dueDate?.let { dueDate ->
                            val remainingDays = ceil((dueDate - now).coerceAtLeast(0L).toDouble() / DAY_MS.toDouble()).toLong()
                            if (remainingDays <= dateWarningDays) add(if (remainingDays == 0L) "موعده اليوم" else "متبقي $remainingDays يوم")
                        }
                    }
                    if (isEmpty() && reminder.priority >= 2) add("تذكير مهم")
                }
                add(
                    AppAlert(
                        key = "reminder:${reminder.vehicleId}:${reminder.id}",
                        title = "تذكير — $vehicleName",
                        body = listOf(reminder.titleAr, details.joinToString(" • ")).filter { it.isNotBlank() }.joinToString(" • "),
                        level = level,
                        fingerprint = "${if (overdue) "overdue" else "due"}:${reminder.priority}",
                        repeatAfterMs = repeatFor(level)
                    )
                )
            }
    }

    private fun overdueDetail(overdueKm: Double?, overdueDays: Long?): String = when {
        overdueKm != null && overdueKm > 0.0 && overdueDays != null && overdueDays > 0L ->
            "متأخر ${formatKm(overdueKm)} كم و$overdueDays يوم"
        overdueKm != null && overdueKm > 0.0 -> "متأخر ${formatKm(overdueKm)} كم"
        overdueDays != null && overdueDays > 0L -> "متأخر $overdueDays يوم"
        else -> "مستحقة الآن"
    }

    private fun dueSoonDetail(remainingKm: Double?, remainingDays: Long?, forecastDays: Long?): String = when {
        remainingKm != null && remainingKm >= 0.0 && remainingDays != null && remainingDays >= 0L ->
            "متبقي ${formatKm(remainingKm)} كم أو $remainingDays يوم"
        remainingKm != null && remainingKm >= 0.0 -> "متبقي ${formatKm(remainingKm)} كم"
        remainingDays != null && remainingDays >= 0L -> "متبقي $remainingDays يوم"
        forecastDays != null && forecastDays >= 0L -> "متوقع خلال $forecastDays يوم"
        else -> "موعدها يقترب"
    }

    private fun documentName(type: DocumentType): String = when (type) {
        DocumentType.VEHICLE_LICENSE -> "رخصة السيارة"
        DocumentType.INSURANCE -> "التأمين"
        DocumentType.INSPECTION -> "الفحص"
        DocumentType.CONTRACT -> "العقد"
        DocumentType.RECEIPT -> "الإيصال"
        DocumentType.OTHER -> "مستند السيارة"
    }

    private fun repeatFor(level: AppAlertLevel): Long = when (level) {
        AppAlertLevel.URGENT -> URGENT_REPEAT_MS
        AppAlertLevel.IMPORTANT -> IMPORTANT_REPEAT_MS
        AppAlertLevel.REMINDER -> REMINDER_REPEAT_MS
    }

    private fun formatKm(value: Double): String = value.coerceAtLeast(0.0).toLong().toString()
}
