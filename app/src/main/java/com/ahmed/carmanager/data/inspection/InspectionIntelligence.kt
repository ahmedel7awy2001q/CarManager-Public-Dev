package com.ahmed.carmanager.data.inspection

import com.ahmed.carmanager.data.local.model.FaultSeverity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleType
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

/** Inspection depth selected by the user. */
enum class InspectionMode(val labelAr: String) {
    QUICK("سريع"),
    FULL("شامل"),
    PRE_TRIP("قبل السفر"),
    CUSTOM("\u0645\u062E\u0635\u0635")
}

enum class InspectionFindingState {
    NOT_CHECKED,
    OK,
    ATTENTION,
    CRITICAL
}

data class InspectionTemplateItem(
    val id: String,
    val titleAr: String,
    val hintAr: String,
    val severity: FaultSeverity = FaultSeverity.MEDIUM,
    val modes: Set<InspectionMode> = InspectionMode.entries.toSet(),
    val custom: Boolean = false
)

data class InspectionReportSummary(
    val mode: InspectionMode,
    val vehicleType: VehicleType,
    val score: Int,
    val okCount: Int,
    val attentionCount: Int,
    val criticalCount: Int,
    val totalCount: Int
)

/**
 * Vehicle-type-aware inspection defaults. Items intentionally use wording shared with maintenance
 * plans (brakes, tires, steering, suspension, battery...) so inspection-created faults can raise
 * the explainable maintenance priority without a fragile hard-coded plan id.
 */
object InspectionCatalog {
    fun defaults(vehicle: VehicleEntity, mode: InspectionMode): List<InspectionTemplateItem> =
        defaults(vehicle.vehicleType, mode)

    fun defaults(type: VehicleType, mode: InspectionMode): List<InspectionTemplateItem> {
        if (mode == InspectionMode.CUSTOM) return emptyList()

        return when (type) {
        VehicleType.CAR -> carItems
        VehicleType.MOTORCYCLE -> motorcycleItems
        VehicleType.OTHER -> otherItems
        }.filter { mode in it.modes }
    }

    fun vehicleTypeLabel(vehicle: VehicleEntity): String = when (vehicle.vehicleType) {
        VehicleType.CAR -> "سيارة"
        VehicleType.MOTORCYCLE -> "موتوسيكل"
        VehicleType.OTHER -> vehicle.customVehicleType?.takeIf { it.isNotBlank() } ?: "مركبة أخرى"
    }

    fun newCustomItem(titleAr: String, hintAr: String, severity: FaultSeverity, mode: InspectionMode) =
        InspectionTemplateItem(
            id = "custom_${UUID.randomUUID()}",
            titleAr = titleAr.trim(),
            hintAr = hintAr.trim(),
            severity = severity,
            modes = setOf(mode),
            custom = true
        )

    private val carItems = listOf(
        InspectionTemplateItem("tires", "الإطارات", "الضغط، النقشة، التشققات والانتفاخات والتآكل غير المتساوي", FaultSeverity.HIGH),
        InspectionTemplateItem("brakes", "الفرامل", "الدواسة طبيعية ولا توجد أصوات أو سحب أو ضعف ملحوظ", FaultSeverity.HIGH),
        InspectionTemplateItem("engine_oil", "زيت المحرك", "المستوى مناسب ولا توجد لمبة زيت أو تسريبات ظاهرة", FaultSeverity.HIGH),
        InspectionTemplateItem("coolant", "دورة التبريد", "المستوى مناسب والمحرك بارد ولا يوجد تسريب أو ارتفاع حرارة", FaultSeverity.HIGH),
        InspectionTemplateItem("lights", "الأنوار والإشارات", "واطي وعالي وفرامل وإشارات وإنارة اللوحة", FaultSeverity.MEDIUM),
        InspectionTemplateItem("warning_lights", "لمبات التحذير", "لا توجد تحذيرات مستمرة للمحرك أو ABS أو Airbag أو الشحن", FaultSeverity.HIGH),
        InspectionTemplateItem("leaks", "التسريبات", "لا توجد بقع زيت أو تبريد أو وقود أو سوائل غير معتادة", FaultSeverity.HIGH),
        InspectionTemplateItem("wipers", "المساحات وسائل الزجاج", "تنظيف جيد ولا يوجد تقطيع أو صوت مزعج", FaultSeverity.LOW, setOf(InspectionMode.FULL, InspectionMode.PRE_TRIP)),
        InspectionTemplateItem("battery", "البطارية ونظام الشحن", "تشغيل طبيعي ولا توجد علامات ضعف أو تآكل واضح بالأقطاب", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL, InspectionMode.PRE_TRIP)),
        InspectionTemplateItem("belts", "السيور والخراطيم", "لا تشققات أو أصوات أو آثار تسريب ظاهرة", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL)),
        InspectionTemplateItem("steering", "نظام التوجيه", "لا ثقل غير معتاد أو أصوات أو فراغ ملحوظ", FaultSeverity.HIGH, setOf(InspectionMode.FULL, InspectionMode.PRE_TRIP)),
        InspectionTemplateItem("suspension", "العفشة والمساعدين", "لا خبط واضح أو ميل أو اهتزاز أو عدم ثبات", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL)),
        InspectionTemplateItem("spare_tools", "الإطار الاحتياطي وأدوات الطوارئ", "الاستبن صالح والرافعة والمفتاح ومستلزمات الطوارئ موجودة", FaultSeverity.MEDIUM, setOf(InspectionMode.PRE_TRIP)),
        InspectionTemplateItem("glass_mirrors", "الزجاج والمرايات", "الرؤية واضحة ولا توجد أضرار مؤثرة أو مرايات غير ثابتة", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL, InspectionMode.PRE_TRIP))
    )

    private val motorcycleItems = listOf(
        InspectionTemplateItem("tires", "الإطارات", "الضغط والنقشة وعدم وجود تشققات أو انتفاخات", FaultSeverity.HIGH),
        InspectionTemplateItem("front_brake", "الفرامل الأمامية", "الاستجابة طبيعية ولا توجد أصوات أو تسريب أو ضعف", FaultSeverity.HIGH),
        InspectionTemplateItem("rear_brake", "الفرامل الخلفية", "الاستجابة طبيعية ولا توجد أصوات أو ضعف ملحوظ", FaultSeverity.HIGH),
        InspectionTemplateItem("drive_chain", "السلسلة / السير ونقل الحركة", "الشد والتزييت والحالة العامة مناسبة ولا يوجد صوت غير طبيعي", FaultSeverity.HIGH),
        InspectionTemplateItem("engine_oil", "زيت المحرك", "المستوى مناسب ولا توجد لمبة زيت أو تسريبات ظاهرة", FaultSeverity.HIGH),
        InspectionTemplateItem("lights", "الأنوار والإشارات", "المصباح الأمامي والخلفي والفرامل والإشارات تعمل", FaultSeverity.MEDIUM),
        InspectionTemplateItem("steering", "المقود والتوجيه", "الحركة حرة بلا ثقل أو فراغ أو أصوات غير طبيعية", FaultSeverity.HIGH),
        InspectionTemplateItem("leaks", "التسريبات", "لا يوجد زيت أو وقود أو سائل تبريد متسرب", FaultSeverity.HIGH),
        InspectionTemplateItem("battery", "البطارية والتشغيل", "التشغيل طبيعي ولا توجد علامات ضعف أو تآكل", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL, InspectionMode.PRE_TRIP)),
        InspectionTemplateItem("suspension", "التعليق والمساعدين", "لا يوجد تسريب بالمساعدين أو عدم ثبات أو أصوات غير طبيعية", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL)),
        InspectionTemplateItem("cooling", "التبريد وحرارة المحرك", "لا ارتفاع حرارة أو تسريب؛ افحص السائل إذا كان النظام سائل التبريد", FaultSeverity.HIGH, setOf(InspectionMode.FULL, InspectionMode.PRE_TRIP)),
        InspectionTemplateItem("horn", "الكلاكس ومفاتيح التحكم", "الكلاكس والمفاتيح الأساسية تعمل بصورة طبيعية", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL, InspectionMode.PRE_TRIP)),
        InspectionTemplateItem("stand", "الستاند والتثبيت", "الستاند ثابت وحساسه يعمل إن وجد", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL)),
        InspectionTemplateItem("warning_lights", "لمبات التحذير", "لا توجد تحذيرات مستمرة بعد التشغيل", FaultSeverity.HIGH, setOf(InspectionMode.FULL, InspectionMode.PRE_TRIP))
    )

    private val otherItems = listOf(
        InspectionTemplateItem("tires", "الإطارات / العجلات", "الحالة والضغط والتثبيت مناسبون لطبيعة المركبة", FaultSeverity.HIGH),
        InspectionTemplateItem("brakes", "نظام الفرامل", "الاستجابة طبيعية ولا توجد علامات ضعف أو تسريب", FaultSeverity.HIGH),
        InspectionTemplateItem("drive", "المحرك / منظومة الدفع", "التشغيل طبيعي ولا توجد أصوات أو اهتزازات غير معتادة", FaultSeverity.HIGH),
        InspectionTemplateItem("lights", "الأنوار والإشارات", "كل الأنوار والإشارات المطلوبة تعمل", FaultSeverity.MEDIUM),
        InspectionTemplateItem("steering", "التوجيه والتحكم", "التحكم طبيعي ولا يوجد فراغ أو ثقل غير معتاد", FaultSeverity.HIGH),
        InspectionTemplateItem("leaks", "السوائل والتسريبات", "لا توجد تسريبات أو روائح أو بقع غير معتادة", FaultSeverity.HIGH),
        InspectionTemplateItem("warning_lights", "مؤشرات التحذير", "لا توجد مؤشرات أو رسائل تحذير مستمرة", FaultSeverity.HIGH),
        InspectionTemplateItem("safety", "معدات السلامة", "معدات السلامة المطلوبة لنوع المركبة متوفرة وصالحة", FaultSeverity.HIGH),
        InspectionTemplateItem("battery", "البطارية / مصدر الطاقة", "التشغيل أو الشحن طبيعي ولا توجد علامات ضعف", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL, InspectionMode.PRE_TRIP)),
        InspectionTemplateItem("suspension", "التعليق والثبات", "لا توجد أصوات أو ميل أو عدم ثبات غير طبيعي", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL)),
        InspectionTemplateItem("belts_hoses", "السيور والخراطيم والوصلات", "لا توجد تشققات أو ارتخاء أو تسريب ظاهر", FaultSeverity.MEDIUM, setOf(InspectionMode.FULL)),
        InspectionTemplateItem("trip_readiness", "جاهزية السفر والحمولة", "الحمولة والتثبيت والأدوات والاحتياطات مناسبة للرحلة", FaultSeverity.MEDIUM, setOf(InspectionMode.PRE_TRIP))
    )
}

/**
 * Compact text codec for per-vehicle customized templates. The whole string lives on VehicleEntity,
 * so current backup and cloud snapshot logic carry it automatically.
 */
object InspectionTemplateConfigCodec {
    private const val HEADER = "CM_INSPECTION_CONFIG_V1"

    fun itemsFor(vehicle: VehicleEntity, mode: InspectionMode): List<InspectionTemplateItem> {
        val parsed = parse(vehicle.inspectionTemplateConfig, vehicle.vehicleType)
        return parsed[mode]?.takeIf { it.isNotEmpty() } ?: InspectionCatalog.defaults(vehicle, mode)
    }

    fun update(
        existing: String?,
        vehicleType: VehicleType,
        mode: InspectionMode,
        items: List<InspectionTemplateItem>
    ): String {
        val map = parse(existing, vehicleType).toMutableMap()
        map[mode] = items
        return serialize(vehicleType, map)
    }

    fun resetMode(existing: String?, vehicleType: VehicleType, mode: InspectionMode): String? {
        val map = parse(existing, vehicleType).toMutableMap()
        map.remove(mode)
        return if (map.isEmpty()) null else serialize(vehicleType, map)
    }

    private fun serialize(type: VehicleType, modes: Map<InspectionMode, List<InspectionTemplateItem>>): String = buildString {
        append(HEADER).append("|type=").append(type.name).append('\n')
        InspectionMode.entries.forEach { mode ->
            val items = modes[mode] ?: return@forEach
            append("MODE|").append(mode.name).append('\n')
            items.forEach { item ->
                append("ITEM|")
                    .append(enc(item.id)).append('|')
                    .append(item.severity.name).append('|')
                    .append(enc(item.titleAr)).append('|')
                    .append(enc(item.hintAr)).append('|')
                    .append(if (item.custom) "1" else "0")
                    .append('\n')
            }
            append("ENDMODE\n")
        }
    }

    private fun parse(value: String?, expectedType: VehicleType): Map<InspectionMode, List<InspectionTemplateItem>> {
        if (value.isNullOrBlank()) return emptyMap()
        val lines = value.lineSequence().toList()
        val header = lines.firstOrNull() ?: return emptyMap()
        if (!header.startsWith(HEADER)) return emptyMap()
        val storedType = header.substringAfter("type=", "").substringBefore('|')
        if (storedType != expectedType.name) return emptyMap()

        val result = linkedMapOf<InspectionMode, MutableList<InspectionTemplateItem>>()
        var mode: InspectionMode? = null
        lines.drop(1).forEach { line ->
            when {
                line.startsWith("MODE|") -> mode = runCatching { InspectionMode.valueOf(line.substringAfter("MODE|")) }.getOrNull()
                line == "ENDMODE" -> mode = null
                line.startsWith("ITEM|") && mode != null -> {
                    val parts = line.split('|')
                    if (parts.size >= 6) {
                        val severity = runCatching { FaultSeverity.valueOf(parts[2]) }.getOrDefault(FaultSeverity.MEDIUM)
                        val item = InspectionTemplateItem(
                            id = dec(parts[1]),
                            titleAr = dec(parts[3]),
                            hintAr = dec(parts[4]),
                            severity = severity,
                            modes = setOf(mode!!),
                            custom = parts[5] == "1"
                        )
                        if (item.id.isNotBlank() && item.titleAr.isNotBlank()) result.getOrPut(mode!!) { mutableListOf() } += item
                    }
                }
            }
        }
        return result
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun dec(value: String): String = runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
}

object InspectionReportCodec {
    private const val HEADER = "CM_INSPECTION_V2"

    fun score(states: Collection<InspectionFindingState>): Int {
        val attention = states.count { it == InspectionFindingState.ATTENTION }
        val critical = states.count { it == InspectionFindingState.CRITICAL }
        return (100 - attention * 8 - critical * 18).coerceIn(0, 100)
    }

    fun encode(
        vehicle: VehicleEntity,
        mode: InspectionMode,
        items: List<InspectionTemplateItem>,
        states: Map<String, InspectionFindingState>,
        notes: String?
    ): String {
        val selectedStates = items.map { states[it.id] ?: InspectionFindingState.NOT_CHECKED }
        val ok = selectedStates.count { it == InspectionFindingState.OK }
        val attention = selectedStates.count { it == InspectionFindingState.ATTENTION }
        val critical = selectedStates.count { it == InspectionFindingState.CRITICAL }
        val score = score(selectedStates)
        return buildString {
            append(HEADER)
                .append("|mode=").append(mode.name)
                .append("|type=").append(vehicle.vehicleType.name)
                .append("|score=").append(score)
                .append("|ok=").append(ok)
                .append("|attention=").append(attention)
                .append("|critical=").append(critical)
                .append("|total=").append(items.size)
                .append('\n')
            append("فحص ").append(mode.labelAr).append(" لـ ").append(InspectionCatalog.vehicleTypeLabel(vehicle))
                .append(" عند عداد ").append(vehicle.currentOdometerKm.toLong()).append(" كم\n")
            items.forEach { item ->
                val state = states[item.id] ?: InspectionFindingState.NOT_CHECKED
                val label = when (state) {
                    InspectionFindingState.OK -> "سليم"
                    InspectionFindingState.ATTENTION -> "يحتاج متابعة"
                    InspectionFindingState.CRITICAL -> "خطر/عاجل"
                    InspectionFindingState.NOT_CHECKED -> "لم يتم الفحص"
                }
                append(item.titleAr).append(": ").append(label).append('\n')
            }
            notes?.trim()?.takeIf { it.isNotEmpty() }?.let { append("ملاحظات عامة: ").append(it) }
        }
    }

    fun parse(notes: String?): InspectionReportSummary? {
        if (notes.isNullOrBlank()) return null
        val first = notes.lineSequence().firstOrNull().orEmpty()
        if (first.startsWith(HEADER)) {
            val fields = first.split('|').drop(1).mapNotNull { token ->
                val key = token.substringBefore('=', "")
                val value = token.substringAfter('=', "")
                if (key.isBlank()) null else key to value
            }.toMap()
            val mode = runCatching { InspectionMode.valueOf(fields["mode"].orEmpty()) }.getOrNull() ?: InspectionMode.QUICK
            val type = runCatching { VehicleType.valueOf(fields["type"].orEmpty()) }.getOrNull() ?: VehicleType.CAR
            return InspectionReportSummary(
                mode = mode,
                vehicleType = type,
                score = fields["score"]?.toIntOrNull()?.coerceIn(0, 100) ?: 100,
                okCount = fields["ok"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                attentionCount = fields["attention"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                criticalCount = fields["critical"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                totalCount = fields["total"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            )
        }

        // Compatibility with the original human-readable inspection reports.
        if (notes.startsWith("فحص ذاتي CarManager")) {
            val lines = notes.lineSequence().drop(1).filter { ':' in it }.toList()
            val attention = lines.count { it.contains("يحتاج متابعة") }
            val ok = lines.count { it.contains("سليم") }
            val total = attention + ok
            if (total == 0) return null
            return InspectionReportSummary(
                mode = InspectionMode.QUICK,
                vehicleType = VehicleType.CAR,
                score = (100 - attention * 8).coerceIn(0, 100),
                okCount = ok,
                attentionCount = attention,
                criticalCount = 0,
                totalCount = total
            )
        }
        return null
    }
}
