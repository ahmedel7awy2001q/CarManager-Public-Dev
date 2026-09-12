package com.ahmed.carmanager.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.CarManagerApplication
import com.ahmed.carmanager.data.diagnostics.*
import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.VehicleDocumentEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.repository.DocumentInput
import com.ahmed.carmanager.data.repository.FaultInput
import com.ahmed.carmanager.data.repository.VehicleRepositoryResult
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.launch

@Composable
fun ThinkDiagReportScreen(
    vehicle: VehicleEntity?,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>,
    onAddFault: (FaultInput) -> Unit,
    onSaveSession: (DocumentInput) -> Unit,
    onMessage: (String) -> Unit,
    onOpenHealth: () -> Unit,
    onOpenFaults: () -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا ثم ارفع تقرير ThinkDiag.", Icons.Default.Memory)
        return
    }

    val context = LocalContext.current
    val app = context.applicationContext as CarManagerApplication
    val scope = rememberCoroutineScope()
    val escalationStore = remember(app) {
        DiagnosticFaultEscalationStore(app.container.database, app.container.authRepository)
    }
    var sourceName by remember(vehicle.vehicleId) { mutableStateOf<String?>(null) }
    var interpretation by remember(vehicle.vehicleId) { mutableStateOf<ThinkDiagInterpretation?>(null) }
    var reportFingerprint by remember(vehicle.vehicleId) { mutableStateOf<String?>(null) }
    var linkedFingerprint by remember(vehicle.vehicleId) { mutableStateOf<String?>(null) }
    var importError by remember(vehicle.vehicleId) { mutableStateOf<String?>(null) }
    val savedThisOpen = remember(vehicle.vehicleId) { mutableStateListOf<String>() }
    val sessions = remember(documents) { DiagnosticSessionCodec.sessions(documents) }
    val latestComparison = remember(sessions) {
        if (sessions.size >= 2) DiagnosticSessionCodec.compare(sessions[0], sessions[1]) else null
    }

    fun analyze(text: String, name: String?) {
        sourceName = name
        interpretation = ThinkDiagReportInterpreter.interpret(text)
        importError = null

        val fingerprint = DiagnosticSessionCodec.fingerprint(text)
        reportFingerprint = fingerprint
        linkedFingerprint = null
        val alreadySaved = fingerprint in savedThisOpen || DiagnosticSessionCodec.containsFingerprint(documents, text)
        if (!alreadySaved) {
            onSaveSession(DiagnosticSessionCodec.toDocumentInput(text, name))
            savedThisOpen += fingerprint
            onMessage("تم حفظ جلسة ThinkDiag في سجل المركبة للمقارنة مع الفحوص القادمة.")
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { readDiagnosticText(context, uri) }
                .onSuccess { (name, text) ->
                    if (text.isBlank()) {
                        importError = "الملف لا يحتوي نصًا يمكن تحليله. احتفظ به لحين إضافة قارئ مخصص لهذا التنسيق."
                    } else {
                        analyze(text, name)
                    }
                }
                .onFailure {
                    importError = "تعذر قراءة التقرير كنص. إذا كان PDF مصورًا أو بتنسيق خاص فسيحتاج قارئًا مخصصًا بعد توفر نموذج حقيقي من ThinkDiag."
                }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumGradientHeader(
                    title = "ThinkDiag والتشخيص",
                    subtitle = "قراءة واعية للتقرير • حفظ جلسات • ربط الأعطال بدون تشخيص وهمي",
                    icon = CMIcons.Diagnostics
                )
                AutomotiveActionCard(
                    title = "رفع تقرير ThinkDiag",
                    subtitle = "TXT / CSV / JSON / XML • دعم PDF الحقيقي عند توفر نموذج فعلي",
                    icon = Icons.Default.UploadFile,
                    tone = AutoTone.VIOLET,
                    modifier = Modifier.fillMaxWidth(),
                    filled = true,
                    onClick = { picker.launch(arrayOf("text/*", "application/json", "text/csv", "application/xml", "application/pdf")) }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutomotiveMetricCard("جلسات محفوظة", sessions.size.toString(), CMIcons.History, AutoTone.BLUE, Modifier.weight(1f))
                    AutomotiveMetricCard(
                        "أكواد حالية",
                        interpretation?.codes?.size?.toString() ?: "—",
                        CMIcons.Fault,
                        if ((interpretation?.codes?.size ?: 0) > 0) AutoTone.CORAL else AutoTone.GRAPHITE,
                        Modifier.weight(1f)
                    )
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = autoToneColors(AutoTone.AMBER).soft) {
                    Text(
                        "DTC نقطة بداية للتشخيص وليس حكمًا بتغيير قطعة. CarManager يعرض ما يمكن إثباته فقط ويحافظ على النص الأصلي للمقارنة.",
                        Modifier.padding(11.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = autoToneColors(AutoTone.AMBER).onSoft,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        importError?.let { error -> item { DiagnosticNotice(error, true) } }

        interpretation?.let { report ->
            item { DiagnosticSummaryCard(report, sourceName) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = onOpenHealth, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.HealthAndSafety, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("صحة المركبة")
                    }
                    FilledTonalButton(onClick = onOpenFaults, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.WarningAmber, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("سجل الأعطال")
                    }
                }
            }

            if (report.codes.isEmpty()) {
                item {
                    DiagnosticNotice(
                        "لم نعثر على DTC واضح. هذا لا يعني أن المركبة سليمة ميكانيكيًا؛ قد يكون التقرير بتنسيق لم نتعرف عليه بعد أو لا يحتوي أكوادًا.",
                        false
                    )
                }
            } else {
                item {
                    Text(
                        "شرح الأكواد",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(report.codes, key = { "${it.first.code}-${it.first.state}" }) { (parsed, explanation) ->
                    DiagnosticCodeCard(parsed, explanation)
                }
                item {
                    val plan = DiagnosticHealthBridge.syncPlan(report, vehicle.currentOdometerKm, faults)
                    val alreadyLinkedThisOpen = reportFingerprint != null && linkedFingerprint == reportFingerprint
                    Button(
                        enabled = plan.totalChanges > 0 && !alreadyLinkedThisOpen,
                        onClick = {
                            linkedFingerprint = reportFingerprint
                            plan.newFaults.forEach(onAddFault)

                            if (plan.escalatedFaults.isEmpty()) {
                                onMessage("تم ربط ${plan.newFaults.size} نتيجة جديدة بسجل الأعطال، وستدخل تلقائيًا في صحة المركبة وأولوية الصيانة المرتبطة.")
                            } else {
                                scope.launch {
                                    var escalatedCount = 0
                                    var firstError: String? = null
                                    plan.escalatedFaults.forEach { fault ->
                                        when (val result = escalationStore.update(fault)) {
                                            is VehicleRepositoryResult.Success -> escalatedCount++
                                            is VehicleRepositoryResult.Error -> if (firstError == null) firstError = result.messageAr
                                        }
                                    }
                                    if (escalatedCount > 0) {
                                        app.container.cloudBackupManager.uploadLatest()
                                    }
                                    val addedCount = plan.newFaults.size
                                    if (addedCount > 0 || escalatedCount > 0) {
                                        onMessage(
                                            "تمت إضافة $addedCount نتيجة جديدة وتصعيد $escalatedCount عطل موجود بدون إنشاء تكرار أو خفض للخطورة."
                                        )
                                    }
                                    firstError?.let(onMessage)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            when {
                                alreadyLinkedThisOpen -> "تم ربط هذا التقرير"
                                plan.totalChanges == 0 -> "لا توجد أعطال جديدة أو تصعيد مطلوب"
                                plan.escalatedFaults.isNotEmpty() -> "ربط ${plan.newFaults.size} جديد • تصعيد ${plan.escalatedFaults.size}"
                                else -> "ربط ${plan.newFaults.size} نتيجة بسجل الأعطال"
                            }
                        )
                    }
                }
            }
            report.warningsAr.forEach { warning -> item { DiagnosticNotice(warning, false) } }
        } ?: item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "ارفع التقرير كما يصدر من ThinkDiag. سيحتفظ المفسّر بالنص الأصلي ويعرض ما استطاع إثباته فقط؛ ولن يفترض أن كود DTC يعني تغيير قطعة بعينها.",
                    Modifier.padding(10.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item { DiagnosticHistoryHeader(sessions.size) }

        latestComparison?.let { comparison ->
            item { DiagnosticComparisonCard(comparison) }
        }

        if (sessions.isEmpty()) {
            item { DiagnosticNotice("لا توجد جلسات ThinkDiag محفوظة بعد. أول تقرير نصي صالح سيُحفظ تلقائيًا للمقارنة لاحقًا.", false) }
        } else {
            items(sessions.take(5), key = { it.documentId }) { session ->
                DiagnosticSessionCard(session)
            }
        }
    }
}

@Composable
private fun DiagnosticSummaryCard(report: ThinkDiagInterpretation, sourceName: String?) {
    val color = when (report.overallConcern) {
        DiagnosticConcernLevel.NORMAL -> MaterialTheme.colorScheme.secondaryContainer
        DiagnosticConcernLevel.FOLLOW_UP -> MaterialTheme.colorScheme.tertiaryContainer
        DiagnosticConcernLevel.IMPORTANT -> MaterialTheme.colorScheme.tertiaryContainer
        DiagnosticConcernLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
    }
    ElevatedCard(shape = RoundedCornerShape(14.dp), colors = CardDefaults.elevatedCardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), horizontalAlignment = Alignment.End) {
            Text(concernLabel(report.overallConcern), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(report.headlineAr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
            Text(report.summaryAr, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            report.vin?.let { Text("VIN: $it", style = MaterialTheme.typography.labelSmall) }
            sourceName?.let { Text("الملف: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DiagnosticCodeCard(parsed: ParsedDiagnosticCode, explanation: ArabicDtcExplanation) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(concernLabel(explanation.concern)) })
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${parsed.code} • ${explanation.titleAr}", fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                    Text("${parsed.system ?: "نظام غير محدد"} • ${stateLabel(parsed.state)}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(explanation.simpleMeaningAr, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            DiagnosticDetail("هل أقدر أقود؟", explanation.canDriveAr)
            DiagnosticDetail("ماذا أفعل؟", explanation.nextStepAr)
            if (explanation.possibleCausesAr.isNotEmpty()) {
                DiagnosticDetail("أسباب محتملة", explanation.possibleCausesAr.joinToString(" • "))
            }
            DiagnosticDetail("ثقة التفسير", confidenceLabel(explanation.confidence))
            parsed.originalText?.takeIf { it.isNotBlank() }?.let { DiagnosticDetail("النص الأصلي", it) }
        }
    }
}

@Composable
private fun DiagnosticHistoryHeader(count: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text("$count", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("سجل جلسات التشخيص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("كل تقرير محفوظ كجلسة مستقلة للمقارنة قبل وبعد الإصلاح", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosticSessionCard(session: DiagnosticSessionSnapshot) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    session.sourceName ?: "جلسة ThinkDiag",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
                Text(
                    "${formatDate(session.capturedAt)} • أكواد ${session.codeCount} • مهم ${session.importantCount} • حرج ${session.criticalCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (session.criticalCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun DiagnosticComparisonCard(comparison: DiagnosticSessionComparison) {
    val container = if (comparison.newCodes.isNotEmpty()) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = container) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("مقارنة آخر جلستين", fontWeight = FontWeight.Bold)
            Text("جديد: ${codeList(comparison.newCodes)}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            Text("مستمر: ${codeList(comparison.persistentCodes)}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            Text("اختفى: ${codeList(comparison.disappearedCodes)}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun DiagnosticDetail(title: String, value: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}

@Composable
private fun DiagnosticNotice(text: String, error: Boolean) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.Top) {
            Icon(if (error) Icons.Default.ErrorOutline else Icons.Default.Info, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
    }
}

private fun readDiagnosticText(context: Context, uri: Uri): Pair<String?, String> {
    val mime = context.contentResolver.getType(uri).orEmpty()
    if (mime.equals("application/pdf", true)) {
        throw IllegalArgumentException("PDF parser pending real ThinkDiag sample")
    }
    val name = queryDisplayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/')
    val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open report")
    val text = stream.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() }
    return name to text
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }
}.getOrNull()

private fun codeList(codes: List<String>): String = if (codes.isEmpty()) "لا يوجد" else codes.joinToString("، ")

private fun concernLabel(value: DiagnosticConcernLevel) = when (value) {
    DiagnosticConcernLevel.NORMAL -> "🟢 غير مقلق"
    DiagnosticConcernLevel.FOLLOW_UP -> "🟡 متابعة"
    DiagnosticConcernLevel.IMPORTANT -> "🟠 مهم"
    DiagnosticConcernLevel.CRITICAL -> "🔴 حرج"
}

private fun stateLabel(value: DiagnosticCodeState) = when (value) {
    DiagnosticCodeState.ACTIVE -> "نشط حاليًا"
    DiagnosticCodeState.PENDING -> "معلّق"
    DiagnosticCodeState.STORED -> "محفوظ"
    DiagnosticCodeState.HISTORICAL -> "تاريخي"
    DiagnosticCodeState.UNKNOWN -> "الحالة غير محددة"
}

private fun confidenceLabel(value: DiagnosticConfidence) = when (value) {
    DiagnosticConfidence.HIGH -> "عالية"
    DiagnosticConfidence.MEDIUM -> "متوسطة — راجع البيانات المصاحبة"
    DiagnosticConfidence.NEEDS_MORE_DATA -> "يحتاج بيانات إضافية/معلومات الشركة المصنعة"
}
