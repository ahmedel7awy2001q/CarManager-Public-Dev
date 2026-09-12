package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleMarketIdentityTest {
    private val cerato = VehicleEntity(
        brand = "Kia",
        model = "Grand Cerato",
        year = 2021,
        engineCapacityCc = 1600
    )

    @Test
    fun grandCerato2021_resolvesEgyptianMarketAliases() {
        val identity = VehicleMarketIdentityResolver.resolve(cerato)

        assertTrue(identity.canonicalName.contains("Cerato BD"))
        assertTrue(identity.displayAliases.any { it.contains("Grand Cerato", ignoreCase = true) })
        assertTrue(identity.displayAliases.any { it.contains("BD", ignoreCase = true) })
    }

    @Test
    fun savedMakeModel_beatsPersonalDisplayNameInSearchPriority() {
        val named = cerato.copy(displayName = "سيارتي")
        val identity = VehicleMarketIdentityResolver.resolve(named)
        val queries = identity.searchQueries("فلتر هواء", maxQueries = 2)

        assertTrue(queries.isNotEmpty())
        assertTrue(queries.first().contains("Kia Grand Cerato", ignoreCase = true))
        assertFalse(queries.first().contains("سيارتي"))
        assertTrue(queries.drop(1).any { it.contains("BD", ignoreCase = true) })
    }

    @Test
    fun wrongVehicleResults_areRejected() {
        val identity = VehicleMarketIdentityResolver.resolve(cerato)

        assertFalse(identity.classifyProductTitle("قلب طلمبة بنزين نيسان صني N17 170423Y50A").accepted)
        assertFalse(identity.classifyProductTitle("فلتر فتيس اوتوماتيك تويوتا كورولا ZRE141 2010-2013").accepted)
    }

    @Test
    fun matchingCeratoGeneration_isAccepted() {
        val identity = VehicleMarketIdentityResolver.resolve(cerato)

        val bd = identity.classifyProductTitle("فلتر بنزين كيا سيراتو BD 2019-2022")
        assertTrue(bd.accepted)
        assertEquals(PartsVehicleMatch.EXACT_GENERATION, bd.level)

        val year = identity.classifyProductTitle("فلتر بنزين كيا سيراتو 2021")
        assertTrue(year.accepted)
        assertEquals(PartsVehicleMatch.MODEL_AND_YEAR, year.level)
    }

    @Test
    fun oldCeratoGeneration_isRejected() {
        val identity = VehicleMarketIdentityResolver.resolve(cerato)

        assertFalse(identity.classifyProductTitle("فلتر بنزين كيا سيراتو TD 2012").accepted)
        assertFalse(identity.classifyProductTitle("فلتر بنزين كيا سيراتو 2012").accepted)
    }

    @Test
    fun userCanTypeVehicleName_butPartQueryIsCleaned() {
        val identity = VehicleMarketIdentityResolver.resolve(cerato)
        val cleaned = identity.sanitizePartQuery("فلتر بنزين كيا جراند سيراتو 2021")

        assertEquals("فلتر بنزين", cleaned)
    }

    @Test
    fun legacySplitOem_survivesVehicleQueryCleanup() {
        val identity = VehicleMarketIdentityResolver.resolve(cerato)
        val cleaned = identity.sanitizePartQuery("26300 35505 كيا جراند سيراتو 2021")

        assertEquals("26300-35505", cleaned)
    }

    @Test
    fun genericFamilyName_isHiddenWhenGenerationIsKnown() {
        val identity = VehicleMarketIdentityResolver.resolve(cerato)
        val generic = identity.classifyProductTitle("فلتر بنزين كيا سيراتو")

        assertTrue(generic.accepted)
        assertEquals(PartsVehicleMatch.MODEL_FAMILY, generic.level)
        assertFalse(identity.acceptsStrict(generic))
    }

    @Test
    fun learnedMarketAlias_becomesExactVehicleIdentity() {
        val logan = VehicleEntity(brand = "Renault", model = "Logan", year = 2020)
        val identity = VehicleMarketIdentityResolver.resolve(logan, setOf("Logan L52"))
        val match = identity.classifyProductTitle("فلتر هواء Renault Logan L52")

        assertTrue(identity.learnedAliases.contains("Logan L52"))
        assertTrue(match.accepted)
        assertTrue(identity.acceptsStrict(match))
        assertEquals(PartsVehicleMatch.EXACT_GENERATION, match.level)
    }
}
