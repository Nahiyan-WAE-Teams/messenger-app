package com.nahiyan.miramessenger

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            LovelyTheme {
                val appViewModel: LovelyViewModel = viewModel(factory = LovelyViewModel.Factory(application))
                LovelyApp(appViewModel)
            }
        }
    }
}
