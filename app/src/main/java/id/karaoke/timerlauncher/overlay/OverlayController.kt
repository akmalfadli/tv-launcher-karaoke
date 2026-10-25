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

    private enum class OverlayMode {
        CORNER,
        CENTER_FINAL_MINUTE,
        FULL_SCREEN_FINISHED
    }

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
    private var currentAppliedMode: OverlayMode? = null
    private var currentAppliedCorner: OverlayCorner? = null

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

        val targetMode = when {
            currentTimerState.status == TimerStatus.FINISHED -> OverlayMode.FULL_SCREEN_FINISHED
            (currentTimerState.status == TimerStatus.RUNNING || currentTimerState.status == TimerStatus.PAUSED) &&
                    currentTimerState.remainingMs in 1..60_000L -> OverlayMode.CENTER_FINAL_MINUTE
            else -> OverlayMode.CORNER
        }

        if (targetMode == OverlayMode.FULL_SCREEN_FINISHED) {
            pillLayout?.visibility = View.GONE
            timesUpContainer?.visibility = View.VISIBLE
            updateLayoutParams(targetMode)
        } else {
            timesUpContainer?.visibility = View.GONE
            pillLayout?.visibility = View.VISIBLE
            updateLayoutParams(targetMode)

            tvPillTimer?.text = currentTimerState.formattedRemaining

            when (targetMode) {
                OverlayMode.CENTER_FINAL_MINUTE -> {
                    // Big center pill for final 60 seconds
                    pillLayout?.setPadding(dpToPx(40), dpToPx(16), dpToPx(40), dpToPx(16))
                    pillLayout?.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#EE12141F")) // Deep dark glass
                        cornerRadius = dpToPx(24).toFloat()
                        setStroke(dpToPx(3), Color.parseColor("#EF4444")) // Vibrant Crimson Red
                    }

                    tvPillTimer?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f)
                    tvPillTimer?.setTextColor(Color.parseColor("#EF4444"))
                    tvPillTimer?.setShadowLayer(16f, 0f, 4f, Color.BLACK)

                    tvPillStatus?.text = if (currentTimerState.status == TimerStatus.PAUSED) {
                        "DIJEDA • WAKTU TERSISA"
                    } else {
                        "⚠️ WAKTU KARAOKE TERSISA"
                    }
                    tvPillStatus?.setTextColor(Color.parseColor("#FCA5A5"))
                    tvPillStatus?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    tvPillStatus?.setPadding(0, dpToPx(4), 0, 0)
                    tvPillStatus?.visibility = View.VISIBLE
                    pillLayout?.alpha = if (currentTimerState.status == TimerStatus.PAUSED) 0.85f else 1.0f
                }
                OverlayMode.CORNER -> {
                    // Standard corner pill
                    pillLayout?.setPadding(dpToPx(18), dpToPx(8), dpToPx(18), dpToPx(8))
                    pillLayout?.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E612141D"))
                        cornerRadius = dpToPx(16).toFloat()
                        setStroke(dpToPx(2), Color.parseColor("#446366F1"))
                    }

                    val spSize = when (currentSize) {
                        OverlaySize.SMALL -> 26f
                        OverlaySize.MEDIUM -> 34f
                        OverlaySize.LARGE -> 44f
                    }
                    tvPillTimer?.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)
                    tvPillTimer?.setTextColor(Color.WHITE)
                    tvPillTimer?.setShadowLayer(8f, 0f, 2f, Color.BLACK)

                    if (currentTimerState.status == TimerStatus.PAUSED) {
                        tvPillStatus?.text = "DIJEDA"
                        tvPillStatus?.setTextColor(Color.parseColor("#FBBF24"))
                        tvPillStatus?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        tvPillStatus?.setPadding(0, 0, 0, 0)
                        tvPillStatus?.visibility = View.VISIBLE
                        pillLayout?.alpha = 0.8f
                    } else {
                        tvPillStatus?.visibility = View.GONE
                        pillLayout?.alpha = 1.0f
                    }
                }
                else -> {}
            }
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
            val initialParams = createLayoutParams(OverlayMode.CORNER)
            windowManager.addView(root, initialParams)
            currentAppliedMode = OverlayMode.CORNER
            currentAppliedCorner = currentCorner
        } catch (e: Exception) {
            android.util.Log.e("OverlayController", "Failed to add initial overlay view to WindowManager", e)
            overlayRootView = null
        }
    }

    private fun updateLayoutParams(mode: OverlayMode) {
        val root = overlayRootView ?: return

        if (currentAppliedMode == mode && currentAppliedCorner == currentCorner) {
            return
        }

        val pillParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            when (mode) {
                OverlayMode.CENTER_FINAL_MINUTE -> {
                    gravity = Gravity.CENTER
                    setMargins(0, 0, 0, 0)
                }
                OverlayMode.CORNER -> {
                    gravity = when (currentCorner) {
                        OverlayCorner.TOP_RIGHT -> Gravity.TOP or Gravity.RIGHT
                        OverlayCorner.TOP_LEFT -> Gravity.TOP or Gravity.LEFT
                        OverlayCorner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.RIGHT
                        OverlayCorner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.LEFT
                    }
                    val margin = dpToPx(32)
                    setMargins(margin, margin, margin, margin)
                }
                OverlayMode.FULL_SCREEN_FINISHED -> {
                    gravity = Gravity.CENTER
                    setMargins(0, 0, 0, 0)
                }
            }
        }
        pillLayout?.layoutParams = pillParams

        val wmParams = createLayoutParams(mode)
        try {
            windowManager.updateViewLayout(root, wmParams)
            currentAppliedMode = mode
            currentAppliedCorner = currentCorner
        } catch (e: Exception) {
            // view might not be attached
        }
    }

    private fun createLayoutParams(mode: OverlayMode): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return when (mode) {
            OverlayMode.FULL_SCREEN_FINISHED -> {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    flags,
                    PixelFormat.TRANSLUCENT
                )
            }
            OverlayMode.CENTER_FINAL_MINUTE -> {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    flags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }
            }
            OverlayMode.CORNER -> {
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
            tvPillTimer = null
            tvPillStatus = null
            timesUpContainer = null
            currentAppliedMode = null
            currentAppliedCorner = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
