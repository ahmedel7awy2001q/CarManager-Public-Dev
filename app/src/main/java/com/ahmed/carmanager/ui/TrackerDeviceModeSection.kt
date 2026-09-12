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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.gps.TrackerDeviceIdentityStore
import com.ahmed.carmanager.data.gps.TrackerDeviceRole
import com.ahmed.carmanager.data.gps.VehicleContextStore

@Composable
fun TrackerDeviceModeSection(
    vehicleId: String,
    identityStore: TrackerDeviceIdentityStore,
    contextStore: VehicleContextStore
) {
    val role by identityStore.roleFlow.collectAsState()
    val boundVehicleId by
        contextStore.headUnitVehicleIdFlow.collectAsState()

    val isHeadUnit =
        role == TrackerDeviceRole.HEAD_UNIT

    val boundHere =
        isHeadUnit && boundVehicleId == vehicleId

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "هذا الجهاز",
                style = MaterialTheme.typography.titleSmall
            )

            Text(
                text = when {
                    boundHere ->
                        "شاشة السيارة — مرتبطة بهذه المركبة"

                    isHeadUnit ->
                        "شاشة السيارة — مرتبطة بمركبة أخرى"

                    else ->
                        "هاتف — يدير المركبات ولا يتتبعها دون سياق موثوق"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isHeadUnit) {
                    OutlinedButton(
                        onClick = {
                            identityStore.setRole(
                                TrackerDeviceRole.PHONE
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("هاتف")
                    }
                } else {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        enabled = false
                    ) {
                        Text("هاتف")
                    }
                }

                if (boundHere) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        enabled = false
                    ) {
                        Text("شاشة السيارة")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            contextStore.bindHeadUnitToVehicle(vehicleId)
                            identityStore.setRole(
                                TrackerDeviceRole.HEAD_UNIT
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("شاشة السيارة")
                    }
                }
            }

            if (isHeadUnit && !boundHere) {
                Text(
                    text = "اختيار شاشة السيارة هنا سينقل ربطها إلى هذه المركبة.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
