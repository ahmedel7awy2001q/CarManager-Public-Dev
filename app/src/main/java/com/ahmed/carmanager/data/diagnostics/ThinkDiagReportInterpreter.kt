package com.ahmed.carmanager.data.diagnostics

import com.ahmed.carmanager.data.local.model.FaultSeverity

enum class DiagnosticConcernLevel { NORMAL, FOLLOW_UP, IMPORTANT, CRITICAL }
enum class DiagnosticConfidence { HIGH, MEDIUM, NEEDS_MORE_DATA }
enum class DiagnosticCodeState { ACTIVE, PENDING, STORED, HISTORICAL, UNKNOWN }

data class ParsedDiagnosticCode(
    val code: String,
    val originalText: String? = null,
    val system: String? = null,
    val state: DiagnosticCodeState = DiagnosticCodeState.UNKNOWN
)

data class ArabicDtcExplanation(
    val code: String,
    val titleAr: String,
    val simpleMeaningAr: String,
    val concern: DiagnosticConcernLevel,
    val confidence: DiagnosticConfidence,
    val canDriveAr: String,
    val nextStepAr: String,
    val possibleCausesAr: List<String>,
    val faultSeverity: FaultSeverity
)

data class ThinkDiagInterpretation(
    val codes: List<Pair<ParsedDiagnosticCode, ArabicDtcExplanation>>,
    val overallConcern: DiagnosticConcernLevel,
    val headlineAr: String,
    val summaryAr: String,
    val warningsAr: List<String>,
    val vin: String? = null
)

/**
 * Offline-first interpreter for text extracted from ThinkDiag/ThinkDiag+ reports.
 * It never treats a DTC as proof that a specific component must be replaced.
 * A real exported report will be used later to tune layout-specific parsing without changing this model.
 */
object ThinkDiagReportInterpreter {
    private val dtcRegex = Regex("\\b[PCBU][0-9A-F]{4}\\b", RegexOption.IGNORE_CASE)
    private val vinRegex = Regex("\\b[A-HJ-NPR-Z0-9]{17}\\b", RegexOption.IGNORE_CASE)

    fun interpret(rawText: String): ThinkDiagInterpretation {
        val vin = vinRegex.find(rawText)?.value?.uppercase()
        val codes = rawText.lineSequence()
            .flatMap { rawLine ->
                val line = rawLine.trim()
                dtcRegex.findAll(line).map { match ->
                    val code = match.value.uppercase()
                    ParsedDiagnosticCode(
                        code = code,
                        originalText = line.takeIf { it.isNotBlank() }?.take(500),
                        system = inferSystem(code, line),
                        state = inferState(line)
                    )
                }
            }
            .distinctBy { it.code to it.state }
            .toList()

        val explained = codes.map { it to DtcArabicKnowledge.explain(it) }
        val concern = explained.maxByOrNull { concernRank(it.second.concern) }?.second?.concern ?: DiagnosticConcernLevel.NORMAL
        val active = explained.count { it.first.state == DiagnosticCodeState.ACTIVE }
        val critical = explained.count { it.second.concern == DiagnosticConcernLevel.CRITICAL }
        val important = explained.count { it.second.concern == DiagnosticConcernLevel.IMPORTANT }
        val warnings = buildList {
            if (codes.isEmpty()) add("لم يتم العثور على أكواد DTC واضحة في النص المستورد؛ هذا لا يثبت أن المركبة خالية من الأعطال.")
            if (explained.any { it.second.confidence == DiagnosticConfidence.NEEDS_MORE_DATA }) add("بعض الأكواد تحتاج بيانات الشركة المصنعة أو Freeze Frame/Live Data لتفسير أدق.")
            add("كود العطل نقطة بداية للتشخيص وليس أمرًا باستبدال القطعة المذكورة.")
        }
        return ThinkDiagInterpretation(
            codes = explained,
            overallConcern = concern,
            headlineAr = when (concern) {
                DiagnosticConcernLevel.NORMAL -> "لا توجد أكواد مقلقة واضحة"
                DiagnosticConcernLevel.FOLLOW_UP -> "توجد ملاحظات تحتاج متابعة"
                DiagnosticConcernLevel.IMPORTANT -> "يوجد عطل مهم يحتاج فحصًا قريبًا"
                DiagnosticConcernLevel.CRITICAL -> "توجد نتيجة حرجة تستحق التصرف سريعًا"
            },
            summaryAr = "تم التعرف على ${codes.size} نتيجة DTC؛ نشط $active، مهم $important، حرج $critical.",
            warningsAr = warnings,
            vin = vin
        )
    }

    private fun inferState(line: String): DiagnosticCodeState {
        val s = line.lowercase()
        return when {
            listOf("current", "active", "present", "حالي", "نشط").any(s::contains) -> DiagnosticCodeState.ACTIVE
            listOf("pending", "معلق", "معلّق", "قيد").any(s::contains) -> DiagnosticCodeState.PENDING
            listOf("history", "historical", "سابق", "تاريخ").any(s::contains) -> DiagnosticCodeState.HISTORICAL
            listOf("stored", "memory", "محفوظ", "مسجل", "مسجّل").any(s::contains) -> DiagnosticCodeState.STORED
            else -> DiagnosticCodeState.UNKNOWN
        }
    }

    private fun inferSystem(code: String, line: String): String = when {
        line.contains("ABS", true) -> "ABS / الفرامل"
        line.contains("SRS", true) || line.contains("AIRBAG", true) -> "SRS / الوسائد الهوائية"
        line.contains("TCM", true) || line.contains("TRANSMISSION", true) -> "ناقل الحركة"
        line.contains("ECM", true) || code.startsWith("P") -> "المحرك / مجموعة الحركة"
        code.startsWith("C") -> "الشاسيه"
        code.startsWith("B") -> "هيكل المركبة"
        code.startsWith("U") -> "شبكة الاتصال بين الوحدات"
        else -> "نظام غير محدد"
    }

    private fun concernRank(level: DiagnosticConcernLevel) = when (level) {
        DiagnosticConcernLevel.NORMAL -> 0
        DiagnosticConcernLevel.FOLLOW_UP -> 1
        DiagnosticConcernLevel.IMPORTANT -> 2
        DiagnosticConcernLevel.CRITICAL -> 3
    }
}

/** Curated offline seed. Unknown/manufacturer-specific codes remain explicitly uncertain. */
object DtcArabicKnowledge {
    private data class Entry(val title: String, val meaning: String, val concern: DiagnosticConcernLevel, val causes: List<String>, val next: String)
    private val entries = mapOf(
        "P0300" to Entry("اختلال احتراق عشوائي/متعدد", "الكمبيوتر رصد احتراقًا غير منتظم في أكثر من أسطوانة أو بصورة عشوائية.", DiagnosticConcernLevel.IMPORTANT, listOf("بوجيهات أو كويلات", "وقود/رشاشات", "تسريب هواء", "ضغط محرك أو توقيت"), "افحص الأكواد المصاحبة وبيانات Misfire وFreeze Frame قبل تغيير أي قطعة."),
        "P0301" to Entry("اختلال احتراق بالأسطوانة 1", "تم رصد Misfire بالأسطوانة الأولى.", DiagnosticConcernLevel.IMPORTANT, listOf("بوجيه", "كويل", "رشاش", "ضغط الأسطوانة"), "قارن البوجيه/الكويل والرشاش وبيانات الأسطوانة قبل الاستبدال."),
        "P0420" to Entry("كفاءة المحول الحفاز أقل من الحد", "قراءات نظام العادم تشير إلى أن كفاءة معالجة الانبعاثات أقل من المتوقع.", DiagnosticConcernLevel.FOLLOW_UP, listOf("حساس أكسجين", "تسريب عادم", "مشكلة احتراق", "المحول الحفاز"), "لا تغيّر علبة البيئة اعتمادًا على الكود وحده؛ افحص Misfire وتسريب العادم وحساسات الأكسجين أولًا."),
        "P0171" to Entry("خليط الوقود فقير - Bank 1", "المحرك يعمل بخليط هواء/وقود أفقر من المتوقع.", DiagnosticConcernLevel.IMPORTANT, listOf("تسريب فاكيوم", "ضغط وقود", "MAF", "رشاشات"), "راجع Fuel Trims وMAF وضغط الوقود وابحث عن تسريب هواء."),
        "P0128" to Entry("حرارة سائل التبريد أقل من المتوقع", "المحرك لا يصل إلى درجة التشغيل المتوقعة بالسرعة المطلوبة.", DiagnosticConcernLevel.FOLLOW_UP, listOf("ثرموستات", "حساس حرارة", "مستوى/دورة تبريد"), "افحص حرارة التشغيل الفعلية ومستوى سائل التبريد والثرموستات."),
        "P0562" to Entry("جهد النظام منخفض", "وحدة التحكم رصدت جهدًا كهربائيًا أقل من المتوقع.", DiagnosticConcernLevel.IMPORTANT, listOf("بطارية ضعيفة", "دينامو/شحن", "أقطاب أو توصيلات"), "اختبر البطارية وجهد الشحن والتوصيلات قبل استبدال أي مكوّن."),
        "U0100" to Entry("فقد الاتصال بوحدة المحرك", "إحدى الوحدات فقدت الاتصال بوحدة التحكم في المحرك.", DiagnosticConcernLevel.IMPORTANT, listOf("هبوط جهد", "شبكة CAN", "فيشة/ضفيرة", "وحدة تحكم"), "ابدأ بالبطارية والجهد ثم افحص أكواد الشبكة والوصلات؛ لا تفترض تلف ECU مباشرة.")
    )

    fun explain(code: ParsedDiagnosticCode): ArabicDtcExplanation {
        val entry = entries[code.code]
        if (entry != null) {
            val adjusted = if (code.state == DiagnosticCodeState.HISTORICAL && entry.concern == DiagnosticConcernLevel.IMPORTANT) DiagnosticConcernLevel.FOLLOW_UP else entry.concern
            return ArabicDtcExplanation(
                code.code, entry.title, entry.meaning, adjusted, DiagnosticConfidence.MEDIUM,
                drivingAdvice(adjusted), entry.next, entry.causes, severity(adjusted)
            )
        }
        val generic = when (code.code.firstOrNull()) {
            'P' -> "كود متعلق بمجموعة الحركة (المحرك/ناقل الحركة)"
            'C' -> "كود متعلق بالشاسيه أو أنظمة الثبات/الفرامل"
            'B' -> "كود متعلق بأنظمة هيكل المركبة"
            'U' -> "كود متعلق بالاتصال بين وحدات التحكم"
            else -> "كود تشخيص إلكتروني"
        }
        val concern = if (code.state == DiagnosticCodeState.ACTIVE) DiagnosticConcernLevel.IMPORTANT else DiagnosticConcernLevel.FOLLOW_UP
        return ArabicDtcExplanation(
            code.code, generic,
            "تم التعرف على الكود لكن لا توجد لدينا الآن دلالة عربية موثوقة كافية لهذا الكود تحديدًا؛ قد يكون خاصًا بالشركة المصنعة.",
            concern, DiagnosticConfidence.NEEDS_MORE_DATA, drivingAdvice(concern),
            "احتفظ بالنص الأصلي من ThinkDiag والنظام والحالة، وراجع بيانات الشركة المصنعة وFreeze Frame قبل اتخاذ قرار إصلاح.",
            emptyList(), severity(concern)
        )
    }

    private fun drivingAdvice(level: DiagnosticConcernLevel) = when (level) {
        DiagnosticConcernLevel.NORMAL -> "لا يوجد قيد قيادة مستنتج من هذه النتيجة وحدها."
        DiagnosticConcernLevel.FOLLOW_UP -> "يمكن المتابعة عادةً إذا لا توجد أعراض أو تحذيرات أخرى، مع الفحص قريبًا."
        DiagnosticConcernLevel.IMPORTANT -> "استخدم المركبة بحذر وافحص السبب قريبًا؛ الأعراض الفعلية قد تستدعي التوقف."
        DiagnosticConcernLevel.CRITICAL -> "يفضل عدم مواصلة القيادة قبل تقييم السبب، خصوصًا إذا ظهرت أعراض سلامة أو حرارة/ضغط زيت."
    }

    private fun severity(level: DiagnosticConcernLevel) = when (level) {
        DiagnosticConcernLevel.NORMAL -> FaultSeverity.LOW
        DiagnosticConcernLevel.FOLLOW_UP -> FaultSeverity.MEDIUM
        DiagnosticConcernLevel.IMPORTANT -> FaultSeverity.HIGH
        DiagnosticConcernLevel.CRITICAL -> FaultSeverity.CRITICAL
    }
}
