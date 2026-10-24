package id.karaoke.timerlauncher.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeClock(
    var elapsed: Long = 100_000L,
    var wall: Long = 1_700_000_000_000L,
    var boot: Int = 1
) : Clock {
    override fun elapsedRealtime(): Long = elapsed
    override fun wallTime(): Long = wall
    override fun bootCount(): Int = boot

    fun advance(ms: Long) {
        elapsed += ms
        wall += ms
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    private lateinit var clock: FakeClock
    private var engine: TimerEngine? = null

    @Before
    fun setup() {
        clock = FakeClock()
    }

    @After
    fun tearDown() {
        engine?.dispose()
        engine = null
    }

    @Test
    fun `start transitions to RUNNING with exact duration`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        assertEquals(TimerStatus.IDLE, eng.state.value.status)

        eng.start(60)

        val state = eng.state.value
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(3_600_000L, state.remainingMs)
        assertEquals(3_600_000L, state.totalMs)
        assertEquals(clock.elapsed + 3_600_000L, state.endElapsed)
        assertFalse(state.restoredAfterReboot)
        eng.dispose()
    }

    @Test
    fun `pause saves remaining time and stops running`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        eng.start(60)

        clock.advance(600_000L) // 10 minutes pass
        eng.pause()

        val state = eng.state.value
        assertEquals(TimerStatus.PAUSED, state.status)
        assertEquals(3_000_000L, state.remainingMs)
        eng.dispose()
    }

    @Test
    fun `resume continues from paused remaining time`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        eng.start(60)
        clock.advance(600_000L)
        eng.pause()

        clock.advance(300_000L) // 5 minutes pass while paused
        eng.resume()

        val state = eng.state.value
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(3_000_000L, state.remainingMs)
        assertEquals(clock.elapsed + 3_000_000L, state.endElapsed)
        eng.dispose()
    }

    @Test
    fun `adjust adds time while running`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        eng.start(60)
        clock.advance(600_000L) // 50m remaining

        eng.adjust(15) // +15m -> 65m remaining

        val state = eng.state.value
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(65 * 60_000L, state.remainingMs)
        eng.dispose()
    }

    @Test
    fun `adjust subtracts time below zero transitions to FINISHED`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        eng.start(10)

        eng.adjust(-15) // -15m -> below zero

        val state = eng.state.value
        assertEquals(TimerStatus.FINISHED, state.status)
        assertEquals(0L, state.remainingMs)
        eng.dispose()
    }

    @Test
    fun `adjust positive while FINISHED restarts timer`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        eng.start(10)
        eng.adjust(-15)
        assertEquals(TimerStatus.FINISHED, eng.state.value.status)

        eng.adjust(30) // Extend by 30 mins

        val state = eng.state.value
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(30 * 60_000L, state.remainingMs)
        eng.dispose()
    }

    @Test
    fun `stop resets timer to IDLE`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        eng.start(60)
        eng.stop()

        val state = eng.state.value
        assertEquals(TimerStatus.IDLE, state.status)
        assertEquals(0L, state.remainingMs)
        eng.dispose()
    }

    @Test
    fun `restoreFromSnapshot on same boot uses elapsed delta`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        val snapshot = TimerSnapshot(
            status = TimerStatus.RUNNING,
            remainingMs = 1_800_000L, // 30 mins
            totalMs = 3_600_000L,
            savedElapsed = clock.elapsed,
            savedWall = clock.wall,
            bootCount = clock.boot
        )

        clock.advance(300_000L) // 5 mins pass while process died
        eng.restoreFromSnapshot(snapshot)

        val state = eng.state.value
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(1_500_000L, state.remainingMs)
        assertFalse(state.restoredAfterReboot)
        eng.dispose()
    }

    @Test
    fun `restoreFromSnapshot after reboot uses wall delta and flags restoredAfterReboot`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        val snapshot = TimerSnapshot(
            status = TimerStatus.RUNNING,
            remainingMs = 1_800_000L, // 30 mins
            totalMs = 3_600_000L,
            savedElapsed = 999_999L,
            savedWall = clock.wall,
            bootCount = 1
        )

        // TV rebooted: boot count incremented, elapsed reset to small number, wall advanced 10m
        clock.boot = 2
        clock.elapsed = 20_000L
        clock.wall += 600_000L // 10 mins pass

        eng.restoreFromSnapshot(snapshot)

        val state = eng.state.value
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(1_200_000L, state.remainingMs) // 30m - 10m = 20m
        assertTrue(state.restoredAfterReboot)
        eng.dispose()
    }

    @Test
    fun `wall clock jump does not affect running timer calculation`() = runTest {
        val eng = TimerEngine(clock, this).also { engine = it }
        eng.start(60)

        // NTP or manual clock jump of 2 hours forward
        clock.wall += 7_200_000L
        // But only 1 minute of monotonic time passed
        clock.elapsed += 60_000L

        eng.pause() // Triggers calculation from endElapsed - elapsedRealtime()

        val state = eng.state.value
        assertEquals(59 * 60_000L, state.remainingMs) // Exactly 59 minutes, unaffected by 2hr jump!
        eng.dispose()
    }
}
