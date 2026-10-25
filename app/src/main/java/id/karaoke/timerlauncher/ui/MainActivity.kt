package id.karaoke.timerlauncher.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import id.karaoke.timerlauncher.App
import id.karaoke.timerlauncher.service.TimerService
import id.karaoke.timerlauncher.ui.home.HomeScreen
import id.karaoke.timerlauncher.ui.settings.SettingsScreen
import id.karaoke.timerlauncher.ui.setup.SetupScreen
import id.karaoke.timerlauncher.ui.theme.KaraokeLauncherTheme

class MainActivity : ComponentActivity() {

    private val currentScreenState = mutableStateOf<Screen>(Screen.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure background timer service is running
        TimerService.start(this)

        setContent {
            KaraokeLauncherTheme {
                val app = App.instance
                val settings by app.settingsRepository.settingsFlow.collectAsState(initial = app.settingsRepository.getSettingsSync())

                val currentScreen by currentScreenState

                // Prevent BACK button from exiting or minimizing the launcher
                BackHandler(enabled = true) {
                    if (currentScreenState.value != Screen.Main) {
                        currentScreenState.value = Screen.Main
                    }
                }

                when {
                    !settings.isSetupDone -> {
                        SetupScreen(
                            onSetupComplete = {
                                currentScreenState.value = Screen.Main
                            }
                        )
                    }
                    currentScreen == Screen.Settings -> {
                        SettingsScreen(
                            onBack = { currentScreenState.value = Screen.Main }
                        )
                    }
                    else -> {
                        HomeScreen(
                            onOpenSettings = { currentScreenState.value = Screen.Settings }
                        )
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentScreenState.value != Screen.Main) {
                currentScreenState.value = Screen.Main
                return true
            }
            // Consume the back key so the launcher is never exited or dismissed
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentScreenState.value = Screen.Main
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
