package io.localizator.app.ui.map

import android.Manifest
import android.graphics.Color
import android.location.Location
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.localizator.app.R
import io.localizator.app.ui.nearby.MyPhone
import io.localizator.app.ui.nearby.NearbyViewModel
import io.localizator.core.location.FusedLocation
import io.localizator.core.location.GeoPoint
import io.localizator.core.location.LocationFusion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import org.osmdroid.util.GeoPoint as OsmPoint

/**
 * Mapa (OpenStreetMap) de un teléfono emparejado: posición propia, círculo GPS del teléfono buscado
 * (influencia), anillo BLE alrededor de este teléfono (precisión) y punto estimado por
 * [LocationFusion]. Solicita permiso de ubicación al abrirse.
 */
@Composable
fun MapScreen(
    phoneId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phone = state.myPhones.firstOrNull { it.beacon.id == phoneId }

    var permissionGranted by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionGranted = viewModel.hasLocationPermission() }
    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val own by produceState<Location?>(null, permissionGranted) {
        if (permissionGranted) viewModel.ownLocationUpdates().catch { }.collect { value = it }
    }
    val nowMs by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(1_000)
            value = SystemClock.elapsedRealtime()
        }
    }

    val ownPoint = own?.let { GeoPoint(it.latitude, it.longitude) }
    val shared = phone?.location?.location
    val estimate = phone?.estimate
    val fused = if (ownPoint != null && shared != null && estimate != null) {
        LocationFusion.fuse(
            seeker = ownPoint,
            target = GeoPoint(shared.latitude, shared.longitude),
            targetAccuracyMeters = shared.accuracyMeters.toDouble(),
            bleDistanceMeters = estimate.distanceMeters,
            bleMinMeters = estimate.distanceMinMeters,
            bleMaxMeters = estimate.distanceMaxMeters,
        )
    } else {
        null
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.radar_back))
            }
            Text(phone?.beacon?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(ownPoint = ownPoint, phone = phone, fused = fused)
        }
        MapInfo(
            phone = phone,
            nowMs = nowMs,
            permissionGranted = permissionGranted,
            hasOwnLocation = ownPoint != null,
            fused = fused,
        )
    }
}

@Composable
private fun OsmMap(ownPoint: GeoPoint?, phone: MyPhone?, fused: FusedLocation?) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(DEFAULT_ZOOM)
        }
    }
    var centered by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val ownLabel = stringResource(R.string.map_you)
    val estimateLabel = stringResource(R.string.map_estimate)
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) { map ->
        map.overlays.clear()
        val sharedLocation = phone?.location?.location
        if (sharedLocation != null) {
            val center = OsmPoint(sharedLocation.latitude, sharedLocation.longitude)
            map.overlays += Polygon(map).apply {
                points = Polygon.pointsAsCircle(center, sharedLocation.accuracyMeters.coerceAtLeast(1).toDouble())
                fillPaint.color = Color.argb(48, 33, 150, 243)
                outlinePaint.color = Color.rgb(33, 150, 243)
                outlinePaint.strokeWidth = 3f
            }
        }
        val estimate = phone?.estimate
        if (ownPoint != null && estimate != null) {
            val center = OsmPoint(ownPoint.latitude, ownPoint.longitude)
            map.overlays += Polygon(map).apply {
                points = Polygon.pointsAsCircle(center, estimate.distanceMaxMeters.coerceAtLeast(0.5))
                if (estimate.distanceMinMeters > 0.1) {
                    holes = listOf(Polygon.pointsAsCircle(center, estimate.distanceMinMeters))
                }
                fillPaint.color = Color.argb(64, 255, 152, 0)
                outlinePaint.color = Color.rgb(255, 152, 0)
                outlinePaint.strokeWidth = 2f
            }
        }
        if (ownPoint != null) {
            map.overlays += Marker(map).apply {
                position = OsmPoint(ownPoint.latitude, ownPoint.longitude)
                title = ownLabel
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            if (!centered) {
                map.controller.setCenter(OsmPoint(ownPoint.latitude, ownPoint.longitude))
                centered = true
            }
        }
        if (fused != null && fused.consistent) {
            map.overlays += Marker(map).apply {
                position = OsmPoint(fused.estimate.latitude, fused.estimate.longitude)
                title = estimateLabel
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
        }
        map.invalidate()
    }
}

@Composable
private fun MapInfo(
    phone: MyPhone?,
    nowMs: Long,
    permissionGranted: Boolean,
    hasOwnLocation: Boolean,
    fused: FusedLocation?,
) {
    val name = phone?.beacon?.name.orEmpty()
    val cardinals = stringArrayResource(R.array.cardinals_8)
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        val received = phone?.location
        Text(
            if (received != null) {
                stringResource(
                    R.string.map_gps_line,
                    name,
                    received.location.accuracyMeters,
                    formatAge(received.ageSeconds(nowMs)),
                )
            } else {
                stringResource(R.string.map_gps_missing, name)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val estimate = phone?.estimate
        Text(
            if (estimate != null) {
                stringResource(R.string.map_ble_line, formatMeters(estimate.distanceMinMeters), formatMeters(estimate.distanceMaxMeters))
            } else {
                stringResource(R.string.map_ble_missing)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val summary = when {
            !permissionGranted -> stringResource(R.string.map_no_permission)
            !hasOwnLocation -> stringResource(R.string.map_waiting_own)
            fused == null -> null
            fused.consistent -> stringResource(
                R.string.map_estimate_line,
                formatMeters(estimate?.distanceMeters ?: 0.0),
                cardinals[((fused.bearingDegrees + 22.5) / 45).toInt() % 8],
            )
            else -> stringResource(R.string.map_inconsistent)
        }
        if (summary != null) Text(summary, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatMeters(meters: Double): String = String.format(Locale.getDefault(), "%.1f m", meters)

private fun formatAge(seconds: Long): String =
    if (seconds < 60) "$seconds s" else "${(seconds / 60.0).roundToInt()} min"

private const val DEFAULT_ZOOM = 19.0
