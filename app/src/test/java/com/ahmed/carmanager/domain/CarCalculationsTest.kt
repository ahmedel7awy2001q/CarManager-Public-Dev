package com.ahmed.carmanager.domain

import org.junit.Assert.*
import org.junit.Test

class CarCalculationsTest {
    @Test fun litersFromMoney_isCorrect() {
        assertEquals(40.0, CarCalculations.litersFromMoney(1000.0, 25.0)!!, 0.0001)
    }

    @Test fun estimatedRange_isCorrect() {
        assertEquals(400.0, CarCalculations.estimatedRangeKm(40.0, 10.0)!!, 0.0001)
    }

    @Test fun consumption_isCorrect() {
        assertEquals(10.0, CarCalculations.consumptionLitersPer100Km(40.0, 400.0)!!, 0.0001)
    }

    @Test fun invalidPrice_returnsNull() {
        assertNull(CarCalculations.litersFromMoney(1000.0, 0.0))
    }

    @Test fun gpsCalibration_tracksDeltaNotAbsoluteDeviceMileage() {
        assertEquals(33000.0, CarCalculations.calibratedVehicleOdometer(126950.0, 126350.0, 32400.0), 0.0001)
    }
}
