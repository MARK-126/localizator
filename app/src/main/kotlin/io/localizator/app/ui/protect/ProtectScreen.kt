package io.localizator.app.ui.protect

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.localizator.app.R
import io.localizator.app.qr.QrCodes
import java.io.File

/**
 * Modo protegido: nombre del teléfono, interruptor del faro (con consentimiento la primera vez y
 * solicitud de notificaciones en Android 13+), interruptor de ubicación compartida (solicita permiso
 * de ubicación), QR de emparejamiento y copia de respaldo compartible.
 */
@Composable
fun ProtectScreen(modifier: Modifier = Modifier, viewModel: ProtectViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showConsent by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable(state.name) { mutableStateOf(state.name) }
    val qr = remember(state.pairingCode) { QrCodes.encode(state.pairingCode, QR_SIZE_PX) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.setEnabled(true) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> if (results.values.any { it }) viewModel.setShareLocation(true) }

    val enable = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setEnabled(true)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.protect_title), style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = nameDraft,
            onValueChange = { nameDraft = it },
            label = { Text(stringResource(R.string.protect_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.setName(nameDraft) }),
            supportingText = { Text(stringResource(R.string.protect_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (nameDraft.trim() != state.name && nameDraft.isNotBlank()) {
            Button(onClick = { viewModel.setName(nameDraft) }) { Text(stringResource(R.string.protect_name_save)) }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.protect_switch), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (state.enabled) R.string.protect_switch_on else R.string.protect_switch_off),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { checked ->
                        when {
                            !checked -> viewModel.setEnabled(false)
                            state.consentGiven -> enable()
                            else -> showConsent = true
                        }
                    },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.protect_location), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.protect_location_body), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = state.shareLocation,
                    onCheckedChange = { checked ->
                        if (checked) {
                            locationPermission.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        } else {
                            viewModel.setShareLocation(false)
                        }
                    },
                )
            }
        }

        Text(stringResource(R.string.protect_qr_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.protect_qr_body), style = MaterialTheme.typography.bodyMedium)
        Image(
            bitmap = qr.asImageBitmap(),
            contentDescription = stringResource(R.string.protect_qr_title),
            modifier = Modifier.size(240.dp).align(Alignment.CenterHorizontally),
        )
        OutlinedButton(
            onClick = { shareQr(context, qr, state.name) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.protect_qr_share))
        }
        Text(
            stringResource(R.string.protect_qr_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(stringResource(R.string.consent_title)) },
            text = { Text(stringResource(R.string.consent_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showConsent = false
                    viewModel.giveConsent()
                    enable()
                }) { Text(stringResource(R.string.consent_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showConsent = false }) { Text(stringResource(R.string.consent_cancel)) }
            },
        )
    }
}

private fun shareQr(context: Context, qr: Bitmap, name: String) {
    val dir = File(context.cacheDir, "qr").apply { mkdirs() }
    val file = File(dir, "localizator-qr.png")
    file.outputStream().use { qr.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.protect_qr_share_subject, name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.protect_qr_share)))
}

private const val QR_SIZE_PX = 768
