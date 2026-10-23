package io.localizator.app.ui.nearby

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import io.localizator.app.R
import io.localizator.core.tracking.Manufacturers
import java.util.Locale

/** Detalle de un dispositivo: estimación y contenido en crudo del último anuncio, copiable como texto. */
@Composable
fun DeviceDetailsDialog(device: SelectedDevice, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val report = device.report()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(device.estimate.name ?: device.estimate.id) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(report, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) {
                Text(stringResource(R.string.details_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.details_close)) }
        },
    )
}

/** Informe en texto plano, una clave por línea. */
internal fun SelectedDevice.report(): String = buildString {
    val e = estimate
    appendLine("mac: ${e.id}")
    appendLine("name: ${e.name ?: "-"}")
    appendLine("category: ${e.category}")
    appendLine("rssi: ${e.rawRssi} dBm (filtrado ${format(e.filteredRssi)})")
    appendLine("distance: ${format(e.distanceMeters)} m [${format(e.distanceMinMeters)} – ${format(e.distanceMaxMeters)}]")
    appendLine("model: ${e.modelSource}")
    appendLine("trend: ${e.trend}")
    appendLine("samples: ${e.sampleCount}")
    val d = details ?: return@buildString
    appendLine("connectable: ${d.connectable}")
    d.manufacturerData.forEach { (id, data) ->
        appendLine("manufacturer ${Manufacturers.labelOf(id)} (0x%04X): %s".format(Locale.US, id, data))
    }
    d.serviceUuids.forEach { appendLine("service: $it") }
    d.serviceData.forEach { (uuid, data) -> appendLine("service data $uuid: $data") }
    append("raw: ${d.rawBytes}")
}

private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
