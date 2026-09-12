package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.DriveEta
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.TripQuoteEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.repository.TripQuoteInput
import kotlin.math.max

/**
 * Canonical route-aware trip quotation experience.
 *
 * Route-aware quote assistant. Start/end points can be selected on the map and the driving
 * distance is calculated by a routing provider, while the resulting distance always remains
 * editable so the user can override unusual roads, detours or a known operational distance.
 * All money shown remains traceable to a visible input or stored vehicle data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripPricingAssistantSheet(
    vehicle: VehicleEntity,
    onDismiss: () -> Unit,
    appViewModel: CarManagerViewModel = viewModel()
) {
    val fuelRecords by appViewModel.fuelRecords.collectAsStateWithLifecycle()
    val plans by appViewModel.maintenancePlans.collectAsStateWithLifecycle()
    val savedQuotes by appViewModel.tripQuotes.collectAsStateWithLifecycle()

    val recentConsumption = remember(fuelRecords) {
        fuelRecords.asSequence()
            .filter { !it.isDeleted }
            .sortedByDescending { it.fuelDate }
            .mapNotNull { it.consumptionLitersPer100Km?.takeIf { value -> value > 0.0 } }
            .take(5)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.average()
    }
    val latestFuelPrice = remember(fuelRecords) {
        fuelRecords.asSequence()
            .filter { !it.isDeleted && it.pricePerLiter > 0.0 }
            .maxByOrNull { it.fuelDate }
            ?.pricePerLiter
    }
    val maintenancePerKm = remember(plans) { quoteMaintenanceReservePerKm(plans) }
    val maintenanceItems = remember(plans) {
        plans.count { !it.isDeleted && it.isActive && (it.intervalKm ?: 0.0) > 0.0 && (it.estimatedCost ?: 0.0) > 0.0 }
    }

    var roundTrip by remember { mutableStateOf(true) }
    var oneWayDistanceText by remember { mutableStateOf("") }
    var passengerCountText by remember { mutableStateOf("1") }
    var capacityText by remember(vehicle.vehicleId) { mutableStateOf(vehicle.passengerCapacity?.toString().orEmpty()) }
    var waitingHoursText by remember { mutableStateOf("") }
    var waitingRateText by remember { mutableStateOf("") }
    var tollsText by remember { mutableStateOf("") }
    var driverExpenseText by remember { mutableStateOf("") }
    var consumptionText by remember(recentConsumption) { mutableStateOf(recentConsumption?.let(::quoteTrimNumber).orEmpty()) }
    var fuelPriceText by remember(latestFuelPrice) { mutableStateOf(latestFuelPrice?.let(::quoteTrimNumber).orEmpty()) }
    var marginText by remember { mutableStateOf("20") }
    var marketOfferText by remember { mutableStateOf("") }
    var includeMaintenance by remember { mutableStateOf(true) }
    var includeAnnualFixed by remember(vehicle.vehicleId) { mutableStateOf(vehicle.includeAnnualFixedCostsInTripCost) }
    var includeDepreciation by remember(vehicle.vehicleId) { mutableStateOf(vehicle.includeDepreciationInTripCost) }
    var showSavedQuotes by remember { mutableStateOf(false) }
    var pendingConvertQuote by remember { mutableStateOf<TripQuoteEntity?>(null) }

    val oneWayDistance = oneWayDistanceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val passengerCount = passengerCountText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val totalSeats = capacityText.toIntOrNull()?.takeIf { it > 0 }
    val waitingHours = waitingHoursText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val waitingRate = waitingRateText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val tolls = tollsText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val driverExpense = driverExpenseText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val consumption = consumptionText.toDoubleOrNull()?.takeIf { it > 0.0 }
    val fuelPrice = fuelPriceText.toDoubleOrNull()?.takeIf { it > 0.0 }

    val annualKm = vehicle.annualDistanceKm?.takeIf { it > 0.0 }
    val annualFixedTotal = listOfNotNull(vehicle.annualLicenseCost, vehicle.annualInsuranceCost, vehicle.annualOtherFixedCost)
        .filter { it > 0.0 }.sum()
    val annualFixedPerKm = if (annualKm != null) annualFixedTotal / annualKm else 0.0
    val annualDepreciation = if ((vehicle.currentMarketValue ?: 0.0) > 0.0 && (vehicle.depreciationAnnualPercent ?: 0.0) > 0.0) {
        vehicle.currentMarketValue!! * vehicle.depreciationAnnualPercent!! / 100.0
    } else 0.0
    val depreciationPerKm = if (annualKm != null) annualDepreciation / annualKm else 0.0
    val marginPercent = (marginText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 500.0)
    val marketOffer = marketOfferText.toDoubleOrNull()?.takeIf { it >= 0.0 }

    val pricing = TripPricingEngine.calculate(
        TripPricingInput(
            oneWayDistanceKm = oneWayDistance,
            roundTrip = roundTrip,
            passengerCount = passengerCount,
            totalSeatCapacity = totalSeats,
            waitingHours = waitingHours,
            waitingRatePerHour = waitingRate,
            tolls = tolls,
            driverExpense = driverExpense,
            consumptionLitersPer100Km = consumption,
            fuelPricePerLiter = fuelPrice,
            maintenancePerKm = maintenancePerKm,
            includeMaintenance = includeMaintenance,
            annualFixedPerKm = annualFixedPerKm,
            includeAnnualFixed = includeAnnualFixed,
            depreciationPerKm = depreciationPerKm,
            includeDepreciation = includeDepreciation,
            profitMarginPercent = marginPercent,
            marketOffer = marketOffer
        )
    )
    val totalDistance = pricing.totalDistanceKm
    val capacityExceeded = pricing.capacityExceeded
    val requiredCars = pricing.requiredCars
    val waitingCost = pricing.waitingCost
    val fuelCost = pricing.fuelCost
    val maintenanceShare = pricing.maintenanceShare
    val annualFixedShare = pricing.annualFixedShare
    val depreciationShare = pricing.depreciationShare
    val trueTripCost = pricing.trueTripCost
    val suggestedQuote = pricing.suggestedQuote
    val perPassengerQuote = pricing.perPassengerQuote
    val marketProfit = pricing.marketProfit

    val waitingComplete = waitingHours <= 0.0 || waitingRate > 0.0
    val readiness = remember(
        oneWayDistance, consumption, fuelPrice, maintenanceItems,
        passengerCount, totalSeats, includeAnnualFixed, annualKm, annualFixedTotal,
        includeDepreciation, annualDepreciation, waitingComplete
    ) {
        var score = 0
        if (oneWayDistance > 0.0) score += 40
        if (consumption != null && fuelPrice != null) score += 20
        if (!includeMaintenance || maintenanceItems > 0) score += 10
        if (passengerCount >= 0 && totalSeats != null) score += 10
        if (!includeAnnualFixed || (annualKm != null && annualFixedTotal > 0.0)) score += 8
        if (!includeDepreciation || (annualKm != null && annualDepreciation > 0.0)) score += 7
        if (waitingComplete) score += 5
        score.coerceIn(0, 100)
    }

    fun loadSavedQuote(quote: TripQuoteEntity) {
        roundTrip = quote.roundTrip
        oneWayDistanceText = quoteTrimNumber(quote.oneWayDistanceKm)
        passengerCountText = quote.passengerCount.toString()
        capacityText = quote.totalSeatCapacity?.toString().orEmpty()
        waitingHoursText = quoteTrimNumber(quote.waitingHours)
        waitingRateText = quoteTrimNumber(quote.waitingRatePerHour)
        tollsText = quoteTrimNumber(quote.tolls)
        driverExpenseText = quoteTrimNumber(quote.driverExpense)
        consumptionText = quote.consumptionLitersPer100Km?.let(::quoteTrimNumber).orEmpty()
        fuelPriceText = quote.fuelPricePerLiter?.let(::quoteTrimNumber).orEmpty()
        marginText = quoteTrimNumber(quote.profitMarginPercent)
        marketOfferText = quote.marketOffer?.let(::quoteTrimNumber).orEmpty()
        includeMaintenance = quote.includeMaintenance
        includeAnnualFixed = quote.includeAnnualFixed
        includeDepreciation = quote.includeDepreciation
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Calculate, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("مساعد تسعير المشوار", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(
                        vehicle.displayName ?: "${vehicle.brand} ${vehicle.model} ${vehicle.year}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            QuoteInfoCard(readiness)

            QuoteSection("المسافة", Icons.Default.Route) {
                Text(
                    "أدخل مسافة الاتجاه الواحد بالكيلومتر. لا يعتمد التسعير على GPS أو الخريطة؛ ويمكنك تعديل الرقم في أي وقت.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                AppField(
                    oneWayDistanceText,
                    { oneWayDistanceText = numericInput(it) },
                    "مسافة الاتجاه الواحد (كم)",
                    keyboardType = KeyboardType.Decimal
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !roundTrip,
                        onClick = { roundTrip = false },
                        label = { Text("ذهاب فقط") },
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp)
                    )
                    FilterChip(
                        selected = roundTrip,
                        onClick = { roundTrip = true },
                        label = { Text("ذهاب وعودة") },
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp)
                    )
                }
                if (totalDistance > 0.0) {
                    Text(
                        "إجمالي مسافة التشغيل: ${formatKm(totalDistance)} كم",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "المسافة تخص عرض السعر فقط. حفظ العرض لا يغير عداد السيارة؛ العداد يتغير فقط عند تسجيل المشوار كرحلة منفذة.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }

            QuoteSection("الركاب وسعة السيارة", Icons.Default.Person) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(passengerCountText, { passengerCountText = quoteDigitsOnly(it) }, "عدد الركاب", Modifier.weight(1f), KeyboardType.Number)
                    AppField(capacityText, { capacityText = quoteDigitsOnly(it) }, "إجمالي المقاعد", Modifier.weight(1f), KeyboardType.Number)
                }
                Text(
                    "إجمالي المقاعد يشمل السائق؛ لذلك السعة المتاحة للركاب = المقاعد − 1.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
                CapacityStatusCard(passengerCount, totalSeats, capacityExceeded, requiredCars, vehicle.passengerCapacity != null)
            }

            QuoteSection("الانتظار والمصاريف المباشرة", Icons.Default.Schedule) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(waitingHoursText, { waitingHoursText = numericInput(it) }, "الانتظار (ساعة)", Modifier.weight(1f), KeyboardType.Decimal)
                    AppField(waitingRateText, { waitingRateText = numericInput(it) }, "تكلفة ساعة الانتظار", Modifier.weight(1f), KeyboardType.Decimal)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(tollsText, { tollsText = numericInput(it) }, "البوابات/الطرق", Modifier.weight(1f), KeyboardType.Decimal)
                    AppField(driverExpenseText, { driverExpenseText = numericInput(it) }, "مصروف السائق", Modifier.weight(1f), KeyboardType.Decimal)
                }
                if (waitingHours > 0.0 && waitingRate <= 0.0) {
                    Text("أدخل تكلفة ساعة الانتظار إذا أردت تحميل وقت الانتظار على التسعير.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }

            QuoteSection("الوقود", Icons.Default.LocalGasStation) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(consumptionText, { consumptionText = numericInput(it) }, "لتر/100كم", Modifier.weight(1f), KeyboardType.Decimal)
                    AppField(fuelPriceText, { fuelPriceText = numericInput(it) }, "سعر اللتر", Modifier.weight(1f), KeyboardType.Decimal)
                }
                Text(
                    when {
                        recentConsumption != null && latestFuelPrice != null -> "تمت التعبئة من متوسط استهلاكك الفعلي وآخر سعر لتر مسجل، ويمكنك تعديلهما لهذا المشوار."
                        recentConsumption != null -> "الاستهلاك من سجلات السيارة؛ سعر اللتر يحتاج إدخالًا."
                        latestFuelPrice != null -> "سعر اللتر من آخر تموين؛ الاستهلاك يحتاج إدخالًا."
                        else -> "لا توجد بيانات وقود كافية بعد؛ أدخل الاستهلاك والسعر يدويًا."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }

            QuoteSection("التكلفة الحقيقية", Icons.Default.SettingsSuggest) {
                QuoteToggleRow(
                    "الصيانة والاستهلاك الدوري",
                    if (maintenanceItems > 0) "$maintenanceItems بند • ${formatMoney(maintenancePerKm)}/كم" else "لا توجد بنود مكتملة التكلفة/الفاصل",
                    includeMaintenance,
                    maintenanceItems > 0
                ) { includeMaintenance = it }
                QuoteToggleRow(
                    "الترخيص والتأمين والمصاريف السنوية",
                    when {
                        annualKm == null -> "المسافة السنوية غير مسجلة"
                        annualFixedTotal <= 0.0 -> "لا توجد مصروفات سنوية مسجلة"
                        else -> "${formatMoney(annualFixedTotal)}/سنة • ${formatMoney(annualFixedPerKm)}/كم"
                    },
                    includeAnnualFixed,
                    annualKm != null && annualFixedTotal > 0.0
                ) { includeAnnualFixed = it }
                QuoteToggleRow(
                    "إهلاك قيمة السيارة",
                    when {
                        annualKm == null -> "المسافة السنوية غير مسجلة"
                        annualDepreciation <= 0.0 -> "قيمة السيارة أو نسبة الإهلاك غير مسجلة"
                        else -> "${formatMoney(annualDepreciation)}/سنة • ${formatMoney(depreciationPerKm)}/كم"
                    },
                    includeDepreciation,
                    annualKm != null && annualDepreciation > 0.0
                ) { includeDepreciation = it }
            }

            QuoteSection("التسعير", Icons.Default.Payments) {
                AppField(marginText, { marginText = numericInput(it) }, "هامش الربح %", keyboardType = KeyboardType.Decimal)
                AppField(marketOfferText, { marketOfferText = numericInput(it) }, "عرض عميل / سعر سوق وجدته - اختياري", keyboardType = KeyboardType.Decimal)
            }

            if (totalDistance > 0.0) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("تفصيل السعر", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.End))
                        QuoteCostLine("الوقود", fuelCost?.let(::formatMoney) ?: "غير محسوب", Icons.Default.LocalGasStation)
                        QuoteCostLine("البوابات", formatMoney(tolls), Icons.Default.Payments)
                        QuoteCostLine("مصروف السائق", formatMoney(driverExpense), Icons.Default.Person)
                        QuoteCostLine("الانتظار", formatMoney(waitingCost), Icons.Default.Schedule)
                        QuoteCostLine("نصيب الصيانة", if (includeMaintenance && maintenanceItems > 0) formatMoney(maintenanceShare) else "غير مضاف", Icons.Default.SettingsSuggest)
                        QuoteCostLine("نصيب المصروفات السنوية", if (annualFixedShare > 0.0) formatMoney(annualFixedShare) else "غير مضاف", Icons.Default.Payments)
                        QuoteCostLine("نصيب الإهلاك", if (depreciationShare > 0.0) formatMoney(depreciationShare) else "غير مضاف", Icons.Default.Route)
                        HorizontalDivider()
                        QuoteCostLine("التكلفة الحقيقية / نقطة التعادل", formatMoney(trueTripCost), Icons.Default.Route, strong = true)
                        QuoteCostLine("السعر المقترح بربح ${quoteTrimNumber(marginPercent)}%", formatMoney(suggestedQuote), Icons.Default.Calculate, strong = true)
                        perPassengerQuote?.let {
                            QuoteCostLine("المقترح لكل راكب", formatMoney(it), Icons.Default.Person, strong = true)
                        }
                        marketProfit?.let { delta ->
                            HorizontalDivider()
                            Text(
                                if (delta >= 0.0) "العرض المدخل يغطي التكلفة ويترك ${formatMoney(delta)} قبل أي مصروف غير مسجل."
                                else "العرض المدخل أقل من التكلفة الحقيقية بـ ${formatMoney(-delta)}.",
                                fontWeight = FontWeight.Bold,
                                color = if (delta >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (capacityExceeded) {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "لا تعتمد هذا السعر كمشوار سيارة واحدة: عدد الركاب يتجاوز السعة القانونية المسجلة.",
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showSavedQuotes = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.History, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("المحفوظة ${savedQuotes.size}")
                }
                Button(
                    onClick = {
                        appViewModel.saveTripQuote(
                            TripQuoteInput(
                                startAddress = null,
                                endAddress = null,
                                startLatitude = null,
                                startLongitude = null,
                                endLatitude = null,
                                endLongitude = null,
                                routeProvider = "manual-km",
                                oneWayDistanceKm = oneWayDistance,
                                roundTrip = roundTrip,
                                totalDistanceKm = totalDistance,
                                passengerCount = passengerCount,
                                totalSeatCapacity = totalSeats,
                                waitingHours = waitingHours,
                                waitingRatePerHour = waitingRate,
                                tolls = tolls,
                                driverExpense = driverExpense,
                                consumptionLitersPer100Km = consumption,
                                fuelPricePerLiter = fuelPrice,
                                maintenancePerKm = maintenancePerKm,
                                includeMaintenance = includeMaintenance && maintenanceItems > 0,
                                annualFixedPerKm = annualFixedPerKm,
                                includeAnnualFixed = includeAnnualFixed && annualKm != null && annualFixedTotal > 0.0,
                                depreciationPerKm = depreciationPerKm,
                                includeDepreciation = includeDepreciation && annualKm != null && annualDepreciation > 0.0,
                                profitMarginPercent = marginPercent,
                                marketOffer = marketOffer,
                                estimatedFuelLiters = pricing.fuelLiters,
                                fuelCost = fuelCost,
                                trueTripCost = trueTripCost,
                                suggestedQuote = suggestedQuote,
                                perPassengerQuote = perPassengerQuote,
                                readinessPercent = readiness
                            )
                        )
                    },
                    enabled = totalDistance > 0.0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("حفظ العرض")
                }
            }
            Text(
                "حفظ عرض السعر لا يغيّر العداد ولا يسجل رحلة. العداد يتغير فقط إذا اخترت لاحقًا تحويل العرض إلى مشوار منفذ ووافقت على التأكيد.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(40.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("تم") }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (showSavedQuotes) {
        SavedTripQuotesDialog(
            quotes = savedQuotes,
            onDismiss = { showSavedQuotes = false },
            onLoad = { quote ->
                loadSavedQuote(quote)
                showSavedQuotes = false
            },
            onDelete = appViewModel::deleteTripQuote,
            onConvert = { quote ->
                pendingConvertQuote = quote
                showSavedQuotes = false
            }
        )
    }

    pendingConvertQuote?.let { quote ->
        AlertDialog(
            onDismissRequest = { pendingConvertQuote = null },
            icon = { Icon(Icons.Default.DriveEta, null) },
            title = { Text("تسجيل العرض كمشوار منفذ؟") },
            text = {
                Text(
                    "سيتم تسجيل رحلة عمل بمسافة ${formatKm(quote.totalDistanceKm)} كم ورفع عداد المركبة بهذه المسافة. نفّذ ذلك فقط إذا أصبحت المسافة المحفوظة هي المسافة الفعلية للمشوار؛ عرض السعر وحده لا يغيّر العداد."
                )
            },
            confirmButton = {
                Button(onClick = {
                    appViewModel.convertTripQuoteToTrip(quote)
                    pendingConvertQuote = null
                }) { Text("تسجيل المشوار") }
            },
            dismissButton = { TextButton(onClick = { pendingConvertQuote = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun SavedTripQuotesDialog(
    quotes: List<TripQuoteEntity>,
    onDismiss: () -> Unit,
    onLoad: (TripQuoteEntity) -> Unit,
    onDelete: (TripQuoteEntity) -> Unit,
    onConvert: (TripQuoteEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.History, null) },
        title = { Text("عروض الأسعار المحفوظة") },
        text = {
            if (quotes.isEmpty()) {
                Text("لا توجد عروض محفوظة لهذه المركبة بعد.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quotes, key = { it.id }) { quote ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.End) {
                                Text(
                                    listOf(quote.startAddress, quote.endAddress).filterNotNull().filter { it.isNotBlank() }.joinToString(" ← ").ifBlank { "عرض مشوار" },
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End
                                )
                                Text("${formatKm(quote.totalDistanceKm)} كم • ${formatMoney(quote.trueTripCost)} تكلفة • ${formatMoney(quote.suggestedQuote)} مقترح", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                                Text("${formatDate(quote.createdAt)} • جاهزية ${quote.readinessPercent}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!quote.convertedTripId.isNullOrBlank()) {
                                    Text("تم تسجيله كمشوار منفذ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { onDelete(quote) }) { Icon(Icons.Default.DeleteOutline, "حذف", tint = MaterialTheme.colorScheme.error) }
                                    if (quote.convertedTripId.isNullOrBlank()) {
                                        TextButton(onClick = { onConvert(quote) }) { Text("تسجيل كمشوار") }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { onLoad(quote) }) { Text("تحميل وإعادة التسعير") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
private fun QuoteInfoCard(readiness: Int) {
    val label = when {
        readiness >= 90 -> "جاهزية عالية للتسعير"
        readiness >= 70 -> "جاهزية جيدة — راجع البنود الناقصة"
        readiness >= 50 -> "البيانات ناقصة وقد تغيّر السعر"
        else -> "أكمل البيانات الأساسية قبل اعتماد السعر"
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$readiness%", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { readiness / 100f }, modifier = Modifier.fillMaxWidth())
            Text("الحساب يستخدم فقط بيانات واضحة أو مسجلة للسيارة؛ البنود غير المعروفة لا يتم اختراعها.", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun QuoteSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            content()
        }
    }
}

@Composable
private fun CapacityStatusCard(
    passengers: Int,
    totalSeats: Int?,
    exceeded: Boolean,
    requiredCars: Int,
    savedInProfile: Boolean
) {
    val passengerSeats = totalSeats?.let { max(0, it - 1) }
    val (text, isError, isWarning) = when {
        totalSeats == null -> Triple("سعة السيارة غير مسجلة؛ لا يمكن تأكيد صلاحية عدد الركاب.", false, true)
        exceeded -> Triple("هذه السيارة تسمح بـ $passengerSeats راكب + السائق. العدد المطلوب يحتاج تقريبًا $requiredCars سيارات مماثلة.", true, false)
        else -> Triple("السعة مناسبة: $passengers من أصل $passengerSeats مقعد متاح للركاب.", false, false)
    }
    val container = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isWarning -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isWarning -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(Modifier.fillMaxWidth(), color = container, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isError || isWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
                null,
                tint = content,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(text, color = content, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                if (totalSeats != null && !savedInProfile) {
                    Text("القيمة المدخلة هنا مؤقتة؛ احفظها في ملف السيارة لاستخدامها تلقائيًا لاحقًا.", color = content, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun QuoteToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun QuoteCostLine(label: String, value: String, icon: ImageVector, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(value, fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(label, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End)
    }
}

private fun quoteMaintenanceReservePerKm(plans: List<MaintenancePlanEntity>): Double =
    plans.asSequence()
        .filter { !it.isDeleted && it.isActive }
        .mapNotNull { plan ->
            val interval = plan.intervalKm?.takeIf { it > 0.0 } ?: return@mapNotNull null
            val cost = plan.estimatedCost?.takeIf { it > 0.0 } ?: return@mapNotNull null
            cost / interval
        }
        .filter { it.isFinite() && it >= 0.0 }
        .sum()
        .let { max(0.0, it) }

private fun quoteTrimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun quoteDigitsOnly(value: String): String = value.map { ch ->
    when (ch) {
        '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
        '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
        else -> ch
    }
}.joinToString("").filter(Char::isDigit)
