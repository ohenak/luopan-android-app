package com.luopan.compass.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorLayer(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val hasMagnetometer: Boolean get() = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) != null
    val hasGyroscope: Boolean get() = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    val hasAccelerometer: Boolean get() = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    private var lastMagEvent: SensorEvent? = null
    private var lastAccelEvent: SensorEvent? = null
    private var lastGyroEvent: SensorEvent? = null

    // Rotation vector values copied on receipt (SensorEvent is recycled by the framework)
    private var lastRotVec: FloatArray? = null

    private var frameCallback: ((SensorFrame) -> Unit)? = null

    private val rotMatrix = FloatArray(16)
    private val orientation = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> lastMagEvent = event
            Sensor.TYPE_ACCELEROMETER -> lastAccelEvent = event
            Sensor.TYPE_GYROSCOPE -> lastGyroEvent = event
            Sensor.TYPE_ROTATION_VECTOR -> {
                // Copy values immediately — event object is reused by the framework
                val len = minOf(event.values.size, 5)
                lastRotVec = event.values.copyOfRange(0, len)
            }
        }
        tryEmitFrame()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun tryEmitFrame() {
        val mag = lastMagEvent ?: return
        val accel = lastAccelEvent ?: return

        val frame = SensorFrame(
            timestamp_ns = mag.timestamp,
            mag_x = mag.values[0], mag_y = mag.values[1], mag_z = mag.values[2],
            mag_bias_x = mag.values[3], mag_bias_y = mag.values[4], mag_bias_z = mag.values[5],
            accel_x = accel.values[0], accel_y = accel.values[1], accel_z = accel.values[2],
            gyro_x = lastGyroEvent?.values?.get(0),
            gyro_y = lastGyroEvent?.values?.get(1),
            gyro_z = lastGyroEvent?.values?.get(2),
            android_heading_deg = computeAndroidHeading()
        )
        frameCallback?.invoke(frame)
    }

    private fun computeAndroidHeading(): Double? {
        val rv = lastRotVec ?: return null
        SensorManager.getRotationMatrixFromVector(rotMatrix, rv)
        SensorManager.getOrientation(rotMatrix, orientation)
        // orientation[0] = azimuth in radians, clockwise from North
        val deg = Math.toDegrees(orientation[0].toDouble())
        return ((deg % 360.0) + 360.0) % 360.0
    }

    fun frames(): Flow<SensorFrame> = callbackFlow {
        frameCallback = { trySend(it) }
        registerSensors()
        awaitClose {
            frameCallback = null
            unregisterSensors()
        }
    }

    private fun registerSensors() {
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }
}
