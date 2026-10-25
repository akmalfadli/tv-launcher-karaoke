package id.karaoke.timerlauncher.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

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

    fun sendNavKey(action: String): Boolean {
        val service = accessibilityService
        if (service != null) {
            val globalAction = when (action.uppercase()) {
                "UP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 16 else null
                "DOWN" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 17 else null
                "LEFT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 18 else null
                "RIGHT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 19 else null
                "CENTER", "OK", "SELECT" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 20 else null
                "BACK" -> AccessibilityService.GLOBAL_ACTION_BACK
                "HOME" -> AccessibilityService.GLOBAL_ACTION_HOME
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
            "HOME" -> "3"
            "ENTER" -> "66"
            "DEL", "BACKSPACE" -> "67"
            "SPACE" -> "62"
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
}
