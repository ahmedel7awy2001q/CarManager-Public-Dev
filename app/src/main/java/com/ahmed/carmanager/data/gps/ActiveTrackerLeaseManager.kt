package com.ahmed.carmanager.data.gps

import com.ahmed.carmanager.data.auth.AuthRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

enum class TrackerLeaseAuthorization {
    MANUAL,
    HEAD_UNIT_BINDING,
    TRUSTED_BLUETOOTH,
    ANDROID_AUTO
}

class ActiveTrackerLeaseManager(
    private val authRepository: AuthRepository,
    private val identityStore: TrackerDeviceIdentityStore,
    private val registry: TrackerDeviceRegistry,
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {
    suspend fun acquire(
        vehicleId: String,
        sessionId: String,
        authorization: TrackerLeaseAuthorization
    ): Result<Boolean> = runCatching {
        require(vehicleId.isNotBlank())
        require(sessionId.isNotBlank())

        registry.ensureTrackerDevice(vehicleId).getOrThrow()

        val uid = authRepository.currentUid()
            ?: error("يجب تسجيل الدخول أولا.")

        val identity = identityStore.identity()
        validateAuthorization(identity.role, authorization)

        val ref = leaseDocument(uid, vehicleId)
        val now = System.currentTimeMillis()

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)

            val active =
                snapshot.getBoolean("active") == true

            val owner =
                snapshot.getString("deviceIdentifier")

            val ownerSession =
                snapshot.getString("sessionId")

            val heartbeat =
                snapshot.getTimestamp("heartbeatAt")
                    ?.toDate()
                    ?.time
                    ?: snapshot.getLong("heartbeatEpochMs")
                    ?: 0L

            val age = now - heartbeat

            val leaseAlive =
                active &&
                    heartbeat > 0L &&
                    age in 0L..STALE_AFTER_MS

            val sameLease =
                owner == identity.deviceIdentifier &&
                    ownerSession == sessionId

            if (leaseAlive && !sameLease) {
                false
            } else {
                transaction.set(
                    ref,
                    mapOf(
                        "active" to true,
                        "vehicleId" to vehicleId,
                        "deviceIdentifier" to
                            identity.deviceIdentifier,
                        "displayName" to identity.displayName,
                        "role" to identity.role.name,
                        "authorization" to authorization.name,
                        "sessionId" to sessionId,
                        "heartbeatAt" to
                            FieldValue.serverTimestamp(),
                        "heartbeatEpochMs" to now,
                        "updatedAt" to
                            FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                true
            }
        }.await()
    }

    suspend fun renew(
        vehicleId: String,
        sessionId: String
    ): Result<Boolean> = runCatching {
        val uid = authRepository.currentUid()
            ?: return@runCatching false

        val identity = identityStore.identity()
        val ref = leaseDocument(uid, vehicleId)
        val now = System.currentTimeMillis()

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)

            val owned =
                snapshot.getBoolean("active") == true &&
                    snapshot.getString("deviceIdentifier") ==
                        identity.deviceIdentifier &&
                    snapshot.getString("sessionId") ==
                        sessionId

            if (!owned) {
                false
            } else {
                transaction.set(
                    ref,
                    mapOf(
                        "heartbeatAt" to
                            FieldValue.serverTimestamp(),
                        "heartbeatEpochMs" to now,
                        "updatedAt" to
                            FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                true
            }
        }.await()
    }

    suspend fun release(
        vehicleId: String,
        sessionId: String
    ): Result<Boolean> = runCatching {
        val uid = authRepository.currentUid()
            ?: return@runCatching false

        val identity = identityStore.identity()
        val ref = leaseDocument(uid, vehicleId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)

            val owned =
                snapshot.getString("deviceIdentifier") ==
                    identity.deviceIdentifier &&
                    snapshot.getString("sessionId") ==
                        sessionId

            if (!owned) {
                false
            } else {
                transaction.set(
                    ref,
                    mapOf(
                        "active" to false,
                        "releasedAt" to
                            FieldValue.serverTimestamp(),
                        "updatedAt" to
                            FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                true
            }
        }.await()
    }

    private fun validateAuthorization(
        role: TrackerDeviceRole,
        authorization: TrackerLeaseAuthorization
    ) {
        val valid = when (role) {
            TrackerDeviceRole.HEAD_UNIT ->
                authorization ==
                    TrackerLeaseAuthorization.HEAD_UNIT_BINDING

            TrackerDeviceRole.PHONE ->
                authorization !=
                    TrackerLeaseAuthorization.HEAD_UNIT_BINDING
        }

        require(valid) {
            "مصدر السماح بالتتبع لا يطابق نوع الجهاز."
        }
    }

    private fun leaseDocument(
        uid: String,
        vehicleId: String
    ) = firestore.collection("users")
        .document(uid)
        .collection("vehicles")
        .document(vehicleId)
        .collection("live")
        .document("activeTracker")

    private companion object {
        const val STALE_AFTER_MS = 45_000L
    }
}
