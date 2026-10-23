package io.localizator.app.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.core.content.ContextCompat
import io.localizator.core.signal.HeadingFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Rumbo del teléfono a partir del sensor `TYPE_ROTATION_VECTOR`, en grados desde el norte
 * magnético, en [0, 360). El sistema de coordenadas se reasigna según la rotación de la pantalla.
 */
class CompassProvider(private val context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable: Boolean get() = sensor != null

    /** Azimut sin suavizar; el flujo termina de inmediato si no hay sensor. */
    fun azimuthDegrees(): Flow<Double> = callbackFlow {
        val manager = sensorManager
        val rotationSensor = sensor
        if (manager == null || rotationSensor == null) {
            close()
            awaitClose {}
            return@callbackFlow
        }
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                val (axisX, axisY) = when (ContextCompat.getDisplayOrDefault(context).rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
                SensorManager.getOrientation(remapped, orientation)
                trySend(HeadingFilter.normalize(Math.toDegrees(orientation[0].toDouble())))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }
}
