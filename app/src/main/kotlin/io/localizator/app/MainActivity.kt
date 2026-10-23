package io.localizator.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.localizator.app.ui.home.HomeScreen
import io.localizator.app.ui.theme.LocalizatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalizatorTheme {
                HomeScreen()
            }
        }
    }
}
