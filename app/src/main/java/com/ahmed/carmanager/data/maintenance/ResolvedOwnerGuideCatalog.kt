package com.ahmed.carmanager.data.maintenance

import com.ahmed.carmanager.data.local.model.VehicleEntity

/**
 * Resolves the two guide layers without changing or installing a maintenance plan:
 * 1) exact OEM/market guidance when CarManager has evidence for this vehicle family;
 * 2) the comprehensive CarManager general reference for everything else.
 */
data class ResolvedOwnerGuide(
    val specificItems: List<OwnerGuideItem>,
    val generalItems: List<OwnerGuideItem>,
    val specificSourceType: OwnerGuideSourceType?,
    val specificSourceLabelAr: String?
)

object ResolvedOwnerGuideCatalog {

    fun resolve(vehicle: VehicleEntity): ResolvedOwnerGuide {
        val preset = MaintenanceCatalog.presetFor(vehicle)
        val guided = MaintenanceGuidanceCatalog.templatesFor(vehicle, preset)
        val specificTemplates = guided.filter { sourceTypeOf(it.notes) != OwnerGuideSourceType.CARMANAGER_GENERAL }
        val specific = specificTemplates.flatMap(::toGuideItems).distinctBy { it.id }

        val coveredFamilies = specific.mapTo(mutableSetOf()) { familyKey(it) }
        val general = OwnerGuideCatalog.itemsFor(vehicle).filter { familyKey(it) !in coveredFamilies }

        val dominant = when {
            specific.any { it.sourceType == OwnerGuideSourceType.OEM_VERIFIED } -> OwnerGuideSourceType.OEM_VERIFIED
            specific.any { it.sourceType == OwnerGuideSourceType.MARKET_REFERENCE } -> OwnerGuideSourceType.MARKET_REFERENCE
            else -> null
        }
        val label = when (dominant) {
            OwnerGuideSourceType.OEM_VERIFIED -> "دليل المصنع الموثق للسيارة"
            OwnerGuideSourceType.MARKET_REFERENCE -> "مرجع سوق/صيانة للسيارة — ليس دليل مصنع"
            OwnerGuideSourceType.CARMANAGER_GENERAL -> "مرجع CarManager العام المحافظ"
            null -> null
        }

        return ResolvedOwnerGuide(
            specificItems = specific,
            generalItems = general,
            specificSourceType = dominant,
            specificSourceLabelAr = label
        )
    }

    private fun toGuideItems(template: MaintenanceTemplate): List<OwnerGuideItem> {
        val sourceType = sourceTypeOf(template.notes)
        val sourceLabel = when (sourceType) {
            OwnerGuideSourceType.OEM_VERIFIED -> "دليل المصنع الموثق"
            OwnerGuideSourceType.MARKET_REFERENCE -> "مرجع سوق/صيانة — ليس دليل مصنع"
            OwnerGuideSourceType.CARMANAGER_GENERAL -> "مرجع CarManager العام المحافظ"
        }
        val note = cleanSourceNote(template.notes, sourceType).ifBlank {
            "الفترة المعروضة مرجع للخدمة/الفحص. دليل المصنع الخاص بالمحرك والفئة يتفوق على أي مرجع عام."
        }
        val main = OwnerGuideItem(
            id = semanticId(template.titleAr),
            titleAr = template.titleAr,
            categoryAr = template.category,
            action = actionFor(template.titleAr),
            intervalKm = template.intervalKm,
            intervalMonths = template.intervalMonths,
            sourceType = sourceType,
            sourceLabelAr = sourceLabel,
            noteAr = note,
            safetyCritical = isSafetyCritical(template.titleAr)
        )

        // A combined oil+filter row covers two semantic families. Split it explicitly so the oil
        // keeps a fluid-change action while the filter keeps a replacement action.
        val normalized = normalize(template.titleAr)
        return if ("زيت المحرك" in normalized && "فلتر" in normalized) {
            listOf(
                main.copy(
                    id = "engine_oil",
                    titleAr = "زيت المحرك",
                    action = OwnerGuideAction.CHANGE_FLUID
                ),
                main.copy(
                    id = "engine_oil_filter",
                    titleAr = "فلتر زيت المحرك",
                    action = OwnerGuideAction.REPLACE
                )
            )
        } else listOf(main)
    }

    private fun sourceTypeOf(notes: String?): OwnerGuideSourceType {
        val text = notes.orEmpty()
        return when {
            // Row-specific local/market wording wins even if an older list-level wrapper prefixed
            // the whole vehicle profile with an OEM source sentence.
            text.contains("خطة محافظة للسوق المحلي") ||
                text.contains("مرجع سوق/صيانة محلي") ||
                text.contains("مرجع سوق مصر") -> OwnerGuideSourceType.MARKET_REFERENCE
            text.contains("دليل المصنع الموثق") -> OwnerGuideSourceType.OEM_VERIFIED
            else -> OwnerGuideSourceType.CARMANAGER_GENERAL
        }
    }

    private fun cleanSourceNote(notes: String?, sourceType: OwnerGuideSourceType): String {
        val text = notes.orEmpty().trim()
        if (text.isBlank()) return ""
        if (sourceType == OwnerGuideSourceType.MARKET_REFERENCE && text.startsWith("دليل المصنع الموثق:")) {
            return text.substringAfter('.', text).trim()
        }
        return text
    }

    private fun actionFor(title: String): OwnerGuideAction {
        val text = normalize(title)
        return when {
            "تدوير" in text -> OwnerGuideAction.ROTATE
            "تنظيف" in text -> OwnerGuideAction.CLEAN_OR_SERVICE
            "مراجعه" in text -> OwnerGuideAction.REVIEW_OEM
            "فحص" in text -> OwnerGuideAction.INSPECT
            ("زيت" in text || "سائل" in text) && !text.contains("فلتر") -> OwnerGuideAction.CHANGE_FLUID
            "استبدال" in text || "تغيير" in text || "فلتر" in text || "بوجيه" in text -> OwnerGuideAction.REPLACE
            else -> OwnerGuideAction.REVIEW_OEM
        }
    }

    private fun semanticId(title: String): String {
        val t = normalize(title)
        return when {
            "فلتر زيت" in t -> "engine_oil_filter"
            "زيت المحرك" in t -> "engine_oil"
            "فلتر التكييف" in t || "فلتر المقصوره" in t -> "cabin_filter"
            "فلتر هواء" in t -> "air_filter_replace"
            "فلتر الوقود" in t -> "fuel_filter"
            "بوجيه" in t || "شمعات الاشعال" in t -> "spark_plugs_review"
            ("ناقل الحركه" in t || "الفتيس" in t) && ("زيت" in t || "سائل" in t) -> "transmission_fluid"
            "سائل الفرامل" in t -> "brake_fluid"
            "تيل الفرامل الامامي" in t -> "front_pads"
            "فرامل خلف" in t -> "rear_pads_shoes"
            "طنابير" in t || "اقراص" in t -> "front_discs"
            "سائل تبريد" in t && ("استبدال" in t || "تغيير" in t || "موعد" in t) -> "coolant_replace_review"
            "سائل تبريد" in t -> "coolant_level"
            "سير المجموعه" in t || "السيور الخارجيه" in t -> "accessory_belt"
            "التوقيت" in t -> "timing_system"
            "الاطارات" in t && "تدوير" in t -> "tire_rotation"
            "الاطارات" in t || "الضغط والنقشه" in t -> "tire_tread"
            "البطاريه" in t -> "battery_test"
            "العفشه" in t || "المساعد" in t -> "shock_absorbers"
            "الكبالن" in t -> "cv_joints_outer"
            "التوجيه" in t || "الدركسيون" in t -> "steering_rack"
            "قواعد المحرك" in t -> "engine_mounts"
            "pcv" in t -> "pcv"
            "التكييف" in t -> "ac_performance"
            "المساحات" in t -> "wipers"
            "الانوار" in t -> "lights"
            else -> "specific_${t.hashCode().toUInt().toString(16)}"
        }
    }

    private fun familyKey(item: OwnerGuideItem): String = when (item.id) {
        "engine_oil", "engine_oil_filter" -> item.id
        "air_filter_replace", "air_filter_inspect" -> "air_filter"
        "spark_plugs_review", "spark_plugs_inspect" -> "spark_plugs"
        "front_discs", "rear_discs_drums" -> "brake_discs"
        "front_pads", "rear_pads_shoes" -> item.id
        "coolant_level", "coolant_replace_review" -> item.id
        "shock_absorbers", "strut_mounts", "control_arms", "control_arm_bushings", "stabilizer_links", "stabilizer_bushes", "ball_joints" -> item.id
        "cv_joints_outer", "cv_joints_inner", "cv_boots" -> item.id
        else -> item.id
    }

    private fun isSafetyCritical(title: String): Boolean {
        val t = normalize(title)
        return listOf("فرامل", "تيل", "طنابير", "اطار", "دركسيون", "توجيه", "مقص", "احزمه الامان", "وسائد").any { it in t }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
