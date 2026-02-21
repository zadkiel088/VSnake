package fr.eurecom.vsnake_test1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class GameOverActivity : AppCompatActivity() {

    private var debugOverlay: AccelerometerDebugOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SNAKE_GAMEOVER", "GameOverActivity started")
        setContentView(R.layout.activity_game_over)

        val hostScore = intent.getIntExtra("hostScore", 0)
        val clientScore = intent.getIntExtra("clientScore", 0)
        val time = intent.getLongExtra("time", 0)
        val loser = intent.getStringExtra("loser") ?: "UNKNOWN"

        val minutes = time / 60
        val seconds = time % 60
        val formatted = String.format("%02d:%02d", minutes, seconds)

        findViewById<TextView>(R.id.txtScore).text =
            "Host : $hostScore\nClient : $clientScore"

        findViewById<TextView>(R.id.txtTime).text =
            "Time : $formatted"

        findViewById<TextView>(R.id.txtResult).text =
            when (loser) {
                "HOST_LOST" -> "Host Lost"
                "CLIENT_LOST" -> "Client Lost"
                else -> "Draw"
            }
        findViewById<Button>(R.id.btnRestart).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
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