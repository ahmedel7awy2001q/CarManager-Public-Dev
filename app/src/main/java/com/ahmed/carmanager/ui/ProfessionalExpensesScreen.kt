package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.ahmed.carmanager.data.local.model.ExpenseCategory
import com.ahmed.carmanager.data.local.model.ExpenseEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.repository.ExpenseInput

@Composable
fun ProfessionalExpensesScreen(
    vehicle: VehicleEntity?,
    expenses: List<ExpenseEntity>,
    onAdd: (ExpenseInput) -> Unit,
    onMessage: (String) -> Unit,
    appViewModel: CarManagerViewModel = viewModel()
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا لفتح سجل المصروفات.", Icons.Default.Payments)
        return
    }

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }
    var attachmentExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    // Expense history can grow indefinitely. Sorting/grouping is done only when Room emits a new list,
    // not when a dialog opens, a ripple animates or any unrelated UI state recomposes.
    val active = remember(expenses) {
        expenses.asSequence().filter { !it.isDeleted }.sortedByDescending { it.expenseDate }.toList()
    }
    val total = remember(active) { active.sumOf { it.amount } }
    val topCategory = remember(active) {
        active.groupBy { it.category }
            .mapValues { (_, rows) -> rows.sumOf { it.amount } }
            .maxByOrNull { it.value }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CMPremium.ScreenPadding, 8.dp, CMPremium.ScreenPadding, 96.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item(key = "expenses-summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .12f))
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .65f)) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.padding(7.dp).size(19.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("سجل المصروفات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text(
                                vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ExpenseMetric("الحركات", active.size.toString(), Modifier.weight(1f))
                        ExpenseMetric("أعلى تصنيف", topCategory?.key?.arLabel() ?: "—", Modifier.weight(1f))
                        ExpenseMetric("الإجمالي", formatMoney(total), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(7.dp))
                    Button(
                        onClick = { showAdd = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة مصروف", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (active.isEmpty()) {
            item { EmptyState("لا توجد مصروفات", "سجل المصروفات المرتبطة بالمركبة وأرفق الفاتورة عند الحاجة.", Icons.Default.Payments) }
        } else {
            item { SectionHeader("الحركات", "تصحيح الخطأ أو إرفاق فاتورة بدون إنشاء سجل مكرر") }
            items(active, key = { it.id }) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { attachmentExpense = item },
                                modifier = Modifier.size(34.dp)
                            ) { Icon(Icons.Default.AttachFile, "الفاتورة والمرفقات", Modifier.size(18.dp)) }
                            Spacer(Modifier.width(2.dp))
                            IconButton(
                                onClick = { editing = item },
                                modifier = Modifier.size(34.dp)
                            ) { Icon(Icons.Default.Edit, "تعديل المصروف", Modifier.size(18.dp)) }
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    item.descriptionAr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(formatMoney(item.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("${item.category.arLabel()} • ${formatDate(item.expenseDate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        item.odometerKm?.let { Text("العداد ${formatKm(it)} كم", style = MaterialTheme.typography.labelSmall) }
                        item.merchant?.takeIf { it.isNotBlank() }?.let { Text("الجهة: $it", style = MaterialTheme.typography.labelSmall) }
                        item.notes?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ProfessionalExpenseDialog(
            vehicle = vehicle,
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { onAdd(it); showAdd = false }
        )
    }

    editing?.let { item ->
        ProfessionalExpenseDialog(
            vehicle = vehicle,
            initial = item,
            onDismiss = { editing = null },
            onSave = { input ->
                appViewModel.updateExpenseRecord(item, input)
                editing = null
            }
        )
    }

    attachmentExpense?.let { item ->
        AttachmentManagerSheet(
            vehicleId = vehicle.vehicleId,
            entityType = EntityType.EXPENSE,
            entityId = item.id,
            title = "${item.descriptionAr} • ${formatDate(item.expenseDate)}",
            onDismiss = { attachmentExpense = null },
            onMessage = onMessage
        )
    }
}

@Composable
private fun ExpenseMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun ProfessionalExpenseDialog(
    vehicle: VehicleEntity,
    initial: ExpenseEntity?,
    onDismiss: () -> Unit,
    onSave: (ExpenseInput) -> Unit
) {
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: ExpenseCategory.OTHER) }
    var description by remember(initial?.id) { mutableStateOf(initial?.descriptionAr.orEmpty()) }
    var amount by remember(initial?.id) { mutableStateOf(initial?.amount?.let(::editableNumber).orEmpty()) }
    var merchant by remember(initial?.id) { mutableStateOf(initial?.merchant.orEmpty()) }
    var odometer by remember(initial?.id) { mutableStateOf(initial?.odometerKm?.let(::editableNumber) ?: editableNumber(vehicle.currentOdometerKm)) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var date by remember(initial?.id) { mutableStateOf(initial?.expenseDate ?: System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (initial == null) Icons.Default.Payments else Icons.Default.Edit, null) },
        title = { Text(if (initial == null) "إضافة مصروف" else "تعديل المصروف") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EnumSelector("التصنيف", ExpenseCategory.entries, category, { category = it }) { it.arLabel() }
                AppField(description, { description = it }, "الوصف")
                AppField(amount, { amount = numericInput(it) }, "المبلغ")
                AppField(merchant, { merchant = it }, "الجهة / المحل - اختياري")
                AppField(odometer, { odometer = numericInput(it) }, "العداد - اختياري")
                AppDateSelector("التاريخ", date, allowClear = false) { selected -> if (selected != null) date = selected }
                AppField(notes, { notes = it }, "ملاحظات - اختياري")
                Text(
                    if (initial == null) "يمكن إضافة الفاتورة بعد الحفظ."
                    else "سيتم تحديث نفس الحركة وحفظ وقت التعديل؛ لن تُنشأ حركة مصروف جديدة.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = description.isNotBlank() && amount.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    onSave(
                        ExpenseInput(
                            category = category,
                            amount = amount.toDouble(),
                            descriptionAr = description,
                            odometerKm = odometer.toDoubleOrNull(),
                            merchant = merchant,
                            notes = notes,
                            date = date
                        )
                    )
                }
            ) { Text(if (initial == null) "حفظ" else "حفظ التعديل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun editableNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
