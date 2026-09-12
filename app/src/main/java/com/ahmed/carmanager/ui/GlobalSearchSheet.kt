package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.*

private data class SearchEntry(
    val title: String,
    val subtitle: String,
    val type: String,
    val icon: ImageVector,
    val haystack: String,
    val action: SearchAction
)

private enum class SearchAction { MAINTENANCE, FAULTS, DOCUMENTS, FUEL, TRIPS, EXPENSES, PARTS, TIMELINE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchSheet(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    maintenance: List<MaintenanceRecordEntity>,
    fuel: List<FuelRecordEntity>,
    expenses: List<ExpenseEntity>,
    trips: List<TripEntity>,
    parts: List<PartEntity>,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>,
    onOpenMaintenance: () -> Unit,
    onOpenFaults: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenFuel: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenParts: () -> Unit,
    onOpenTimeline: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val entries = remember(vehicle, plans, maintenance, fuel, expenses, trips, parts, faults, documents) {
        buildSearchEntries(vehicle, plans, maintenance, fuel, expenses, trips, parts, faults, documents)
    }
    val normalized = query.trim().lowercase()
    val results = remember(entries, normalized) {
        if (normalized.length < 2) emptyList()
        else entries.filter { it.haystack.contains(normalized) }.take(40)
    }

    fun open(action: SearchAction) {
        onDismiss()
        when (action) {
            SearchAction.MAINTENANCE -> onOpenMaintenance()
            SearchAction.FAULTS -> onOpenFaults()
            SearchAction.DOCUMENTS -> onOpenDocuments()
            SearchAction.FUEL -> onOpenFuel()
            SearchAction.TRIPS -> onOpenTrips()
            SearchAction.EXPENSES -> onOpenExpenses()
            SearchAction.PARTS -> onOpenParts()
            SearchAction.TIMELINE -> onOpenTimeline()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            PremiumSectionTitle(
                "البحث الشامل",
                vehicle?.let { "ابحث داخل ملف ${it.displayName ?: "${it.brand} ${it.model}"}" }
                    ?: "ابحث في بيانات المركبة الحالية"
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("صيانة، قطعة/OEM، عطل، محطة، رحلة، مستند...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "مسح") }
                }
            )
            Spacer(Modifier.height(10.dp))

            when {
                query.isBlank() -> SearchHintGrid(
                    onMaintenance = { open(SearchAction.MAINTENANCE) },
                    onFaults = { open(SearchAction.FAULTS) },
                    onFuel = { open(SearchAction.FUEL) },
                    onParts = { open(SearchAction.PARTS) }
                )
                normalized.length < 2 -> EmptyState("اكتب حرفين على الأقل", "سنبحث في كل سجل المركبة من مكان واحد.", Icons.Rounded.Search)
                results.isEmpty() -> Column(horizontalAlignment = Alignment.End) {
                    EmptyState("لا توجد نتائج محلية", "يمكنك فتح بحث قطع الغيار والأسعار للبحث في المصادر الخارجية.", Icons.Rounded.SearchOff)
                    FilledTonalButton(onClick = { open(SearchAction.PARTS) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(CMIcons.Parts, null); Spacer(Modifier.width(6.dp)); Text("بحث قطع الغيار والأسعار")
                    }
                }
                else -> {
                    Text("${results.size} نتيجة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(bottom = 18.dp)
                    ) {
                        items(results) { item ->
                            Surface(
                                onClick = { open(item.action) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PremiumIconBadge(item.icon, size = 38)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            PremiumStatusPill(item.type)
                                            Spacer(Modifier.width(7.dp))
                                            Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                                        }
                                        if (item.subtitle.isNotBlank()) {
                                            Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    PremiumChevron()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHintGrid(onMaintenance: () -> Unit, onFaults: () -> Unit, onFuel: () -> Unit, onParts: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("وصول سريع", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AutomotiveFeatureTile("الصيانة", "الخطة والسجل", CMIcons.Maintenance, AutoTone.CORAL, Modifier.weight(1f), onMaintenance)
            AutomotiveFeatureTile("الأعطال", "تشخيص وإصلاح", CMIcons.Fault, AutoTone.RED, Modifier.weight(1f), onFaults)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AutomotiveFeatureTile("الوقود", "تموين واستهلاك", CMIcons.Fuel, AutoTone.BLUE, Modifier.weight(1f), onFuel)
            AutomotiveFeatureTile("قطع الغيار", "اسم، OEM وأسعار", CMIcons.Parts, AutoTone.AMBER, Modifier.weight(1f), onParts)
        }
        Spacer(Modifier.height(18.dp))
    }
}

private fun buildSearchEntries(
    vehicle: VehicleEntity?,
    plans: List<MaintenancePlanEntity>,
    maintenance: List<MaintenanceRecordEntity>,
    fuel: List<FuelRecordEntity>,
    expenses: List<ExpenseEntity>,
    trips: List<TripEntity>,
    parts: List<PartEntity>,
    faults: List<FaultRecordEntity>,
    documents: List<VehicleDocumentEntity>
): List<SearchEntry> = buildList {
    vehicle?.let {
        add(SearchEntry(
            title = it.displayName ?: "${it.brand} ${it.model}",
            subtitle = "${it.year} • ${formatKm(it.currentOdometerKm)} كم${it.plateNumber?.let { p -> " • $p" } ?: ""}",
            type = "المركبة",
            icon = Icons.Rounded.DirectionsCar,
            haystack = listOf(it.displayName, it.brand, it.model, it.plateNumber, it.vin, it.year.toString()).joinToString(" ").lowercase(),
            action = SearchAction.TIMELINE
        ))
    }
    plans.filter { !it.isDeleted }.forEach {
        add(SearchEntry(it.titleAr, "خطة صيانة • ${it.estimatedCost?.let(::formatMoney) ?: "بدون سعر"}", "صيانة", Icons.Rounded.BuildCircle,
            listOf(it.titleAr, it.category, it.notes).joinToString(" ").lowercase(), SearchAction.MAINTENANCE))
    }
    maintenance.filter { !it.isDeleted }.forEach {
        add(SearchEntry(it.titleAr, "${formatDate(it.serviceDate)} • ${formatMoney(it.totalCost)}${it.serviceCenter?.let { s -> " • $s" } ?: ""}", "سجل صيانة", Icons.Rounded.Handyman,
            listOf(it.titleAr, it.category, it.serviceCenter, it.technician, it.invoiceNumber, it.notes).joinToString(" ").lowercase(), SearchAction.MAINTENANCE))
    }
    parts.filter { !it.isDeleted }.forEach {
        add(SearchEntry(
            it.nameAr,
            listOfNotNull(it.partNumber?.let { number -> "OEM/رقم $number" }, it.brand, it.cost?.let(::formatMoney)).joinToString(" • "),
            "قطعة",
            CMIcons.Parts,
            listOf(it.nameAr, it.category, it.brand, it.partNumber, it.supplier, it.notes).joinToString(" ").lowercase(),
            SearchAction.PARTS
        ))
    }
    faults.filter { !it.isDeleted }.forEach {
        add(SearchEntry(it.symptomAr, "${it.severity.arLabel()} • ${it.diagnosisAr ?: "بدون تشخيص نهائي"}", "عطل", Icons.Rounded.WarningAmber,
            listOf(it.symptomAr, it.diagnosisAr, it.notes, it.severity.name, it.status.name).joinToString(" ").lowercase(), SearchAction.FAULTS))
    }
    documents.filter { !it.isDeleted }.forEach {
        add(SearchEntry(it.documentType.arLabel(), it.documentNumber ?: it.expiryDate?.let { d -> "ينتهي ${formatDate(d)}" } ?: "مستند محفوظ", "مستند", Icons.Rounded.Description,
            listOf(it.documentType.arLabel(), it.documentNumber, it.notes).joinToString(" ").lowercase(), SearchAction.DOCUMENTS))
    }
    fuel.filter { !it.isDeleted }.forEach {
        add(SearchEntry(it.stationName ?: it.fuelType.arLabel(), "${formatDate(it.fuelDate)} • ${formatMoney(it.amountPaid)} • ${formatLiters(it.liters)}", "وقود", Icons.Rounded.LocalGasStation,
            listOf(it.stationName, it.fuelType.arLabel(), it.notes, it.amountPaid.toString()).joinToString(" ").lowercase(), SearchAction.FUEL))
    }
    expenses.filter { !it.isDeleted }.forEach {
        add(SearchEntry(it.descriptionAr, "${it.category.arLabel()} • ${formatMoney(it.amount)}${it.merchant?.let { m -> " • $m" } ?: ""}", "مصروف", Icons.Rounded.Payments,
            listOf(it.descriptionAr, it.category.arLabel(), it.merchant, it.paymentMethod, it.notes).joinToString(" ").lowercase(), SearchAction.EXPENSES))
    }
    trips.filter { !it.isDeleted }.forEach {
        add(SearchEntry(it.tripType.arLabel(), "${formatDate(it.startTime)} • ${formatKm(it.distanceKm)} كم • ${it.startAddress ?: ""}", "رحلة", Icons.Rounded.Route,
            listOf(it.tripType.arLabel(), it.startAddress, it.endAddress, it.notes).joinToString(" ").lowercase(), SearchAction.TRIPS))
    }
}
