package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.TransmissionType
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleTechnicalCoverageTest {
    @Test
    fun verifiedTechnicalSuggestionGetsVerifiedState() {
        val suggestion = VehicleTechnicalSuggestion(
            engineCapacityCc = 1600,
            engineName = "1.6 MPI",
            transmissionType = TransmissionType.AUTOMATIC,
            confidence = 92,
            reason = "test",
            sources = listOf(TechnicalSuggestionSource("source", "evidence"))
        )
        val result = VehicleTechnicalCoverageEvaluator.evaluate("Kia", "Cerato", 2021, listOf(suggestion))
        assertEquals(VehicleTechnicalCoverageState.TECHNICALLY_VERIFIED, result.state)
    }

    @Test
    fun generationOnlySuggestionDoesNotPretendPowertrainIsVerified() {
        val suggestion = VehicleTechnicalSuggestion(
            generationCode = "N17",
            generationName = "Sunny N17",
            confidence = 94,
            reason = "test",
            sources = listOf(TechnicalSuggestionSource("source", "evidence"))
        )
        val result = VehicleTechnicalCoverageEvaluator.evaluate("Nissan", "Sunny", 2021, listOf(suggestion))
        assertEquals(VehicleTechnicalCoverageState.IDENTITY_KNOWN_TECHNICAL_INCOMPLETE, result.state)
    }
}
