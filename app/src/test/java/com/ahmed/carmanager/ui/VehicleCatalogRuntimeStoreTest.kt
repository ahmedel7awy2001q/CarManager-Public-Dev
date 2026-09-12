package com.ahmed.carmanager.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VehicleCatalogRuntimeStoreTest {
    @Before fun setUp() = VehicleCatalogRuntimeStore.clearRemote()
    @After fun tearDown() = VehicleCatalogRuntimeStore.clearRemote()

    @Test
    fun higherConfidenceRemoteGenerationOverridesBundledRangeAndKeepsAliases() {
        val evidence = VehicleCatalogEvidence(
            sourceId = "oem-test",
            title = "OEM test source",
            kind = VehicleCatalogSourceKind.OEM,
            confidence = 99
        )
        val remote = VehicleSelectionCatalog.Make(
            id = "kia",
            arName = "كيا",
            enName = "Kia",
            confidence = 95,
            evidence = listOf(evidence),
            models = listOf(
                VehicleSelectionCatalog.Model(
                    id = "cerato",
                    arName = "سيراتو",
                    enName = "Cerato",
                    fromYear = 2018,
                    toYear = 2025,
                    confidence = 95,
                    evidence = listOf(evidence),
                    marketAliases = listOf(
                        VehicleCatalogAlias(
                            name = "Kia K3",
                            relation = VehicleCatalogAliasRelation.GLOBAL_NAME,
                            confidence = 94,
                            evidence = listOf(evidence)
                        )
                    ),
                    generations = listOf(
                        VehicleSelectionCatalog.Generation(
                            code = "BD",
                            label = "Cerato BD updated",
                            fromYear = 2018,
                            toYear = 2025,
                            confidence = 95,
                            evidence = listOf(evidence)
                        )
                    )
                )
            )
        )
        VehicleCatalogRuntimeStore.applyRemote(
            listOf(remote),
            VehicleCatalogRuntimeState(activeVersion = 12, status = VehicleCatalogRuntimeState.Status.UPDATED)
        )

        val kia = VehicleSelectionCatalog.findMake("Kia")!!
        val cerato = VehicleSelectionCatalog.findModel(kia, "Cerato")!!
        val bd = cerato.generations.first { it.code == "BD" }

        assertEquals(2025, bd.toYear)
        assertEquals(95, bd.confidence)
        assertTrue(cerato.marketAliases.any { it.name == "Kia K3" })
        assertTrue(bd.evidence.any { it.sourceId == "oem-test" })
    }
}
