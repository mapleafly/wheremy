package com.wheremy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.wheremy.app.ui.WheremyRoot
import com.wheremy.app.ui.home.HomeViewModel
import com.wheremy.app.ui.theme.WheremyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WheremyTheme {
                WheremyRoot(greeting = homeViewModel.greeting)
            }
        }
    }
}
