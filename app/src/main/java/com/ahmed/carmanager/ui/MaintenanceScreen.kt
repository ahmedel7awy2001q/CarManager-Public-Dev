@file:Suppress("DEPRECATION")

package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.EntityType
import com.ahmed.carmanager.data.local.model.FaultRecordEntity
import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.MaintenanceRecordEntity
import com.ahmed.carmanager.data.local.model.ReminderRule
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenanceCatalog
import com.ahmed.carmanager.data.maintenance.MaintenancePriorityEngine
import com.ahmed.carmanager.data.maintenance.MaintenancePriorityLevel
import com.ahmed.carmanager.data.maintenance.MaintenanceTemplate
import com.ahmed.carmanager.data.maintenance.MaintenanceUrgency
import com.ahmed.carmanager.data.maintenance.PrioritizedMaintenanceBundle
import com.ahmed.carmanager.data.maintenance.ProjectionConfidence
import com.ahmed.carmanager.data.repository.MaintenancePlanInput
import com.ahmed.carmanager.data.repository.MaintenanceRecordInput

@Composable
fun MaintenanceScreen(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    history: List<MaintenanceRecordEntity>,
    onAddPlan: (MaintenancePlanInput) -> Unit,
    onUpdatePlan: (String, MaintenancePlanInput) -> Unit,
    onAddRecord: (MaintenanceRecordInput) -> Unit,
    onMessage: (String) -> Unit = {},
    faults: List<FaultRecordEntity> = emptyList()
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة من الجراج أولًا.", Icons.Default.Build)
        return
    }

    var showPlan by remember { mutableStateOf(false) }
    var showRecord by remember { mutableStateOf(false) }
    var showBatchRecord by remember { mutableStateOf(false) }
    var showBaselineSetup by remember { mutableStateOf(false) }
    var baselinePlan by remember { mutableStateOf<MaintenancePlanEntity?>(null) }
    var attachmentRecord by remember { mutableStateOf<MaintenanceRecordEntity?>(null) }
    val bundle = remember(vehicle.currentOdometerKm, plans) { MaintenanceAdvisor.nextBundle(vehicle, plans) }
    val priorityBundle = remember(bundle, faults) { bundle?.let { MaintenancePriorityEngine.prioritizeBundle(it, faults) } }
    val statuses = remember(vehicle.currentOdometerKm, plans) { plans.associateWith { MaintenanceAdvisor.statusFor(vehicle, it) } }
    val overdue = statuses.count { it.value.urgency == MaintenanceUrgency.OVERDUE }
    val dueSoon = statuses.count { it.value.urgency == MaintenanceUrgency.DUE_SOON }
    val overduePlans = remember(plans, statuses) { plans.filter { statuses[it]?.urgency == MaintenanceUrgency.OVERDUE } }
    val dueSoonPlans = remember(plans, statuses) { plans.filter { statuses[it]?.urgency == MaintenanceUrgency.DUE_SOON } }
    val laterPlans = remember(plans, statuses) { plans.filter { statuses[it]?.urgency == MaintenanceUrgency.UPCOMING } }
    val baselineKnownCount = remember(plans) { plans.count { it.lastServiceOdometerKm != null || it.lastServiceDate != null } }
    val baselineMissingCount = (plans.size - baselineKnownCount).coerceAtLeast(0)
    var showAllLater by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = CMPremium.ScreenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            MaintenanceCockpitHeader(
                vehicle = vehicle,
                overdue = overdue,
                dueSoon = dueSoon,
                activeCount = plans.size
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AutomotiveActionCard(
                    title = "إضافة بند",
                    subtitle = "موعد وسعر وأولوية",
                    icon = CMIcons.Add,
                    tone = AutoTone.AMBER,
                    modifier = Modifier.weight(1f),
                    onClick = { showPlan = true }
                )
                AutomotiveActionCard(
                    title = "صيانة مجمعة",
                    subtitle = "عدة بنود في زيارة واحدة",
                    icon = CMIcons.Maintenance,
                    tone = AutoTone.CORAL,
                    modifier = Modifier.weight(1f),
                    filled = true,
                    onClick = { showBatchRecord = true }
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomotiveMetricCard("متأخرة", overdue.toString(), Icons.Rounded.ErrorOutline, if (overdue > 0) AutoTone.RED else AutoTone.GRAPHITE, Modifier.weight(1f))
                AutomotiveMetricCard("قريبًا", dueSoon.toString(), Icons.Rounded.Schedule, AutoTone.AMBER, Modifier.weight(1f))
                AutomotiveMetricCard("نشطة", plans.size.toString(), Icons.Rounded.CheckCircle, AutoTone.GREEN, Modifier.weight(1f))
            }
        }

        if (priorityBundle != null) {
            item {
                UpcomingBundleCard(bundle = priorityBundle, onRecord = { showBatchRecord = true })
            }
        }

        if (plans.isNotEmpty() && (baselineMissingCount > 0 || history.isEmpty())) {
            item {
                MaintenanceBaselineCard(
                    known = baselineKnownCount,
                    total = plans.size,
                    onClick = { showBaselineSetup = true }
                )
            }
        }

        item { SectionHeader("خطة الصيانة", "الآن = المتأخر • قريبًا = داخل نافذة التنبيه • لاحقًا = باقي البنود") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text("الآن $overdue") }, leadingIcon = { Icon(Icons.Default.WarningAmber, null, Modifier.size(16.dp)) })
                AssistChip(onClick = {}, label = { Text("قريبًا $dueSoon") }, leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(16.dp)) })
                AssistChip(onClick = {}, label = { Text("لاحقًا ${ (plans.size - overdue - dueSoon).coerceAtLeast(0) }") }, leadingIcon = { Icon(Icons.Default.EventAvailable, null, Modifier.size(16.dp)) })
            }
        }

        if (plans.isEmpty()) {
            item { EmptyState("لا توجد خطة صيانة", "أضف بندًا أو افتح قائمة الصيانة الذكية من المزيد.", Icons.Default.Checklist) }
        } else {
            if (overduePlans.isNotEmpty()) {
                item { MaintenanceGroupTitle("الآن", "بنود متأخرة أو مستحقة بالفعل", overduePlans.size, MaterialTheme.colorScheme.error) }
                items(overduePlans, key = { "now-${it.id}" }) { plan -> CompactPlanCard(vehicle = vehicle, plan = plan, faults = faults, onEditBaseline = { baselinePlan = plan }) }
            }
            if (dueSoonPlans.isNotEmpty()) {
                item { MaintenanceGroupTitle("قريبًا", "داخل نافذة التنبيه الحالية", dueSoonPlans.size, MaterialTheme.colorScheme.tertiary) }
                items(dueSoonPlans, key = { "soon-${it.id}" }) { plan -> CompactPlanCard(vehicle = vehicle, plan = plan, faults = faults, onEditBaseline = { baselinePlan = plan }) }
            }
            if (laterPlans.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (laterPlans.size > 3) TextButton(onClick = { showAllLater = !showAllLater }) { Text(if (showAllLater) "عرض أقل" else "عرض الكل") }
                        Spacer(Modifier.weight(1f))
                        MaintenanceGroupTitle("لاحقًا", "باقي البنود تحت المتابعة", laterPlans.size, MaterialTheme.colorScheme.primary)
                    }
                }
                items(if (showAllLater) laterPlans else laterPlans.take(3), key = { "later-${it.id}" }) { plan -> CompactPlanCard(vehicle = vehicle, plan = plan, faults = faults, onEditBaseline = { baselinePlan = plan }) }
            }
        }

        item { SectionHeader("سجل الصيانة", if (history.isEmpty()) "لا توجد عمليات مسجلة" else "${history.size} عملية مسجلة") }
        if (history.isEmpty()) {
            item {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("سجّل أول عملية صيانة ليبدأ تاريخ المركبة الفعلي.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(history, key = { it.id }) { record ->
                MaintenanceHistoryRow(record = record, currentOdometer = vehicle.currentOdometerKm, onAttachment = { attachmentRecord = record })
            }
        }

        item { Spacer(Modifier.height(92.dp)) }
    }

    if (showPlan) {
        MaintenancePlanDialog(
            vehicle = vehicle,
            onDismiss = { showPlan = false },
            onSave = { onAddPlan(it); showPlan = false }
        )
    }
    if (showRecord) {
        MaintenanceRecordDialog(
            vehicle = vehicle,
            plans = plans,
            onDismiss = { showRecord = false },
            onSave = { onAddRecord(it); showRecord = false }
        )
    }
    if (showBatchRecord) {
        MaintenanceBatchDialog(
            vehicle = vehicle,
            plans = plans,
            onDismiss = { showBatchRecord = false },
            onSave = { records ->
                records.forEach(onAddRecord)
                showBatchRecord = false
                onMessage("تم تسجيل ${records.size} بند ضمن عملية صيانة واحدة وتحديث مواعيدها التالية.")
            }
        )
    }
    if (showBaselineSetup) {
        MaintenanceBaselineSheet(
            vehicle = vehicle,
            plans = plans,
            onDismiss = { showBaselineSetup = false },
            onEdit = { plan ->
                showBaselineSetup = false
                baselinePlan = plan
            }
        )
    }
    baselinePlan?.let { plan ->
        MaintenanceLastServiceSheet(
            vehicle = vehicle,
            plan = plan,
            onDismiss = { baselinePlan = null },
            onSave = { input ->
                onUpdatePlan(plan.id, input)
                baselinePlan = null
                onMessage("تم تحديث آخر صيانة لبند ${plan.titleAr} وحساب الاستحقاق القادم تلقائيًا.")
            }
        )
    }
    attachmentRecord?.let { record ->
        AttachmentManagerSheet(
            vehicleId = vehicle.vehicleId,
            entityType = EntityType.MAINTENANCE,
            entityId = record.id,
            title = "${record.titleAr} • ${formatDate(record.serviceDate)}",
            onDismiss = { attachmentRecord = null },
            onMessage = onMessage
        )
    }
}

@Composable
private fun MaintenanceBaselineCard(known: Int, total: Int, onClick: () -> Unit) {
    val progress = if (total <= 0) 0f else known.toFloat() / total.toFloat()
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.History, null, Modifier.padding(9.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("استكمال تاريخ الصيانة السابق", fontWeight = FontWeight.ExtraBold)
                    Text("أدخل آخر تغيير تعرفه فقط واترك غير المعروف بدون تخمين.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text("تم تعريف آخر صيانة لـ $known من $total بند", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceBaselineSheet(
    vehicle: VehicleEntity,
    plans: List<MaintenancePlanEntity>,
    onDismiss: () -> Unit,
    onEdit: (MaintenancePlanEntity) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("تاريخ الصيانة عند إضافة السيارة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "هذه القائمة لا تفترض أن أي صيانة تمت. اضغط على البند وسجّل آخر تغيير فقط إذا كنت تعرف العداد أو التاريخ الحقيقي.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(plans, key = { it.id }) { plan ->
                    OutlinedCard(onClick = { onEdit(plan) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(plan.titleAr, fontWeight = FontWeight.Bold)
                                val lastText = buildList {
                                    plan.lastServiceOdometerKm?.let { add("${formatKm(it)} كم") }
                                    plan.lastServiceDate?.let { add(formatDate(it)) }
                                }.joinToString(" • ").ifBlank { "آخر صيانة غير مسجلة" }
                                Text(lastText, style = MaterialTheme.typography.labelSmall, color = if (lastText.startsWith("آخر")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceLastServiceSheet(
    vehicle: VehicleEntity,
    plan: MaintenancePlanEntity,
    onDismiss: () -> Unit,
    onSave: (MaintenancePlanInput) -> Unit
) {
    var lastKm by remember(plan.id) { mutableStateOf(plan.lastServiceOdometerKm?.let(::editableNumber).orEmpty()) }
    var lastDate by remember(plan.id) { mutableStateOf(plan.lastServiceDate) }
    val kmValue = lastKm.toDoubleOrNull()
    val kmValid = kmValue == null || (kmValue >= 0.0 && kmValue <= vehicle.currentOdometerKm)
    val hasKnownAnchor = when (plan.reminderRule) {
        ReminderRule.DATE_ONLY -> lastDate != null
        ReminderRule.ODOMETER_ONLY -> kmValue != null
        ReminderRule.WHICHEVER_COMES_FIRST -> kmValue != null || lastDate != null
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.End
        ) {
            Text(plan.titleAr, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("آخر صيانة معروفة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("لا تكتب قيمة تقريبية. إذا لم تكن تعرف آخر تغيير اتركه غير مسجل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            if (plan.reminderRule != ReminderRule.DATE_ONLY) {
                AppField(lastKm, { lastKm = numericInput(it) }, "آخر تغيير عند عداد (كم)")
                if (!kmValid) Text("يجب ألا يتجاوز آخر تغيير العداد الحالي ${formatKm(vehicle.currentOdometerKm)} كم.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            if (plan.reminderRule != ReminderRule.ODOMETER_ONLY) {
                AppDateSelector("تاريخ آخر تغيير", lastDate, allowClear = true) { lastDate = it }
            }

            Surface(Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                    Text("بعد الحفظ", fontWeight = FontWeight.Bold)
                    Text("سيحسب CarManager موعد الصيانة القادم تلقائيًا من فترة هذا البند، بدون إنشاء عملية صيانة وهمية في السجل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                Button(
                    enabled = kmValid && hasKnownAnchor,
                    onClick = {
                        onSave(plan.toInputWithLastService(kmValue, lastDate))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("حفظ آخر صيانة") }
            }
            if (plan.lastServiceOdometerKm != null || plan.lastServiceDate != null) {
                TextButton(
                    onClick = { onSave(plan.toInputWithLastService(null, null)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("مسح آخر صيانة وجعلها غير معروفة") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun MaintenancePlanEntity.toInputWithLastService(lastKm: Double?, lastDate: Long?): MaintenancePlanInput =
    MaintenancePlanInput(
        titleAr = titleAr,
        category = category,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        reminderRule = reminderRule,
        estimatedCost = estimatedCost,
        warningBeforeKm = warningBeforeKm,
        warningBeforeDays = warningBeforeDays,
        notes = notes,
        lastServiceOdometerKm = if (reminderRule == ReminderRule.DATE_ONLY) null else lastKm,
        lastServiceDate = if (reminderRule == ReminderRule.ODOMETER_ONLY) null else lastDate,
        nextDueOdometerKm = null,
        nextDueDate = null,
        priority = priority
    )

@Composable
private fun MaintenanceGroupTitle(title: String, subtitle: String, count: Int, accent: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .12f)) {
                Text(count.toString(), Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = accent)
        }
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MaintenanceCockpitHeader(vehicle: VehicleEntity, overdue: Int, dueSoon: Int, activeCount: Int) {
    val accent = if (overdue > 0) androidx.compose.ui.graphics.Color(0xFFFF8F89) else androidx.compose.ui.graphics.Color(0xFF65E0B2)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF101A20), androidx.compose.ui.graphics.Color(0xFF263039), androidx.compose.ui.graphics.Color(0xFF12272B))))
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .08f)) {
                    Icon(CMIcons.Maintenance, null, Modifier.padding(13.dp).size(30.dp), tint = accent)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("مركز الصيانة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
                    Text("${vehicle.displayName ?: vehicle.model} • ${formatKm(vehicle.currentOdometerKm)} كم", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .65f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MaintenanceHeroStat("نشطة", activeCount.toString(), AutoTone.GREEN, Modifier.weight(1f))
                MaintenanceHeroStat("قريبًا", dueSoon.toString(), AutoTone.AMBER, Modifier.weight(1f))
                MaintenanceHeroStat("متأخرة", overdue.toString(), if (overdue > 0) AutoTone.RED else AutoTone.GRAPHITE, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MaintenanceHeroStat(label: String, value: String, tone: AutoTone, modifier: Modifier) {
    val c = autoToneColors(tone)
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .07f)) {
        Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = c.strong)
            Text(label, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .62f))
        }
    }
}

@Composable
private fun MaintenanceSummaryTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, warning: Boolean, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer.copy(alpha = .72f) else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpcomingBundleCard(
    bundle: PrioritizedMaintenanceBundle,
    onRecord: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val source = bundle.source
    val critical = bundle.summary(MaintenancePriorityLevel.CRITICAL)
    val important = bundle.summary(MaintenancePriorityLevel.IMPORTANT)
    val defer = bundle.summary(MaintenancePriorityLevel.CAN_DEFER)
    val criticalBundle = bundle.highestPriority == MaintenancePriorityLevel.CRITICAL

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (criticalBundle) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (criticalBundle) Icons.Rounded.WarningAmber else Icons.Rounded.EventAvailable,
                    null,
                    tint = if (criticalBundle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (source.hasOverdueItems) "الحزمة المطلوبة الآن" else "الحزمة القادمة حسب الأولوية", fontWeight = FontWeight.Bold)
                    Text(
                        "${bundle.items.size} بند • أعلى أولوية: ${priorityLabel(bundle.highestPriority)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(bundle.highestPriorityReasonAr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriorityCostPill("حرج", critical.itemCount, critical.estimatedCost, MaintenancePriorityLevel.CRITICAL, Modifier.weight(1f))
                PriorityCostPill("مهم", important.itemCount, important.estimatedCost, MaintenancePriorityLevel.IMPORTANT, Modifier.weight(1f))
                PriorityCostPill("يمكن تأجيله", defer.itemCount, defer.estimatedCost, MaintenancePriorityLevel.CAN_DEFER, Modifier.weight(1f))
            }

            Spacer(Modifier.height(7.dp))
            bundle.items.take(if (expanded) bundle.items.size else 3).forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    MaintenancePriorityBadge(item.decision.level)
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(item.status.plan.titleAr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.decision.reasonAr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (bundle.items.size > 3) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
                    Text(if (expanded) "إخفاء التفاصيل" else "عرض ${bundle.items.size - 3} بنود أخرى")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 5.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text("إجمالي التكلفة المعروفة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(source.estimatedCost), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("أقرب استحقاق", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        source.targetOdometerKm?.let { "${formatKm(it)} كم" } ?: source.targetDate?.let(::formatDate) ?: "حسب الحالة",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            val unknownByPriority = bundle.costByPriority.sumOf { it.unknownCostCount }
            if (unknownByPriority > 0) {
                Text("$unknownByPriority بند بدون سعر تقديري ولا يدخل في الإجمالي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(5.dp))
            Button(onClick = onRecord, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("تسجيل صيانة منفذة")
            }
        }
    }
}

@Composable
private fun PriorityCostPill(
    label: String,
    count: Int,
    cost: Double,
    level: MaintenancePriorityLevel,
    modifier: Modifier
) {
    val container = when (level) {
        MaintenancePriorityLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
        MaintenancePriorityLevel.IMPORTANT -> MaterialTheme.colorScheme.secondaryContainer
        MaintenancePriorityLevel.CAN_DEFER -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(11.dp), color = container) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$label • $count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(formatMoney(cost), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun CompactPlanCard(
    vehicle: VehicleEntity,
    plan: MaintenancePlanEntity,
    faults: List<FaultRecordEntity>,
    onEditBaseline: () -> Unit
) {
    var expanded by remember(plan.id) { mutableStateOf(false) }
    val status = remember(vehicle.currentOdometerKm, plan) { MaintenanceAdvisor.statusFor(vehicle, plan) }
    val priority = remember(status, faults) { MaintenancePriorityEngine.evaluate(status, faults) }

    ElevatedCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MaintenancePriorityBadge(priority.level)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(plan.titleAr, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(priority.reasonAr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(5.dp))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(20.dp))
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                plan.estimatedCost?.let { Text(formatMoney(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        status.overdueByKm != null -> "متأخرة ${formatKm(status.overdueByKm)} كم"
                        status.remainingKm != null -> "متبقي ${formatKm(status.remainingKm.coerceAtLeast(0.0))} كم"
                        status.overdueByDays != null -> "متأخرة ${status.overdueByDays} يوم"
                        status.remainingDays != null -> "متبقي ${status.remainingDays.coerceAtLeast(0L)} يوم"
                        else -> "حسب الاستحقاق"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status.urgency == MaintenanceUrgency.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 7.dp))
                DetailLine("التصنيف", plan.category)
                DetailLine("سبب الأولوية", priority.reasonAr)
                plan.lastServiceOdometerKm?.let { DetailLine("آخر خدمة", "${formatKm(it)} كم") }
                plan.lastServiceDate?.let { DetailLine("تاريخ آخر خدمة", formatDate(it)) }
                if (plan.lastServiceOdometerKm == null && plan.lastServiceDate == null) {
                    DetailLine("آخر خدمة", "غير مسجلة")
                }
                status.resolvedNextDueOdometerKm?.let { DetailLine("القادم عند", "${formatKm(it)} كم") }
                status.resolvedNextDueDate?.let { DetailLine("الموعد القادم", formatDate(it)) }
                status.usageProjection?.let { usage ->
                    DetailLine("معدل الاستخدام", "${formatKm(usage.kmPerDay)} كم/يوم • ${usage.confidence.arLabel()}")
                    status.projectedOdometerDueDate?.let { DetailLine("الوصول المتوقع", formatDate(it)) }
                }
                if (!plan.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(plan.notes!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onEditBaseline, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.History, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (plan.lastServiceOdometerKm == null && plan.lastServiceDate == null) "إضافة آخر صيانة معروفة" else "تعديل آخر صيانة")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(value, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MaintenancePriorityBadge(level: MaintenancePriorityLevel) {
    val container = when (level) {
        MaintenancePriorityLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
        MaintenancePriorityLevel.IMPORTANT -> MaterialTheme.colorScheme.secondaryContainer
        MaintenancePriorityLevel.CAN_DEFER -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (level) {
        MaintenancePriorityLevel.CRITICAL -> MaterialTheme.colorScheme.error
        MaintenancePriorityLevel.IMPORTANT -> MaterialTheme.colorScheme.secondary
        MaintenancePriorityLevel.CAN_DEFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            priorityLabel(level),
            Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun priorityLabel(level: MaintenancePriorityLevel): String = when (level) {
    MaintenancePriorityLevel.CRITICAL -> "حرج"
    MaintenancePriorityLevel.IMPORTANT -> "مهم"
    MaintenancePriorityLevel.CAN_DEFER -> "يمكن تأجيله"
}

@Composable
private fun MaintenanceHistoryRow(record: MaintenanceRecordEntity, currentOdometer: Double, onAttachment: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttachment, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.AttachFile, "المرفقات", Modifier.size(19.dp)) }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMoney(record.totalCost), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Text(record.titleAr, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${formatDate(record.serviceDate)} • ${formatKm(record.odometerKm)} كم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val secondary = buildList {
                    if (!record.serviceCenter.isNullOrBlank()) add(record.serviceCenter!!)
                    if (record.odometerKm < currentOdometer) add("سجل تاريخي")
                }.joinToString(" • ")
                if (secondary.isNotBlank()) Text(secondary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun intervalText(plan: MaintenancePlanEntity): String = buildString {
    if (plan.reminderRule != ReminderRule.DATE_ONLY) plan.intervalKm?.let { append("كل ${formatKm(it)} كم") }
    if (plan.reminderRule == ReminderRule.WHICHEVER_COMES_FIRST && plan.intervalKm != null && plan.intervalMonths != null) append(" أو ")
    if (plan.reminderRule != ReminderRule.ODOMETER_ONLY) plan.intervalMonths?.let { append("كل $it شهر") }
}.ifBlank { "حسب الحالة" }

private fun ProjectionConfidence.arLabel() = when (this) {
    ProjectionConfidence.LOW -> "دقة مبدئية"
    ProjectionConfidence.MEDIUM -> "دقة جيدة"
    ProjectionConfidence.HIGH -> "دقة مرتفعة"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenancePlanDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (MaintenancePlanInput) -> Unit) {
    val templates = remember(vehicle.vehicleId, vehicle.brand, vehicle.model, vehicle.year) {
        MaintenanceCatalog.templatesFor(vehicle, MaintenanceCatalog.presetFor(vehicle))
    }
    var selectedTemplate by remember { mutableStateOf<MaintenanceTemplate?>(null) }
    var customMode by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("دورية") }
    var intervalKm by remember { mutableStateOf("") }
    var intervalMonths by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    fun applyTemplate(template: MaintenanceTemplate) {
        selectedTemplate = template
        customMode = false
        title = template.titleAr
        category = template.category
        intervalKm = template.intervalKm?.let(::editableNumber).orEmpty()
        intervalMonths = template.intervalMonths?.toString().orEmpty()
        cost = template.estimatedCost?.let(::editableNumber).orEmpty()
        notes = template.notes.orEmpty()
    }

    val valid = title.isNotBlank() && ((intervalKm.toDoubleOrNull() ?: 0.0) > 0 || (intervalMonths.toIntOrNull() ?: 0) > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة بند صيانة") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 540.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = if (customMode) "بند مخصص" else selectedTemplate?.titleAr ?: "اختر من القائمة",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("بند الصيانة") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(template.titleAr)
                                        Text(template.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { applyTemplate(template); expanded = false }
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
                                cost = ""
                                notes = ""
                                expanded = false
                            }
                        )
                    }
                }

                if (customMode || selectedTemplate != null) {
                    AppField(title, { title = it }, "اسم البند")
                    AppField(category, { category = it }, "التصنيف")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        AppField(intervalKm, { intervalKm = numericInput(it) }, "كل كم", Modifier.weight(1f))
                        AppField(intervalMonths, { intervalMonths = numericInput(it).substringBefore('.') }, "كل شهر", Modifier.weight(1f))
                    }
                    AppField(cost, { cost = numericInput(it) }, "التكلفة التقديرية")
                    AppField(notes, { notes = it }, "ملاحظات")
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                val km = intervalKm.toDoubleOrNull()?.takeIf { it > 0 }
                val months = intervalMonths.toIntOrNull()?.takeIf { it > 0 }
                val rule = when {
                    km != null && months != null -> ReminderRule.WHICHEVER_COMES_FIRST
                    km != null -> ReminderRule.ODOMETER_ONLY
                    else -> ReminderRule.DATE_ONLY
                }
                onSave(
                    MaintenancePlanInput(
                        titleAr = title.trim(),
                        category = category.trim().ifBlank { "دورية" },
                        intervalKm = km,
                        intervalMonths = months,
                        reminderRule = rule,
                        estimatedCost = cost.toDoubleOrNull(),
                        notes = notes.trim().takeIf { it.isNotEmpty() },
                        lastServiceOdometerKm = selectedTemplate?.lastServiceOdometerKm,
                        nextDueOdometerKm = selectedTemplate?.nextDueOdometerKm,
                        priority = selectedTemplate?.priority ?: 0
                    )
                )
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceRecordDialog(
    vehicle: VehicleEntity,
    plans: List<MaintenancePlanEntity>,
    onDismiss: () -> Unit,
    onSave: (MaintenanceRecordInput) -> Unit
) {
    var planId by remember { mutableStateOf<String?>(null) }
    var planMenu by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf(editableNumber(vehicle.currentOdometerKm)) }
    var cost by remember { mutableStateOf("") }
    var center by remember { mutableStateOf("") }
    var invoice by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    val valid = title.isNotBlank() && odometer.toDoubleOrNull() != null && cost.toDoubleOrNull() != null && date != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صيانة مجمعة") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (plans.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = planMenu, onExpandedChange = { planMenu = !planMenu }) {
                        OutlinedTextField(
                            value = plans.firstOrNull { it.id == planId }?.titleAr ?: "اختر بندًا أو اكتب يدويًا",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("ربط بخطة صيانة - اختياري") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(planMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = planMenu, onDismissRequest = { planMenu = false }) {
                            DropdownMenuItem(text = { Text("بدون ربط") }, onClick = { planId = null; planMenu = false })
                            plans.forEach { plan ->
                                DropdownMenuItem(text = { Text(plan.titleAr) }, onClick = {
                                    planId = plan.id
                                    title = plan.titleAr
                                    planMenu = false
                                })
                            }
                        }
                    }
                }
                AppField(title, { title = it }, "اسم الصيانة")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AppField(odometer, { odometer = numericInput(it) }, "العداد", Modifier.weight(1f))
                    AppField(cost, { cost = numericInput(it) }, "الإجمالي", Modifier.weight(1f))
                }
                AppDateSelector("تاريخ الصيانة", date) { date = it }
                AppField(center, { center = it }, "مركز الصيانة - اختياري")
                AppField(invoice, { invoice = it }, "رقم الفاتورة - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                Text("يمكن تسجيل صيانة تاريخية بعداد أقل من العداد الحالي؛ لن يتم خفض عداد المركبة الحالي.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave(
                    MaintenanceRecordInput(
                        titleAr = title.trim(),
                        odometerKm = odometer.toDouble(),
                        totalCost = cost.toDouble(),
                        planId = planId,
                        serviceCenter = center.trim().takeIf { it.isNotEmpty() },
                        invoiceNumber = invoice.trim().takeIf { it.isNotEmpty() },
                        notes = notes.trim().takeIf { it.isNotEmpty() },
                        date = date!!
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun editableNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
