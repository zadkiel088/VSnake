package fr.eurecom.vsnake_test1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class PauseGameActivity : AppCompatActivity() {
    private var debugOverlay: AccelerometerDebugOverlay? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pause)
        val hostScore = intent.getIntExtra("hostScore", 0)
        val clientScore = intent.getIntExtra("clientScore", 0)
        val time = intent.getLongExtra("time", 0)
        val minutes = time / 60
        val seconds = time % 60
        val formatted = String.format("%02d:%02d", minutes, seconds)

        findViewById<TextView>(R.id.txtPauseScore).text = "Host : $hostScore | Client : $clientScore"
        findViewById<TextView>(R.id.txtPauseTime).text = "Temps : $formatted"
        findViewById<Button>(R.id.btnResume).setOnClickListener {
            Log.d("DEBUG_PAUSEMENU", "Send Resume to the other player")
            NetworkManager.socket.send("RESUME")
            finish()
        }
        findViewById<Button>(R.id.btnMenu).setOnClickListener {
            val intent = Intent(this, MainMenuActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
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