package id.karaoke.timerlauncher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import id.karaoke.timerlauncher.App
import id.karaoke.timerlauncher.service.TimerService
import id.karaoke.timerlauncher.ui.home.HomeScreen
import id.karaoke.timerlauncher.ui.settings.SettingsScreen
import id.karaoke.timerlauncher.ui.setup.SetupScreen
import id.karaoke.timerlauncher.ui.theme.KaraokeLauncherTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure background timer service is running
        TimerService.start(this)

        setContent {
            KaraokeLauncherTheme {
                val app = App.instance
                val settings by app.settingsRepository.settingsFlow.collectAsState(initial = app.settingsRepository.getSettingsSync())

                var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

                when {
                    !settings.isSetupDone -> {
                        SetupScreen(
                            onSetupComplete = {
                                currentScreen = Screen.Main
                            }
                        )
                    }
                    currentScreen == Screen.Settings -> {
                        SettingsScreen(
                            onBack = { currentScreen = Screen.Main }
                        )
                    }
                    else -> {
                        HomeScreen(
                            onOpenSettings = { currentScreen = Screen.Settings }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TimerService.notifyLauncherForeground(this, inForeground = true)
    }

    override fun onPause() {
        TimerService.notifyLauncherForeground(this, inForeground = false)
        super.onPause()
    }
}

sealed interface Screen {
    data object Main : Screen
    data object Settings : Screen
}
