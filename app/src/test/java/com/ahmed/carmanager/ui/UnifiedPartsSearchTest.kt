package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedPartsSearchTest {
    private val logan2021 = VehicleEntity(
        brand = "Renault",
        model = "Logan",
        year = 2021
    )

    @Test
    fun logan2021_automaticallyIncludesNewLoganStoreAliases() {
        val aliases = AutoSpareUnifiedAdapter.searchAliasesForStore(logan2021)

        assertTrue(aliases.any { it.contains("نيو لوجان") })
        assertTrue(aliases.any { it.contains("New Logan", ignoreCase = true) })
        assertTrue(aliases.any { it == "لوجان" || it.equals("Logan", ignoreCase = true) })
    }

    @Test
    fun userOnlyNeedsToTypePartName() {
        val identity = VehicleMarketIdentityResolver.resolve(logan2021)
        val queries = identity.searchQueries("بوجيهات", maxQueries = 12)

        assertEquals("بوجيهات", identity.sanitizePartQuery("بوجيهات"))
        assertTrue(queries.isNotEmpty())
        assertTrue(queries.all { it.contains("بوجيهات") })
        assertTrue(queries.any { it.contains("نيو لوجان") || it.contains("New Logan", ignoreCase = true) })
    }

    @Test
    fun storefrontAliasesBecomePartOfUnifiedVehicleIdentity() {
        val identity = VehicleMarketIdentityResolver.resolve(logan2021)

        assertTrue(identity.displayAliases.any { it.contains("New Logan", ignoreCase = true) || it.contains("نيو لوجان") })
        assertTrue(identity.aliasProfiles.any {
            (it.name.contains("New Logan", ignoreCase = true) || it.name.contains("نيو لوجان")) && it.confidence >= 96
        })
    }

    @Test
    fun olderLoganDoesNotUseNewLoganStoreRoute() {
        val oldLogan = logan2021.copy(year = 2013)
        val aliases = AutoSpareUnifiedAdapter.searchAliasesForStore(oldLogan)

        assertFalse(aliases.any { it.contains("New Logan", ignoreCase = true) || it.contains("نيو لوجان") })
        assertTrue(aliases.any { it == "لوجان" || it.equals("Logan", ignoreCase = true) })
    }

    @Test
    fun partFamiliesMapToAutoSpareCategoryIntent() {
        assertTrue(AutoSpareUnifiedAdapter.categoryIntent("بوجيهات").any { it.contains("بوجيه") })
        assertTrue(AutoSpareUnifiedAdapter.categoryIntent("تيل فرامل أمامي").any { it.contains("فرامل") })
        assertTrue(AutoSpareUnifiedAdapter.categoryIntent("مساعدين أمامي").any { it.contains("عفش") })
        assertTrue(AutoSpareUnifiedAdapter.categoryIntent("ردياتير").any { it.contains("تبريد") })
    }

    @Test
    fun wrongVehicleTitleStillDoesNotBecomeLoganFitment() {
        val identity = VehicleMarketIdentityResolver.resolve(logan2021)

        assertFalse(identity.classifyProductTitle("بوجيه ايريديوم جيب جراند شيروكي 2016-2024").accepted)
        assertFalse(identity.classifyProductTitle("بوجيه هوندا اكورد 2018").accepted)
    }
}
