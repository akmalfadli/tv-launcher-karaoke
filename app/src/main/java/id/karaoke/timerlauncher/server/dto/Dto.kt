package id.karaoke.timerlauncher.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val pin: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val expiresAt: Long
)

@Serializable
data class ErrorResponse(
    val error: String,
    val retryAfterSeconds: Long? = null
)

@Serializable
data class StatusDto(
    val roomName: String,
    val status: String,
    val remainingMs: Long,
    val totalMs: Long,
    val serverElapsed: Long,
    val restoredAfterReboot: Boolean,
    val appVersion: String = "1.0.0",
    val warnings: List<String> = emptyList()
)

@Serializable
data class TimerActionRequest(
    val minutes: Int = 0
)

@Serializable
data class WsMessage(
    val type: String, // "state", "timer_finished", "heartbeat", "warning"
    val status: StatusDto? = null,
    val message: String? = null
)
