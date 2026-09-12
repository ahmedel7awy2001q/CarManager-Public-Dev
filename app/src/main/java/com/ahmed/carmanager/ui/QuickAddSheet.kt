package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.repository.*

private data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val tone: AutoTone,
    val onClick: () -> Unit
)

private enum class QuickEntry {
    FUEL, MAINTENANCE, INSPECTION, EXPENSE, ODOMETER, TRIP, REMINDER, DOCUMENT, FAULT
}

private val QUICK_GASOLINE_FUEL_TYPES = listOf(
    FuelType.GASOLINE_80,
    FuelType.GASOLINE_92,
    FuelType.GASOLINE_95
)

private fun FuelType.isQuickGasolineGrade(): Boolean = this in QUICK_GASOLINE_FUEL_TYPES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    vehicle: VehicleEntity?,
    onDismiss: () -> Unit,
    onFuel: () -> Unit,
    onMaintenance: () -> Unit,
    onExpense: () -> Unit,
    onTrip: () -> Unit,
    onReminder: () -> Unit,
    onDocument: () -> Unit,
    onFault: () -> Unit,
    onInspection: () -> Unit,
    onSetOdometer: (Double) -> Unit
) {
    val appViewModel: CarManagerViewModel = viewModel()
    val plans by appViewModel.maintenancePlans.collectAsStateWithLifecycle()
    var activeEntry by remember { mutableStateOf<QuickEntry?>(null) }

    fun saved() {
        activeEntry = null
        onDismiss()
    }

    if (activeEntry == null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(width = 32.dp, height = 4.dp) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("إضافة سريعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = vehicle?.let { "أضف السجل مباشرة إلى ${it.displayName ?: "${it.brand} ${it.model}"}." }
                        ?: "أضف مركبة أولًا لبدء تسجيل الحركات.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )

                val actions = listOf(
                    QuickAction("تموين", CMIcons.Fuel, AutoTone.BLUE) { activeEntry = QuickEntry.FUEL },
                    QuickAction("صيانة", CMIcons.Maintenance, AutoTone.CORAL) { activeEntry = QuickEntry.MAINTENANCE },
                    QuickAction("فحص سريع", CMIcons.Inspection, AutoTone.GREEN) { activeEntry = QuickEntry.INSPECTION },
                    QuickAction("مصروف", CMIcons.Expense, AutoTone.AMBER) { activeEntry = QuickEntry.EXPENSE },
                    QuickAction("عداد", CMIcons.Odometer, AutoTone.TEAL) { activeEntry = QuickEntry.ODOMETER },
                    QuickAction("مشوار", CMIcons.Trip, AutoTone.VIOLET) { activeEntry = QuickEntry.TRIP },
                    QuickAction("تذكير", CMIcons.Reminder, AutoTone.CORAL) { activeEntry = QuickEntry.REMINDER },
                    QuickAction("مستند", CMIcons.Document, AutoTone.BLUE) { activeEntry = QuickEntry.DOCUMENT },
                    QuickAction("عطل", CMIcons.Fault, AutoTone.RED) { activeEntry = QuickEntry.FAULT }
                )

                actions.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        rowItems.forEach { action ->
                            QuickActionCard(action, Modifier.weight(1f), enabled = vehicle != null)
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                ) {
                    Text(
                        "الضغط على أي بند يفتح نموذج الإدخال نفسه مباشرة؛ لا يتم نقلك إلى شاشة أخرى.",
                        Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    val selectedVehicle = vehicle
    if (selectedVehicle != null) {
        when (activeEntry) {
            QuickEntry.FUEL -> QuickFuelDialog(
                vehicle = selectedVehicle,
                onDismiss = { activeEntry = null },
                onSave = { appViewModel.addFuel(it); saved() }
            )
            QuickEntry.MAINTENANCE -> QuickMaintenanceDialog(
                vehicle = selectedVehicle,
                plans = plans.filter { !it.isDeleted && it.isActive },
                onDismiss = { activeEntry = null },
                onSave = { appViewModel.addMaintenanceRecord(it); saved() }
            )
            QuickEntry.INSPECTION -> QuickInspectionDialog(
                vehicle = selectedVehicle,
                onDismiss = { activeEntry = null },
                onOpenFullInspection = { activeEntry = null; onDismiss(); onInspection() },
                onSave = { appViewModel.addDocument(it); saved() }
            )
            QuickEntry.EXPENSE -> QuickExpenseDialog(
                vehicle = selectedVehicle,
                onDismiss = { activeEntry = null },
                onSave = { appViewModel.addExpense(it); saved() }
            )
            QuickEntry.ODOMETER -> QuickOdometerDialog(
                vehicle = selectedVehicle,
                onDismiss = { activeEntry = null },
                onSave = { onSetOdometer(it); saved() }
            )
            QuickEntry.TRIP -> QuickTripDialog(
                vehicle = selectedVehicle,
                onDismiss = { activeEntry = null },
                onSave = { appViewModel.addTrip(it); saved() }
            )
            QuickEntry.REMINDER -> QuickReminderDialog(
                vehicle = selectedVehicle,
                onDismiss = { activeEntry = null },
                onSave = { appViewModel.addReminder(it); saved() }
            )
            QuickEntry.DOCUMENT -> QuickDocumentDialog(
                onDismiss = { activeEntry = null },
                onSave = { appViewModel.addDocument(it); saved() }
            )
            QuickEntry.FAULT -> QuickFaultDialog(
                vehicle = selectedVehicle,
                onDismiss = { activeEntry = null },
                onSave = { appViewModel.addFault(it); saved() }
            )
            null -> Unit
        }
    }

    // Legacy navigation callbacks intentionally remain in the public signature for source compatibility.
    @Suppress("UNUSED_VARIABLE")
    val legacyCallbacks = listOf(onFuel, onMaintenance, onExpense, onTrip, onReminder, onDocument, onFault)
}

@Composable
private fun QuickActionCard(action: QuickAction, modifier: Modifier, enabled: Boolean) {
    val tone = autoToneColors(action.tone)
    Surface(
        onClick = action.onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(11.dp),
        color = tone.soft.copy(alpha = .56f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tone.strong.copy(alpha = .18f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .82f)) {
                Icon(action.icon, null, Modifier.padding(5.dp).size(14.dp), tint = tone.strong)
            }
            Spacer(Modifier.width(5.dp))
            Text(
                action.title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickFormColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun QuickFuelDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (FuelInput) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var km by remember(vehicle.vehicleId) { mutableStateOf(vehicle.currentOdometerKm.toString()) }
    var station by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(false) }
    var fuelType by remember(vehicle.vehicleId) { mutableStateOf(vehicle.fuelType) }
    val gasolineVehicle = vehicle.fuelType.isQuickGasolineGrade()
    val fuelChoices = remember(gasolineVehicle) {
        if (gasolineVehicle) QUICK_GASOLINE_FUEL_TYPES else FuelType.entries.toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة تموين سريع") },
        text = {
            QuickFormColumn {
                AppField(amount, { amount = numericInput(it) }, "المبلغ المدفوع")
                AppField(price, { price = numericInput(it) }, "سعر اللتر")
                EnumSelector(
                    if (gasolineVehicle) "نوع البنزين" else "نوع الوقود",
                    fuelChoices,
                    fuelType,
                    { fuelType = it }
                ) { it.arLabel() }
                if (gasolineVehicle && fuelType != vehicle.fuelType) {
                    Text(
                        "سيُسجل التموين كـ ${fuelType.arLabel()} بينما الوقود الأساسي في ملف السيارة هو ${vehicle.fuelType.arLabel()}. سجّل الدرجة التي تم استخدامها فعليًا.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.End
                    )
                }
                AppField(km, { km = numericInput(it) }, "العداد (كم)")
                AppField(station, { station = it }, "المحطة - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(full, { full = it })
                    Spacer(Modifier.width(7.dp))
                    Text("تفويلة كاملة")
                }
            }
        },
        confirmButton = {
            val valid = amount.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true &&
                price.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true &&
                km.toDoubleOrNull()?.let { it.isFinite() && it >= vehicle.currentOdometerKm } == true &&
                (!gasolineVehicle || fuelType.isQuickGasolineGrade())
            Button(enabled = valid, onClick = {
                onSave(FuelInput(amount.toDouble(), price.toDouble(), km.toDouble(), fuelType, station, full, notes = notes))
            }) { Text("حفظ التموين") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickMaintenanceDialog(
    vehicle: VehicleEntity,
    plans: List<MaintenancePlanEntity>,
    onDismiss: () -> Unit,
    onSave: (MaintenanceRecordInput) -> Unit
) {
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("صيانة") }
    var km by remember(vehicle.vehicleId) { mutableStateOf(vehicle.currentOdometerKm.toString()) }
    var cost by remember { mutableStateOf("") }
    var center by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId }
    val parsedKm = km.toDoubleOrNull()
    val kmValid = parsedKm != null && parsedKm >= 0.0 && parsedKm <= vehicle.currentOdometerKm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل صيانة مباشرة") },
        text = {
            QuickFormColumn {
                if (plans.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedPlan?.titleAr ?: "صيانة أخرى",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("ربط ببند من خطة الصيانة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("صيانة أخرى") }, onClick = { selectedPlanId = null; expanded = false })
                            plans.forEach { plan ->
                                DropdownMenuItem(text = { Text(plan.titleAr) }, onClick = {
                                    selectedPlanId = plan.id
                                    title = plan.titleAr
                                    category = plan.category
                                    expanded = false
                                })
                            }
                        }
                    }
                }
                AppField(title, { title = it }, "بند الصيانة")
                AppField(km, { km = numericInput(it) }, "العداد (كم)")
                if (parsedKm != null && parsedKm > vehicle.currentOdometerKm) {
                    Text(
                        "عداد الصيانة لا يتجاوز العداد الحالي ${formatKm(vehicle.currentOdometerKm)} كم. حدّث العداد أولًا من زر «عداد» إذا كانت القراءة الجديدة صحيحة.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.End
                    )
                } else {
                    Text(
                        "يمكن تسجيل صيانة تاريخية بعداد أقل من الحالي؛ لن يتم خفض عداد المركبة.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
                AppField(cost, { cost = numericInput(it) }, "التكلفة")
                AppField(center, { center = it }, "مركز الصيانة - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank() && kmValid && cost.toDoubleOrNull()?.let { it >= 0 } == true, onClick = {
                onSave(
                    MaintenanceRecordInput(
                        titleAr = title.trim(),
                        category = category,
                        odometerKm = parsedKm!!,
                        totalCost = cost.toDouble(),
                        planId = selectedPlanId,
                        serviceCenter = center.takeIf { it.isNotBlank() },
                        notes = notes.takeIf { it.isNotBlank() }
                    )
                )
            }) { Text("حفظ الصيانة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}

@Composable
private fun QuickExpenseDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (ExpenseInput) -> Unit) {
    var category by remember { mutableStateOf(ExpenseCategory.OTHER) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مصروف") },
        text = {
            QuickFormColumn {
                EnumSelector("التصنيف", ExpenseCategory.entries, category, { category = it }) { it.arLabel() }
                AppField(amount, { amount = numericInput(it) }, "المبلغ")
                AppField(description, { description = it }, "الوصف")
                AppField(merchant, { merchant = it }, "الجهة - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
            }
        },
        confirmButton = {
            Button(enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && description.isNotBlank(), onClick = {
                onSave(
                    ExpenseInput(
                        category = category,
                        amount = amount.toDouble(),
                        descriptionAr = description.trim(),
                        odometerKm = vehicle.currentOdometerKm,
                        merchant = merchant.takeIf { it.isNotBlank() },
                        notes = notes.takeIf { it.isNotBlank() }
                    )
                )
            }) { Text("حفظ المصروف") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}

@Composable
private fun QuickTripDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (TripInput) -> Unit) {
    var type by remember { mutableStateOf(TripType.PERSONAL) }
    var distance by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مشوار") },
        text = {
            QuickFormColumn {
                EnumSelector("نوع المشوار", TripType.entries, type, { type = it }) { it.arLabel() }
                AppField(distance, { distance = numericInput(it) }, "المسافة (كم)")
                AppField(start, { start = it }, "من - اختياري")
                AppField(end, { end = it }, "إلى - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                Text(
                    "المشوار اليدوي يرفع العداد بالمسافة المحفوظة، لذلك أدخل المسافة الفعلية فقط.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(enabled = distance.toDoubleOrNull()?.let { it > 0 } == true, onClick = {
                onSave(TripInput(type, distance.toDouble(), startAddress = start, endAddress = end, notes = notes))
            }) { Text("حفظ المشوار") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}

@Composable
private fun QuickReminderDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (ReminderInput) -> Unit) {
    var title by remember { mutableStateOf("") }
    var rule by remember { mutableStateOf(ReminderRule.WHICHEVER_COMES_FIRST) }
    var km by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة تذكير") },
        text = {
            QuickFormColumn {
                AppField(title, { title = it }, "العنوان")
                EnumSelector("طريقة التنبيه", ReminderRule.entries, rule, { rule = it }) { quickReminderRuleLabel(it) }
                if (rule != ReminderRule.DATE_ONLY) AppField(km, { km = numericInput(it) }, "العداد المستهدف")
                if (rule != ReminderRule.ODOMETER_ONLY) AppDateSelector("التاريخ", date, allowClear = true, onSelected = { date = it })
            }
        },
        confirmButton = {
            val parsedKm = km.toDoubleOrNull()
            val requirementValid = when (rule) {
                ReminderRule.DATE_ONLY -> date != null
                ReminderRule.ODOMETER_ONLY -> parsedKm?.let { it >= vehicle.currentOdometerKm } == true
                ReminderRule.WHICHEVER_COMES_FIRST -> date != null || parsedKm?.let { it >= vehicle.currentOdometerKm } == true
            }
            Button(enabled = title.isNotBlank() && requirementValid, onClick = {
                onSave(ReminderInput(title.trim(), rule, date, parsedKm?.takeIf { it >= vehicle.currentOdometerKm }))
            }) { Text("حفظ التذكير") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}

@Composable
private fun QuickDocumentDialog(onDismiss: () -> Unit, onSave: (DocumentInput) -> Unit) {
    var type by remember { mutableStateOf(DocumentType.OTHER) }
    var number by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var expiryDate by remember { mutableStateOf<Long?>(null) }
    var notes by remember { mutableStateOf("") }
    val now = System.currentTimeMillis()
    val issueValid = issueDate == null || issueDate!! <= now
    val expiryValid = issueDate == null || expiryDate == null || expiryDate!! >= issueDate!!
    val datesValid = issueValid && expiryValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مستند") },
        text = {
            QuickFormColumn {
                EnumSelector("نوع المستند", DocumentType.entries, type, { type = it }) { it.arLabel() }
                AppField(number, { number = it }, "الرقم - اختياري")
                AppDateSelector("تاريخ الإصدار", issueDate, allowClear = true, onSelected = { issueDate = it })
                AppDateSelector("تاريخ الانتهاء", expiryDate, allowClear = true, onSelected = { expiryDate = it })
                if (!issueValid) {
                    Text("تاريخ الإصدار لا يمكن أن يكون في المستقبل.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                } else if (!expiryValid) {
                    Text("تاريخ الانتهاء لا يمكن أن يسبق تاريخ الإصدار.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
            }
        },
        confirmButton = {
            Button(enabled = datesValid, onClick = {
                onSave(DocumentInput(type, number.takeIf { it.isNotBlank() }, issueDate, expiryDate, notes = notes.takeIf { it.isNotBlank() }))
            }) { Text("حفظ المستند") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}

@Composable
private fun QuickFaultDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (FaultInput) -> Unit) {
    var symptom by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(FaultSeverity.MEDIUM) }
    var cost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل عطل") },
        text = {
            QuickFormColumn {
                AppField(symptom, { symptom = it }, "وصف العطل")
                EnumSelector("الخطورة", FaultSeverity.entries, severity, { severity = it }) { it.arLabel() }
                AppField(cost, { cost = numericInput(it) }, "تكلفة متوقعة - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
            }
        },
        confirmButton = {
            Button(enabled = symptom.isNotBlank(), onClick = {
                onSave(FaultInput(symptom.trim(), severity = severity, odometerKm = vehicle.currentOdometerKm, repairCost = cost.toDoubleOrNull(), notes = notes))
            }) { Text("حفظ العطل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}

@Composable
private fun QuickInspectionDialog(
    vehicle: VehicleEntity,
    onDismiss: () -> Unit,
    onOpenFullInspection: () -> Unit,
    onSave: (DocumentInput) -> Unit
) {
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فحص سريع") },
        text = {
            QuickFormColumn {
                Text("سجل ملاحظة فحص مباشرة عند عداد ${formatKm(vehicle.currentOdometerKm)} كم.", style = MaterialTheme.typography.bodySmall)
                AppField(notes, { notes = it }, "نتيجة أو ملاحظات الفحص")
                TextButton(onClick = onOpenFullInspection, modifier = Modifier.fillMaxWidth()) { Text("فتح الفحص الشامل بدلًا من ذلك") }
            }
        },
        confirmButton = {
            Button(enabled = notes.isNotBlank(), onClick = {
                onSave(
                    DocumentInput(
                        type = DocumentType.INSPECTION,
                        issueDate = System.currentTimeMillis(),
                        notes = "فحص سريع عند ${formatKm(vehicle.currentOdometerKm)} كم — ${notes.trim()}"
                    )
                )
            }) { Text("حفظ الفحص") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}

@Composable
private fun QuickOdometerDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember(vehicle.vehicleId) {
        mutableStateOf(if (vehicle.currentOdometerKm % 1.0 == 0.0) vehicle.currentOdometerKm.toLong().toString() else vehicle.currentOdometerKm.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(CMIcons.Odometer, null, Modifier.size(22.dp)) },
        title = { Text("تحديث عداد المركبة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            QuickFormColumn {
                Text("العداد الحالي: ${formatKm(vehicle.currentOdometerKm)} كم", style = MaterialTheme.typography.bodyMedium)
                AppField(value, { value = numericInput(it) }, "العداد الجديد (كم)")
                Text("لن يقبل التطبيق قراءة أقل من آخر عداد مسجل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            val parsed = value.toDoubleOrNull()
            Button(enabled = parsed != null && parsed >= vehicle.currentOdometerKm, onClick = { parsed?.let(onSave) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}

private fun quickReminderRuleLabel(rule: ReminderRule): String = when (rule) {
    ReminderRule.DATE_ONLY -> "بالتاريخ"
    ReminderRule.ODOMETER_ONLY -> "بالعداد"
    ReminderRule.WHICHEVER_COMES_FIRST -> "أيهما أقرب"
}
