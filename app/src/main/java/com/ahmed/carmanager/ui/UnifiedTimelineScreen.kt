package com.ahmed.carmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.diagnostics.DiagnosticSessionCodec
import com.ahmed.carmanager.data.inspection.InspectionReportCodec
import com.ahmed.carmanager.data.local.model.*
import java.util.Calendar

private enum class TimelineKind(val label: String, val icon: ImageVector) {
    MAINTENANCE("صيانة", Icons.Default.Build),
    FUEL("وقود", Icons.Default.LocalGasStation),
    EXPENSE("مصروف", Icons.Default.Payments),
    TRIP("رحلة", Icons.Default.Route),
    MILEAGE("عداد", Icons.Default.Speed),
    FAULT("عطل", Icons.Default.WarningAmber),
    DOCUMENT("مستند", Icons.Default.Description),
    ASSET("قطعة", Icons.Default.SettingsSuggest)
}

private enum class TimelineFilter(val label: String) {
    ALL("الكل"), MAINTENANCE("الصيانة"), FUEL("الوقود"), EXPENSE("المصاريف"), TRIP("الرحلات"), MILEAGE("العداد"), OTHER("أخرى")
}

private enum class TimelinePeriod(val label: String) {
    ALL("كل الوقت"), TODAY("اليوم"), DAYS_7("7 أيام"), DAYS_30("30 يوم"), YEAR("هذه السنة")
}

private data class TimelineEvent(
    val id: String,
    val kind: TimelineKind,
    val date: Long,
    val title: String,
    val subtitle: String,
    val odometerKm: Double? = null,
    val amount: Double? = null,
    val countInSpend: Boolean = false
)

@Composable
fun UnifiedTimelineScreen(
    vehicle: VehicleEntity?,
    maintenance: List<MaintenanceRecordEntity>,
    fuel: List<FuelRecordEntity>,
    expenses: List<ExpenseEntity>,
    trips: List<TripEntity>,
    odometer: List<OdometerRecordEntity>,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>,
    parts: List<PartEntity>,
    tires: List<TireEntity>,
    batteries: List<BatteryRecordEntity>
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا لعرض سجلها الكامل.", Icons.Default.History)
        return
    }

    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(TimelineFilter.ALL) }
    var period by remember { mutableStateOf(TimelinePeriod.ALL) }
    val now = System.currentTimeMillis()

    val allEvents = remember(maintenance, fuel, expenses, trips, odometer, faults, documents, parts, tires, batteries) {
        buildTimelineEvents(maintenance, fuel, expenses, trips, odometer, faults, documents, parts, tires, batteries)
    }
    val visible = remember(allEvents, query, typeFilter, period) {
        val cutoff = cutoffFor(period, now)
        allEvents.filter { event ->
            val typeOk = when (typeFilter) {
                TimelineFilter.ALL -> true
                TimelineFilter.MAINTENANCE -> event.kind == TimelineKind.MAINTENANCE
                TimelineFilter.FUEL -> event.kind == TimelineKind.FUEL
                TimelineFilter.EXPENSE -> event.kind == TimelineKind.EXPENSE
                TimelineFilter.TRIP -> event.kind == TimelineKind.TRIP
                TimelineFilter.MILEAGE -> event.kind == TimelineKind.MILEAGE
                TimelineFilter.OTHER -> event.kind in setOf(TimelineKind.FAULT, TimelineKind.DOCUMENT, TimelineKind.ASSET)
            }
            val timeOk = cutoff == null || event.date >= cutoff
            val searchOk = query.isBlank() || event.title.contains(query, true) || event.subtitle.contains(query, true)
            typeOk && timeOk && searchOk
        }
    }
    val spend = visible.filter { it.countInSpend }.sumOf { it.amount ?: 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { PremiumGradientHeader("السجل الذكي", "كل حركات ${vehicle.displayName ?: vehicle.model} في Timeline واحد قابل للبحث", CMIcons.Timeline) }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text("${formatKm(vehicle.currentOdometerKm)} كم • ${visible.size} حركة ظاهرة", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TimelineMetric("التكلفة المسجلة", formatMoney(spend), Modifier.weight(1f))
                        TimelineMetric("كل الحركات", allEvents.size.toString(), Modifier.weight(1f))
                    }
                    Text(
                        "التكلفة تجمع الوقود والصيانة والمصروفات فقط لتجنب احتساب المصدر نفسه مرتين.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("ابحث في السجل") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "مسح البحث") }
                },
                shape = RoundedCornerShape(18.dp)
            )
        }

        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimelineFilter.entries.forEach { filter ->
                    FilterChip(selected = typeFilter == filter, onClick = { typeFilter = filter }, label = { Text(filter.label) })
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimelinePeriod.entries.forEach { item ->
                    FilterChip(selected = period == item, onClick = { period = item }, label = { Text(item.label) })
                }
            }
        }

        if (visible.isEmpty()) {
            item { EmptyState("لا توجد حركات مطابقة", "غيّر البحث أو الفترة أو نوع الحركة، أو ابدأ بتسجيل بيانات المركبة.", Icons.Default.SearchOff) }
        } else {
            items(visible, key = { it.id }) { event -> TimelineEventCard(event) }
        }
    }
}

@Composable
private fun TimelineMetric(title: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TimelineEventCard(event: TimelineEvent) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.Start) {
                event.amount?.let { Text(formatMoney(it), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                Text(formatDate(event.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(event.kind.label) })
                    Spacer(Modifier.width(6.dp))
                    Text(event.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
                if (event.subtitle.isNotBlank()) {
                    Text(event.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                }
                event.odometerKm?.let { Text("العداد: ${formatKm(it)} كم", style = MaterialTheme.typography.labelMedium) }
            }
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(event.kind.icon, null, Modifier.padding(7.dp).size(19.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun buildTimelineEvents(
    maintenance: List<MaintenanceRecordEntity>,
    fuel: List<FuelRecordEntity>,
    expenses: List<ExpenseEntity>,
    trips: List<TripEntity>,
    odometer: List<OdometerRecordEntity>,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>,
    parts: List<PartEntity>,
    tires: List<TireEntity>,
    batteries: List<BatteryRecordEntity>
): List<TimelineEvent> {
    val events = mutableListOf<TimelineEvent>()

    maintenance.filter { !it.isDeleted }.forEach { item ->
        events += TimelineEvent(
            id = "m:${item.id}", kind = TimelineKind.MAINTENANCE, date = item.serviceDate,
            title = item.titleAr,
            subtitle = listOfNotNull(item.category, item.serviceCenter, item.technician).filter { it.isNotBlank() }.joinToString(" • "),
            odometerKm = item.odometerKm, amount = item.totalCost, countInSpend = true
        )
    }
    fuel.filter { !it.isDeleted }.forEach { item ->
        events += TimelineEvent(
            id = "f:${item.id}", kind = TimelineKind.FUEL, date = item.fuelDate,
            title = "تموين ${fuelLabel(item.fuelType)}",
            subtitle = listOfNotNull(item.stationName, "${formatKm(item.liters)} لتر", if (item.isFullTank) "تفويلة كاملة" else "تعبئة جزئية").joinToString(" • "),
            odometerKm = item.odometerKm, amount = item.amountPaid, countInSpend = true
        )
    }
    expenses.filter { !it.isDeleted }.forEach { item ->
        events += TimelineEvent(
            id = "e:${item.id}", kind = TimelineKind.EXPENSE, date = item.expenseDate,
            title = item.descriptionAr,
            subtitle = listOfNotNull(item.merchant, item.notes).filter { it.isNotBlank() }.joinToString(" • "),
            odometerKm = item.odometerKm, amount = item.amount, countInSpend = true
        )
    }
    trips.filter { !it.isDeleted }.forEach { item ->
        val route = listOfNotNull(item.startAddress, item.endAddress).filter { it.isNotBlank() }.joinToString(" ← ")
        events += TimelineEvent(
            id = "t:${item.id}", kind = TimelineKind.TRIP, date = item.startTime,
            title = "رحلة ${tripLabel(item.tripType)}",
            subtitle = listOf(route, "${formatKm(item.distanceKm)} كم").filter { it.isNotBlank() }.joinToString(" • "),
            odometerKm = item.endOdometerKm ?: item.startOdometerKm,
            amount = item.estimatedOperatingCost ?: item.fuelCost,
            countInSpend = false
        )
    }
    odometer.filter {
        !it.isDeleted && it.source in setOf(OdometerSource.MANUAL, OdometerSource.GPS, OdometerSource.IMPORT, OdometerSource.CALIBRATION)
    }.forEach { item ->
        events += TimelineEvent(
            id = "o:${item.id}", kind = TimelineKind.MILEAGE, date = item.recordedAt,
            title = "قراءة عداد — ${odometerSourceLabel(item.source)}",
            subtitle = item.notes.orEmpty(), odometerKm = item.odometerKm
        )
    }
    faults.filter { !it.isDeleted }.forEach { item ->
        events += TimelineEvent(
            id = "x:${item.id}", kind = TimelineKind.FAULT, date = item.reportedDate,
            title = item.symptomAr,
            subtitle = item.diagnosisAr ?: faultStatusLabel(item.status),
            odometerKm = item.odometerKm, amount = item.repairCost
        )
    }
    documents.filter { !it.isDeleted && !DiagnosticSessionCodec.isSessionDocument(it) }.forEach { item ->
        val inspection = if (item.documentType == DocumentType.INSPECTION) InspectionReportCodec.parse(item.notes) else null
        val subtitle = if (inspection != null) {
            "${inspection.mode.labelAr} • النتيجة ${inspection.score}% • متابعة ${inspection.attentionCount} • حرج ${inspection.criticalCount}"
        } else {
            listOfNotNull(
                item.documentNumber,
                item.expiryDate?.let { "ينتهي ${formatDate(it)}" },
                item.notes?.takeIf { it.length <= 180 }
            ).filter { it.isNotBlank() }.joinToString(" • ")
        }
        events += TimelineEvent(
            id = "d:${item.id}", kind = TimelineKind.DOCUMENT, date = item.issueDate ?: item.createdAt,
            title = documentLabel(item.documentType), subtitle = subtitle
        )
    }
    parts.filter { !it.isDeleted }.forEach { item ->
        events += TimelineEvent(
            id = "p:${item.id}", kind = TimelineKind.ASSET, date = item.installDate ?: item.purchaseDate ?: item.createdAt,
            title = "تركيب ${item.nameAr}",
            subtitle = listOfNotNull(item.brand, item.partNumber, item.supplier).filter { it.isNotBlank() }.joinToString(" • "),
            odometerKm = item.installOdometerKm, amount = item.cost
        )
    }
    tires.filter { !it.isDeleted }.forEach { item ->
        events += TimelineEvent(
            id = "r:${item.id}", kind = TimelineKind.ASSET, date = item.installDate ?: item.createdAt,
            title = "إطار ${tirePositionLabel(item.position)}",
            subtitle = listOfNotNull(item.brand, item.model, item.size, item.manufactureDateText).filter { it.isNotBlank() }.joinToString(" • "),
            odometerKm = item.installOdometerKm, amount = item.cost
        )
    }
    batteries.filter { !it.isDeleted }.forEach { item ->
        events += TimelineEvent(
            id = "b:${item.id}", kind = TimelineKind.ASSET, date = item.installDate ?: item.purchaseDate ?: item.createdAt,
            title = "تركيب بطارية",
            subtitle = listOfNotNull(item.brand, item.model, item.capacityAh?.let { "${formatKm(it)} Ah" }).filter { it.isNotBlank() }.joinToString(" • "),
            odometerKm = item.installOdometerKm, amount = item.cost
        )
    }

    return events.sortedByDescending { it.date }
}

private fun cutoffFor(period: TimelinePeriod, now: Long): Long? = when (period) {
    TimelinePeriod.ALL -> null
    TimelinePeriod.TODAY -> Calendar.getInstance().run {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    TimelinePeriod.DAYS_7 -> now - 7L * DAY_MS
    TimelinePeriod.DAYS_30 -> now - 30L * DAY_MS
    TimelinePeriod.YEAR -> Calendar.getInstance().run {
        timeInMillis = now
        set(Calendar.MONTH, Calendar.JANUARY); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}

private fun fuelLabel(value: FuelType): String = when (value) {
    FuelType.GASOLINE_80 -> "بنزين 80"
    FuelType.GASOLINE_92 -> "بنزين 92"
    FuelType.GASOLINE_95 -> "بنزين 95"
    FuelType.DIESEL -> "ديزل"
    FuelType.ELECTRIC -> "شحن كهربائي"
    FuelType.HYBRID -> "هجين"
    FuelType.OTHER -> "وقود"
}

private fun tripLabel(value: TripType): String = when (value) {
    TripType.PERSONAL -> "شخصية"
    TripType.WORK -> "عمل"
    TripType.TRAVEL -> "سفر"
    TripType.SERVICE -> "صيانة"
    TripType.OTHER -> "أخرى"
}

private fun odometerSourceLabel(value: OdometerSource): String = when (value) {
    OdometerSource.MANUAL -> "يدوي"
    OdometerSource.GPS -> "GPS"
    OdometerSource.IMPORT -> "استيراد"
    OdometerSource.CALIBRATION -> "معايرة"
    OdometerSource.SERVICE -> "صيانة"
    OdometerSource.FUEL -> "وقود"
    OdometerSource.TRIP -> "رحلة"
}

private fun faultStatusLabel(value: FaultStatus): String = when (value) {
    FaultStatus.OPEN -> "مفتوح"
    FaultStatus.DIAGNOSED -> "تم التشخيص"
    FaultStatus.IN_REPAIR -> "قيد الإصلاح"
    FaultStatus.RESOLVED -> "تم الحل"
    FaultStatus.CLOSED -> "مغلق"
}

private fun documentLabel(value: DocumentType): String = when (value) {
    DocumentType.VEHICLE_LICENSE -> "رخصة المركبة"
    DocumentType.INSURANCE -> "التأمين"
    DocumentType.INSPECTION -> "الفحص"
    DocumentType.CONTRACT -> "عقد"
    DocumentType.RECEIPT -> "إيصال"
    DocumentType.OTHER -> "مستند"
}

private fun tirePositionLabel(value: TirePosition): String = when (value) {
    TirePosition.FRONT_LEFT -> "أمامي يسار"
    TirePosition.FRONT_RIGHT -> "أمامي يمين"
    TirePosition.REAR_LEFT -> "خلفي يسار"
    TirePosition.REAR_RIGHT -> "خلفي يمين"
    TirePosition.SPARE -> "احتياطي"
    TirePosition.UNASSIGNED -> "غير محدد"
}

private const val DAY_MS = 86_400_000L
