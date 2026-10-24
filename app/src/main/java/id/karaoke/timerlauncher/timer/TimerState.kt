package id.karaoke.timerlauncher.timer

import kotlinx.serialization.Serializable

@Serializable
enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED
}

@Serializable
data class TimerSnapshot(
    val status: TimerStatus,
    val remainingMs: Long,
    val totalMs: Long,
    val savedElapsed: Long,
    val savedWall: Long,
    val bootCount: Int,
    val restoredAfterReboot: Boolean = false
)

data class TimerState(
    val status: TimerStatus = TimerStatus.IDLE,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val endElapsed: Long = 0L,
    val restoredAfterReboot: Boolean = false
) {
    val isRunning: Boolean get() = status == TimerStatus.RUNNING
    val isPaused: Boolean get() = status == TimerStatus.PAUSED
    val isFinished: Boolean get() = status == TimerStatus.FINISHED
    val isIdle: Boolean get() = status == TimerStatus.IDLE

    val formattedRemaining: String
        get() {
            val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L

            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val progress: Float
        get() {
            if (totalMs <= 0L) return 0f
            return (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        }
}
