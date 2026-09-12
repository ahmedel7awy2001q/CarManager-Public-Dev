package com.ahmed.carmanager.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ahmed.carmanager.MainActivity
import com.ahmed.carmanager.R
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.ReminderEntity
import com.ahmed.carmanager.data.local.model.ReminderRule
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Background delivery for maintenance and the app's pre-existing manual reminders.
 *
 * Maintenance due-state is calculated by [MaintenanceNotificationPolicy], which delegates to the
 * same MaintenanceAdvisor used by the UI. Notification state is persisted locally only to prevent
 * duplicate background alerts; no user maintenance data is copied outside Room.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!canPostNotifications()) return@withContext Result.success()

        runCatching {
            val uid = AuthRepository().currentUid() ?: return@runCatching Result.success()
            val db = CarDatabase.getInstance(applicationContext)
            val now = System.currentTimeMillis()
            val stateStore = NotificationStateStore(applicationContext)

            val activeVehicles = db.vehicleDao().getAll(uid)
                .filter { it.status == VehicleStatus.ACTIVE || it.status == VehicleStatus.SECONDARY }

            val maintenanceAlerts = buildList {
                for (vehicle in activeVehicles) {
                    val plans = db.maintenanceDao().getAllPlans(vehicle.vehicleId)
                    addAll(MaintenanceNotificationPolicy.alertsFor(vehicle, plans, now))
                }
            }
                .filter { stateStore.shouldNotify(it.key, it.fingerprint, it.repeatAfterMs, now) }

            // Keep the manual-reminder feature that already existed in CarManager. It now benefits
            // from stable ids and de-duplication too, so maintenance work does not regress it.
            val vehiclesById = activeVehicles.associateBy { it.vehicleId }
            val manualAlerts = db.supportDao().getAllOpenReminders()
                .mapNotNull { reminder -> manualAlert(reminder, vehiclesById[reminder.vehicleId], now) }
                .filter { stateStore.shouldNotify(it.key, it.fingerprint, it.repeatAfterMs, now) }

            val combined = (maintenanceAlerts.map(::toPayload) + manualAlerts)
                .sortedWith(
                    compareByDescending<NotificationPayload> { it.urgent }
                        .thenBy { it.title }
                        .thenBy { it.key }
                )
                .take(MAX_ALERTS_PER_RUN)

            if (combined.isNotEmpty()) {
                createChannels()
                combined.forEach { payload ->
                    postNotification(payload)
                    stateStore.markNotified(payload.key, payload.fingerprint, now)
                }
                if (combined.size > 1) postSummary(combined)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun toPayload(alert: MaintenanceAlert): NotificationPayload = NotificationPayload(
        key = alert.key,
        title = alert.title,
        body = alert.body,
        fingerprint = alert.fingerprint,
        repeatAfterMs = alert.repeatAfterMs,
        urgent = alert.level == MaintenanceAlertLevel.OVERDUE
    )

    private fun manualAlert(reminder: ReminderEntity, vehicle: VehicleEntity?, now: Long): NotificationPayload? {
        vehicle ?: return null
        if (reminder.isDeleted || reminder.isCompleted) return null

        val warningDays = (reminder.warningBeforeDays ?: 0).coerceAtLeast(0)
        val warningKm = (reminder.warningBeforeKm ?: 0.0).coerceAtLeast(0.0)
        val dateDue = reminder.dueDate?.let { now >= it - warningDays * DAY_MS } ?: false
        val kmDue = reminder.dueOdometerKm?.let { vehicle.currentOdometerKm >= it - warningKm } ?: false
        val triggered = when (reminder.rule) {
            ReminderRule.DATE_ONLY -> dateDue
            ReminderRule.ODOMETER_ONLY -> kmDue
            ReminderRule.WHICHEVER_COMES_FIRST -> dateDue || kmDue
        }
        if (!triggered) return null

        val dateOverdue = reminder.dueDate?.let { now >= it } ?: false
        val kmOverdue = reminder.dueOdometerKm?.let { vehicle.currentOdometerKm >= it } ?: false
        val overdue = when (reminder.rule) {
            ReminderRule.DATE_ONLY -> dateOverdue
            ReminderRule.ODOMETER_ONLY -> kmOverdue
            ReminderRule.WHICHEVER_COMES_FIRST -> dateOverdue || kmOverdue
        }
        val vehicleName = vehicle.displayName?.takeIf { it.isNotBlank() }
            ?: listOf(vehicle.brand, vehicle.model).filter { it.isNotBlank() }.joinToString(" ")
        val detail = if (overdue) {
            "مستحق الآن"
        } else {
            buildList {
                reminder.dueOdometerKm?.let { dueKm ->
                    val remaining = dueKm - vehicle.currentOdometerKm
                    if (remaining >= 0.0) add("متبقي ${remaining.toLong()} كم")
                }
                reminder.dueDate?.let { dueDate ->
                    val days = ceil((dueDate - now).coerceAtLeast(0L).toDouble() / DAY_MS.toDouble()).toLong()
                    add(if (days == 0L) "موعده اليوم" else "متبقي $days يوم")
                }
            }.joinToString(" أو ")
        }

        return NotificationPayload(
            key = "manual-reminder:${reminder.vehicleId}:${reminder.id}",
            title = "تذكير — $vehicleName",
            body = listOf(reminder.titleAr, detail).filter { it.isNotBlank() }.joinToString(" • "),
            fingerprint = "${if (overdue) "overdue" else "due"}:${reminder.updatedAt}:${reminder.warningBeforeKm}:${reminder.warningBeforeDays}",
            repeatAfterMs = if (overdue) DAY_MS else 7L * DAY_MS,
            urgent = overdue && reminder.priority >= 2
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    OVERDUE_CHANNEL_ID,
                    "الصيانة المستحقة",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تنبيهات الصيانة التي وصلت إلى موعدها أو تجاوزته"
                    enableVibration(true)
                },
                NotificationChannel(
                    UPCOMING_CHANNEL_ID,
                    "مواعيد الصيانة القادمة",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "تنبيهات قبل موعد الصيانة حسب الكيلومترات أو التاريخ الذي تختاره"
                }
            )
        )
    }

    private fun postNotification(payload: NotificationPayload) {
        // Keep the permission check immediately next to notify(). Besides guarding against a user
        // revoking permission between worker start and delivery, this makes the contract explicit to
        // Android Lint instead of relying on inter-procedural inference from doWork().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = NotificationManagerCompat.from(applicationContext)
        val channelId = if (payload.urgent) OVERDUE_CHANNEL_ID else UPCOMING_CHANNEL_ID
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId(payload.key),
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ALERT_KEY, payload.key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_car)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(NOTIFICATION_GROUP)
            .setCategory(if (payload.urgent) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (payload.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(notificationId(payload.key), notification)
    }

    private fun postSummary(alerts: List<NotificationPayload>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val urgentCount = alerts.count { it.urgent }
        val channelId = if (urgentCount > 0) OVERDUE_CHANNEL_ID else UPCOMING_CHANNEL_ID
        val summaryText = if (urgentCount > 0) {
            "لديك ${alerts.size} تنبيهات، منها $urgentCount صيانة مستحقة"
        } else {
            "لديك ${alerts.size} مواعيد صيانة أو تذكيرات قريبة"
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            SUMMARY_NOTIFICATION_ID,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_car)
            .setContentTitle("CarManager — تنبيهات الصيانة")
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setPriority(if (urgentCount > 0) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    private fun notificationId(key: String): Int = NOTIFICATION_ID_BASE + ((key.hashCode() and 0x7fffffff) % 100_000)

    private data class NotificationPayload(
        val key: String,
        val title: String,
        val body: String,
        val fingerprint: String,
        val repeatAfterMs: Long,
        val urgent: Boolean
    )

    private class NotificationStateStore(context: Context) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun shouldNotify(key: String, fingerprint: String, repeatAfterMs: Long, now: Long): Boolean {
            val previousFingerprint = prefs.getString("$key:fingerprint", null)
            val previousAt = prefs.getLong("$key:at", 0L)
            return previousFingerprint != fingerprint || previousAt <= 0L || now - previousAt >= repeatAfterMs
        }

        fun markNotified(key: String, fingerprint: String, now: Long) {
            prefs.edit()
                .putString("$key:fingerprint", fingerprint)
                .putLong("$key:at", now)
                .apply()
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_ALERTS_PER_RUN = 8
        const val OVERDUE_CHANNEL_ID = "car_manager_maintenance_overdue_v1"
        const val UPCOMING_CHANNEL_ID = "car_manager_maintenance_upcoming_v1"
        const val NOTIFICATION_GROUP = "car_manager_maintenance"
        const val SUMMARY_NOTIFICATION_ID = 4_099
        const val NOTIFICATION_ID_BASE = 4_200
        const val EXTRA_ALERT_KEY = "car_manager_alert_key"
        const val PREFS_NAME = "car_manager_maintenance_notification_state_v1"
    }
}

object ReminderScheduler {
    // Keep the historical unique work name so an upgrade replaces the old 12-hour schedule instead
    // of leaving two periodic workers active.
    private const val PERIODIC_WORK_NAME = "car-manager-reminder-check"
    private const val IMMEDIATE_WORK_NAME = "car-manager-reminder-check-immediate-v1"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val periodic = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )

        // Evaluate once shortly after startup as well. De-duplication prevents an app-open loop from
        // repeatedly notifying about the same unchanged maintenance item.
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build()
        )
    }
}
