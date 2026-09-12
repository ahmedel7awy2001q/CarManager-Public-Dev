package com.ahmed.carmanager.data.diagnostics

import com.ahmed.carmanager.data.local.model.DocumentType
import com.ahmed.carmanager.data.local.model.VehicleDocumentEntity
import com.ahmed.carmanager.data.repository.DocumentInput
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Persists ThinkDiag scans using the existing vehicle_documents table.
 * This deliberately avoids a Room schema migration: diagnostic sessions therefore remain
 * compatible with schema v4, local backup, and the existing cloud backup pipeline.
 */
object DiagnosticSessionCodec {
    const val FORMAT = "CM_DIAGNOSTIC_SESSION_V1"
    private const val RAW_MARKER = "\nRAW_TEXT_BEGIN\n"
    private const val DOCUMENT_PREFIX = "THINKDIAG2:"

    fun fingerprint(rawText: String): String {
        val normalized = rawText.trim().replace("\r\n", "\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun isSessionDocument(document: VehicleDocumentEntity): Boolean =
        !document.isDeleted &&
            document.documentType == DocumentType.OTHER &&
            (document.documentNumber?.startsWith(DOCUMENT_PREFIX) == true ||
                document.notes?.startsWith(FORMAT) == true)

    fun toDocumentInput(
        rawText: String,
        sourceName: String?,
        capturedAt: Long = System.currentTimeMillis()
    ): DocumentInput {
        val fp = fingerprint(rawText)
        val notes = buildString {
            append(FORMAT)
            append('\n')
            append("fingerprint=").append(fp).append('\n')
            append("capturedAt=").append(capturedAt).append('\n')
            append("sourceName=").append(encode(sourceName.orEmpty()))
            append(RAW_MARKER)
            append(rawText)
        }
        return DocumentInput(
            type = DocumentType.OTHER,
            number = "$DOCUMENT_PREFIX${fp.take(20)}",
            issueDate = capturedAt,
            notes = notes
        )
    }

    fun parse(document: VehicleDocumentEntity): DiagnosticSessionSnapshot? {
        if (!isSessionDocument(document)) return null
        val notes = document.notes ?: return null
        if (!notes.startsWith(FORMAT)) return null
        val rawIndex = notes.indexOf(RAW_MARKER)
        if (rawIndex < 0) return null

        val header = notes.substring(0, rawIndex)
        val rawText = notes.substring(rawIndex + RAW_MARKER.length)
        if (rawText.isBlank()) return null
        val fields = header.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val split = line.indexOf('=')
                if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
            }
            .toMap()

        val calculatedFingerprint = fingerprint(rawText)
        val storedFingerprint = fields["fingerprint"].orEmpty()
        if (storedFingerprint.isNotBlank() && storedFingerprint != calculatedFingerprint) return null

        val capturedAt = fields["capturedAt"]?.toLongOrNull()
            ?: document.issueDate
            ?: document.createdAt
        val sourceName = decode(fields["sourceName"].orEmpty()).ifBlank { null }
        return DiagnosticSessionSnapshot(
            documentId = document.id,
            capturedAt = capturedAt,
            sourceName = sourceName,
            fingerprint = calculatedFingerprint,
            rawText = rawText,
            interpretation = ThinkDiagReportInterpreter.interpret(rawText)
        )
    }

    fun sessions(documents: List<VehicleDocumentEntity>): List<DiagnosticSessionSnapshot> =
        documents.mapNotNull(::parse).sortedByDescending { it.capturedAt }

    fun containsFingerprint(documents: List<VehicleDocumentEntity>, rawText: String): Boolean {
        val target = fingerprint(rawText)
        return documents.any { document ->
            if (!isSessionDocument(document)) return@any false
            val direct = document.documentNumber?.removePrefix(DOCUMENT_PREFIX)
            direct == target.take(20) || parse(document)?.fingerprint == target
        }
    }

    fun compare(
        current: DiagnosticSessionSnapshot,
        previous: DiagnosticSessionSnapshot
    ): DiagnosticSessionComparison {
        val currentCodes = current.interpretation.codes.map { it.first.code.uppercase() }.toSet()
        val previousCodes = previous.interpretation.codes.map { it.first.code.uppercase() }.toSet()
        return DiagnosticSessionComparison(
            newCodes = (currentCodes - previousCodes).sorted(),
            persistentCodes = (currentCodes intersect previousCodes).sorted(),
            disappearedCodes = (previousCodes - currentCodes).sorted()
        )
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}

data class DiagnosticSessionSnapshot(
    val documentId: String,
    val capturedAt: Long,
    val sourceName: String?,
    val fingerprint: String,
    val rawText: String,
    val interpretation: ThinkDiagInterpretation
) {
    val codeCount: Int get() = interpretation.codes.size
    val criticalCount: Int get() = interpretation.codes.count { it.second.concern == DiagnosticConcernLevel.CRITICAL }
    val importantCount: Int get() = interpretation.codes.count { it.second.concern == DiagnosticConcernLevel.IMPORTANT }
}

data class DiagnosticSessionComparison(
    val newCodes: List<String>,
    val persistentCodes: List<String>,
    val disappearedCodes: List<String>
) {
    val hasChanges: Boolean get() = newCodes.isNotEmpty() || disappearedCodes.isNotEmpty()
}
