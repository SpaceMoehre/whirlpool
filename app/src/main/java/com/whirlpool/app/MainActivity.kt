package com.whirlpool.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whirlpool.app.ui.WhirlpoolScreen
import com.whirlpool.app.ui.WhirlpoolViewModel
import com.whirlpool.app.theme.WhirlpoolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhirlpoolTheme {
                val vm: WhirlpoolViewModel = viewModel(
                    factory = WhirlpoolViewModel.factory(applicationContext),
                )
                WhirlpoolScreen(viewModel = vm)
            }
        }
    }
}
