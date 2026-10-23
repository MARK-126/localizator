package io.localizator.app.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.localizator.app.R
import io.localizator.app.ui.nearby.NearbyScreen
import io.localizator.app.ui.protect.ProtectScreen

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    SEARCH(R.string.tab_search, Icons.Filled.Search),
    PROTECT(R.string.tab_protect, Icons.Filled.Lock),
}

/** Pantalla principal con navegación inferior entre [NearbyScreen] y [ProtectScreen]. */
@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(Tab.SEARCH) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.SEARCH -> NearbyScreen(modifier = Modifier.padding(padding))
            Tab.PROTECT -> ProtectScreen(modifier = Modifier.padding(padding))
        }
    }
}
