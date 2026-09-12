package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.gps.AndroidAutoConnectionMonitor
import com.ahmed.carmanager.data.gps.AndroidCarConnectionState
import com.ahmed.carmanager.data.gps.VehicleContextStore

@Composable
fun AndroidAutoSection(
    vehicleId: String,
    store: VehicleContextStore,
    monitor: AndroidAutoConnectionMonitor
) {
    val boundVehicleId by
        store.androidAutoVehicleIdFlow.collectAsState()

    val connectionFlow = remember(monitor) {
        monitor.observeState()
    }

    val connectionState by connectionFlow.collectAsState(
        initial = AndroidCarConnectionState.NOT_CONNECTED
    )

    val boundHere = boundVehicleId == vehicleId
    val boundElsewhere =
        boundVehicleId != null && !boundHere

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    text = when (connectionState) {
                        AndroidCarConnectionState.ANDROID_AUTO ->
                            "● متصل الآن"

                        AndroidCarConnectionState.ANDROID_AUTOMOTIVE ->
                            "تشغيل أصلي على نظام السيارة"

                        AndroidCarConnectionState.UNKNOWN ->
                            "حالة غير معروفة"

                        AndroidCarConnectionState.NOT_CONNECTED ->
                            "○ غير متصل"
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Android Auto",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Text(
                text = when {
                    boundHere ->
                        "مرتبط بهذه المركبة."

                    boundElsewhere ->
                        "مرتبط بمركبة أخرى. يمكنك نقله لهذه المركبة."

                    else ->
                        "غير مرتبط بمركبة حتى الآن."
                },
                style = MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (boundHere) {
                OutlinedButton(
                    onClick = {
                        store.clearAndroidAutoBinding()
                    }
                ) {
                    Text("إلغاء الربط")
                }
            } else {
                Button(
                    onClick = {
                        store.bindAndroidAutoToVehicle(vehicleId)
                    }
                ) {
                    Text(
                        if (boundElsewhere)
                            "ربط بهذه المركبة بدلا منها"
                        else
                            "ربط بهذه المركبة"
                    )
                }
            }
        }
    }
}
