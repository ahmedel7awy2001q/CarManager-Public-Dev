package com.ahmed.carmanager.data.gps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HeadUnitHeartbeatController(
    private val identityStore: TrackerDeviceIdentityStore,
    private val contextStore: VehicleContextStore,
    private val presenceManager: LiveTrackerPresenceManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job = scope.launch {
            var previousVehicleId: String? = null

            combine(identityStore.roleFlow, contextStore.headUnitVehicleIdFlow) { role, boundVehicleId ->
                boundVehicleId?.takeIf { role == TrackerDeviceRole.HEAD_UNIT && it.isNotBlank() }
            }
                .distinctUntilChanged()
                .flatMapLatest { vehicleId ->
                    if (vehicleId == null) {
                        flowOf<String?>(null)
                    } else {
                        flow {
                            while (currentCoroutineContext().isActive) {
                                emit(vehicleId)
                                delay(HEARTBEAT_MS)
                            }
                        }
                    }
                }
                .collect { vehicleId ->
                    if (previousVehicleId != null && previousVehicleId != vehicleId) {
                        presenceManager.markOffline(previousVehicleId!!)
                    }
                    if (!vehicleId.isNullOrBlank()) {
                        presenceManager.heartbeat(vehicleId)
                    }
                    previousVehicleId = vehicleId
                }
        }
    }

    private companion object {
        const val HEARTBEAT_MS = 20_000L
    }
}
