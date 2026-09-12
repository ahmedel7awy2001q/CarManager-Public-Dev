package com.ahmed.carmanager.notifications

import android.content.Context
import androidx.work.*
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.gps.GpsCredentialStore
import com.ahmed.carmanager.data.gps.GpsSyncManager
import com.ahmed.carmanager.data.gps.GpsSyncResult
import com.ahmed.carmanager.data.local.CarDatabase
import java.util.concurrent.TimeUnit

class GpsSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val auth = AuthRepository()
        if (auth.currentUid() == null) return Result.success()
        val manager = GpsSyncManager(
            CarDatabase.getInstance(applicationContext),
            GpsCredentialStore(applicationContext),
            auth
        )
        return when (manager.syncAll()) {
            is GpsSyncResult.Success -> Result.success()
            is GpsSyncResult.Error -> Result.retry()
        }
    }
}

object GpsSyncScheduler {
    private const val WORK_NAME = "car-manager-gps-sync"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<GpsSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
