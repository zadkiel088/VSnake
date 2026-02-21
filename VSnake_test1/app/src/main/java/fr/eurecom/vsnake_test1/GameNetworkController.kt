package fr.eurecom.vsnake_test1

interface GameNetworkController {
    fun onRemoteInput(input: String)
    fun onGameState(state: String)

    fun onGameOver(
        loser: GameView.GameOverResult,
        hostScore: Int,
        clientScore: Int,
        time: Long
    )
}
