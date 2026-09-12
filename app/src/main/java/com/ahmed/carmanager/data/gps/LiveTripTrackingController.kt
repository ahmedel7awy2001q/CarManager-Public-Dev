package com.ahmed.carmanager.data.gps

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class LiveTripTrackingController(
    context: Context,
    private val identityStore: TrackerDeviceIdentityStore
) {
    private val appContext = context.applicationContext

    fun startManual(vehicleId: String) {
        require(vehicleId.isNotBlank())

        val authorization =
            when (identityStore.role()) {
                TrackerDeviceRole.PHONE ->
                    TrackerLeaseAuthorization.MANUAL

                TrackerDeviceRole.HEAD_UNIT ->
                    TrackerLeaseAuthorization.HEAD_UNIT_BINDING
            }

        val intent =
            Intent(
                appContext,
                LiveTripTrackingService::class.java
            )
                .setAction(
                    LiveTripTrackingService.ACTION_START
                )
                .putExtra(
                    LiveTripTrackingService.EXTRA_VEHICLE_ID,
                    vehicleId
                )
                .putExtra(
                    LiveTripTrackingService.EXTRA_AUTHORIZATION,
                    authorization.name
                )
                .putExtra(
                    LiveTripTrackingService.EXTRA_AUTOMATIC,
                    false
                )

        ContextCompat.startForegroundService(
            appContext,
            intent
        )
    }

    fun startAutomatic(
        vehicleId: String,
        authorization: TrackerLeaseAuthorization
    ): Boolean {
        require(vehicleId.isNotBlank())
        require(identityStore.role() == TrackerDeviceRole.PHONE)
        require(
            authorization == TrackerLeaseAuthorization.TRUSTED_BLUETOOTH ||
                authorization == TrackerLeaseAuthorization.ANDROID_AUTO
        )

        if (!isAppForeground()) return false

        val intent =
            Intent(
                appContext,
                LiveTripTrackingService::class.java
            )
                .setAction(
                    LiveTripTrackingService.ACTION_START
                )
                .putExtra(
                    LiveTripTrackingService.EXTRA_VEHICLE_ID,
                    vehicleId
                )
                .putExtra(
                    LiveTripTrackingService.EXTRA_AUTHORIZATION,
                    authorization.name
                )
                .putExtra(
                    LiveTripTrackingService.EXTRA_AUTOMATIC,
                    true
                )

        return runCatching {
            ContextCompat.startForegroundService(
                appContext,
                intent
            )
            true
        }.getOrDefault(false)
    }

    fun stopAutomatic(vehicleId: String) {
        require(vehicleId.isNotBlank())

        val intent =
            Intent(
                appContext,
                LiveTripTrackingService::class.java
            )
                .setAction(
                    LiveTripTrackingService.ACTION_STOP_AUTOMATIC
                )
                .putExtra(
                    LiveTripTrackingService.EXTRA_VEHICLE_ID,
                    vehicleId
                )

        runCatching {
            appContext.startService(intent)
        }
    }

    fun stop() {
        val intent =
            Intent(
                appContext,
                LiveTripTrackingService::class.java
            ).setAction(
                LiveTripTrackingService.ACTION_STOP
            )

        appContext.startService(intent)
    }

    private fun isAppForeground(): Boolean {
        val state =
            ActivityManager.RunningAppProcessInfo()

        ActivityManager.getMyMemoryState(state)

        return state.importance <=
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}
