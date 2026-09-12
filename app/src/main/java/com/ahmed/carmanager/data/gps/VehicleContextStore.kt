package com.ahmed.carmanager.data.gps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Device-local vehicle context.
 *
 * Keeps vehicle binding and trusted connection signals outside Room
 * so GPS tracking can be expanded without a database migration.
 */
class VehicleContextStore(
    context: Context
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val _androidAutoVehicleId = MutableStateFlow(
        prefs.getString(
            KEY_ANDROID_AUTO_VEHICLE_ID,
            null
        )?.takeIf { it.isNotBlank() }
    )

    val androidAutoVehicleIdFlow: StateFlow<String?> =
        _androidAutoVehicleId.asStateFlow()

    private val _headUnitVehicleId = MutableStateFlow(
        prefs.getString(
            KEY_HEAD_UNIT_VEHICLE_ID,
            null
        )?.takeIf { it.isNotBlank() }
    )

    val headUnitVehicleIdFlow: StateFlow<String?> =
        _headUnitVehicleId.asStateFlow()

    fun boundHeadUnitVehicleId(): String? =
        prefs.getString(
            KEY_HEAD_UNIT_VEHICLE_ID,
            null
        )?.takeIf { it.isNotBlank() }

    fun bindHeadUnitToVehicle(
        vehicleId: String
    ) {
        require(vehicleId.isNotBlank()) {
            "معرف المركبة غير صالح."
        }

        prefs.edit()
            .putString(
                KEY_HEAD_UNIT_VEHICLE_ID,
                vehicleId
            )
            .apply()
        _headUnitVehicleId.value = vehicleId
    }

    fun clearHeadUnitBinding() {
        prefs.edit()
            .remove(KEY_HEAD_UNIT_VEHICLE_ID)
            .apply()
        _headUnitVehicleId.value = null
    }
    fun androidAutoVehicleId(): String? =
        prefs.getString(
            KEY_ANDROID_AUTO_VEHICLE_ID,
            null
        )?.takeIf { it.isNotBlank() }

    fun bindAndroidAutoToVehicle(
        vehicleId: String
    ) {
        require(vehicleId.isNotBlank()) {
            "معرف المركبة غير صالح."
        }

        prefs.edit()
            .putString(
                KEY_ANDROID_AUTO_VEHICLE_ID,
                vehicleId
            )
            .apply()
        _androidAutoVehicleId.value = vehicleId
    }

    fun clearAndroidAutoBinding() {
        prefs.edit()
            .remove(KEY_ANDROID_AUTO_VEHICLE_ID)
            .apply()
        _androidAutoVehicleId.value = null
    }

    fun trustedBluetoothAddress(
        vehicleId: String
    ): String? =
        prefs.getString(
            bluetoothAddressKey(vehicleId),
            null
        )?.takeIf { it.isNotBlank() }

    fun trustedBluetoothName(
        vehicleId: String
    ): String? =
        prefs.getString(
            bluetoothNameKey(vehicleId),
            null
        )?.takeIf { it.isNotBlank() }

    fun setTrustedBluetooth(
        vehicleId: String,
        address: String?,
        name: String?
    ) {
        require(vehicleId.isNotBlank()) {
            "معرف المركبة غير صالح."
        }

        prefs.edit().apply {
            if (address.isNullOrBlank()) {
                remove(bluetoothAddressKey(vehicleId))
            } else {
                putString(
                    bluetoothAddressKey(vehicleId),
                    address.trim()
                )
            }

            if (name.isNullOrBlank()) {
                remove(bluetoothNameKey(vehicleId))
            } else {
                putString(
                    bluetoothNameKey(vehicleId),
                    name.trim()
                )
            }
        }.apply()
    }

    fun clearTrustedBluetooth(
        vehicleId: String
    ) {
        prefs.edit()
            .remove(bluetoothAddressKey(vehicleId))
            .remove(bluetoothNameKey(vehicleId))
            .apply()
    }

    private val autoTrackingVersion = MutableStateFlow(0L)

    fun observeAutoTrackingEnabled(
        vehicleId: String
    ): Flow<Boolean> =
        autoTrackingVersion
            .map { autoTrackingEnabled(vehicleId) }
            .distinctUntilChanged()

    fun autoTrackingEnabled(
        vehicleId: String
    ): Boolean =
        prefs.getBoolean(
            autoTrackingKey(vehicleId),
            false
        )

    fun setAutoTrackingEnabled(
        vehicleId: String,
        enabled: Boolean
    ) {
        prefs.edit()
            .putBoolean(
                autoTrackingKey(vehicleId),
                enabled
            )
            .apply()
        autoTrackingVersion.value = autoTrackingVersion.value + 1L
    }

    private fun bluetoothAddressKey(
        vehicleId: String
    ) = "vehicle_${vehicleId}_bt_address"

    private fun bluetoothNameKey(
        vehicleId: String
    ) = "vehicle_${vehicleId}_bt_name"

    private fun autoTrackingKey(
        vehicleId: String
    ) = "vehicle_${vehicleId}_auto_tracking"

    private companion object {
        const val PREFS_NAME =
            "carmanager_vehicle_context"

        const val KEY_HEAD_UNIT_VEHICLE_ID =
            "head_unit_vehicle_id"

        const val KEY_ANDROID_AUTO_VEHICLE_ID =
            "android_auto_vehicle_id"
    }
}

