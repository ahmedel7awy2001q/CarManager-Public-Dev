package com.ahmed.carmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmed.carmanager.CarManagerApplication
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.repository.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

internal enum class MoreDestination(val label: String, val icon: ImageVector) {
    TRIPS("المشاوير", CMIcons.Trip),
    EXPENSES("المصاريف", CMIcons.Expense),
    PARTS("قطع الغيار", CMIcons.Parts),
    TIRES_BATTERY("الإطارات والبطارية", CMIcons.Tires),
    FAULTS("الأعطال", CMIcons.Fault),
    DOCUMENTS("المستندات", CMIcons.Document),
    REMINDERS("التنبيهات", CMIcons.Reminder),
    REPORTS("التقارير", CMIcons.Reports),
    GARAGE("الجراج", CMIcons.Garage)
}

@Composable
internal fun MoreHubScreen(onOpen: (MoreDestination) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item { SectionHeader("المزيد", "أدوات إدارة المركبة") }
        items(MoreDestination.entries) { item ->
            ElevatedCard(onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChevronLeft, null, Modifier.size(18.dp))
                    Spacer(Modifier.weight(1f))
                    Text(item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(item.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun TripsScreen(vehicle: VehicleEntity?, trips: List<TripEntity>, onAdd: (TripInput) -> Unit, appViewModel: CarManagerViewModel = viewModel()) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا.", Icons.Default.Route)
        return
    }
    var show by remember { mutableStateOf(false) }
    var selectedTripId by remember { mutableStateOf<String?>(null) }
    var showPricingAssistant by remember { mutableStateOf(false) }
    var editingTrip by remember { mutableStateOf<TripEntity?>(null) }
    var deletingTrip by remember { mutableStateOf<TripEntity?>(null) }
    val active = trips.filter { !it.isDeleted }
    val distance = active.sumOf { it.distanceKm }

    LazyColumn(
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "المشاوير",
                subtitle = "${vehicle.displayName ?: vehicle.model} • شخصي، عمل، سفر، خدمة ومسارات GPS",
                icon = CMIcons.Trip
            )
        }
        item {
            AutomotiveActionCard(
                title = "إضافة مشوار يدوي",
                subtitle = "أو ابدأ التتبع المباشر من مركز GPS",
                icon = CMIcons.Trip,
                tone = AutoTone.VIOLET,
                modifier = Modifier.fillMaxWidth(),
                filled = true,
                onClick = { show = true }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomotiveMetricCard("عدد المشاوير", active.size.toString(), CMIcons.Trip, AutoTone.VIOLET, Modifier.weight(1f))
                AutomotiveMetricCard("إجمالي المسافة", if (distance > 0) "${formatKm(distance)} كم" else "—", CMIcons.Odometer, AutoTone.BLUE, Modifier.weight(1f))
            }
        }
        item {
            AutomotiveActionCard(
                title = "مساعد تسعير المشوار",
                subtitle = "المسار، الركاب، الانتظار، التكلفة الحقيقية والسعر المقترح",
                icon = CMIcons.Pricing,
                tone = AutoTone.TEAL,
                modifier = Modifier.fillMaxWidth(),
                filled = true,
                onClick = { showPricingAssistant = true }
            )
        }
        if (active.isEmpty()) {
            item { EmptyState("لا توجد مشاوير", "ابدأ تتبع مشوار من شاشة GPS أو أضف مشوارًا يدويًا.", Icons.Default.Route) }
        } else {
            items(active, key = { it.id }) { trip ->
                ElevatedCard(
                    onClick = { selectedTripId = if (selectedTripId == trip.id) null else trip.id },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${formatKm(trip.distanceKm)} كم", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                            Text(trip.tripType.arLabel(), fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { deletingTrip = trip }) {
                                Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(4.dp))
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { editingTrip = trip }) {
                                Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تعديل المشوار")
                            }
                        }
                        Text(formatDate(trip.startTime), style = MaterialTheme.typography.labelMedium)
                        if (!trip.startAddress.isNullOrBlank() || !trip.endAddress.isNullOrBlank()) {
                            Text("${trip.startAddress ?: "—"} ← ${trip.endAddress ?: "—"}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        }
                        trip.startOdometerKm?.let { start ->
                            trip.endOdometerKm?.let { end -> Text("العداد: ${formatKm(start)} ← ${formatKm(end)} كم", style = MaterialTheme.typography.bodySmall) }
                        }
                        trip.fuelCost?.let { Text("وقود: ${formatMoney(it)}", style = MaterialTheme.typography.bodySmall) }
                        trip.estimatedOperatingCost?.let { Text("تشغيل: ${formatMoney(it)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (selectedTripId == trip.id) {
                    TripDetailsCard(trip = trip, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    if (show) TripDialog(vehicle, { show = false }) { onAdd(it); show = false }
    if (showPricingAssistant) {
        TripPricingAssistantSheet(vehicle = vehicle, onDismiss = { showPricingAssistant = false })
    }
    editingTrip?.let { trip ->
        TripEditDialog(
            vehicle = vehicle,
            trip = trip,
            onDismiss = { editingTrip = null },
            onSave = { input ->
                appViewModel.updateTripRecord(trip, input)
                editingTrip = null
            }
        )
    }
    deletingTrip?.let { trip ->
        AlertDialog(
            onDismissRequest = { deletingTrip = null },
            title = { Text("حذف المشوار؟") },
            text = { Text("سيتم إخفاء هذا المشوار من السجل بأمان. لن يتم إرجاع العداد للخلف ولن تُحذف قراءات GPS الخام المرتبطة بالمركبة.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTripId = null
                        appViewModel.deleteTripRecord(trip)
                        deletingTrip = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف المشوار") }
            },
            dismissButton = { TextButton(onClick = { deletingTrip = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun TripDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (TripInput) -> Unit) {
    var type by remember { mutableStateOf(TripType.PERSONAL) }
    var distance by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var fuelCost by remember { mutableStateOf("") }
    var totalCost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val d = distance.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مشوار") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EnumSelector("نوع المشوار", TripType.entries, type, { type = it }) { it.arLabel() }
                AppField(distance, { distance = numericInput(it) }, "المسافة (كم)")
                AppField(start, { start = it }, "من - اختياري")
                AppField(end, { end = it }, "إلى - اختياري")
                AppField(fuelCost, { fuelCost = numericInput(it) }, "تكلفة الوقود - اختياري")
                AppField(totalCost, { totalCost = numericInput(it) }, "تكلفة التشغيل - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                d?.takeIf { it > 0 }?.let {
                    Text("عداد النهاية المتوقع: ${formatKm(vehicle.currentOdometerKm + it)} كم", style = MaterialTheme.typography.bodySmall)
                }
                Text("حفظ المشوار اليدوي يرفع العداد بمسافة المشوار؛ لا يُستخدم متوسط أو تقدير زمني لتغيير العداد.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = d?.let { it > 0 } == true,
                onClick = {
                    onSave(
                        TripInput(
                            tripType = type,
                            distanceKm = d!!,
                            startAddress = start,
                            endAddress = end,
                            fuelCost = fuelCost.toDoubleOrNull(),
                            operatingCost = totalCost.toDoubleOrNull(),
                            notes = notes
                        )
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** Legacy screen retained for compatibility. Main navigation uses ProfessionalExpensesScreen. */
@Composable
fun ExpensesScreen(vehicle: VehicleEntity?, expenses: List<ExpenseEntity>, onAdd: (ExpenseInput) -> Unit) {
    ProfessionalExpensesScreen(vehicle, expenses, onAdd, onMessage = {})
}

/** Legacy screen retained for compatibility. Main navigation uses ProfessionalPartsScreen. */
@Composable
fun PartsScreen(vehicle: VehicleEntity?, parts: List<PartEntity>, onAdd: (PartInput) -> Unit) {
    ProfessionalPartsScreen(vehicle, parts, onAdd)
}

/** Legacy screen retained for compatibility. Main navigation uses ProfessionalTiresBatteryScreen. */
@Composable
fun TiresBatteryScreen(
    vehicle: VehicleEntity?,
    tires: List<TireEntity>,
    batteries: List<BatteryRecordEntity>,
    onAddTire: (TireInput) -> Unit,
    onAddBattery: (BatteryInput) -> Unit
) {
    ProfessionalTiresBatteryScreen(vehicle, tires, batteries, onAddTire, onAddBattery)
}

@Composable
fun FaultsScreen(
    vehicle: VehicleEntity?,
    faults: List<FaultRecordEntity>,
    onAdd: (FaultInput) -> Unit,
    onMessage: (String) -> Unit = {}
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا.", Icons.Default.Warning)
        return
    }

    val context = LocalContext.current
    val app = context.applicationContext as CarManagerApplication
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var statusTarget by remember { mutableStateOf<FaultRecordEntity?>(null) }
    val active = faults.filter { !it.isDeleted }.sortedWith(compareBy<FaultRecordEntity> { faultStatusRank(it.status) }.thenByDescending { it.reportedDate })
    val openCount = active.count { it.status !in setOf(FaultStatus.RESOLVED, FaultStatus.CLOSED) }

    LazyColumn(
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "الأعطال والإصلاحات",
                subtitle = "من العرض الأول حتى التشخيص والإصلاح والإغلاق",
                icon = CMIcons.Fault
            )
        }
        item {
            AutomotiveActionCard(
                title = "تسجيل عطل",
                subtitle = "سجل العرض والتشخيص والتكلفة بدون فقد التاريخ",
                icon = CMIcons.Fault,
                tone = AutoTone.RED,
                modifier = Modifier.fillMaxWidth(),
                filled = true,
                onClick = { showAdd = true }
            )
        }
        item {
            val criticalOpen = active.count { it.status !in setOf(FaultStatus.RESOLVED, FaultStatus.CLOSED) && it.severity == FaultSeverity.CRITICAL }
            val resolvedCount = active.count { it.status in setOf(FaultStatus.RESOLVED, FaultStatus.CLOSED) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomotiveMetricCard("مفتوح", openCount.toString(), CMIcons.Attention, if (openCount > 0) AutoTone.RED else AutoTone.GREEN, Modifier.weight(1f))
                AutomotiveMetricCard("حرج", criticalOpen.toString(), CMIcons.Fault, if (criticalOpen > 0) AutoTone.RED else AutoTone.GRAPHITE, Modifier.weight(1f))
                AutomotiveMetricCard("تم حله", resolvedCount.toString(), Icons.Default.TaskAlt, AutoTone.GREEN, Modifier.weight(1f))
            }
        }
        if (active.isEmpty()) {
            item { EmptyState("لا توجد أعطال مسجلة", "سجل الأعراض والتشخيص والتكلفة، ثم حدّث الحالة حتى الإغلاق مع بقاء التاريخ كاملًا.", Icons.Default.Verified) }
        } else {
            items(active, key = { it.id }) { fault ->
                FaultCard(fault = fault, onStatus = { statusTarget = fault })
            }
        }
    }

    if (showAdd) FaultDialog(vehicle, { showAdd = false }) { onAdd(it); showAdd = false }

    statusTarget?.let { fault ->
        FaultStatusDialog(
            fault = fault,
            onDismiss = { statusTarget = null },
            onSave = { status ->
                statusTarget = null
                scope.launch {
                    when (val result = app.container.faultStatusStore.updateStatus(fault, status)) {
                        is VehicleRepositoryResult.Success -> {
                            app.container.cloudBackupManager.uploadLatest()
                            onMessage("تم تحديث حالة العطل إلى ${faultStatusLabel(status)} مع الاحتفاظ بالسجل كاملًا.")
                        }
                        is VehicleRepositoryResult.Error -> onMessage(result.messageAr)
                    }
                }
            }
        )
    }
}

@Composable
private fun FaultCard(fault: FaultRecordEntity, onStatus: () -> Unit) {
    val resolved = fault.status == FaultStatus.RESOLVED || fault.status == FaultStatus.CLOSED
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        colors = if (fault.severity == FaultSeverity.CRITICAL && !resolved) {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else CardDefaults.elevatedCardColors()
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = onStatus, label = { Text(faultStatusLabel(fault.status)) })
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(fault.symptomAr, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("${fault.severity.arLabel()} • ${formatDate(fault.reportedDate)}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            FaultRepairJourney(fault.status)
            Spacer(Modifier.height(8.dp))
            fault.odometerKm?.let { Text("العداد: ${formatKm(it)} كم", style = MaterialTheme.typography.bodySmall) }
            fault.diagnosisAr?.takeIf { it.isNotBlank() }?.let {
                Surface(shape = RoundedCornerShape(12.dp), color = autoToneColors(AutoTone.VIOLET).soft) {
                    Text("التشخيص: $it", Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), style = MaterialTheme.typography.bodySmall, color = autoToneColors(AutoTone.VIOLET).onSoft, textAlign = TextAlign.End)
                }
            }
            fault.repairCost?.let { Text("تكلفة الإصلاح: ${formatMoney(it)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = autoToneColors(AutoTone.AMBER).strong) }
            fault.resolvedDate?.let { Text("تاريخ الحل: ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = autoToneColors(AutoTone.GREEN).strong) }
            fault.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End) }
        }
    }
}

@Composable
private fun FaultRepairJourney(status: FaultStatus) {
    val stages = listOf(
        FaultStatus.OPEN to "مُبلغ",
        FaultStatus.DIAGNOSED to "تشخيص",
        FaultStatus.IN_REPAIR to "إصلاح",
        FaultStatus.RESOLVED to "تم الحل"
    )
    val effective = if (status == FaultStatus.CLOSED) FaultStatus.RESOLVED else status
    val currentIndex = when (effective) {
        FaultStatus.OPEN -> 0
        FaultStatus.DIAGNOSED -> 1
        FaultStatus.IN_REPAIR -> 2
        FaultStatus.RESOLVED, FaultStatus.CLOSED -> 3
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        stages.forEachIndexed { index, (_, label) ->
            val completed = index <= currentIndex
            val tone = autoToneColors(if (completed) AutoTone.TEAL else AutoTone.GRAPHITE)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(if (index == currentIndex) 13.dp else 10.dp)
                        .clip(CircleShape)
                        .background(if (completed) tone.strong else tone.soft)
                )
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (completed) tone.strong else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (index < stages.lastIndex) {
                Box(
                    Modifier.weight(1f).padding(horizontal = 5.dp).height(2.dp)
                        .clip(CircleShape)
                        .background(if (index < currentIndex) autoToneColors(AutoTone.TEAL).strong else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun FaultDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (FaultInput) -> Unit) {
    var symptom by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(FaultSeverity.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل عطل") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EnumSelector("الخطورة", FaultSeverity.entries, severity, { severity = it }) { it.arLabel() }
                AppField(symptom, { symptom = it }, "وصف العطل")
                AppField(diagnosis, { diagnosis = it }, "التشخيص - اختياري")
                AppField(cost, { cost = numericInput(it) }, "تكلفة الإصلاح - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                Text("سجل العطل يبقى محفوظًا حتى بعد الحل؛ تغيير الحالة لا يحذف التاريخ.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = symptom.isNotBlank(),
                onClick = {
                    onSave(
                        FaultInput(
                            symptomAr = symptom,
                            diagnosisAr = diagnosis,
                            severity = severity,
                            odometerKm = vehicle.currentOdometerKm,
                            repairCost = cost.toDoubleOrNull(),
                            notes = notes
                        )
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun FaultStatusDialog(fault: FaultRecordEntity, onDismiss: () -> Unit, onSave: (FaultStatus) -> Unit) {
    var selected by remember(fault.id) { mutableStateOf(fault.status) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حالة العطل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FaultStatus.entries.forEach { status ->
                    Surface(
                        onClick = { selected = status },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected == status) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected == status, onClick = { selected = status })
                            Spacer(Modifier.weight(1f))
                            Text(faultStatusLabel(status), fontWeight = if (selected == status) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Text("الحل/الإغلاق يسجل تاريخ الحل ولا يحذف العطل. إعادة الفتح تمسح تاريخ الحل الحالي فقط وتحافظ على بقية البيانات.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(enabled = selected != fault.status, onClick = { onSave(selected) }) { Text("حفظ الحالة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** Legacy screen retained for compatibility. Main navigation uses ProfessionalDocumentsScreen. */
@Composable
fun DocumentsScreen(vehicle: VehicleEntity?, documents: List<VehicleDocumentEntity>, onAdd: (DocumentInput) -> Unit) {
    ProfessionalDocumentsScreen(vehicle, documents, onAdd, onMessage = {})
}

@Composable
fun RemindersScreen(
    vehicle: VehicleEntity?,
    reminders: List<ReminderEntity>,
    onAdd: (ReminderInput) -> Unit,
    onComplete: (ReminderEntity) -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا.", Icons.Default.NotificationsActive)
        return
    }
    var show by remember { mutableStateOf(false) }
    val active = reminders.filter { !it.isDeleted && !it.isCompleted }

    LazyColumn(
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "التنبيهات الذكية",
                subtitle = "مواعيد وعدادات مرتبطة بالمركبة بدل التذكير العشوائي",
                icon = CMIcons.Reminder
            )
        }
        item {
            AutomotiveActionCard(
                title = "إضافة تنبيه",
                subtitle = "بالتاريخ أو العداد أو أيهما أقرب",
                icon = CMIcons.Reminder,
                tone = AutoTone.CORAL,
                modifier = Modifier.fillMaxWidth(),
                filled = true,
                onClick = { show = true }
            )
        }
        item {
            AutomotiveMetricCard("تنبيهات مفتوحة", active.size.toString(), CMIcons.Reminder, if (active.isNotEmpty()) AutoTone.CORAL else AutoTone.GREEN, Modifier.fillMaxWidth())
        }
        if (active.isEmpty()) {
            item { EmptyState("لا توجد تنبيهات مفتوحة", "أضف تنبيهًا بالتاريخ أو العداد أو أيهما أقرب.", Icons.Default.NotificationsActive) }
        } else {
            items(active, key = { it.id }) { reminder ->
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = false, onCheckedChange = { checked -> if (checked) onComplete(reminder) })
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(reminder.titleAr, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            reminder.dueOdometerKm?.let { Text("عند ${formatKm(it)} كم", style = MaterialTheme.typography.bodySmall) }
                            reminder.dueDate?.let { Text("في ${formatDate(it)}", style = MaterialTheme.typography.bodySmall) }
                            Text(reminderRuleLabel(reminder.rule), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (show) ReminderDialog(vehicle, { show = false }) { onAdd(it); show = false }
}

@Composable
private fun ReminderDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (ReminderInput) -> Unit) {
    var title by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var rule by remember { mutableStateOf(ReminderRule.WHICHEVER_COMES_FIRST) }
    val parsedDate = parseCompactDate(date)
    val parsedKm = km.toDoubleOrNull()
    val requirementValid = when (rule) {
        ReminderRule.DATE_ONLY -> parsedDate != null
        ReminderRule.ODOMETER_ONLY -> parsedKm?.let { it >= vehicle.currentOdometerKm } == true
        ReminderRule.WHICHEVER_COMES_FIRST -> parsedDate != null || parsedKm?.let { it >= vehicle.currentOdometerKm } == true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة تنبيه") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 470.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EnumSelector("طريقة التنبيه", ReminderRule.entries, rule, { rule = it }, ::reminderRuleLabel)
                AppField(title, { title = it }, "العنوان")
                if (rule != ReminderRule.DATE_ONLY) AppField(km, { km = numericInput(it) }, "العداد المستهدف")
                if (rule != ReminderRule.ODOMETER_ONLY) {
                    AppField(date, { date = digitsOnlyDate(it) }, "التاريخ YYYYMMDD")
                    if (date.length == 8 && parsedDate == null) Text("التاريخ غير صالح.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && requirementValid,
                onClick = {
                    onSave(
                        ReminderInput(
                            titleAr = title,
                            rule = rule,
                            dueDate = parsedDate,
                            dueOdometerKm = parsedKm?.takeIf { it >= vehicle.currentOdometerKm }
                        )
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** Legacy reports entry retained for compatibility. Main navigation uses ProfessionalReportsScreen. */
@Composable
fun ReportsScreen(
    vehicle: VehicleEntity?,
    fuel: List<FuelRecordEntity>,
    maintenance: List<MaintenanceRecordEntity>,
    expenses: List<ExpenseEntity>,
    trips: List<TripEntity>
) {
    ProfessionalReportsScreen(vehicle, fuel, maintenance, expenses, trips, odometer = emptyList())
}

private fun faultStatusRank(status: FaultStatus): Int = when (status) {
    FaultStatus.OPEN -> 0
    FaultStatus.DIAGNOSED -> 1
    FaultStatus.IN_REPAIR -> 2
    FaultStatus.RESOLVED -> 3
    FaultStatus.CLOSED -> 4
}

private fun faultStatusLabel(status: FaultStatus): String = when (status) {
    FaultStatus.OPEN -> "مفتوح"
    FaultStatus.DIAGNOSED -> "تم التشخيص"
    FaultStatus.IN_REPAIR -> "قيد الإصلاح"
    FaultStatus.RESOLVED -> "تم الحل"
    FaultStatus.CLOSED -> "مغلق"
}

private fun reminderRuleLabel(rule: ReminderRule): String = when (rule) {
    ReminderRule.DATE_ONLY -> "بالتاريخ"
    ReminderRule.ODOMETER_ONLY -> "بالعداد"
    ReminderRule.WHICHEVER_COMES_FIRST -> "أيهما أقرب"
}

private fun digitsOnlyDate(value: String): String = value.map { ch ->
    when (ch.code) {
        in 0x0660..0x0669 -> ('0'.code + (ch.code - 0x0660)).toChar()
        in 0x06F0..0x06F9 -> ('0'.code + (ch.code - 0x06F0)).toChar()
        else -> ch
    }
}.joinToString("").filter(Char::isDigit).take(8)

private fun parseCompactDate(text: String): Long? {
    if (text.length != 8) return null
    return runCatching {
        SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { isLenient = false }.parse(text)?.time
    }.getOrNull()
}
