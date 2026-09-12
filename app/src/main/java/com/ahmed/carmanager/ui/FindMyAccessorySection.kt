package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.gps.AccessoryTrackerConfig
import com.ahmed.carmanager.data.gps.AccessoryTrackerSlot
import com.ahmed.carmanager.data.gps.AccessoryTrackerStore

@Composable
fun FindMyAccessorySection(store: AccessoryTrackerStore) {
    var editing by remember { mutableStateOf<AccessoryTrackerSlot?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val keyTag = remember(revision) { store.load(AccessoryTrackerSlot.KEY) }
    val carTag = remember(revision) { store.load(AccessoryTrackerSlot.VEHICLE) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AutomotiveIconBadge(Icons.Rounded.Sensors, AutoTone.VIOLET, size = 48)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("تاجات وأجهزة العثور", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Xiaomi Tag • Apple Find My", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "CarManager يسجل التاج كطبقة أمان مرتبطة بالمركبة، لكن Apple لا توفر لتطبيقات Android واجهة عامة لقراءة موقع Find My مباشرة. الموقع الحي يظل داخل Find My، بينما GPS والهاتف وشاشة السيارة تبقى مصادر التتبع المباشر داخل CarManager.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
            Spacer(Modifier.height(12.dp))
            AccessorySlotRow(AccessoryTrackerSlot.KEY, keyTag) { editing = AccessoryTrackerSlot.KEY }
            Spacer(Modifier.height(8.dp))
            AccessorySlotRow(AccessoryTrackerSlot.VEHICLE, carTag) { editing = AccessoryTrackerSlot.VEHICLE }
        }
    }

    editing?.let { slot ->
        AccessoryTrackerDialog(
            slot = slot,
            existing = store.load(slot),
            onDismiss = { editing = null },
            onSave = { name -> store.save(slot, name); revision++; editing = null },
            onRemove = { store.remove(slot); revision++; editing = null }
        )
    }
}

@Composable
private fun AccessorySlotRow(slot: AccessoryTrackerSlot, config: AccessoryTrackerConfig?, onClick: () -> Unit) {
    val tone = if (config != null) AutoTone.GREEN else AutoTone.GRAPHITE
    val c = autoToneColors(tone)
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = c.soft) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (config != null) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline, null, Modifier.size(21.dp), tint = c.strong)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(slot.titleAr, fontWeight = FontWeight.Bold)
                Text(config?.let { "${it.name} • ${it.network}" } ?: "لم تتم إضافة تاج", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AccessoryTrackerDialog(
    slot: AccessoryTrackerSlot,
    existing: AccessoryTrackerConfig?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var name by remember(slot, existing?.name) { mutableStateOf(existing?.name ?: "Xiaomi Tag") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AutomotiveIconBadge(Icons.Rounded.Sensors, AutoTone.VIOLET, size = 46) },
        title = { Text(slot.titleAr) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(name, { name = it }, "اسم التاج")
                Text("الشبكة: Apple Find My", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("حفظ") } },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = onRemove) { Text("إزالة", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
}
