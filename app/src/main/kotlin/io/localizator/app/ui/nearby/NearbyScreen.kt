package io.localizator.app.ui.nearby

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import io.localizator.core.tracking.NearbyFilter
import io.localizator.core.tracking.ProximityZone
import java.util.Locale

/**
 * Lista de dispositivos BLE cercanos con filtros de distancia máxima y "solo Android".
 * El escaneo solo está activo mientras la pantalla es visible.
 */
@Composable
fun NearbyScreen(
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        FilterBar(
            filter = state.filter,
            onMaxDistance = viewModel::setMaxDistance,
            onAndroidPhonesOnly = viewModel::setAndroidPhonesOnly,
        )
        when {
            state.totalCount == 0 -> CenteredMessage(R.string.nearby_searching, showProgress = true)
            state.devices.isEmpty() -> CenteredMessage(R.string.nearby_no_matches, showProgress = false)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        pluralStringResource(
                            R.plurals.nearby_count,
                            state.totalCount,
                            state.devices.size,
                            state.totalCount,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.devices, key = { it.id }) { DeviceRow(it) }
            }
        }
    }
}

@Composable
private fun FilterBar(
    filter: NearbyFilter,
    onMaxDistance: (Double) -> Unit,
    onAndroidPhonesOnly: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter.androidPhonesOnly,
            onClick = { onAndroidPhonesOnly(!filter.androidPhonesOnly) },
            label = { Text(stringResource(R.string.filter_android_only)) },
        )
        MAX_DISTANCE_OPTIONS.forEach { meters ->
            FilterChip(
                selected = filter.maxDistanceMeters == meters,
                onClick = { onMaxDistance(meters) },
                label = { Text(stringResource(R.string.filter_max_distance, meters.toInt())) },
            )
        }
    }
}

@Composable
private fun CenteredMessage(@StringRes message: Int, showProgress: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showProgress) CircularProgressIndicator()
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
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

private val MAX_DISTANCE_OPTIONS = listOf(5.0, 15.0, NearbyFilter.DEFAULT_MAX_DISTANCE_METERS)

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
