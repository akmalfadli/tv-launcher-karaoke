package id.karaoke.timerlauncher.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import id.karaoke.timerlauncher.settings.OverlayCorner
import id.karaoke.timerlauncher.settings.OverlaySize
import id.karaoke.timerlauncher.timer.TimerState
import id.karaoke.timerlauncher.timer.TimerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main)

    private var overlayRootView: FrameLayout? = null
    private var pillLayout: LinearLayout? = null
    private var tvPillTimer: TextView? = null
    private var tvPillStatus: TextView? = null
    private var timesUpContainer: LinearLayout? = null
    private var tvTimesUpTitle: TextView? = null
    private var tvTimesUpSubtitle: TextView? = null

    private var isLauncherInForeground: Boolean = false
    private var currentTimerState: TimerState = TimerState()
    private var currentCorner: OverlayCorner = OverlayCorner.TOP_RIGHT
    private var currentSize: OverlaySize = OverlaySize.MEDIUM

    @SuppressLint("RtlHardcoded")
    fun updateState(state: TimerState) {
        scope.launch {
            currentTimerState = state
            render()
        }
    }

    fun updateConfig(corner: OverlayCorner, size: OverlaySize) {
        scope.launch {
            currentCorner = corner
            currentSize = size
            render()
        }
    }

    fun setLauncherForeground(inForeground: Boolean) {
        scope.launch {
            isLauncherInForeground = inForeground
            render()
        }
    }

    private fun render() {
        if (!Settings.canDrawOverlays(context)) {
            removeOverlay()
            return
        }

        // When launcher is in foreground and timer is not finished, hide overlay so launcher's own UI is clean
        if (isLauncherInForeground && currentTimerState.status != TimerStatus.FINISHED) {
            removeOverlay()
            return
        }

        // If IDLE, no overlay needed
        if (currentTimerState.status == TimerStatus.IDLE) {
            removeOverlay()
            return
        }

        ensureViewsCreated()

        val root = overlayRootView ?: return

        if (currentTimerState.status == TimerStatus.FINISHED) {
            // Full screen TIMES UP alert
            pillLayout?.visibility = View.GONE
            timesUpContainer?.visibility = View.VISIBLE
            updateLayoutParams(isFullScreen = true)
        } else {
            // Pill corner overlay
            timesUpContainer?.visibility = View.GONE
            pillLayout?.visibility = View.VISIBLE
            updateLayoutParams(isFullScreen = false)

            tvPillTimer?.text = currentTimerState.formattedRemaining
            if (currentTimerState.status == TimerStatus.PAUSED) {
                tvPillStatus?.text = "DIJEDA"
                tvPillStatus?.visibility = View.VISIBLE
                pillLayout?.alpha = 0.8f
            } else {
                tvPillStatus?.visibility = View.GONE
                pillLayout?.alpha = 1.0f
            }

            // Text sizing
            val spSize = when (currentSize) {
                OverlaySize.SMALL -> 26f
                OverlaySize.MEDIUM -> 34f
                OverlaySize.LARGE -> 44f
            }
            tvPillTimer?.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)
        }
    }

    private fun ensureViewsCreated() {
        if (overlayRootView != null) return

        val root = FrameLayout(context)
        overlayRootView = root

        // 1. Pill Layout
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(18), dpToPx(8), dpToPx(18), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E612141D")) // High opacity dark glass
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#446366F1")) // Indigo accent border
            }
        }
        pillLayout = pill

        val tvTimer = TextView(context).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        tvPillTimer = tvTimer
        pill.addView(tvTimer)

        val tvStatus = TextView(context).apply {
            setTextColor(Color.parseColor("#FBBF24")) // Amber
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        tvPillStatus = tvStatus
        pill.addView(tvStatus)

        root.addView(pill)

        // 2. Fullscreen Times-Up Alert
        val timesUp = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#D97F1D1D")) // Deep semi-translucent red
            visibility = View.GONE
        }
        timesUpContainer = timesUp

        val title = TextView(context).apply {
            text = "WAKTU HABIS"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(16f, 0f, 4f, Color.BLACK)
        }
        tvTimesUpTitle = title
        timesUp.addView(title)

        val subtitle = TextView(context).apply {
            text = "Silakan hubungi operator untuk memperpanjang waktu sewa"
            setTextColor(Color.parseColor("#FEE2E2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(16), 0, 0)
        }
        tvTimesUpSubtitle = subtitle
        timesUp.addView(subtitle)

        root.addView(timesUp, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Initial WindowManager add
        try {
            val initialParams = createLayoutParams(isFullScreen = false)
            windowManager.addView(root, initialParams)
        } catch (e: Exception) {
            android.util.Log.e("OverlayController", "Failed to add initial overlay view to WindowManager", e)
            overlayRootView = null
        }
    }

    private fun updateLayoutParams(isFullScreen: Boolean) {
        val root = overlayRootView ?: return

        // Update pill layout gravity within root
        val pillParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = when (currentCorner) {
                OverlayCorner.TOP_RIGHT -> Gravity.TOP or Gravity.RIGHT
                OverlayCorner.TOP_LEFT -> Gravity.TOP or Gravity.LEFT
                OverlayCorner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.RIGHT
                OverlayCorner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.LEFT
            }
            val margin = dpToPx(32)
            setMargins(margin, margin, margin, margin)
        }
        pillLayout?.layoutParams = pillParams

        val wmParams = createLayoutParams(isFullScreen)
        try {
            windowManager.updateViewLayout(root, wmParams)
        } catch (e: Exception) {
            // view might not be attached
        }
    }

    private fun createLayoutParams(isFullScreen: Boolean): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return if (isFullScreen) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT
            )
        } else {
            val gravityVal = when (currentCorner) {
                OverlayCorner.TOP_RIGHT -> Gravity.TOP or Gravity.RIGHT
                OverlayCorner.TOP_LEFT -> Gravity.TOP or Gravity.LEFT
                OverlayCorner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.RIGHT
                OverlayCorner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.LEFT
            }
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = gravityVal
            }
        }
    }

    private fun removeOverlay() {
        overlayRootView?.let { root ->
            try {
                windowManager.removeView(root)
            } catch (e: Exception) {
                // ignore
            }
            overlayRootView = null
            pillLayout = null
            timesUpContainer = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
