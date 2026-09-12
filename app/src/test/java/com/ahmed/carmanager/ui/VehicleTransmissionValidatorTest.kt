package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.TransmissionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class VehicleTransmissionValidatorTest {
    @Test
    fun automaticAnd6at_areConsistent() {
        assertNull(VehicleTransmissionValidator.conflict(TransmissionType.AUTOMATIC, "6AT", "A6GF1"))
    }

    @Test
    fun cvtAnd6at_areRejected() {
        assertNotNull(VehicleTransmissionValidator.conflict(TransmissionType.CVT, "6AT", null))
    }

    @Test
    fun cvtAndIvt_areConsistent() {
        assertNull(VehicleTransmissionValidator.conflict(TransmissionType.CVT, "IVT", null))
        assertEquals(TransmissionType.CVT, VehicleTransmissionValidator.infer("IVT", null))
    }

    @Test
    fun dctAndDsg_areConsistent() {
        assertNull(VehicleTransmissionValidator.conflict(TransmissionType.DCT, "7DSG", null))
    }
}
