package com.ahmed.carmanager.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.ahmed.carmanager.data.gps.TrustedBluetoothDevice
import com.ahmed.carmanager.data.gps.TrustedBluetoothManager
import com.ahmed.carmanager.data.gps.TrustedBluetoothConnectionMonitor

@Composable
fun TrustedBluetoothSection(
    vehicleId: String,
    manager: TrustedBluetoothManager,
    connectionMonitor: TrustedBluetoothConnectionMonitor
) {
    var trusted by remember(vehicleId) {
        mutableStateOf(manager.trustedDevice(vehicleId))
    }
    val hasBluetoothPermission =
        manager.hasConnectPermission()

    val bluetoothConnected by remember(
        vehicleId,
        trusted?.address,
        hasBluetoothPermission
    ) {
        connectionMonitor.observe(vehicleId)
    }.collectAsState(initial = false)

    var devices by remember {
        mutableStateOf<List<TrustedBluetoothDevice>>(emptyList())
    }

    var showPicker by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    fun openPicker() {
        val result = manager.pairedDevices()

        result.onSuccess {
            devices = it
            showPicker = true
            message = if (it.isEmpty()) {
                "لا توجد أجهزة Bluetooth مقترنة على هذا الجهاز."
            } else {
                null
            }
        }

        result.onFailure {
            message = it.message ?: "تعذر قراءة أجهزة Bluetooth."
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                connectionMonitor.refresh()
                openPicker()
            } else {
                message = "يلزم السماح بالاتصال بأجهزة Bluetooth."
            }
        }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "Bluetooth المركبة",
                fontWeight = FontWeight.Bold
            )

            if (trusted == null) {
                Text(
                    text = "لم يتم تحديد جهاز موثوق لهذه المركبة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = trusted!!.name,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "جهاز موثوق لهذه المركبة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (trusted != null) {
                Text(
                    text = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            !hasBluetoothPermission ->
                            "يلزم إذن Bluetooth لمعرفة حالة الاتصال"

                        bluetoothConnected ->
                            "● متصل الآن"

                        else ->
                            "○ غير متصل"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bluetoothConnected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (trusted != null) {
                    TextButton(
                        onClick = {
                            manager.clearTrustedDevice(vehicleId)
                            connectionMonitor.refresh()
                            trusted = null
                            message = "تم إلغاء ربط Bluetooth."
                        }
                    ) {
                        Text("إلغاء الربط")
                    }
                }

                FilledTonalButton(
                    onClick = {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            !manager.hasConnectPermission()
                        ) {
                            permissionLauncher.launch(
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        } else {
                            openPicker()
                        }
                    },
                    contentPadding = PaddingValues(
                        horizontal = 10.dp,
                        vertical = 5.dp
                    )
                ) {
                    Text(
                        if (trusted == null)
                            "اختيار جهاز"
                        else
                            "تغيير الجهاز"
                    )
                }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = {
                showPicker = false
            },
            title = {
                Text("اختر Bluetooth المركبة")
            },
            text = {
                if (devices.isEmpty()) {
                    Text(
                        "قم بإقران الجهاز من إعدادات Android أولا."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = devices,
                            key = { it.address }
                        ) { device ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        manager
                                            .bindTrustedDevice(
                                                vehicleId,
                                                device
                                            )
                                            .onSuccess {
                                                trusted = device
                                                connectionMonitor.refresh()
                                                message =
                                                    "تم ربط الجهاز بهذه المركبة."
                                                showPicker = false
                                            }
                                            .onFailure {
                                                message =
                                                    it.message
                                                        ?: "تعذر ربط الجهاز."
                                            }
                                    },
                                shape = MaterialTheme.shapes.small,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier.padding(9.dp)
                                ) {
                                    Text(
                                        device.name,
                                        fontWeight =
                                            FontWeight.SemiBold
                                    )

                                    Text(
                                        device.address,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPicker = false
                    }
                ) {
                    Text("إغلاق")
                }
            }
        )
    }
}

