package id.karaoke.timerlauncher.ui.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.karaoke.timerlauncher.App
import id.karaoke.timerlauncher.auth.PinHasher
import id.karaoke.timerlauncher.device.DeviceHealth
import id.karaoke.timerlauncher.ui.DpadActionButton
import id.karaoke.timerlauncher.ui.DpadKeyButton
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
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val app = App.instance
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(1) }
    var hasOverlay by remember { mutableStateOf(DeviceHealth.hasOverlayPermission(context)) }
    var isBatteryIgnored by remember { mutableStateOf(DeviceHealth.isIgnoringBatteryOptimizations(context)) }
    var roomName by remember { mutableStateOf("Ruang 1") }
    var pinValue by remember { mutableStateOf("") }
    var pinConfirmValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var ipAddress by remember { mutableStateOf(DeviceHealth.getIpAddress(context)) }

    // Periodic check for permission activation
    LaunchedEffect(Unit) {
        while (true) {
            hasOverlay = DeviceHealth.hasOverlayPermission(context)
            isBatteryIgnored = DeviceHealth.isIgnoringBatteryOptimizations(context)
            ipAddress = DeviceHealth.getIpAddress(context)
            delay(1500L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(820.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Konfigurasi Perangkat (Langkah $step dari 4)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Sony Bravia XR X90",
                    fontSize = 14.sp,
                    color = PrimaryIndigo,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                1 -> {
                    // Step 1: Izin Overlay & Optimasi Baterai
                    Text(
                        text = "Izin Sistem & Optimasi Baterai",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Aplikasi memerlukan izin overlay agar timer dapat tampil di atas YouTube.",
                        fontSize = 14.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x11FFFFFF))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Izin Tampil di Atas Aplikasi Lain", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                if (hasOverlay) "Status: Diizinkan (OK)" else "Status: Belum diizinkan. Jalankan skrip ADB.",
                                color = if (hasOverlay) SuccessGreen else DangerRed,
                                fontSize = 13.sp
                            )
                        }
                        if (!hasOverlay) {
                            Text(
                                "adb shell appops set id.karaoke.timerlauncher SYSTEM_ALERT_WINDOW allow",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = AccentAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x11FFFFFF))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Optimasi Baterai Latar Belakang", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                if (isBatteryIgnored) "Status: Dikecualikan (Aman)" else "Status: Aktif",
                                color = if (isBatteryIgnored) SuccessGreen else AccentAmber,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FocusableButton(
                            text = "Lanjutkan",
                            isPrimary = true,
                            onClick = { step = 2 }
                        )
                    }
                }

                2 -> {
                    // Step 2: Nama Ruangan
                    Text(
                        text = "Pilih Nama Ruangan",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Nama ini akan ditampilkan pada layar TV dan halaman kontrol operator.",
                        fontSize = 14.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                    )

                    val presets = listOf("Ruang 1", "Ruang 2", "Ruang 3", "Ruang 4", "Ruang VIP 1", "Ruang VIP 2")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        for (name in presets) {
                            val isSelected = roomName == name
                            FocusableButton(
                                text = name,
                                isPrimary = isSelected,
                                onClick = { roomName = name }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FocusableButton("Kembali", isPrimary = false, onClick = { step = 1 })
                        FocusableButton("Lanjutkan", isPrimary = true, onClick = { step = 3 })
                    }
                }

                3 -> {
                    // Step 3: Buat PIN Admin
                    Text(
                        text = "Buat PIN Admin",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "PIN 4 digit ini digunakan operator untuk login dari HP.",
                        fontSize = 14.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    )

                    // Dots display
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < pinValue.length
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isFilled) PrimaryIndigo else Color(0x22FFFFFF))
                                    .border(1.dp, if (isFilled) PrimaryIndigo else Color(0x44FFFFFF), RoundedCornerShape(9.dp))
                            )
                        }
                    }

                    if (pinError != null) {
                        Text(pinError!!, color = DangerRed, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }

                    // Keypad
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫")
                    )

                    for (row in rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                            for (key in row) {
                                DpadKeyButton(
                                    text = key,
                                    onClick = {
                                        when (key) {
                                            "C" -> pinValue = ""
                                            "⌫" -> if (pinValue.isNotEmpty()) pinValue = pinValue.dropLast(1)
                                            else -> if (pinValue.length < 4) pinValue += key
                                        }
                                        pinError = null
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FocusableButton("Kembali", isPrimary = false, onClick = { step = 2 })
                        FocusableButton(
                            text = "Simpan & Lanjut",
                            isPrimary = true,
                            onClick = {
                                if (pinValue.length < 4) {
                                    pinError = "PIN harus 4 digit"
                                } else {
                                    scope.launch {
                                        val salt = PinHasher.generateSalt()
                                        val hash = PinHasher.hashPin(pinValue, salt)
                                        app.settingsRepository.setPin(hash, salt)
                                        app.settingsRepository.updateRoomName(roomName)
                                        step = 4
                                    }
                                }
                            }
                        )
                    }
                }

                4 -> {
                    // Step 4: Setup Selesai & Admin QR Code
                    Text(
                        text = "Setup Selesai!",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Text(
                        text = "Pindai QR code ini dengan HP operator yang terhubung ke WiFi yang sama.",
                        fontSize = 14.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    val adminUrl = "http://$ipAddress:8080"
                    val qrBitmap = remember(adminUrl) {
                        QrCodeGenerator.generateQrBitmap(adminUrl, 360)
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Admin QR Code",
                            modifier = Modifier.size(160.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Alamat IP: $adminUrl",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    FocusableButton(
                        text = "Selesai & Buka Launcher",
                        isPrimary = true,
                        onClick = {
                            scope.launch {
                                app.settingsRepository.setSetupDone(true)
                                onSetupComplete()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FocusableButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> Color.White
        isPrimary -> PrimaryIndigo
        else -> Color(0x22FFFFFF)
    }

    val textCol = when {
        isFocused -> Color.Black
        isPrimary -> Color.White
        else -> TextPrimary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) AccentAmber else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = textCol
        )
    }
}
