package com.ahmed.carmanager.data.diagnostics

import org.junit.Assert.*
import org.junit.Test

class ThinkDiagReportInterpreterTest {
    @Test fun parsesAndExplainsKnownCodes() {
        val report = ThinkDiagReportInterpreter.interpret("ECM Current P0300 Random Misfire\nECM Stored P0420 Catalyst")
        assertEquals(2, report.codes.size)
        assertTrue(report.codes.any { it.first.code == "P0300" && it.second.titleAr.contains("احتراق") })
        assertEquals(DiagnosticConcernLevel.IMPORTANT, report.overallConcern)
    }

    @Test fun repeatedCodeKeepsActualStatesFromEachLine() {
        val report = ThinkDiagReportInterpreter.interpret("ECM Stored P0300 old\nECM Active P0300 current")
        val states = report.codes.filter { it.first.code == "P0300" }.map { it.first.state }.toSet()
        assertEquals(setOf(DiagnosticCodeState.STORED, DiagnosticCodeState.ACTIVE), states)
    }

    @Test fun unknownManufacturerCodeStaysExplicitlyUncertain() {
        val report = ThinkDiagReportInterpreter.interpret("BCM Active B1234 Manufacturer code")
        val explanation = report.codes.single().second
        assertEquals(DiagnosticConfidence.NEEDS_MORE_DATA, explanation.confidence)
        assertTrue(explanation.simpleMeaningAr.contains("لا توجد لدينا"))
    }

    @Test fun extractsVinWhenPresent() {
        val report = ThinkDiagReportInterpreter.interpret("VIN: KMHDH41DBEU123456\nP0562 Active")
        assertEquals("KMHDH41DBEU123456", report.vin)
    }
}
