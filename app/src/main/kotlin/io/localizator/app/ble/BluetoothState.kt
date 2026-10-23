package io.localizator.app.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Estado del adaptador Bluetooth. */
enum class BluetoothState { UNSUPPORTED, OFF, ON }

fun Context.bluetoothAdapter(): BluetoothAdapter? =
    getSystemService(BluetoothManager::class.java)?.adapter

fun Context.currentBluetoothState(): BluetoothState {
    val adapter = bluetoothAdapter() ?: return BluetoothState.UNSUPPORTED
    return if (adapter.isEnabled) BluetoothState.ON else BluetoothState.OFF
}

/** [BluetoothState] observado mediante `BluetoothAdapter.ACTION_STATE_CHANGED` mientras el composable está activo. */
@Composable
fun rememberBluetoothState(): State<BluetoothState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(context.currentBluetoothState()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                state.value = context.currentBluetoothState()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
