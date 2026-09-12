package com.ahmed.carmanager.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.*
import com.ahmed.carmanager.data.repository.BatteryInput
import com.ahmed.carmanager.data.repository.PartInput
import com.ahmed.carmanager.data.repository.TireInput
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalPartsScreen(
    vehicle: VehicleEntity?,
    parts: List<PartEntity>,
    onAdd: (PartInput) -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد سيارة محددة", "اختر سيارة أولًا.", Icons.Default.SettingsSuggest)
        return
    }

    var showAdd by remember { mutableStateOf(false) }
    val active = parts.filter { !it.isDeleted && it.status == ItemStatus.ACTIVE }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "قطع الغيار والعمر الفعلي",
                subtitle = "${vehicle.displayName ?: vehicle.model} • تاريخ التركيب والتكلفة والمتابعة",
                icon = Icons.Default.SettingsSuggest
            )
        }
        item {
            AutomotiveActionCard(
                title = "تسجيل قطعة غيار",
                subtitle = "احتفظ بتاريخ التركيب والعداد والضمان",
                icon = Icons.Default.Build,
                tone = AutoTone.AMBER,
                modifier = Modifier.fillMaxWidth(),
                filled = true,
                onClick = { showAdd = true }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomotiveMetricCard("قطع نشطة", active.size.toString(), Icons.Default.SettingsSuggest, AutoTone.AMBER, Modifier.weight(1f))
                AutomotiveMetricCard("تكلفة مسجلة", active.mapNotNull { it.cost }.sum().let { if (it > 0) formatMoney(it) else "—" }, CMIcons.Expense, AutoTone.VIOLET, Modifier.weight(1f))
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "يمكن تسجيل قطعة تم تركيبها قديمًا عند عداد وتاريخ سابقين. هذا لا يخفض عداد السيارة الحالي؛ بل يستخدم لحساب العمر المستهلك والمتبقي.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        if (active.isEmpty()) {
            item { EmptyState("لا توجد قطع مسجلة", "سجل القطع المهمة التي تريد متابعة عمرها وتكلفتها.", Icons.Default.Build) }
        } else {
            items(active, key = { it.id }) { part ->
                ProfessionalPartCard(vehicle, part)
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            PartHistoryForm(
                vehicle = vehicle,
                onCancel = { showAdd = false },
                onSave = { onAdd(it); showAdd = false }
            )
        }
    }
}

@Composable
private fun ProfessionalPartCard(vehicle: VehicleEntity, part: PartEntity) {
    val kmUsed = part.installOdometerKm?.let { max(0.0, vehicle.currentOdometerKm - it) }
    val remainingKm = if (kmUsed != null && part.expectedLifeKm != null) part.expectedLifeKm - kmUsed else null
    val ageMonths = part.installDate?.let { monthsBetween(it, System.currentTimeMillis()) }
    val remainingMonths = if (ageMonths != null && part.expectedLifeMonths != null) part.expectedLifeMonths - ageMonths else null
    val urgent = remainingKm?.let { it <= 0 } == true || remainingMonths?.let { it <= 0 } == true
    val near = !urgent && (remainingKm?.let { it <= 2_000 } == true || remainingMonths?.let { it <= 2 } == true)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = when {
            urgent -> CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            near -> CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            else -> CardDefaults.elevatedCardColors()
        }
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (urgent || near) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (urgent) "راجع الآن" else "قريب") },
                        leadingIcon = { Icon(if (urgent) Icons.Default.Warning else Icons.Default.Schedule, null, Modifier.size(16.dp)) }
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(part.nameAr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(part.brand, part.partNumber?.let { "#$it" }).joinToString(" • ").ifBlank { part.category }, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            part.installDate?.let { Text("تاريخ التركيب: ${formatDate(it)}") }
            part.installOdometerKm?.let { Text("عداد التركيب: ${formatKm(it)} كم") }
            kmUsed?.let { Text("قطعت منذ التركيب: ${formatKm(it)} كم") }
            ageMonths?.let { Text("العمر الحالي: $it شهر") }
            remainingKm?.let {
                Text(
                    if (it <= 0) "تجاوز العمر التقديري بـ ${formatKm(-it)} كم" else "متبقي تقديري: ${formatKm(it)} كم",
                    fontWeight = FontWeight.Bold,
                    color = if (it <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            remainingMonths?.let {
                Text(if (it <= 0) "تجاوز العمر الزمني التقديري بـ ${-it} شهر" else "متبقي زمني تقديري: $it شهر")
            }
            part.cost?.let { Text("التكلفة الفعلية المسجلة: ${formatMoney(it)}") }
            part.warrantyUntil?.let { Text("الضمان حتى: ${formatDate(it)}") }
            if (!part.notes.isNullOrBlank()) Text(part.notes!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PartHistoryForm(vehicle: VehicleEntity, onCancel: () -> Unit, onSave: (PartInput) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("قطع غيار") }
    var brand by remember { mutableStateOf("") }
    var partNumber by remember { mutableStateOf("") }
    var installKm by remember { mutableStateOf(formatEditable(vehicle.currentOdometerKm)) }
    var installDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cost by remember { mutableStateOf("") }
    var lifeKm by remember { mutableStateOf("") }
    var lifeMonths by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val kmValue = installKm.toDoubleOrNull()
    val valid = name.isNotBlank() && kmValue != null && kmValue >= 0 && kmValue <= vehicle.currentOdometerKm

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End
    ) {
        Text("تسجيل قطعة غيار", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("أدخل الواقع كما حدث، حتى لو كان التركيب منذ فترة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        AppField(name, { name = it }, "اسم القطعة *")
        AppField(category, { category = it }, "التصنيف")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(brand, { brand = it }, "الماركة", Modifier.weight(1f))
            AppField(partNumber, { partNumber = it }, "Part Number", Modifier.weight(1f))
        }
        HistoricalDateField("تاريخ التركيب", installDate) { installDate = it }
        AppField(installKm, { installKm = numericInput(it) }, "عداد التركيب (كم)")
        if ((kmValue ?: 0.0) > vehicle.currentOdometerKm) {
            Text("عداد التركيب لا يمكن أن يكون أكبر من العداد الحالي.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        AppField(cost, { cost = numericInput(it) }, "التكلفة الفعلية")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(lifeKm, { lifeKm = numericInput(it) }, "عمر تقديري كم", Modifier.weight(1f))
            AppField(lifeMonths, { lifeMonths = it.filter(Char::isDigit) }, "عمر تقديري شهر", Modifier.weight(1f))
        }
        AppField(supplier, { supplier = it }, "المورد / مركز الصيانة")
        OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), minLines = 2, maxLines = 4)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("إلغاء") }
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        PartInput(
                            nameAr = name,
                            category = category,
                            brand = brand,
                            partNumber = partNumber,
                            installOdometerKm = kmValue,
                            cost = cost.toDoubleOrNull(),
                            expectedLifeKm = lifeKm.toDoubleOrNull(),
                            expectedLifeMonths = lifeMonths.toIntOrNull(),
                            supplier = supplier,
                            notes = notes,
                            installDate = installDate
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("حفظ") }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalTiresBatteryScreen(
    vehicle: VehicleEntity?,
    tires: List<TireEntity>,
    batteries: List<BatteryRecordEntity>,
    onAddTire: (TireInput) -> Unit,
    onAddBattery: (BatteryInput) -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد سيارة محددة", "اختر سيارة أولًا.", Icons.Default.TireRepair)
        return
    }
    var addTire by remember { mutableStateOf(false) }
    var addBattery by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "الإطارات والبطارية",
                subtitle = "عمر فعلي مبني على تاريخ وعداد التركيب وليس افتراضات ثابتة",
                icon = Icons.Default.TireRepair
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AutomotiveActionCard("إضافة إطار", "مقاس وعمر وتكلفة", Icons.Default.TireRepair, AutoTone.BLUE, Modifier.weight(1f), onClick = { addTire = true })
                AutomotiveActionCard("إضافة بطارية", "تركيب وسعة وعمر", Icons.Default.BatteryChargingFull, AutoTone.GREEN, Modifier.weight(1f), onClick = { addBattery = true })
            }
        }
        item {
            val tireCount = tires.count { !it.isDeleted && it.status == ItemStatus.ACTIVE }
            val batteryCount = batteries.count { !it.isDeleted && it.status == ItemStatus.ACTIVE }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomotiveMetricCard("إطارات نشطة", tireCount.toString(), Icons.Default.TireRepair, AutoTone.BLUE, Modifier.weight(1f))
                AutomotiveMetricCard("بطاريات نشطة", batteryCount.toString(), Icons.Default.BatteryChargingFull, AutoTone.GREEN, Modifier.weight(1f))
            }
        }

        item { Text("الإطارات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (tires.none { !it.isDeleted && it.status == ItemStatus.ACTIVE }) {
            item { Text("لا توجد إطارات نشطة مسجلة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(tires.filter { !it.isDeleted && it.status == ItemStatus.ACTIVE }, key = { it.id }) { tire ->
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(15.dp), horizontalAlignment = Alignment.End) {
                        Text("${tire.position.arLabel()} • ${tire.brand ?: "إطار"} ${tire.model ?: ""}", fontWeight = FontWeight.Bold)
                        tire.size?.let { Text("المقاس: $it") }
                        tire.installDate?.let { Text("تاريخ التركيب: ${formatDate(it)}") }
                        tire.installOdometerKm?.let {
                            Text("عداد التركيب: ${formatKm(it)} كم")
                            Text("المسافة منذ التركيب: ${formatKm(max(0.0, vehicle.currentOdometerKm - it))} كم")
                        }
                        tire.cost?.let { Text("التكلفة: ${formatMoney(it)}") }
                    }
                }
            }
        }

        item { Text("البطارية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (batteries.none { !it.isDeleted && it.status == ItemStatus.ACTIVE }) {
            item { Text("لا توجد بطارية نشطة مسجلة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(batteries.filter { !it.isDeleted && it.status == ItemStatus.ACTIVE }, key = { it.id }) { battery ->
                val age = battery.installDate?.let { monthsBetween(it, System.currentTimeMillis()) }
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(15.dp), horizontalAlignment = Alignment.End) {
                        Text("${battery.brand ?: "بطارية"} ${battery.model ?: ""}", fontWeight = FontWeight.Bold)
                        battery.capacityAh?.let { Text("السعة: ${formatKm(it)} Ah") }
                        battery.installDate?.let { Text("تاريخ التركيب: ${formatDate(it)}") }
                        battery.installOdometerKm?.let { Text("عداد التركيب: ${formatKm(it)} كم") }
                        age?.let { Text("العمر الحالي: $it شهر") }
                        if (age != null && battery.expectedLifeMonths != null) {
                            val remaining = battery.expectedLifeMonths - age
                            Text(if (remaining > 0) "متبقي تقديري: $remaining شهر" else "تجاوز العمر التقديري بـ ${-remaining} شهر", color = if (remaining <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        battery.cost?.let { Text("التكلفة: ${formatMoney(it)}") }
                    }
                }
            }
        }
    }

    if (addTire) {
        ModalBottomSheet(onDismissRequest = { addTire = false }) {
            TireHistoryForm(vehicle, { addTire = false }) { onAddTire(it); addTire = false }
        }
    }
    if (addBattery) {
        ModalBottomSheet(onDismissRequest = { addBattery = false }) {
            BatteryHistoryForm(vehicle, { addBattery = false }) { onAddBattery(it); addBattery = false }
        }
    }
}

@Composable
private fun TireHistoryForm(vehicle: VehicleEntity, onCancel: () -> Unit, onSave: (TireInput) -> Unit) {
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(vehicle.tireSize.orEmpty()) }
    var position by remember { mutableStateOf(TirePosition.UNASSIGNED) }
    var installKm by remember { mutableStateOf(formatEditable(vehicle.currentOdometerKm)) }
    var installDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cost by remember { mutableStateOf("") }
    var pressure by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var manufacture by remember { mutableStateOf("") }
    val km = installKm.toDoubleOrNull()

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.End) {
        Text("تسجيل إطار", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        EnumSelector("المكان", TirePosition.entries, position, { position = it }) { it.arLabel() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(brand, { brand = it }, "الماركة", Modifier.weight(1f)); AppField(model, { model = it }, "الموديل", Modifier.weight(1f))
        }
        AppField(size, { size = it }, "المقاس")
        AppField(serial, { serial = it }, "Serial - اختياري")
        AppField(manufacture, { manufacture = it }, "تاريخ التصنيع/الكود - اختياري")
        HistoricalDateField("تاريخ التركيب", installDate) { installDate = it }
        AppField(installKm, { installKm = numericInput(it) }, "عداد التركيب (كم)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(cost, { cost = numericInput(it) }, "التكلفة", Modifier.weight(1f)); AppField(pressure, { pressure = numericInput(it) }, "ضغط PSI", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("إلغاء") }
            Button(enabled = km != null && km >= 0 && km <= vehicle.currentOdometerKm, onClick = {
                onSave(TireInput(brand = brand, model = model, size = size, position = position, installOdometerKm = km, cost = cost.toDoubleOrNull(), pressurePsi = pressure.toDoubleOrNull(), serialNumber = serial, manufactureDateText = manufacture, installDate = installDate))
            }, modifier = Modifier.weight(1f)) { Text("حفظ") }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun BatteryHistoryForm(vehicle: VehicleEntity, onCancel: () -> Unit, onSave: (BatteryInput) -> Unit) {
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var installKm by remember { mutableStateOf(formatEditable(vehicle.currentOdometerKm)) }
    var installDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cost by remember { mutableStateOf("") }
    var warranty by remember { mutableStateOf("") }
    var expectedLife by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val km = installKm.toDoubleOrNull()

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.End) {
        Text("تسجيل بطارية", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(brand, { brand = it }, "الماركة", Modifier.weight(1f)); AppField(model, { model = it }, "الموديل", Modifier.weight(1f))
        }
        AppField(capacity, { capacity = numericInput(it) }, "السعة Ah")
        HistoricalDateField("تاريخ التركيب", installDate) { installDate = it }
        AppField(installKm, { installKm = numericInput(it) }, "عداد التركيب (كم)")
        AppField(cost, { cost = numericInput(it) }, "التكلفة الفعلية")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(warranty, { warranty = it.filter(Char::isDigit) }, "الضمان شهر", Modifier.weight(1f)); AppField(expectedLife, { expectedLife = it.filter(Char::isDigit) }, "عمر تقديري شهر", Modifier.weight(1f))
        }
        OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("إلغاء") }
            Button(enabled = km != null && km >= 0 && km <= vehicle.currentOdometerKm, onClick = {
                onSave(BatteryInput(brand = brand, model = model, capacityAh = capacity.toDoubleOrNull(), installOdometerKm = km, cost = cost.toDoubleOrNull(), warrantyMonths = warranty.toIntOrNull(), expectedLifeMonths = expectedLife.toIntOrNull(), notes = notes, installDate = installDate))
            }, modifier = Modifier.weight(1f)) { Text("حفظ") }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HistoricalDateField(label: String, value: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val calendar = remember(value) { Calendar.getInstance().apply { timeInMillis = value } }
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val selected = Calendar.getInstance().apply {
                        clear(); set(year, month, day, 12, 0, 0)
                    }.timeInMillis
                    onChange(selected)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Default.CalendarMonth, null)
        Spacer(Modifier.width(8.dp))
        Text("$label: ${formatDate(value)}")
    }
}

private fun monthsBetween(from: Long, to: Long): Int = max(0, (TimeUnit.MILLISECONDS.toDays(to - from) / 30.4375).toInt())
private fun formatEditable(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
