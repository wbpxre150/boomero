package com.wbpxre150.boomero.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.wbpxre150.boomero.model.GameState
import com.wbpxre150.boomero.model.DartType
import com.wbpxre150.boomero.model.Dart
import com.wbpxre150.boomero.model.Category
import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class GameViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val gson = Gson()
    private val PREFS_NAME = "boomero_game_state"
    private val KEY_GAME_STATE = "saved_game_state"
    private var skipCircleCheck = false

    // Initialize _gameState with loaded state or default
    private val _gameState = MutableStateFlow(loadInitialState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    init {
        println("Debug: GameViewModel initialized")
    }

    private fun loadInitialState(): GameState {
        println("Debug: Loading initial game state")
        return try {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val gameStateJson = sharedPrefs.getString(KEY_GAME_STATE, null)
            if (gameStateJson != null) {
                gson.fromJson(gameStateJson, GameState::class.java).also {
                    println("Debug: Game state loaded successfully")
                }
            } else {
                println("Debug: No saved game state found, using default")
                GameState()
            }
        } catch (e: Exception) {
            println("Debug: Error loading game state: ${e.message}")
            println("Debug: Using default game state")
            GameState()
        }
    }

    private fun saveGameState() {
        println("Debug: Saving game state")
        try {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val gameStateJson = gson.toJson(_gameState.value)
            sharedPrefs.edit().putString(KEY_GAME_STATE, gameStateJson).apply()
            println("Debug: Game state saved successfully")
        } catch (e: Exception) {
            println("Debug: Error saving game state: ${e.message}")
        }
    }

    sealed interface UiDialogState {
        data class DoubleChoice(
            val number: Int,
            val currentDart: Int,
            val scoringPlayer: Int,
            val needsFinalize: Boolean,
            val currentDarts: List<Dart?> // Add current darts to state
        ) : UiDialogState

        data class TripleChoice(
            val number: Int,
            val currentDart: Int,
            val scoringPlayer: Int,
            val needsFinalize: Boolean,
            val currentDarts: List<Dart?> // Add current darts to state
        ) : UiDialogState

        data class CircleChoice(
            val points: Int,
            val scoringPlayer: Int,
            val darts: List<Dart?>
        ) : UiDialogState

        data class TurnSummary(
            val darts: List<Dart?>,
            val pointsScored: Int,
            val currentPlayer: Int
        ) : UiDialogState

        object None : UiDialogState
    }

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message

    private val _uiDialogState = MutableStateFlow<UiDialogState>(UiDialogState.None)
    val uiDialogState: StateFlow<UiDialogState> = _uiDialogState.asStateFlow()

    fun handleDartThrow(type: DartType, number: Int) {
        viewModelScope.launch {
            println("Debug:handleDartThrow - Start - Current Player: ${_gameState.value.currentPlayer}")
            val state = _gameState.value
            val needsFinalize = state.currentDart == 2 // Check if this will be the last dart

            when (type) {
                DartType.SINGLE -> handleSingleHit(number)
                DartType.DOUBLE -> {
                    handleDoubleHit(number, needsFinalize)
                    // Return early only if showing a choice dialog
                    if (_uiDialogState.value is UiDialogState.DoubleChoice) {
                        return@launch
                    }
                }
                DartType.TRIPLE -> {
                    handleTripleHit(number, needsFinalize)
                    // Return early only if showing a choice dialog
                    if (_uiDialogState.value is UiDialogState.TripleChoice) {
                        return@launch
                    }
                }
                DartType.BULLSEYE -> handleBullseyeHit(number)
                DartType.MISS -> handleMiss()
            }

            println("Debug:handleDartThrow - After hit handling - Current Player: ${_gameState.value.currentPlayer}")
            // Only handle turn completion if we're not showing a choice dialog
            if (_uiDialogState.value == UiDialogState.None && state.currentDart == 2) {
                handleTurnCompletion()
            }
            println("Debug:handleDartThrow - End - Current Player: ${_gameState.value.currentPlayer}")
        }
    }

    private fun handleSingleHit(number: Int) {
        val state = _gameState.value
        if (!isValidNumber(number)) {
            viewModelScope.launch { _message.emit("Invalid number. Must be between 1 and 20.") }
            return
        }

        val column = if (state.currentPlayer == 1) 0 else 2
        val otherColumn = if (state.currentPlayer == 1) 2 else 0
        val newDart = Dart(DartType.SINGLE, number)

        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()
        newMatrix[number - 1][column]++

        // Calculate scoring
        var newPointsThisTurn = state.pointsThisTurn
        val hitsAfterThrow = newMatrix[number - 1][column]
        val otherPlayerHits = newMatrix[number - 1][otherColumn]

        // Only score points if number >= 10 AND it's the 4th or greater hit AND other player hasn't reached 3 hits
        if (number >= 10 && hitsAfterThrow >= 4 && otherPlayerHits < 3) {
            newPointsThisTurn += number
            viewModelScope.launch {
                _message.emit("Scored $number points")
            }
        }

        _gameState.value = state
            .updateCurrentTurnDart(state.currentDart, newDart)
            .copy(
                matrix = newMatrix,
                pointsThisTurn = newPointsThisTurn,
                currentDart = state.currentDart + 1
            )
    }

    private fun handleDoubleUnderTen(number: Int, currentDart: Int) {
        val state = _gameState.value
        val column = if (state.currentPlayer == 1) 0 else 2
        val otherColumn = if (state.currentPlayer == 1) 2 else 0

        // Create new dart with category scoring
        val dartWithCategory = Dart(
            type = DartType.DOUBLE,
            number = number,
            scoredAsCategory = true
        )

        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()

        // Get current hits in doubles category
        val currentCategoryHits = newMatrix[Category.DOUBLE - 1][column]
        val otherPlayerCategoryHits = newMatrix[Category.DOUBLE - 1][otherColumn]

        // Add hit to doubles category
        newMatrix[Category.DOUBLE - 1][column]++

        // Calculate points if applicable
        var newPointsThisTurn = state.pointsThisTurn
        if (currentCategoryHits >= 3 && otherPlayerCategoryHits < 3) {
            newPointsThisTurn += number * 2
        }

        // Update state
        _gameState.value = state.copy(
            matrix = newMatrix,
            pointsThisTurn = newPointsThisTurn
        ).updateCurrentTurnDart(currentDart, dartWithCategory)
    }

    private fun handleDoubleEliminated(number: Int, currentDart: Int) {
        val state = _gameState.value
        val column = if (state.currentPlayer == 1) 0 else 2
        val otherColumn = if (state.currentPlayer == 1) 2 else 0

        // Create dart without category scoring
        val dartWithoutCategory = Dart(
            type = DartType.DOUBLE,
            number = number,
            scoredAsCategory = false
        )

        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()
        var newPointsThisTurn = state.pointsThisTurn

        val startingHits = newMatrix[number - 1][column]
        val otherPlayerHits = newMatrix[number - 1][otherColumn]

        if (number >= 10 && otherPlayerHits < 3) {
            // Calculate points for each new hit
            for (i in 1..2) {
                val hitNumber = startingHits + i
                if (hitNumber >= 4) {
                    newPointsThisTurn += number
                }
            }
        }

        // Add the hits after calculating points
        newMatrix[number - 1][column] += 2

        // Update state
        _gameState.value = state.copy(
            matrix = newMatrix,
            pointsThisTurn = newPointsThisTurn
        ).updateCurrentTurnDart(currentDart, dartWithoutCategory)
    }

    private fun handleDoubleHit(number: Int, needsFinalize: Boolean) {
        println("Debug:handleDoubleHit - Start - Number: $number")
        val state = _gameState.value
        if (!isValidNumber(number)) {
            viewModelScope.launch { _message.emit("Invalid number. Must be between 1 and 20.") }
            return
        }

        val column = if (state.currentPlayer == 1) 0 else 2
        val otherColumn = if (state.currentPlayer == 1) 2 else 0
        println("Debug:handleDoubleHit - Column: $column, Other Column: $otherColumn")
        println("Debug:handleDoubleHit - Needs finalize: $needsFinalize")

        // Create the dart first
        val newDart = Dart(type = DartType.DOUBLE, number = number)

        // Update current turn darts
        _gameState.value = state.updateCurrentTurnDart(state.currentDart, newDart)

        if (number < 10) {
            // Automatically handle numbers under 10 as before...
            handleDoubleUnderTen(number, state.currentDart)
        } else if (isCategoryEliminated(Category.DOUBLE - 1)) {
            // Handle eliminated category as before...
            handleDoubleEliminated(number, state.currentDart)
        } else {
            // Show dialog for choice and RETURN immediately
            viewModelScope.launch {
                _uiDialogState.emit(UiDialogState.DoubleChoice(
                    number = number,
                    currentDart = state.currentDart,
                    scoringPlayer = state.currentPlayer,
                    needsFinalize = needsFinalize,
                    currentDarts = _gameState.value.currentTurnDarts
                ))
            }
            // Important: Return here to prevent further processing
            return
        }

        // Only increment the dart count for automatic processing
        _gameState.value = _gameState.value.copy(
            currentDart = _gameState.value.currentDart + 1
        )
    }

    private fun handleTripleUnderTen(number: Int, currentDart: Int) {
        val state = _gameState.value
        val column = if (state.currentPlayer == 1) 0 else 2
        val otherColumn = if (state.currentPlayer == 1) 2 else 0

        // Create new dart with category scoring
        val dartWithCategory = Dart(
            type = DartType.TRIPLE,
            number = number,
            scoredAsCategory = true
        )

        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()

        // Get current hits in triples category
        val currentCategoryHits = newMatrix[Category.TRIPLE - 1][column]
        val otherPlayerCategoryHits = newMatrix[Category.TRIPLE - 1][otherColumn]

        // Add hit to triples category
        newMatrix[Category.TRIPLE - 1][column]++

        // Calculate points if applicable
        var newPointsThisTurn = state.pointsThisTurn
        if (currentCategoryHits >= 3 && otherPlayerCategoryHits < 3) {
            newPointsThisTurn += number * 3  // Triple value for scoring
        }

        // Update state
        _gameState.value = state.copy(
            matrix = newMatrix,
            pointsThisTurn = newPointsThisTurn
        ).updateCurrentTurnDart(currentDart, dartWithCategory)
    }

    private fun handleTripleEliminated(number: Int, currentDart: Int) {
        val state = _gameState.value
        val column = if (state.currentPlayer == 1) 0 else 2
        val otherColumn = if (state.currentPlayer == 1) 2 else 0

        // Create dart without category scoring
        val dartWithoutCategory = Dart(
            type = DartType.TRIPLE,
            number = number,
            scoredAsCategory = false
        )

        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()
        var newPointsThisTurn = state.pointsThisTurn

        val startingHits = newMatrix[number - 1][column]
        val otherPlayerHits = newMatrix[number - 1][otherColumn]

        println("Debug:handleTripleEliminated - Starting hits: $startingHits")
        println("Debug:handleTripleEliminated - Other player hits: $otherPlayerHits")

        if (number >= 10 && otherPlayerHits < 3) {
            // Calculate points for each new hit
            for (i in 1..3) {  // Three hits for triple
                val hitNumber = startingHits + i
                println("Debug:handleTripleEliminated - Checking hit number: $hitNumber")
                if (hitNumber >= 4) {
                    newPointsThisTurn += number
                    println("Debug:handleTripleEliminated - Added $number points for hit $hitNumber")
                }
            }
        }

        // Add the hits after calculating points
        newMatrix[number - 1][column] += 3

        // Update state
        _gameState.value = state.copy(
            matrix = newMatrix,
            pointsThisTurn = newPointsThisTurn
        ).updateCurrentTurnDart(currentDart, dartWithoutCategory)
    }

    private fun handleTripleHit(number: Int, needsFinalize: Boolean) {
        println("Debug:handleTripleHit - Start - Number: $number")
        val state = _gameState.value
        if (!isValidNumber(number)) {
            viewModelScope.launch { _message.emit("Invalid number. Must be between 1 and 20.") }
            return
        }

        val column = if (state.currentPlayer == 1) 0 else 2
        val otherColumn = if (state.currentPlayer == 1) 2 else 0
        println("Debug:handleTripleHit - Column: $column, Other Column: $otherColumn")
        println("Debug:handleTripleHit - Needs finalize: $needsFinalize")

        // Create the dart first
        val newDart = Dart(type = DartType.TRIPLE, number = number)

        // Update current turn darts
        _gameState.value = state.updateCurrentTurnDart(state.currentDart, newDart)

        if (number < 10) {
            // Automatically handle numbers under 10
            handleTripleUnderTen(number, state.currentDart)
        } else if (isCategoryEliminated(Category.TRIPLE - 1)) {
            // Handle eliminated category
            handleTripleEliminated(number, state.currentDart)
        } else {
            // Show dialog for choice and RETURN immediately
            viewModelScope.launch {
                _uiDialogState.emit(UiDialogState.TripleChoice(
                    number = number,
                    currentDart = state.currentDart,
                    scoringPlayer = state.currentPlayer,
                    needsFinalize = needsFinalize,
                    currentDarts = _gameState.value.currentTurnDarts
                ))
            }
            // Important: Return here to prevent further processing
            return
        }

        // Only increment the dart count for automatic processing
        _gameState.value = _gameState.value.copy(
            currentDart = _gameState.value.currentDart + 1
        )
    }

    private fun handleBullseyeHit(number: Int) {
        val state = _gameState.value
        if (number != 1 && number != 2) {
            viewModelScope.launch {
                _message.emit("Invalid bullseye type. Use 1 for single (25) or 2 for double (50).")
            }
            return
        }

        val column = if (state.currentPlayer == 1) 0 else 2
        val otherColumn = if (state.currentPlayer == 1) 2 else 0
        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()
        val newDart = Dart(type = DartType.BULLSEYE, number = number)
        var newPointsThisTurn = state.pointsThisTurn

        if (number == 2) {  // Double bullseye (50)
            repeat(2) {
                val hitsBeforeAdd = newMatrix[Category.BULLSEYE - 1][column]
                newMatrix[Category.BULLSEYE - 1][column]++

                // Score 25 points per hit if it's the 4th or greater hit and other player hasn't reached 3
                if (hitsBeforeAdd >= 3 && newMatrix[Category.BULLSEYE - 1][otherColumn] < 3) {
                    newPointsThisTurn += 25
                }
            }
        } else {  // Single bullseye (25)
            val hitsBeforeAdd = newMatrix[Category.BULLSEYE - 1][column]
            newMatrix[Category.BULLSEYE - 1][column]++

            // Score 25 points if it's the 4th or greater hit and other player hasn't reached 3
            if (hitsBeforeAdd >= 3 && newMatrix[Category.BULLSEYE - 1][otherColumn] < 3) {
                newPointsThisTurn += 25
            }
        }

        _gameState.value = state
            .updateCurrentTurnDart(state.currentDart, newDart)
            .copy(
                matrix = newMatrix,
                pointsThisTurn = newPointsThisTurn,
                currentDart = state.currentDart + 1
            )

        viewModelScope.launch {
            _message.emit("Added ${if (number == 2) "double" else "single"} bullseye")
        }
    }

    fun keepAsNumbers() {
        println("Debug:keepAsNumbers - Starting to reprocess darts")
        val currentState = _gameState.value
        val column = if (currentState.currentPlayer == 1) 0 else 2
        val otherColumn = if (currentState.currentPlayer == 1) 2 else 0

        // Create new matrix starting from current state
        val newMatrix = currentState.matrix.map { it.copyOf() }.toTypedArray()
        var newPointsThisTurn = 0

        // Get first dart's number
        val number = currentState.currentTurnDarts.firstOrNull()?.number ?: return
        println("Debug:keepAsNumbers - Starting hits in matrix: ${newMatrix[number - 1][column]}")

        // First remove the hits from all darts in this turn
        currentState.currentTurnDarts.forEach { dart ->
            if (dart?.valid == true) {
                when (dart.type) {
                    DartType.SINGLE -> {
                        newMatrix[number - 1][column]--
                        println("Debug:keepAsNumbers - Removed single, hits now: ${newMatrix[number - 1][column]}")
                    }
                    DartType.DOUBLE -> {
                        newMatrix[number - 1][column] -= 2
                        println("Debug:keepAsNumbers - Removed double, hits now: ${newMatrix[number - 1][column]}")
                    }
                    DartType.TRIPLE -> {
                        newMatrix[number - 1][column] -= 3
                        println("Debug:keepAsNumbers - Removed triple, hits now: ${newMatrix[number - 1][column]}")
                    }
                    else -> {
                        println("Debug:keepAsNumbers - Skipping removal for ${dart.type}")
                    }
                }
            }
        }

        println("Debug:keepAsNumbers - After removing turn's darts, hits: ${newMatrix[number - 1][column]}")

        // Now reprocess all darts in order
        currentState.currentTurnDarts.forEach { dart ->
            println("Debug:keepAsNumbers - Processing dart: $dart")

            if (dart?.valid == true) {
                val startingHits = newMatrix[number - 1][column]
                val otherPlayerHits = newMatrix[number - 1][otherColumn]

                when (dart.type) {
                    DartType.SINGLE -> {
                        newMatrix[number - 1][column]++

                        // Score points if it's the 4th or greater hit AND other player hasn't reached 3 hits
                        if (number >= 10 && newMatrix[number - 1][column] >= 4 && otherPlayerHits < 3) {
                            newPointsThisTurn += number
                        }

                        println("Debug:keepAsNumbers - Added single, hits now: ${newMatrix[number - 1][column]}")
                    }
                    DartType.DOUBLE -> {
                        // Add two hits
                        newMatrix[number - 1][column] += 2

                        // Calculate points for hits that cross the threshold
                        if (number >= 10 && otherPlayerHits < 3) {
                            for (i in 1..2) {
                                val hitNumber = startingHits + i
                                if (hitNumber >= 4) {
                                    newPointsThisTurn += number
                                }
                            }
                        }

                        println("Debug:keepAsNumbers - Added double, hits now: ${newMatrix[number - 1][column]}")
                    }
                    DartType.TRIPLE -> {
                        // Add three hits
                        newMatrix[number - 1][column] += 3

                        // Calculate points for hits that cross the threshold
                        if (number >= 10 && otherPlayerHits < 3) {
                            for (i in 1..3) {
                                val hitNumber = startingHits + i
                                if (hitNumber >= 4) {
                                    newPointsThisTurn += number
                                }
                            }
                        }

                        println("Debug:keepAsNumbers - Added triple, hits now: ${newMatrix[number - 1][column]}")
                    }
                    else -> {
                        println("Debug:keepAsNumbers - Skipping ${dart.type}")
                    }
                }
            }
        }

        println("Debug:keepAsNumbers - Final points calculated: $newPointsThisTurn")

        // Update the state with our new matrix and points
        _gameState.value = currentState.copy(
            matrix = newMatrix,
            pointsThisTurn = newPointsThisTurn
        )

        // Show turn summary
        viewModelScope.launch {
            _uiDialogState.emit(UiDialogState.TurnSummary(
                darts = currentState.currentTurnDarts,
                pointsScored = newPointsThisTurn,
                currentPlayer = currentState.currentPlayer
            ))
        }
    }

    private fun finalizeTurn() {
        println("Debug:finalizeTurn - Start")
        val state = _gameState.value
        println("Debug:finalizeTurn - Current points this turn: ${state.pointsThisTurn}")
        println("Debug:finalizeTurn - Current player: ${state.currentPlayer}")

        if (!skipCircleCheck && canScoreCircle()) {
            println("Debug:finalizeTurn - Can score circle, showing dialog")
            handleCircleScoring()
            return
        }

        // Show turn summary dialog before actually finalizing
        viewModelScope.launch {
            _uiDialogState.emit(UiDialogState.TurnSummary(
                darts = state.currentTurnDarts,
                pointsScored = state.pointsThisTurn,
                currentPlayer = state.currentPlayer
            ))
        }
    }

    fun confirmTurn() {
        val state = _gameState.value

        val newPlayer1Score = if (state.currentPlayer == 1) state.player1Score + state.pointsThisTurn else state.player1Score
        val newPlayer2Score = if (state.currentPlayer == 2) state.player2Score + state.pointsThisTurn else state.player2Score

        _gameState.value = state.copy(
            player1Score = newPlayer1Score,
            player2Score = newPlayer2Score,
            currentPlayer = if (state.currentPlayer == 1) 2 else 1,
            currentDart = 0,
            pointsThisTurn = 0,
            currentTurnDarts = List(3) { null }
        )

        _uiDialogState.value = UiDialogState.None

        // Add check for game end
        checkGameEnd()
        saveGameState()
    }

    fun resetTurn() {
        println("Debug:resetTurn - Starting turn reset")
        val state = _gameState.value
        val currentPlayer = state.currentPlayer
        val column = if (currentPlayer == 1) 0 else 2

        // Create a new matrix to track changes
        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()

        // First check if all darts are scored as circle
        val allDartsCircle = state.currentTurnDarts.all { dart ->
            dart?.scoredAsCircle == true
        }

        if (allDartsCircle) {
            // If it's a circle turn, just remove the circle hit
            println("Debug:resetTurn - Removing circle hit from circle turn")
            val currentCircleHits = newMatrix[Category.CIRCLE - 1][column]
            newMatrix[Category.CIRCLE - 1][column] = (currentCircleHits - 1).coerceAtLeast(0)
        } else {
            // Process each dart based on its type and scoring method
            state.currentTurnDarts.forEach { dart ->
                if (dart?.valid == true) {
                    when (dart.type) {
                        DartType.SINGLE -> {
                            if (dart.number in 1..20) {
                                println("Debug:resetTurn - Removing single ${dart.number}")
                                val currentHits = newMatrix[dart.number - 1][column]
                                newMatrix[dart.number - 1][column] = (currentHits - 1).coerceAtLeast(0)
                            }
                        }
                        DartType.DOUBLE -> {
                            if (dart.scoredAsCategory) {
                                println("Debug:resetTurn - Removing double from category")
                                val currentHits = newMatrix[Category.DOUBLE - 1][column]
                                newMatrix[Category.DOUBLE - 1][column] = (currentHits - 1).coerceAtLeast(0)
                            } else if (dart.number in 1..20) {
                                println("Debug:resetTurn - Removing double ${dart.number}")
                                val currentHits = newMatrix[dart.number - 1][column]
                                newMatrix[dart.number - 1][column] = (currentHits - 2).coerceAtLeast(0)
                            }
                        }
                        DartType.TRIPLE -> {
                            if (dart.scoredAsCategory) {
                                println("Debug:resetTurn - Removing triple from category")
                                val currentHits = newMatrix[Category.TRIPLE - 1][column]
                                newMatrix[Category.TRIPLE - 1][column] = (currentHits - 1).coerceAtLeast(0)
                            } else if (dart.number in 1..20) {
                                println("Debug:resetTurn - Removing triple ${dart.number}")
                                val currentHits = newMatrix[dart.number - 1][column]
                                newMatrix[dart.number - 1][column] = (currentHits - 3).coerceAtLeast(0)
                            }
                        }
                        DartType.BULLSEYE -> {
                            val currentHits = newMatrix[Category.BULLSEYE - 1][column]
                            val hitsToRemove = if (dart.number == 2) 2 else 1
                            println("Debug:resetTurn - Removing ${if (dart.number == 2) "double" else "single"} bullseye")
                            newMatrix[Category.BULLSEYE - 1][column] = (currentHits - hitsToRemove).coerceAtLeast(0)
                        }
                        DartType.MISS -> {
                            println("Debug:resetTurn - Skipping miss")
                        }
                    }
                }
            }
        }

        // Reset the game state with the new matrix
        _gameState.value = state.copy(
            matrix = newMatrix,
            currentDart = 0,
            pointsThisTurn = 0,
            currentTurnDarts = List(3) { null }
        )

        // Clear the dialog
        _uiDialogState.value = UiDialogState.None

        // Save the updated state
        saveGameState()

        println("Debug:resetTurn - Turn reset complete")

        viewModelScope.launch {
            _message.emit("Turn reset successfully")
        }
    }

    private fun checkGameEnd() {
        val state = _gameState.value
        if (playerHasWon(0) || playerHasWon(2) ||
            (playerHasCompletedAll(0) && playerHasCompletedAll(2))) {
            _gameState.value = state.copy(isGameOver = true)
        }
    }

    // Helper functions
    private fun isValidNumber(number: Int) = number in 1..20

    private fun canScoreCircle(): Boolean {
        val state = _gameState.value
        if (state.currentDart != 3) return false

        val darts = state.currentTurnDarts

        // Debug logging to help diagnose circle check
        println("Debug:canScoreCircle - Checking darts: ${darts.joinToString()}")
        println("Debug:canScoreCircle - All valid: ${darts.all { it?.valid == true }}")
        println("Debug:canScoreCircle - Numbers: ${darts.mapNotNull { it?.number }}")

        return darts.all { it?.valid == true } &&
                darts.filterNotNull().map { it.number }.distinct().size == 1 &&  // All darts have same number
                darts.size == 3 &&  // Ensure we have exactly 3 darts
                darts.all { it != null }  // Ensure no null darts
    }

    private fun playerHasCompletedAll(column: Int): Boolean {
        val state = _gameState.value
        return (9 until state.matrix.size).all { row ->
            playerIsThree(column, row) >= 3
        }
    }

    private fun playerIsThree(column: Int, row: Int): Int {
        val state = _gameState.value
        if (column != 0 && column != 2) return -1
        if (row < 0 || row >= state.matrix.size) return -1
        return if (state.matrix[row][column] > 3) 3 else state.matrix[row][column]
    }

    private fun playerHasWon(column: Int): Boolean {
        val state = _gameState.value
        val otherColumn = if (column == 0) 2 else 0
        val playerScore = if (column == 0) state.player1Score else state.player2Score
        val otherScore = if (column == 0) state.player2Score else state.player1Score

        // Check if player has completed all categories
        if (!playerHasCompletedAll(column)) {
            return false
        }

        // Check if other player has scoring opportunities left
        val otherPlayerCanScore = (9 until state.matrix.size).any { row ->
            playerIsThree(otherColumn, row) >= 3 && playerIsThree(column, row) < 3
        }

        return playerHasCompletedAll(column) &&
               ((playerScore > otherScore && !otherPlayerCanScore) ||
                (playerScore > otherScore && playerHasCompletedAll(otherColumn)))
    }

    private fun handleCircleScoring() {
        println("Debug:handleCircleScoring - Attempting to show circle choice dialog")
        val state = _gameState.value
        val circlePoints = calculateCirclePoints()
        val currentPlayer = state.currentPlayer
        val currentDarts = state.currentTurnDarts.toList()

        viewModelScope.launch {
            _uiDialogState.emit(UiDialogState.CircleChoice(
                points = circlePoints,
                scoringPlayer = currentPlayer,
                darts = currentDarts
            ))
        }
        println("Debug:handleCircleScoring - Circle choice dialog emitted")
    }

    fun autoScoreCircle(circlePoints: Int, scoringPlayer: Int, darts: List<Dart?>) {
        println("autoScoreCircle - Start - Using Player: $scoringPlayer")
        val state = _gameState.value
        val column = if (scoringPlayer == 1) 0 else 2
        val otherColumn = if (scoringPlayer == 1) 2 else 0

        // Create new darts marked as circle category with special flag
        val updatedDarts = darts.map { dart ->
            dart?.copy(
                scoredAsCategory = true,
                scoredAsCircle = true  // New flag specifically for circle scoring
            )
        }

        // First remove any existing hits from the numbers
        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()
        darts.forEach { dart ->
            if (dart?.valid == true) {
                when (dart.type) {
                    DartType.SINGLE -> {
                        if (dart.number > 0 && dart.number <= 20) {
                            println("Debug: Removed 1 hit for single ${dart.number}")
                            val currentHits = newMatrix[dart.number - 1][column]
                            newMatrix[dart.number - 1][column] = (currentHits - 1).coerceAtLeast(0)
                        }
                    }
                    DartType.DOUBLE -> {
                        if (!dart.scoredAsCategory) {
                            println("Debug: Removed 2 hits for double ${dart.number}")
                            val currentHits = newMatrix[dart.number - 1][column]
                            newMatrix[dart.number - 1][column] = (currentHits - 2).coerceAtLeast(0)
                        } else {
                            println("Debug: Removed 1 hit from double category")
                            val currentHits = newMatrix[Category.DOUBLE - 1][column]
                            newMatrix[Category.DOUBLE - 1][column] = (currentHits - 1).coerceAtLeast(0)
                        }
                    }
                    DartType.TRIPLE -> {
                        if (!dart.scoredAsCategory) {
                            println("Debug: Removed 3 hits for triple ${dart.number}")
                            val currentHits = newMatrix[dart.number - 1][column]
                            newMatrix[dart.number - 1][column] = (currentHits - 3).coerceAtLeast(0)
                        } else {
                            println("Debug: Removed 1 hit from triple category")
                            val currentHits = newMatrix[Category.TRIPLE - 1][column]
                            newMatrix[Category.TRIPLE - 1][column] = (currentHits - 1).coerceAtLeast(0)
                        }
                    }
                    else -> {} // Handle other cases if needed
                }
            }
        }

        // Get current circle hits before adding new hit
        val currentCircleHits = newMatrix[Category.CIRCLE - 1][column]
        val otherPlayerCircleHits = newMatrix[Category.CIRCLE - 1][otherColumn]

        // Add the circle hit
        newMatrix[Category.CIRCLE - 1][column]++
        println("Debug: Added circle to column $column")

        // Calculate points if this is the 4th or higher circle AND other player has less than 3
        var pointsScored = 0
        if (currentCircleHits >= 3 && otherPlayerCircleHits < 3) {
            pointsScored = circlePoints
        }

        // Update the state with new matrix, darts marked as circle, and points
        _gameState.value = state.copy(
            matrix = newMatrix,
            pointsThisTurn = pointsScored,
            currentTurnDarts = updatedDarts  // Use the updated darts marked as circle
        )

        // Show turn summary dialog
        viewModelScope.launch {
            _uiDialogState.emit(UiDialogState.TurnSummary(
                darts = updatedDarts,
                pointsScored = pointsScored,
                currentPlayer = scoringPlayer
            ))
        }
    }

    private fun calculateCirclePoints(): Int {
        val state = _gameState.value
        var total = 0

        // Special handling for bullseyes
        if (state.currentTurnDarts[0]?.type == DartType.BULLSEYE) {
            state.currentTurnDarts.forEach { dart ->
                total += if (dart?.number == 2) 50 else 25
            }
            return total
        }

        // Regular number handling
        val baseNumber = state.currentTurnDarts[0]?.number ?: return 0
        state.currentTurnDarts.forEach { dart ->
            when (dart?.type) {
                DartType.SINGLE -> total += baseNumber
                DartType.DOUBLE -> total += baseNumber * 2
                DartType.TRIPLE -> total += baseNumber * 3
                else -> { /* No action needed */ }
            }
        }
        return total
    }

    private fun isCategoryEliminated(row: Int): Boolean {
        return playerIsThree(0, row) >= 3 && playerIsThree(2, row) >= 3
    }

    fun handleMiss() {
        val state = _gameState.value
        val newDart = Dart(
            type = DartType.MISS,
            number = 0,
            valid = false
        )

        _gameState.value = state
            .updateCurrentTurnDart(state.currentDart, newDart)
            .copy(
                currentDart = state.currentDart + 1
            )

        viewModelScope.launch {
            _message.emit("Recorded miss (0 points)")
        }
    }

    sealed class DialogState {
        data class TripleChoice(val number: Int, val currentDart: Int) : DialogState()
        data class DoubleChoice(val number: Int, val currentDart: Int) : DialogState()
        data class CircleChoice(val points: Int) : DialogState()
        object None : DialogState()
    }

    private val _dialogState = MutableStateFlow<DialogState>(DialogState.None)
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    fun dismissDialog() {
        viewModelScope.launch {
            val currentState = _uiDialogState.value
            _uiDialogState.emit(UiDialogState.None)

            // Only handle turn completion if we're not finalizing a turn
            // (i.e., not coming from keepAsNumbers or autoScoreCircle)
            if (currentState !is UiDialogState.CircleChoice) {
                handleTurnCompletion()
            }
        }
    }

    fun getCategoryName(row: Int): String {
        return when {
            row < 20 -> (row + 1).toString()
            row == Category.DOUBLE - 1 -> "DBL"
            row == Category.TRIPLE - 1 -> "TPL"
            row == Category.BULLSEYE - 1 -> "BULL"
            row == Category.CIRCLE - 1 -> "CIRC"
            else -> "???"
        }
    }

    fun restartGame() {
        _gameState.value = GameState()
        saveGameState()
    }

    fun handleDoubleChoice(number: Int, currentDart: Int, useDoubleCategory: Boolean) {
        val state = _gameState.value
        val dialogState = _uiDialogState.value as? UiDialogState.DoubleChoice ?: return
        val scoringPlayer = dialogState.scoringPlayer
        val column = if (scoringPlayer == 1) 0 else 2
        val otherColumn = if (scoringPlayer == 1) 2 else 0
        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()
        val newCurrentTurnDarts = dialogState.currentDarts.toMutableList()

        println("Debug:handleDoubleChoice - Processing choice for number: $number")
        println("Debug:handleDoubleChoice - Using double category: $useDoubleCategory")
        println("Debug:handleDoubleChoice - Current matrix hits for number: ${newMatrix[number - 1][column]}")

        val newDart = Dart(
            type = DartType.DOUBLE,
            number = number,
            scoredAsCategory = useDoubleCategory,
            valid = true
        )

        // Update the darts list
        newCurrentTurnDarts[currentDart] = newDart
        var newPointsThisTurn = state.pointsThisTurn

        if (useDoubleCategory) {
            // Get current hits in doubles category before adding new hit
            val currentCategoryHits = newMatrix[Category.DOUBLE - 1][column]
            val otherPlayerCategoryHits = newMatrix[Category.DOUBLE - 1][otherColumn]

            // Add hit to doubles category
            newMatrix[Category.DOUBLE - 1][column]++

            // Score points if it's the 4th or greater hit AND other player hasn't closed category
            if (currentCategoryHits >= 3 && otherPlayerCategoryHits < 3) {
                newPointsThisTurn += number * 2  // Score double value of the number
            }

            println("Debug:handleDoubleChoice - Added to doubles category")
        } else {
            // Scoring as individual numbers
            val startingHits = newMatrix[number - 1][column]
            val otherPlayerHits = newMatrix[number - 1][otherColumn]

            println("Debug:handleDoubleChoice - Scoring as numbers - Starting hits: $startingHits")
            println("Debug:handleDoubleChoice - Other player hits: $otherPlayerHits")

            // Add two hits to the number
            newMatrix[number - 1][column] += 2

            // Calculate points if applicable (number >= 10 and other player hasn't closed)
            if (number >= 10 && otherPlayerHits < 3) {
                // Check each new hit for scoring
                for (i in 1..2) {
                    val hitNumber = startingHits + i
                    if (hitNumber >= 4) {
                        newPointsThisTurn += number
                        println("Debug:handleDoubleChoice - Added $number points for hit $hitNumber")
                    }
                }
            }

            println("Debug:handleDoubleChoice - Final hits after scoring: ${newMatrix[number - 1][column]}")
        }

        // Update state with new dart and scoring
        _gameState.value = state.copy(
            matrix = newMatrix,
            currentTurnDarts = newCurrentTurnDarts,
            pointsThisTurn = newPointsThisTurn,
            currentPlayer = scoringPlayer,
            currentDart = currentDart + 1  // Increment the current dart count
        )

        // Dismiss the dialog
        _uiDialogState.value = UiDialogState.None

        // Check if this was the last dart and handle turn completion if needed
        if (currentDart + 1 >= 3) {
            handleTurnCompletion()
        }

        println("Debug:handleDoubleChoice - State updated, final hits: ${newMatrix[number - 1][column]}")
    }

    fun handleTripleChoice(number: Int, currentDart: Int, useTripleCategory: Boolean) {
        val state = _gameState.value
        val dialogState = _uiDialogState.value as? UiDialogState.TripleChoice ?: return
        val scoringPlayer = dialogState.scoringPlayer
        val column = if (scoringPlayer == 1) 0 else 2
        val otherColumn = if (scoringPlayer == 1) 2 else 0
        val newMatrix = state.matrix.map { it.copyOf() }.toTypedArray()
        val newCurrentTurnDarts = dialogState.currentDarts.toMutableList()

        println("Debug:handleTripleChoice - Processing choice for number: $number")
        println("Debug:handleTripleChoice - Using triple category: $useTripleCategory")
        println("Debug:handleTripleChoice - Current matrix hits for number: ${newMatrix[number - 1][column]}")

        val newDart = Dart(
            type = DartType.TRIPLE,
            number = number,
            scoredAsCategory = useTripleCategory,
            valid = true
        )

        // Update the darts list
        newCurrentTurnDarts[currentDart] = newDart
        var newPointsThisTurn = state.pointsThisTurn

        if (useTripleCategory) {
            // Get current hits in triples category before adding new hit
            val currentCategoryHits = newMatrix[Category.TRIPLE - 1][column]
            val otherPlayerCategoryHits = newMatrix[Category.TRIPLE - 1][otherColumn]

            // Add hit to triples category
            newMatrix[Category.TRIPLE - 1][column]++

            // Score points if it's the 4th or greater hit AND other player hasn't closed category
            if (currentCategoryHits >= 3 && otherPlayerCategoryHits < 3) {
                newPointsThisTurn += number * 3  // Score triple value of the number
            }

            println("Debug:handleTripleChoice - Added to triples category")
        } else {
            // Scoring as individual numbers
            val startingHits = newMatrix[number - 1][column]
            val otherPlayerHits = newMatrix[number - 1][otherColumn]

            println("Debug:handleTripleChoice - Scoring as numbers - Starting hits: $startingHits")
            println("Debug:handleTripleChoice - Other player hits: $otherPlayerHits")

            // Add three hits to the number
            newMatrix[number - 1][column] += 3

            // Calculate points if applicable (number >= 10 and other player hasn't closed)
            if (number >= 10 && otherPlayerHits < 3) {
                // Check each new hit for scoring
                for (i in 1..3) {
                    val hitNumber = startingHits + i
                    if (hitNumber >= 4) {
                        newPointsThisTurn += number
                        println("Debug:handleTripleChoice - Added $number points for hit $hitNumber")
                    }
                }
            }

            println("Debug:handleTripleChoice - Final hits after scoring: ${newMatrix[number - 1][column]}")
        }

        // Update state with new dart and scoring
        _gameState.value = state.copy(
            matrix = newMatrix,
            currentTurnDarts = newCurrentTurnDarts,
            pointsThisTurn = newPointsThisTurn,
            currentPlayer = scoringPlayer,
            currentDart = currentDart + 1  // Increment the current dart count
        )

        // Dismiss the dialog
        _uiDialogState.value = UiDialogState.None

        // Check if this was the last dart and handle turn completion if needed
        if (currentDart + 1 >= 3) {
            handleTurnCompletion()
        }

        println("Debug:handleTripleChoice - State updated, final hits: ${newMatrix[number - 1][column]}")
    }

    private fun handleTurnCompletion() {
        println("Debug:handleTurnCompletion - Start")
        val currentState = _gameState.value

        if (currentState.currentDart == 3) {
            if (!skipCircleCheck && canScoreCircle()) {
                // Check if all darts are under 10 and are singles
                val allDartsUnderTen = currentState.currentTurnDarts.all { dart ->
                    dart?.number?.let { it < 10 } == true && dart.type == DartType.SINGLE
                }

                if (allDartsUnderTen) {
                    println("Debug:handleTurnCompletion - Automatically scoring circle for numbers under 10")
                    // Calculate circle points
                    val circlePoints = calculateCirclePoints()
                    // Auto-score the circle
                    autoScoreCircle(
                        circlePoints,
                        currentState.currentPlayer,
                        currentState.currentTurnDarts
                    )
                } else {
                    println("Debug:handleTurnCompletion - Showing circle choice dialog")
                    handleCircleScoring()
                }
                return
            } else {
                // Show turn summary dialog
                viewModelScope.launch {
                    _uiDialogState.emit(UiDialogState.TurnSummary(
                        darts = currentState.currentTurnDarts,
                        pointsScored = currentState.pointsThisTurn,
                        currentPlayer = currentState.currentPlayer
                    ))
                }
            }
        }
    }

}