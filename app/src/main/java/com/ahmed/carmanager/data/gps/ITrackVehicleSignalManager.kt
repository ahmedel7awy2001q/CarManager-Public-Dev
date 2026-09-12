package com.ahmed.carmanager.data.gps

import com.ahmed.carmanager.data.local.CarDatabase
import com.ahmed.carmanager.data.local.model.GpsProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class ITrackVehicleSignalManager(
    database: CarDatabase
) {
    private val gpsDao = database.gpsDao()

    fun observeConfirmed(
        vehicleId: String
    ): Flow<Boolean> {
        val readingFlow =
            gpsDao.observeLatestReadingByProvider(
                vehicleId = vehicleId,
                provider = GpsProvider.ITRACK
            )

        val clock = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(60_000L)
            }
        }

        return combine(
            readingFlow,
            clock
        ) { reading, now ->
            if (reading == null) {
                false
            } else {
                val age = now - reading.timestamp

                age in 0L..ITRACK_STALE_AFTER_MS
            }
        }
    }

    private companion object {
        const val ITRACK_STALE_AFTER_MS =
            2L * 60L * 60L * 1000L
    }
}
