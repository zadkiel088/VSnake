package fr.eurecom.vsnake_test1

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class AccelerometerDebugOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val topBar: View
    private val bottomBar: View
    private val leftBar: View
    private val rightBar: View

    init {
        LayoutInflater.from(context).inflate(R.layout.accelerometer_debug_overlay, this, true)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        leftBar = findViewById(R.id.leftBar)
        rightBar = findViewById(R.id.rightBar)
        // initial state hidden
        topBar.alpha = 0f
        bottomBar.alpha = 0f
        leftBar.alpha = 0f
        rightBar.alpha = 0f
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
    }

    fun register() {
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    fun unregister() {
        sensorManager.unregisterListener(this)
        topBar.alpha = 0f
        bottomBar.alpha = 0f
        leftBar.alpha = 0f
        rightBar.alpha = 0f
    }
    override fun onSensorChanged(event: SensorEvent) {
        var ax = event.values[0]
        var ay = event.values[1]
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_90 -> {
                val temp = ax; ax = -ay; ay = temp
            }
            Surface.ROTATION_180 -> {
                ax = -ax; ay = -ay
            }
            Surface.ROTATION_270 -> {
                val temp = ax; ax = ay; ay = -temp
            }
        }
        val threshold = 3f
        topBar.alpha = if (ay < -threshold) 1f else 0f
        bottomBar.alpha = if (ay > threshold) 1f else 0f
        leftBar.alpha = if (ax > threshold) 1f else 0f
        rightBar.alpha = if (ax < -threshold) 1f else 0f
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}