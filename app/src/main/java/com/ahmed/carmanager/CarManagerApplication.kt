package com.ahmed.carmanager

import android.app.Application
import android.content.Context
import com.ahmed.carmanager.data.auth.AuthRepository
import com.ahmed.carmanager.data.catalog.VehicleCatalogUpdateManager
import com.ahmed.carmanager.diagnostics.AppCrashDiagnostics
import com.ahmed.carmanager.data.backup.DatabaseBackupManager
import com.ahmed.carmanager.data.cloud.CloudAccountBackupManager
import com.ahmed.carmanager.data.gps.GpsCredentialStore
import com.ahmed.carmanager.data.gps.GpsSyncManager
import com.ahmed.carmanager.data.gps.ITrackVehicleSignalManager
import com.ahmed.carmanager.data.gps.TrackerDeviceIdentityStore
import com.ahmed.carmanager.data.gps.TrackerDeviceRegistry
import com.ahmed.carmanager.data.gps.LiveTrackerPresenceManager
import com.ahmed.carmanager.data.gps.HeadUnitHeartbeatController
import com.ahmed.carmanager.data.gps.VehicleContextStore
import com.ahmed.carmanager.data.gps.VehicleContextResolver
import com.ahmed.carmanager.data.gps.VehicleContextEngine
import com.ahmed.carmanager.data.gps.TrustedBluetoothManager
import com.ahmed.carmanager.data.gps.TrustedBluetoothConnectionMonitor
import com.ahmed.carmanager.data.gps.AndroidAutoConnectionMonitor
import com.ahmed.carmanager.data.gps.ActiveTrackerLeaseManager
import com.ahmed.carmanager.data.gps.TrackerLeaseSessionController
import com.ahmed.carmanager.data.gps.LiveTripManager
import com.ahmed.carmanager.data.gps.LiveTripTrackingController
import com.ahmed.carmanager.data.gps.AutoTripTrackingCoordinator
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.repository.*
import com.ahmed.carmanager.data.settings.AppearancePreferences
import com.ahmed.carmanager.notifications.GpsSyncScheduler
import com.ahmed.carmanager.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: CarDatabase by lazy { CarDatabase.getInstance(appContext) }
    val authRepository: AuthRepository by lazy { AuthRepository() }
    val vehicleRepository: VehicleRepository by lazy { RoomVehicleRepository(database, authRepository) }
    val operationsRepository: CarOperationsRepository by lazy {
        GasolineCarOperationsRepository(
            database = database,
            authRepository = authRepository,
            delegate = RoomCarOperationsRepository(database, authRepository)
        )
    }
    val faultStatusStore: FaultStatusStore by lazy { FaultStatusStore(database, authRepository) }
    val attachmentRepository: AttachmentRepository by lazy { AttachmentRepository(database, authRepository) }
    val cloudBackupManager: CloudAccountBackupManager by lazy { CloudAccountBackupManager(database, authRepository) }
    val gpsCredentialStore: GpsCredentialStore by lazy { GpsCredentialStore(appContext) }
    val trackerDeviceIdentityStore: TrackerDeviceIdentityStore by lazy { TrackerDeviceIdentityStore(appContext) }
    val trackerDeviceRegistry: TrackerDeviceRegistry by lazy { TrackerDeviceRegistry(database, authRepository, trackerDeviceIdentityStore, vehicleContextStore) }
    val liveTrackerPresenceManager: LiveTrackerPresenceManager by lazy { LiveTrackerPresenceManager(authRepository, trackerDeviceIdentityStore, trackerDeviceRegistry) }
    val activeTrackerLeaseManager: ActiveTrackerLeaseManager by lazy { ActiveTrackerLeaseManager(authRepository, trackerDeviceIdentityStore, trackerDeviceRegistry) }
    val trackerLeaseSessionController: TrackerLeaseSessionController by lazy { TrackerLeaseSessionController(activeTrackerLeaseManager) }
    val liveTripManager: LiveTripManager by lazy { LiveTripManager(database, trackerDeviceRegistry, trackerLeaseSessionController) }
    val liveTripTrackingController: LiveTripTrackingController by lazy { LiveTripTrackingController(appContext, trackerDeviceIdentityStore) }
    val autoTripTrackingCoordinator: AutoTripTrackingCoordinator by lazy { AutoTripTrackingCoordinator(database, vehicleRepository, trackerDeviceIdentityStore, vehicleContextStore, trustedBluetoothConnectionMonitor, androidAutoConnectionMonitor, vehicleContextEngine, liveTripTrackingController) }
    val headUnitHeartbeatController: HeadUnitHeartbeatController by lazy { HeadUnitHeartbeatController(trackerDeviceIdentityStore, vehicleContextStore, liveTrackerPresenceManager) }
    val vehicleContextStore: VehicleContextStore by lazy { VehicleContextStore(appContext) }
    val vehicleContextResolver: VehicleContextResolver by lazy { VehicleContextResolver() }
    val vehicleContextEngine: VehicleContextEngine by lazy { VehicleContextEngine(trackerDeviceIdentityStore, vehicleContextStore, vehicleContextResolver) }
    val trustedBluetoothManager: TrustedBluetoothManager by lazy { TrustedBluetoothManager(appContext, vehicleContextStore) }
    val trustedBluetoothConnectionMonitor: TrustedBluetoothConnectionMonitor by lazy { TrustedBluetoothConnectionMonitor(appContext, vehicleContextStore) }
    val androidAutoConnectionMonitor: AndroidAutoConnectionMonitor by lazy { AndroidAutoConnectionMonitor(appContext) }
    val iTrackVehicleSignalManager: ITrackVehicleSignalManager by lazy { ITrackVehicleSignalManager(database) }
    val gpsSyncManager: GpsSyncManager by lazy { GpsSyncManager(database, gpsCredentialStore, authRepository) }
    val backupManager: DatabaseBackupManager by lazy { DatabaseBackupManager(database, authRepository) }
    val appearancePreferences: AppearancePreferences by lazy { AppearancePreferences(appContext) }
    internal val vehicleCatalogUpdateManager: VehicleCatalogUpdateManager by lazy { VehicleCatalogUpdateManager(appContext) }
}

class CarManagerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    // Process-lifetime background startup scope. None of these tasks is required to draw the first
    // screen, so they must not compete with the UI thread during cold start.
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppCrashDiagnostics.install(this)

        startupScope.launch {
            // Give the first frame and account/vehicle UI a short head start. WorkManager scheduling,
            // catalog disk/network work and tracker graph initialization are all process services,
            // not prerequisites for rendering the first screen.
            delay(750L)

            runCatching { ReminderScheduler.schedule(this@CarManagerApplication) }
            runCatching { GpsSyncScheduler.schedule(this@CarManagerApplication) }
            runCatching { container.vehicleCatalogUpdateManager.start() }
            runCatching { container.headUnitHeartbeatController.start() }
            runCatching { container.autoTripTrackingCoordinator.start() }
        }
    }
}
