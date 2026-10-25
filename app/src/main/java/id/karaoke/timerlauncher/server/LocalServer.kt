package id.karaoke.timerlauncher.server

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import id.karaoke.timerlauncher.auth.LoginRateLimiter
import id.karaoke.timerlauncher.auth.PinHasher
import id.karaoke.timerlauncher.auth.SessionManager
import id.karaoke.timerlauncher.device.DeviceHealth
import id.karaoke.timerlauncher.App
import id.karaoke.timerlauncher.server.dto.*
import id.karaoke.timerlauncher.settings.OverlayCorner
import id.karaoke.timerlauncher.settings.OverlaySize
import id.karaoke.timerlauncher.settings.SettingsRepository
import java.util.Base64
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
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.head
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
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
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
                encodeDefaults = true
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
            get("/manifest.json") {
                serveAsset(call, "web/manifest.json", ContentType.parse("application/manifest+json"))
            }
            get("/sw.js") {
                serveAsset(call, "web/sw.js", ContentType.Application.JavaScript)
            }
            get("/icon-192.png") {
                serveAsset(call, "web/icon-192.png", ContentType.Image.PNG)
            }
            get("/icon-512.png") {
                serveAsset(call, "web/icon-512.png", ContentType.Image.PNG)
            }
            get("/icon.svg") {
                serveAsset(call, "web/icon.svg", ContentType.Image.SVG)
            }
            get("/customer") {
                serveAsset(call, "web/customer.html", ContentType.Text.Html)
            }
            get("/customer.css") {
                serveAsset(call, "web/customer.css", ContentType.Text.CSS)
            }
            get("/customer.js") {
                serveAsset(call, "web/customer.js", ContentType.Application.JavaScript)
            }
            get("/player") {
                val v = call.request.queryParameters["v"] ?: ""
                val mute = call.request.queryParameters["mute"] ?: "1"
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <style>
                            * { margin:0; padding:0; box-sizing:border-box; background:#000; overflow:hidden; }
                            html, body, iframe { width:100%; height:100%; position:absolute; top:0; left:0; border:none; }
                        </style>
                    </head>
                    <body>
                        <iframe id="ytplayer"
                                src="https://www.youtube.com/embed/$v?autoplay=1&mute=$mute&controls=0&loop=1&playlist=$v&playsinline=1&modestbranding=1&rel=0&iv_load_policy=3&enablejsapi=1"
                                referrerpolicy="strict-origin-when-cross-origin"
                                allow="autoplay; encrypted-media; fullscreen"
                                allowfullscreen>
                        </iframe>
                    </body>
                    </html>
                """.trimIndent()
                call.respondText(html, ContentType.Text.Html)
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

            // Authenticated Timer & Rental API
            get("/api/status") {
                if (!authenticate(call)) return@get
                call.respond(createStatusDto())
            }

            get("/api/rentals") {
                if (!authenticate(call)) return@get
                call.respond(settingsRepository.getRentals())
            }

            post("/api/rentals/clear") {
                if (!authenticate(call)) return@post
                settingsRepository.clearRentals()
                settingsRepository.recordAction("RENTALS_CLEAR", "Hapus semua riwayat rental", call.request.origin.remoteHost)
                call.respond(mapOf("status" to "cleared"))
            }

            post("/api/rental/customer-name") {
                if (!authenticate(call)) return@post
                val req = runCatching { call.receive<CustomerNameRequest>() }.getOrNull()
                val name = req?.customerName?.trim().takeUnless { it.isNullOrBlank() } ?: "Pelanggan"
                settingsRepository.updateActiveCustomerName(name)
                wsHub.broadcast(WsMessage("state", createStatusDto()))
                call.respond(createStatusDto())
            }

            post("/api/timer/start") {
                if (!authenticate(call)) return@post
                val req = runCatching { call.receive<TimerActionRequest>() }.getOrNull()
                val minutes = req?.minutes ?: 60
                val customerName = req?.customerName?.trim().takeUnless { it.isNullOrBlank() } ?: "Pelanggan"
                val settings = settingsRepository.getSettings()

                settingsRepository.startRental(customerName, settings.roomName, minutes)
                timerEngine.start(minutes)
                settingsRepository.recordAction("TIMER_START", "Mulai timer $minutes menit ($customerName)", call.request.origin.remoteHost)
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
                if (minutes > 0) {
                    settingsRepository.extendRental(minutes)
                }
                timerEngine.adjust(minutes)
                settingsRepository.recordAction("TIMER_ADJUST", "Sesuaikan timer ($minutes menit)", call.request.origin.remoteHost)
                call.respond(createStatusDto())
            }

            post("/api/timer/stop") {
                if (!authenticate(call)) return@post
                timerEngine.stop()
                settingsRepository.finishRental("STOPPED")
                settingsRepository.recordAction("TIMER_STOP", "Hentikan timer", call.request.origin.remoteHost)
                call.respond(createStatusDto())
            }

            post("/api/timer/reset") {
                if (!authenticate(call)) return@post
                timerEngine.reset()
                settingsRepository.finishRental("RESET")
                settingsRepository.recordAction("TIMER_RESET", "Reset timer", call.request.origin.remoteHost)
                call.respond(createStatusDto())
            }

            // Customer Remote Endpoints (Accessible by room guests)
            get("/api/customer/status") {
                call.respond(createCustomerStatusDto())
            }

            post("/api/customer/request") {
                val req = runCatching { call.receive<CustomerActionRequest>() }.getOrNull()
                if (req == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Payload tidak valid"))
                    return@post
                }
                val created = settingsRepository.submitCustomerRequest(
                    type = req.type,
                    requestedMinutes = req.requestedMinutes,
                    message = req.message
                )
                settingsRepository.recordAction(
                    "CUSTOMER_REQUEST",
                    "Permintaan pelanggan: ${req.type} (${req.requestedMinutes}m)",
                    call.request.origin.remoteHost
                )
                wsHub.broadcast(WsMessage("state", createStatusDto()))
                call.respond(created)
            }

            post("/api/customer/volume") {
                val req = runCatching { call.receive<CustomerVolumeRequest>() }.getOrNull()
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null && req != null) {
                    when (req.action.uppercase()) {
                        "UP" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        "DOWN" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        "MUTE" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                    }
                }
                call.respond(mapOf("status" to "ok"))
            }

            post("/api/customer/open-youtube") {
                val currentStatus = timerEngine.state.value.status
                if (currentStatus != id.karaoke.timerlauncher.timer.TimerStatus.RUNNING) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Waktu belum berjalan. Sesi karaoke belum aktif.")
                    )
                    return@post
                }
                try {
                    val pm = context.packageManager
                    val ytIntent = pm.getLaunchIntentForPackage("com.google.android.youtube.tv")
                        ?: pm.getLaunchIntentForPackage("com.google.android.youtube")
                        ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
                    ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(ytIntent)
                    settingsRepository.recordAction("CUSTOMER_YOUTUBE", "Pelanggan membuka YouTube dari remote HP", call.request.origin.remoteHost)
                    call.respond(mapOf("status" to "ok"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Gagal membuka YouTube: ${e.message}"))
                }
            }

            post("/api/customer/nav") {
                val currentStatus = timerEngine.state.value.status
                if (currentStatus != id.karaoke.timerlauncher.timer.TimerStatus.RUNNING) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Waktu belum berjalan. Sesi karaoke belum aktif.")
                    )
                    return@post
                }
                val req = runCatching { call.receive<CustomerNavRequest>() }.getOrNull()
                if (req == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Payload tidak valid"))
                    return@post
                }
                val handled = id.karaoke.timerlauncher.service.TvNavigationController.sendNavKey(req.action)
                call.respond(mapOf("status" to if (handled) "ok" else "failed"))
            }

            // Admin: Respond to Customer Request
            post("/api/admin/customer-request/respond") {
                if (!authenticate(call)) return@post
                val req = runCatching { call.receive<RespondCustomerRequest>() }.getOrNull()
                if (req == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Payload tidak valid"))
                    return@post
                }

                val pending = settingsRepository.getPendingCustomerRequest()
                if (pending != null && pending.id == req.requestId) {
                    if (req.action.equals("ACCEPT", ignoreCase = true)) {
                        if (pending.type == "EXTENSION" && pending.requestedMinutes > 0) {
                            settingsRepository.extendRental(pending.requestedMinutes)
                            timerEngine.adjust(pending.requestedMinutes)
                            settingsRepository.recordAction(
                                "CUSTOMER_REQUEST_ACCEPTED",
                                "Admin menyetujui tambah ${pending.requestedMinutes} menit dari pelanggan",
                                call.request.origin.remoteHost
                            )
                        } else {
                            settingsRepository.recordAction(
                                "CUSTOMER_REQUEST_ACCEPTED",
                                "Admin melayani panggilan ${pending.type}",
                                call.request.origin.remoteHost
                            )
                        }
                    } else {
                        settingsRepository.recordAction(
                            "CUSTOMER_REQUEST_REJECTED",
                            "Admin menolak permintaan ${pending.type}",
                            call.request.origin.remoteHost
                        )
                    }
                }
                settingsRepository.clearCustomerRequest(req.requestId)
                wsHub.broadcast(WsMessage("state", createStatusDto()))
                call.respond(createStatusDto())
            }

            // Apps Management Endpoints
            get("/api/apps") {
                if (!authenticate(call)) return@get
                val settings = settingsRepository.getSettings()
                val apps = App.instance.appDiscoveryManager.getInstalledApps(settings.allowedPackages).map {
                    AppDto(
                        packageName = it.packageName,
                        appName = it.appName,
                        isLeanback = it.isLeanback,
                        isAllowed = it.isAllowed
                    )
                }
                call.respond(apps)
            }

            post("/api/apps/toggle") {
                if (!authenticate(call)) return@post
                val req = call.receive<ToggleAppRequest>()
                settingsRepository.toggleAllowedPackage(req.packageName, req.isAllowed)
                settingsRepository.recordAction(
                    "APP_TOGGLE",
                    "Ubah status aplikasi ${req.packageName} menjadi ${if (req.isAllowed) "DIIZINKAN" else "DIBLOKIR"}",
                    call.request.origin.remoteHost
                )
                val settings = settingsRepository.getSettings()
                val apps = App.instance.appDiscoveryManager.getInstalledApps(settings.allowedPackages).map {
                    AppDto(
                        packageName = it.packageName,
                        appName = it.appName,
                        isLeanback = it.isLeanback,
                        isAllowed = it.isAllowed
                    )
                }
                call.respond(apps)
            }

            // Settings Management Endpoints
            get("/api/settings") {
                if (!authenticate(call)) return@get
                call.respond(buildSettingsDto())
            }

            post("/api/settings") {
                if (!authenticate(call)) return@post
                val req = runCatching { call.receive<UpdateSettingsRequest>() }.getOrNull()
                if (req == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Payload tidak valid"))
                    return@post
                }

                val corner = req.overlayCorner?.let {
                    runCatching { OverlayCorner.valueOf(it) }.getOrNull()
                }
                val size = req.overlaySize?.let {
                    runCatching { OverlaySize.valueOf(it) }.getOrNull()
                }

                if (!req.newPin.isNullOrBlank()) {
                    val pin = req.newPin.trim()
                    if (pin.length < 4 || pin.length > 6 || !pin.all { it.isDigit() }) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("PIN harus berupa 4-6 digit angka"))
                        return@post
                    }
                    val salt = PinHasher.generateSalt()
                    val hash = PinHasher.hashPin(pin, salt)
                    settingsRepository.setPin(hash, salt)
                    settingsRepository.recordAction("SETTINGS_PIN_CHANGE", "Ubah PIN keamanan dari web admin", call.request.origin.remoteHost)
                }

                settingsRepository.updateGeneralSettings(
                    roomName = req.roomName,
                    hourlyRate = req.hourlyRate,
                    corner = corner,
                    size = size,
                    standbyBgMode = req.standbyBgMode,
                    standbyYoutubeUrl = req.standbyYoutubeUrl,
                    standbyYoutubeMute = req.standbyYoutubeMute,
                    standbyVideoMute = req.standbyVideoMute,
                    operatorPhone = req.operatorPhone
                )
                settingsRepository.recordAction(
                    "SETTINGS_UPDATE",
                    "Perbarui konfigurasi ruangan dari web admin",
                    call.request.origin.remoteHost
                )

                wsHub.broadcast(WsMessage("state", createStatusDto()))
                call.respond(buildSettingsDto())
            }

            post("/api/settings/background-image") {
                if (!authenticate(call)) return@post
                val req = runCatching { call.receive<UploadImageRequest>() }.getOrNull()
                if (req == null || req.imageBase64.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Gambar tidak valid"))
                    return@post
                }

                try {
                    // Extract raw base64 if prefixed with data:image/...;base64,
                    val pureBase64 = if (req.imageBase64.contains(",")) {
                        req.imageBase64.substringAfter(",")
                    } else {
                        req.imageBase64
                    }
                    val bytes = Base64.getDecoder().decode(pureBase64)
                    settingsRepository.saveStandbyBgImage(bytes)
                    settingsRepository.recordAction("BG_IMAGE_UPLOAD", "Unggah gambar background layar siap", call.request.origin.remoteHost)

                    wsHub.broadcast(WsMessage("state", createStatusDto()))
                    call.respond(buildSettingsDto())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Gagal memproses gambar: ${e.message}"))
                }
            }

            get("/api/settings/background-image") {
                val file = settingsRepository.getStandbyBgFile()
                if (!file.exists()) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Gambar latar belum diatur"))
                    return@get
                }
                call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
            }

            post("/api/settings/background-image/delete") {
                if (!authenticate(call)) return@post
                settingsRepository.deleteStandbyBgImage()
                settingsRepository.recordAction("BG_IMAGE_DELETE", "Hapus gambar background layar siap", call.request.origin.remoteHost)

                wsHub.broadcast(WsMessage("state", createStatusDto()))
                call.respond(buildSettingsDto())
            }

            post("/api/settings/background-video") {
                if (!authenticate(call)) return@post
                try {
                    val stream = call.receiveChannel().toInputStream()
                    settingsRepository.saveStandbyVideoStream(stream)
                    settingsRepository.recordAction("BG_VIDEO_UPLOAD", "Unggah video background layar siap", call.request.origin.remoteHost)

                    wsHub.broadcast(WsMessage("state", createStatusDto()))
                    call.respond(buildSettingsDto())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Gagal memproses video: ${e.message}"))
                }
            }

            get("/api/settings/background-video") {
                val file = settingsRepository.getStandbyVideoFile()
                if (!file.exists()) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Video latar belum diatur"))
                    return@get
                }
                call.respondFile(file)
            }

            head("/api/settings/background-video") {
                val file = settingsRepository.getStandbyVideoFile()
                if (!file.exists()) {
                    call.respond(HttpStatusCode.NotFound)
                    return@head
                }
                call.respondFile(file)
            }

            post("/api/settings/background-video/delete") {
                if (!authenticate(call)) return@post
                settingsRepository.deleteStandbyVideo()
                settingsRepository.recordAction("BG_VIDEO_DELETE", "Hapus video background layar siap", call.request.origin.remoteHost)

                wsHub.broadcast(WsMessage("state", createStatusDto()))
                call.respond(buildSettingsDto())
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

    suspend fun buildSettingsDto(): SettingsDto {
        val settings = settingsRepository.getSettings()
        return SettingsDto(
            roomName = settings.roomName,
            hourlyRate = settings.hourlyRate,
            overlayCorner = settings.overlayCorner.name,
            overlaySize = settings.overlaySize.name,
            standbyBgMode = settings.standbyBgMode,
            standbyYoutubeUrl = settings.standbyYoutubeUrl,
            standbyYoutubeMute = settings.standbyYoutubeMute,
            standbyVideoMute = settings.standbyVideoMute,
            hasCustomBgImage = settingsRepository.getStandbyBgFile().exists(),
            hasCustomBgVideo = settingsRepository.getStandbyVideoFile().exists(),
            operatorPhone = settings.operatorPhone
        )
    }

    suspend fun createStatusDto(): StatusDto {
        val timerState = timerEngine.state.value
        val settings = settingsRepository.getSettings()
        val deviceStatus = DeviceHealth.getDeviceStatus(context)
        val activeCustomer = settingsRepository.getActiveCustomerName()
        val pendingCustomerReq = settingsRepository.getPendingCustomerRequest()

        return StatusDto(
            roomName = settings.roomName,
            status = timerState.status.name,
            remainingMs = timerState.remainingMs,
            totalMs = timerState.totalMs,
            serverElapsed = clock.elapsedRealtime(),
            restoredAfterReboot = timerState.restoredAfterReboot,
            appVersion = "1.0.0",
            warnings = deviceStatus.warnings,
            customerName = activeCustomer,
            pendingCustomerRequest = pendingCustomerReq
        )
    }

    suspend fun createCustomerStatusDto(): CustomerStatusDto {
        val timerState = timerEngine.state.value
        val settings = settingsRepository.getSettings()
        val activeCustomer = settingsRepository.getActiveCustomerName()
        val pendingCustomerReq = settingsRepository.getPendingCustomerRequest()

        val totalSeconds = (timerState.remainingMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val formatted = if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }

        return CustomerStatusDto(
            roomName = settings.roomName,
            status = timerState.status.name,
            remainingMs = timerState.remainingMs,
            totalMs = timerState.totalMs,
            formattedRemaining = formatted,
            customerName = activeCustomer,
            operatorPhone = settings.operatorPhone,
            pendingRequest = pendingCustomerReq
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
            // Observe pending customer requests
            launch {
                settingsRepository.pendingCustomerRequest.collectLatest {
                    wsHub.broadcast(WsMessage("state", createStatusDto()))
                }
            }
            // Observe events (like Finished)
            launch {
                timerEngine.events.collectLatest { event ->
                    when (event) {
                        is TimerEvent.Finished -> {
                            settingsRepository.finishRental("COMPLETED")
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
