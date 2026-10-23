package io.localizator.app.ui.radar

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.localizator.app.R
import io.localizator.app.sensors.Haptics
import io.localizator.app.ui.nearby.DeviceDetailsDialog
import io.localizator.app.ui.nearby.NearbyViewModel
import io.localizator.core.signal.ProximityFeedback
import io.localizator.core.signal.Trend
import io.localizator.core.tracking.DeviceEstimate
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Radar de un teléfono emparejado: este teléfono en el centro, círculo en la distancia estimada,
 * banda con el intervalo de incertidumbre, brújula que gira con el rumbo, tendencia y vibración por
 * proximidad. Si el teléfono deja de detectarse, conserva la última estimación en gris.
 */
@Composable
fun RadarScreen(
    phoneId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val heading by viewModel.heading.collectAsStateWithLifecycle()
    val phone = state.myPhones.firstOrNull { it.beacon.id == phoneId }
    val live = phone?.estimate
    var lastKnown by remember { mutableStateOf<DeviceEstimate?>(null) }
    SideEffect { if (live != null) lastKnown = live }
    val shown = live ?: lastKnown
    var vibrationEnabled by rememberSaveable { mutableStateOf(true) }

    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val feedback = remember { ProximityFeedback() }
    val distance by rememberUpdatedState(live?.distanceMeters)
    LaunchedEffect(vibrationEnabled, live != null) {
        while (vibrationEnabled) {
            val meters = distance ?: break
            haptics.pulse(PULSE_MS)
            delay(feedback.pulseIntervalMs(meters))
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.radar_back))
            }
            Text(
                phone?.beacon?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.select(phoneId) }, enabled = live != null) {
                Text(stringResource(R.string.radar_details))
            }
        }

        RadarCanvas(
            estimate = shown,
            lost = live == null,
            heading = heading,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )

        if (!viewModel.compassAvailable) {
            Text(stringResource(R.string.radar_compass_unavailable), style = MaterialTheme.typography.bodySmall)
        }

        if (shown != null) {
            Text(
                "${formatMeters(shown.distanceMeters)} ${trendSymbol(shown.trend)}".trimEnd(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.radar_range_trend,
                    formatMeters(shown.distanceMinMeters),
                    formatMeters(shown.distanceMaxMeters),
                    stringResource(if (live == null) R.string.radar_lost else trendLabel(shown.trend)),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(stringResource(R.string.radar_waiting), style = MaterialTheme.typography.bodyLarge)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.radar_vibration), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(12.dp))
            Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it })
        }
    }

    state.selected?.let { DeviceDetailsDialog(it, onDismiss = { viewModel.select(null) }) }
}

@Composable
private fun RadarCanvas(estimate: DeviceEstimate?, lost: Boolean, heading: Double?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val accent = if (lost) colors.outline else colors.primary
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant)
    val cardinalStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
    val cardinals = listOf(
        stringResource(R.string.cardinal_north) to 0.0,
        stringResource(R.string.cardinal_east) to 90.0,
        stringResource(R.string.cardinal_south) to 180.0,
        stringResource(R.string.cardinal_west) to 270.0,
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2 - 32.dp.toPx()
        val scaleMeters = radarScaleMeters(estimate?.distanceMaxMeters)
        val pxPerMeter = radius / scaleMeters.toFloat()

        for (ring in 1..RINGS) {
            drawCircle(colors.outlineVariant, radius * ring / RINGS, center, style = Stroke(1.dp.toPx()))
        }
        drawLabel(textMeasurer, formatScale(scaleMeters), labelStyle, polar(radius, 45.0, 0.0))

        if (estimate != null) {
            val inner = (estimate.distanceMinMeters * pxPerMeter).toFloat().coerceAtMost(radius)
            val outer = (estimate.distanceMaxMeters * pxPerMeter).toFloat().coerceAtMost(radius)
            drawCircle(
                accent.copy(alpha = 0.18f),
                (inner + outer) / 2,
                center,
                style = Stroke(max(outer - inner, 2.dp.toPx())),
            )
            drawCircle(
                accent,
                (estimate.distanceMeters * pxPerMeter).toFloat().coerceIn(2.dp.toPx(), radius),
                center,
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
            )
        }
        drawCircle(colors.primary, 7.dp.toPx(), center)

        if (heading != null) {
            for (bearing in 0 until 360 step TICK_DEGREES) {
                val outerPoint = polar(radius, bearing.toDouble(), heading)
                val innerPoint = polar(radius - 8.dp.toPx(), bearing.toDouble(), heading)
                drawLine(colors.outline, innerPoint, outerPoint, strokeWidth = 1.5.dp.toPx())
            }
            cardinals.forEach { (label, bearing) ->
                drawLabel(textMeasurer, label, cardinalStyle, polar(radius + 18.dp.toPx(), bearing, heading))
            }
        }
    }
}

private fun DrawScope.polar(r: Float, bearingDegrees: Double, headingDegrees: Double): Offset {
    val a = Math.toRadians(bearingDegrees - headingDegrees)
    return Offset(center.x + (r * sin(a)).toFloat(), center.y - (r * cos(a)).toFloat())
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

/** Escala del radar: primer valor de [SCALES_METERS] ≥ 1,2 × [maxDistance]. */
internal fun radarScaleMeters(maxDistance: Double?): Double {
    val needed = (maxDistance ?: SCALES_METERS.first()) * SCALE_MARGIN
    return SCALES_METERS.firstOrNull { it >= needed } ?: SCALES_METERS.last()
}

private fun formatScale(meters: Double): String = String.format(Locale.getDefault(), "%.0f m", meters)

private fun formatMeters(meters: Double): String =
    if (meters < 0.1) "< " + String.format(Locale.getDefault(), "%.1f m", 0.1)
    else String.format(Locale.getDefault(), "%.1f m", min(meters, 99.9))

private fun trendSymbol(trend: Trend): String = when (trend) {
    Trend.APPROACHING -> "↑"
    Trend.MOVING_AWAY -> "↓"
    Trend.STEADY -> "•"
    Trend.UNKNOWN -> ""
}

@StringRes
private fun trendLabel(trend: Trend): Int = when (trend) {
    Trend.APPROACHING -> R.string.radar_trend_approaching
    Trend.MOVING_AWAY -> R.string.radar_trend_away
    Trend.STEADY -> R.string.radar_trend_steady
    Trend.UNKNOWN -> R.string.radar_trend_unknown
}

private val SCALES_METERS = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 30.0)
private const val SCALE_MARGIN = 1.2
private const val RINGS = 4
private const val TICK_DEGREES = 30
private const val PULSE_MS = 30L
