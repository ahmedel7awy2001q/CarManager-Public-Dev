package com.ahmed.carmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenanceItemStatus
import com.ahmed.carmanager.data.maintenance.MaintenanceUrgency
import java.util.Calendar

@Composable
fun DashboardScreen(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    maintenance: List<MaintenanceRecordEntity>,
    fuel: List<FuelRecordEntity>,
    expenses: List<ExpenseEntity>,
    trips: List<TripEntity>,
    reminders: List<ReminderEntity>,
    latestGps: GpsReadingEntity?,
    onSetOdometer: (Double) -> Unit,
    vehicleCount: Int = 1,
    onOpenMaintenance: () -> Unit = {},
    onOpenFuel: () -> Unit = {},
    onOpenGps: () -> Unit = {},
    onOpenGarage: () -> Unit = {}
) {
    if (vehicle == null) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(CMIcons.Vehicle, null, modifier = Modifier.padding(14.dp).size(34.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            Text("لا توجد مركبة محددة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("أضف مركبة أو اخترها من الجراج لتظهر لوحة المتابعة.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
            Button(onClick = onOpenGarage) { Icon(CMIcons.Garage, null); Spacer(Modifier.width(8.dp)); Text("فتح الجراج") }
        }
        return
    }

    var showOdometer by remember { mutableStateOf(false) }
    val monthStart = remember { startOfMonth() }
    val monthFuel = fuel.filter { it.fuelDate >= monthStart }.sumOf { it.amountPaid }
    val monthMaintenance = maintenance.filter { it.serviceDate >= monthStart }.sumOf { it.totalCost }
    val monthExpenses = expenses.filter { it.expenseDate >= monthStart }.sumOf { it.amount }
    val monthTrips = trips.filter { it.startTime >= monthStart }.sumOf { it.distanceKm }
    val totalMonth = monthFuel + monthMaintenance + monthExpenses
    val avgConsumption = fuel.mapNotNull { it.consumptionLitersPer100Km }.take(5).takeIf { it.isNotEmpty() }?.average()
    val nextBundle = remember(vehicle.currentOdometerKm, plans) { MaintenanceAdvisor.nextBundle(vehicle, plans) }
    val planStatuses = remember(vehicle.currentOdometerKm, plans) { plans.map { MaintenanceAdvisor.statusFor(vehicle, it) } }
    val overdueCount = planStatuses.count { it.urgency == MaintenanceUrgency.OVERDUE }
    val soonCount = planStatuses.count { it.urgency == MaintenanceUrgency.DUE_SOON }
    val attentionCount = overdueCount + soonCount + reminders.size
    val criticalItem = planStatuses.firstOrNull { it.urgency == MaintenanceUrgency.OVERDUE }
        ?: planStatuses.firstOrNull { it.urgency == MaintenanceUrgency.DUE_SOON }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.End) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (!vehicle.vehiclePhotoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = vehicle.vehiclePhotoUri,
                                contentDescription = "صورة المركبة",
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
                                Icon(CMIcons.Vehicle, null, modifier = Modifier.padding(12.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (overdueCount > 0) {
                                    AssistChip(onClick = onOpenMaintenance, label = { Text("تحتاج اهتمام") }, leadingIcon = { Icon(Icons.Default.Warning, null, Modifier.size(16.dp)) })
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            }
                            Text("${vehicle.year} • ${vehicle.fuelType.arLabel()} • ${vehicle.transmissionType.arLabel()}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            if (!vehicle.plateNumber.isNullOrBlank()) Text("لوحة ${vehicle.plateNumber}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("عداد المركبة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${formatKm(vehicle.currentOdometerKm)} كم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    FilledTonalButton(onClick = { showOdometer = true }) {
                        Icon(CMIcons.Edit, null); Spacer(Modifier.width(6.dp)); Text("تحديث العداد")
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardStatusTile("المركبات", vehicleCount.toString(), CMIcons.Garage, Modifier.weight(1f), onOpenGarage)
                DashboardStatusTile("عاجل الآن", overdueCount.toString(), Icons.Default.NotificationImportant, Modifier.weight(1f), onOpenMaintenance, overdueCount > 0)
                DashboardStatusTile("متابعة", (soonCount + reminders.size).toString(), Icons.Default.Checklist, Modifier.weight(1f), onOpenMaintenance)
            }
        }

        item {
            if (attentionCount > 0) {
                AttentionCenterCard(criticalItem, reminders.size, onOpenMaintenance)
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("لا توجد عناصر عاجلة", fontWeight = FontWeight.Bold)
                            Text("الصيانة والتنبيهات المسجلة حاليًا تحت المتابعة.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardQuickAction("الصيانة", CMIcons.Maintenance, onOpenMaintenance, Modifier.weight(1f))
                DashboardQuickAction("البنزين", CMIcons.Fuel, onOpenFuel, Modifier.weight(1f))
                DashboardQuickAction("GPS", CMIcons.Location, onOpenGps, Modifier.weight(1f))
                DashboardQuickAction("الجراج", CMIcons.Garage, onOpenGarage, Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("مصروف الشهر", formatMoney(totalMonth), CMIcons.Expense, Modifier.weight(1f))
                MetricCard("مسافة الشهر", "${formatKm(monthTrips)} كم", CMIcons.Trip, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("متوسط الاستهلاك", avgConsumption?.let { "${formatKm(it)} ل/100" } ?: "—", CMIcons.Fuel, Modifier.weight(1f))
                MetricCard("تنبيهات مفتوحة", reminders.size.toString(), CMIcons.Reminder, Modifier.weight(1f))
            }
        }

        item {
            SectionHeader("الصيانة القادمة", "تجميع تلقائي للبنود المتقاربة في الموعد")
            Spacer(Modifier.height(8.dp))
            if (nextBundle == null) {
                ElevatedCard(onClick = onOpenMaintenance, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("لم تضف خطة صيانة بعد", fontWeight = FontWeight.Bold)
                            Text("من الضبط أضف قائمة الصيانة الذكية أو أنشئ البنود يدويًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                ElevatedCard(
                    onClick = onOpenMaintenance,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (nextBundle.hasOverdueItems) CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.elevatedCardColors()
                ) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.End) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (nextBundle.hasOverdueItems) Icons.Default.Warning else Icons.Default.EventAvailable, null, tint = if (nextBundle.hasOverdueItems) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Text(if (nextBundle.hasOverdueItems) "مطلوب الآن" else "الحزمة الأقرب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        nextBundle.items.take(4).forEach { Text("• ${it.plan.titleAr}", fontWeight = FontWeight.SemiBold) }
                        if (nextBundle.items.size > 4) Text("+ ${nextBundle.items.size - 4} بنود أخرى", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        nextBundle.targetOdometerKm?.let { Text("أقرب عداد: ${formatKm(it)} كم") }
                        nextBundle.targetDate?.let { Text("أقرب تاريخ: ${formatDate(it)}") }
                        Text("التكلفة المتوقعة المعروفة: ${formatMoney(nextBundle.estimatedCost)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (nextBundle.unknownCostCount > 0) Text("${nextBundle.unknownCostCount} بند يحتاج تحديد سعر", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            SectionHeader("GPS", "آخر حالة مسجلة لجهاز التتبع")
            Spacer(Modifier.height(8.dp))
            ElevatedCard(onClick = onOpenGps, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(CMIcons.Location, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(when (latestGps?.connectionStatus) {
                            GpsConnectionStatus.ONLINE -> "متصل"
                            GpsConnectionStatus.OFFLINE -> "غير متصل"
                            else -> "لم تتم مزامنة GPS بعد"
                        }, fontWeight = FontWeight.Bold)
                        if (latestGps?.todayMileageKm != null) Text("مسافة اليوم: ${formatKm(latestGps.todayMileageKm)} كم")
                        if (latestGps?.speedKmh != null) Text("آخر سرعة: ${formatKm(latestGps.speedKmh)} كم/س")
                    }
                }
            }
        }

        item {
            SectionHeader("ملخص الشهر")
            Spacer(Modifier.height(8.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryRow("البنزين", monthFuel)
                    SummaryRow("الصيانة", monthMaintenance)
                    SummaryRow("مصاريف أخرى", monthExpenses)
                    HorizontalDivider()
                    SummaryRow("الإجمالي", totalMonth, true)
                }
            }
        }
    }

    if (showOdometer) {
        var value by remember { mutableStateOf(vehicle.currentOdometerKm.toString()) }
        AlertDialog(
            onDismissRequest = { showOdometer = false },
            icon = { Icon(CMIcons.Odometer, null) },
            title = { Text("تحديث عداد المركبة") },
            text = { AppField(value, { value = numericInput(it) }, "العداد الحالي (كم)") },
            confirmButton = { Button(enabled = value.toDoubleOrNull()?.let { it >= vehicle.currentOdometerKm } == true, onClick = { onSetOdometer(value.toDouble()); showOdometer = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { showOdometer = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun DashboardStatusTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    warning: Boolean = false
) {
    val container = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    Surface(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), color = container) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun AttentionCenterCard(
    criticalItem: MaintenanceItemStatus?,
    reminderCount: Int,
    onOpenMaintenance: () -> Unit
) {
    ElevatedCard(
        onClick = onOpenMaintenance,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .55f)) {
                Icon(CMIcons.Fault, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("يحتاج اهتمامك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    criticalItem?.urgency == MaintenanceUrgency.OVERDUE -> {
                        Text(criticalItem.plan.titleAr, fontWeight = FontWeight.SemiBold)
                        criticalItem.overdueByKm?.let { Text("متأخر ${formatKm(it)} كم عن الاستحقاق", style = MaterialTheme.typography.bodySmall) }
                        criticalItem.overdueByDays?.let { Text("متأخر $it يوم عن الموعد", style = MaterialTheme.typography.bodySmall) }
                    }
                    criticalItem != null -> {
                        Text(criticalItem.plan.titleAr, fontWeight = FontWeight.SemiBold)
                        criticalItem.remainingKm?.let { Text("متبقي نحو ${formatKm(it.coerceAtLeast(0.0))} كم", style = MaterialTheme.typography.bodySmall) }
                        criticalItem.remainingDays?.let { Text("متبقي نحو ${it.coerceAtLeast(0L)} يوم", style = MaterialTheme.typography.bodySmall) }
                    }
                    reminderCount > 0 -> Text("لديك $reminderCount تنبيه مفتوح يحتاج مراجعة.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(CMIcons.Chevron, null)
        }
    }
}

@Composable
private fun DashboardQuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(vertical = 9.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatMoney(value), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun startOfMonth(): Long = Calendar.getInstance().run {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
}
