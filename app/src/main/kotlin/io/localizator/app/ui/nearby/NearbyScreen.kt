package io.localizator.app.ui.nearby

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.localizator.app.R
import io.localizator.app.qr.QrCodes
import io.localizator.core.beacon.PairedBeacon
import io.localizator.core.signal.Trend
import io.localizator.core.tracking.DeviceEstimate
import io.localizator.core.tracking.Manufacturers
import io.localizator.core.tracking.NearbyFilter
import io.localizator.core.tracking.ProximityZone
import java.util.Locale

/**
 * Búsqueda: sección "Mis teléfonos" (emparejados, sin filtrar; al tocar uno detectado se invoca
 * [onOpenRadar]) con emparejamiento por cámara o imagen, y lista del resto de dispositivos con
 * filtros de distancia máxima y "solo Android".
 * El escaneo solo está activo mientras la pantalla es visible.
 */
@Composable
fun NearbyScreen(
    onOpenRadar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPairOptions by rememberSaveable { mutableStateOf(false) }
    var forgetCandidate by rememberSaveable { mutableStateOf<String?>(null) }

    val onCode: (String?) -> Unit = { text -> reportPairing(context, text?.let(viewModel::pair)) }
    val scanQr = rememberLauncherForActivityResult(ScanContract()) { onCode(it.contents) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onCode(QrCodes.loadBitmap(context, uri)?.let(QrCodes::decode))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.my_phones_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showPairOptions = true }) { Text(stringResource(R.string.my_phones_add)) }
            }
        }
        if (state.myPhones.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.my_phones_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(state.myPhones, key = { it.beacon.id }) { phone ->
            val estimate = phone.estimate
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (estimate != null) {
                    DeviceRow(estimate, onClick = { onOpenRadar(estimate.id) })
                } else {
                    UndetectedPhoneRow(phone.beacon, onClick = { forgetCandidate = phone.beacon.id })
                }
            }
        }
        item {
            FilterBar(
                filter = state.filter,
                onMaxDistance = viewModel::setMaxDistance,
                onAndroidPhonesOnly = viewModel::setAndroidPhonesOnly,
            )
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                when {
                    state.totalCount == 0 -> Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.nearby_searching), style = MaterialTheme.typography.bodyLarge)
                    }
                    state.devices.isEmpty() ->
                        Text(stringResource(R.string.nearby_no_matches), style = MaterialTheme.typography.bodyLarge)
                    else -> Text(
                        pluralStringResource(R.plurals.nearby_count, state.totalCount, state.devices.size, state.totalCount),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        items(state.devices, key = { it.id }) { device ->
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                DeviceRow(device, onClick = { viewModel.select(device.id) })
            }
        }
    }

    state.selected?.let { selected ->
        val isPaired = selected.estimate.id.startsWith(PairedBeacon.ID_PREFIX)
        DeviceDetailsDialog(
            device = selected,
            onDismiss = { viewModel.select(null) },
            onForget = if (isPaired) ({ viewModel.forget(selected.estimate.id) }) else null,
        )
    }

    if (showPairOptions) {
        AlertDialog(
            onDismissRequest = { showPairOptions = false },
            title = { Text(stringResource(R.string.pair_title)) },
            text = { Text(stringResource(R.string.pair_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showPairOptions = false
                    scanQr.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt(context.getString(R.string.pair_scan_prompt))
                            .setBeepEnabled(false),
                    )
                }) { Text(stringResource(R.string.pair_scan)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPairOptions = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.pair_image)) }
            },
        )
    }

    forgetCandidate?.let { id ->
        AlertDialog(
            onDismissRequest = { forgetCandidate = null },
            title = { Text(stringResource(R.string.forget_title)) },
            text = { Text(stringResource(R.string.forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    forgetCandidate = null
                    viewModel.forget(id)
                }) { Text(stringResource(R.string.forget_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { forgetCandidate = null }) { Text(stringResource(R.string.consent_cancel)) }
            },
        )
    }
}

private fun reportPairing(context: Context, beacon: PairedBeacon?) {
    val message = if (beacon != null) {
        context.getString(R.string.pair_success, beacon.name)
    } else {
        context.getString(R.string.pair_invalid)
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

@Composable
private fun UndetectedPhoneRow(beacon: PairedBeacon, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(beacon.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.my_phones_not_detected), style = MaterialTheme.typography.bodySmall)
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
            .padding(horizontal = 16.dp),
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
private fun DeviceRow(device: DeviceEstimate, onClick: () -> Unit) {
    val manufacturer = device.manufacturerId?.let(Manufacturers::labelOf)
    val title = device.name ?: manufacturer ?: stringResource(R.string.nearby_unknown_device)

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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

private fun formatMeters(meters: Double): String =
    if (meters < MIN_DISPLAY_METERS) {
        "< " + String.format(Locale.getDefault(), "%.1f m", MIN_DISPLAY_METERS)
    } else {
        String.format(Locale.getDefault(), "%.1f m", meters)
    }

private const val MIN_DISPLAY_METERS = 0.1

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
