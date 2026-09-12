package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleMarketAliasLearnerTest {
    @Test
    fun generationCodeNearModelAndRelevantYears_isLearned() {
        val vehicle = VehicleEntity(brand = "Renault", model = "Logan", year = 2020)
        val identity = VehicleMarketIdentityResolver.resolve(vehicle)
        val offers = listOf(
            PartsPriceOffer(
                providerId = "egypt-store-a",
                storeName = "Egypt Store",
                title = "فلتر هواء Renault Logan L52 2019-2021",
                priceEgp = 450.0,
                availability = PartsOfferAvailability.IN_STOCK,
                checkedAt = 1L,
                sourceUrl = "https://example.eg/part"
            )
        )

        val learned = VehicleMarketAliasLearner.infer(vehicle, identity, offers)

        assertTrue(learned.any { it.contains("L52", ignoreCase = true) })
    }
}
