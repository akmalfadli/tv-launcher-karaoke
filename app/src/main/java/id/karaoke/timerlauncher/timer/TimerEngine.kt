package id.karaoke.timerlauncher.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimerEngine(
    private val clock: Clock,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onSnapshot: (suspend (TimerSnapshot) -> Unit)? = null
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimerEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    private var countdownJob: Job? = null
    private var tickerJob: Job? = null

    suspend fun start(minutes: Int) = mutex.withLock {
        require(minutes > 0) { "Minutes must be greater than 0" }
        val durationMs = minutes * 60_000L
        val nowElapsed = clock.elapsedRealtime()
        val endElapsed = nowElapsed + durationMs

        updateState(
            TimerState(
                status = TimerStatus.RUNNING,
                remainingMs = durationMs,
                totalMs = durationMs,
                endElapsed = endElapsed,
                restoredAfterReboot = false
            )
        )
        armTimers()
        saveSnapshot()
    }

    suspend fun pause() = mutex.withLock {
        val current = _state.value
        if (current.status != TimerStatus.RUNNING) return@withLock

        val remaining = (current.endElapsed - clock.elapsedRealtime()).coerceAtLeast(0L)
        if (remaining <= 0L) {
            handleFinished()
        } else {
            updateState(
                current.copy(
                    status = TimerStatus.PAUSED,
                    remainingMs = remaining,
                    endElapsed = 0L
                )
            )
            disarmTimers()
            saveSnapshot()
        }
    }

    suspend fun resume() = mutex.withLock {
        val current = _state.value
        if (current.status != TimerStatus.PAUSED) return@withLock
        if (current.remainingMs <= 0L) {
            handleFinished()
            return@withLock
        }

        val endElapsed = clock.elapsedRealtime() + current.remainingMs
        updateState(
            current.copy(
                status = TimerStatus.RUNNING,
                endElapsed = endElapsed
            )
        )
        armTimers()
        saveSnapshot()
    }

    suspend fun adjust(minutes: Int) = mutex.withLock {
        val deltaMs = minutes * 60_000L
        val current = _state.value

        when (current.status) {
            TimerStatus.RUNNING -> {
                val now = clock.elapsedRealtime()
                val currentRemaining = (current.endElapsed - now).coerceAtLeast(0L)
                val newRemaining = currentRemaining + deltaMs

                if (newRemaining <= 0L) {
                    handleFinished()
                } else {
                    val newTotal = (current.totalMs + deltaMs).coerceAtLeast(newRemaining)
                    val newEnd = now + newRemaining
                    updateState(
                        current.copy(
                            remainingMs = newRemaining,
                            totalMs = newTotal,
                            endElapsed = newEnd
                        )
                    )
                    armTimers()
                    saveSnapshot()
                }
            }
            TimerStatus.PAUSED -> {
                val newRemaining = current.remainingMs + deltaMs
                if (newRemaining <= 0L) {
                    handleFinished()
                } else {
                    val newTotal = (current.totalMs + deltaMs).coerceAtLeast(newRemaining)
                    updateState(
                        current.copy(
                            remainingMs = newRemaining,
                            totalMs = newTotal
                        )
                    )
                    saveSnapshot()
                }
            }
            TimerStatus.FINISHED -> {
                if (minutes > 0) {
                    val now = clock.elapsedRealtime()
                    val newRemaining = deltaMs
                    val newTotal = current.totalMs + deltaMs
                    updateState(
                        TimerState(
                            status = TimerStatus.RUNNING,
                            remainingMs = newRemaining,
                            totalMs = newTotal,
                            endElapsed = now + newRemaining,
                            restoredAfterReboot = false
                        )
                    )
                    armTimers()
                    saveSnapshot()
                }
            }
            TimerStatus.IDLE -> {
                if (minutes > 0) {
                    // Start directly if Idle
                    start(minutes)
                }
            }
        }
    }

    suspend fun stop() = mutex.withLock {
        updateState(TimerState(status = TimerStatus.IDLE))
        disarmTimers()
        saveSnapshot()
    }

    suspend fun reset() = stop()

    fun dispose() {
        disarmTimers()
    }

    suspend fun restoreFromSnapshot(snapshot: TimerSnapshot) = mutex.withLock {
        when (snapshot.status) {
            TimerStatus.IDLE -> {
                updateState(TimerState(status = TimerStatus.IDLE))
                disarmTimers()
            }
            TimerStatus.PAUSED -> {
                updateState(
                    TimerState(
                        status = TimerStatus.PAUSED,
                        remainingMs = snapshot.remainingMs.coerceAtLeast(0L),
                        totalMs = snapshot.totalMs,
                        restoredAfterReboot = snapshot.restoredAfterReboot
                    )
                )
                disarmTimers()
            }
            TimerStatus.FINISHED -> {
                updateState(
                    TimerState(
                        status = TimerStatus.FINISHED,
                        remainingMs = 0L,
                        totalMs = snapshot.totalMs,
                        restoredAfterReboot = snapshot.restoredAfterReboot
                    )
                )
                disarmTimers()
            }
            TimerStatus.RUNNING -> {
                val currentBootCount = clock.bootCount()
                val isSameBoot = (snapshot.bootCount == currentBootCount)
                val remainingMs: Long

                if (isSameBoot) {
                    val elapsedDelta = clock.elapsedRealtime() - snapshot.savedElapsed
                    remainingMs = snapshot.remainingMs - elapsedDelta
                } else {
                    val wallDelta = clock.wallTime() - snapshot.savedWall
                    remainingMs = snapshot.remainingMs - wallDelta
                }

                if (remainingMs <= 0L) {
                    updateState(
                        TimerState(
                            status = TimerStatus.FINISHED,
                            remainingMs = 0L,
                            totalMs = snapshot.totalMs,
                            restoredAfterReboot = !isSameBoot || snapshot.restoredAfterReboot
                        )
                    )
                    disarmTimers()
                    _events.tryEmit(TimerEvent.Finished)
                } else {
                    val endElapsed = clock.elapsedRealtime() + remainingMs
                    updateState(
                        TimerState(
                            status = TimerStatus.RUNNING,
                            remainingMs = remainingMs,
                            totalMs = snapshot.totalMs,
                            endElapsed = endElapsed,
                            restoredAfterReboot = !isSameBoot || snapshot.restoredAfterReboot
                        )
                    )
                    armTimers()
                }
            }
        }
    }

    fun createSnapshot(): TimerSnapshot {
        val current = _state.value
        val nowElapsed = clock.elapsedRealtime()
        val remaining = if (current.status == TimerStatus.RUNNING) {
            (current.endElapsed - nowElapsed).coerceAtLeast(0L)
        } else {
            current.remainingMs
        }

        return TimerSnapshot(
            status = current.status,
            remainingMs = remaining,
            totalMs = current.totalMs,
            savedElapsed = nowElapsed,
            savedWall = clock.wallTime(),
            bootCount = clock.bootCount(),
            restoredAfterReboot = current.restoredAfterReboot
        )
    }

    private suspend fun handleFinished() {
        disarmTimers()
        val current = _state.value
        updateState(
            current.copy(
                status = TimerStatus.FINISHED,
                remainingMs = 0L,
                endElapsed = 0L
            )
        )
        _events.emit(TimerEvent.Finished)
        saveSnapshot()
    }

    private fun armTimers() {
        countdownJob?.cancel()
        tickerJob?.cancel()

        val current = _state.value
        if (current.status != TimerStatus.RUNNING) return

        val remaining = (current.endElapsed - clock.elapsedRealtime()).coerceAtLeast(0L)

        // Exact finish detector
        countdownJob = scope.launch {
            if (remaining > 0L) {
                delay(remaining)
            }
            mutex.withLock {
                if (_state.value.status == TimerStatus.RUNNING) {
                    val actualRemaining = (_state.value.endElapsed - clock.elapsedRealtime()).coerceAtLeast(0L)
                    if (actualRemaining <= 0L) {
                        handleFinished()
                    }
                }
            }
        }

        // 1-second ticker for UI and state subscribers
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                mutex.withLock {
                    if (_state.value.status == TimerStatus.RUNNING) {
                        val currentRem = (_state.value.endElapsed - clock.elapsedRealtime()).coerceAtLeast(0L)
                        if (currentRem <= 0L) {
                            handleFinished()
                            return@withLock
                        } else {
                            updateState(_state.value.copy(remainingMs = currentRem))
                        }
                    }
                }
            }
        }
    }

    private fun disarmTimers() {
        countdownJob?.cancel()
        countdownJob = null
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateState(newState: TimerState) {
        _state.value = newState
    }

    private suspend fun saveSnapshot() {
        val snapshot = createSnapshot()
        onSnapshot?.invoke(snapshot)
    }
}

sealed interface TimerEvent {
    data object Finished : TimerEvent
}
