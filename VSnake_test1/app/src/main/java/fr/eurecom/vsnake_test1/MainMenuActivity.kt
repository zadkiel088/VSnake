package fr.eurecom.vsnake_test1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainMenuActivity : AppCompatActivity() {

    private var debugOverlay: AccelerometerDebugOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)
        val selectedCityName = intent.getStringExtra("selectedCity")

        findViewById<Button>(R.id.btnBluetooth).setOnClickListener {
            startActivity(Intent(this, BluetoothDiscoveryActivity::class.java))
        }

        findViewById<Button>(R.id.btnWifi).setOnClickListener {
            val selectedCityName = intent.getStringExtra("selectedCity")

            val wifiIntent = Intent(this, WifiDiscoveryActivity::class.java)
            wifiIntent.putExtra("selectedCity", selectedCityName)
            startActivity(wifiIntent)
        }


        findViewById<Button>(R.id.btnCalibrate).setOnClickListener {
            SensorHolder.controller?.calibrate()
        }
        debugOverlay = findViewById(R.id.debugOverlay)
    }
    override fun onResume() {
        super.onResume()
        debugOverlay?.register()
    }
    override fun onPause() {
        super.onPause()
        debugOverlay?.unregister()
    }
}