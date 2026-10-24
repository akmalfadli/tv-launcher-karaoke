package id.karaoke.timerlauncher.auth

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class Session(
    val token: String,
    val createdAt: Long,
    val expiresAt: Long
)

class SessionManager(
    private val sessionDurationMs: Long = 12 * 60 * 60 * 1000L // 12 hours
) {
    private val sessions = ConcurrentHashMap<String, Session>()
    private val secureRandom = SecureRandom()

    fun createSession(): Session {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        val now = System.currentTimeMillis()
        val session = Session(
            token = token,
            createdAt = now,
            expiresAt = now + sessionDurationMs
        )
        sessions[token] = session
        cleanExpiredSessions()
        return session
    }

    fun isValid(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val session = sessions[token] ?: return false
        if (System.currentTimeMillis() > session.expiresAt) {
            sessions.remove(token)
            return false
        }
        return true
    }

    fun revoke(token: String) {
        sessions.remove(token)
    }

    fun revokeAll() {
        sessions.clear()
    }

    private fun cleanExpiredSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.expiresAt < now }
    }
}

class LoginRateLimiter(
    private val maxAttempts: Int = 5,
    private val lockoutDurationMs: Long = 5 * 60 * 1000L // 5 minutes
) {
    private data class AttemptRecord(
        var failedCount: Int = 0,
        var lockedUntil: Long = 0L
    )

    private val records = ConcurrentHashMap<String, AttemptRecord>()

    fun isLocked(identifier: String): Boolean {
        val record = records[identifier] ?: return false
        val now = System.currentTimeMillis()
        if (now < record.lockedUntil) {
            return true
        }
        if (record.lockedUntil != 0L && now >= record.lockedUntil) {
            // Lockout expired, reset
            record.failedCount = 0
            record.lockedUntil = 0L
        }
        return false
    }

    fun getRemainingLockoutSeconds(identifier: String): Long {
        val record = records[identifier] ?: return 0L
        val remainingMs = record.lockedUntil - System.currentTimeMillis()
        return (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
    }

    fun recordFailedAttempt(identifier: String): Boolean {
        val record = records.computeIfAbsent(identifier) { AttemptRecord() }
        record.failedCount++
        if (record.failedCount >= maxAttempts) {
            record.lockedUntil = System.currentTimeMillis() + lockoutDurationMs
            return true // is now locked
        }
        return false
    }

    fun recordSuccessfulAttempt(identifier: String) {
        records.remove(identifier)
    }
}
