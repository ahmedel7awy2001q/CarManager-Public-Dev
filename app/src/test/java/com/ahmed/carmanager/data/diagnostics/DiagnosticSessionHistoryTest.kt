package com.ahmed.carmanager.data.diagnostics

import com.ahmed.carmanager.data.local.model.VehicleDocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSessionHistoryTest {

    @Test
    fun session_roundTrip_preservesReportAndSource() {
        val raw = "VIN KMHD841ABCU123456\nP0300 active ECM\nP0420 stored ECM"
        val input = DiagnosticSessionCodec.toDocumentInput(raw, "scan-1.txt", capturedAt = 123456L)
        val entity = VehicleDocumentEntity(
            id = "doc-1",
            vehicleId = "vehicle-1",
            documentType = input.type,
            documentNumber = input.number,
            issueDate = input.issueDate,
            notes = input.notes
        )

        val parsed = requireNotNull(DiagnosticSessionCodec.parse(entity))
        assertEquals("scan-1.txt", parsed.sourceName)
        assertEquals(123456L, parsed.capturedAt)
        assertEquals(2, parsed.codeCount)
        assertEquals(setOf("P0300", "P0420"), parsed.interpretation.codes.map { it.first.code }.toSet())
        assertTrue(DiagnosticSessionCodec.containsFingerprint(listOf(entity), raw))
    }

    @Test
    fun compare_marksNewPersistentAndDisappearedCodes() {
        val previous = session("old", "P0300 active\nP0420 stored", 100L)
        val current = session("new", "P0300 active\nU0100 active", 200L)

        val comparison = DiagnosticSessionCodec.compare(current, previous)
        assertEquals(listOf("U0100"), comparison.newCodes)
        assertEquals(listOf("P0300"), comparison.persistentCodes)
        assertEquals(listOf("P0420"), comparison.disappearedCodes)
        assertTrue(comparison.hasChanges)
    }

    @Test
    fun ordinaryDocument_isNotDiagnosticSession() {
        val ordinary = VehicleDocumentEntity(
            id = "doc-normal",
            vehicleId = "vehicle-1",
            documentType = com.ahmed.carmanager.data.local.model.DocumentType.OTHER,
            documentNumber = "123",
            notes = "فاتورة عادية"
        )
        assertFalse(DiagnosticSessionCodec.isSessionDocument(ordinary))
        assertEquals(null, DiagnosticSessionCodec.parse(ordinary))
    }

    private fun session(id: String, raw: String, at: Long): DiagnosticSessionSnapshot {
        val input = DiagnosticSessionCodec.toDocumentInput(raw, "$id.txt", at)
        val entity = VehicleDocumentEntity(
            id = id,
            vehicleId = "vehicle-1",
            documentType = input.type,
            documentNumber = input.number,
            issueDate = input.issueDate,
            notes = input.notes
        )
        return requireNotNull(DiagnosticSessionCodec.parse(entity))
    }
}
