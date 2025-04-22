package com.wbpxre150.boomero.model

data class GameState(
    val player1Score: Int = 0,
    val player2Score: Int = 0,
    val currentPlayer: Int = 1,
    val currentDart: Int = 0,
    val pointsThisTurn: Int = 0,
    val isGameOver: Boolean = false,
    val matrix: Array<Array<Int>> = Array(24) { Array(3) { 0 } },
    val currentTurnDarts: List<Dart?> = List(3) { null }
) {
    // Calculate turns left based on current dart
    val turnsLeft: Int
        get() = 3 - currentDart

    fun updateCurrentTurnDart(index: Int, dart: Dart?): GameState {
        val newDarts = currentTurnDarts.toMutableList()
        newDarts[index] = dart
        return copy(currentTurnDarts = newDarts)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (player1Score != other.player1Score) return false
        if (player2Score != other.player2Score) return false
        if (currentPlayer != other.currentPlayer) return false
        if (currentDart != other.currentDart) return false
        if (pointsThisTurn != other.pointsThisTurn) return false
        if (isGameOver != other.isGameOver) return false
        if (!matrix.contentDeepEquals(other.matrix)) return false
        if (currentTurnDarts != other.currentTurnDarts) return false

        return true
    }

    override fun hashCode(): Int {
        var result = player1Score
        result = 31 * result + player2Score
        result = 31 * result + currentPlayer
        result = 31 * result + currentDart
        result = 31 * result + pointsThisTurn
        result = 31 * result + isGameOver.hashCode()
        result = 31 * result + matrix.contentDeepHashCode()
        result = 31 * result + currentTurnDarts.hashCode()
        return result
    }
}
