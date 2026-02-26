package com.whirlpool.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whirlpool.app.ui.WhirlpoolScreen
import com.whirlpool.app.ui.WhirlpoolViewModel
import com.whirlpool.app.theme.WhirlpoolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: WhirlpoolViewModel = viewModel(
                factory = WhirlpoolViewModel.factory(applicationContext),
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            val darkModeEnabled = state.settings.theme.equals("Dark", ignoreCase = true)

            WhirlpoolTheme(darkTheme = darkModeEnabled) {
                WhirlpoolScreen(
                    viewModel = vm,
                    darkModeEnabled = darkModeEnabled,
                    onDarkModeToggle = { },
                )
            }
        }
    }
}
