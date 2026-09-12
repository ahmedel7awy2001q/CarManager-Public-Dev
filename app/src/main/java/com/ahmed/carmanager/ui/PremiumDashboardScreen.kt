package com.ahmed.carmanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ahmed.carmanager.R
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.maintenance.MaintenanceAdvisor
import com.ahmed.carmanager.data.maintenance.MaintenanceBundle
import com.ahmed.carmanager.domain.InsightLevel
import com.ahmed.carmanager.domain.SmartInsight
import com.ahmed.carmanager.domain.SmartVehicleInsights
import java.util.Locale

/**
 * Compact executive cockpit.
 *
 * The previous home screen was visually rich but expensive: large gauges, nested cards and a
 * radial decoration consumed most of the first viewport. This version keeps the same decisions and
 * shortcuts while reducing draw/layout work and making CarManager feel denser and more premium.
 */
@Composable
fun PremiumDashboardScreen(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    maintenance: List<MaintenanceRecordEntity>,
    fuel: List<FuelRecordEntity>,
    expenses: List<ExpenseEntity>,
    trips: List<TripEntity>,
    reminders: List<ReminderEntity>,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>,
    latestGps: GpsReadingEntity?,
    onSetOdometer: (Double) -> Unit,
    vehicleCount: Int,
    onOpenMaintenance: () -> Unit,
    onOpenFuel: () -> Unit,
    onOpenGps: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenAttention: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenParts: () -> Unit,
    onQuickAdd: () -> Unit
) {
    if (vehicle == null) {
        EmptyExecutiveDashboard(onOpenGarage)
        return
    }

    var showOdometer by remember(vehicle.vehicleId) { mutableStateOf(false) }
    var showReadiness by remember(vehicle.vehicleId) { mutableStateOf(false) }
    var showPricingAssistant by remember(vehicle.vehicleId) { mutableStateOf(false) }

    val smart = remember(vehicle, plans, maintenance, fuel, expenses, trips, reminders, faults, documents) {
        SmartVehicleInsights.analyze(vehicle, plans, maintenance, fuel, expenses, trips, reminders, faults, documents)
    }
    val nextBundle = remember(vehicle.currentOdometerKm, plans) { MaintenanceAdvisor.nextBundle(vehicle, plans) }
    val urgentCount = smart.overdueMaintenanceCount + smart.openImportantFaultCount + smart.documentsNeedingAttention
    val attentionCount = urgentCount + smart.dueSoonMaintenanceCount
    val readiness = remember(vehicle, plans, maintenance, fuel, trips, documents) {
        dashboardReadinessItems(vehicle, plans, maintenance, fuel, trips, documents)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ExecutiveBrandStrip(attentionCount, onOpenAttention, onOpenGarage) }
        item {
            ExecutiveVehicleCard(
                vehicle = vehicle,
                nextBundle = nextBundle,
                readiness = smart.dataConfidence,
                urgentCount = urgentCount,
                recentConsumptionL100 = smart.recentConsumptionL100,
                onOdometer = { showOdometer = true },
                onReadiness = { showReadiness = true },
                onFuel = onOpenFuel,
                onMaintenance = onOpenMaintenance
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AutomotiveActionCard("صيانة", "سجل أو راجع", CMIcons.Maintenance, AutoTone.CORAL, Modifier.weight(1f), onClick = onOpenMaintenance)
                AutomotiveActionCard("تموين", "وقود واستهلاك", CMIcons.Fuel, AutoTone.BLUE, Modifier.weight(1f), onClick = onOpenFuel)
                AutomotiveActionCard("إضافة", "كل العمليات", CMIcons.Add, AutoTone.TEAL, Modifier.weight(1f), filled = true, onClick = onQuickAdd)
            }
        }
        item { CompactAttentionCard(smart.insights.firstOrNull(), attentionCount, onOpenAttention) }
        item { AutomotiveSectionTitle("الموجز", "أرقام مهمة بدون ازدحام") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AutomotiveMetricCard("هذا الشهر", if (smart.monthSpend > 0) compactDashboardMoney(smart.monthSpend) else "—", CMIcons.Expense, AutoTone.AMBER, Modifier.weight(1f), onOpenReports)
                AutomotiveMetricCard("تحتاج انتباه", attentionCount.toString(), CMIcons.Attention, if (attentionCount > 0) AutoTone.RED else AutoTone.GREEN, Modifier.weight(1f), onOpenAttention)
                AutomotiveMetricCard("المركبات", vehicleCount.toString(), CMIcons.Garage, AutoTone.VIOLET, Modifier.weight(1f), onOpenGarage)
            }
        }
        item {
            CompactMaintenanceSnapshot(
                nextBundle,
                smart.overdueMaintenanceCount,
                smart.dueSoonMaintenanceCount,
                smart.estimatedKnownUpcomingMaintenanceCost,
                smart.upcomingMaintenanceMissingPriceCount,
                onOpenMaintenance
            )
        }
        item { AutomotiveSectionTitle("الأدوات", "وصول سريع لأهم المراكز") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AutomotiveFeatureTile("صحة المركبة", "فحص وأعطال وأولوية", CMIcons.Health, AutoTone.GREEN, Modifier.weight(1f), onOpenHealth)
                AutomotiveFeatureTile("تسعير مشوار", "تكلفة وسعر مقترح", CMIcons.Pricing, AutoTone.TEAL, Modifier.weight(1f)) { showPricingAssistant = true }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AutomotiveFeatureTile("بحث قطع الغيار", "سعر • OEM • توافق", CMIcons.Parts, AutoTone.AMBER, Modifier.weight(1f), onOpenParts)
                AutomotiveFeatureTile("GPS والتتبع", latestGps?.let { "آخر موقع مسجل متاح" } ?: "أجهزة ومصادر التتبع", CMIcons.Gps, AutoTone.BLUE, Modifier.weight(1f), onOpenGps)
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showOdometer) {
        ExecutiveOdometerDialog(vehicle, { showOdometer = false }) {
            onSetOdometer(it)
            showOdometer = false
        }
    }
    if (showReadiness) DataReadinessSheet(smart.dataConfidence, readiness) { showReadiness = false }
    if (showPricingAssistant) TripPricingAssistantSheet(vehicle = vehicle, onDismiss = { showPricingAssistant = false })
}

@Composable
private fun ExecutiveBrandStrip(attentionCount: Int, onAttention: () -> Unit, onGarage: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onGarage, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Icon(CMIcons.Garage, "المركبات", Modifier.padding(8.dp).size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(6.dp))
        Surface(onClick = onAttention, shape = RoundedCornerShape(12.dp), color = autoToneColors(if (attentionCount > 0) AutoTone.RED else AutoTone.GREEN).soft.copy(alpha = .72f)) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CMIcons.Attention, null, Modifier.size(16.dp), tint = autoToneColors(if (attentionCount > 0) AutoTone.RED else AutoTone.GREEN).strong)
                if (attentionCount > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(attentionCount.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("CarManager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("تصميم احمد الحاوي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExecutiveVehicleCard(
    vehicle: VehicleEntity,
    nextBundle: MaintenanceBundle?,
    readiness: Int,
    urgentCount: Int,
    recentConsumptionL100: Double?,
    onOdometer: () -> Unit,
    onReadiness: () -> Unit,
    onFuel: () -> Unit,
    onMaintenance: () -> Unit
) {
    val accent = Color(0xFF60D8D6)
    val remaining = nextBundle?.items?.mapNotNull { it.remainingKm }?.minOrNull()?.coerceAtLeast(0.0)
    val target = nextBundle?.targetOdometerKm
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0B2228),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .18f)),
        shadowElevation = 0.dp
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF0A2026), Color(0xFF0D3238))))) {
            Column(Modifier.fillMaxWidth().padding(13.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ExecutiveVehicleVisual(vehicle)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (urgentCount > 0) Color(0xFFFF8D86) else Color(0xFF63DDB2)))
                            Spacer(Modifier.width(5.dp))
                            Text(if (urgentCount > 0) "تحتاج متابعة" else "الحالة مستقرة", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .78f))
                        }
                        Text(vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${vehicle.brand} ${vehicle.model} • ${vehicle.year}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .60f), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(11.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ExecutiveMetric("العداد", "${compactDashboardKm(vehicle.currentOdometerKm)} كم", CMIcons.Odometer, Color(0xFF6FD7FF), Modifier.weight(1f), onOdometer)
                    ExecutiveMetric("الصيانة القادمة", remaining?.let { "${compactDashboardKm(it)} كم" } ?: "راجع الخطة", CMIcons.Maintenance, Color(0xFF6DDEB2), Modifier.weight(1f), onMaintenance)
                }
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ExecutiveMiniAction(recentConsumptionL100?.let { "${formatKm(it)} ل/100كم" } ?: "أضف تموين للحساب", CMIcons.Fuel, Modifier.weight(1f), onFuel)
                    ExecutiveMiniAction("جاهزية التحليل $readiness%", Icons.Rounded.AutoGraph, Modifier.weight(1f), onReadiness)
                }
                if (target != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("الاستحقاق التالي عند ${formatKm(target)} كم", Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .50f))
                }
            }
        }
    }
}

@Composable
private fun ExecutiveVehicleVisual(vehicle: VehicleEntity) {
    Surface(Modifier.size(58.dp), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .08f)) {
        if (!vehicle.vehiclePhotoUri.isNullOrBlank()) {
            AsyncImage(vehicle.vehiclePhotoUri, "صورة المركبة", Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        } else {
            Image(painterResource(R.drawable.ic_launcher_premium), "CarManager", Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun ExecutiveMetric(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(13.dp), color = Color.White.copy(alpha = .065f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .06f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = accent)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .55f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ExecutiveMiniAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(11.dp), color = Color.White.copy(alpha = .05f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp), tint = Color(0xFF64D7DE))
            Spacer(Modifier.width(5.dp))
            Text(label, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactAttentionCard(insight: SmartInsight?, attentionCount: Int, onOpen: () -> Unit) {
    val tone = when {
        attentionCount > 0 -> AutoTone.RED
        insight?.level == InsightLevel.ATTENTION -> AutoTone.AMBER
        else -> AutoTone.GREEN
    }
    val c = autoToneColors(tone)
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = c.soft.copy(alpha = .68f), border = androidx.compose.foundation.BorderStroke(1.dp, c.strong.copy(alpha = .14f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AutomotiveIconBadge(if (attentionCount > 0) CMIcons.Attention else Icons.Rounded.Verified, tone, size = 32)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(if (attentionCount > 0) "يحتاج انتباهك" else "كل شيء تحت السيطرة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                Text(insight?.titleAr ?: "لا توجد أولوية عاجلة من البيانات المسجلة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(5.dp))
            PremiumChevron()
        }
    }
}

@Composable
private fun CompactMaintenanceSnapshot(nextBundle: MaintenanceBundle?, overdue: Int, dueSoon: Int, knownCost: Double, missingPrice: Int, onOpen: () -> Unit) {
    val tone = if (overdue > 0) AutoTone.RED else AutoTone.CORAL
    val c = autoToneColors(tone)
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 0.dp) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AutomotiveIconBadge(CMIcons.Maintenance, tone, size = 34)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("الصيانة القادمة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    Text(nextBundle?.targetOdometerKm?.let { "عند ${formatKm(it)} كم" } ?: "راجع الخطة المسجلة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("متأخرة $overdue", style = MaterialTheme.typography.labelSmall, color = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text("قريبًا $dueSoon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (knownCost > 0) formatMoney(knownCost) else "التكلفة غير مكتملة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = c.strong)
                    if (missingPrice > 0) Text("$missingPrice بند بدون سعر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyExecutiveDashboard(onOpenGarage: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_launcher_premium), "CarManager", Modifier.size(92.dp).clip(RoundedCornerShape(24.dp)))
            Spacer(Modifier.height(14.dp))
            Text("ابدأ تجربة CarManager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("أضف مركبتك لإنشاء ملف ذكي للصيانة والوقود والتكاليف والرحلات.", Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpenGarage, shape = RoundedCornerShape(13.dp)) { Text("إضافة مركبة") }
        }
    }
}

@Composable
private fun ExecutiveOdometerDialog(vehicle: VehicleEntity, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember(vehicle.vehicleId) { mutableStateOf(vehicle.currentOdometerKm.toLong().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AutomotiveIconBadge(CMIcons.Odometer, AutoTone.TEAL, size = 36) },
        title = { Text("تحديث العداد") },
        text = {
            Column {
                Text("القراءة الحالية ${formatKm(vehicle.currentOdometerKm)} كم", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(7.dp))
                AppField(text, { text = numericInput(it) }, "العداد الجديد (كم)")
            }
        },
        confirmButton = {
            val value = text.toDoubleOrNull()
            Button(onClick = { value?.let(onSave) }, enabled = value != null && value >= vehicle.currentOdometerKm) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun dashboardReadinessItems(vehicle: VehicleEntity, plans: List<MaintenancePlanEntity>, maintenance: List<MaintenanceRecordEntity>, fuel: List<FuelRecordEntity>, trips: List<TripEntity>, documents: List<VehicleDocumentEntity>) = listOf(
    DataReadinessItem("بيانات المركبة", "ملف المركبة موجود", true, 20),
    DataReadinessItem("العداد", "قراءة عداد فعلية", vehicle.currentOdometerKm > 0, 15),
    DataReadinessItem("خطة الصيانة", "بند صيانة نشط واحد على الأقل", plans.any { it.isActive && !it.isDeleted }, 20),
    DataReadinessItem("سجل الصيانة", "عملية صيانة منفذة واحدة على الأقل", maintenance.any { !it.isDeleted }, 15),
    DataReadinessItem("بيانات الوقود", "3 سجلات استهلاك تعطي أعلى دقة", fuel.count { !it.isDeleted && it.consumptionLitersPer100Km != null } >= 3, 20),
    DataReadinessItem("الرحلات", "3 رحلات تساعد في مؤشرات الاستخدام", trips.count { !it.isDeleted } >= 3, 5),
    DataReadinessItem("المستندات", "رخصة أو مستند له تاريخ انتهاء", documents.any { !it.isDeleted && (it.documentType == DocumentType.VEHICLE_LICENSE || it.expiryDate != null) }, 5)
)

private fun compactDashboardKm(value: Double): String = when {
    value >= 1_000_000 -> "${String.format(Locale.US, "%.1f", value / 1_000_000)}M"
    value >= 1_000 -> "${String.format(Locale.US, "%.1f", value / 1_000)}K"
    else -> value.toLong().toString()
}.replace(".0K", "K").replace(".0M", "M")

private fun compactDashboardMoney(value: Double): String = when {
    value >= 1_000_000 -> "${String.format(Locale.US, "%.1f", value / 1_000_000)}م"
    value >= 1_000 -> "${String.format(Locale.US, "%.1f", value / 1_000)}ألف"
    else -> formatMoney(value)
}.replace(".0م", "م").replace(".0ألف", "ألف")