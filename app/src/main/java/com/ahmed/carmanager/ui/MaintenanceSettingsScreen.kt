@file:Suppress("DEPRECATION")

package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck

import androidx.compose.material.icons.automirrored.filled.FactCheck

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.ReminderRule
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenanceCatalog
import com.ahmed.carmanager.data.maintenance.MaintenancePreset
import com.ahmed.carmanager.data.maintenance.MaintenanceTemplate
import com.ahmed.carmanager.data.maintenance.MaintenanceUrgency
import com.ahmed.carmanager.data.repository.MaintenancePlanInput
import java.util.Calendar

private enum class MaintenanceViewFilter(val label: String) {
    ALL("الكل"), DUE("مستحقة"), SOON("قريبًا"), ACTIVE("نشطة"), DISABLED("متوقفة")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceSettingsScreen(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    onInstallPreset: (MaintenancePreset) -> Unit,
    onAddPlan: (MaintenancePlanInput) -> Unit,
    onUpdatePlan: (String, MaintenancePlanInput) -> Unit,
    onSetActive: (String, Boolean) -> Unit,
    onDeletePlan: (String) -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أو أضف واحدة أولًا.", Icons.AutoMirrored.Filled.FactCheck)
        return
    }

    var editing by remember { mutableStateOf<MaintenancePlanEntity?>(null) }
    var deleting by remember { mutableStateOf<MaintenancePlanEntity?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(MaintenanceViewFilter.ALL) }

    val preset = remember(vehicle.vehicleId, vehicle.year, vehicle.brand, vehicle.model) { MaintenanceCatalog.presetFor(vehicle) }
    val statusMap = remember(vehicle.currentOdometerKm, plans) { plans.associateWith { MaintenanceAdvisor.statusFor(vehicle, it) } }
    val activeCount = plans.count { it.isActive }
    val overdueCount = plans.count { it.isActive && statusMap[it]?.urgency == MaintenanceUrgency.OVERDUE }
    val soonCount = plans.count { it.isActive && statusMap[it]?.urgency == MaintenanceUrgency.DUE_SOON }

    val visiblePlans = remember(plans, statusMap, search, filter) {
        plans.filter { plan ->
            val textMatches = search.isBlank() || plan.titleAr.contains(search, true) || plan.category.contains(search, true)
            val urgency = statusMap[plan]?.urgency
            val filterMatches = when (filter) {
                MaintenanceViewFilter.ALL -> true
                MaintenanceViewFilter.DUE -> plan.isActive && urgency == MaintenanceUrgency.OVERDUE
                MaintenanceViewFilter.SOON -> plan.isActive && urgency == MaintenanceUrgency.DUE_SOON
                MaintenanceViewFilter.ACTIVE -> plan.isActive
                MaintenanceViewFilter.DISABLED -> !plan.isActive
            }
            textMatches && filterMatches
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "خطة الصيانة",
                subtitle = "${vehicle.displayName ?: vehicle.model} • ${plans.size} بند • عداد ${formatKm(vehicle.currentOdometerKm)} كم",
                icon = CMIcons.Maintenance
            )
        }
        item {
            AutomotiveActionCard(
                title = "إضافة بند صيانة",
                subtitle = "موعد وتكرار وسعر وأولوية",
                icon = CMIcons.Add,
                tone = AutoTone.CORAL,
                modifier = Modifier.fillMaxWidth(),
                filled = true,
                onClick = { showNew = true }
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AutomotiveMetricCard("نشطة", activeCount.toString(), Icons.Default.TaskAlt, AutoTone.GREEN, Modifier.weight(1f))
                AutomotiveMetricCard("قريبًا", soonCount.toString(), Icons.Default.Schedule, AutoTone.AMBER, Modifier.weight(1f))
                AutomotiveMetricCard("مستحقة", overdueCount.toString(), Icons.Default.WarningAmber, if (overdueCount > 0) AutoTone.RED else AutoTone.GRAPHITE, Modifier.weight(1f))
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = autoToneColors(AutoTone.TEAL).soft
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { onInstallPreset(preset) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, "استكمال القائمة", Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(MaintenanceCatalog.titleFor(preset), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (plans.isEmpty()) "إنشاء البنود المقترحة بضغطة واحدة" else "استكمال البنود الناقصة فقط دون استبدال تعديلاتك",
                            style = MaterialTheme.typography.labelSmall,
                            color = autoToneColors(AutoTone.TEAL).onSoft
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(19.dp)) },
                trailingIcon = {
                    if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "مسح", Modifier.size(18.dp)) }
                },
                placeholder = { Text("بحث في بنود الصيانة") }
            )
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MaintenanceViewFilter.entries.forEach { item ->
                    FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
                }
            }
        }

        if (visiblePlans.isEmpty()) {
            item { EmptyState("لا توجد نتائج", if (plans.isEmpty()) "ابدأ بالقائمة المقترحة أو أضف بندًا." else "غيّر البحث أو الفلتر.", Icons.Default.SearchOff) }
        } else {
            items(visiblePlans, key = { it.id }) { plan ->
                CompactSettingsPlanCard(
                    vehicle = vehicle,
                    plan = plan,
                    onEdit = { editing = plan },
                    onDelete = { deleting = plan },
                    onSetActive = { onSetActive(plan.id, it) }
                )
            }
        }

        item { Spacer(Modifier.height(92.dp)) }
    }

    if (showNew) {
        MaintenancePlanEditorSheet(
            vehicle = vehicle,
            plan = null,
            onDismiss = { showNew = false },
            onSave = { onAddPlan(it); showNew = false }
        )
    }
    editing?.let { plan ->
        MaintenancePlanEditorSheet(
            vehicle = vehicle,
            plan = plan,
            onDismiss = { editing = null },
            onSave = { onUpdatePlan(plan.id, it); editing = null }
        )
    }
    deleting?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("حذف بند الصيانة؟") },
            text = {
                Text(
                    "سيتم حذف «${plan.titleAr}» من خطة الصيانة والحزمة القادمة والتنبيهات المرتبطة بالخطة فقط. " +
                        "سجل الصيانة السابق سيبقى محفوظًا بالكامل، ولن تُحذف أي عملية صيانة تاريخية أو فاتورة أو مرفق تابع لها."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePlan(plan.id)
                        deleting = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("حذف من الخطة")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun SettingsSummaryTile(label: String, value: Int, warning: Boolean, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(value.toString(), fontWeight = FontWeight.Bold, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CompactSettingsPlanCard(
    vehicle: VehicleEntity,
    plan: MaintenancePlanEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: (Boolean) -> Unit
) {
    var expanded by remember(plan.id) { mutableStateOf(false) }
    val status = remember(vehicle.currentOdometerKm, plan) { MaintenanceAdvisor.statusFor(vehicle, plan) }

    ElevatedCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (plan.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.DeleteOutline, "حذف", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Edit, "تعديل", Modifier.size(18.dp)) }
                Switch(checked = plan.isActive, onCheckedChange = onSetActive, modifier = Modifier.height(32.dp))
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(plan.titleAr, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${plan.category} • ${plan.reminderRule.arLabel()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(5.dp))
                SettingsUrgencyBadge(status.urgency)
            }

            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                plan.estimatedCost?.let { Text(formatMoney(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.weight(1f))
                Text(
                    status.resolvedNextDueOdometerKm?.let { "القادم ${formatKm(it)} كم" }
                        ?: status.resolvedNextDueDate?.let { "القادم ${formatDate(it)}" }
                        ?: plan.intervalKm?.let { "كل ${formatKm(it)} كم" }
                        ?: plan.intervalMonths?.let { "كل $it شهر" }
                        ?: "حسب الحالة",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                plan.lastServiceOdometerKm?.let { SettingsDetail("آخر خدمة", "${formatKm(it)} كم") }
                plan.lastServiceDate?.let { SettingsDetail("تاريخ آخر خدمة", formatDate(it)) }
                status.remainingKm?.let { SettingsDetail("المتبقي", "${formatKm(it.coerceAtLeast(0.0))} كم") }
                status.overdueByKm?.let { SettingsDetail("التأخير", "${formatKm(it)} كم") }
                status.remainingDays?.let { SettingsDetail("المتبقي زمنيًا", "${it.coerceAtLeast(0)} يوم") }
                if (!plan.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(plan.notes!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsUrgencyBadge(urgency: MaintenanceUrgency) {
    val label = when (urgency) {
        MaintenanceUrgency.OVERDUE -> "مستحقة"
        MaintenanceUrgency.DUE_SOON -> "قريبًا"
        MaintenanceUrgency.UPCOMING -> "قادمة"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (urgency == MaintenanceUrgency.OVERDUE) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (urgency == MaintenanceUrgency.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(value, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenancePlanEditorSheet(
    vehicle: VehicleEntity,
    plan: MaintenancePlanEntity?,
    onDismiss: () -> Unit,
    onSave: (MaintenancePlanInput) -> Unit
) {
    val templates = remember(vehicle.vehicleId, vehicle.brand, vehicle.model, vehicle.year) {
        MaintenanceCatalog.templatesFor(vehicle, MaintenanceCatalog.presetFor(vehicle))
    }

    var templateMenu by remember { mutableStateOf(false) }
    var selectedTemplate by remember(plan?.id) { mutableStateOf<MaintenanceTemplate?>(null) }
    var customMode by remember(plan?.id) { mutableStateOf(plan != null) }
    var title by remember(plan?.id) { mutableStateOf(plan?.titleAr.orEmpty()) }
    var category by remember(plan?.id) { mutableStateOf(plan?.category ?: "دورية") }
    var reminderRule by remember(plan?.id) { mutableStateOf(plan?.reminderRule ?: ReminderRule.WHICHEVER_COMES_FIRST) }
    var intervalKm by remember(plan?.id) { mutableStateOf(plan?.intervalKm?.let(::settingsEditableNumber).orEmpty()) }
    var intervalMonths by remember(plan?.id) { mutableStateOf(plan?.intervalMonths?.toString().orEmpty()) }
    var lastKm by remember(plan?.id) { mutableStateOf(plan?.lastServiceOdometerKm?.let(::settingsEditableNumber).orEmpty()) }
    var lastDate by remember(plan?.id) { mutableStateOf(plan?.lastServiceDate) }
    var nextKm by remember(plan?.id) { mutableStateOf(plan?.nextDueOdometerKm?.let(::settingsEditableNumber).orEmpty()) }
    var nextDate by remember(plan?.id) { mutableStateOf(plan?.nextDueDate) }
    var cost by remember(plan?.id) { mutableStateOf(plan?.estimatedCost?.let(::settingsEditableNumber).orEmpty()) }
    var warningKm by remember(plan?.id) { mutableStateOf(plan?.warningBeforeKm?.let(::settingsEditableNumber) ?: "1000") }
    var warningDays by remember(plan?.id) { mutableStateOf(plan?.warningBeforeDays?.toString() ?: "30") }
    var priority by remember(plan?.id) { mutableIntStateOf(plan?.priority ?: 0) }
    var notes by remember(plan?.id) { mutableStateOf(plan?.notes.orEmpty()) }

    fun applyTemplate(template: MaintenanceTemplate) {
        selectedTemplate = template
        customMode = false
        title = template.titleAr
        category = template.category
        intervalKm = template.intervalKm?.let(::settingsEditableNumber).orEmpty()
        intervalMonths = template.intervalMonths?.toString().orEmpty()
        cost = template.estimatedCost?.let(::settingsEditableNumber).orEmpty()
        lastKm = template.lastServiceOdometerKm?.let(::settingsEditableNumber).orEmpty()
        nextKm = template.nextDueOdometerKm?.let(::settingsEditableNumber).orEmpty()
        notes = template.notes.orEmpty()
        priority = template.priority
        reminderRule = when {
            template.intervalKm != null && template.intervalMonths != null -> ReminderRule.WHICHEVER_COMES_FIRST
            template.intervalKm != null -> ReminderRule.ODOMETER_ONLY
            else -> ReminderRule.DATE_ONLY
        }
    }

    val kmValue = intervalKm.toDoubleOrNull()?.takeIf { it > 0 }
    val monthValue = intervalMonths.toIntOrNull()?.takeIf { it > 0 }
    val valid = title.isNotBlank() && when (reminderRule) {
        ReminderRule.ODOMETER_ONLY -> kmValue != null
        ReminderRule.DATE_ONLY -> monthValue != null || nextDate != null
        ReminderRule.WHICHEVER_COMES_FIRST -> kmValue != null || monthValue != null
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.End
        ) {
            Text(if (plan == null) "إضافة بند صيانة" else "تعديل بند الصيانة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("أدخل التاريخ أو العداد الحقيقي فقط عندما يكون معروفًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            if (plan == null) {
                ExposedDropdownMenuBox(expanded = templateMenu, onExpandedChange = { templateMenu = !templateMenu }) {
                    OutlinedTextField(
                        value = if (customMode) "بند مخصص" else selectedTemplate?.titleAr ?: "اختر بند الصيانة",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("قائمة البنود") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(templateMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = templateMenu, onDismissRequest = { templateMenu = false }) {
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(template.titleAr)
                                        Text(template.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { applyTemplate(template); templateMenu = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("بند مخصص") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                selectedTemplate = null
                                customMode = true
                                title = ""
                                category = "دورية"
                                intervalKm = ""
                                intervalMonths = ""
                                lastKm = ""
                                lastDate = null
                                nextKm = ""
                                nextDate = null
                                cost = ""
                                notes = ""
                                priority = 0
                                templateMenu = false
                            }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (plan != null || customMode || selectedTemplate != null) {
                EditorGroup("البند") {
                    AppField(title, { title = it }, "اسم البند")
                    AppField(category, { category = it }, "التصنيف")
                }

                EditorGroup("الاستحقاق") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReminderRule.entries.forEach { rule ->
                            FilterChip(selected = reminderRule == rule, onClick = { reminderRule = rule }, label = { Text(rule.arLabel()) })
                        }
                    }
                    if (reminderRule != ReminderRule.DATE_ONLY) {
                        NumberField(intervalKm, { intervalKm = numericInput(it) }, "التكرار كل كم")
                    }
                    if (reminderRule != ReminderRule.ODOMETER_ONLY) {
                        NumberField(intervalMonths, { intervalMonths = numericInput(it).substringBefore('.') }, "التكرار كل شهر", KeyboardType.Number)
                    }
                }

                EditorGroup("آخر خدمة") {
                    if (reminderRule != ReminderRule.DATE_ONLY) NumberField(lastKm, { lastKm = numericInput(it) }, "آخر خدمة عند عداد - اختياري")
                    if (reminderRule != ReminderRule.ODOMETER_ONLY) AppDateSelector("تاريخ آخر خدمة - اختياري", lastDate, allowClear = true) { lastDate = it }
                }

                EditorGroup("القادم") {
                    if (reminderRule != ReminderRule.DATE_ONLY) NumberField(nextKm, { nextKm = numericInput(it) }, "القادم عند عداد - اختياري")
                    if (reminderRule != ReminderRule.ODOMETER_ONLY) AppDateSelector("تاريخ القادم - اختياري", nextDate, allowClear = true) { nextDate = it }
                }

                EditorGroup("التكلفة والتنبيه") {
                    NumberField(cost, { cost = numericInput(it) }, "التكلفة التقديرية")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (reminderRule != ReminderRule.DATE_ONLY) NumberField(warningKm, { warningKm = numericInput(it) }, "قبل كم", modifier = Modifier.weight(1f))
                        if (reminderRule != ReminderRule.ODOMETER_ONLY) NumberField(warningDays, { warningDays = numericInput(it).substringBefore('.') }, "قبل أيام", KeyboardType.Number, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "عادية", 5 to "مهمة", 10 to "عالية").forEach { (value, label) ->
                            FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(label) })
                        }
                    }
                }

                EditorGroup("ملاحظات") {
                    OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, label = { Text("ملاحظات") })
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("إلغاء") }
                    Button(
                        enabled = valid,
                        onClick = {
                            onSave(
                                MaintenancePlanInput(
                                    titleAr = title.trim(),
                                    category = category.trim().ifBlank { "دورية" },
                                    intervalKm = if (reminderRule == ReminderRule.DATE_ONLY) null else kmValue,
                                    intervalMonths = if (reminderRule == ReminderRule.ODOMETER_ONLY) null else monthValue,
                                    reminderRule = reminderRule,
                                    estimatedCost = cost.toDoubleOrNull(),
                                    warningBeforeKm = if (reminderRule == ReminderRule.DATE_ONLY) null else warningKm.toDoubleOrNull(),
                                    warningBeforeDays = if (reminderRule == ReminderRule.ODOMETER_ONLY) null else warningDays.toIntOrNull(),
                                    notes = notes.trim().takeIf { it.isNotEmpty() },
                                    lastServiceOdometerKm = if (reminderRule == ReminderRule.DATE_ONLY) null else lastKm.toDoubleOrNull(),
                                    lastServiceDate = if (reminderRule == ReminderRule.ODOMETER_ONLY) null else lastDate,
                                    nextDueOdometerKm = if (reminderRule == ReminderRule.DATE_ONLY) null else nextKm.toDoubleOrNull(),
                                    nextDueDate = if (reminderRule == ReminderRule.ODOMETER_ONLY) null else nextDate,
                                    priority = priority
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("حفظ") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditorGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalAlignment = Alignment.End) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        content()
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
}

private fun ReminderRule.arLabel() = when (this) {
    ReminderRule.DATE_ONLY -> "تاريخ"
    ReminderRule.ODOMETER_ONLY -> "عداد"
    ReminderRule.WHICHEVER_COMES_FIRST -> "الأسبق"
}

private fun settingsEditableNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun addMonths(timestamp: Long, months: Int): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    add(Calendar.MONTH, months)
}.timeInMillis
