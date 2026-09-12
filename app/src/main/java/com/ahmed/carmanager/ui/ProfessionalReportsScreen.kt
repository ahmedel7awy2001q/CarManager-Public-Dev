package com.ahmed.carmanager.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private enum class ReportPeriod(val label: String) {
    DAYS_30("30 يوم"), DAYS_90("3 أشهر"), YEAR("السنة"), ALL("كل الوقت")
}

private data class CostSlice(val title: String, val value: Double, val icon: ImageVector, val tone: AutoTone)
private data class MonthlyCost(val key: String, val label: String, val value: Double)

@Composable
fun ProfessionalReportsScreen(
    vehicle: VehicleEntity?,
    fuel: List<FuelRecordEntity>,
    maintenance: List<MaintenanceRecordEntity>,
    expenses: List<ExpenseEntity>,
    trips: List<TripEntity>,
    odometer: List<OdometerRecordEntity>
) {
    if (vehicle == null) {
        EmptyState("لا توجد سيارة محددة", "اختر سيارة أولًا لعرض التقارير.", Icons.Rounded.Assessment)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(ReportPeriod.DAYS_30) }
    val now = System.currentTimeMillis()
    val cutoff = remember(period) { reportCutoff(period, now) }
    val f = remember(fuel, cutoff) { fuel.filter { !it.isDeleted && (cutoff == null || it.fuelDate >= cutoff) } }
    val m = remember(maintenance, cutoff) { maintenance.filter { !it.isDeleted && (cutoff == null || it.serviceDate >= cutoff) } }
    val e = remember(expenses, cutoff) { expenses.filter { !it.isDeleted && (cutoff == null || it.expenseDate >= cutoff) } }
    val t = remember(trips, cutoff) { trips.filter { !it.isDeleted && (cutoff == null || it.startTime >= cutoff) } }
    val o = remember(odometer, cutoff) { odometer.filter { !it.isDeleted && (cutoff == null || it.recordedAt >= cutoff) } }

    // Aggregate only when the filtered source list changes, not when a chart/button recomposes.
    val fuelCost = remember(f) { f.sumOf { it.amountPaid } }
    val maintenanceCost = remember(m) { m.sumOf { it.totalCost } }
    val expenseCost = remember(e) { e.sumOf { it.amount } }
    val total = fuelCost + maintenanceCost + expenseCost
    val distance = remember(vehicle.currentOdometerKm, vehicle.purchaseOdometerKm, period, o, t) {
        reportDistance(vehicle, period, o, t)
    }
    val costPerKm = remember(distance, total) { distance?.takeIf { it > 0 }?.let { total / it } }
    val consumption = remember(f) {
        f.mapNotNull { it.consumptionLitersPer100Km }.takeIf { it.isNotEmpty() }?.average()
    }
    val slices = remember(fuelCost, maintenanceCost, expenseCost) {
        listOf(
            CostSlice("الوقود", fuelCost, CMIcons.Fuel, AutoTone.BLUE),
            CostSlice("الصيانة", maintenanceCost, CMIcons.Maintenance, AutoTone.CORAL),
            CostSlice("مصاريف أخرى", expenseCost, CMIcons.Expense, AutoTone.AMBER)
        )
    }
    val monthly = remember(f, m, e) { buildMonthlyCosts(f, m, e).takeLast(6) }
    val topMaintenance = remember(m) {
        m.groupBy { it.titleAr }
            .mapValues { (_, rows) -> rows.sumOf { it.totalCost } }
            .entries.sortedByDescending { it.value }.take(5)
    }
    val topExpenseCategories = remember(e) {
        e.groupBy { it.category }
            .mapValues { (_, rows) -> rows.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(5)
    }
    val exportData = remember(vehicle, period, f, m, e, t, total, fuelCost, maintenanceCost, expenseCost, distance, costPerKm, consumption) {
        VehicleReportExportData(
            vehicle = vehicle,
            periodLabel = period.label,
            totalCost = total,
            fuelCost = fuelCost,
            maintenanceCost = maintenanceCost,
            otherCost = expenseCost,
            distanceKm = distance,
            costPerKm = costPerKm,
            consumptionL100 = consumption,
            fuel = f,
            maintenance = m,
            expenses = e,
            trips = t
        )
    }

    fun showExportResult(success: Boolean, type: String) {
        Toast.makeText(context, if (success) "تم تصدير تقرير $type بنجاح." else "تعذر تصدير تقرير $type.", Toast.LENGTH_SHORT).show()
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { VehicleReportExporter.exportPdf(context, uri, exportData) } }
            showExportResult(result.isSuccess, "PDF")
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { VehicleReportExporter.exportCsv(context, uri, exportData) } }
            showExportResult(result.isSuccess, "CSV / Excel")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CMPremium.ScreenPadding, 8.dp, CMPremium.ScreenPadding, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ReportsCockpitHeader(vehicle = vehicle, total = total, periodLabel = period.label) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReportPeriod.entries.forEach { item ->
                    FilterChip(
                        selected = period == item,
                        onClick = { period = item },
                        label = { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (period == item) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(13.dp)) }
                        } else null
                    )
                }
            }
        }

        item {
            ReportHeroCard(
                vehicle = vehicle,
                total = total,
                distance = distance,
                costPerKm = costPerKm,
                consumption = consumption,
                onPdf = { pdfLauncher.launch("CarManager-${vehicle.brand}-${vehicle.model}.pdf") },
                onCsv = { csvLauncher.launch("CarManager-${vehicle.brand}-${vehicle.model}.csv") }
            )
        }

        item { ReportSectionTitle("توزيع التكلفة", "المبالغ المسجلة فعليًا خلال ${period.label}") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val donutColors = slices.map { autoToneColors(it.tone).strong }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AutomotiveDonutChart(
                            values = slices.map { it.value },
                            colors = donutColors,
                            centerValue = if (total > 0) compactReportMoney(total) else "—",
                            centerLabel = "الإجمالي",
                            modifier = Modifier.size(106.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            slices.forEach { slice -> ReportLegendRow(slice, total) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    slices.forEach { slice -> CostBar(slice, total) }
                }
            }
        }

        if (monthly.size >= 2) {
            item { ReportSectionTitle("اتجاه المصروفات", "آخر الأشهر التي تحتوي على بيانات حقيقية") }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        val maxValue = monthly.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
                        monthly.forEach { row -> MonthlyCostRow(row, maxValue) }
                    }
                }
            }
        }

        item { ReportSectionTitle("مؤشرات التشغيل", "عدد العمليات المسجلة داخل الفترة الحالية") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ReportInfoCard("التموين", f.size.toString(), CMIcons.Fuel, AutoTone.BLUE, Modifier.weight(1f))
                ReportInfoCard("الصيانة", m.size.toString(), CMIcons.Maintenance, AutoTone.CORAL, Modifier.weight(1f))
                ReportInfoCard("المصاريف", e.size.toString(), CMIcons.Expense, AutoTone.AMBER, Modifier.weight(1f))
                ReportInfoCard("الرحلات", t.size.toString(), CMIcons.Trip, AutoTone.VIOLET, Modifier.weight(1f))
            }
        }

        if (topMaintenance.isNotEmpty()) {
            item { ReportSectionTitle("أعلى بنود الصيانة تكلفة", "مرتبة حسب السجل الفعلي") }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        topMaintenance.forEachIndexed { index, entry ->
                            RankingRow(index + 1, entry.key, formatMoney(entry.value))
                        }
                    }
                }
            }
        }

        if (topExpenseCategories.isNotEmpty()) {
            item { ReportSectionTitle("تفاصيل المصروفات الأخرى", "أعلى التصنيفات خلال الفترة") }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        topExpenseCategories.forEachIndexed { index, entry ->
                            RankingRow(index + 1, entry.key.arLabel(), formatMoney(entry.value))
                        }
                    }
                }
            }
        }

        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .60f)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "تكلفة الكيلومتر لا تظهر إلا عند توفر مسافة قابلة للحساب من العداد أو الرحلات. لا نرفع العداد تلقائيًا ولا نضيف تقديرات غير مسجلة. PDF وCSV يُنشآن محليًا عند الطلب.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsCockpitHeader(vehicle: VehicleEntity, total: Double, periodLabel: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF171326), androidx.compose.ui.graphics.Color(0xFF29204A), androidx.compose.ui.graphics.Color(0xFF142A35))))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .08f)) {
                    Icon(CMIcons.Reports, null, Modifier.padding(8.dp).size(22.dp), tint = androidx.compose.ui.graphics.Color(0xFFC6B5FF))
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("التقارير والتحليلات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
                    Text("${vehicle.displayName ?: vehicle.model} • $periodLabel", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .64f))
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(formatMoney(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.weight(1f))
                Text("إجمالي الإنفاق المسجل", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .60f))
            }
        }
    }
}

@Composable
private fun ReportHeroCard(
    vehicle: VehicleEntity,
    total: Double,
    distance: Double?,
    costPerKm: Double?,
    consumption: Double?,
    onPdf: () -> Unit,
    onCsv: () -> Unit
) {
    val brush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.surface
        )
    )
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(brush)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
                    Icon(Icons.Rounded.Insights, null, Modifier.padding(7.dp).size(19.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("إجمالي تكلفة الفترة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(formatMoney(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ReportHeroMetric("المسافة", distance?.let { "${formatKm(it)} كم" } ?: "غير كافية", Icons.Rounded.Route, Modifier.weight(1f))
                ReportHeroMetric("تكلفة/كم", costPerKm?.let { "${formatKm(it)} ج.م" } ?: "—", Icons.Rounded.Payments, Modifier.weight(1f))
                ReportHeroMetric("الاستهلاك", consumption?.let { "${formatKm(it)} ل/100كم" } ?: "—", Icons.Rounded.LocalGasStation, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onCsv,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.TableView, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CSV / Excel", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onPdf,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ReportHeroMetric(title: String, value: String, icon: ImageVector, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(3.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReportLegendRow(slice: CostSlice, total: Double) {
    val c = autoToneColors(slice.tone)
    val percent = if (total > 0) ((slice.value / total) * 100.0).roundToInt() else 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c.strong))
        Spacer(Modifier.width(6.dp))
        Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = c.strong)
        Spacer(Modifier.weight(1f))
        Text(slice.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun compactReportMoney(value: Double): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000)
    else -> value.toLong().toString()
}

@Composable
private fun CostBar(slice: CostSlice, total: Double) {
    val progress = if (total > 0) (slice.value / total).toFloat().coerceIn(0f, 1f) else 0f
    val percent = (progress * 100f).roundToInt()
    val tone = autoToneColors(slice.tone)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(9.dp), color = tone.soft) {
                Icon(slice.icon, null, Modifier.padding(6.dp).size(16.dp), tint = tone.strong)
            }
            Spacer(Modifier.width(7.dp))
            Text(formatMoney(slice.value), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(slice.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("$percent% من الإجمالي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            color = tone.strong,
            trackColor = tone.soft
        )
    }
}

@Composable
private fun MonthlyCostRow(row: MonthlyCost, maxValue: Double) {
    val progress = (row.value / maxValue).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatMoney(row.value), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun ReportInfoCard(title: String, value: String, icon: ImageVector, tone: AutoTone, modifier: Modifier) {
    val c = autoToneColors(tone)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = c.soft),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.strong.copy(alpha = .14f))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(17.dp), tint = c.strong)
            Spacer(Modifier.height(3.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun RankingRow(rank: Int, title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(rank.toString(), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(7.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        Text(title, Modifier.weight(1.4f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReportSectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalAlignment = Alignment.End) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun reportCutoff(period: ReportPeriod, now: Long): Long? = when (period) {
    ReportPeriod.DAYS_30 -> now - 30L * DAY_MS_REPORT
    ReportPeriod.DAYS_90 -> now - 90L * DAY_MS_REPORT
    ReportPeriod.YEAR -> Calendar.getInstance().run {
        timeInMillis = now
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    ReportPeriod.ALL -> null
}

private fun reportDistance(vehicle: VehicleEntity, period: ReportPeriod, odometer: List<OdometerRecordEntity>, trips: List<TripEntity>): Double? {
    val recorded = odometer.map { it.odometerKm }.distinct().sorted()
    if (recorded.size >= 2) return (recorded.last() - recorded.first()).takeIf { it > 0 }
    val tripDistance = trips.sumOf { it.distanceKm }.takeIf { it > 0 }
    if (tripDistance != null) return tripDistance
    if (period == ReportPeriod.ALL) {
        return vehicle.purchaseOdometerKm?.let { (vehicle.currentOdometerKm - it).takeIf { d -> d > 0 } }
    }
    return null
}

private fun buildMonthlyCosts(
    fuel: List<FuelRecordEntity>,
    maintenance: List<MaintenanceRecordEntity>,
    expenses: List<ExpenseEntity>
): List<MonthlyCost> {
    val values = linkedMapOf<String, Double>()
    val keyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    fun add(date: Long, amount: Double) {
        val key = keyFormat.format(Date(date))
        values[key] = (values[key] ?: 0.0) + amount
    }
    fuel.forEach { add(it.fuelDate, it.amountPaid) }
    maintenance.forEach { add(it.serviceDate, it.totalCost) }
    expenses.forEach { add(it.expenseDate, it.amount) }
    val labelFormat = SimpleDateFormat("MMM yyyy", Locale("ar", "EG"))
    val parser = SimpleDateFormat("yyyy-MM", Locale.US)
    return values.toSortedMap().map { (key, value) ->
        val label = runCatching { parser.parse(key)?.let(labelFormat::format) }.getOrNull() ?: key
        MonthlyCost(key, label, max(0.0, value))
    }
}

private const val DAY_MS_REPORT = 86_400_000L
