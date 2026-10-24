package id.karaoke.timerlauncher.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val app = App.instance

    val timerState by app.timerEngine.state.collectAsState()
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = app.settingsRepository.getSettingsSync())

    var isAdbOn by remember { mutableStateOf(DeviceHealth.isAdbEnabled(context)) }
    var ipAddress by remember { mutableStateOf(DeviceHealth.getIpAddress(context)) }
    var showPinDialog by remember { mutableStateOf(false) }

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
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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

            // 3. Bottom Row: Karaoke App Shortcut & Corner Admin QR (if Idle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Karaoke App Shortcut Card
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppShortcutCard(
                        title = "Buka YouTube",
                        subtitle = "Aplikasi Karaoke",
                        packageName = "com.google.android.youtube.tv",
                        fallbackPackage = "com.google.android.youtube",
                        context = context
                    )
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
    }
}

@Composable
fun AppShortcutCard(
    title: String,
    subtitle: String,
    packageName: String,
    fallbackPackage: String,
    context: Context
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(240.dp, 100.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) PrimaryIndigo else SurfaceDark)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(18.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable {
                launchApp(context, packageName, fallbackPackage)
            }
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) Color.White else TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = if (isFocused) Color(0xFFE0E7FF) else TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
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
    val intent = pm.getLeanbackLaunchIntentForPackage(primaryPkg)
        ?: pm.getLaunchIntentForPackage(primaryPkg)
        ?: pm.getLeanbackLaunchIntentForPackage(fallbackPkg)
        ?: pm.getLaunchIntentForPackage(fallbackPkg)

    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Aplikasi YouTube tidak ditemukan pada perangkat ini", Toast.LENGTH_LONG).show()
    }
}
