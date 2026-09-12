package com.ahmed.carmanager.data.gps

import android.location.Location

data class LiveLocationPoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double?,
    val headingDegrees: Double?,
    val accuracyMeters: Float,
    val deltaMeters: Float
)

class LiveLocationPointFilter {

    private var previousSample: Location? = null
    private var distanceAnchor: Location? = null

    fun reset() {
        previousSample = null
        distanceAnchor = null
    }

    fun evaluate(location: Location): LiveLocationPoint? {
        if (!location.hasAccuracy() ||
            location.accuracy <= 0f ||
            location.accuracy > MAX_ACCURACY_METERS
        ) return null

        if (location.latitude !in -90.0..90.0 ||
            location.longitude !in -180.0..180.0
        ) return null

        val rawSpeed =
            if (location.hasSpeed())
                location.speed.coerceAtLeast(0f).toDouble() * 3.6
            else null

        if (rawSpeed != null &&
            rawSpeed > MAX_PLAUSIBLE_SPEED_KMH
        ) return null

        val old = previousSample
        var impliedSpeed: Double? = null

        if (old != null) {
            val sampleDistance = old.distanceTo(location)
            val seconds = elapsedSeconds(old, location)

            if (seconds > 0.0) {
                impliedSpeed =
                    (sampleDistance / seconds) * 3.6

                if (impliedSpeed >
                    MAX_PLAUSIBLE_SPEED_KMH
                ) return null
            }
        }

        val anchor = distanceAnchor
        var deltaMeters = 0f

        if (anchor == null) {
            distanceAnchor = Location(location)
        } else {
            val distance = anchor.distanceTo(location)

            val noiseThreshold =
                maxOf(
                    MIN_MOVEMENT_METERS,
                    minOf(
                        MAX_NOISE_METERS,
                        maxOf(
                            anchor.accuracy,
                            location.accuracy
                        ) * 0.5f
                    )
                )

            val likelyStopped =
                rawSpeed != null &&
                    rawSpeed <= STOP_SPEED_KMH

            val stationaryEscape =
                maxOf(
                    STATIONARY_ESCAPE_METERS,
                    noiseThreshold * 2f
                )

            if (distance >= noiseThreshold &&
                (!likelyStopped ||
                    distance >= stationaryEscape)
            ) {
                deltaMeters = distance
                distanceAnchor = Location(location)
            }
        }

        previousSample = Location(location)

        val resolvedSpeed =
            rawSpeed ?: when {
                old == null -> null
                deltaMeters == 0f -> 0.0
                else -> impliedSpeed
            }

        return LiveLocationPoint(
            timestamp =
                location.time.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = resolvedSpeed,
            headingDegrees =
                if (location.hasBearing())
                    location.bearing.toDouble()
                else null,
            accuracyMeters = location.accuracy,
            deltaMeters = deltaMeters
        )
    }

    private fun elapsedSeconds(
        old: Location,
        current: Location
    ): Double =
        when {
            current.elapsedRealtimeNanos >
                old.elapsedRealtimeNanos ->
                (current.elapsedRealtimeNanos -
                    old.elapsedRealtimeNanos) /
                    1_000_000_000.0

            current.time > old.time ->
                (current.time - old.time) / 1000.0

            else -> 0.0
        }

    private companion object {
        const val MAX_ACCURACY_METERS = 50f
        const val MAX_PLAUSIBLE_SPEED_KMH = 300.0
        const val MIN_MOVEMENT_METERS = 3f
        const val MAX_NOISE_METERS = 15f
        const val STOP_SPEED_KMH = 3.0
        const val STATIONARY_ESCAPE_METERS = 25f
    }
}
