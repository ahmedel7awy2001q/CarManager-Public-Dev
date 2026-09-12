package com.ahmed.carmanager.data.gps

import com.ahmed.carmanager.data.local.model.GpsReadingEntity
import com.ahmed.carmanager.data.local.model.TripEntity

data class LiveTripStats(
    val currentSpeedKmh: Double?,
    val averageSpeedKmh: Double?,
    val stopCount: Int
)

object LiveTripStatsCalculator {

    fun calculate(
        trip: TripEntity,
        readings: List<GpsReadingEntity>
    ): LiveTripStats {

        val ordered =
            readings
                .filter { it.timestamp >= trip.startTime }
                .sortedBy { it.timestamp }

        val currentSpeed =
            ordered.lastOrNull()
                ?.speedKmh
                ?.coerceAtLeast(0.0)

        val durationSeconds =
            (trip.durationSeconds ?: 0L)
                .coerceAtLeast(0L)

        val averageSpeed =
            if (durationSeconds > 0L &&
                trip.distanceKm >= 0.0
            ) {
                trip.distanceKm /
                    (durationSeconds / 3600.0)
            } else {
                null
            }

        var stopStartedAt: Long? = null
        var stopCount = 0
        var hasMoved = false

        for (reading in ordered) {
            val speed =
                reading.speedKmh
                    ?.coerceAtLeast(0.0)
                    ?: continue

            when {
                speed >= MOVING_SPEED_KMH -> {
                    hasMoved = true

                    val started = stopStartedAt

                    if (started != null &&
                        reading.timestamp - started >=
                        MIN_STOP_DURATION_MS
                    ) {
                        stopCount++
                    }

                    stopStartedAt = null
                }

                speed <= STOP_SPEED_KMH &&
                    hasMoved &&
                    stopStartedAt == null -> {
                    stopStartedAt = reading.timestamp
                }
            }
        }

        val pendingStop = stopStartedAt

        if (pendingStop != null) {
            val lastTimestamp =
                ordered.lastOrNull()?.timestamp
                    ?: pendingStop

            if (lastTimestamp - pendingStop >=
                MIN_STOP_DURATION_MS
            ) {
                stopCount++
            }
        }

        return LiveTripStats(
            currentSpeedKmh = currentSpeed,
            averageSpeedKmh = averageSpeed,
            stopCount = stopCount
        )
    }

    private const val STOP_SPEED_KMH = 3.0
    private const val MOVING_SPEED_KMH = 5.0
    private const val MIN_STOP_DURATION_MS = 60_000L
}

