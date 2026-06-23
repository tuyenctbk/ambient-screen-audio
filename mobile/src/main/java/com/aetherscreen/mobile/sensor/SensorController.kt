package com.aetherscreen.mobile.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class SensorController(
    context: Context,
    private val onProximityChanged: (Boolean) -> Unit,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastAcceleration = 0f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private val shakeThreshold = 14f // Delta acceleration threshold for shake detection

    fun startListening() {
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maximumRange = proximitySensor?.maximumRange ?: 5f
                val isClose = distance < maximumRange
                onProximityChanged(isClose)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                lastAcceleration = currentAcceleration
                currentAcceleration = sqrt(x * x + y * y + z * z)
                val delta = kotlin.math.abs(currentAcceleration - lastAcceleration)

                if (delta > shakeThreshold) {
                    onShakeDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
