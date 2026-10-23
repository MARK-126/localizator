package io.localizator.app.ui.home

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.localizator.app.R
import io.localizator.app.ble.BluetoothPermissions
import io.localizator.app.ble.BluetoothState
import io.localizator.app.ble.rememberBluetoothState
import io.localizator.app.ui.main.MainScreen

/**
 * Pantalla inicial. Muestra, por orden de prioridad: falta de BLE, permisos pendientes,
 * Bluetooth desactivado o [MainScreen]. Los permisos se vuelven a comprobar en cada `ON_RESUME`.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(BluetoothPermissions.allGranted(context)) }
    var permissionsRequested by remember { mutableStateOf(false) }
    val bluetoothState by rememberBluetoothState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsGranted = BluetoothPermissions.allGranted(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionsRequested = true
        permissionsGranted = results.values.all { it }
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    if (bluetoothState == BluetoothState.ON && permissionsGranted) {
        MainScreen()
        return
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                bluetoothState == BluetoothState.UNSUPPORTED ->
                    StatusMessage(R.string.app_name, R.string.bluetooth_unsupported)

                !permissionsGranted -> {
                    StatusMessage(R.string.permissions_title, R.string.permissions_body)
                    Button(onClick = { permissionLauncher.launch(BluetoothPermissions.required) }) {
                        Text(stringResource(R.string.permissions_grant))
                    }
                    if (permissionsRequested) {
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        }) {
                            Text(stringResource(R.string.permissions_settings))
                        }
                    }
                }

                else -> {
                    StatusMessage(R.string.bluetooth_off_title, R.string.bluetooth_off_body)
                    Button(onClick = { enableBluetoothLauncher.launch(enableBluetoothIntent()) }) {
                        Text(stringResource(R.string.bluetooth_enable))
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun enableBluetoothIntent() = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

@Composable
private fun StatusMessage(@StringRes title: Int, @StringRes body: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
}
