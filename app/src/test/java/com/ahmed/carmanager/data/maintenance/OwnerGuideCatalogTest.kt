package com.ahmed.carmanager.data.maintenance

import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.TransmissionType
import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.*
import org.junit.Test

class OwnerGuideCatalogTest {

    @Test
    fun genericAutomaticVehicleHasRequestedConservativeIntervalsAndChassisItems() {
        val vehicle = VehicleEntity(
            brand = "Example",
            model = "Sedan",
            year = 2024,
            fuelType = FuelType.GASOLINE_95,
            transmissionType = TransmissionType.AUTOMATIC
        )

        val items = OwnerGuideCatalog.itemsFor(vehicle)
        fun item(id: String) = items.first { it.id == id }

        assertEquals(10_000.0, item("engine_oil").intervalKm!!, 0.0)
        assertEquals(12, item("engine_oil").intervalMonths)
        assertEquals(20_000.0, item("air_filter_replace").intervalKm!!, 0.0)
        assertEquals(10_000.0, item("cabin_filter").intervalKm!!, 0.0)
        assertEquals(60_000.0, item("transmission_fluid").intervalKm!!, 0.0)
        assertEquals(36, item("transmission_fluid").intervalMonths)

        assertTrue(items.any { it.id == "shock_absorbers" })
        assertTrue(items.any { it.id == "stabilizer_links" })
        assertTrue(items.any { it.id == "front_pads" })
        assertTrue(items.any { it.id == "front_discs" })
        assertTrue(items.any { it.id == "tire_tread" })
        assertTrue(items.all { it.sourceType == OwnerGuideSourceType.CARMANAGER_GENERAL })
    }

    @Test
    fun cvtUsesFortyThousandKmOrThreeYearsGeneralReference() {
        val vehicle = VehicleEntity(
            brand = "Example",
            model = "CVT",
            year = 2024,
            transmissionType = TransmissionType.CVT
        )
        val transmission = OwnerGuideCatalog.itemsFor(vehicle).first { it.id == "transmission_fluid" }
        assertEquals(40_000.0, transmission.intervalKm!!, 0.0)
        assertEquals(36, transmission.intervalMonths)
    }

    @Test
    fun pureEvHasHighVoltageAndCabinGuideWithoutCombustionMaintenance() {
        val ev = VehicleEntity(
            brand = "Example",
            model = "EV",
            year = 2026,
            fuelType = FuelType.ELECTRIC,
            transmissionType = TransmissionType.AUTOMATIC
        )

        val items = OwnerGuideCatalog.itemsFor(ev)
        val ids = items.map { it.id }

        assertFalse("EV must not receive engine oil guidance", "engine_oil" in ids)
        assertFalse("EV must not receive conventional transmission-fluid guidance", "transmission_fluid" in ids)
        assertFalse("EV must not receive conventional alternator guidance", "charging_system" in ids)
        assertFalse("EV must not receive starter guidance", "starter" in ids)
        assertTrue("EV should keep cabin-filter maintenance", "cabin_filter" in ids)
        assertTrue("EV should expose reduction-gear review", "ev_reduction_gear_fluid" in ids)
        assertTrue("EV should expose high-voltage battery inspection", "hv_battery" in ids)
        assertTrue("EV should expose charging-port inspection", "charge_port" in ids)
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun hybridKeepsCombustionServiceAndAddsHighVoltageWithoutDuplicateIds() {
        val hybrid = VehicleEntity(
            brand = "Example",
            model = "Hybrid",
            year = 2026,
            fuelType = FuelType.HYBRID,
            transmissionType = TransmissionType.CVT
        )

        val items = OwnerGuideCatalog.itemsFor(hybrid)
        val ids = items.map { it.id }

        assertTrue("Hybrid still has an engine-oil service", "engine_oil" in ids)
        assertTrue("Hybrid has high-voltage battery coverage", "hv_battery" in ids)
        assertTrue("Hybrid has battery cooling-air-path coverage", "hybrid_battery_air_path" in ids)
        assertFalse("Hybrid does not use an EV charging-port rule by default", "charge_port" in ids)
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun unknownVehicleDoesNotPretendToHaveOemGuide() {
        val vehicle = VehicleEntity(brand = "UnknownBrand", model = "UnknownModel", year = 2026)
        val resolved = ResolvedOwnerGuideCatalog.resolve(vehicle)
        assertTrue(resolved.specificItems.isEmpty())
        assertNull(resolved.specificSourceType)
        assertTrue(resolved.generalItems.isNotEmpty())
        assertTrue(resolved.generalItems.all { it.sourceType == OwnerGuideSourceType.CARMANAGER_GENERAL })
    }

    @Test
    fun knownProfilesAreLabeledByEvidenceLevel() {
        val cerato = VehicleEntity(
            brand = "Kia",
            model = "Grand Cerato",
            year = 2021,
            transmissionType = TransmissionType.AUTOMATIC
        )
        val soueast = VehicleEntity(
            brand = "SouEast",
            model = "DX3",
            year = 2022,
            transmissionType = TransmissionType.CVT
        )

        val ceratoGuide = ResolvedOwnerGuideCatalog.resolve(cerato)
        val soueastGuide = ResolvedOwnerGuideCatalog.resolve(soueast)

        assertEquals(OwnerGuideSourceType.OEM_VERIFIED, ceratoGuide.specificSourceType)
        assertTrue(ceratoGuide.specificItems.any { it.sourceType == OwnerGuideSourceType.OEM_VERIFIED })
        assertEquals(OwnerGuideSourceType.MARKET_REFERENCE, soueastGuide.specificSourceType)
        assertTrue(soueastGuide.specificItems.all { it.sourceType == OwnerGuideSourceType.MARKET_REFERENCE })
    }
}
