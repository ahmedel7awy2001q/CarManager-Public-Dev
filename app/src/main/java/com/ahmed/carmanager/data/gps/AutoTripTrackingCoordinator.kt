package com.ahmed.carmanager.data.gps

import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.TripEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleStatus
import com.ahmed.carmanager.data.repository.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AutoTripTrackingCoordinator(
    private val database: CarDatabase,
    private val vehicleRepository: VehicleRepository,
    private val identityStore: TrackerDeviceIdentityStore,
    private val contextStore: VehicleContextStore,
    private val bluetoothMonitor: TrustedBluetoothConnectionMonitor,
    private val androidAutoMonitor: AndroidAutoConnectionMonitor,
    private val contextEngine: VehicleContextEngine,
    private val trackingController: LiveTripTrackingController
) {
    private val tripDao = database.tripDao()

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var job: Job? = null
    private var activeVehicleId: String? = null

    fun start() {
        if (job != null) return

        job = scope.launch {
            combine(
                vehicleRepository.observeVehicles(),
                androidAutoMonitor.observeAndroidAutoConnected(),
                contextStore.androidAutoVehicleIdFlow,
                identityStore.roleFlow
            ) { vehicles, androidAutoConnected, androidAutoVehicleId, role ->
                AutoInput(
                    vehicles = vehicles.filter {
                        it.status == VehicleStatus.ACTIVE ||
                            it.status == VehicleStatus.SECONDARY
                    },
                    androidAutoConnected = androidAutoConnected,
                    androidAutoVehicleId = androidAutoVehicleId,
                    role = role
                )
            }
                .flatMapLatest { input ->
                    if (
                        input.role != TrackerDeviceRole.PHONE ||
                        input.vehicles.isEmpty()
                    ) {
                        flowOf(AutoTarget())
                    } else {
                        combine(
                            input.vehicles.map { vehicle ->
                                combine(
                                    bluetoothMonitor.observe(vehicle.vehicleId),
                                    contextStore.observeAutoTrackingEnabled(
                                        vehicle.vehicleId
                                    ),
                                    tripDao.observeOpenTrip(vehicle.vehicleId)
                                ) { bluetoothConnected, enabled, openTrip ->
                                    AutoCandidate(
                                        vehicleId = vehicle.vehicleId,
                                        bluetoothConnected = bluetoothConnected,
                                        enabled = enabled,
                                        openTrip = openTrip
                                    )
                                }
                            }
                        ) { states ->
                            resolveTarget(input, states.toList())
                        }
                    }
                }
                .flatMapLatest { target -> retryTargetUntilApplied(target) }
                .collect { target ->
                    applyTarget(target)
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null

        activeVehicleId?.let { vehicleId ->
            runCatching {
                trackingController.stopAutomatic(vehicleId)
            }
        }
        activeVehicleId = null
    }

    private fun retryTargetUntilApplied(target: AutoTarget) = flow {
        emit(target)
        val targetVehicleId = target.vehicleId ?: return@flow
        while (activeVehicleId != targetVehicleId) {
            delay(RETRY_MS)
            if (activeVehicleId != targetVehicleId) emit(target)
        }
    }

    private fun resolveTarget(
        input: AutoInput,
        states: List<AutoCandidate>
    ): AutoTarget {
        if (
            states.any {
                it.openTrip != null &&
                    !it.openTrip.isAutomatic
            }
        ) {
            return AutoTarget()
        }

        val candidates =
            states
                .filter { it.enabled }
                .map { state ->
                    contextEngine.candidate(
                        vehicleId = state.vehicleId,
                        trustedBluetoothConnected =
                            state.bluetoothConnected,
                        androidAutoConnected =
                            input.androidAutoConnected &&
                                input.androidAutoVehicleId ==
                                state.vehicleId
                    )
                }

        val decision =
            contextEngine.resolve(candidates = candidates)

        val vehicleId =
            decision.vehicleId
                ?.takeIf { decision.autoStartAllowed }
                ?: return AutoTarget()

        val authorization =
            when {
                VehicleContextSignal.ANDROID_AUTO in
                    decision.signals ->
                    TrackerLeaseAuthorization.ANDROID_AUTO

                VehicleContextSignal.TRUSTED_BLUETOOTH in
                    decision.signals ->
                    TrackerLeaseAuthorization.TRUSTED_BLUETOOTH

                else -> return AutoTarget()
            }

        return AutoTarget(
            vehicleId = vehicleId,
            authorization = authorization
        )
    }

    private fun applyTarget(target: AutoTarget) {
        val previous = activeVehicleId

        if (
            previous != null &&
            previous != target.vehicleId
        ) {
            runCatching {
                trackingController.stopAutomatic(previous)
            }
            activeVehicleId = null
        }

        val vehicleId = target.vehicleId ?: return
        val authorization = target.authorization ?: return

        if (activeVehicleId == vehicleId) return

        val started =
            runCatching {
                trackingController.startAutomatic(
                    vehicleId = vehicleId,
                    authorization = authorization
                )
            }.getOrDefault(false)

        if (started) {
            activeVehicleId = vehicleId
        }
    }

    private data class AutoInput(
        val vehicles: List<VehicleEntity>,
        val androidAutoConnected: Boolean,
        val androidAutoVehicleId: String?,
        val role: TrackerDeviceRole
    )

    private data class AutoCandidate(
        val vehicleId: String,
        val bluetoothConnected: Boolean,
        val enabled: Boolean,
        val openTrip: TripEntity?
    )

    private data class AutoTarget(
        val vehicleId: String? = null,
        val authorization: TrackerLeaseAuthorization? = null
    )

    private companion object {
        const val RETRY_MS = 15_000L
    }
}
