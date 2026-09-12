package com.ahmed.carmanager.data.gps

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

enum class AndroidCarConnectionState {
    NOT_CONNECTED,
    ANDROID_AUTO,
    ANDROID_AUTOMOTIVE,
    UNKNOWN
}

class AndroidAutoConnectionMonitor(
    context: Context
) {
    private val carConnection =
        CarConnection(context.applicationContext)

    fun observeState(): Flow<AndroidCarConnectionState> =
        callbackFlow {
            val observer = Observer<Int> { type ->
                val state = when (type) {
                    CarConnection.CONNECTION_TYPE_NOT_CONNECTED ->
                        AndroidCarConnectionState.NOT_CONNECTED

                    CarConnection.CONNECTION_TYPE_PROJECTION ->
                        AndroidCarConnectionState.ANDROID_AUTO

                    CarConnection.CONNECTION_TYPE_NATIVE ->
                        AndroidCarConnectionState.ANDROID_AUTOMOTIVE

                    else ->
                        AndroidCarConnectionState.UNKNOWN
                }

                trySend(state)
            }

            carConnection.type.observeForever(observer)

            awaitClose {
                carConnection.type.removeObserver(observer)
            }
        }.flowOn(Dispatchers.Main.immediate).distinctUntilChanged()

    fun observeAndroidAutoConnected(): Flow<Boolean> =
        callbackFlow {
            val observer = Observer<Int> { type ->
                trySend(
                    type ==
                        CarConnection.CONNECTION_TYPE_PROJECTION
                )
            }

            carConnection.type.observeForever(observer)

            awaitClose {
                carConnection.type.removeObserver(observer)
            }
        }.flowOn(Dispatchers.Main.immediate).distinctUntilChanged()
}
