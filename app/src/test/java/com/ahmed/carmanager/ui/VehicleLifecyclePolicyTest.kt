package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleLifecyclePolicyTest {
    @Test
    fun operationalListExcludesSoldArchivedAndDeleted() {
        val active = VehicleEntity(brand = "Kia", model = "Cerato", year = 2021, status = VehicleStatus.ACTIVE)
        val secondary = VehicleEntity(brand = "Renault", model = "Logan", year = 2021, status = VehicleStatus.SECONDARY)
        val sold = VehicleEntity(brand = "Nissan", model = "Sunny", year = 2020, status = VehicleStatus.SOLD)
        val archived = VehicleEntity(brand = "Toyota", model = "Corolla", year = 2019, status = VehicleStatus.ARCHIVED)
        val deleted = active.copy(vehicleId = "deleted", isDeleted = true)
        val all = listOf(active, secondary, sold, archived, deleted)

        assertEquals(listOf(active, secondary), VehicleLifecyclePolicy.operational(all))
        assertEquals(listOf(sold, archived), VehicleLifecyclePolicy.historical(all))
        assertFalse(VehicleLifecyclePolicy.isOperational(sold))
        assertTrue(VehicleLifecyclePolicy.isHistorical(archived))
    }
}
