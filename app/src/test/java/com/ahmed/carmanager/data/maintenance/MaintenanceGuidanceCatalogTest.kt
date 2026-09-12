package com.ahmed.carmanager.data.maintenance

import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.TransmissionType
import com.ahmed.carmanager.data.local.model.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceGuidanceCatalogTest {

    private val cerato = VehicleEntity(
        brand = "Kia",
        model = "Grand Cerato",
        year = 2021,
        currentOdometerKm = 93_000.0
    )

    @Test
    fun manufacturerGuidanceNeverEmbedsUserHistoryOrPrices() {
        val templates = MaintenanceGuidanceCatalog.templatesFor(cerato, MaintenancePreset.CERATO_2021_EGYPT)

        assertTrue(templates.isNotEmpty())
        templates.forEach { template ->
            assertNull("Catalog must not invent last-service odometer for ${template.titleAr}", template.lastServiceOdometerKm)
            assertNull("Catalog must not invent next-due odometer for ${template.titleAr}", template.nextDueOdometerKm)
            assertNull("Catalog must not hardcode a current market price for ${template.titleAr}", template.estimatedCost)
        }
    }

    @Test
    fun ceratoGuidanceUsesInspectionForConditionBasedBrakes() {
        val templates = MaintenanceGuidanceCatalog.templatesFor(cerato, MaintenancePreset.CERATO_2021_EGYPT)
        val pads = templates.first { it.titleAr.contains("تيل الفرامل الأمامي") }

        assertTrue(pads.titleAr.contains("فحص"))
        assertFalse(pads.notes.orEmpty().contains("استبدال عند 40"))
    }

    @Test
    fun ceratoGuidanceIncludesNormalAndSevereOilSchedulesWithoutChoosingForUser() {
        val templates = MaintenanceGuidanceCatalog.templatesFor(cerato, MaintenancePreset.CERATO_2021_EGYPT)
        val normal = templates.first { it.titleAr == "زيت المحرك + فلتر الزيت" }
        val severe = templates.first { it.titleAr.contains("استخدام شاق") && it.titleAr.contains("زيت المحرك") }

        assertEquals(10_000.0, normal.intervalKm!!, 0.01)
        assertEquals(12, normal.intervalMonths)
        assertEquals(5_000.0, severe.intervalKm!!, 0.01)
        assertEquals(6, severe.intervalMonths)
    }

    @Test
    fun genericGuideProvidesActionableIntervalsWithoutInventingHistory() {
        val otherCar = VehicleEntity(brand = "Generic", model = "Car", year = 2024, currentOdometerKm = 50_000.0)
        val templates = MaintenanceGuidanceCatalog.templatesFor(otherCar, MaintenancePreset.SMART_DEFAULT)

        assertTrue(templates.size >= 25)
        assertTrue(templates.count { it.intervalKm != null || it.intervalMonths != null } >= 24)
        templates.forEach {
            assertNull(it.lastServiceOdometerKm)
            assertNull(it.nextDueOdometerKm)
            assertNull(it.estimatedCost)
        }
    }

    @Test
    fun electricVehiclePresetContainsNoCombustionMaintenance() {
        val ev = VehicleEntity(
            brand = "Example",
            model = "EV",
            year = 2026,
            fuelType = FuelType.ELECTRIC,
            transmissionType = TransmissionType.AUTOMATIC
        )
        val templates = MaintenanceGuidanceCatalog.templatesFor(ev, MaintenancePreset.SMART_DEFAULT)
        val titles = templates.map { it.titleAr }

        assertFalse(titles.any { it.contains("زيت المحرك") })
        assertFalse(titles.any { it.contains("فلتر الوقود") })
        assertFalse(titles.any { it.contains("البوجيه") || it.contains("شمعات الإشعال") })
        assertFalse(titles.any { it.contains("CVT") || it.contains("الأوتوماتيك AT") })
        assertTrue(titles.any { it.contains("فلتر التكييف") })
        assertTrue(titles.any { it.contains("بطارية ومنظومة الجهد العالي") })
        assertTrue(titles.any { it.contains("Reduction Gear") })
        assertTrue(titles.any { it.contains("منفذ وكابل الشحن") })
        assertTrue(templates.all { it.notes.orEmpty().contains("مرجع CarManager العام المحافظ") })
    }

    @Test
    fun hybridPresetKeepsCombustionCoreAndAddsHighVoltageChecks() {
        val hybrid = VehicleEntity(
            brand = "Example",
            model = "Hybrid",
            year = 2026,
            fuelType = FuelType.HYBRID,
            transmissionType = TransmissionType.CVT
        )
        val templates = MaintenanceGuidanceCatalog.templatesFor(hybrid, MaintenancePreset.SMART_DEFAULT)

        assertTrue(templates.any { it.titleAr == "زيت المحرك + فلتر الزيت" })
        assertTrue(templates.any { it.titleAr.contains("CVT") })
        assertTrue(templates.any { it.titleAr.contains("الجهد العالي") })
    }

    @Test
    fun soueastDx3GetsDedicatedReferenceInsteadOfBlankGuide() {
        val dx3 = VehicleEntity(brand = "SouEast", model = "DX3", year = 2022, currentOdometerKm = 12_000.0)
        val templates = MaintenanceGuidanceCatalog.templatesFor(dx3, MaintenancePreset.SMART_DEFAULT)
        val oil = templates.first { it.titleAr.contains("زيت المحرك") }
        assertEquals(10_000.0, oil.intervalKm!!, 0.01)
        assertEquals(6, oil.intervalMonths)
        assertTrue(templates.any { it.titleAr.contains("ناقل الحركة") && it.intervalKm != null })
    }

    @Test
    fun fiatTipo2022UsesOwnerHandbookStyleSchedule() {
        val tipo = VehicleEntity(brand = "Fiat", model = "Tipo", year = 2022, currentOdometerKm = 90_000.0)
        val templates = MaintenanceGuidanceCatalog.templatesFor(tipo, MaintenancePreset.SMART_DEFAULT)
        val service = templates.first { it.titleAr.contains("الصيانة الدورية") }
        val brakeFluid = templates.first { it.titleAr == "سائل الفرامل" }
        assertEquals(15_000.0, service.intervalKm!!, 0.01)
        assertEquals(24, brakeFluid.intervalMonths)
        assertNotNull(templates.firstOrNull { it.notes.orEmpty().contains("Owner Handbook 2022") })
    }
}
