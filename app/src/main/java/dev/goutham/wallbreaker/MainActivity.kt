package dev.goutham.wallbreaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.goutham.wallbreaker.ui.HistoryScreen
import dev.goutham.wallbreaker.ui.HomeViewModel
import dev.goutham.wallbreaker.ui.SettingsScreen
import dev.goutham.wallbreaker.ui.theme.WallbreakerTheme

private enum class Screen { Home, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WallbreakerTheme {
                Surface(Modifier.fillMaxSize()) { Root() }
            }
        }
    }
}

@Composable
private fun Root() {
    val homeVm: HomeViewModel = viewModel()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(300)) { w -> dir * w / 6 } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(tween(300)) { w -> -dir * w / 6 } + fadeOut(tween(300)))
        },
        label = "nav",
    ) { target ->
        when (target) {
            Screen.Home -> HistoryScreen(vm = homeVm, onOpenSettings = { screen = Screen.Settings })
            Screen.Settings -> SettingsScreen(onBack = { homeVm.refreshConfigured(); screen = Screen.Home })
        }
    }

    BackHandler(enabled = screen == Screen.Settings) {
        homeVm.refreshConfigured()
        screen = Screen.Home
    }
}
