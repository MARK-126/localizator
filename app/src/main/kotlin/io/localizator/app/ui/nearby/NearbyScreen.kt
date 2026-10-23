package io.localizator.app.ui.nearby

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.localizator.app.R
import io.localizator.core.signal.Trend
import io.localizator.core.tracking.DeviceEstimate
import io.localizator.core.tracking.Manufacturers
import io.localizator.core.tracking.ProximityZone
import java.util.Locale

/** Lista de dispositivos BLE cercanos. El escaneo solo está activo mientras la pantalla es visible. */
@Composable
fun NearbyScreen(
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel = viewModel(),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    if (devices.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.nearby_searching), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                pluralStringResource(R.plurals.nearby_count, devices.size, devices.size),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(devices, key = { it.id }) { DeviceRow(it) }
    }
}

@Composable
private fun DeviceRow(device: DeviceEstimate) {
    val manufacturer = device.manufacturerId?.let(Manufacturers::labelOf)
    val title = device.name ?: manufacturer ?: stringResource(R.string.nearby_unknown_device)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(manufacturer.takeIf { device.name != null }, device.id).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.nearby_rssi, device.filteredRssi.toInt(), stringResource(zoneLabel(device.zone))),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${formatMeters(device.distanceMeters)} ${trendSymbol(device.trend)}".trimEnd(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        R.string.nearby_range,
                        formatMeters(device.distanceMinMeters),
                        formatMeters(device.distanceMaxMeters),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun formatMeters(meters: Double): String = String.format(Locale.getDefault(), "%.1f m", meters)

private fun trendSymbol(trend: Trend): String = when (trend) {
    Trend.APPROACHING -> "↑"
    Trend.MOVING_AWAY -> "↓"
    Trend.STEADY -> "•"
    Trend.UNKNOWN -> ""
}

@StringRes
private fun zoneLabel(zone: ProximityZone): Int = when (zone) {
    ProximityZone.IMMEDIATE -> R.string.zone_immediate
    ProximityZone.NEAR -> R.string.zone_near
    ProximityZone.FAR -> R.string.zone_far
    ProximityZone.UNKNOWN -> R.string.zone_unknown
}
