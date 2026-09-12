package com.ahmed.carmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmed.carmanager.data.local.model.EntityType
import com.ahmed.carmanager.data.local.model.FuelRecordEntity
import com.ahmed.carmanager.data.local.model.FuelType
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.repository.FuelInput

private val GASOLINE_FUEL_TYPES = listOf(
    FuelType.GASOLINE_80,
    FuelType.GASOLINE_92,
    FuelType.GASOLINE_95
)

private fun FuelType.isGasolineGrade(): Boolean = this in GASOLINE_FUEL_TYPES

@Composable
fun FuelScreen(
    vehicle: VehicleEntity?,
    records: List<FuelRecordEntity>,
    onAdd: (FuelInput) -> Unit,
    onMessage: (String) -> Unit = {}
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة من الجراج أولًا.", Icons.Default.LocalGasStation)
        return
    }
    val appViewModel: CarManagerViewModel = viewModel()
    var showAdd by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<FuelRecordEntity?>(null) }
    var deletingRecord by remember { mutableStateOf<FuelRecordEntity?>(null) }
    var attachmentRecord by remember { mutableStateOf<FuelRecordEntity?>(null) }

    val active = remember(records) { records.filter { !it.isDeleted } }
    val total = remember(active) { active.sumOf { it.amountPaid } }
    val liters = remember(active) { active.sumOf { it.liters } }
    val consumption = remember(active) {
        active.asSequence()
            .mapNotNull { it.consumptionLitersPer100Km }
            .take(5)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.average()
    }

    LazyColumn(
        contentPadding = PaddingValues(CMPremium.ScreenPadding, 8.dp, CMPremium.ScreenPadding, 96.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            SectionHeader("الوقود", "تموين واستهلاك ومدى من البيانات الفعلية") {
                Button(
                    onClick = { showAdd = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp)
                ) { Text("إضافة", style = MaterialTheme.typography.labelLarge) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FuelMetric("إجمالي الوقود", formatMoney(total), Modifier.weight(1f))
                FuelMetric("إجمالي اللترات", formatLiters(liters), Modifier.weight(1f))
                FuelMetric("متوسط الاستهلاك", consumption?.let { "${formatKm(it)} ل/100" } ?: "—", Modifier.weight(1f))
            }
        }
        if (active.isEmpty()) {
            item { EmptyState("لا توجد عمليات تموين", "أدخل المبلغ وسعر الوحدة وسيحسب التطبيق الكمية والمدى تلقائيًا.") }
        } else {
            items(active, key = { it.id }) { record ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { deletingRecord = record }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.DeleteOutline, "حذف التموين", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.width(1.dp))
                            IconButton(onClick = { editingRecord = record }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Edit, "تعديل التموين", Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(1.dp))
                            IconButton(onClick = { attachmentRecord = record }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.AttachFile, "إيصال أو مرفق", Modifier.size(18.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatMoney(record.amountPaid), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                Text("${record.fuelType.arLabel()} • ${formatDate(record.fuelDate)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("${formatLiters(record.liters)} • ${formatMoney(record.pricePerLiter)}/لتر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("العداد ${formatKm(record.odometerKm)} كم", style = MaterialTheme.typography.labelSmall)
                        record.stationName?.takeIf { it.isNotBlank() }?.let {
                            Text("المحطة: $it", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        record.consumptionLitersPer100Km?.let {
                            Text("استهلاك فعلي ${formatKm(it)} لتر/100كم", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        record.estimatedRangeKm?.let {
                            Text("مدى متوقع ${formatKm(it)} كم", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (record.isFullTank) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f)) {
                                Text("تفويلة كاملة", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        FuelDialog(
            vehicle = vehicle,
            avgConsumption = consumption,
            initial = null,
            onDismiss = { showAdd = false }
        ) {
            onAdd(it)
            showAdd = false
        }
    }

    editingRecord?.let { record ->
        FuelDialog(
            vehicle = vehicle,
            avgConsumption = consumption,
            initial = record,
            onDismiss = { editingRecord = null }
        ) { input ->
            appViewModel.updateFuelRecord(record, active, input)
            editingRecord = null
        }
    }

    deletingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("حذف عملية التموين؟") },
            text = {
                Text(
                    "سيتم إخفاء هذه العملية وإعادة حساب استهلاك الوقود للسجلات المتبقية. لن يتم إرجاع عداد السيارة للخلف تلقائيًا."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appViewModel.deleteFuelRecord(record, active)
                        deletingRecord = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف التموين") }
            },
            dismissButton = { TextButton(onClick = { deletingRecord = null }) { Text("إلغاء") } }
        )
    }

    attachmentRecord?.let { record ->
        AttachmentManagerSheet(
            vehicleId = vehicle.vehicleId,
            entityType = EntityType.FUEL,
            entityId = record.id,
            title = "${record.fuelType.arLabel()} • ${formatDate(record.fuelDate)}",
            onDismiss = { attachmentRecord = null },
            onMessage = onMessage
        )
    }
}

@Composable
private fun FuelMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .60f)
    ) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun FuelDialog(
    vehicle: VehicleEntity,
    avgConsumption: Double?,
    initial: FuelRecordEntity?,
    onDismiss: () -> Unit,
    onSave: (FuelInput) -> Unit
) {
    val editing = initial != null
    var amount by remember(initial?.id) { mutableStateOf(initial?.amountPaid?.toString().orEmpty()) }
    var price by remember(initial?.id) { mutableStateOf(initial?.pricePerLiter?.toString().orEmpty()) }
    var km by remember(initial?.id) { mutableStateOf((initial?.odometerKm ?: vehicle.currentOdometerKm).toString()) }
    var station by remember(initial?.id) { mutableStateOf(initial?.stationName.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var full by remember(initial?.id) { mutableStateOf(initial?.isFullTank ?: false) }
    var fuelType by remember(initial?.id) { mutableStateOf(initial?.fuelType ?: vehicle.fuelType) }
    var fuelDate by remember(initial?.id) { mutableStateOf(initial?.fuelDate ?: System.currentTimeMillis()) }
    var expected by remember(initial?.id, avgConsumption) {
        mutableStateOf((initial?.consumptionLitersPer100Km ?: avgConsumption)?.toString().orEmpty())
    }

    val parsedExpected = expected.toDoubleOrNull()
    val expectedValid = expected.isBlank() || parsedExpected?.let { it.isFinite() && it > 0.0 && it <= 100.0 } == true
    val liters = amount.toDoubleOrNull()?.let { a -> price.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.let { a / it } }
    val litersForPreview = if (full) {
        vehicle.tankCapacityLiters?.takeIf { it.isFinite() && it > 0.0 } ?: liters
    } else liters
    val range = litersForPreview?.let { l -> parsedExpected?.takeIf { expectedValid && it > 0 }?.let { l / it * 100.0 } }
    val futureDate = fuelDate > System.currentTimeMillis()
    val gasolineVehicle = vehicle.fuelType.isGasolineGrade()
    val fuelChoices = remember(gasolineVehicle) {
        if (gasolineVehicle) GASOLINE_FUEL_TYPES else FuelType.entries.toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "تعديل التموين" else "إضافة تموين") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (editing) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(11.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .52f)
                    ) {
                        Text(
                            "صحح التاريخ أو السعر أو المبلغ أو العداد؛ بعد الحفظ يعاد حساب الاستهلاك المرتبط بالتفويل الكامل. لا يمكن رفع هذا السجل فوق عداد المركبة الحالي.",
                            Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                AppDateSelector("تاريخ التموين", fuelDate, onSelected = { if (it != null) fuelDate = it })
                if (futureDate) {
                    Text("تاريخ التموين لا يمكن أن يكون في المستقبل.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                AppField(amount, { amount = numericInput(it) }, "المبلغ المدفوع")
                AppField(price, { price = numericInput(it) }, "سعر اللتر")
                EnumSelector(
                    if (gasolineVehicle) "نوع البنزين" else "نوع الوقود",
                    fuelChoices,
                    fuelType,
                    { fuelType = it }
                ) { it.arLabel() }
                if (gasolineVehicle && fuelType != vehicle.fuelType) {
                    Text(
                        "سيُسجل هذا التموين كـ ${fuelType.arLabel()} بينما الوقود الأساسي في ملف السيارة هو ${vehicle.fuelType.arLabel()}. هذا مسموح لتسجيل ما تم استخدامه فعليًا.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.End
                    )
                }
                AppField(km, { km = numericInput(it) }, "عداد المركبة (كم)")
                AppField(station, { station = it }, "المحطة - اختياري")
                AppField(expected, { expected = numericInput(it) }, "استهلاك متوقع لتر/100كم - اختياري")
                if (!expectedValid) {
                    Text(
                        "الاستهلاك المتوقع يجب أن يكون أكبر من صفر ولا يتجاوز 100 لتر/100كم.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.End
                    )
                }
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(full, { full = it })
                    Spacer(Modifier.width(7.dp))
                    Text("تفويلة كاملة حتى آخر الخزان", style = MaterialTheme.typography.bodyMedium)
                }
                if (liters != null) {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.End) {
                            Text("الكمية: ${formatLiters(liters)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            range?.let {
                                Text(
                                    if (full && vehicle.tankCapacityLiters?.let { tank -> tank.isFinite() && tank > 0.0 } == true)
                                        "مدى خزان كامل متوقع: ${formatKm(it)} كم"
                                    else
                                        "مدى الوقود المسجل المتوقع: ${formatKm(it)} كم",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val validKm = km.toDoubleOrNull()?.let { value ->
                value.isFinite() && if (editing) value in 0.0..vehicle.currentOdometerKm else value >= vehicle.currentOdometerKm
            } == true
            val valid = amount.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true &&
                price.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true &&
                validKm && !futureDate && expectedValid &&
                (!gasolineVehicle || fuelType.isGasolineGrade())
            Button(enabled = valid, onClick = {
                onSave(
                    FuelInput(
                        amountPaid = amount.toDouble(),
                        pricePerLiter = price.toDouble(),
                        odometerKm = km.toDouble(),
                        fuelType = fuelType,
                        stationName = station,
                        isFullTank = full,
                        expectedConsumptionL100 = parsedExpected,
                        notes = notes,
                        date = fuelDate
                    )
                )
            }) { Text(if (editing) "حفظ التعديل" else "حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
