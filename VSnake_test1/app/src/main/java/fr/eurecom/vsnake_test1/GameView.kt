package fr.eurecom.vsnake_test1

import android.content.Context
import android.content.Intent
import android.view.SurfaceView
import kotlin.random.Random
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import kotlin.math.min
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import java.util.concurrent.atomic.AtomicBoolean


class GameView : SurfaceView, Runnable, SurfaceHolder.Callback {

    constructor(context: Context) : super(context) {
        init(context)
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    private lateinit var sensorController: SensorController
    private val isPlaying = AtomicBoolean(false)
    private var gameThread: Thread? = null
    private val paint = Paint()
    private var hostSnake = Snake()
    private var clientSnake = Snake()
    private var hostColor = Color.GREEN
    private var clientColor = Color.BLUE
    private var food = Point(10, 10)
    private var gridSize = 50
    private val gridCountX = 20
    private val gridCountY = 15
    private var offsetX = 0f
    private var offsetY = 0f
    private var snakeDirection = Direction.RIGHT
    private var startTime = System.currentTimeMillis()
    private var restoredElapsed: Long? = null
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        isAntiAlias = true
    }

    var networkController: GameNetworkController? = null

    private var isHost = false

    private var isPaused = false

    private var canRun = false

    private var surfaceReady = false

    private var hostSpeedMultiplier = 1f
    private var clientSpeedMultiplier = 1f
    private val stateLock = Any()
    private var networkThread: Thread? = null
    private val networkRunning = AtomicBoolean(false)
    var onGameOver: ((hostScore: Int, clientScore: Int, time: Long, loser: GameOverResult) -> Unit)? = null
    private var resumePending = false
    private var gameInitialized = false
    private var ticks = 0

    private var hostScore = 0
    private var clientScore = 0
    private var restoredHostScore: Int? = null
    private var restoredClientScore: Int? = null


    private fun init(context: Context) {
        Log.d("SNAKE_GAMEVIEW_INIT", "GameView initialized")
        sensorController = SensorController(context) { dx, dy ->
            val newDirection = when {
                dx > 2 -> Direction.RIGHT
                dx < -2 -> Direction.LEFT
                dy > 2 -> Direction.UP
                dy < -2 -> Direction.DOWN
                else -> snakeDirection
            }
            if (!isOppositeDirection(newDirection)) {
                snakeDirection = newDirection

                if (isHost) {
                    hostSnake.setDirection(newDirection)
                } else {
                    networkController?.onRemoteInput(newDirection.name)
                }
            }
        }
        SensorHolder.controller = sensorController
        holder.addCallback(this)
    }

    override fun run() {
        // Boucle du thread, surveillée par isPlaying
        Log.d("SNAKE_GAMEVIEW_RUN", "Game loop started")
        while (isPlaying.get()) {
            if (isHost) {
                update()
            }
            draw()

            try {
                val baseDelay = 200L
                val delay = (baseDelay / hostSpeedMultiplier).toLong()
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
                // Le thread a été interrompu pendant un pause() → on sort proprement
                break
            }
        }
    }

    private fun update() {
        if (!gameInitialized) {
            Log.d("SNAKE_GAMEVIEW_UPDATE_1", "Update OK | host=${hostSnake.getHead()} client=${clientSnake.getHead()}")
            return
        }

        hostSnake.move()
        clientSnake.move()

        ticks++

        if (ticks < 2) return

        if (hostSnake.getHead() == food) {
            hostSnake.grow()
            spawnFood()
            hostScore++
        }

        if (clientSnake.getHead() == food) {
            clientSnake.grow()
            spawnFood()
            clientScore++
        }


        if (isHost) {
            val vsResult = checkSnakeVsSnake()
            if (vsResult != null) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000

                onGameOver?.invoke(
                    hostScore,
                    clientScore,
                    elapsed,
                    vsResult
                )

                networkController?.onGameOver(
                    vsResult,
                    hostScore,
                    clientScore,
                    elapsed
                )

                stopGameThread()
                return
            }

        }

        if (checkCollision(hostSnake) || checkCollision(clientSnake)) {
            Log.d("SNAKE_GAMEVIEW_UPDATE_2", "Collision detected → game over")

            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            val loser =
                if (checkCollision(hostSnake)) GameOverResult.HOST_LOST
                else GameOverResult.CLIENT_LOST

            onGameOver?.invoke(
                hostScore,
                clientScore,
                elapsed,
                loser
            )

            networkController?.onGameOver(
                loser,
                hostScore,
                clientScore,
                elapsed
            )


            stopGameThread()
            sensorController.unregister()
            return
        }
    }

    private fun draw() {
        synchronized(stateLock) {
            if (!holder.surface.isValid) return
            val canvas = holder.lockCanvas()
            canvas.drawColor(getBackgroundColorByLight())
            // walls
            paint.color = Color.DKGRAY
            for (x in 0 until gridCountX) {
                for (y in 0 until gridCountY) {
                    if (x == 0 || x == gridCountX - 1 || y == 0 || y == gridCountY - 1) {
                        canvas.drawRect(
                            offsetX + x * gridSize.toFloat(),
                            offsetY + y * gridSize.toFloat(),
                            offsetX + (x + 1) * gridSize.toFloat(),
                            offsetY + (y + 1) * gridSize.toFloat(),
                            paint
                        )
                    }
                }
            }
            // snake
            // host snake
            paint.color = hostColor
            for (p in hostSnake.getBody()) {
                canvas.drawRect(
                    offsetX + p.x * gridSize,
                    offsetY + p.y * gridSize,
                    offsetX + (p.x + 1) * gridSize,
                    offsetY + (p.y + 1) * gridSize,
                    paint
                )
            }

            // client snake
            paint.color = clientColor
            for (p in clientSnake.getBody()) {
                canvas.drawRect(
                    offsetX + p.x * gridSize,
                    offsetY + p.y * gridSize,
                    offsetX + (p.x + 1) * gridSize,
                    offsetY + (p.y + 1) * gridSize,
                    paint
                )
            }

            // food
            paint.color = getFruitColorByTime()
            canvas.drawRect(
                offsetX + food.x * gridSize.toFloat(),
                offsetY + food.y * gridSize.toFloat(),
                offsetX + (food.x + 1) * gridSize.toFloat(),
                offsetY + (food.y + 1) * gridSize.toFloat(),
                paint
            )
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            val minutes = elapsed / 60
            val seconds = elapsed % 60
            val timeText = String.format("%02d:%02d", minutes, seconds)
            canvas.drawText("Host: $hostScore", 20f, 60f, textPaint)
            canvas.drawText("Client: $clientScore", 20f, 120f, textPaint)
            canvas.drawText("Time: $timeText", 20f, 180f, textPaint)
            canvas.drawText("Host Speed x$hostSpeedMultiplier",20f,240f,textPaint)
            holder.unlockCanvasAndPost(canvas)
        }
    }

    fun resume() {
        if (!canRun || !surfaceReady) {
            Log.d("SNAKE_GAMEVIEW_RESUME_1", "resume deferred (canRun=$canRun surfaceReady=$surfaceReady)")
            resumePending = true
            return
        }

        resumePending = false
        isPaused = false

        if (isPlaying.get()) return

        Log.d("SNAKE_GAMEVIEW_GAME_2", "resume() called | canRun=$canRun | isPlaying=${isPlaying.get()}")
        isPlaying.set(true)

        // Nouveau thread à chaque reprise
        gameThread = Thread(this)
        gameThread?.start()

        sensorController.register()

        restoredHostScore?.let { this.hostScore = it }
        restoredClientScore?.let { this.clientScore = it }
        restoredElapsed?.let { elapsed ->
            startTime = System.currentTimeMillis() - (elapsed * 1000)
        }

        restoredHostScore = null
        restoredClientScore = null
        restoredElapsed = null
    }

    fun pause() {
        isPaused = true
        stopGameThread()
        sensorController.unregister()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        Log.d("SNAKE_GAMEVIEW_SURFACECREATED", "Surface created | canRun=$canRun")

        if (resumePending && canRun) {
            resume()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        pause()
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        if (!gameInitialized) {
            initSnakes()
            spawnFood()
            gameInitialized = true

            Log.d(
                "SNAKE_GAMEVIEW_SURFACECHANGED",
                "Game initialized after surface ready (${width}x${height})"
            )
        }
    }


    fun startFromNetwork() {
        Log.d("SNAKE_GAMEVIEW_STARTFROMNETWORK", "START received → game starts")
        canRun = true

        if (isHost) {
            initSnakes()
            spawnFood()
        }

        resume()
    }

    private fun stopGameThread() {
        if (!isPlaying.get()) return
        isPlaying.set(false)
        gameThread?.interrupt()  // Réveille le thread s'il dort
        //gameThread?.join(50)     // Timeout de sécurité
        gameThread = null
    }

    private fun getBackgroundColorByLight(): Int {
        val lux = sensorController.lastLightLux
        Log.d("SNAKE_GAMEVIEW_GETBACKGROUNDCOLOR", "intensity of the light=$lux")
        return when {
            lux > 1000f -> Color.WHITE
            lux > 200f -> Color.DKGRAY
            else -> Color.BLACK
        }
    }

    private fun getFruitColorByTime(): Int {
        val hour = java.util.Calendar.getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 6..10 -> Color.GREEN
            in 11..15 -> Color.RED
            in 16..19 -> Color.rgb(255, 165, 0) // orange
            else -> Color.WHITE
        }
    }

    /*fun setSnakeColorByContinent(continent: String) {
        snakeColor = when (continent) {
            "EUROPE" -> Color.BLUE
            "AFRICA" -> Color.YELLOW
            "ASIA" -> Color.RED
            "NORTH_AMERICA" -> Color.GREEN
            "SOUTH_AMERICA" -> Color.MAGENTA
            "OCEANIA" -> Color.CYAN
            else -> Color.WHITE
        }
        Log.d("SNAKE_GAMEVIEW_SNAKECOLOR", "Snake color set for continent=$continent")
    }*/
    fun setHostColor(color: Int) {
        hostColor = color
    }
    fun setClientColor(color: Int) {
        clientColor = color
    }

    private fun spawnFood() {
        do {
            food = Point(
                Random.nextInt(1, gridCountX - 1),
                Random.nextInt(1, gridCountY - 1)
            )
        } while (hostSnake.getBody().contains(food) ||
            clientSnake.getBody().contains(food)
        )
    }

    private fun checkCollision(snake: Snake): Boolean {
        val head = snake.getHead()
        if (ticks > 1 && snake.getBody().drop(1).contains(head))
            return true
        if (head.x <= 0 || head.x >= gridCountX - 1 ||
            head.y <= 0 || head.y >= gridCountY - 1
        ) return true
        /*if (snake.getBody().drop(1).contains(head))
            return true*/
        return false
    }

    /*fun setSpeedMultiplier(multiplier: Float) {
        speedMultiplier = multiplier.coerceIn(0.75f, 1.25f)
        Log.d("SNAKE_GAMEVIEW_SNAKESPEED", "Speed multiplier set to $speedMultiplier")
    }*/

    fun setHostSpeedMultiplier(mult: Float) {
        hostSpeedMultiplier = mult
        Log.d("SNAKE_GAMEVIEW_SNAKESPEED", "Speed host multiplier set to $hostSpeedMultiplier")
    }
    fun setClientSpeedMultiplier(mult: Float) {
        clientSpeedMultiplier = mult
        Log.d("SNAKE_GAMEVIEW_SNAKESPEED", "Speed client multiplier set to $clientSpeedMultiplier")
    }

    private fun isOppositeDirection(newDir: Direction): Boolean {
        return (snakeDirection == Direction.UP && newDir == Direction.DOWN) ||
                (snakeDirection == Direction.DOWN && newDir == Direction.UP) ||
                (snakeDirection == Direction.LEFT && newDir == Direction.RIGHT) ||
                (snakeDirection == Direction.RIGHT && newDir == Direction.LEFT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gridSize = min(w / gridCountX, h / gridCountY)
        offsetX = (w - gridCountX * gridSize) / 2f
        offsetY = (h - gridCountY * gridSize) / 2f
    }

    fun getHostScore(): Int = hostScore
    fun getClientScore(): Int = clientScore

    fun setRestoredState(
        hostScore: Int,
        clientScore: Int,
        elapsed: Long
    ) {
        restoredHostScore = hostScore
        restoredClientScore = clientScore
        restoredElapsed = elapsed
    }


    fun getElapsedTime(): Long =
        (System.currentTimeMillis() - startTime) / 1000

    fun calibrateSensors() {
        sensorController.calibrate()
    }

    fun setHostMode(sendState: (String) -> Unit) {
        isHost = true
        networkRunning.set(true)

        networkThread = Thread {
            try {
                while (networkRunning.get()) {
                    Thread.sleep(50)
                    sendState(buildGameState())
                }
            } catch (e: InterruptedException) {
                Log.d("SNAKE_GAMEVIEW_SETHOSTMODE", "Network thread stopped cleanly")
            }
        }

        networkThread?.start()
    }

    fun stopNetwork() {
        networkRunning.set(false)
        networkThread?.interrupt()
        networkThread = null
    }


    fun setClientMode() {
        isHost = false
    }

    fun buildGameState(): String {
        synchronized(stateLock) {
            return "STATE;" +
                    "host=${hostSnake.serialize()};" +
                    "client=${clientSnake.serialize()};" +
                    "food=${food.x},${food.y};" +
                    "hostScore=$hostScore;"+
                    "clientScore=$clientScore;" +
                    "hostSpeed=$hostSpeedMultiplier;" +
                    "clientSpeed=$clientSpeedMultiplier;" +
                    "hostColor=$hostColor;" +
                    "clientColor=$clientColor"
        }
    }

    fun applyRemoteState(state: String) {
        synchronized(stateLock) {
            val map = state.split(";")
                .drop(1)
                .associate {
                    val (k, v) = it.split("=")
                    k to v
                }

            hostSpeedMultiplier = map["hostSpeed"]?.toFloat() ?: hostSpeedMultiplier
            clientSpeedMultiplier = map["clientSpeed"]?.toFloat() ?: clientSpeedMultiplier

            hostColor = map["hostColor"]?.toInt() ?: hostColor
            clientColor = map["clientColor"]?.toInt() ?: clientColor


            map["host"]?.let {
                hostSnake.setBody(parseSnake(it))
            }

            map["client"]?.let {
                val body = parseSnake(it).distinct()
                if (body.size >= 3) {
                    clientSnake.setBody(body)
                }
            }

            map["food"]?.let {
                val (x, y) = it.split(",")
                food = Point(x.toInt(), y.toInt())
            }
        }
    }

    private fun parseSnake(data: String): List<Point> =
        data.split("|").map {
            val (x, y) = it.split(",")
            Point(x.toInt(), y.toInt())
        }

    fun applyClientInput(input: String) {
        val dir = try {
            Direction.valueOf(input)
        } catch (e: Exception) {
            return
        }

        clientSnake.setDirection(dir)
    }

    private fun initSnakes() {
        val centerX = gridCountX / 2
        val centerY = gridCountY / 2

        // HOST : centre, un peu au-dessus, vers la droite
        hostSnake.setBody(
            listOf(
                Point(centerX, centerY - 2),
                Point(centerX, centerY - 2),
                Point(centerX, centerY - 2)
            )
        )
        hostSnake.setDirection(Direction.RIGHT)

        // CLIENT : centre, un peu en dessous, vers la gauche
        clientSnake.setBody(
            listOf(
                Point(centerX, centerY + 2),
                Point(centerX, centerY + 2),
                Point(centerX, centerY + 2)
            )
        )
        clientSnake.setDirection(Direction.LEFT)
    }

    private fun checkSnakeVsSnake(): GameOverResult? {
        val hostHead = hostSnake.getHead()
        val clientHead = clientSnake.getHead()

        if (clientSnake.getBody().contains(hostHead)) {
            return GameOverResult.HOST_LOST
        }

        if (hostSnake.getBody().contains(clientHead)) {
            return GameOverResult.CLIENT_LOST
        }

        return null
    }

    enum class GameOverResult {
        HOST_LOST,
        CLIENT_LOST
    }
}