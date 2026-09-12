package com.ahmed.carmanager.data.gps

import com.ahmed.carmanager.data.auth.AuthRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

data class TrackerPresenceSnapshot(
    val deviceIdentifier: String,
    val displayName: String,
    val role: TrackerDeviceRole,
    val isCurrentDevice: Boolean = false,
    val heartbeatEpochMs: Long = 0L,
    val heartbeatServerEpochMs: Long = 0L,
    val onlineHint: Boolean = false
) {
    fun isOnline(
        nowEpochMs: Long = System.currentTimeMillis(),
        staleAfterMs: Long = 60_000L
    ): Boolean {
        if (!onlineHint) return false

        val heartbeat = heartbeatServerEpochMs
            .takeIf { it > 0L }
            ?: heartbeatEpochMs

        if (heartbeat <= 0L) return false

        val age = nowEpochMs - heartbeat
        return age in 0L..staleAfterMs
    }
}

class LiveTrackerPresenceManager(
    private val authRepository: AuthRepository,
    private val identityStore: TrackerDeviceIdentityStore,
    private val registry: TrackerDeviceRegistry,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun heartbeat(
        vehicleId: String
    ): Result<Unit> = runCatching {
        require(vehicleId.isNotBlank()) {
            "معرف المركبة غير صالح."
        }

        val uid = authRepository.currentUid()
            ?: error("سجل الدخول أولا.")

        val localDevice = registry
            .ensureTrackerDevice(vehicleId)
            .getOrThrow()

        val identity = identityStore.identity()
        val now = System.currentTimeMillis()

        require(
            localDevice.deviceIdentifier ==
                identity.deviceIdentifier
        ) {
            "تعذر مطابقة هوية جهاز التتبع."
        }

        trackerDocument(
            uid = uid,
            vehicleId = vehicleId,
            deviceIdentifier = identity.deviceIdentifier
        ).set(
            mapOf(
                "deviceIdentifier" to identity.deviceIdentifier,
                "displayName" to identity.displayName,
                "role" to identity.role.name,
                "provider" to identity.provider.name,
                "gpsDeviceId" to localDevice.id,
                "heartbeatAt" to FieldValue.serverTimestamp(),
                "heartbeatEpochMs" to now,
                "updatedAt" to FieldValue.serverTimestamp(),
                "onlineHint" to true
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun markOffline(
        vehicleId: String
    ): Result<Unit> = runCatching {
        require(vehicleId.isNotBlank()) {
            "معرف المركبة غير صالح."
        }

        val uid = authRepository.currentUid()
            ?: error("سجل الدخول أولا.")

        val identity = identityStore.identity()

        trackerDocument(
            uid = uid,
            vehicleId = vehicleId,
            deviceIdentifier = identity.deviceIdentifier
        ).set(
            mapOf(
                "deviceIdentifier" to identity.deviceIdentifier,
                "onlineHint" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun fetchTrackers(
        vehicleId: String
    ): Result<List<TrackerPresenceSnapshot>> = runCatching {
        require(vehicleId.isNotBlank()) {
            "معرف المركبة غير صالح."
        }

        val uid = authRepository.currentUid()
            ?: error("سجل الدخول أولا.")

        val currentIdentity = identityStore.identity()

        firestore.collection("users")
            .document(uid)
            .collection("vehicles")
            .document(vehicleId)
            .collection("trackers")
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                val deviceIdentifier =
                    document.getString("deviceIdentifier")
                        ?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null

                val role = runCatching {
                    TrackerDeviceRole.valueOf(
                        document.getString("role").orEmpty()
                    )
                }.getOrNull()
                    ?: return@mapNotNull null

                TrackerPresenceSnapshot(
                    deviceIdentifier = deviceIdentifier,
                    displayName =
                        document.getString("displayName")
                            ?.takeIf { it.isNotBlank() }
                            ?: "Android Device",
                    role = role,
                    isCurrentDevice =
                        deviceIdentifier ==
                            currentIdentity.deviceIdentifier,
                    heartbeatEpochMs =
                        document.getLong("heartbeatEpochMs")
                            ?: 0L,
                    heartbeatServerEpochMs =
                        document.getTimestamp("heartbeatAt")
                            ?.toDate()
                            ?.time
                            ?: 0L,
                    onlineHint =
                        document.getBoolean("onlineHint")
                            ?: false
                )
            }
            .sortedByDescending {
                it.heartbeatServerEpochMs.takeIf { value -> value > 0L } ?: it.heartbeatEpochMs
            }
    }

    fun observeHeadUnitOnline(
        vehicleId: String
    ): Flow<Boolean> {
        val uid = authRepository.currentUid()
            ?: return flowOf(false)

        return callbackFlow {
            var latest = emptyList<TrackerPresenceSnapshot>()

            fun publish() {
                val now = System.currentTimeMillis()
                trySend(
                    latest.any { tracker ->
                        tracker.role == TrackerDeviceRole.HEAD_UNIT &&
                            tracker.isOnline(now)
                    }
                )
            }

            val registration = firestore.collection("users")
                .document(uid)
                .collection("vehicles")
                .document(vehicleId)
                .collection("trackers")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        latest = emptyList()
                        publish()
                        return@addSnapshotListener
                    }

                    latest = snapshot?.documents
                        ?.mapNotNull { document ->
                            val role = runCatching {
                                TrackerDeviceRole.valueOf(
                                    document.getString("role").orEmpty()
                                )
                            }.getOrNull() ?: return@mapNotNull null

                            TrackerPresenceSnapshot(
                                deviceIdentifier = document.id,
                                displayName = document.getString("displayName") ?: "Android Device",
                                role = role,
                                heartbeatEpochMs = document.getLong("heartbeatEpochMs") ?: 0L,
                                heartbeatServerEpochMs = document.getTimestamp("heartbeatAt")?.toDate()?.time ?: 0L,
                                onlineHint = document.getBoolean("onlineHint") ?: false
                            )
                        } ?: emptyList()

                    publish()
                }

            val ticker = launch {
                while (isActive) {
                    delay(10_000L)
                    publish()
                }
            }

            awaitClose {
                ticker.cancel()
                registration.remove()
            }
        }.distinctUntilChanged()
    }

    private fun trackerDocument(
        uid: String,
        vehicleId: String,
        deviceIdentifier: String
    ) = firestore.collection("users")
        .document(uid)
        .collection("vehicles")
        .document(vehicleId)
        .collection("trackers")
        .document(deviceIdentifier)
}

