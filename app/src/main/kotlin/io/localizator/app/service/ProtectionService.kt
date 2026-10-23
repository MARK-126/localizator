package io.localizator.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.localizator.app.MainActivity
import io.localizator.app.R
import io.localizator.app.ble.BeaconAdvertiser
import io.localizator.app.localizatorApp
import io.localizator.core.beacon.BeaconCodec
import io.localizator.core.beacon.BeaconProtocol
import io.localizator.core.beacon.RotatingId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service (`connectedDevice`) que emite el faro mientras el modo protegido está activo.
 *
 * Reinicia el anuncio al empezar cada ventana del ID rotativo y, si el emisor falla o no está
 * disponible, lo reintenta cada [RETRY_INTERVAL_MS]. La acción [ACTION_STOP] (botón de la
 * notificación) desactiva el modo protegido y detiene el servicio.
 */
class ProtectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null

    @Volatile
    private var failed = false

    private lateinit var advertiser: BeaconAdvertiser

    override fun onCreate() {
        super.onCreate()
        advertiser = BeaconAdvertiser(this) { failed = true }
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            localizatorApp.protection.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop == null) loop = scope.launch { advertiseLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        advertiser.stop()
        super.onDestroy()
    }

    private suspend fun advertiseLoop() {
        var advertisedWindow: Long? = null
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val window = BeaconProtocol.windowOf(now)
            if (window != advertisedWindow || failed) {
                val secret = localizatorApp.protection.state.value.beacon.secret
                val payload = BeaconCodec.encode(RotatingId.compute(secret, window), null)
                failed = false
                advertisedWindow = if (advertiser.start(payload)) window else null
            }
            val untilNextWindow = BeaconProtocol.nextWindowStart(now) - now
            delay(minOf(untilNextWindow, RETRY_INTERVAL_MS))
        }
    }

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

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ProtectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProtectionService::class.java))
        }
    }
}
