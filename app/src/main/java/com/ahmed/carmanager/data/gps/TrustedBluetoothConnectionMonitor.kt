package com.ahmed.carmanager.data.gps

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class TrustedBluetoothConnectionMonitor(
    context: Context,
    private val store: VehicleContextStore
) {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(BluetoothManager::class.java)

    private val refreshVersion = MutableStateFlow(0L)

    fun refresh() {
        refreshVersion.value = refreshVersion.value + 1L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(vehicleId: String): Flow<Boolean> =
        refreshVersion.flatMapLatest {
            observeCurrent(vehicleId)
        }

    private fun observeCurrent(vehicleId: String): Flow<Boolean> {
        val target = store.trustedBluetoothAddress(vehicleId)
            ?.uppercase()
            ?: return flowOf(false)

        if (!hasPermission()) return flowOf(false)

        val adapter = manager?.adapter
            ?: return flowOf(false)

        return callbackFlow {
            val active =
                mutableMapOf<Int, MutableSet<String>>()
            val proxies =
                mutableMapOf<Int, BluetoothProfile>()

            fun publish() {
                trySend(
                    active.values.any { target in it }
                )
            }

            fun update(
                profile: Int,
                address: String,
                connected: Boolean
            ) {
                val addresses =
                    active.getOrPut(profile) {
                        mutableSetOf()
                    }

                if (connected) {
                    addresses += address.uppercase()
                } else {
                    addresses -= address.uppercase()
                }

                publish()
            }

            val receiver = object : BroadcastReceiver() {
                @Suppress("DEPRECATION")
                override fun onReceive(
                    context: Context?,
                    intent: Intent?
                ) {
                    if (!hasPermission()) {
                        trySend(false)
                        return
                    }

                    val action = intent?.action ?: return
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(
                            BluetoothDevice.EXTRA_DEVICE
                        ) ?: return

                    val address = device.address.uppercase()

                    when (action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED ->
                            update(-1, address, true)

                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            active.values.forEach {
                                it -= address
                            }
                            publish()
                        }

                        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ->
                            update(
                                BluetoothProfile.A2DP,
                                address,
                                intent.getIntExtra(
                                    BluetoothProfile.EXTRA_STATE,
                                    BluetoothProfile.STATE_DISCONNECTED
                                ) == BluetoothProfile.STATE_CONNECTED
                            )

                        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ->
                            update(
                                BluetoothProfile.HEADSET,
                                address,
                                intent.getIntExtra(
                                    BluetoothProfile.EXTRA_STATE,
                                    BluetoothProfile.STATE_DISCONNECTED
                                ) == BluetoothProfile.STATE_CONNECTED
                            )
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }

            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

            val listener =
                object : BluetoothProfile.ServiceListener {
                    @SuppressLint("MissingPermission")
                    override fun onServiceConnected(
                        profile: Int,
                        proxy: BluetoothProfile
                    ) {
                        proxies[profile] = proxy
                        active[profile] =
                            proxy.connectedDevices
                                .map {
                                    it.address.uppercase()
                                }
                                .toMutableSet()
                        publish()
                    }

                    override fun onServiceDisconnected(
                        profile: Int
                    ) {
                        active.remove(profile)
                        proxies.remove(profile)
                        publish()
                    }
                }

            adapter.getProfileProxy(
                appContext,
                listener,
                BluetoothProfile.A2DP
            )

            adapter.getProfileProxy(
                appContext,
                listener,
                BluetoothProfile.HEADSET
            )

            publish()

            awaitClose {
                runCatching {
                    appContext.unregisterReceiver(receiver)
                }

                proxies.forEach { (profile, proxy) ->
                    runCatching {
                        adapter.closeProfileProxy(
                            profile,
                            proxy
                        )
                    }
                }
            }
        }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
}

