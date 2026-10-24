package id.karaoke.timerlauncher.server

import android.content.Context
import id.karaoke.timerlauncher.auth.LoginRateLimiter
import id.karaoke.timerlauncher.auth.PinHasher
import id.karaoke.timerlauncher.auth.SessionManager
import id.karaoke.timerlauncher.device.DeviceHealth
import id.karaoke.timerlauncher.server.dto.ErrorResponse
import id.karaoke.timerlauncher.server.dto.LoginRequest
import id.karaoke.timerlauncher.server.dto.LoginResponse
import id.karaoke.timerlauncher.server.dto.StatusDto
import id.karaoke.timerlauncher.server.dto.TimerActionRequest
import id.karaoke.timerlauncher.server.dto.WsMessage
import id.karaoke.timerlauncher.settings.SettingsRepository
import id.karaoke.timerlauncher.timer.Clock
import id.karaoke.timerlauncher.timer.TimerEngine
import id.karaoke.timerlauncher.timer.TimerEvent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

class LocalServer(
    private val context: Context,
    private val clock: Clock,
    private val timerEngine: TimerEngine,
    private val settingsRepository: SettingsRepository,
    private val sessionManager: SessionManager,
    private val rateLimiter: LoginRateLimiter,
    private val port: Int = 8080
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wsHub = WebSocketHub()
    private val json = Json { ignoreUnknownKeys = true }
    private var engine: EmbeddedServer<*, *>? = null
    private val isRunning = AtomicBoolean(false)
    private var broadcasterJob: Job? = null
    private var heartbeatJob: Job? = null

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                configureServer(this)
            }.start(wait = false)

            startBroadcaster()
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            broadcasterJob?.cancel()
            heartbeatJob?.cancel()
            engine?.stop(1000, 2000)
            engine = null
        }
    }

    private fun configureServer(application: Application) {
        application.install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }
        application.install(WebSockets)

        application.routing {
            // Static web app assets
            get("/") {
                serveAsset(call, "web/index.html", ContentType.Text.Html)
            }
            get("/style.css") {
                serveAsset(call, "web/style.css", ContentType.Text.CSS)
            }
            get("/app.js") {
                serveAsset(call, "web/app.js", ContentType.Application.JavaScript)
            }

            // Auth routes
            post("/api/auth/login") {
                val clientIp = call.request.origin.remoteHost
                if (rateLimiter.isLocked(clientIp)) {
                    val remaining = rateLimiter.getRemainingLockoutSeconds(clientIp)
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("Terlalu banyak percobaan PIN salah. Coba lagi nanti.", remaining)
                    )
                    return@post
                }

                val req = runCatching { call.receive<LoginRequest>() }.getOrNull()
                if (req == null || req.pin.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("PIN tidak boleh kosong"))
                    return@post
                }

                val settings = settingsRepository.getSettings()
                val isValid = if (settings.pinHash.isBlank()) {
                    // Default temporary PIN if not yet set in setup
                    req.pin == "1234"
                } else {
                    PinHasher.verifyPin(req.pin, settings.pinSalt, settings.pinHash)
                }

                if (isValid) {
                    rateLimiter.recordSuccessfulAttempt(clientIp)
                    val session = sessionManager.createSession()
                    settingsRepository.recordAction("LOGIN_SUCCESS", "Login admin dari HP", clientIp)
                    call.respond(LoginResponse(session.token, session.expiresAt))
                } else {
                    val locked = rateLimiter.recordFailedAttempt(clientIp)
                    settingsRepository.recordAction("LOGIN_FAILED", "Percobaan PIN salah", clientIp)
                    if (locked) {
                        val remaining = rateLimiter.getRemainingLockoutSeconds(clientIp)
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            ErrorResponse("PIN salah. Akun terkunci selama 5 menit.", remaining)
                        )
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("PIN salah"))
                    }
                }
            }

            post("/api/auth/logout") {
                val token = extractToken(call)
                if (token != null) {
                    sessionManager.revoke(token)
                }
                call.respond(HttpStatusCode.OK, mapOf("status" to "logged_out"))
            }

            // Authenticated Timer API
            get("/api/status") {
                if (!authenticate(call)) return@get
                call.respond(createStatusDto())
            }

            post("/api/timer/start") {
                if (!authenticate(call)) return@post
                val req = runCatching { call.receive<TimerActionRequest>() }.getOrNull()
                val minutes = req?.minutes ?: 60
                timerEngine.start(minutes)
                settingsRepository.recordAction("TIMER_START", "Mulai timer $minutes menit", call.request.origin.remoteHost)
                call.respond(createStatusDto())
            }

            post("/api/timer/pause") {
                if (!authenticate(call)) return@post
                timerEngine.pause()
                settingsRepository.recordAction("TIMER_PAUSE", "Jeda timer", call.request.origin.remoteHost)
                call.respond(createStatusDto())
            }

            post("/api/timer/resume") {
                if (!authenticate(call)) return@post
                timerEngine.resume()
                settingsRepository.recordAction("TIMER_RESUME", "Lanjutkan timer", call.request.origin.remoteHost)
                call.respond(createStatusDto())
            }

            post("/api/timer/adjust") {
                if (!authenticate(call)) return@post
                val req = runCatching { call.receive<TimerActionRequest>() }.getOrNull()
                val minutes = req?.minutes ?: 0
                timerEngine.adjust(minutes)
                settingsRepository.recordAction("TIMER_ADJUST", "Sesuaikan timer ($minutes menit)", call.request.origin.remoteHost)
                call.respond(createStatusDto())
            }

            post("/api/timer/stop") {
                if (!authenticate(call)) return@post
                timerEngine.stop()
                settingsRepository.recordAction("TIMER_STOP", "Hentikan timer", call.request.origin.remoteHost)
                call.respond(createStatusDto())
            }

            post("/api/timer/reset") {
                if (!authenticate(call)) return@post
                timerEngine.reset()
                settingsRepository.recordAction("TIMER_RESET", "Reset timer", call.request.origin.remoteHost)
                call.respond(createStatusDto())
            }

            // WebSocket
            webSocket("/ws") {
                val token = call.parameters["token"]
                if (!sessionManager.isValid(token)) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                    return@webSocket
                }

                wsHub.addSession(this)
                try {
                    // Send initial state immediately
                    send(Frame.Text(json.encodeToString(WsMessage("state", createStatusDto()))))

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            if (text.contains("ping")) {
                                send(Frame.Text(json.encodeToString(WsMessage("heartbeat"))))
                            }
                        }
                    }
                } finally {
                    wsHub.removeSession(this)
                }
            }
        }
    }

    private suspend fun serveAsset(call: ApplicationCall, assetPath: String, contentType: ContentType) {
        try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            call.respondBytes(bytes, contentType)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.NotFound, "Asset not found")
        }
    }

    private suspend fun authenticate(call: ApplicationCall): Boolean {
        val token = extractToken(call)
        if (!sessionManager.isValid(token)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Sesi tidak valid atau telah kedaluwarsa"))
            return false
        }
        return true
    }

    private fun extractToken(call: ApplicationCall): String? {
        val authHeader = call.request.header("Authorization")
        if (!authHeader.isNullOrBlank() && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            return authHeader.substring(7).trim()
        }
        val customHeader = call.request.header("X-Admin-Token")
        if (!customHeader.isNullOrBlank()) {
            return customHeader.trim()
        }
        return call.request.queryParameters["token"]
    }

    suspend fun createStatusDto(): StatusDto {
        val timerState = timerEngine.state.value
        val settings = settingsRepository.getSettings()
        val deviceStatus = DeviceHealth.getDeviceStatus(context)

        return StatusDto(
            roomName = settings.roomName,
            status = timerState.status.name,
            remainingMs = timerState.remainingMs,
            totalMs = timerState.totalMs,
            serverElapsed = clock.elapsedRealtime(),
            restoredAfterReboot = timerState.restoredAfterReboot,
            appVersion = "1.0.0",
            warnings = deviceStatus.warnings
        )
    }

    private fun startBroadcaster() {
        broadcasterJob = scope.launch {
            // Observe state changes and broadcast
            launch {
                timerEngine.state.collectLatest {
                    wsHub.broadcast(WsMessage("state", createStatusDto()))
                }
            }
            // Observe events (like Finished)
            launch {
                timerEngine.events.collectLatest { event ->
                    when (event) {
                        is TimerEvent.Finished -> {
                            wsHub.broadcast(WsMessage("timer_finished", createStatusDto()))
                        }
                    }
                }
            }
            // Periodic 15-second heartbeat
            heartbeatJob = launch {
                while (isActive) {
                    delay(15_000L)
                    wsHub.broadcast(WsMessage("heartbeat", createStatusDto()))
                }
            }
        }
    }
}
