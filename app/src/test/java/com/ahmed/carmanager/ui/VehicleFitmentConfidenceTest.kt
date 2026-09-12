package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.TransmissionType
import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleFitmentConfidenceTest {
    private val captiva = VehicleEntity(
        brand = "Chevrolet",
        model = "Captiva",
        year = 2021,
        engineCapacityCc = 1500,
        transmissionType = TransmissionType.CVT
    )

    @Test
    fun siblingName_withoutOem_isCappedAndExplained() {
        val identity = VehicleMarketIdentityResolver.resolve(captiva)
        val offer = PartsPriceOffer(
            providerId = "eg",
            storeName = "Egypt Store",
            title = "MG Hector 2021 فلتر هواء 1.5 CVT",
            priceEgp = 1000.0,
            availability = PartsOfferAvailability.IN_STOCK,
            checkedAt = 1L,
            sourceUrl = "https://example.eg",
            vehicleMatch = PartsVehicleMatch.EXACT_GENERATION
        )

        val result = PartsFitmentEvaluator.assess(captiva, identity, offer, "فلتر هواء")

        assertTrue(result.confidence <= 76)
        assertTrue(result.signals.any { it.contains("شقيقة") })
    }

    @Test
    fun explicitEngineConflict_isRejected() {
        val identity = VehicleMarketIdentityResolver.resolve(captiva)
        val offer = PartsPriceOffer(
            providerId = "eg",
            storeName = "Egypt Store",
            title = "Chevrolet Captiva 2021 طقم 2.0 AT",
            priceEgp = 1000.0,
            availability = PartsOfferAvailability.IN_STOCK,
            checkedAt = 1L,
            sourceUrl = "https://example.eg",
            vehicleMatch = PartsVehicleMatch.MODEL_AND_YEAR
        )

        val result = PartsFitmentEvaluator.assess(captiva, identity, offer, "طقم")

        assertFalse(result.accepted)
        assertTrue(result.reason.contains("تعارض"))
    }

    @Test
    fun exactOemMatch_canConfirmCrossMarketListing() {
        val identity = VehicleMarketIdentityResolver.resolve(captiva)
        val offer = PartsPriceOffer(
            providerId = "eg",
            storeName = "Egypt Store",
            title = "Baojun 530 2021 OEM 23901234",
            priceEgp = 1000.0,
            availability = PartsOfferAvailability.IN_STOCK,
            checkedAt = 1L,
            sourceUrl = "https://example.eg",
            vehicleMatch = PartsVehicleMatch.EXACT_GENERATION
        )

        val result = PartsFitmentEvaluator.assess(captiva, identity, offer, "23901234")

        assertTrue(result.accepted)
        assertTrue(result.confidence >= 99)
        assertTrue(result.reason.contains("OEM"))
    }
}
