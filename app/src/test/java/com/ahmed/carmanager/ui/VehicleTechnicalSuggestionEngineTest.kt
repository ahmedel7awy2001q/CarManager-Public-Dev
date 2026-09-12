package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.TransmissionType
import org.junit.Assert.*
import org.junit.Test

class VehicleTechnicalSuggestionEngineTest {
    @Test fun grandCeratoGetsHighConfidenceBdPowertrain() {
        val x = VehicleTechnicalSuggestionEngine.suggest("Kia", "Grand Cerato", 2021).first()
        assertEquals("BD", x.generationCode)
        assertEquals("G4FG", x.engineCode)
        assertEquals(1591, x.engineCapacityCc)
        assertEquals(TransmissionType.AUTOMATIC, x.transmissionType)
        assertTrue(x.confidence >= 90)
        assertTrue(x.sources.isNotEmpty())
    }

    @Test fun egyptCaptiva2020AutoFillsCvtPowertrain() {
        val x = VehicleTechnicalSuggestionEngine.suggest("Chevrolet", "Captiva", 2020).first()
        assertEquals(1500, x.engineCapacityCc)
        assertEquals("1.5 Turbo", x.engineName)
        assertEquals(FuelType.GASOLINE_92, x.fuelType)
        assertEquals(TransmissionType.CVT, x.transmissionType)
        assertEquals("CVT", x.transmissionName)
        assertEquals(52.0, x.tankCapacityLiters!!, 0.001)
        assertNull(x.passengerCapacity)
        assertTrue(x.confidence >= 90)
        assertTrue(x.sources.size >= 2)
    }

    @Test fun egyptCaptivaSevenSeatTrimKeepsSeatVariant() {
        val x = VehicleTechnicalSuggestionEngine.suggest("Chevrolet", "Captiva", 2021, trim = "Premier 7 Seats").first()
        assertEquals(7, x.passengerCapacity)
        assertEquals(TransmissionType.CVT, x.transmissionType)
    }

    @Test fun unknownVehicleIsNotGuessed() {
        assertTrue(VehicleTechnicalSuggestionEngine.suggest("Unknown", "Mystery", 2024).isEmpty())
    }

    @Test fun mg5DoesNotInventTechnicalCodes() {
        val x = VehicleTechnicalSuggestionEngine.suggest("MG", "5", 2023).first()
        assertEquals(1500, x.engineCapacityCc)
        assertEquals(TransmissionType.CVT, x.transmissionType)
        assertNull(x.engineCode)
        assertNull(x.transmissionCode)
    }
}
