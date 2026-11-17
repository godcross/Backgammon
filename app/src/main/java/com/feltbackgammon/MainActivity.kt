package com.feltbackgammon

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.feltbackgammon.ui.DeepWood
import com.feltbackgammon.ui.FeltTheme
import com.feltbackgammon.ui.GameScreen
import com.feltbackgammon.ui.HomeScreen
import com.feltbackgammon.ui.NewGameScreen
import com.feltbackgammon.ui.SettingsScreen
import com.feltbackgammon.ui.StatsScreen

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            FeltTheme {
                BackHandler(enabled = vm.screen != Screen.HOME) { vm.goHome() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(DeepWood)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                ) {
                    Crossfade(targetState = vm.screen, animationSpec = tween(250), label = "screen") { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(vm)
                            Screen.NEW_GAME -> NewGameScreen(vm)
                            Screen.GAME -> GameScreen(vm)
                            Screen.STATS -> StatsScreen(vm)
                            Screen.SETTINGS -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
