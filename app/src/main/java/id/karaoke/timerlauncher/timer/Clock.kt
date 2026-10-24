package id.karaoke.timerlauncher.timer

import android.os.SystemClock

interface Clock {
    fun elapsedRealtime(): Long
    fun wallTime(): Long
    fun bootCount(): Int
}

class SystemClockImpl(
    private val bootCountProvider: () -> Int = { 0 }
) : Clock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun wallTime(): Long = System.currentTimeMillis()
    override fun bootCount(): Int = bootCountProvider()
}
