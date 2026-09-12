package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.filled.FactCheck

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.inspection.InspectionCatalog
import com.ahmed.carmanager.data.inspection.InspectionFindingState
import com.ahmed.carmanager.data.inspection.InspectionMode
import com.ahmed.carmanager.data.inspection.InspectionReportCodec
import com.ahmed.carmanager.data.inspection.InspectionTemplateConfigCodec
import com.ahmed.carmanager.data.inspection.InspectionTemplateItem
import com.ahmed.carmanager.data.local.model.DocumentType
import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.FaultSeverity
import com.ahmed.carmanager.data.local.model.FaultStatus
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.repository.DocumentInput
import com.ahmed.carmanager.data.repository.FaultInput

@Composable
fun VehicleInspectionScreen(
    vehicle: VehicleEntity?,
    faults: List<FaultRecordEntity> = emptyList(),
    onAddFault: (FaultInput) -> Unit,
    onSaveInspection: (DocumentInput) -> Unit,
    onSaveTemplateConfig: (String?) -> Unit = {},
    onMessage: (String) -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا لإجراء الفحص الذاتي.", Icons.AutoMirrored.Filled.FactCheck)
        return
    }

    var mode by remember(vehicle.vehicleId) { mutableStateOf(InspectionMode.QUICK) }
    var effectiveConfig by remember(vehicle.vehicleId) { mutableStateOf(vehicle.inspectionTemplateConfig) }
    LaunchedEffect(vehicle.inspectionTemplateConfig) { effectiveConfig = vehicle.inspectionTemplateConfig }

    var sessionItems by remember(vehicle.vehicleId) {
        mutableStateOf(InspectionTemplateConfigCodec.itemsFor(vehicle, mode))
    }
    val states = remember(vehicle.vehicleId) { mutableStateMapOf<String, InspectionFindingState>() }
    var notes by remember(vehicle.vehicleId) { mutableStateOf("") }
    var confirmSave by remember { mutableStateOf(false) }
    var customize by remember { mutableStateOf(false) }

    fun resetStates(items: List<InspectionTemplateItem>) {
        states.clear()
        items.forEach { states[it.id] = InspectionFindingState.NOT_CHECKED }
    }

    LaunchedEffect(mode, effectiveConfig, vehicle.vehicleType) {
        val configuredVehicle = vehicle.copy(inspectionTemplateConfig = effectiveConfig)
        sessionItems = InspectionTemplateConfigCodec.itemsFor(configuredVehicle, mode)
        resetStates(sessionItems)
    }

    val checked = sessionItems.count { (states[it.id] ?: InspectionFindingState.NOT_CHECKED) != InspectionFindingState.NOT_CHECKED }
    val attention = sessionItems.count { states[it.id] == InspectionFindingState.ATTENTION }
    val critical = sessionItems.count { states[it.id] == InspectionFindingState.CRITICAL }
    val progress = if (sessionItems.isEmpty()) 0f else checked / sessionItems.size.toFloat()
    val score = if (checked == 0) null else InspectionReportCodec.score(sessionItems.map { states[it.id] ?: InspectionFindingState.NOT_CHECKED })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumGradientHeader(
                    title = "الفحص الذكي",
                    subtitle = "${vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}"} • ${InspectionCatalog.vehicleTypeLabel(vehicle)}",
                    icon = CMIcons.Inspection
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutomotiveMetricCard("النتيجة", score?.let { "$it%" } ?: "لم يبدأ", CMIcons.Health, when { score == null -> AutoTone.GRAPHITE; critical > 0 -> AutoTone.RED; attention > 0 -> AutoTone.AMBER; else -> AutoTone.GREEN }, Modifier.weight(1f))
                    AutomotiveMetricCard("تم فحصه", "$checked/${sessionItems.size}", CMIcons.Inspection, AutoTone.BLUE, Modifier.weight(1f))
                    AutomotiveMetricCard("حرج", critical.toString(), CMIcons.Fault, if (critical > 0) AutoTone.RED else AutoTone.GRAPHITE, Modifier.weight(1f))
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            InspectionMode.entries.forEach { item ->
                                FilterChip(
                                    selected = mode == item,
                                    onClick = { mode = item },
                                    label = { Text(item.labelAr) },
                                    leadingIcon = {
                                        Icon(
                                            when (item) {
                                                InspectionMode.QUICK -> Icons.Default.Bolt
                                                InspectionMode.FULL -> Icons.Default.Checklist
                                                InspectionMode.PRE_TRIP -> Icons.Default.Route
                                                InspectionMode.CUSTOM -> Icons.Default.Tune
                                            },
                                            null,
                                            Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (critical > 0) "$critical حالة حرجة تحتاج إجراء" else if (attention > 0) "$attention بند يحتاج متابعة" else "الفحص الحالي بلا ملاحظات خطرة حتى الآن",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { customize = true }) {
                    Icon(Icons.Default.Tune, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تخصيص القالب")
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("نقاط الفحص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${mode.labelAr} • تظهر البنود المناسبة لنوع المركبة فقط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(sessionItems, key = { it.id }) { template ->
            InspectionItemCard(
                template = template,
                state = states[template.id] ?: InspectionFindingState.NOT_CHECKED,
                onState = { states[template.id] = it }
            )
        }

        if (sessionItems.isEmpty()) {
            item { EmptyState("القالب فارغ", "أعد القالب الافتراضي أو أضف بند فحص مخصص.", Icons.Default.Tune) }
        }

        item { AppField(notes, { notes = it }, "ملاحظات عامة - اختياري") }

        item {
            Button(
                enabled = sessionItems.isNotEmpty() && checked == sessionItems.size,
                onClick = { confirmSave = true },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        critical > 0 -> "حفظ الفحص وتسجيل $critical حالة حرجة"
                        attention > 0 -> "حفظ الفحص وتسجيل $attention متابعة"
                        else -> "حفظ نتيجة الفحص"
                    }
                )
            }
        }

        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)
            ) {
                Text(
                    "أي بند «متابعة» أو «حرج» يُربط بسجل الأعطال. وإذا كان له بند صيانة مرتبط، ترتفع أولويته تلقائيًا في الحزمة القادمة حتى تتم معالجة العطل. لا يتم إنشاء عطل مكرر إذا كان نفس بند الفحص مفتوحًا بالفعل.",
                    Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }

    if (customize) {
        InspectionTemplateCustomizerSheet(
            mode = mode,
            initialItems = sessionItems,
            onDismiss = { customize = false },
            onSave = { newItems ->
                val config = InspectionTemplateConfigCodec.update(effectiveConfig, vehicle.vehicleType, mode, newItems)
                effectiveConfig = config
                sessionItems = newItems
                resetStates(newItems)
                onSaveTemplateConfig(config)
                customize = false
                onMessage("تم حفظ قالب الفحص المخصص لهذه المركبة وسيتم تضمينه في النسخ الاحتياطية والمزامنة.")
            },
            onReset = {
                val config = InspectionTemplateConfigCodec.resetMode(effectiveConfig, vehicle.vehicleType, mode)
                effectiveConfig = config
                val defaults = InspectionCatalog.defaults(vehicle, mode)
                sessionItems = defaults
                resetStates(defaults)
                onSaveTemplateConfig(config)
                customize = false
                onMessage("تمت استعادة قالب ${mode.labelAr} الافتراضي لهذه المركبة.")
            }
        )
    }

    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            icon = { Icon(if (critical > 0 || attention > 0) Icons.Default.WarningAmber else Icons.Default.CheckCircle, null) },
            title = { Text("حفظ نتيجة الفحص؟") },
            text = {
                Text(
                    when {
                        critical > 0 -> "سيتم حفظ تقرير الفحص وربط الحالات الحرجة والمتابعات بسجل الأعطال، مما يرفع أولوية الصيانة المرتبطة تلقائيًا."
                        attention > 0 -> "سيتم حفظ تقرير الفحص وربط البنود التي تحتاج متابعة بسجل الأعطال والصيانة المرتبطة."
                        else -> "كل البنود تم تعليمها كسليمة. سيتم حفظ التقرير في تاريخ المركبة."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val problemItems = sessionItems.filter {
                        states[it.id] == InspectionFindingState.ATTENTION || states[it.id] == InspectionFindingState.CRITICAL
                    }
                    val newFaultItems = problemItems.filterNot { hasOpenInspectionFault(it, faults) }
                    newFaultItems.forEach { item ->
                        val state = states[item.id] ?: InspectionFindingState.ATTENTION
                        onAddFault(
                            FaultInput(
                                symptomAr = "ملاحظة فحص: ${item.titleAr}",
                                severity = if (state == InspectionFindingState.CRITICAL) FaultSeverity.CRITICAL else item.severity,
                                odometerKm = vehicle.currentOdometerKm,
                                notes = "InspectionItemId=${item.id}; InspectionMode=${mode.name}; المصدر: فحص CarManager. يبقى العطل مفتوحًا للمتابعة ويرفع أولوية الصيانة المرتبطة حتى إغلاقه."
                            )
                        )
                    }
                    onSaveInspection(
                        DocumentInput(
                            type = DocumentType.INSPECTION,
                            issueDate = System.currentTimeMillis(),
                            notes = InspectionReportCodec.encode(vehicle, mode, sessionItems, states, notes)
                        )
                    )
                    confirmSave = false
                    val skippedDuplicates = problemItems.size - newFaultItems.size
                    onMessage(
                        buildString {
                            append("تم حفظ فحص ${mode.labelAr} بنتيجة ${score ?: 0}%")
                            if (newFaultItems.isNotEmpty()) append(" وإضافة ${newFaultItems.size} متابعة إلى سجل الأعطال")
                            if (skippedDuplicates > 0) append("، مع تجنب $skippedDuplicates عطل مكرر")
                            append(".")
                        }
                    )
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun InspectionItemCard(
    template: InspectionTemplateItem,
    state: InspectionFindingState,
    onState: (InspectionFindingState) -> Unit
) {
    val container = when (state) {
        InspectionFindingState.NOT_CHECKED -> MaterialTheme.colorScheme.surface
        InspectionFindingState.OK -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .42f)
        InspectionFindingState.ATTENTION -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .55f)
        InspectionFindingState.CRITICAL -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .62f)
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = container)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(inspectionIcon(template.id), null, Modifier.padding(7.dp).size(19.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(template.titleAr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(template.hintAr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                InspectionStateButton("حرج", Icons.Default.Error, state == InspectionFindingState.CRITICAL, true, Modifier.weight(1f)) { onState(InspectionFindingState.CRITICAL) }
                InspectionStateButton("متابعة", Icons.Default.WarningAmber, state == InspectionFindingState.ATTENTION, false, Modifier.weight(1f)) { onState(InspectionFindingState.ATTENTION) }
                InspectionStateButton("سليم", Icons.Default.CheckCircle, state == InspectionFindingState.OK, false, Modifier.weight(1f)) { onState(InspectionFindingState.OK) }
            }
        }
    }
}

@Composable
private fun InspectionStateButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    critical: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        colors = if (selected) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = when {
                    critical -> MaterialTheme.colorScheme.errorContainer
                    label == "متابعة" -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
            )
        } else ButtonDefaults.outlinedButtonColors()
    ) {
        Icon(icon, null, Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectionTemplateCustomizerSheet(
    mode: InspectionMode,
    initialItems: List<InspectionTemplateItem>,
    onDismiss: () -> Unit,
    onSave: (List<InspectionTemplateItem>) -> Unit,
    onReset: () -> Unit
) {
    val draft = remember(mode, initialItems) { initialItems.toMutableStateList() }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var addNew by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("تخصيص قالب ${mode.labelAr}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("يمكن تعديل الاسم والوصف والأهمية، تغيير الترتيب، حذف بند أو إضافة بند مخصص.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(draft.size, key = { index -> draft[index].id }) { index ->
                    val item = draft[index]
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (index > 0) { val moved = draft.removeAt(index); draft.add(index - 1, moved) } }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ArrowUpward, "لأعلى", Modifier.size(17.dp))
                            }
                            IconButton(onClick = { if (index < draft.lastIndex) { val moved = draft.removeAt(index); draft.add(index + 1, moved) } }, enabled = index < draft.lastIndex, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ArrowDownward, "لأسفل", Modifier.size(17.dp))
                            }
                            IconButton(onClick = { editingIndex = index }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, "تعديل", Modifier.size(17.dp)) }
                            IconButton(onClick = { draft.removeAt(index) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.DeleteOutline, "حذف", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error) }
                            Spacer(Modifier.width(5.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(item.titleAr, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(severityLabel(item.severity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(7.dp))
            FilledTonalButton(onClick = { addNew = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("إضافة بند مخصص")
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TextButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("استعادة الافتراضي") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                Button(onClick = { onSave(draft.toList()) }, enabled = draft.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("حفظ") }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    editingIndex?.let { index ->
        val current = draft.getOrNull(index)
        if (current != null) {
            InspectionTemplateItemEditor(
                title = "تعديل بند الفحص",
                item = current,
                mode = mode,
                onDismiss = { editingIndex = null },
                onSave = { draft[index] = it; editingIndex = null }
            )
        }
    }
    if (addNew) {
        InspectionTemplateItemEditor(
            title = "بند فحص مخصص",
            item = null,
            mode = mode,
            onDismiss = { addNew = false },
            onSave = { draft.add(it); addNew = false }
        )
    }
}

@Composable
private fun InspectionTemplateItemEditor(
    title: String,
    item: InspectionTemplateItem?,
    mode: InspectionMode,
    onDismiss: () -> Unit,
    onSave: (InspectionTemplateItem) -> Unit
) {
    var name by remember(item?.id) { mutableStateOf(item?.titleAr.orEmpty()) }
    var hint by remember(item?.id) { mutableStateOf(item?.hintAr.orEmpty()) }
    var severity by remember(item?.id) { mutableStateOf(item?.severity ?: FaultSeverity.MEDIUM) }
    val valid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم البند") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(hint, { hint = it }, label = { Text("طريقة/ملاحظة الفحص") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                Text("الأهمية عند ظهور مشكلة", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(FaultSeverity.LOW, FaultSeverity.MEDIUM, FaultSeverity.HIGH, FaultSeverity.CRITICAL).forEach { level ->
                        FilterChip(selected = severity == level, onClick = { severity = level }, label = { Text(severityLabel(level)) })
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                val result = if (item == null) {
                    InspectionCatalog.newCustomItem(name, hint, severity, mode)
                } else {
                    item.copy(titleAr = name.trim(), hintAr = hint.trim(), severity = severity, modes = setOf(mode))
                }
                onSave(result)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun hasOpenInspectionFault(item: InspectionTemplateItem, faults: List<FaultRecordEntity>): Boolean {
    val marker = "InspectionItemId=${item.id}"
    val normalizedTitle = normalizeInspectionText(item.titleAr)
    return faults.any { fault ->
        !fault.isDeleted && fault.status != FaultStatus.RESOLVED && fault.status != FaultStatus.CLOSED &&
            (fault.notes.orEmpty().contains(marker) || normalizeInspectionText(fault.symptomAr).contains(normalizedTitle))
    }
}

private fun normalizeInspectionText(value: String): String = value.lowercase()
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
    .replace(Regex("[^\\p{L}\\p{N}]+"), "")

private fun severityLabel(value: FaultSeverity): String = when (value) {
    FaultSeverity.LOW -> "منخفضة"
    FaultSeverity.MEDIUM -> "متوسطة"
    FaultSeverity.HIGH -> "عالية"
    FaultSeverity.CRITICAL -> "حرجة"
}

private fun inspectionIcon(id: String): ImageVector = when {
    "tire" in id -> Icons.Default.TireRepair
    "brake" in id -> Icons.Default.Security
    "oil" in id -> Icons.Default.OilBarrel
    "cool" in id -> Icons.Default.DeviceThermostat
    "light" in id -> Icons.Default.Lightbulb
    "battery" in id -> Icons.Default.BatteryChargingFull
    "steer" in id -> Icons.Default.Navigation
    "suspension" in id -> Icons.Default.AirlineSeatReclineExtra
    "warning" in id -> Icons.Default.WarningAmber
    "leak" in id -> Icons.Default.WaterDrop
    "chain" in id || "belt" in id -> Icons.Default.Settings
    "trip" in id || "spare" in id -> Icons.Default.Route
    else -> Icons.AutoMirrored.Filled.FactCheck
}
