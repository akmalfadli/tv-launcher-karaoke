package id.karaoke.timerlauncher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import id.karaoke.timerlauncher.settings.OverlayCorner
import id.karaoke.timerlauncher.settings.OverlaySize
import id.karaoke.timerlauncher.ui.QrCodeGenerator
import id.karaoke.timerlauncher.ui.setup.FocusableButton
import id.karaoke.timerlauncher.ui.theme.AccentAmber
import id.karaoke.timerlauncher.ui.theme.BgDark
import id.karaoke.timerlauncher.ui.theme.DangerRed
import id.karaoke.timerlauncher.ui.theme.SuccessGreen
import id.karaoke.timerlauncher.ui.theme.SurfaceDark
import id.karaoke.timerlauncher.ui.theme.TextMuted
import id.karaoke.timerlauncher.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = App.instance
    val scope = rememberCoroutineScope()
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = app.settingsRepository.getSettingsSync())
    val deviceStatus = remember { DeviceHealth.getDeviceStatus(context) }

    val adminUrl = "http://${deviceStatus.ipAddress}:8080"
    val qrBitmap = remember(adminUrl) {
        QrCodeGenerator.generateQrBitmap(adminUrl, 260)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .width(960.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Left Column: Options
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Pengaturan Ruangan",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = settings.roomName,
                    fontSize = 14.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Posisi Overlay
                Text("Posisi Overlay Timer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMuted)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                ) {
                    val corners = listOf(
                        OverlayCorner.TOP_RIGHT to "Kanan Atas",
                        OverlayCorner.TOP_LEFT to "Kiri Atas",
                        OverlayCorner.BOTTOM_RIGHT to "Kanan Bawah"
                    )
                    for ((c, label) in corners) {
                        FocusableButton(
                            text = label,
                            isPrimary = settings.overlayCorner == c,
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.updateOverlayConfig(c, settings.overlaySize)
                                }
                            }
                        )
                    }
                }

                // Ukuran Overlay
                Text("Ukuran Overlay Timer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMuted)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                ) {
                    val sizes = listOf(
                        OverlaySize.SMALL to "Kecil",
                        OverlaySize.MEDIUM to "Sedang",
                        OverlaySize.LARGE to "Besar"
                    )
                    for ((s, label) in sizes) {
                        FocusableButton(
                            text = label,
                            isPrimary = settings.overlaySize == s,
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.updateOverlayConfig(settings.overlayCorner, s)
                                }
                            }
                        )
                    }
                }

                // Aplikasi yang Diizinkan di TV
                val installedApps = remember(settings.allowedPackages) {
                    app.appDiscoveryManager.getInstalledApps(settings.allowedPackages)
                }

                Text("Aplikasi yang Diizinkan di TV", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMuted)
                Text(
                    "Pilih aplikasi yang dapat dibuka oleh pelanggan pada layar beranda:",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    items(installedApps.size) { idx ->
                        val item = installedApps[idx]
                        FocusableButton(
                            text = "${if (item.isAllowed) "✓ " else "+ "}${item.appName}",
                            isPrimary = item.isAllowed,
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.toggleAllowedPackage(item.packageName, !item.isAllowed)
                                }
                            }
                        )
                    }
                }

                // Status Sistem
                Text("Status Sistem & Keamanan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMuted)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x11FFFFFF))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Izin Overlay", fontSize = 13.sp, color = TextPrimary)
                        Text(
                            if (deviceStatus.isOverlayPermissionGranted) "Aktif (OK)" else "Belum Aktif",
                            fontSize = 13.sp,
                            color = if (deviceStatus.isOverlayPermissionGranted) SuccessGreen else DangerRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status ADB", fontSize = 13.sp, color = TextPrimary)
                        Text(
                            if (deviceStatus.isAdbEnabled) "Aktif (Wajib dimatikan!)" else "Nonaktif (Aman)",
                            fontSize = 13.sp,
                            color = if (deviceStatus.isAdbEnabled) DangerRed else SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FocusableButton(
                        text = "Kembali ke Beranda",
                        isPrimary = true,
                        onClick = onBack
                    )
                    FocusableButton(
                        text = "Buka Pengaturan TV",
                        isPrimary = false,
                        onClick = {
                            runCatching {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            // Right Column: Admin QR
            Column(
                modifier = Modifier
                    .weight(0.7f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0DFFFFFF))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Akses HP Operator",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Pindai untuk membuka remote",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "QR Code Admin",
                        modifier = Modifier.size(140.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = adminUrl,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAmber
                )
            }
        }
    }
}
