package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.rounded.FactCheck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.inspection.InspectionCatalog
import com.ahmed.carmanager.data.inspection.InspectionReportCodec
import com.ahmed.carmanager.data.inspection.InspectionReportSummary
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenancePriorityEngine
import com.ahmed.carmanager.data.maintenance.MaintenancePriorityLevel
import com.ahmed.carmanager.data.maintenance.MaintenanceUrgency
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

private enum class HealthLevel { GOOD, ATTENTION, URGENT }

private data class HealthRow(
    val title: String,
    val subtitle: String,
    val progress: Float?,
    val level: HealthLevel
)

private data class InspectionHealthSnapshot(
    val report: InspectionReportSummary,
    val date: Long,
    val previousDate: Long?
)

private data class VehicleHealthSummary(
    val score: Int,
    val label: String,
    val maintenanceRows: List<HealthRow>,
    val assetRows: List<HealthRow>,
    val documentRows: List<HealthRow>,
    val criticalMaintenanceCount: Int,
    val importantMaintenanceCount: Int,
    val openFaultCount: Int,
    val latestInspection: InspectionHealthSnapshot?
)

@Composable
fun VehicleHealthScreen(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    parts: List<PartEntity>,
    tires: List<TireEntity>,
    batteries: List<BatteryRecordEntity>,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>,
    reminders: List<ReminderEntity>,
    onOpenMaintenance: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenParts: () -> Unit,
    onOpenFaults: () -> Unit = {},
    onOpenInspection: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {}
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا لعرض مؤشر الصحة والمتابعة.", Icons.Rounded.HealthAndSafety)
        return
    }

    val summary = remember(vehicle, plans, parts, tires, batteries, faults, documents, reminders) {
        buildHealthSummary(vehicle, plans, parts, tires, batteries, faults, documents, reminders)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { HealthHero(vehicle, summary) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HealthStat("حرج", summary.criticalMaintenanceCount, Icons.Rounded.ErrorOutline, summary.criticalMaintenanceCount > 0, Modifier.weight(1f), onOpenMaintenance)
                HealthStat("مهم", summary.importantMaintenanceCount, Icons.Rounded.Schedule, false, Modifier.weight(1f), onOpenMaintenance)
                HealthStat("أعطال", summary.openFaultCount, Icons.Rounded.WarningAmber, summary.openFaultCount > 0, Modifier.weight(1f), onOpenFaults)
            }
        }

        item {
            val reasons = buildList {
                if (summary.criticalMaintenanceCount > 0) add("${summary.criticalMaintenanceCount} صيانة حرجة")
                if (summary.importantMaintenanceCount > 0) add("${summary.importantMaintenanceCount} صيانة مهمة")
                if (summary.openFaultCount > 0) add("${summary.openFaultCount} عطل مفتوح")
                summary.latestInspection?.report?.let { r ->
                    if (r.criticalCount > 0) add("${r.criticalCount} ملاحظة حرجة بالفحص")
                    if (r.attentionCount > 0) add("${r.attentionCount} ملاحظة متابعة بالفحص")
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Insights, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("لماذا الدرجة ${summary.score}%؟", fontWeight = FontWeight.Bold)
                        Text(if (reasons.isEmpty()) "لا توجد أسباب مسجلة تخفض المؤشر حاليًا." else reasons.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    }
                }
            }
        }

        item { InspectionHealthCard(summary.latestInspection, onOpenInspection) }
        item { DiagnosticHealthCard(documents, onOpenDiagnostics) }

        item { HealthSectionHeader("الصيانة القادمة", "مرتبة حسب الأولوية المفسّرة ومرتبطة بالأعطال المفتوحة", onOpenMaintenance) }
        if (summary.maintenanceRows.isEmpty()) {
            item { EmptyState("لا توجد بنود صيانة نشطة", "أضف قائمة الصيانة الذكية حتى يبدأ التقييم.") }
        } else {
            items(summary.maintenanceRows.take(8)) { HealthStatusCard(it) }
        }

        item { HealthSectionHeader("القطع والاستهلاك", "يظهر العمر فقط عندما توجد بيانات فعلية كافية", onOpenParts) }
        if (summary.assetRows.isEmpty()) {
            item { CompactInfo("لا توجد قطع بعمر متوقع مسجل حتى الآن.") }
        } else {
            items(summary.assetRows.take(6)) { HealthStatusCard(it) }
        }

        item { HealthSectionHeader("المستندات", "الترخيص والتأمين والمستندات ذات تاريخ الانتهاء", onOpenDocuments) }
        if (summary.documentRows.isEmpty()) {
            item { CompactInfo("لا توجد مستندات بتاريخ انتهاء مسجل.") }
        } else {
            items(summary.documentRows.take(6)) { HealthStatusCard(it) }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("كيف نحسب المؤشر؟", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "يبدأ المؤشر من 100 ويخفضه فقط ما تدعمه بيانات فعلية: صيانة حرجة أو مهمة، أعطال مفتوحة، فحص حديث به ملاحظات، مستندات منتهية، أو قطع تجاوزت العمر المتوقع. نتائج ThinkDiag تؤثر عبر الأعطال التي تم ربطها حتى لا تُحتسب المشكلة نفسها مرتين. نقص البيانات وحده لا يُعتبر عطلًا.",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthHero(vehicle: VehicleEntity, summary: VehicleHealthSummary) {
    val tone = when {
        summary.score >= 85 -> AutoTone.GREEN
        summary.score >= 65 -> AutoTone.AMBER
        else -> AutoTone.RED
    }
    val c = autoToneColors(tone)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF09171D), androidx.compose.ui.graphics.Color(0xFF16313A), androidx.compose.ui.graphics.Color(0xFF101B23))))
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(102.dp)) {
                CircularProgressIndicator(
                    progress = { summary.score / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 9.dp,
                    color = c.strong,
                    trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = .10f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${summary.score}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
                    Text(summary.label, style = MaterialTheme.typography.labelSmall, color = c.strong)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("صحة المركبة", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .56f))
                Text(vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
                Text("${InspectionCatalog.vehicleTypeLabel(vehicle)} • ${vehicle.year} • ${formatKm(vehicle.currentOdometerKm)} كم", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .62f))
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        summary.score >= 85 -> "الحالة المسجلة جيدة. لا توجد مؤشرات قوية تستدعي تدخلاً عاجلاً."
                        summary.score >= 65 -> "توجد نقاط تستحق المتابعة قبل أن تتحول إلى أولوية أعلى."
                        else -> "توجد عناصر حرجة أو أعطال مفتوحة تستحق المراجعة الآن."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = .82f)
                )
            }
        }
    }
}

@Composable
private fun HealthStat(
    title: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    warning: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = if (warning) MaterialTheme.colorScheme.errorContainer.copy(alpha = .58f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InspectionHealthCard(snapshot: InspectionHealthSnapshot?, onOpenInspection: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onOpenInspection, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)) {
                Icon(Icons.AutoMirrored.Rounded.FactCheck, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (snapshot == null) "ابدأ فحص" else "فحص جديد")
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                if (snapshot == null) {
                    Text("الفحص الذكي", fontWeight = FontWeight.Bold)
                    Text("لا يوجد فحص محفوظ يمكن إدخاله في مؤشر الصحة بعد.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val r = snapshot.report
                    Text("آخر فحص • ${r.mode.labelAr} • ${r.score}%", fontWeight = FontWeight.Bold)
                    Text(
                        "${formatDate(snapshot.date)} • سليم ${r.okCount} • متابعة ${r.attentionCount} • حرج ${r.criticalCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (r.criticalCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    snapshot.previousDate?.let {
                        Text("الفحص السابق ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthSectionHeader(title: String, subtitle: String, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text("فتح") }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HealthStatusCard(row: HealthRow) {
    val accent = when (row.level) {
        HealthLevel.GOOD -> MaterialTheme.colorScheme.secondary
        HealthLevel.ATTENTION -> MaterialTheme.colorScheme.tertiary
        HealthLevel.URGENT -> MaterialTheme.colorScheme.error
    }
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = when (row.level) {
                    HealthLevel.GOOD -> MaterialTheme.colorScheme.secondaryContainer
                    HealthLevel.ATTENTION -> MaterialTheme.colorScheme.tertiaryContainer
                    HealthLevel.URGENT -> MaterialTheme.colorScheme.errorContainer
                }) {
                    Text(
                        when (row.level) { HealthLevel.GOOD -> "جيد"; HealthLevel.ATTENTION -> "مهم"; HealthLevel.URGENT -> "حرج" },
                        Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(row.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                    Text(row.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                }
            }
            row.progress?.let {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { it.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompactInfo(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}

private fun buildHealthSummary(
    vehicle: VehicleEntity,
    plans: List<MaintenancePlanEntity>,
    parts: List<PartEntity>,
    tires: List<TireEntity>,
    batteries: List<BatteryRecordEntity>,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>,
    reminders: List<ReminderEntity>,
    now: Long = System.currentTimeMillis()
): VehicleHealthSummary {
    val activePlans = plans.filter { it.isActive && !it.isDeleted }
    val statuses = activePlans.map { MaintenanceAdvisor.statusFor(vehicle, it, now) }
    val prioritized = MaintenancePriorityEngine.sort(statuses, faults)

    val maintenanceRows = prioritized.map { (status, decision) ->
        val progress = maintenanceProgress(vehicle, status.plan, now)
        val dueText = when (status.urgency) {
            MaintenanceUrgency.OVERDUE -> when {
                status.overdueByKm != null && status.overdueByKm > 0 -> "متأخر ${formatKm(status.overdueByKm)} كم"
                status.overdueByDays != null && status.overdueByDays > 0 -> "متأخر ${status.overdueByDays} يوم"
                else -> "مستحق الآن"
            }
            MaintenanceUrgency.DUE_SOON -> when {
                status.remainingKm != null -> "متبقي ${formatKm(max(0.0, status.remainingKm))} كم"
                status.remainingDays != null -> "متبقي ${max(0, status.remainingDays)} يوم"
                else -> "يقترب موعده"
            }
            MaintenanceUrgency.UPCOMING -> when {
                status.remainingKm != null -> "متبقي ${formatKm(max(0.0, status.remainingKm))} كم"
                status.remainingDays != null -> "متبقي ${max(0, status.remainingDays)} يوم"
                else -> "تحت المتابعة"
            }
        }
        HealthRow(
            title = status.plan.titleAr,
            subtitle = "${decision.reasonAr} • $dueText",
            progress = progress,
            level = when (decision.level) {
                MaintenancePriorityLevel.CRITICAL -> HealthLevel.URGENT
                MaintenancePriorityLevel.IMPORTANT -> HealthLevel.ATTENTION
                MaintenancePriorityLevel.CAN_DEFER -> HealthLevel.GOOD
            }
        )
    }

    val partRows = parts.filter { it.status == ItemStatus.ACTIVE && !it.isDeleted }.mapNotNull { part ->
        val kmProgress = if (part.installOdometerKm != null && part.expectedLifeKm != null && part.expectedLifeKm > 0) {
            ((vehicle.currentOdometerKm - part.installOdometerKm) / part.expectedLifeKm).toFloat()
        } else null
        val monthProgress = if (part.installDate != null && part.expectedLifeMonths != null && part.expectedLifeMonths > 0) {
            (monthsBetween(part.installDate, now).toDouble() / part.expectedLifeMonths).toFloat()
        } else null
        val progress = listOfNotNull(kmProgress, monthProgress).maxOrNull() ?: return@mapNotNull null
        HealthRow(part.nameAr, lifecycleSubtitle(progress), progress.coerceAtLeast(0f), lifecycleLevel(progress))
    }

    val batteryRows = batteries.filter { it.status == ItemStatus.ACTIVE && !it.isDeleted }.mapNotNull { battery ->
        val install = battery.installDate ?: return@mapNotNull null
        val expected = battery.expectedLifeMonths ?: return@mapNotNull null
        if (expected <= 0) return@mapNotNull null
        val months = monthsBetween(install, now)
        val progress = (months.toDouble() / expected).toFloat()
        val name = listOfNotNull("البطارية", battery.brand, battery.model).joinToString(" ")
        HealthRow(name, "العمر الحالي $months شهر من $expected شهر متوقع", progress.coerceAtLeast(0f), lifecycleLevel(progress))
    }

    val tireRow = if (tires.any { it.status == ItemStatus.ACTIVE && !it.isDeleted }) {
        HealthRow("الإطارات", "${tires.count { it.status == ItemStatus.ACTIVE && !it.isDeleted }} إطار نشط مسجل — الحالة الفعلية تعتمد على الفحص ونقشة الإطار وعمره", null, HealthLevel.GOOD)
    } else null

    val documentRows = documents.filter { !it.isDeleted && it.documentType != DocumentType.INSPECTION && it.expiryDate != null }
        .sortedBy { it.expiryDate }
        .map { doc ->
            val days = daysBetween(now, doc.expiryDate!!)
            val level = when {
                days < 0 -> HealthLevel.URGENT
                days <= 30 -> HealthLevel.ATTENTION
                else -> HealthLevel.GOOD
            }
            val title = when (doc.documentType) {
                DocumentType.VEHICLE_LICENSE -> "ترخيص المركبة"
                DocumentType.INSURANCE -> "التأمين"
                DocumentType.INSPECTION -> "الفحص"
                DocumentType.CONTRACT -> "عقد"
                DocumentType.RECEIPT -> "إيصال"
                DocumentType.OTHER -> "مستند"
            }
            val subtitle = if (days < 0) "منتهي منذ ${-days} يوم" else "متبقي $days يوم"
            val progress = if (doc.issueDate != null && doc.issueDate < doc.expiryDate) {
                val total = (doc.expiryDate - doc.issueDate).toDouble()
                ((now - doc.issueDate).toDouble() / total).toFloat().coerceAtLeast(0f)
            } else null
            HealthRow(title, subtitle, progress, level)
        }

    val inspectionPairs = documents.asSequence()
        .filter { !it.isDeleted && it.documentType == DocumentType.INSPECTION && it.issueDate != null }
        .sortedByDescending { it.issueDate }
        .mapNotNull { doc -> InspectionReportCodec.parse(doc.notes)?.let { report -> report to doc.issueDate!! } }
        .toList()
    val latestInspection = inspectionPairs.firstOrNull()?.let { (report, date) ->
        InspectionHealthSnapshot(report, date, inspectionPairs.getOrNull(1)?.second)
    }

    val criticalMaintenanceCount = prioritized.count { it.second.level == MaintenancePriorityLevel.CRITICAL }
    val importantMaintenanceCount = prioritized.count { it.second.level == MaintenancePriorityLevel.IMPORTANT }
    val openFaults = faults.filter { !it.isDeleted && it.status != FaultStatus.RESOLVED && it.status != FaultStatus.CLOSED }
    val expiredDocs = documentRows.count { it.level == HealthLevel.URGENT }
    val soonDocs = documentRows.count { it.level == HealthLevel.ATTENTION }
    val assetUrgent = (partRows + batteryRows).count { it.level == HealthLevel.URGENT }
    val assetSoon = (partRows + batteryRows).count { it.level == HealthLevel.ATTENTION }

    var penalty = 0
    penalty += min(38, criticalMaintenanceCount * 10)
    penalty += min(20, importantMaintenanceCount * 4)
    penalty += min(18, expiredDocs * 9)
    penalty += min(8, soonDocs * 3)
    penalty += min(16, assetUrgent * 7 + assetSoon * 3)
    val faultPenalty = openFaults.fold(0) { total, fault ->
        total + when (fault.severity) {
            FaultSeverity.CRITICAL -> 12
            FaultSeverity.HIGH -> 8
            FaultSeverity.MEDIUM -> 4
            FaultSeverity.LOW -> 2
        }
    }
    penalty += min(22, faultPenalty)
    latestInspection?.let { snapshot ->
        val ageDays = daysBetween(snapshot.date, now).coerceAtLeast(0)
        if (ageDays <= 90) penalty += min(10, snapshot.report.criticalCount * 4 + snapshot.report.attentionCount * 2)
    }
    penalty += min(6, reminders.count { !it.isDeleted && !it.isCompleted })

    val score = (100 - penalty).coerceIn(0, 100)
    val label = when {
        score >= 90 -> "ممتازة"
        score >= 80 -> "جيدة جدًا"
        score >= 65 -> "تحتاج متابعة"
        else -> "تحتاج اهتمام"
    }

    return VehicleHealthSummary(
        score = score,
        label = label,
        maintenanceRows = maintenanceRows,
        assetRows = (partRows + batteryRows + listOfNotNull(tireRow)).sortedBy { healthRank(it.level) },
        documentRows = documentRows,
        criticalMaintenanceCount = criticalMaintenanceCount,
        importantMaintenanceCount = importantMaintenanceCount,
        openFaultCount = openFaults.size,
        latestInspection = latestInspection
    )
}

private fun maintenanceProgress(vehicle: VehicleEntity, plan: MaintenancePlanEntity, now: Long): Float? {
    val kmProgress = if (plan.lastServiceOdometerKm != null && plan.intervalKm != null && plan.intervalKm > 0) {
        ((vehicle.currentOdometerKm - plan.lastServiceOdometerKm) / plan.intervalKm).toFloat()
    } else null
    val timeProgress = if (plan.lastServiceDate != null && plan.intervalMonths != null && plan.intervalMonths > 0) {
        (monthsBetween(plan.lastServiceDate, now).toDouble() / plan.intervalMonths).toFloat()
    } else null
    return when (plan.reminderRule) {
        ReminderRule.ODOMETER_ONLY -> kmProgress
        ReminderRule.DATE_ONLY -> timeProgress
        ReminderRule.WHICHEVER_COMES_FIRST -> listOfNotNull(kmProgress, timeProgress).maxOrNull()
    }?.coerceAtLeast(0f)
}

private fun lifecycleLevel(progress: Float): HealthLevel = when {
    progress >= 1f -> HealthLevel.URGENT
    progress >= .8f -> HealthLevel.ATTENTION
    else -> HealthLevel.GOOD
}

private fun lifecycleSubtitle(progress: Float): String = when {
    progress >= 1f -> "تجاوز العمر المتوقع — راجع الحالة الفعلية"
    progress >= .8f -> "استهلك تقريبًا ${(progress * 100).toInt()}% من العمر المتوقع"
    else -> "استهلك تقريبًا ${(progress * 100).toInt().coerceAtLeast(0)}% من العمر المتوقع"
}

private fun healthRank(value: HealthLevel): Int = when (value) {
    HealthLevel.URGENT -> 0
    HealthLevel.ATTENTION -> 1
    HealthLevel.GOOD -> 2
}

private fun daysBetween(from: Long, to: Long): Long = TimeUnit.MILLISECONDS.toDays(to - from)

private fun monthsBetween(from: Long, to: Long): Int {
    if (to <= from) return 0
    return max(0, (TimeUnit.MILLISECONDS.toDays(to - from) / 30.4375).toInt())
}
