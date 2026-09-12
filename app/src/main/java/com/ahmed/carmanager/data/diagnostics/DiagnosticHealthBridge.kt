package com.ahmed.carmanager.data.diagnostics

import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.FaultSeverity
import com.ahmed.carmanager.data.local.model.FaultStatus
import com.ahmed.carmanager.data.repository.FaultInput

/**
 * Planned changes for linking one ThinkDiag interpretation to the existing fault history.
 * Existing open DTCs are never duplicated or downgraded; they are only escalated when the
 * new scan has a strictly higher severity.
 */
data class DiagnosticFaultSyncPlan(
    val newFaults: List<FaultInput>,
    val escalatedFaults: List<FaultRecordEntity>
) {
    val totalChanges: Int get() = newFaults.size + escalatedFaults.size
}

/** Converts interpreted report findings into the existing fault/maintenance/Vehicle Health pipeline. */
object DiagnosticHealthBridge {
    fun faultInputs(
        report: ThinkDiagInterpretation,
        odometerKm: Double,
        existingFaults: List<FaultRecordEntity>
    ): List<FaultInput> = syncPlan(report, odometerKm, existingFaults).newFaults

    fun syncPlan(
        report: ThinkDiagInterpretation,
        odometerKm: Double,
        existingFaults: List<FaultRecordEntity>,
        now: Long = System.currentTimeMillis()
    ): DiagnosticFaultSyncPlan {
        val newFaults = mutableListOf<FaultInput>()
        val escalated = mutableListOf<FaultRecordEntity>()

        // A report can contain the same DTC in more than one state. Treat it as one fault and
        // keep the strongest interpretation instead of creating duplicate records in one scan.
        val strongestByCode = report.codes
            .filter { it.second.concern != DiagnosticConcernLevel.NORMAL }
            .groupBy { it.first.code.uppercase() }
            .mapValues { (_, findings) ->
                findings.maxWithOrNull(
                    compareBy<Pair<ParsedDiagnosticCode, ArabicDtcExplanation>>(
                        { severityRank(it.second.faultSeverity) },
                        { stateRank(it.first.state) }
                    )
                )!!
            }

        strongestByCode.values.forEach { (parsed, explanation) ->
            val existing = findOpenCode(parsed.code, existingFaults)
            if (existing == null) {
                newFaults += toFaultInput(parsed, explanation, odometerKm)
            } else if (severityRank(explanation.faultSeverity) > severityRank(existing.severity)) {
                escalated += existing.copy(
                    severity = explanation.faultSeverity,
                    notes = appendEscalationNote(
                        existingNotes = existing.notes,
                        previousSeverity = existing.severity,
                        parsed = parsed,
                        explanation = explanation,
                        odometerKm = odometerKm,
                        now = now
                    ),
                    updatedAt = now
                )
            }
        }

        return DiagnosticFaultSyncPlan(newFaults = newFaults, escalatedFaults = escalated)
    }

    fun healthPenalty(report: ThinkDiagInterpretation): Int = report.codes.fold(0) { total, (_, explanation) ->
        total + when (explanation.concern) {
            DiagnosticConcernLevel.NORMAL -> 0
            DiagnosticConcernLevel.FOLLOW_UP -> 2
            DiagnosticConcernLevel.IMPORTANT -> 6
            DiagnosticConcernLevel.CRITICAL -> 12
        }
    }.coerceAtMost(30)

    fun healthSummaryAr(report: ThinkDiagInterpretation): String = buildString {
        append(report.headlineAr).append(" • ").append(report.summaryAr)
        report.vin?.let { append(" • VIN ").append(it) }
    }

    private fun toFaultInput(
        parsed: ParsedDiagnosticCode,
        explanation: ArabicDtcExplanation,
        odometerKm: Double
    ) = FaultInput(
        symptomAr = "ThinkDiag ${parsed.code}: ${explanation.titleAr}",
        severity = explanation.faultSeverity,
        odometerKm = odometerKm,
        notes = diagnosticDetails(parsed, explanation)
    )

    private fun appendEscalationNote(
        existingNotes: String?,
        previousSeverity: FaultSeverity,
        parsed: ParsedDiagnosticCode,
        explanation: ArabicDtcExplanation,
        odometerKm: Double,
        now: Long
    ): String = buildString {
        existingNotes?.takeIf { it.isNotBlank() }?.let {
            append(it.trim())
            append("\n\n")
        }
        append("DiagnosticUpdate=THINKDIAG2; DTC=").append(parsed.code)
        append("; EscalatedFrom=").append(previousSeverity.name)
        append("; EscalatedTo=").append(explanation.faultSeverity.name)
        append("; UpdatedAt=").append(now)
        append("; OdometerKm=").append(odometerKm)
        append('\n')
        append(diagnosticDetails(parsed, explanation))
    }

    private fun diagnosticDetails(
        parsed: ParsedDiagnosticCode,
        explanation: ArabicDtcExplanation
    ): String = buildString {
        append("DiagnosticSource=THINKDIAG2; DTC=").append(parsed.code)
        append("; State=").append(parsed.state.name)
        parsed.system?.let { append("; System=").append(it) }
        append("\nالمعنى: ").append(explanation.simpleMeaningAr)
        append("\nهل هو مقلق؟ ").append(concernLabel(explanation.concern))
        append("\nالقيادة: ").append(explanation.canDriveAr)
        append("\nالخطوة التالية: ").append(explanation.nextStepAr)
        if (explanation.possibleCausesAr.isNotEmpty()) {
            append("\nأسباب محتملة وليست تشخيصًا مؤكدًا: ")
                .append(explanation.possibleCausesAr.joinToString("، "))
        }
        append("\nالثقة: ").append(confidenceLabel(explanation.confidence))
        parsed.originalText?.let { append("\nالنص الأصلي: ").append(it) }
    }

    private fun findOpenCode(code: String, faults: List<FaultRecordEntity>): FaultRecordEntity? {
        val marker = "DTC=$code"
        return faults.firstOrNull {
            !it.isDeleted && it.status != FaultStatus.RESOLVED && it.status != FaultStatus.CLOSED &&
                (it.notes.orEmpty().contains(marker, ignoreCase = true) || it.symptomAr.contains(code, ignoreCase = true))
        }
    }

    private fun severityRank(value: FaultSeverity): Int = when (value) {
        FaultSeverity.LOW -> 0
        FaultSeverity.MEDIUM -> 1
        FaultSeverity.HIGH -> 2
        FaultSeverity.CRITICAL -> 3
    }

    private fun stateRank(value: DiagnosticCodeState): Int = when (value) {
        DiagnosticCodeState.UNKNOWN -> 0
        DiagnosticCodeState.HISTORICAL -> 1
        DiagnosticCodeState.STORED -> 2
        DiagnosticCodeState.PENDING -> 3
        DiagnosticCodeState.ACTIVE -> 4
    }

    private fun concernLabel(value: DiagnosticConcernLevel) = when (value) {
        DiagnosticConcernLevel.NORMAL -> "غير مقلق حاليًا"
        DiagnosticConcernLevel.FOLLOW_UP -> "يحتاج متابعة"
        DiagnosticConcernLevel.IMPORTANT -> "مهم ويحتاج فحصًا قريبًا"
        DiagnosticConcernLevel.CRITICAL -> "حرج"
    }

    private fun confidenceLabel(value: DiagnosticConfidence) = when (value) {
        DiagnosticConfidence.HIGH -> "عالية"
        DiagnosticConfidence.MEDIUM -> "متوسطة"
        DiagnosticConfidence.NEEDS_MORE_DATA -> "يحتاج بيانات إضافية"
    }
}
