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
import com.ahmed.carmanager.data.local.model.VehicleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background bridge between CarManager's local attention data and Android notifications.
 *
 * The worker reads only local application data. Delivery state is rate-limited so a periodic check
 * never becomes notification spam. A meaningful state change is delivered on the next check.
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
            val supportDao = db.supportDao()
            val now = System.currentTimeMillis()
            val remindersByVehicle = supportDao.getAllOpenReminders().groupBy { it.vehicleId }
            val activeVehicles = db.vehicleDao().getAll(uid)
                .filter { it.status == VehicleStatus.ACTIVE || it.status == VehicleStatus.SECONDARY }

            val alerts = buildList {
                for (vehicle in activeVehicles) {
                    val plans = db.maintenanceDao().getAllPlans(vehicle.vehicleId)
                    val faults = supportDao.observeFaults(vehicle.vehicleId).first()
                    val documents = supportDao.observeDocuments(vehicle.vehicleId).first()
                    addAll(
                        AttentionNotificationPolicy.forVehicle(
                            vehicle = vehicle,
                            plans = plans,
                            faults = faults,
                            documents = documents,
                            reminders = remindersByVehicle[vehicle.vehicleId].orEmpty(),
                            now = now
                        )
                    )
                }
            }

            val stateStore = NotificationStateStore(applicationContext)
            val toPost = alerts.filter { stateStore.shouldNotify(it, now) }.take(MAX_ALERTS_PER_RUN)
            if (toPost.isNotEmpty()) {
                createChannels()
                postNotifications(toPost, stateStore, now)
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

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val urgent = NotificationChannel(
            URGENT_CHANNEL_ID,
            "تنبيهات السيارة العاجلة",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "الصيانة المتأخرة والأعطال الحرجة والمستندات شديدة الأهمية"
            enableVibration(true)
        }
        val important = NotificationChannel(
            IMPORTANT_CHANNEL_ID,
            "تنبيهات السيارة المهمة",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "الصيانة القريبة والأعطال المهمة والرخص والمستندات التي تقترب من الانتهاء"
        }
        val reminder = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "تذكيرات السيارة",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "التذكيرات المبكرة والتنبيهات العادية"
        }
        manager.createNotificationChannels(listOf(urgent, important, reminder))
    }

    private fun postNotifications(
        alerts: List<AppAlert>,
        stateStore: NotificationStateStore,
        now: Long
    ) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alerts.forEach { alert ->
            val channel = channelFor(alert.level)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                notificationId(alert.key),
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_ALERT_KEY, alert.key)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, channel)
                .setSmallIcon(R.drawable.ic_launcher_car)
                .setContentTitle(alert.title)
                .setContentText(alert.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setGroup(NOTIFICATION_GROUP)
                .setCategory(
                    if (alert.level == AppAlertLevel.URGENT) NotificationCompat.CATEGORY_ERROR
                    else NotificationCompat.CATEGORY_REMINDER
                )
                .setPriority(
                    when (alert.level) {
                        AppAlertLevel.URGENT -> NotificationCompat.PRIORITY_HIGH
                        AppAlertLevel.IMPORTANT -> NotificationCompat.PRIORITY_DEFAULT
                        AppAlertLevel.REMINDER -> NotificationCompat.PRIORITY_LOW
                    }
                )
                .build()
            manager.notify(notificationId(alert.key), notification)
            stateStore.markNotified(alert, now)
        }

        if (alerts.size > 1) {
            val urgentCount = alerts.count { it.level == AppAlertLevel.URGENT }
            val importantCount = alerts.count { it.level == AppAlertLevel.IMPORTANT }
            val summaryText = when {
                urgentCount > 0 -> "لديك ${alerts.size} تنبيهات، منها $urgentCount عاجل"
                importantCount > 0 -> "لديك ${alerts.size} تنبيهات، منها $importantCount مهم"
                else -> "لديك ${alerts.size} تذكيرات تحتاج مراجعة"
            }
            val summaryLevel = when {
                urgentCount > 0 -> AppAlertLevel.URGENT
                importantCount > 0 -> AppAlertLevel.IMPORTANT
                else -> AppAlertLevel.REMINDER
            }
            val summaryIntent = PendingIntent.getActivity(
                applicationContext,
                SUMMARY_NOTIFICATION_ID,
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.notify(
                SUMMARY_NOTIFICATION_ID,
                NotificationCompat.Builder(applicationContext, channelFor(summaryLevel))
                    .setSmallIcon(R.drawable.ic_launcher_car)
                    .setContentTitle("CarManager — مركز الانتباه")
                    .setContentText(summaryText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
                    .setContentIntent(summaryIntent)
                    .setAutoCancel(true)
                    .setGroup(NOTIFICATION_GROUP)
                    .setGroupSummary(true)
                    .setPriority(
                        when (summaryLevel) {
                            AppAlertLevel.URGENT -> NotificationCompat.PRIORITY_HIGH
                            AppAlertLevel.IMPORTANT -> NotificationCompat.PRIORITY_DEFAULT
                            AppAlertLevel.REMINDER -> NotificationCompat.PRIORITY_LOW
                        }
                    )
                    .build()
            )
        }
    }

    private fun channelFor(level: AppAlertLevel): String = when (level) {
        AppAlertLevel.URGENT -> URGENT_CHANNEL_ID
        AppAlertLevel.IMPORTANT -> IMPORTANT_CHANNEL_ID
        AppAlertLevel.REMINDER -> REMINDER_CHANNEL_ID
    }

    private fun notificationId(key: String): Int = 4_200 + ((key.hashCode() and 0x7fffffff) % 100_000)

    private class NotificationStateStore(context: Context) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun shouldNotify(alert: AppAlert, now: Long): Boolean {
            val lastFingerprint = prefs.getString("${alert.key}:fingerprint", null)
            val lastAt = prefs.getLong("${alert.key}:at", 0L)
            return lastFingerprint != alert.fingerprint || lastAt <= 0L || now - lastAt >= alert.repeatAfterMs
        }

        fun markNotified(alert: AppAlert, now: Long) {
            prefs.edit()
                .putString("${alert.key}:fingerprint", alert.fingerprint)
                .putLong("${alert.key}:at", now)
                .apply()
        }
    }

    private companion object {
        const val MAX_ALERTS_PER_RUN = 8
        const val URGENT_CHANNEL_ID = "car_manager_urgent_v3"
        const val IMPORTANT_CHANNEL_ID = "car_manager_important_v3"
        const val REMINDER_CHANNEL_ID = "car_manager_reminders_v3"
        const val NOTIFICATION_GROUP = "car_manager_attention"
        const val SUMMARY_NOTIFICATION_ID = 4_099
        const val EXTRA_ALERT_KEY = "car_manager_alert_key"
        const val PREFS_NAME = "car_manager_notification_state_v3"
    }
}

object ReminderScheduler {
    // Keep the original periodic work name so an upgrade replaces the old 12-hour schedule instead
    // of leaving two periodic workers side by side.
    private const val PERIODIC_WORK_NAME = "car-manager-reminder-check"
    private const val IMMEDIATE_WORK_NAME = "car-manager-reminder-check-immediate-v3"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val periodic = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )

        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReminderWorker>().build()
        )
    }
}
