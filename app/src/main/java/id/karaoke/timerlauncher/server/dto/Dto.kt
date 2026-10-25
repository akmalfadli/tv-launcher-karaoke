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
data class CustomerRequestDto(
    val id: String,
    val type: String, // "EXTENSION" or "WAITER"
    val requestedMinutes: Int = 0,
    val message: String = "",
    val timestamp: Long = 0L,
    val status: String = "PENDING" // "PENDING", "ACCEPTED", "REJECTED"
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
    val warnings: List<String> = emptyList(),
    val customerName: String? = null,
    val pendingCustomerRequest: CustomerRequestDto? = null
)

@Serializable
data class CustomerStatusDto(
    val roomName: String,
    val status: String,
    val remainingMs: Long,
    val totalMs: Long,
    val formattedRemaining: String,
    val customerName: String? = null,
    val operatorPhone: String = "",
    val pendingRequest: CustomerRequestDto? = null
)

@Serializable
data class CustomerActionRequest(
    val type: String = "EXTENSION", // "EXTENSION" or "WAITER"
    val requestedMinutes: Int = 60,
    val message: String = ""
)

@Serializable
data class CustomerVolumeRequest(
    val action: String = "UP" // "UP", "DOWN", "MUTE"
)

@Serializable
data class RespondCustomerRequest(
    val requestId: String,
    val action: String = "ACCEPT" // "ACCEPT" or "REJECT"
)

@Serializable
data class TimerActionRequest(
    val minutes: Int = 0,
    val customerName: String? = null
)

@Serializable
data class CustomerNameRequest(
    val customerName: String
)

@Serializable
data class RentalRecord(
    val id: String,
    val customerName: String,
    val roomName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val initialDurationMinutes: Int,
    val extendedMinutes: Int = 0,
    val totalDurationMinutes: Int = initialDurationMinutes,
    val status: String = "ACTIVE" // "ACTIVE", "COMPLETED", "STOPPED", "RESET"
)

@Serializable
data class WsMessage(
    val type: String, // "state", "timer_finished", "heartbeat", "warning"
    val status: StatusDto? = null,
    val message: String? = null
)

@Serializable
data class AppDto(
    val packageName: String,
    val appName: String,
    val isLeanback: Boolean,
    val isAllowed: Boolean
)

@Serializable
data class ToggleAppRequest(
    val packageName: String,
    val isAllowed: Boolean
)

@Serializable
data class SettingsDto(
    val roomName: String,
    val hourlyRate: Long,
    val overlayCorner: String,
    val overlaySize: String,
    val standbyBgMode: String = "NONE",
    val standbyYoutubeUrl: String = "",
    val standbyYoutubeMute: Boolean = true,
    val standbyVideoMute: Boolean = true,
    val hasCustomBgImage: Boolean = false,
    val hasCustomBgVideo: Boolean = false,
    val operatorPhone: String = ""
)

@Serializable
data class UpdateSettingsRequest(
    val roomName: String? = null,
    val hourlyRate: Long? = null,
    val overlayCorner: String? = null,
    val overlaySize: String? = null,
    val newPin: String? = null,
    val standbyBgMode: String? = null,
    val standbyYoutubeUrl: String? = null,
    val standbyYoutubeMute: Boolean? = null,
    val standbyVideoMute: Boolean? = null,
    val operatorPhone: String? = null
)

@Serializable
data class UploadImageRequest(
    val imageBase64: String
)

