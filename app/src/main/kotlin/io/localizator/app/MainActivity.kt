package io.localizator.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.localizator.app.ble.BluetoothPermissions
import io.localizator.app.service.ProtectionService
import io.localizator.app.ui.home.HomeScreen
import io.localizator.app.ui.theme.LocalizatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (localizatorApp.protection.state.value.enabled && BluetoothPermissions.allGranted(this)) {
            ProtectionService.start(this)
        }
        setContent {
            LocalizatorTheme {
                HomeScreen()
            }
        }
    }
}
