package com.whirlpool.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.whirlpool.app.ui.WhirlpoolScreen
import com.whirlpool.app.ui.WhirlpoolViewModel
import com.whirlpool.app.theme.WhirlpoolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        enableEdgeToEdge()
        setContent {
            var darkModeEnabled by rememberSaveable {
                mutableStateOf(settingsPrefs.getBoolean(KEY_DARK_MODE, true))
            }

            WhirlpoolTheme(darkTheme = darkModeEnabled) {
                val vm: WhirlpoolViewModel = viewModel(
                    factory = WhirlpoolViewModel.factory(applicationContext),
                )
                WhirlpoolScreen(
                    viewModel = vm,
                    darkModeEnabled = darkModeEnabled,
                    onDarkModeToggle = { enabled ->
                        darkModeEnabled = enabled
                        settingsPrefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
                    },
                )
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "whirlpool_settings"
        const val KEY_DARK_MODE = "dark_mode"
    }
}
