package com.wbpxre150.boomero

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import com.wbpxre150.boomero.ui.GameScreen
import com.wbpxre150.boomero.viewmodel.GameViewModel
import com.wbpxre150.boomero.ui.theme.BoomeroTheme

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting activity initialization")

        setContent {
            Log.d(TAG, "onCreate: Setting up composition")
            BoomeroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    GameScreen(viewModel)
                }
            }
        }

        Log.d(TAG, "onCreate: Activity initialization complete")
    }
}
