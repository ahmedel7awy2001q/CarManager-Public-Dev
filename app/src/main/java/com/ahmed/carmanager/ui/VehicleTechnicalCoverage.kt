package com.ahmed.carmanager.ui

internal enum class VehicleTechnicalCoverageState {
    TECHNICALLY_VERIFIED,
    IDENTITY_KNOWN_TECHNICAL_INCOMPLETE,
    UNKNOWN_IDENTITY
}

internal data class VehicleTechnicalCoverage(
    val state: VehicleTechnicalCoverageState,
    val title: String,
    val detail: String
)

internal object VehicleTechnicalCoverageEvaluator {
    fun evaluate(
        brand: String,
        model: String,
        year: Int?,
        suggestions: List<VehicleTechnicalSuggestion>
    ): VehicleTechnicalCoverage {
        val make = VehicleSelectionCatalog.findMake(brand)
        val knownModel = VehicleSelectionCatalog.findModel(make, model)
        val identityKnown = brand.isNotBlank() && model.isNotBlank() && year != null &&
            (knownModel != null || suggestions.isNotEmpty())

        val verifiedTechnical = suggestions.any { suggestion ->
            if (suggestion.confidence < 85 || suggestion.sources.isEmpty()) return@any false
            val detailCount = listOf(
                suggestion.engineCapacityCc,
                suggestion.engineName,
                suggestion.engineCode,
                suggestion.fuelType,
                suggestion.transmissionType,
                suggestion.transmissionName,
                suggestion.transmissionCode,
                suggestion.tankCapacityLiters
            ).count { it != null }
            detailCount >= 2
        }

        return when {
            verifiedTechnical -> VehicleTechnicalCoverage(
                VehicleTechnicalCoverageState.TECHNICALLY_VERIFIED,
                "بيانات فنية موثقة متاحة",
                "يمكن تطبيق المواصفات المقترحة ومراجعتها قبل الحفظ. لن يملأ CarManager أي كود غير موثق."
            )
            identityKnown -> VehicleTechnicalCoverage(
                VehicleTechnicalCoverageState.IDENTITY_KNOWN_TECHNICAL_INCOMPLETE,
                "هوية السيارة معروفة — التفاصيل الفنية غير مكتملة",
                "الماركة والموديل والسنة معروفون، لكن لا توجد أدلة كافية لملء المحرك أو الفتيس تلقائيًا. اترك الحقول غير المعروفة فارغة بدل التخمين."
            )
            else -> VehicleTechnicalCoverage(
                VehicleTechnicalCoverageState.UNKNOWN_IDENTITY,
                "السيارة غير مكتملة في الكتالوج الفني حاليًا",
                "يمكن حفظها يدويًا واستخدام بقية التطبيق. البيانات الفنية ستظل اختيارية ولن يتم اختراع مواصفات لها."
            )
        }
    }
}
