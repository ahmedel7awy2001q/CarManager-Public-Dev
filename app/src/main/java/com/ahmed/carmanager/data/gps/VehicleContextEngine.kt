package com.ahmed.carmanager.data.gps

class VehicleContextEngine(
    private val identityStore: TrackerDeviceIdentityStore,
    private val contextStore: VehicleContextStore,
    private val resolver: VehicleContextResolver
) {

    fun resolve(
        manualVehicleId: String? = null,
        candidates: List<VehicleContextCandidate>
    ): VehicleContextDecision {
        val identity = identityStore.identity()

        return resolver.resolve(
            role = identity.role,
            boundHeadUnitVehicleId =
                contextStore.boundHeadUnitVehicleId(),
            manualVehicleId = manualVehicleId,
            candidates = candidates
        )
    }

    fun candidate(
        vehicleId: String,
        trustedBluetoothConnected: Boolean = false,
        androidAutoConnected: Boolean = false,
        headUnitOnline: Boolean = false,
        itrackVehicleConfirmed: Boolean = false,
        deviceMotionDetected: Boolean = false
    ): VehicleContextCandidate {
        require(vehicleId.isNotBlank()) {
            "Invalid vehicle id."
        }

        return VehicleContextCandidate(
            vehicleId = vehicleId,
            androidAutoConnected = androidAutoConnected,
            trustedBluetoothConnected =
                trustedBluetoothConnected,
            headUnitOnline = headUnitOnline,
            itrackVehicleConfirmed =
                itrackVehicleConfirmed,
            deviceMotionDetected =
                deviceMotionDetected
        )
    }
}
