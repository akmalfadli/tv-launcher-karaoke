package id.karaoke.timerlauncher.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.karaoke.timerlauncher.App
import id.karaoke.timerlauncher.auth.PinHasher
import id.karaoke.timerlauncher.ui.theme.AccentAmber
import id.karaoke.timerlauncher.ui.theme.DangerRed
import id.karaoke.timerlauncher.ui.theme.PrimaryIndigo
import id.karaoke.timerlauncher.ui.theme.SurfaceDark
import id.karaoke.timerlauncher.ui.theme.TextMuted
import id.karaoke.timerlauncher.ui.theme.TextPrimary

@Composable
fun PinLockDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    val rateLimiter = app.rateLimiter
    val clientKey = "tv_local"

    fun handleDigit(d: String) {
        if (enteredPin.length < 6) {
            enteredPin += d
            errorMessage = null
        }
    }

    fun handleDelete() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    fun handleClear() {
        enteredPin = ""
        errorMessage = null
    }

    fun handleSubmit() {
        if (rateLimiter.isLocked(clientKey)) {
            val remSec = rateLimiter.getRemainingLockoutSeconds(clientKey)
            errorMessage = "Terkunci. Coba lagi dalam $remSec detik."
            return
        }

        if (enteredPin.isEmpty()) {
            errorMessage = "PIN tidak boleh kosong"
            return
        }

        isVerifying = true
        scope.launch {
            val settings = app.settingsRepository.getSettings()
            val valid = if (settings.pinHash.isBlank()) {
                enteredPin == "1234"
            } else {
                PinHasher.verifyPin(enteredPin, settings.pinSalt, settings.pinHash)
            }

            if (valid) {
                rateLimiter.recordSuccessfulAttempt(clientKey)
                onSuccess()
            } else {
                val locked = rateLimiter.recordFailedAttempt(clientKey)
                if (locked) {
                    val remSec = rateLimiter.getRemainingLockoutSeconds(clientKey)
                    errorMessage = "PIN salah! Terkunci selama $remSec detik."
                } else {
                    errorMessage = "PIN salah"
                }
                enteredPin = ""
            }
            isVerifying = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Masukkan PIN Admin",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Pengaturan ruangan dilindungi PIN",
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // PIN Display Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(48.dp)
                    .padding(bottom = 12.dp)
            ) {
                for (i in 0 until 6) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFilled) PrimaryIndigo else Color(0x22FFFFFF))
                            .border(1.dp, if (isFilled) PrimaryIndigo else Color(0x44FFFFFF), RoundedCornerShape(8.dp))
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = DangerRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Keypad rows
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "⌫")
            )

            for (row in rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    for (key in row) {
                        DpadKeyButton(
                            text = key,
                            onClick = {
                                when (key) {
                                    "C" -> handleClear()
                                    "⌫" -> handleDelete()
                                    else -> handleDigit(key)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DpadActionButton(
                    text = "Batal",
                    color = Color(0x22FFFFFF),
                    textColor = TextPrimary,
                    onClick = onDismiss
                )
                DpadActionButton(
                    text = "OK",
                    color = PrimaryIndigo,
                    textColor = Color.White,
                    onClick = { handleSubmit() }
                )
            }
        }
    }
}

@Composable
fun DpadKeyButton(
    text: String,
    modifier: Modifier = Modifier.size(72.dp, 44.dp),
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) PrimaryIndigo else Color(0x14FFFFFF))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) Color.White else TextPrimary
        )
    }
}

@Composable
fun DpadActionButton(
    text: String,
    color: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(110.dp, 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else color)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) AccentAmber else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) Color.Black else textColor
        )
    }
}
