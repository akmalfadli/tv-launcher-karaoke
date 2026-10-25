package id.karaoke.timerlauncher.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import id.karaoke.timerlauncher.ui.MainActivity

class TvRemoteAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        TvNavigationController.accessibilityService = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (TvNavigationController.accessibilityService == this) {
            TvNavigationController.accessibilityService = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op
    }

    override fun onInterrupt() {
        // No-op
    }
}

object TvNavigationController {
    var accessibilityService: TvRemoteAccessibilityService? = null

    fun sendNavKey(action: String, context: Context? = null): Boolean {
        if (action.equals("HOME", ignoreCase = true)) {
            val ctx = context ?: accessibilityService
            if (ctx != null) {
                bringLauncherToFront(ctx)
                return true
            }
        }

        val service = accessibilityService
        if (service != null) {
            val globalAction = when (action.uppercase()) {
                "UP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 16 else null
                "DOWN" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 17 else null
                "LEFT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 18 else null
                "RIGHT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 19 else null
                "CENTER", "OK", "SELECT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 20 else null
                "BACK" -> AccessibilityService.GLOBAL_ACTION_BACK
                else -> null
            }
            if (globalAction != null) {
                val ok = service.performGlobalAction(globalAction)
                if (ok) return true
            }
        }

        // Fallback: system input keyevent
        val keyCode = when (action.uppercase()) {
            "UP" -> "19"
            "DOWN" -> "20"
            "LEFT" -> "21"
            "RIGHT" -> "22"
            "CENTER", "OK", "SELECT" -> "23"
            "BACK" -> "4"
            "ENTER" -> "66"
            "DEL", "BACKSPACE" -> "67"
            "SPACE" -> "62"
            "MEDIA_PAUSE", "PAUSE" -> "127"
            "MEDIA_STOP", "STOP" -> "86"
            "MEDIA_PLAY_PAUSE", "PLAY_PAUSE" -> "85"
            "0" -> "7"
            "1" -> "8"
            "2" -> "9"
            "3" -> "10"
            "4" -> "11"
            "5" -> "12"
            "6" -> "13"
            "7" -> "14"
            "8" -> "15"
            "9" -> "16"
            else -> return false
        }
        return try {
            Runtime.getRuntime().exec(arrayOf("input", "keyevent", keyCode))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Brings the Karaoke Launcher (MainActivity) directly to foreground.
     */
    fun bringLauncherToFront(context: Context) {
        try {
            val homeIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                8888,
                homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                pendingIntent.send()
            } catch (_: Exception) {
                context.startActivity(homeIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("TvNavController", "Failed to bring MainActivity to front", e)
        }
    }

    /**
     * Stops/pauses running media playback (e.g. YouTube), reclaims audio focus,
     * and brings the TV back to the Karaoke Launcher Home screen.
     */
    fun stopPlaybackAndReturnHome(context: Context) {
        // 1. Send MEDIA PAUSE and MEDIA STOP keycodes to pause any running video/music player
        try {
            sendNavKey("MEDIA_PAUSE")
            sendNavKey("MEDIA_STOP")
        } catch (e: Exception) {
            android.util.Log.e("TvNavController", "Error sending media pause keys", e)
        }

        // 2. Request Audio Focus to force external apps (like YouTube TV) to abandon audio focus and pause
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                    audioManager.requestAudioFocus(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TvNavController", "Error requesting audio focus", e)
        }

        // 3. Bring Karaoke Launcher (MainActivity) to foreground directly
        bringLauncherToFront(context)

        // 4. Kill background YouTube processes so it doesn't continue streaming/buffering
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.killBackgroundProcesses("com.google.android.youtube.tv")
            activityManager?.killBackgroundProcesses("com.google.android.youtube")
        } catch (e: Exception) {
            android.util.Log.e("TvNavController", "Error killing background YouTube", e)
        }
    }
}
