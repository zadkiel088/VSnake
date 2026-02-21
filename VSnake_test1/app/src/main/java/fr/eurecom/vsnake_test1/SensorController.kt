package fr.eurecom.vsnake_test1

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs


class SensorController(
    private val context: Context,
    private val onTilt: (dx: Float, dy: Float) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var firstEvent = true
    private val tiltThreshold = 3.0f
    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    var lastLightLux: Float = 0f
        private set


    fun calibrate() {
        offsetX = lastX
        offsetY = lastY
    }
    fun register() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    fun unregister() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {

        // ✅ On ne traite QUE l'accéléromètre ici
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) {
            return
        }

        // Sécurité supplémentaire
        if (event.values.size < 2) {
            return
        }

        var ax = event.values[0]
        var ay = event.values[1]

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = try {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.rotation
        } catch (e: Exception) {
            Surface.ROTATION_0
        }

        when (rotation) {
            Surface.ROTATION_90 -> {
                val temp = ax
                ax = -ay
                ay = temp
            }
            Surface.ROTATION_180 -> {
                ax = -ax
                ay = -ay
            }
            Surface.ROTATION_270 -> {
                val temp = ax
                ax = ay
                ay = -temp
            }
        }

        lastX = ax
        lastY = ay

        if (firstEvent) {
            calibrate()
            firstEvent = false
        }

        val dx = -(ax - offsetX)
        val dy = -(ay - offsetY)

        if (abs(dx) < tiltThreshold && abs(dy) < tiltThreshold) return

        onTilt(dx, dy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}