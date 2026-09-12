package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.repository.MaintenanceRecordInput
import com.ahmed.carmanager.data.repository.PartInput

private enum class PartsAdvancedTab(val label: String) {
    NEEDS("احتياجي"),
    RECORDS("سجل القطع")
}

private data class PartsNeedStatus(
    val plan: MaintenancePlanEntity,
    val nextKm: Double?,
    val remainingKm: Double?,
    val dueDate: Long?,
    val rank: Int
)

/**
 * Focused advanced center. Prices live in the results-first quick search and the owner guide lives
 * in OwnerGuideScreen, so this screen deliberately contains only the user's actionable plan and
 * installed-parts record. This removes the legacy duplicate "guide" tab and avoids two competing
 * sources of maintenance truth.
 */
@Composable
internal fun PartsAdvancedCenterScreen(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    maintenance: List<MaintenanceRecordEntity>,
    faults: List<FaultRecordEntity>,
    parts: List<PartEntity>,
    onAddPart: (PartInput) -> Unit,
    onAddMaintenance: (MaintenanceRecordInput) -> Unit,
    onMessage: (String) -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة من الجراج أولًا.", CMIcons.Parts)
        return
    }

    var tab by remember(vehicle.vehicleId) { mutableStateOf(PartsAdvancedTab.NEEDS) }
    var selectedPlan by remember(vehicle.vehicleId) { mutableStateOf<MaintenancePlanEntity?>(null) }
    var showAddPart by remember(vehicle.vehicleId) { mutableStateOf(false) }

    val activePlans = remember(plans) { plans.filter { !it.isDeleted && it.isActive } }
    val needStatuses = remember(activePlans, vehicle.currentOdometerKm) {
        activePlans.map { plan ->
            val nextKm = plan.nextDueOdometerKm
                ?: plan.lastServiceOdometerKm?.let { last -> plan.intervalKm?.let { interval -> last + interval } }
            val remaining = nextKm?.minus(vehicle.currentOdometerKm)
            val now = System.currentTimeMillis()
            val overdue = remaining?.let { it <= 0.0 } == true || plan.nextDueDate?.let { it <= now } == true
            val soon = !overdue && (
                remaining?.let { it <= (plan.warningBeforeKm ?: 1_000.0) } == true ||
                    plan.nextDueDate?.let { it <= now + (plan.warningBeforeDays ?: 30) * 86_400_000L } == true
                )
            PartsNeedStatus(
                plan = plan,
                nextKm = nextKm,
                remainingKm = remaining,
                dueDate = plan.nextDueDate,
                rank = when {
                    overdue -> 0
                    soon -> 1
                    else -> 2
                }
            )
        }.sortedWith(
            compareBy<PartsNeedStatus> { it.rank }
                .thenBy { it.remainingKm ?: Double.MAX_VALUE }
                .thenByDescending { it.plan.priority }
        )
    }
    val openFaults = remember(faults) {
        faults.count { !it.isDeleted && it.status !in setOf(FaultStatus.RESOLVED, FaultStatus.CLOSED) }
    }
    val activeParts = remember(parts) {
        parts.filter { !it.isDeleted }
            .sortedWith(compareByDescending<PartEntity> { it.installDate ?: it.purchaseDate ?: it.createdAt })
    }
    val latestMaintenanceByPlan = remember(maintenance) {
        maintenance.asSequence()
            .filter { !it.isDeleted && it.maintenancePlanId != null }
            .groupBy { it.maintenancePlanId!! }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.serviceDate } }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            PartsAdvancedTab.entries.forEach { item ->
                Tab(
                    selected = tab == item,
                    onClick = { tab = item },
                    text = {
                        Text(
                            item.label,
                            maxLines = 1,
                            fontWeight = if (tab == item) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        when (tab) {
            PartsAdvancedTab.NEEDS -> PartsNeedsList(
                needItems = needStatuses,
                openFaults = openFaults,
                lastMaintenanceByPlan = latestMaintenanceByPlan,
                onRecord = { selectedPlan = it },
                onMessage = onMessage
            )

            PartsAdvancedTab.RECORDS -> PartsInstalledRecords(
                parts = activeParts,
                onAdd = { showAddPart = true }
            )
        }
    }

    selectedPlan?.let { plan ->
        RecordMaintenanceDialog(
            vehicle = vehicle,
            plan = plan,
            onDismiss = { selectedPlan = null },
            onSave = { input ->
                onAddMaintenance(input)
                selectedPlan = null
            }
        )
    }

    if (showAddPart) {
        AddInstalledPartDialog(
            vehicle = vehicle,
            onDismiss = { showAddPart = false },
            onSave = { input ->
                onAddPart(input)
                showAddPart = false
            }
        )
    }
}

@Composable
private fun PartsNeedsList(
    needItems: List<PartsNeedStatus>,
    openFaults: Int,
    lastMaintenanceByPlan: Map<String, MaintenanceRecordEntity?>,
    onRecord: (MaintenancePlanEntity) -> Unit,
    onMessage: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            SectionHeader(
                "احتياجي من الخطة الفعلية",
                "هذه القائمة مبنية فقط على بنود الصيانة التي فعّلتها أنت، وليست نسخة من دليل السيارة."
            )
        }

        if (openFaults > 0) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .62f)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "يوجد $openFaults عطل مفتوح. لا تشترِ قطعة اعتمادًا على العرض وحده قبل تثبيت التشخيص.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }

        if (needItems.isEmpty()) {
            item {
                EmptyState(
                    "لا توجد بنود نشطة في خطة الصيانة",
                    "أضف البنود التي تريد متابعتها من دليل السيارة أو إعدادات الصيانة؛ الدليل الكامل لا يتحول تلقائيًا إلى تنبيهات.",
                    Icons.Default.EventAvailable
                )
            }
        } else {
            items(needItems, key = { it.plan.id }) { needItem ->
                val last = lastMaintenanceByPlan[needItem.plan.id]
                val tone = when (needItem.rank) {
                    0 -> AutoTone.RED
                    1 -> AutoTone.AMBER
                    else -> AutoTone.TEAL
                }
                val colors = autoToneColors(tone)
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Column(
                        Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50), color = colors.soft) {
                                Text(
                                    needLabel(needItem),
                                    Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSoft,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(needItem.plan.titleAr, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                                Text(needItem.plan.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        val schedule = buildList {
                            needItem.nextKm?.let { add("موعد العداد ${formatKm(it)} كم") }
                            needItem.dueDate?.let { add(formatDate(it)) }
                        }.joinToString(" • ")
                        if (schedule.isNotBlank()) {
                            Text(schedule, style = MaterialTheme.typography.labelMedium, color = colors.strong, fontWeight = FontWeight.Bold)
                        }
                        last?.let {
                            Text(
                                "آخر تنفيذ مسجل: ${formatDate(it.serviceDate)} • ${formatKm(it.odometerKm)} كم",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        needItem.plan.estimatedCost?.takeIf { it > 0.0 }?.let {
                            Text("تكلفة الخطة: ${formatMoney(it)}", style = MaterialTheme.typography.labelSmall)
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onMessage("للبحث عن السعر والتوافق استخدم «بحث سريع» من أعلى مركز قطع الغيار.")
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.PriceCheck, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("سعر وتوافق", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { onRecord(needItem.plan) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.BuildCircle, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تسجيل تنفيذ", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

private fun needLabel(item: PartsNeedStatus): String {
    val remaining = item.remainingKm
    return when {
        item.rank == 0 -> "مستحق الآن"
        item.rank == 1 && remaining != null -> "متبقي ${formatKm(remaining.coerceAtLeast(0.0))} كم"
        remaining != null -> "بعد ${formatKm(remaining.coerceAtLeast(0.0))} كم"
        item.dueDate != null -> "موعد ${formatDate(item.dueDate)}"
        else -> "متابعة"
    }
}

@Composable
private fun PartsInstalledRecords(parts: List<PartEntity>, onAdd: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            SectionHeader("سجل القطع المركبة", "قطعك الفعلية فقط؛ الأسعار الحية موجودة في البحث السريع") {
                FilledTonalButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة")
                }
            }
        }

        if (parts.isEmpty()) {
            item { EmptyState("لا توجد قطع مسجلة", "أضف القطع التي تم شراؤها أو تركيبها فعليًا فقط.", CMIcons.Parts) }
        } else {
            items(parts, key = { it.id }) { part ->
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                    Column(
                        Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50), color = partStatusTone(part.status)) {
                                Text(
                                    partStatusLabel(part.status),
                                    Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(part.nameAr, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(part.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val identity = listOfNotNull(part.brand, part.partNumber).filter { it.isNotBlank() }.joinToString(" • ")
                        if (identity.isNotBlank()) Text(identity, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        val install = buildList {
                            part.installDate?.let { add(formatDate(it)) }
                            part.installOdometerKm?.let { add("${formatKm(it)} كم") }
                        }.joinToString(" • ")
                        if (install.isNotBlank()) Text("التركيب: $install", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        part.cost?.let { Text("التكلفة: ${formatMoney(it)}", style = MaterialTheme.typography.labelSmall) }
                        part.supplier?.takeIf { it.isNotBlank() }?.let { Text("المورد: $it", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun RecordMaintenanceDialog(
    vehicle: VehicleEntity,
    plan: MaintenancePlanEntity,
    onDismiss: () -> Unit,
    onSave: (MaintenanceRecordInput) -> Unit
) {
    var odometer by remember(plan.id) { mutableStateOf(vehicle.currentOdometerKm.toString()) }
    var cost by remember(plan.id) { mutableStateOf(plan.estimatedCost?.toString().orEmpty()) }
    var serviceCenter by remember(plan.id) { mutableStateOf("") }
    var notes by remember(plan.id) { mutableStateOf("") }
    var date by remember(plan.id) { mutableStateOf<Long?>(System.currentTimeMillis()) }
    val km = odometer.toDoubleOrNull()
    val amount = cost.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل تنفيذ: ${plan.titleAr}") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppField(odometer, { odometer = numericInput(it) }, "العداد عند التنفيذ", keyboardType = KeyboardType.Decimal)
                AppField(cost, { cost = numericInput(it) }, "التكلفة - اختياري", keyboardType = KeyboardType.Decimal)
                AppField(serviceCenter, { serviceCenter = it }, "مركز الخدمة - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                AppDateSelector("تاريخ التنفيذ", date) { date = it }
                if (km != null && km > vehicle.currentOdometerKm) {
                    Text(
                        "لا نسجل صيانة على عداد أعلى من عداد المركبة الحالي. حدّث العداد أولًا إذا كانت هذه القراءة صحيحة.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.End
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = km != null && km >= 0.0 && km <= vehicle.currentOdometerKm && date != null,
                onClick = {
                    val resolvedKm = km
                    val resolvedDate = date
                    if (resolvedKm != null && resolvedDate != null) {
                        onSave(
                            MaintenanceRecordInput(
                                titleAr = plan.titleAr,
                                category = plan.category,
                                odometerKm = resolvedKm,
                                totalCost = amount,
                                planId = plan.id,
                                serviceCenter = serviceCenter.trim().ifBlank { null },
                                notes = notes.trim().ifBlank { null },
                                date = resolvedDate
                            )
                        )
                    }
                }
            ) { Text("حفظ التنفيذ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddInstalledPartDialog(
    vehicle: VehicleEntity,
    onDismiss: () -> Unit,
    onSave: (PartInput) -> Unit
) {
    var name by remember(vehicle.vehicleId) { mutableStateOf("") }
    var category by remember(vehicle.vehicleId) { mutableStateOf("") }
    var brand by remember(vehicle.vehicleId) { mutableStateOf("") }
    var partNumber by remember(vehicle.vehicleId) { mutableStateOf("") }
    var installKm by remember(vehicle.vehicleId) { mutableStateOf(vehicle.currentOdometerKm.toString()) }
    var cost by remember(vehicle.vehicleId) { mutableStateOf("") }
    var lifeKm by remember(vehicle.vehicleId) { mutableStateOf("") }
    var lifeMonths by remember(vehicle.vehicleId) { mutableStateOf("") }
    var supplier by remember(vehicle.vehicleId) { mutableStateOf("") }
    var notes by remember(vehicle.vehicleId) { mutableStateOf("") }
    var installDate by remember(vehicle.vehicleId) { mutableStateOf<Long?>(System.currentTimeMillis()) }

    val odometer = installKm.toDoubleOrNull()
    val months = lifeMonths.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة قطعة مركبة") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                AppField(name, { name = it }, "اسم القطعة *")
                AppField(category, { category = it }, "التصنيف *")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppField(brand, { brand = it }, "الماركة", Modifier.weight(1f))
                    AppField(partNumber, { partNumber = it }, "رقم القطعة / OEM", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppField(installKm, { installKm = numericInput(it) }, "عداد التركيب", Modifier.weight(1f), KeyboardType.Decimal)
                    AppField(cost, { cost = numericInput(it) }, "التكلفة", Modifier.weight(1f), KeyboardType.Decimal)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppField(lifeKm, { lifeKm = numericInput(it) }, "عمر متوقع/كم", Modifier.weight(1f), KeyboardType.Decimal)
                    AppField(lifeMonths, { lifeMonths = it.filter(Char::isDigit) }, "عمر/شهر", Modifier.weight(1f), KeyboardType.Number)
                }
                AppField(supplier, { supplier = it }, "المورد - اختياري")
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                AppDateSelector("تاريخ التركيب", installDate, allowClear = true) { installDate = it }
                if (odometer != null && odometer > vehicle.currentOdometerKm) {
                    Text("عداد التركيب لا يمكن أن يتجاوز عداد المركبة الحالي.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && category.isNotBlank() &&
                    (odometer == null || odometer <= vehicle.currentOdometerKm) &&
                    (months == null || months > 0),
                onClick = {
                    onSave(
                        PartInput(
                            nameAr = name.trim(),
                            category = category.trim(),
                            brand = brand.trim().ifBlank { null },
                            partNumber = partNumber.trim().ifBlank { null },
                            installOdometerKm = odometer,
                            cost = cost.toDoubleOrNull(),
                            expectedLifeKm = lifeKm.toDoubleOrNull(),
                            expectedLifeMonths = months,
                            supplier = supplier.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null },
                            installDate = installDate
                        )
                    )
                }
            ) { Text("حفظ القطعة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun partStatusTone(status: ItemStatus) = when (status) {
    ItemStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
    ItemStatus.REPLACED -> MaterialTheme.colorScheme.secondaryContainer
    ItemStatus.RETIRED -> MaterialTheme.colorScheme.surfaceVariant
    ItemStatus.DAMAGED -> MaterialTheme.colorScheme.errorContainer
    ItemStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
}

private fun partStatusLabel(status: ItemStatus) = when (status) {
    ItemStatus.ACTIVE -> "مركبة"
    ItemStatus.REPLACED -> "تم استبدالها"
    ItemStatus.RETIRED -> "خارج الخدمة"
    ItemStatus.DAMAGED -> "تالفة"
    ItemStatus.UNKNOWN -> "غير محدد"
}
