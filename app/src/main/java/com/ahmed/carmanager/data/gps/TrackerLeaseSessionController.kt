package com.ahmed.carmanager.data.gps

import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackerLeaseSessionState {
    OWNED,
    DEGRADED_OFFLINE,
    LOST
}

data class TrackerLeaseSession(
    val vehicleId: String,
    val sessionId: String,
    val authorization: TrackerLeaseAuthorization,
    val state: TrackerLeaseSessionState
)

class TrackerLeaseSessionController(
    private val leaseManager: ActiveTrackerLeaseManager
) {
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var renewJob: Job? = null

    private val _session =
        MutableStateFlow<TrackerLeaseSession?>(null)

    val session: StateFlow<TrackerLeaseSession?> =
        _session.asStateFlow()

    suspend fun start(
        vehicleId: String,
        authorization: TrackerLeaseAuthorization,
        sessionId: String = UUID.randomUUID().toString()
    ): Result<Boolean> {
        require(sessionId.isNotBlank())

        val currentSession = _session.value

        if (currentSession != null) {
            val sameSession =
                currentSession.vehicleId == vehicleId &&
                    currentSession.sessionId == sessionId &&
                    currentSession.authorization == authorization &&
                    currentSession.state != TrackerLeaseSessionState.LOST

            return Result.success(sameSession)
        }

        val acquired = leaseManager.acquire(
            vehicleId,
            sessionId,
            authorization
        )

        val initialState = when {
            acquired.getOrNull() == true ->
                TrackerLeaseSessionState.OWNED

            acquired.isFailure &&
                acquired.exceptionOrNull().isOfflineFailure() ->
                TrackerLeaseSessionState.DEGRADED_OFFLINE

            acquired.isFailure ->
                return Result.failure(
                    acquired.exceptionOrNull()
                        ?: IllegalStateException("Lease acquisition failed.")
                )

            else ->
                return Result.success(false)
        }

        _session.value = TrackerLeaseSession(
            vehicleId = vehicleId,
            sessionId = sessionId,
            authorization = authorization,
            state = initialState
        )

        startRenewLoop()
        return Result.success(true)
    }

    suspend fun stop() {
        renewJob?.cancel()
        renewJob = null

        val current = _session.value
        _session.value = null

        if (current != null) {
            leaseManager.release(
                current.vehicleId,
                current.sessionId
            )
        }
    }

    private fun startRenewLoop() {
        renewJob?.cancel()

        renewJob = scope.launch {
            while (isActive) {
                delay(15_000L)

                val current =
                    _session.value ?: break

                val refreshed =
                    if (current.state ==
                        TrackerLeaseSessionState.DEGRADED_OFFLINE
                    ) {
                        leaseManager.acquire(
                            current.vehicleId,
                            current.sessionId,
                            current.authorization
                        )
                    } else {
                        leaseManager.renew(
                            current.vehicleId,
                            current.sessionId
                        )
                    }

                val nextState = when {
                    refreshed.getOrNull() == true ->
                        TrackerLeaseSessionState.OWNED

                    refreshed.isFailure &&
                        refreshed.exceptionOrNull()
                            .isOfflineFailure() ->
                        TrackerLeaseSessionState.DEGRADED_OFFLINE

                    else ->
                        TrackerLeaseSessionState.LOST
                }

                _session.value =
                    current.copy(state = nextState)

                if (nextState ==
                    TrackerLeaseSessionState.LOST
                ) {
                    break
                }
            }
        }
    }

    private fun Throwable?.isOfflineFailure(): Boolean {
        var current = this

        repeat(8) {
            val error =
                current as? FirebaseFirestoreException

            if (error != null) {
                return error.code ==
                    FirebaseFirestoreException.Code.UNAVAILABLE ||
                    error.code ==
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
            }

            current = current?.cause
                ?: return false
        }

        return false
    }
}


