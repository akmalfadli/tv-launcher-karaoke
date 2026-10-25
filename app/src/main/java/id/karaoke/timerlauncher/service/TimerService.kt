package id.karaoke.timerlauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import id.karaoke.timerlauncher.App
import id.karaoke.timerlauncher.R
import id.karaoke.timerlauncher.overlay.OverlayController
import id.karaoke.timerlauncher.timer.TimerState
import id.karaoke.timerlauncher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var overlayController: OverlayController
    private var stateObserverJob: Job? = null
    private var settingsObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val app = application as App
        overlayController = OverlayController(this)

        // Start Ktor local server
        app.localServer.start()

        // Start foreground
        val notification = buildNotification(app.timerEngine.state.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            android.util.Log.e("TimerService", "Failed to startForeground with type, falling back to basic startForeground", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Throwable) {
                android.util.Log.e("TimerService", "Failed basic startForeground fallback", e2)
            }
        }

        // Observe timer state
        stateObserverJob = serviceScope.launch {
            app.timerEngine.state.collectLatest { state ->
                overlayController.updateState(state)
                updateNotification(state)
            }
        }

        // Observe settings changes (corner and size)
        settingsObserverJob = serviceScope.launch {
            app.settingsRepository.settingsFlow.collectLatest { settings ->
                overlayController.updateConfig(settings.overlayCorner, settings.overlaySize)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra(EXTRA_LAUNCHER_FOREGROUND) == true) {
            val inForeground = intent.getBooleanExtra(EXTRA_LAUNCHER_FOREGROUND, false)
            overlayController.setLauncherForeground(inForeground)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        val app = application as App
        app.localServer.stop()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: TimerState): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            state.isRunning -> "Sisa waktu: ${state.formattedRemaining}"
            state.isPaused -> "Timer dijeda: ${state.formattedRemaining}"
            state.isFinished -> "WAKTU HABIS!"
            else -> "Siap menerima sesi baru"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Karaoke Timer")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(state: TimerState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(state))
    }

    companion object {
        const val CHANNEL_ID = "karaoke_timer_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_LAUNCHER_FOREGROUND = "extra_launcher_foreground"

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun notifyLauncherForeground(context: Context, inForeground: Boolean) {
            val intent = Intent(context, TimerService::class.java).apply {
                putExtra(EXTRA_LAUNCHER_FOREGROUND, inForeground)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
