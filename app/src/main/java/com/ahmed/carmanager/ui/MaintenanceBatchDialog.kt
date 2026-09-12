package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.MaintenancePlanEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.repository.MaintenanceRecordInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceBatchDialog(
    vehicle: VehicleEntity,
    plans: List<MaintenancePlanEntity>,
    onDismiss: () -> Unit,
    onSave: (List<MaintenanceRecordInput>) -> Unit
) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var totalCost by remember { mutableStateOf("") }
    var serviceCenter by remember { mutableStateOf("") }
    var invoice by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val activePlans = remember(plans) { plans.filter { it.isActive } }
    val selectedPlans = activePlans.filter { selected[it.id] == true }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("تسجيل صيانة مجمعة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("اختر كل البنود التي تم تنفيذها في نفس الزيارة. سيتم تحديث موعد كل بند على حدة مع الاحتفاظ بتكلفة العملية الإجمالية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("عداد التنفيذ: ${formatKm(vehicle.currentOdometerKm)} كم", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${selectedPlans.size} محدد", fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 330.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(activePlans, key = { it.id }) { plan ->
                    val checked = selected[plan.id] == true
                    Surface(
                        onClick = { selected[plan.id] = !checked },
                        shape = RoundedCornerShape(13.dp),
                        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { selected[plan.id] = it })
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(plan.titleAr, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(plan.nextDueOdometerKm?.let { "استحقاق ${formatKm(it)} كم" }, plan.estimatedCost?.let { formatMoney(it) }).joinToString(" • ").ifBlank { plan.category },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(totalCost, { totalCost = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("إجمالي تكلفة الزيارة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(serviceCenter, { serviceCenter = it }, label = { Text("مركز الصيانة") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(invoice, { invoice = it }, label = { Text("رقم الفاتورة") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات - اختياري") }, modifier = Modifier.fillMaxWidth())

            Button(
                enabled = selectedPlans.isNotEmpty(),
                onClick = {
                    val total = totalCost.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val estimateSum = selectedPlans.sumOf { it.estimatedCost ?: 0.0 }
                    val count = selectedPlans.size.coerceAtLeast(1)
                    val inputs = selectedPlans.mapIndexed { index, plan ->
                        val allocated = when {
                            total <= 0.0 -> 0.0
                            estimateSum > 0.0 && (plan.estimatedCost ?: 0.0) > 0.0 -> total * ((plan.estimatedCost ?: 0.0) / estimateSum)
                            else -> total / count
                        }
                        MaintenanceRecordInput(
                            titleAr = plan.titleAr,
                            category = plan.category,
                            odometerKm = vehicle.currentOdometerKm,
                            totalCost = allocated,
                            planId = plan.id,
                            serviceCenter = serviceCenter.ifBlank { null },
                            invoiceNumber = invoice.ifBlank { null },
                            notes = buildString {
                                append("جزء من صيانة مجمعة تضم ${selectedPlans.size} بند")
                                if (index == 0 && total > 0) append("؛ إجمالي الفاتورة ${formatMoney(total)}")
                                if (notes.isNotBlank()) append("؛ ${notes.trim()}")
                            }
                        )
                    }
                    onSave(inputs)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(6.dp))
                Text("حفظ ${selectedPlans.size} بند صيانة")
            }
        }
    }
}
