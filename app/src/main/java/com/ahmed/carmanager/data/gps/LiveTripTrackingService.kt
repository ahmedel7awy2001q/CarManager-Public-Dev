package com.ahmed.carmanager.data.gps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ahmed.carmanager.R
import com.ahmed.carmanager.CarManagerApplication
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LiveTripTrackingService : Service() {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var fusedLocationClient:
        FusedLocationProviderClient

    private val pointFilter =
        LiveLocationPointFilter()

    private val pointMutex = Mutex()

    private var locationCallback:
        LocationCallback? = null

    private var trackingJob: Job? = null
    private var tripId: String? = null
    private var activeVehicleId: String? = null
    private var activeAutomatic = false

    private val container
        get() =
            (application as CarManagerApplication).container

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> stopTracking()

            ACTION_STOP_AUTOMATIC -> {
                val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
                if (activeAutomatic &&
                    (vehicleId.isNullOrBlank() || activeVehicleId == vehicleId)
                ) {
                    stopTracking()
                }
            }

            ACTION_START -> {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification()
                )

                startTracking(intent)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeLocationUpdates()
        trackingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun locationRequest(): LocationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L
        )
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(0f)
            .build()



    private fun startTracking(intent: Intent) {
        removeLocationUpdates()
        pointFilter.reset()

        if (!hasLocationPermission()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val vehicleId =
            intent.getStringExtra(EXTRA_VEHICLE_ID)
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }

        val authorization =
            intent.getStringExtra(EXTRA_AUTHORIZATION)
                ?.let {
                    runCatching {
                        TrackerLeaseAuthorization.valueOf(it)
                    }.getOrNull()
                }
                ?: run {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }

        val automatic =
            intent.getBooleanExtra(EXTRA_AUTOMATIC, false)

        trackingJob?.cancel()

        trackingJob = scope.launch {
            val started =
                container.liveTripManager.startOrRecover(
                    vehicleId = vehicleId,
                    authorization = authorization,
                    isAutomatic = automatic
                )

            val active =
                started.getOrNull()?.trip
                    ?: run {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }

            activeVehicleId = active.vehicleId
            activeAutomatic = active.isAutomatic
            tripId = active.id
            pointFilter.reset()

            beginLocationUpdates()
        }
    }

    @Suppress("MissingPermission")
    private fun beginLocationUpdates() {
        if (!hasLocationPermission()) return

        val callback = object : LocationCallback() {
            override fun onLocationResult(
                result: LocationResult
            ) {
                val activeTripId = tripId ?: return

                scope.launch {
                    pointMutex.withLock {
                        for (location in result.locations) {
                            val point =
                                pointFilter.evaluate(location)
                                    ?: continue

                            val saved =
                                container.liveTripManager.recordPoint(
                                    activeTripId,
                                    point
                                )

                            if (saved.isFailure) {
                                val lease =
                                    container
                                        .trackerLeaseSessionController
                                        .session.value

                                if (lease?.state ==
                                    TrackerLeaseSessionState.LOST
                                ) {
                                    stopTracking()
                                    return@withLock
                                }
                            }
                        }
                    }
                }
            }
        }

        locationCallback = callback

        fusedLocationClient.requestLocationUpdates(
            locationRequest(),
            callback,
            Looper.getMainLooper()
        )
    }

    private fun removeLocationUpdates() {
        val callback = locationCallback ?: return
        fusedLocationClient.removeLocationUpdates(callback)
        locationCallback = null
    }

    private fun stopTracking() {
        removeLocationUpdates()

        val activeTripId = tripId
        tripId = null
        activeVehicleId = null
        activeAutomatic = false

        scope.launch {
            if (activeTripId != null) {
                pointMutex.withLock {
                    container.liveTripManager.finish(activeTripId)
                }
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                getSystemService(NotificationManager::class.java)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "تتبع الرحلات",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "تتبع الرحلات النشطة باستخدام الموقع"
                setShowBadge(false)
            }

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent =
            Intent(this, LiveTripTrackingService::class.java)
                .setAction(ACTION_STOP)

        val stopPendingIntent = PendingIntent.getService(
            this,
            2402,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_car)
            .setContentTitle("CarManager")
            .setContentText("جار تتبع الرحلة والموقع")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_launcher_car,
                "إيقاف",
                stopPendingIntent
            )
            .build()
    }
    companion object {
        const val ACTION_START =
            "com.ahmed.carmanager.action.START_LIVE_TRIP"

        const val ACTION_STOP_AUTOMATIC =
            "com.ahmed.carmanager.action.STOP_AUTOMATIC_LIVE_TRIP"

        const val ACTION_STOP =
            "com.ahmed.carmanager.action.STOP_LIVE_TRIP"

        const val EXTRA_VEHICLE_ID = "vehicle_id"
        const val EXTRA_AUTHORIZATION = "authorization"
        const val EXTRA_AUTOMATIC = "automatic"

        private const val CHANNEL_ID =
            "car_manager_live_tracking"

        private const val NOTIFICATION_ID = 2401
    }
}






