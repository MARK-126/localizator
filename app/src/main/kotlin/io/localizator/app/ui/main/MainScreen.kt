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
import io.localizator.app.ui.map.MapScreen
import io.localizator.app.ui.nearby.NearbyScreen
import io.localizator.app.ui.protect.ProtectScreen
import io.localizator.app.ui.radar.RadarScreen

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    SEARCH(R.string.tab_search, Icons.Filled.Search),
    PROTECT(R.string.tab_protect, Icons.Filled.Lock),
}

/**
 * Pantalla principal con navegación inferior entre [NearbyScreen] y [ProtectScreen]. Dentro de
 * *Buscar*, abrir un teléfono muestra [RadarScreen] y, desde él, [MapScreen]; *Atrás* vuelve un nivel.
 */
@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(Tab.SEARCH) }
    var radarPhoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var mapPhoneId by rememberSaveable { mutableStateOf<String?>(null) }
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
            Tab.SEARCH -> {
                val mapId = mapPhoneId
                val radarId = radarPhoneId
                when {
                    mapId != null -> MapScreen(mapId, onBack = { mapPhoneId = null }, modifier = Modifier.padding(padding))
                    radarId != null -> RadarScreen(
                        radarId,
                        onBack = { radarPhoneId = null },
                        onOpenMap = { mapPhoneId = it },
                        modifier = Modifier.padding(padding),
                    )
                    else -> NearbyScreen(onOpenRadar = { radarPhoneId = it }, modifier = Modifier.padding(padding))
                }
            }
            Tab.PROTECT -> ProtectScreen(modifier = Modifier.padding(padding))
        }
    }
}
