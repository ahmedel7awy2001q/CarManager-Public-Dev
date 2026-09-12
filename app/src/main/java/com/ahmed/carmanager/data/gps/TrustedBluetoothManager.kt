package com.ahmed.carmanager.data.gps

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class TrustedBluetoothDevice(
    val address: String,
    val name: String
)

class TrustedBluetoothManager(
    context: Context,
    private val vehicleContextStore: VehicleContextStore
) {
    private val appContext = context.applicationContext

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    fun isBluetoothAvailable(): Boolean =
        bluetoothManager?.adapter != null

    @SuppressLint("MissingPermission")
    fun pairedDevices(): Result<List<TrustedBluetoothDevice>> =
        runCatching {
            requireConnectPermission()

            val adapter = bluetoothManager?.adapter
                ?: return@runCatching emptyList()

            adapter.bondedDevices
                .map { device ->
                    TrustedBluetoothDevice(
                        address = device.address,
                        name = device.name
                            ?.takeIf { it.isNotBlank() }
                            ?: "Bluetooth Device"
                    )
                }
                .sortedBy { it.name.lowercase() }
        }

    fun trustedDevice(
        vehicleId: String
    ): TrustedBluetoothDevice? {
        val address =
            vehicleContextStore
                .trustedBluetoothAddress(vehicleId)
                ?: return null

        val name =
            vehicleContextStore
                .trustedBluetoothName(vehicleId)
                ?: "Bluetooth Device"

        return TrustedBluetoothDevice(
            address = address,
            name = name
        )
    }

    fun bindTrustedDevice(
        vehicleId: String,
        device: TrustedBluetoothDevice
    ): Result<Unit> =
        runCatching {
            require(vehicleId.isNotBlank()) {
                "Invalid vehicle id."
            }

            val paired = pairedDevices().getOrThrow()

            require(
                paired.any {
                    it.address.equals(
                        device.address,
                        ignoreCase = true
                    )
                }
            ) {
                "Bluetooth device is not paired."
            }

            vehicleContextStore.setTrustedBluetooth(
                vehicleId = vehicleId,
                address = device.address,
                name = device.name
            )
        }

    fun clearTrustedDevice(
        vehicleId: String
    ) {
        vehicleContextStore.clearTrustedBluetooth(vehicleId)
    }

    private fun requireConnectPermission() {
        check(hasConnectPermission()) {
            "Bluetooth permission is required."
        }
    }
}
