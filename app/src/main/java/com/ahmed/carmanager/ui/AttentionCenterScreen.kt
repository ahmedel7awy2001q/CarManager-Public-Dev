package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenanceUrgency
import java.util.concurrent.TimeUnit

private enum class AttentionAction { MAINTENANCE, FAULTS, DOCUMENTS, REMINDERS }
private data class AttentionItem(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val kind: PremiumStatusKind,
    val rank: Int,
    val action: AttentionAction
)

@Composable
fun AttentionCenterScreen(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>,
    reminders: List<ReminderEntity>,
    onOpenMaintenance: () -> Unit,
    onOpenFaults: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenReminders: () -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا لمراجعة ما يحتاج انتباهك.", Icons.Rounded.NotificationsActive)
        return
    }

    val items = remember(vehicle, plans, faults, documents, reminders) {
        buildAttentionItems(vehicle, plans, faults, documents, reminders)
    }
    val urgent = items.count { it.kind == PremiumStatusKind.DANGER }
    val warning = items.count { it.kind == PremiumStatusKind.WARNING }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "مركز الانتباه",
                subtitle = if (items.isEmpty()) "كل ما نعرفه عن المركبة تحت السيطرة الآن."
                else "رتبنا لك ما يستحق الإجراء أولًا بدل البحث بين الشاشات.",
                icon = if (items.isEmpty()) Icons.Rounded.TaskAlt else Icons.Rounded.NotificationImportant
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomotiveMetricCard("عاجل", urgent.toString(), Icons.Rounded.ErrorOutline, if (urgent > 0) AutoTone.RED else AutoTone.GRAPHITE, Modifier.weight(1f))
                AutomotiveMetricCard("متابعة", warning.toString(), Icons.Rounded.Schedule, if (warning > 0) AutoTone.AMBER else AutoTone.GRAPHITE, Modifier.weight(1f))
                AutomotiveMetricCard("إجمالي", items.size.toString(), Icons.Rounded.Checklist, AutoTone.VIOLET, Modifier.weight(1f))
            }
        }
        if (items.isEmpty()) {
            item { EmptyState("لا يوجد ما يحتاج إجراء الآن", "سنظهر هنا الصيانة المتأخرة والأعطال والمستندات والتذكيرات عندما تحتاج متابعة.", Icons.Rounded.Verified) }
        } else {
            item { PremiumSectionTitle("الأولوية الآن", "الأخطر أولًا ثم ما يقترب موعده") }
            items(items, key = { "${it.action}-${it.title}-${it.detail}" }) { item ->
                AttentionCard(item) {
                    when (item.action) {
                        AttentionAction.MAINTENANCE -> onOpenMaintenance()
                        AttentionAction.FAULTS -> onOpenFaults()
                        AttentionAction.DOCUMENTS -> onOpenDocuments()
                        AttentionAction.REMINDERS -> onOpenReminders()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun AttentionCard(item: AttentionItem, onClick: () -> Unit) {
    val container = when (item.kind) {
        PremiumStatusKind.DANGER -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .48f)
        PremiumStatusKind.WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .55f)
        PremiumStatusKind.GOOD -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f)
        PremiumStatusKind.NEUTRAL -> MaterialTheme.colorScheme.surface
    }
    val tone = when (item.kind) {
        PremiumStatusKind.DANGER -> MaterialTheme.colorScheme.error
        PremiumStatusKind.WARNING -> MaterialTheme.colorScheme.tertiary
        PremiumStatusKind.GOOD -> MaterialTheme.colorScheme.secondary
        PremiumStatusKind.NEUTRAL -> MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PremiumIconBadge(item.icon, tone = tone, container = tone.copy(alpha = .12f), size = 42)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(item.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
            Spacer(Modifier.width(6.dp))
            PremiumChevron()
        }
    }
}

private fun buildAttentionItems(
    vehicle: VehicleEntity,
    plans: List<MaintenancePlanEntity>,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>,
    reminders: List<ReminderEntity>,
    now: Long = System.currentTimeMillis()
): List<AttentionItem> = buildList {
    plans.filter { it.isActive && !it.isDeleted }.forEach { plan ->
        val status = MaintenanceAdvisor.statusFor(vehicle, plan, now)
        when (status.urgency) {
            MaintenanceUrgency.OVERDUE -> add(
                AttentionItem(
                    title = plan.titleAr,
                    detail = buildString {
                        append("صيانة متأخرة")
                        status.overdueByKm?.takeIf { it > 0 }?.let { append(" • ${formatKm(it)} كم") }
                        status.overdueByDays?.takeIf { it > 0 }?.let { append(" • $it يوم") }
                    },
                    icon = Icons.Rounded.BuildCircle,
                    kind = PremiumStatusKind.DANGER,
                    rank = 0,
                    action = AttentionAction.MAINTENANCE
                )
            )
            MaintenanceUrgency.DUE_SOON -> add(
                AttentionItem(
                    title = plan.titleAr,
                    detail = status.remainingKm?.let { "موعد الصيانة يقترب • متبقي ${formatKm(it)} كم" }
                        ?: status.remainingDays?.let { "موعد الصيانة يقترب • متبقي $it يوم" }
                        ?: "موعد الصيانة يقترب",
                    icon = Icons.Rounded.Schedule,
                    kind = PremiumStatusKind.WARNING,
                    rank = 2,
                    action = AttentionAction.MAINTENANCE
                )
            )
            else -> Unit
        }
    }

    faults.filter { !it.isDeleted && it.status != FaultStatus.RESOLVED && it.status != FaultStatus.CLOSED }.forEach { fault ->
        val danger = fault.severity == FaultSeverity.CRITICAL || fault.severity == FaultSeverity.HIGH
        add(
            AttentionItem(
                title = fault.symptomAr,
                detail = "عطل مفتوح • ${fault.severity.arLabel()}${fault.diagnosisAr?.let { " • $it" } ?: ""}",
                icon = Icons.Rounded.WarningAmber,
                kind = if (danger) PremiumStatusKind.DANGER else PremiumStatusKind.WARNING,
                rank = if (fault.severity == FaultSeverity.CRITICAL) 0 else if (fault.severity == FaultSeverity.HIGH) 1 else 3,
                action = AttentionAction.FAULTS
            )
        )
    }

    documents.filter { !it.isDeleted && it.expiryDate != null }.forEach { doc ->
        val days = TimeUnit.MILLISECONDS.toDays(doc.expiryDate!! - now)
        if (days <= 30) {
            add(
                AttentionItem(
                    title = doc.documentType.arLabel(),
                    detail = when {
                        days < 0 -> "منتهي منذ ${-days} يوم"
                        days == 0L -> "ينتهي اليوم"
                        else -> "ينتهي خلال $days يوم"
                    },
                    icon = Icons.Rounded.Description,
                    kind = if (days < 0) PremiumStatusKind.DANGER else PremiumStatusKind.WARNING,
                    rank = if (days < 0) 0 else 3,
                    action = AttentionAction.DOCUMENTS
                )
            )
        }
    }

    reminders.filter { !it.isDeleted && !it.isCompleted }.forEach { reminder ->
        val dueByDate = reminder.dueDate?.let { it <= now + 7L * 86_400_000L } ?: false
        val dueByKm = reminder.dueOdometerKm?.let { it <= vehicle.currentOdometerKm + (reminder.warningBeforeKm ?: 500.0) } ?: false
        if (dueByDate || dueByKm || reminder.priority >= 2) {
            val overdueDate = reminder.dueDate?.let { it < now } ?: false
            val overdueKm = reminder.dueOdometerKm?.let { it < vehicle.currentOdometerKm } ?: false
            add(
                AttentionItem(
                    title = reminder.titleAr,
                    detail = when {
                        overdueDate || overdueKm -> "تذكير متأخر يحتاج إجراء"
                        reminder.dueDate != null -> "قريب • ${formatDate(reminder.dueDate)}"
                        reminder.dueOdometerKm != null -> "قريب • عند ${formatKm(reminder.dueOdometerKm)} كم"
                        else -> "تذكير مهم"
                    },
                    icon = Icons.Rounded.NotificationsActive,
                    kind = if (overdueDate || overdueKm) PremiumStatusKind.DANGER else PremiumStatusKind.WARNING,
                    rank = if (overdueDate || overdueKm) 1 else 4,
                    action = AttentionAction.REMINDERS
                )
            )
        }
    }
}.sortedWith(compareBy<AttentionItem> { it.rank }.thenBy { it.title })
