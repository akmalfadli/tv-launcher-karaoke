package id.karaoke.timerlauncher

import android.app.Application
import id.karaoke.timerlauncher.auth.LoginRateLimiter
import id.karaoke.timerlauncher.auth.SessionManager
import id.karaoke.timerlauncher.server.LocalServer
import id.karaoke.timerlauncher.settings.SettingsRepository
import id.karaoke.timerlauncher.timer.Clock
import id.karaoke.timerlauncher.timer.SystemClockImpl
import id.karaoke.timerlauncher.timer.TimerEngine
import id.karaoke.timerlauncher.timer.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var clock: Clock
        private set
    lateinit var timerRepository: TimerRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var timerEngine: TimerEngine
        private set
    lateinit var sessionManager: SessionManager
        private set
    lateinit var rateLimiter: LoginRateLimiter
        private set
    lateinit var localServer: LocalServer
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        clock = SystemClockImpl(
            bootCountProvider = {
                try {
                    android.provider.Settings.Global.getInt(
                        contentResolver,
                        android.provider.Settings.Global.BOOT_COUNT,
                        0
                    )
                } catch (e: Exception) {
                    0
                }
            }
        )

        timerRepository = TimerRepository(this)
        settingsRepository = SettingsRepository(this)
        sessionManager = SessionManager()
        rateLimiter = LoginRateLimiter()

        timerEngine = TimerEngine(
            clock = clock,
            scope = applicationScope,
            onSnapshot = { snapshot ->
                timerRepository.saveSnapshot(snapshot)
            }
        )

        localServer = LocalServer(
            context = this,
            clock = clock,
            timerEngine = timerEngine,
            settingsRepository = settingsRepository,
            sessionManager = sessionManager,
            rateLimiter = rateLimiter,
            port = 8080
        )

        // Restore snapshot on startup
        applicationScope.launch {
            val saved = timerRepository.loadSnapshot()
            if (saved != null) {
                timerEngine.restoreFromSnapshot(saved)
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
