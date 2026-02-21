package fr.eurecom.vsnake_test1

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView

    private lateinit var udp: UdpGameSocket

    private var isHost = false
    private var gameStarted = false
    private var startRequested = false
    lateinit var socket: NearbyGameSocket
    private var pauseActivityOpen = false
    private var hostCity: City? = null
    private var clientCity: City? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameView = findViewById(R.id.gameView)

        val cityName = intent.getStringExtra("selectedCity")
        if (cityName == null) {
            Log.e("MAIN_ACTIVITY", "selectedCity is NULL")
            finish()
            return
        }
        val selectedCity = City.valueOf(cityName)


        gameView.networkController = object : GameNetworkController {

            override fun onRemoteInput(input: String) {
                socket.send("INPUT;$input")
            }

            override fun onGameState(state: String) {}

            override fun onGameOver(
                loser: GameView.GameOverResult,
                hostScore: Int,
                clientScore: Int,
                time: Long
            ) {
                socket.send(
                    "GAMEOVER;" +
                            "loser=${loser.name};" +
                            "hostScore=$hostScore;" +
                            "clientScore=$clientScore;" +
                            "time=$time"
                )
                handleGameOverLocal(hostScore, clientScore, time, loser.name)
            }
        }

        val pauseBtn = findViewById<ImageButton>(R.id.pauseButton)
        pauseBtn.setOnClickListener {
            handlePause()
            socket.send("PAUSE")
        }

        isHost = intent.getBooleanExtra("IS_HOST", false)

        /*if (isHost) {
            hostCity = City.NICE
            clientCity = City.MARADI
            maybeComputeGameParams()
        }*/
        hostCity = selectedCity
        if (isHost) {
            gameView.setHostMode { state ->
                socket.send(state)
            }
        }
        else {
            gameView.setClientMode()
        }
        gameView.startFromNetwork()
        socket = NetworkManager.socket

        if (!isHost) {
            Log.d("DEBUG_MAINACTIVITY_CITY", "CITY send: ${selectedCity.name}")
            socket.send("CITY;name=${selectedCity.name}")
        }

        socket.setOnMessageReceived { msg ->
            runOnUiThread {
                when {
                    msg.startsWith("STATE") -> {
                        gameView.applyRemoteState(msg)
                    }

                    msg == "START" -> {
                        gameView.startFromNetwork()
                    }

                    msg == "PAUSE" -> {
                        handlePause()
                    }

                    msg == "RESUME" -> {
                        if (!isFinishing) {
                            Log.d("DEBUG_MAINACTIVITY_RESUME", "received Resume from the other player")
                            gameView.resume()
                        }
                    }

                    msg.startsWith("INPUT") -> {
                        if (isHost) {
                            val dir = msg.split(";")[1]
                            gameView.applyClientInput(dir)
                        }
                    }

                    msg.startsWith("GAMEOVER") -> {
                        Log.d("DEBUG_MAINACTIVITY_GAMEOVER", "Collision received → game over")
                        val parts = msg.split(";")
                        val map = parts.drop(1).associate {
                            val (k, v) = it.split("=")
                            k to v
                        }

                        val loser = map["loser"] ?: "UNKNOWN"
                        val hostScore = map["hostScore"]?.toInt() ?: 0
                        val clientScore = map["clientScore"]?.toInt() ?: 0
                        val time = map["time"]?.toLong() ?: 0

                        handleGameOverLocal(hostScore, clientScore, time, loser)
                    }

                    msg.startsWith("CITY") -> {
                        if (isHost) {
                            val cityName = msg.split("=")[1]
                            clientCity = City.valueOf(cityName)
                            Log.d("DEBUG_MAINACTIVITY_CITY", "CITY recieved: ${clientCity}")
                            maybeComputeGameParams()
                        }
                    }
                }
            }
        }
    }

    private fun computeSpeedMultiplier(temp: Double): Float {
        return when {
            temp <= -20 -> 0.75f
            temp in -20.0..20.0 -> {
                0.75f + ((temp + 20) / 40.0 * 0.25).toFloat()
            }
            temp in 21.0..60.0 -> {
                1.0f + ((temp - 20) / 40.0 * 0.25).toFloat()
            }
            else -> 1.25f
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("DEBUG_MAINACTIVITY_ONDESTROY", "MainActivity destroyed → closing Network")
        gameView.stopNetwork()
    }


    private fun handlePause() {
        if (pauseActivityOpen) return
        pauseActivityOpen = true

        gameView.pause()

        val elapsed = gameView.getElapsedTime()
        val hostScore = gameView.getHostScore()
        val clientScore = gameView.getClientScore()

        val intent = Intent(this, PauseGameActivity::class.java).apply {
            putExtra("hostScore", hostScore)
            putExtra("clientScore", clientScore)
            putExtra("time", elapsed)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        pauseActivityOpen = false
    }

    private fun handleGameOverLocal(
        hostScore: Int,
        clientScore: Int,
        time: Long,
        loser: String
    ) {
        val intent = Intent(this, GameOverActivity::class.java).apply {
            putExtra("hostScore", hostScore)
            putExtra("clientScore", clientScore)
            putExtra("time", time)
            putExtra("loser", loser)
        }
        startActivity(intent)
    }

    private fun handleClientCity(city: City) {
        clientCity = city
        maybeComputeGameParams()
    }

    private fun maybeComputeGameParams() {
        if (hostCity == null || clientCity == null) return

        computeCityParams(hostCity!!, true)
        computeCityParams(clientCity!!, false)

        //socket.send("START")
        //gameView.startFromNetwork()
    }

    private fun computeCityParams(city: City, isHostCity: Boolean) {
        val tempManager = TemperatureManager()
        val locationStyleManager = LocationStyleManager()

        tempManager.fetchTemperature(city.lat, city.lon) { temp ->
            temp ?: return@fetchTemperature

            val speed = computeSpeedMultiplier(temp)

            runOnUiThread {
                if (isHostCity) {
                    gameView.setHostSpeedMultiplier(speed)
                } else {
                    gameView.setClientSpeedMultiplier(speed)
                }
            }
        }

        locationStyleManager.fetchContinent(city.lat, city.lon) { continent ->
            continent ?: return@fetchContinent

            val color = continentToColor(continent)
            Log.d("SNAKE_MAINACTIVITY_SNAKECONTINENT", "continent=$continent")
            runOnUiThread {
                if (isHostCity) {
                    gameView.setHostColor(color)
                } else {
                    gameView.setClientColor(color)
                }
            }
        }
    }

    private fun continentToColor(continent: String): Int {
        val color = when (continent) {
            "EUROPE" -> Color.BLUE
            "AFRICA" -> Color.YELLOW
            "ASIA" -> Color.RED
            "NORTH_AMERICA" -> Color.GREEN
            "SOUTH_AMERICA" -> Color.MAGENTA
            "OCEANIA" -> Color.CYAN
            else -> Color.WHITE
        }

        Log.d("SNAKE_COLOR", "Snake color set for continent=$continent")
        return color
    }


}