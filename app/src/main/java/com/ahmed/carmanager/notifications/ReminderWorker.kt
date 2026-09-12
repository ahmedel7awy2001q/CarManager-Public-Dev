package com.ahmed.carmanager.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.ahmed.carmanager.R
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.VehicleStatus
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenanceUrgency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val uid = AuthRepository().currentUid() ?: return@runCatching Result.success()
            val db = CarDatabase.getInstance(applicationContext)
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60L * 60L * 1000L

            val manualReminderTitles = db.supportDao().getAllOpenReminders().mapNotNull { reminder ->
                val vehicle = db.vehicleDao().getById(reminder.vehicleId, uid) ?: return@mapNotNull null
                if (vehicle.status != VehicleStatus.ACTIVE && vehicle.status != VehicleStatus.SECONDARY) return@mapNotNull null
                val dateDue = reminder.dueDate?.let { dueDate ->
                    val warning = (reminder.warningBeforeDays ?: 0).coerceAtLeast(0)
                    now >= dueDate - warning * dayMs
                } ?: false
                val kmDue = reminder.dueOdometerKm?.let { dueKm ->
                    val warning = (reminder.warningBeforeKm ?: 0.0).coerceAtLeast(0.0)
                    vehicle.currentOdometerKm >= dueKm - warning
                } ?: false
                val due = when (reminder.rule) {
                    com.ahmed.carmanager.data.local.model.ReminderRule.DATE_ONLY -> dateDue
                    com.ahmed.carmanager.data.local.model.ReminderRule.ODOMETER_ONLY -> kmDue
                    com.ahmed.carmanager.data.local.model.ReminderRule.WHICHEVER_COMES_FIRST -> dateDue || kmDue
                }
                reminder.titleAr.takeIf { due }
            }

            val maintenanceTitles = mutableListOf<String>()
            val activeVehicles = db.vehicleDao().getAll(uid)
                .filter { it.status == VehicleStatus.ACTIVE || it.status == VehicleStatus.SECONDARY }
            for (vehicle in activeVehicles) {
                val vehicleName = vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}"
                val plans = db.maintenanceDao().getAllPlans(vehicle.vehicleId)
                for (plan in plans) {
                    if (!plan.isActive || plan.isDeleted) continue
                    val status = MaintenanceAdvisor.statusFor(vehicle, plan, now)
                    when (status.urgency) {
                        MaintenanceUrgency.OVERDUE -> maintenanceTitles += "$vehicleName: ${plan.titleAr} — مستحقة الآن"
                        MaintenanceUrgency.DUE_SOON -> maintenanceTitles += "$vehicleName: ${plan.titleAr} — موعدها يقترب"
                        MaintenanceUrgency.UPCOMING -> Unit
                    }
                }
            }

            val titles = (maintenanceTitles + manualReminderTitles).distinct().take(5)
            if (titles.isNotEmpty()) showNotifications(titles)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun showNotifications(titles: List<String>) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "car_manager_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "تنبيهات السيارة", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "تنبيهات الصيانة والمستندات والعداد"
                }
            )
        }
        titles.forEachIndexed { index, title ->
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_launcher_car)
                .setContentTitle("إدارة السيارات")
                .setContentText(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(title))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(4100 + index, notification)
        }
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "car-manager-reminder-check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}