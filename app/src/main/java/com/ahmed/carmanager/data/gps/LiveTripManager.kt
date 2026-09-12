package com.ahmed.carmanager.data.gps

import androidx.room.withTransaction
import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.GpsConnectionStatus
import com.ahmed.carmanager.data.local.model.GpsReadingEntity
import com.ahmed.carmanager.data.local.model.TripEntity
import java.util.UUID

data class LiveTripStartResult(
    val trip: TripEntity,
    val recovered: Boolean
)

private const val MAX_OPEN_TRIP_RECOVERY_GAP_MS = 30 * 60_000L

class LiveTripManager(
    private val database: CarDatabase,
    private val registry: TrackerDeviceRegistry,
    private val leaseController: TrackerLeaseSessionController
) {
    private val tripDao = database.tripDao()
    private val gpsDao = database.gpsDao()

    suspend fun startOrRecover(
        vehicleId: String,
        authorization: TrackerLeaseAuthorization,
        isAutomatic: Boolean
    ): Result<LiveTripStartResult> = runCatching {

        require(vehicleId.isNotBlank())

        val existing = tripDao.getOpenTrip(vehicleId)
        val recoveryNow = System.currentTimeMillis()

        val lastActivity =
            existing?.let {
                maxOf(it.startTime, it.updatedAt)
            }

        val recoverable =
            lastActivity != null &&
                recoveryNow - lastActivity in
                0L..MAX_OPEN_TRIP_RECOVERY_GAP_MS

        if (
            existing != null &&
            recoverable &&
            existing.isAutomatic != isAutomatic
        ) {
            error(
                "Active trip tracking mode cannot be changed."
            )
        }

        if (existing != null && !recoverable) {
            leaseController.stop()

            val staleEnd =
                maxOf(existing.startTime, existing.updatedAt)
                    .coerceAtMost(recoveryNow)

            tripDao.update(
                existing.copy(
                    endTime = staleEnd,
                    durationSeconds =
                        ((staleEnd - existing.startTime) / 1000L)
                            .coerceAtLeast(0L),
                    updatedAt = recoveryNow
                )
            )
        }

        if (existing != null && recoverable) {
            val acquired = leaseController.start(
                vehicleId = vehicleId,
                authorization = authorization,
                sessionId = existing.id
            ).getOrThrow()

            check(acquired) {
                "هذه المركبة يتم تتبعها من جهاز آخر."
            }

            return@runCatching LiveTripStartResult(
                trip = existing,
                recovered = true
            )
        }

        val device = registry
            .ensureTrackerDevice(vehicleId)
            .getOrThrow()

        val tripId = UUID.randomUUID().toString()
        val startOdometerKm = database.vehicleDao().getById(vehicleId)?.currentOdometerKm

        val acquired = leaseController.start(
            vehicleId = vehicleId,
            authorization = authorization,
            sessionId = tripId
        ).getOrThrow()

        check(acquired) {
            "هذه المركبة يتم تتبعها من جهاز آخر."
        }

        val now = System.currentTimeMillis()

        val trip = TripEntity(
            id = tripId,
            vehicleId = vehicleId,
            gpsDeviceId = device.id,
            startTime = now,
            startOdometerKm = startOdometerKm,
            endTime = null,
            distanceKm = 0.0,
            durationSeconds = 0L,
            isAutomatic = isAutomatic,
            createdAt = now,
            updatedAt = now
        )

        try {
            tripDao.insert(trip)
        } catch (error: Throwable) {
            leaseController.stop()
            throw error
        }

        LiveTripStartResult(
            trip = trip,
            recovered = false
        )
    }

    suspend fun recordPoint(
        tripId: String,
        point: LiveLocationPoint
    ): Result<TripEntity> = runCatching {
        val trip = tripDao.getById(tripId)
            ?: error("Active trip not found.")

        check(trip.endTime == null) {
            "Trip is already finished."
        }

        val lease = leaseController.session.value

        check(
            lease != null &&
                lease.sessionId == trip.id &&
                lease.vehicleId == trip.vehicleId &&
                lease.state != TrackerLeaseSessionState.LOST
        ) {
            "Tracker lease is no longer owned by this device."
        }

        val deviceId = trip.gpsDeviceId
            ?: error("Trip has no GPS source.")

        val now = System.currentTimeMillis()

        val updated = trip.copy(
            distanceKm = trip.distanceKm +
                point.deltaMeters.coerceAtLeast(0f) / 1000.0,
            durationSeconds =
                ((point.timestamp - trip.startTime) / 1000L)
                    .coerceAtLeast(0L),
            startLatitude =
                trip.startLatitude ?: point.latitude,
            startLongitude =
                trip.startLongitude ?: point.longitude,
            endLatitude = point.latitude,
            endLongitude = point.longitude,
            updatedAt = now
        )

        database.withTransaction {
            gpsDao.insertReading(
                GpsReadingEntity(
                    vehicleId = trip.vehicleId,
                    gpsDeviceId = deviceId,
                    timestamp = point.timestamp,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    speedKmh = point.speedKmh,
                    headingDegrees = point.headingDegrees,
                    connectionStatus = GpsConnectionStatus.ONLINE
                )
            )

            tripDao.update(updated)
        }

        updated
    }

    suspend fun finish(
        tripId: String
    ): Result<TripEntity> = runCatching {
        val trip = tripDao.getById(tripId)
            ?: error("Trip not found.")

        if (trip.endTime != null) {
            leaseController.stop()
            return@runCatching trip
        }

        val now = System.currentTimeMillis()

        val finished = trip.copy(
            endTime = now,
            durationSeconds =
                ((now - trip.startTime) / 1000L)
                    .coerceAtLeast(0L),
            updatedAt = now
        )

        tripDao.update(finished)
        leaseController.stop()

        finished
    }
}


