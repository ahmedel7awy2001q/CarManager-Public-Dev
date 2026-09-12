package com.ahmed.carmanager.data.inspection

import com.ahmed.carmanager.data.local.model.FaultSeverity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionIntelligenceTest {

    @Test
    fun templatesAreVehicleTypeAware() {
        val car = vehicle(VehicleType.CAR)
        val motorcycle = vehicle(VehicleType.MOTORCYCLE)
        val other = vehicle(VehicleType.OTHER, "تروسيكل")

        val carQuick = InspectionCatalog.defaults(car, InspectionMode.QUICK)
        val bikeFull = InspectionCatalog.defaults(motorcycle, InspectionMode.FULL)
        val otherTrip = InspectionCatalog.defaults(other, InspectionMode.PRE_TRIP)

        assertTrue(carQuick.any { it.id == "brakes" })
        assertFalse(carQuick.any { it.id == "drive_chain" })
        assertTrue(bikeFull.any { it.id == "drive_chain" })
        assertTrue(bikeFull.any { it.id == "stand" })
        assertTrue(otherTrip.any { it.id == "trip_readiness" })
        assertEquals("تروسيكل", InspectionCatalog.vehicleTypeLabel(other))
    }

    @Test
    fun customizedTemplateRoundTripsPerMode() {
        val vehicle = vehicle(VehicleType.MOTORCYCLE)
        val custom = InspectionCatalog.newCustomItem(
            titleAr = "فحص حامل الهاتف",
            hintAr = "ثابت ولا يهتز",
            severity = FaultSeverity.LOW,
            mode = InspectionMode.QUICK
        )
        val config = InspectionTemplateConfigCodec.update(
            existing = null,
            vehicleType = vehicle.vehicleType,
            mode = InspectionMode.QUICK,
            items = listOf(custom)
        )

        val restored = InspectionTemplateConfigCodec.itemsFor(vehicle.copy(inspectionTemplateConfig = config), InspectionMode.QUICK)
        assertEquals(1, restored.size)
        assertEquals(custom.id, restored.single().id)
        assertEquals("فحص حامل الهاتف", restored.single().titleAr)
        assertTrue(restored.single().custom)
    }

    @Test
    fun reportCodecKeepsScoreAndProblemCounts() {
        val vehicle = vehicle(VehicleType.CAR)
        val items = InspectionCatalog.defaults(vehicle, InspectionMode.QUICK).take(3)
        val states = mapOf(
            items[0].id to InspectionFindingState.OK,
            items[1].id to InspectionFindingState.ATTENTION,
            items[2].id to InspectionFindingState.CRITICAL
        )

        val encoded = InspectionReportCodec.encode(vehicle, InspectionMode.QUICK, items, states, "مراجعة بعد أسبوع")
        val report = InspectionReportCodec.parse(encoded)

        assertNotNull(report)
        assertEquals(74, report!!.score)
        assertEquals(1, report.okCount)
        assertEquals(1, report.attentionCount)
        assertEquals(1, report.criticalCount)
        assertEquals(3, report.totalCount)
        assertEquals(VehicleType.CAR, report.vehicleType)
    }

    private fun vehicle(type: VehicleType, customType: String? = null) = VehicleEntity(
        brand = "Test",
        model = "Vehicle",
        year = 2026,
        vehicleType = type,
        customVehicleType = customType
    )
}
