package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.local.model.VehicleStatus

@Composable
internal fun GarageHistoryAccessCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("سجل المركبات", fontWeight = FontWeight.Bold)
                Text("$count مباعة أو مؤرشفة • لا تظهر في التشغيل اليومي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GarageHistorySheet(
    vehicles: List<VehicleEntity>,
    onRestore: (String) -> Unit,
    onSoftDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<VehicleEntity?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("سجل المركبات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Text("المباعة والمؤرشفة محفوظة هنا ولا تدخل في GPS أو قطع الغيار أو العمليات اليومية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vehicles, key = { it.vehicleId }) { vehicle ->
                    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.End) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (vehicle.status == VehicleStatus.SOLD) "مباعة" else "مؤرشفة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}", fontWeight = FontWeight.Bold)
                                    Text("${vehicle.year} • ${formatKm(vehicle.currentOdometerKm)} كم", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (vehicle.status == VehicleStatus.SOLD) {
                                val sale = buildList {
                                    vehicle.soldAt?.let { add("بيع ${formatDate(it)}") }
                                    vehicle.salePrice?.let { add(formatMoney(it)) }
                                }.joinToString(" • ")
                                if (sale.isNotBlank()) Text(sale, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pendingDelete = vehicle }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("إزالة")
                                }
                                Button(onClick = { onRestore(vehicle.vehicleId) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Restore, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("إعادة للجراج")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    pendingDelete?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("إزالة المركبة من الحساب؟") },
            text = { Text("سيتم إخفاء «${vehicle.displayName ?: "${vehicle.brand} ${vehicle.model}"}» من قائمة الحساب. لن نحذف سجلات الصيانة أو الوقود أو الرحلات أو الملكية من قاعدة البيانات؛ الإزالة آمنة وقابلة للاسترداد تقنيًا من النسخ الاحتياطية.") },
            confirmButton = {
                TextButton(onClick = { onSoftDelete(vehicle.vehicleId); pendingDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("إزالة من الحساب") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } }
        )
    }
}
