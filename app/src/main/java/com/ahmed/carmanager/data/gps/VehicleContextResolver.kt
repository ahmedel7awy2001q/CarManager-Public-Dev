package com.ahmed.carmanager.data.gps

enum class VehicleContextState {
    MANUAL,
    CONFIRMED,
    AMBIGUOUS,
    NO_MATCH,
    BLOCKED
}

enum class VehicleContextSignal {
    MANUAL_SELECTION,
    BOUND_HEAD_UNIT,
    ANDROID_AUTO,
    TRUSTED_BLUETOOTH,
    HEAD_UNIT_ONLINE,
    ITRACK_VEHICLE,
    DEVICE_MOTION
}

data class VehicleContextCandidate(
    val vehicleId: String,
    val androidAutoConnected: Boolean = false,
    val trustedBluetoothConnected: Boolean = false,
    val headUnitOnline: Boolean = false,
    val itrackVehicleConfirmed: Boolean = false,
    val deviceMotionDetected: Boolean = false
)

data class VehicleContextDecision(
    val state: VehicleContextState,
    val vehicleId: String? = null,
    val signals: Set<VehicleContextSignal> = emptySet(),
    val startAllowed: Boolean = false,
    val autoStartAllowed: Boolean = false
)

class VehicleContextResolver {

    fun resolve(
        role: TrackerDeviceRole,
        boundHeadUnitVehicleId: String?,
        manualVehicleId: String?,
        candidates: List<VehicleContextCandidate>
    ): VehicleContextDecision {

        // A head unit is permanently tied to its bound vehicle.
        // Manual selection must never override that binding.
        if (role == TrackerDeviceRole.HEAD_UNIT) {
            return resolveHeadUnit(
                boundHeadUnitVehicleId,
                candidates
            )
        }

        manualVehicleId
            ?.takeIf { it.isNotBlank() }
            ?.let { selected ->
                return VehicleContextDecision(
                    state = VehicleContextState.MANUAL,
                    vehicleId = selected,
                    signals = setOf(
                        VehicleContextSignal.MANUAL_SELECTION
                    ),
                    startAllowed = true,
                    autoStartAllowed = false
                )
            }

        return resolvePhone(candidates)
    }

    private fun resolveHeadUnit(
        boundVehicleId: String?,
        candidates: List<VehicleContextCandidate>
    ): VehicleContextDecision {

        if (boundVehicleId.isNullOrBlank()) {
            return VehicleContextDecision(
                state = VehicleContextState.BLOCKED
            )
        }

        val candidate = candidates.firstOrNull {
            it.vehicleId == boundVehicleId
        }

        val signals = buildSet {
            add(VehicleContextSignal.BOUND_HEAD_UNIT)

            if (candidate?.deviceMotionDetected == true) {
                add(VehicleContextSignal.DEVICE_MOTION)
            }
        }

        return VehicleContextDecision(
            state = VehicleContextState.CONFIRMED,
            vehicleId = boundVehicleId,
            signals = signals,
            startAllowed = true,

            // The head unit is permanently bound to one vehicle,
            // but automatic start still requires real movement.
            autoStartAllowed =
                candidate?.deviceMotionDetected == true
        )
    }

    private fun resolvePhone(
        candidates: List<VehicleContextCandidate>
    ): VehicleContextDecision {

        val qualified = candidates.mapNotNull { candidate ->
            val strongSignals = buildSet {
                if (candidate.androidAutoConnected) {
                    add(VehicleContextSignal.ANDROID_AUTO)
                }

                if (candidate.trustedBluetoothConnected) {
                    add(VehicleContextSignal.TRUSTED_BLUETOOTH)
                }
            }

            if (strongSignals.isEmpty()) {
                null
            } else {
                val allSignals = buildSet {
                    addAll(strongSignals)

                    if (candidate.headUnitOnline) {
                        add(VehicleContextSignal.HEAD_UNIT_ONLINE)
                    }

                    if (candidate.itrackVehicleConfirmed) {
                        add(VehicleContextSignal.ITRACK_VEHICLE)
                    }

                    if (candidate.deviceMotionDetected) {
                        add(VehicleContextSignal.DEVICE_MOTION)
                    }
                }

                candidate to allSignals
            }
        }

        if (qualified.isEmpty()) {
            return VehicleContextDecision(
                state = VehicleContextState.NO_MATCH,

                // Motion by itself is intentionally ignored.
                // The user may be walking, riding a motorcycle,
                // using public transport, or riding in another car.
                startAllowed = false,
                autoStartAllowed = false
            )
        }

        if (qualified.size > 1) {
            return VehicleContextDecision(
                state = VehicleContextState.AMBIGUOUS,
                startAllowed = false,
                autoStartAllowed = false
            )
        }

        val (candidate, signals) = qualified.single()

        return VehicleContextDecision(
            state = VehicleContextState.CONFIRMED,
            vehicleId = candidate.vehicleId,
            signals = signals,
            startAllowed = true,
            // A unique trusted car-specific connection is sufficient
            // for phone auto tracking. Motion never identifies the vehicle.
            autoStartAllowed = true
        )
    }
}



