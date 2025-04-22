package com.wbpxre150.boomero.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.wbpxre150.boomero.model.Dart
import com.wbpxre150.boomero.model.GameState
import com.wbpxre150.boomero.model.DartType
import com.wbpxre150.boomero.ui.theme.BrightGreen
import com.wbpxre150.boomero.viewmodel.GameViewModel
import com.wbpxre150.boomero.viewmodel.GameViewModel.UiDialogState
import kotlinx.coroutines.launch


// Helper function to format dart display
private fun formatDartDisplay(dart: Dart): String {
    return when {
        dart.type == DartType.MISS -> "Miss"
        dart.type == DartType.BULLSEYE ->
            if (dart.number == 2) "Double Bull (50)" else "Single Bull (25)"
        dart.scoredAsCategory -> "${dart.type} Category"
        else -> "${dart.type} ${dart.number}"
    }
}

@Composable
fun TurnSummaryDialog(
    darts: List<Dart?>,
    pointsScored: Int,
    currentPlayer: Int,
    onConfirm: () -> Unit,
    onReset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Do nothing, force user to choose */ },
        title = {
            Text(
                text = "Turn Summary",
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Player $currentPlayer's Turn",
                    style = MaterialTheme.typography.subtitle1,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Darts thrown:",
                    style = MaterialTheme.typography.subtitle1
                )
                darts.forEachIndexed { index, dart ->
                    dart?.let {
                        Text(
                            text = "${index + 1}. ${formatDartDisplay(it)}",
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Points scored: $pointsScored",
                    style = MaterialTheme.typography.subtitle1,
                    textAlign = TextAlign.Center
                )
            }
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = onReset,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colors.error
                    )
                ) {
                    Text("Reset Turn")
                }
                Button(onClick = onConfirm) {
                    Text("Submit Score")
                }
            }
        }
    )
}

@Composable
fun NewGameConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Start New Game?",
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "Do you want to start a new game?",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onDismiss) {
                    Text("No")
                }
                Button(onClick = onConfirm) {
                    Text("Yes")
                }
            }
        }
    )
}

@Composable
fun GameOverDialog(
    player1Score: Int,
    player2Score: Int,
    onDismiss: () -> Unit,
    onNewGame: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Game Over!",
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val winner = if (player1Score > player2Score) "Player 1" else "Player 2"
                Text(
                    text = "$winner wins!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body1
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Final Score:",
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Player 1: $player1Score",
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Player 2: $player2Score",
                    textAlign = TextAlign.Center
                )
            }
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = onNewGame) {
                    Text("Start New Game")
                }
            }
        }
    )
}

@Composable
fun CircleChoiceDialog(
    points: Int,
    onDismiss: () -> Unit,
    onChoice: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Score as Circle?",
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "You can score this as a circle for $points points if completed",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { onChoice(false) }) {  // Changed from onDismiss
                    Text("Keep as Numbers")
                }
                Button(onClick = { onChoice(true) }) {
                    Text("Score as Circle")
                }
            }
        }
    )
}

@Composable
fun DoubleHitChoiceDialog(
    number: Int,
    onDismiss: () -> Unit,
    onChoice: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Score Double $number",
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "How would you like to score this double hit?",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { onChoice(false) }) {
                    Text("As Two $number's")
                }
                Button(onClick = { onChoice(true) }) {
                    Text("As Double Category")
                }
            }
        }
    )
}

@Composable
fun TripleHitChoiceDialog(
    number: Int,
    onDismiss: () -> Unit,
    onChoice: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Score Triple $number",
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "How would you like to score this triple hit?",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { onChoice(false) }) {
                    Text("As Three $number's")
                }
                Button(onClick = { onChoice(true) }) {
                    Text("As Triple Category")
                }
            }
        }
    )
}

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val TAG = "GameScreen"

    LaunchedEffect(Unit) {
        Log.d(TAG, "GameScreen initially launched")
    }

    SideEffect {
        Log.d(TAG, "GameScreen recomposed")
    }

    val gameState = viewModel.gameState.collectAsState().value
    val dialogState = viewModel.uiDialogState.collectAsState().value
    val scope = rememberCoroutineScope()

    // Add state for new game confirmation dialog
    var showNewGameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Add Row for New Game button and existing header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // New Game button
            OutlinedButton(
                onClick = { showNewGameDialog = true },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("NG")
            }

            // Existing header wrapped in a Box to take remaining space
            Box(modifier = Modifier.weight(1f)) {
                GameHeader(gameState)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        ScoringMatrix(gameState, viewModel::getCategoryName)
        Spacer(modifier = Modifier.height(16.dp))
        DartInputSection(
            onDartThrow = { type, number ->
                scope.launch { viewModel.handleDartThrow(type, number) }
            }
        )
    }

    // Show new game confirmation dialog when needed
    if (showNewGameDialog) {
        NewGameConfirmationDialog(
            onDismiss = { showNewGameDialog = false },
            onConfirm = {
                viewModel.restartGame()
                showNewGameDialog = false
            }
        )
    }

    // Game over notification.
    if (gameState.isGameOver) {
        GameOverDialog(
            player1Score = gameState.player1Score,
            player2Score = gameState.player2Score,
            onDismiss = { /* Game can only continue with new game */ },
            onNewGame = {
                viewModel.restartGame()
            }
        )
    }

    // Existing dialog handling
    when (val currentDialog = dialogState) {
        is UiDialogState.CircleChoice -> {
            CircleChoiceDialog(
                points = currentDialog.points,
                onDismiss = { viewModel.dismissDialog() },
                onChoice = { useCircle ->
                    if (useCircle) {
                        viewModel.autoScoreCircle(
                            currentDialog.points,
                            currentDialog.scoringPlayer,
                            currentDialog.darts
                        )
                    } else {
                        viewModel.keepAsNumbers()
                        // Remove viewModel.dismissDialog() call since keepAsNumbers handles it
                    }
                    // The dialog state is now handled by the respective functions
                }
            )
        }
        is UiDialogState.DoubleChoice -> {
            DoubleHitChoiceDialog(
                number = currentDialog.number,
                onDismiss = { viewModel.dismissDialog() },
                onChoice = { useDoubleCategory ->
                    viewModel.handleDoubleChoice(
                        currentDialog.number,
                        currentDialog.currentDart,
                        useDoubleCategory
                    )
                    viewModel.dismissDialog()
                }
            )
        }
        is UiDialogState.TripleChoice -> {
            TripleHitChoiceDialog(
                number = currentDialog.number,
                onDismiss = { viewModel.dismissDialog() },
                onChoice = { useTripleCategory ->
                    viewModel.handleTripleChoice(
                        currentDialog.number,
                        currentDialog.currentDart,
                        useTripleCategory
                    )
                    viewModel.dismissDialog()
                }
            )
        }
        is UiDialogState.TurnSummary -> {
            TurnSummaryDialog(
                darts = currentDialog.darts,
                pointsScored = currentDialog.pointsScored,
                currentPlayer = currentDialog.currentPlayer,
                onConfirm = { viewModel.confirmTurn() },
                onReset = { viewModel.resetTurn() }
            )
        }
        UiDialogState.None -> {} // Empty block for None case
    }
}

@Composable
fun GameHeader(gameState: GameState) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "Player 1: ${gameState.player1Score}",
                style = MaterialTheme.typography.h5,
                color = if (gameState.currentPlayer == 1) BrightGreen else LocalContentColor.current
            )
            Text(
                text = "Player 2: ${gameState.player2Score}",
                style = MaterialTheme.typography.h5,
                color = if (gameState.currentPlayer == 2) BrightGreen else LocalContentColor.current
            )
        }
        Text(
            text = "Turns left: ${3 - gameState.currentDart}",
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ScoringMatrix(gameState: GameState, getCategoryName: (Int) -> String) {
    Surface(
        elevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        LazyColumn(
            modifier = Modifier.padding(8.dp)
        ) {
            items(gameState.matrix.indices.toList()) { row ->
                if (row >= 9) {  // Only show categories 10-20 and special categories
                    MatrixRow(
                        row = row,
                        player1Hits = gameState.matrix[row][0],
                        player2Hits = gameState.matrix[row][2],
                        categoryName = getCategoryName(row)
                    )
                }
            }
        }
    }
}

@Composable
fun MatrixRow(
    row: Int,
    player1Hits: Int,
    player2Hits: Int,
    categoryName: String
) {
    val isEliminated = player1Hits >= 3 && player2Hits >= 3

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "X".repeat((player1Hits.coerceAtMost(3))),
                color = when {
                    isEliminated -> MaterialTheme.colors.error
                    player1Hits >= 3 -> BrightGreen
                    else -> LocalContentColor.current
                }
            )
        }

        Text(
            text = categoryName,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = if (isEliminated) MaterialTheme.colors.error else LocalContentColor.current
        )

        Box(
            modifier = Modifier.width(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "X".repeat((player2Hits.coerceAtMost(3))),
                color = when {
                    isEliminated -> MaterialTheme.colors.error
                    player2Hits >= 3 -> BrightGreen
                    else -> LocalContentColor.current
                }
            )
        }
    }
}

@Composable
fun DartInputSection(
    onDartThrow: (DartType, Int) -> Unit
) {
    var selectedType by remember { mutableStateOf<DartType?>(null) }
    var selectedNumber by remember { mutableStateOf<Int?>(null) }

    Surface(
        elevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Dart type selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DartType.values().forEach { type ->
                    OutlinedButton(
                        onClick = {
                            selectedType = type
                            selectedNumber = null  // Reset number when type changes
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = if (selectedType == type)
                                MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Text(type.name.take(1))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Create a Box with weight to ensure the number selection doesn't push the button off screen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Number selection - show different options based on selected type
                when (selectedType) {
                    DartType.BULLSEYE -> {
                        // Show only 25 and 50 options for Bullseye
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            OutlinedButton(
                                onClick = { selectedNumber = 1 },  // 1 represents 25 (single bullseye)
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    backgroundColor = if (selectedNumber == 1)
                                        MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent
                                )
                            ) {
                                Text("25")
                            }
                            OutlinedButton(
                                onClick = { selectedNumber = 2 },  // 2 represents 50 (double bullseye)
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    backgroundColor = if (selectedNumber == 2)
                                        MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent
                                )
                            ) {
                                Text("50")
                            }
                        }
                    }
                    DartType.MISS -> {
                        // No number selection needed for misses
                        selectedNumber = 0
                    }
                    else -> {
                        // Show 1-20 grid for other dart types
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),  // Add minimal horizontal spacing
                            verticalArrangement = Arrangement.spacedBy(2.dp),    // Add minimal vertical spacing
                            contentPadding = PaddingValues(2.dp),
                            modifier = Modifier.fillMaxHeight()  // Fill available height
                        ) {
                            val numbers = (1..20).toList()
                            items(numbers) { number ->
                                OutlinedButton(
                                    onClick = { selectedNumber = number },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(0.dp),  // Remove padding around button
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),  // Reduce internal padding
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        backgroundColor = if (selectedNumber == number)
                                            MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent
                                    )
                                ) {
                                    Text(
                                        "$number",
                                        style = MaterialTheme.typography.body1.copy()  // Slightly smaller text
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Throw Dart button - now outside the Box and always visible
            Button(
                onClick = {
                    selectedType?.let { type ->
                        selectedNumber?.let { number ->
                            onDartThrow(type, number)
                            selectedType = null
                            selectedNumber = null
                        }
                    }
                },
                enabled = selectedType != null && (selectedNumber != null || selectedType == DartType.MISS),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text("Throw Dart")
            }
        }
    }
}