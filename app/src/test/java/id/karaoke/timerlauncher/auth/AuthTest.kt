package id.karaoke.timerlauncher.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {

    @Test
    fun `PinHasher hashes and verifies correct PIN`() {
        val pin = "4321"
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hashPin(pin, salt)

        assertNotEquals(pin, hash)
        assertTrue(PinHasher.verifyPin(pin, salt, hash))
        assertFalse(PinHasher.verifyPin("0000", salt, hash))
        assertFalse(PinHasher.verifyPin("432", salt, hash))
    }

    @Test
    fun `SessionManager creates valid token and revokes correctly`() {
        val manager = SessionManager(sessionDurationMs = 60_000L)
        val session = manager.createSession()

        assertTrue(session.token.isNotBlank())
        assertTrue(manager.isValid(session.token))
        assertFalse(manager.isValid("invalid_token_xyz"))

        manager.revoke(session.token)
        assertFalse(manager.isValid(session.token))
    }

    @Test
    fun `SessionManager revokeAll invalidates all active sessions`() {
        val manager = SessionManager(sessionDurationMs = 60_000L)
        val s1 = manager.createSession()
        val s2 = manager.createSession()

        assertTrue(manager.isValid(s1.token))
        assertTrue(manager.isValid(s2.token))

        manager.revokeAll()

        assertFalse(manager.isValid(s1.token))
        assertFalse(manager.isValid(s2.token))
    }

    @Test
    fun `LoginRateLimiter locks after 5 consecutive failures`() {
        val limiter = LoginRateLimiter(maxAttempts = 5, lockoutDurationMs = 60_000L)
        val ip = "192.168.1.50"

        assertFalse(limiter.isLocked(ip))

        for (i in 1..4) {
            val locked = limiter.recordFailedAttempt(ip)
            assertFalse(locked)
            assertFalse(limiter.isLocked(ip))
        }

        // 5th attempt locks
        val lockedOn5th = limiter.recordFailedAttempt(ip)
        assertTrue(lockedOn5th)
        assertTrue(limiter.isLocked(ip))
        assertTrue(limiter.getRemainingLockoutSeconds(ip) > 0)

        // Reset on success
        limiter.recordSuccessfulAttempt(ip)
        assertFalse(limiter.isLocked(ip))
    }
}
