package io.localizator.app.ui.calibration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.localizator.app.R
import io.localizator.app.ui.nearby.NearbyViewModel
import io.localizator.core.calibration.Calibrator
import io.localizator.core.signal.PathLossModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private enum class Step { READY, MEASURING, RESULT }

/**
 * Asistente de calibración de un teléfono emparejado: mide [MEASURE_MS] de RSSI crudo a cada
 * distancia de [DISTANCES_METERS], ajusta un [PathLossModel] con [Calibrator] y muestra la distancia
 * estimada antes y después. *Guardar* lo aplica; *Repetir* reinicia; *Atrás* lo descarta.
 */
@Composable
fun CalibrationScreen(
    phoneId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NearbyViewModel = viewModel(),
) {
    BackHandler(onBack = onDone)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val name = state.myPhones.firstOrNull { it.beacon.id == phoneId }?.beacon?.name.orEmpty()
    val calibrator = remember { Calibrator() }
    val previousModel = remember { viewModel.currentModel(phoneId) }
    var pointIndex by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf(Step.READY) }
    var progress by remember { mutableFloatStateOf(0f) }
    var samples by remember { mutableIntStateOf(0) }
    var tooFew by remember { mutableStateOf(false) }
    val distance = DISTANCES_METERS[pointIndex]

    LaunchedEffect(step, pointIndex) {
        if (step != Step.MEASURING) return@LaunchedEffect
        calibrator.clear(distance)
        samples = 0
        val ticker = launch {
            val start = System.currentTimeMillis()
            while (true) {
                progress = ((System.currentTimeMillis() - start).toFloat() / MEASURE_MS).coerceAtMost(1f)
                delay(PROGRESS_TICK_MS)
            }
        }
        withTimeoutOrNull(MEASURE_MS) {
            viewModel.rssiSamples(phoneId).collect {
                calibrator.addSample(distance, it.toDouble())
                samples = calibrator.sampleCount(distance)
            }
        }
        ticker.cancel()
        tooFew = calibrator.sampleCount(distance) < Calibrator.MIN_SAMPLES_PER_POINT
        when {
            tooFew -> step = Step.READY
            pointIndex < DISTANCES_METERS.lastIndex -> {
                pointIndex++
                step = Step.READY
            }
            else -> step = Step.RESULT
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.radar_back))
            }
            Text(stringResource(R.string.calibration_title, name), style = MaterialTheme.typography.titleLarge)
        }

        when (step) {
            Step.READY -> {
                Text(
                    stringResource(R.string.calibration_step, pointIndex + 1, DISTANCES_METERS.size),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(R.string.calibration_place, formatMeters(distance)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.calibration_conditions), style = MaterialTheme.typography.bodyMedium)
                if (tooFew) {
                    Text(
                        stringResource(R.string.calibration_too_few, samples, Calibrator.MIN_SAMPLES_PER_POINT),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = {
                    tooFew = false
                    progress = 0f
                    step = Step.MEASURING
                }) { Text(stringResource(R.string.calibration_measure)) }
            }

            Step.MEASURING -> {
                Text(
                    stringResource(R.string.calibration_measuring, formatMeters(distance)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.calibration_samples, samples), style = MaterialTheme.typography.bodyMedium)
            }

            Step.RESULT -> {
                val model = calibrator.fit()
                if (model == null) {
                    Text(stringResource(R.string.calibration_failed), color = MaterialTheme.colorScheme.error)
                } else {
                    CalibrationResult(calibrator, previousModel, model)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model != null) {
                        Button(onClick = {
                            viewModel.saveCalibration(phoneId, model)
                            onDone()
                        }) { Text(stringResource(R.string.calibration_save)) }
                    }
                    OutlinedButton(onClick = {
                        calibrator.clear()
                        pointIndex = 0
                        tooFew = false
                        step = Step.READY
                    }) { Text(stringResource(R.string.calibration_repeat)) }
                    TextButton(onClick = onDone) { Text(stringResource(R.string.consent_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun CalibrationResult(calibrator: Calibrator, before: PathLossModel, after: PathLossModel) {
    val beforeEstimates = calibrator.evaluate(before)
    val afterEstimates = calibrator.evaluate(after)
    Text(stringResource(R.string.calibration_result), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(
            R.string.calibration_model,
            String.format(Locale.getDefault(), "%.1f", after.measuredPowerAt1m),
            String.format(Locale.getDefault(), "%.2f", after.pathLossExponent),
        ),
        style = MaterialTheme.typography.bodyLarge,
    )
    DISTANCES_METERS.forEach { real ->
        val b = beforeEstimates[real] ?: return@forEach
        val a = afterEstimates[real] ?: return@forEach
        Text(
            stringResource(R.string.calibration_compare, formatMeters(real), formatMeters(b), formatMeters(a)),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatMeters(meters: Double): String = String.format(Locale.getDefault(), "%.1f m", meters)

private val DISTANCES_METERS = listOf(1.0, 3.0)
private const val MEASURE_MS = 12_000L
private const val PROGRESS_TICK_MS = 200L
