package io.localizator.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.localizator.app.MainActivity
import io.localizator.app.R
import io.localizator.app.ble.BeaconAdvertiser
import io.localizator.app.localizatorApp
import io.localizator.app.location.LocationSource
import io.localizator.app.sensors.BatteryMonitor
import io.localizator.core.beacon.BeaconCodec
import io.localizator.core.beacon.BeaconProtocol
import io.localizator.core.beacon.RotatingId
import io.localizator.core.location.BatteryPolicy
import io.localizator.core.location.LocationPayload
import io.localizator.core.location.PowerMode
import io.localizator.core.location.SharedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Foreground service (`connectedDevice`, más `location` si se comparte la ubicación) que emite el
 * faro mientras el modo protegido está activo.
 *
 * Reinicia el anuncio:
 * - al empezar cada ventana del ID rotativo;
 * - en cuanto el Bluetooth vuelve a `STATE_ON` (Android detiene los anuncios al apagarlo sin
 *   notificar a la app);
 * - al recibir una posición nueva, al cambiar el [PowerMode] y, mientras se comparte la ubicación,
 *   al menos cada [LOCATION_REFRESH_MS] para actualizar su antigüedad;
 * - si el emisor falla o no está disponible, cada [RETRY_INTERVAL_MS].
 *
 * El GPS sigue [BatteryPolicy]: cada 30 s (alta precisión), cada 5 min (equilibrada) o apagado. Si
 * el sistema no permite el tipo `location` (por ejemplo, en un reinicio en segundo plano), el
 * servicio continúa solo como `connectedDevice`. La acción [ACTION_STOP] (botón de la
 * notificación) desactiva el modo protegido y detiene el servicio.
 */
class ProtectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null

    @Volatile
    private var failed = false

    @Volatile
    private var lastLocation: Location? = null

    @Volatile
    private var powerMode = PowerMode.NORMAL

    private lateinit var advertiser: BeaconAdvertiser
    private lateinit var locationSource: LocationSource
    private val batteryPolicy = BatteryPolicy()

    private val restart = Channel<Unit>(Channel.CONFLATED)

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON) {
                restart.trySend(Unit)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        advertiser = BeaconAdvertiser(this) { failed = true }
        locationSource = LocationSource(this)
        createChannel()
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            localizatorApp.protection.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        val bluetoothOnly = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundTypes())
        }.recoverCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, bluetoothOnly)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop == null) {
            loop = scope.launch { advertiseLoop() }
            scope.launch { trackPowerAndLocation() }
        }
        restart.trySend(Unit)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothReceiver)
        scope.cancel()
        advertiser.stop()
        super.onDestroy()
    }

    private fun sharingLocation(): Boolean =
        localizatorApp.protection.state.value.shareLocation && locationSource.hasPermission()

    private fun foregroundTypes(): Int {
        val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        return if (sharingLocation()) base or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else base
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun trackPowerAndLocation() {
        combine(
            BatteryMonitor.states(this).map { batteryPolicy.modeFor(it.levelPercent, it.charging) },
            localizatorApp.protection.state.map { it.shareLocation }.distinctUntilChanged(),
        ) { mode, share -> mode to share }
            .distinctUntilChanged()
            .flatMapLatest { (mode, share) ->
                if (mode != powerMode) {
                    powerMode = mode
                    restart.trySend(Unit)
                }
                val interval = BatteryPolicy.gpsIntervalMs(mode)
                if (share && interval != null && locationSource.hasPermission()) {
                    locationSource.updates(interval, highAccuracy = mode == PowerMode.NORMAL)
                        .catch { }
                } else {
                    emptyFlow<Location>()
                }
            }
            .collect { location ->
                lastLocation = location
                restart.trySend(Unit)
            }
    }

    private suspend fun advertiseLoop() {
        var advertisedWindow: Long? = null
        var forceRestart = false
        var sequence = 0
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val window = BeaconProtocol.windowOf(now)
            val sharing = sharingLocation()
            if (window != advertisedWindow || failed || forceRestart) {
                val secret = localizatorApp.protection.state.value.beacon.secret
                val rotatingId = RotatingId.compute(secret, window)
                val payload = BeaconCodec.encode(rotatingId, null)
                val locationPayload = lastLocation?.takeIf { sharing }?.let {
                    sequence = (sequence + 1) and 0xFF
                    LocationPayload.encode(it.toShared(), secret, rotatingId, sequence)
                }
                failed = false
                val started = advertiser.start(payload, locationPayload, lowPower = powerMode == PowerMode.CRITICAL)
                advertisedWindow = if (started) window else null
            }
            val untilNextWindow = BeaconProtocol.nextWindowStart(now) - now
            val wait = minOf(untilNextWindow, if (sharing) LOCATION_REFRESH_MS else RETRY_INTERVAL_MS)
            val signalled = withTimeoutOrNull(wait) { restart.receive() } != null
            forceRestart = signalled || (sharing && lastLocation != null)
        }
    }

    private fun Location.toShared(): SharedLocation = SharedLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy.roundToInt() else UNKNOWN_ACCURACY_METERS,
        ageSeconds = ((SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000_000L).toInt(),
    )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.protection_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, ProtectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.protection_notification_title))
            .setContentText(getString(R.string.protection_notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.protection_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.localizator.app.action.STOP_PROTECTION"
        private const val RETRY_INTERVAL_MS = 30_000L
        private const val LOCATION_REFRESH_MS = 60_000L
        private const val UNKNOWN_ACCURACY_METERS = 255

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ProtectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProtectionService::class.java))
        }
    }
}
