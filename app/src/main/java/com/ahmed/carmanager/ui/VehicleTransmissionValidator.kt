package com.ahmed.carmanager.ui

import com.ahmed.carmanager.data.local.model.TransmissionType
import java.util.Locale

/**
 * Cross-field validation for the selected transmission type and free-text name/code.
 * The validator is deliberately conservative: it only reports a conflict when the
 * entered text explicitly identifies another transmission family.
 */
internal object VehicleTransmissionValidator {
    fun conflict(
        selected: TransmissionType,
        transmissionName: String?,
        transmissionCode: String?
    ): String? {
        if (selected == TransmissionType.OTHER) return null
        val inferred = infer(transmissionName, transmissionCode) ?: return null
        if (inferred == selected) return null
        return "يوجد تعارض بين نوع ناقل الحركة المختار (${label(selected)}) والاسم/الكود المكتوب الذي يشير إلى ${label(inferred)}. صحّح أحدهما قبل الحفظ."
    }

    internal fun infer(transmissionName: String?, transmissionCode: String?): TransmissionType? {
        val text = listOfNotNull(transmissionName, transmissionCode)
            .joinToString(" ")
            .trim()
            .uppercase(Locale.ROOT)
        if (text.isBlank()) return null

        return when {
            Regex("""(^|[^A-Z0-9])([3-9]|10)?(CVT|IVT)([^A-Z0-9]|$)""").containsMatchIn(text) ->
                TransmissionType.CVT
            Regex("""(^|[^A-Z0-9])([3-9]|10)?(DCT|DSG)([^A-Z0-9]|$)""").containsMatchIn(text) ||
                "DUAL CLUTCH" in text || "DUAL-CLUTCH" in text ->
                TransmissionType.DCT
            Regex("""(^|[^A-Z0-9])([3-9]|10)?MT([^A-Z0-9]|$)""").containsMatchIn(text) ||
                "MANUAL" in text ->
                TransmissionType.MANUAL
            Regex("""(^|[^A-Z0-9])([3-9]|10)?AT([^A-Z0-9]|$)""").containsMatchIn(text) ||
                "AUTOMATIC" in text || "A/T" in text ->
                TransmissionType.AUTOMATIC
            else -> null
        }
    }

    private fun label(type: TransmissionType): String = when (type) {
        TransmissionType.MANUAL -> "يدوي"
        TransmissionType.AUTOMATIC -> "أوتوماتيك تقليدي"
        TransmissionType.CVT -> "CVT/IVT"
        TransmissionType.DCT -> "DCT/DSG"
        TransmissionType.OTHER -> "أخرى"
    }
}
