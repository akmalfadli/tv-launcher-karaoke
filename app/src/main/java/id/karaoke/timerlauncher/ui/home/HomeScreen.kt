package id.karaoke.timerlauncher.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.graphics.BitmapFactory
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.karaoke.timerlauncher.App
import id.karaoke.timerlauncher.device.DeviceHealth
import id.karaoke.timerlauncher.timer.TimerStatus
import id.karaoke.timerlauncher.ui.PinLockDialog
import id.karaoke.timerlauncher.ui.QrCodeGenerator
import id.karaoke.timerlauncher.ui.theme.AccentAmber
import id.karaoke.timerlauncher.ui.theme.BgDark
import id.karaoke.timerlauncher.ui.theme.DangerRed
import id.karaoke.timerlauncher.ui.theme.PrimaryIndigo
import id.karaoke.timerlauncher.ui.theme.SuccessGreen
import id.karaoke.timerlauncher.ui.theme.SurfaceDark
import id.karaoke.timerlauncher.ui.theme.TextMuted
import id.karaoke.timerlauncher.ui.theme.TextPrimary
import kotlinx.coroutines.delay

fun extractYoutubeVideoId(url: String): String? {
    if (url.isBlank()) return null
    val clean = url.trim()
    val pattern = Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?v=|embed/|shorts/|live/|v/))([a-zA-Z0-9_-]{11})""")
    val match = pattern.find(clean)
    if (match != null) {
        return match.groupValues[1]
    }
    if (clean.length == 11 && clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        return clean
    }
    return null
}

private fun buildYoutubeHtml(videoId: String, isMuted: Boolean): String {
    val muteVal = if (isMuted) 1 else 0
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin:0; padding:0; box-sizing:border-box; background:#000; overflow:hidden; }
                html, body { width:100%; height:100%; background:#000; }
                #player { width:100%; height:100%; position:absolute; top:0; left:0; border:none; }
                iframe { width:100%; height:100%; position:absolute; top:0; left:0; border:none; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
                var player;
                var apiReady = false;

                function onYouTubeIframeAPIReady() {
                    apiReady = true;
                    console.log("KaraokeYT: YouTube IFrame API Ready");
                    player = new YT.Player('player', {
                        width: '100%',
                        height: '100%',
                        videoId: '$videoId',
                        playerVars: {
                            'autoplay': 1,
                            'controls': 0,
                            'disablekb': 1,
                            'fs': 0,
                            'loop': 1,
                            'playlist': '$videoId',
                            'modestbranding': 1,
                            'rel': 0,
                            'playsinline': 1,
                            'enablejsapi': 1,
                            'origin': 'https://www.youtube.com'
                        },
                        events: {
                            'onReady': onPlayerReady,
                            'onStateChange': onPlayerStateChange,
                            'onError': onPlayerError
                        }
                    });
                }

                function onPlayerReady(event) {
                    console.log("KaraokeYT: Player ready, starting playback");
                    if ($isMuted) {
                        event.target.mute();
                    } else {
                        event.target.unMute();
                    }
                    event.target.playVideo();
                }

                function onPlayerStateChange(event) {
                    console.log("KaraokeYT: Player state changed to " + event.data);
                    if (event.data === 0) { // ENDED -> loop
                        if (player && typeof player.playVideo === 'function') {
                            player.playVideo();
                        }
                    }
                }

                function onPlayerError(event) {
                    console.error("KaraokeYT: Player error " + event.data + ", attempting fallback iframe");
                    loadFallbackIframe();
                }

                function loadFallbackIframe() {
                    var container = document.body;
                    container.innerHTML = '<iframe src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&mute=$muteVal&controls=0&loop=1&playlist=$videoId&playsinline=1&modestbranding=1&rel=0" allow="autoplay; encrypted-media"></iframe>';
                }

                // Fallback timeout if API script fails to load in 3.5s
                setTimeout(function() {
                    if (!apiReady) {
                        console.log("KaraokeYT: API load timeout, using direct fallback iframe");
                        loadFallbackIframe();
                    }
                }, 3500);
            </script>
        </body>
        </html>
    """.trimIndent()
}

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val app = App.instance

    val timerState by app.timerEngine.state.collectAsState()
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = app.settingsRepository.getSettingsSync())

    var isAdbOn by remember { mutableStateOf(DeviceHealth.isAdbEnabled(context)) }
    var ipAddress by remember { mutableStateOf(DeviceHealth.getIpAddress(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showLockedNotice by remember { mutableStateOf(false) }

    // Recheck ADB & IP every 5s
    LaunchedEffect(Unit) {
        while (true) {
            isAdbOn = DeviceHealth.isAdbEnabled(context)
            ipAddress = DeviceHealth.getIpAddress(context)
            delay(5000L)
        }
    }

    val adminUrl = "http://$ipAddress:8080"
    val qrBitmap = remember(adminUrl) {
        QrCodeGenerator.generateQrBitmap(adminUrl, 200)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Standby Background: Active strictly when timer is IDLE
        if (timerState.isIdle) {
            when (settings.standbyBgMode) {
                "IMAGE" -> {
                    val bgBitmap = remember(settings.bgImageTimestamp, settings.standbyBgMode) {
                        val file = app.settingsRepository.getStandbyBgFile()
                        if (file.exists()) {
                            runCatching {
                                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                            }.getOrNull()
                        } else null
                    }
                    if (bgBitmap != null) {
                        Image(
                            bitmap = bgBitmap,
                            contentDescription = "Standby Background",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xD90E0F12),
                                            Color(0xB30E0F12),
                                            Color(0xE60E0F12)
                                        )
                                    )
                                )
                        )
                    }
                }
                "VIDEO" -> {
                    val videoFile = remember(settings.bgVideoTimestamp, settings.standbyBgMode) {
                        val file = app.settingsRepository.getStandbyVideoFile()
                        if (file.exists()) file else null
                    }
                    if (videoFile != null) {
                        AndroidView(
                            factory = { ctx ->
                                android.widget.VideoView(ctx).apply {
                                    setVideoPath(videoFile.absolutePath)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        if (settings.standbyVideoMute) {
                                            mp.setVolume(0f, 0f)
                                        } else {
                                            mp.setVolume(1f, 1f)
                                        }
                                        start()
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        android.util.Log.e("KaraokeVideo", "Video playback error: $what, $extra")
                                        true
                                    }
                                }
                            },
                            update = { videoView ->
                                val currentTag = "${videoFile.lastModified()}:${settings.standbyVideoMute}"
                                if (videoView.tag != currentTag) {
                                    videoView.tag = currentTag
                                    videoView.setVideoPath(videoFile.absolutePath)
                                    videoView.setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        if (settings.standbyVideoMute) {
                                            mp.setVolume(0f, 0f)
                                        } else {
                                            mp.setVolume(1f, 1f)
                                        }
                                        videoView.start()
                                    }
                                }
                            },
                            onRelease = { videoView ->
                                videoView.stopPlayback()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xD90E0F12),
                                            Color(0x800E0F12),
                                            Color(0xE60E0F12)
                                        )
                                    )
                                )
                        )
                    }
                }
                "YOUTUBE" -> {
                    val videoId = remember(settings.standbyYoutubeUrl) {
                        extractYoutubeVideoId(settings.standbyYoutubeUrl)
                    }
                    if (!videoId.isNullOrBlank()) {
                        AndroidView(
                            factory = { ctx ->
                                YouTubePlayerView(ctx).apply {
                                    enableAutomaticInitialization = false
                                    val options = IFramePlayerOptions.Builder()
                                        .controls(0)
                                        .rel(0)
                                        .ivLoadPolicy(3)
                                        .build()

                                    initialize(object : AbstractYouTubePlayerListener() {
                                        override fun onReady(youTubePlayer: YouTubePlayer) {
                                            android.util.Log.d("KaraokeYT", "YouTubePlayer onReady: $videoId")
                                            if (settings.standbyYoutubeMute) {
                                                youTubePlayer.mute()
                                            } else {
                                                youTubePlayer.unMute()
                                            }
                                            youTubePlayer.loadVideo(videoId, 0f)
                                        }

                                        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                            android.util.Log.d("KaraokeYT", "YouTubePlayer state: $state")
                                            if (state == PlayerConstants.PlayerState.ENDED) {
                                                youTubePlayer.seekTo(0f)
                                                youTubePlayer.play()
                                            }
                                        }

                                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                            android.util.Log.e("KaraokeYT", "YouTubePlayer error: $error")
                                        }
                                    }, options)
                                }
                            },
                            update = { playerView ->
                                val key = "$videoId:${settings.standbyYoutubeMute}"
                                if (playerView.tag != key) {
                                    playerView.tag = key
                                    playerView.getYouTubePlayerWhenReady(object : YouTubePlayerCallback {
                                        override fun onYouTubePlayer(youTubePlayer: YouTubePlayer) {
                                            android.util.Log.d("KaraokeYT", "Updating video in ready player: $videoId")
                                            if (settings.standbyYoutubeMute) {
                                                youTubePlayer.mute()
                                            } else {
                                                youTubePlayer.unMute()
                                            }
                                            youTubePlayer.loadVideo(videoId, 0f)
                                        }
                                    })
                                }
                            },
                            onRelease = { playerView ->
                                android.util.Log.d("KaraokeYT", "Releasing YouTubePlayerView")
                                playerView.release()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x880E0F12))
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Room name, ADB warning, and Settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = settings.roomName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Karaoke TV Launcher",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }

                if (isAdbOn) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33EF4444))
                            .border(1.dp, DangerRed, RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚠️ Debugging ADB aktif! Segera matikan di opsi pengembang demi keamanan.",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HomeDpadButton(
                    text = "⚙ Pengaturan",
                    onClick = { showPinDialog = true }
                )
            }

            // 2. Center Hero: Timer Display & Status
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Badge
                val (badgeBg, badgeTextCol, statusText) = when (timerState.status) {
                    TimerStatus.IDLE -> Triple(Color(0x22FFFFFF), TextMuted, "SIAP")
                    TimerStatus.RUNNING -> Triple(Color(0x3310B981), SuccessGreen, "BERJALAN")
                    TimerStatus.PAUSED -> Triple(Color(0x33F59E0B), AccentAmber, "DIJEDA")
                    TimerStatus.FINISHED -> Triple(Color(0x44EF4444), DangerRed, "WAKTU HABIS")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeBg)
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        color = badgeTextCol,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Big Timer digits
                Text(
                    text = timerState.formattedRemaining,
                    fontSize = 104.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = when (timerState.status) {
                        TimerStatus.FINISHED -> DangerRed
                        TimerStatus.PAUSED -> AccentAmber
                        else -> Color.White
                    },
                    letterSpacing = (-2).sp
                )

                if (timerState.isFinished) {
                    Text(
                        text = "Silakan hubungi operator untuk menambah waktu sewa",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }

            // 3. Bottom Row: Allowed Apps Shortcuts & Corner Admin QR (if Idle)
            val installedApps = remember(settings.allowedPackages) {
                app.appDiscoveryManager.getInstalledApps(settings.allowedPackages)
            }
            val allowedApps = remember(installedApps) {
                installedApps.filter { it.isAllowed }
            }
            val isSessionActive = timerState.isRunning || timerState.isPaused
            val isLocked = !isSessionActive

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Allowed Apps Shortcut Cards
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (allowedApps.isNotEmpty()) {
                        for (item in allowedApps.take(4)) {
                            AppShortcutCard(
                                title = item.appName,
                                subtitle = if (isLocked) "Terkunci • Hubungi Kasir" else (if (item.isLeanback) "Aplikasi TV" else "Aplikasi Karaoke"),
                                iconBitmap = item.iconBitmap,
                                isLocked = isLocked,
                                onClick = {
                                    if (isLocked) {
                                        showLockedNotice = true
                                    } else {
                                        launchApp(context, item.packageName, "")
                                    }
                                }
                            )
                        }
                    } else {
                        AppShortcutCard(
                            title = "Buka YouTube",
                            subtitle = if (isLocked) "Terkunci • Hubungi Kasir" else "Aplikasi Karaoke",
                            iconBitmap = null,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    showLockedNotice = true
                                } else {
                                    launchApp(context, "com.google.android.youtube.tv", "org.smarttube.stable")
                                }
                            }
                        )
                    }
                }

                // Corner QR Code (visible only when timer is IDLE)
                if (timerState.isIdle) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark)
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(4.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "Admin QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column {
                            Text("Remote Operator", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(adminUrl, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentAmber)
                        }
                    }
                }
            }
        }

        // Admin PIN lock modal
        if (showPinDialog) {
            PinLockDialog(
                onDismiss = { showPinDialog = false },
                onSuccess = {
                    showPinDialog = false
                    onOpenSettings()
                }
            )
        }

        // App Locked Notice modal
        if (showLockedNotice) {
            AppLockedDialog(
                roomName = settings.roomName,
                onDismiss = { showLockedNotice = false }
            )
        }
    }
}

@Composable
fun AppShortcutCard(
    title: String,
    subtitle: String,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val cardBg = when {
        isFocused && isLocked -> Color(0x33EF4444)
        isFocused -> PrimaryIndigo
        isLocked -> Color(0x1A1E2028)
        else -> SurfaceDark
    }

    val cardBorderColor = when {
        isFocused && isLocked -> DangerRed
        isFocused -> Color.White
        isLocked -> Color(0x22FFFFFF)
        else -> Color(0x33FFFFFF)
    }

    Box(
        modifier = Modifier
            .size(240.dp, 100.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = title,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📺", fontSize = 24.sp)
                    }
                }

                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DangerRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔒",
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Column {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) Color.White else if (isLocked) Color(0xFFD1D5DB) else TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = if (isLocked) {
                        if (isFocused) Color(0xFFFCA5A5) else DangerRed
                    } else {
                        if (isFocused) Color(0xFFE0E7FF) else TextMuted
                    },
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun AppLockedDialog(
    roomName: String,
    onDismiss: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        delay(6000L)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(2.dp, DangerRed, RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x33EF4444)),
                contentAlignment = Alignment.Center
            ) {
                Text("🔒", fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Akses Karaoke Belum Dibuka",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Sesi karaoke di $roomName belum dimulai atau waktu sewa telah habis.\n\nSilakan hubungi kasir atau operator untuk mengaktifkan sesi karaoke.",
                fontSize = 15.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isFocused) Color.White else Color(0x22FFFFFF))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) AccentAmber else Color(0x33FFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .focusRequester(focusRequester)
                    .focusable(interactionSource = interactionSource)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Mengerti (Tutup)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) Color.Black else Color.White
                )
            }
        }
    }
}

@Composable
fun HomeDpadButton(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else Color(0x1AFFFFFF))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) AccentAmber else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) Color.Black else TextPrimary
        )
    }
}

private fun launchApp(context: Context, primaryPkg: String, fallbackPkg: String) {
    val pm = context.packageManager
    val candidates = listOf(primaryPkg, "org.smarttube.stable", fallbackPkg)
    var intent: Intent? = null
    for (pkg in candidates) {
        intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) break
    }

    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Aplikasi YouTube tidak ditemukan pada perangkat ini", Toast.LENGTH_LONG).show()
    }
}
