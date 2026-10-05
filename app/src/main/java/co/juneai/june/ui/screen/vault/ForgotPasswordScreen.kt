package co.juneai.june.ui.screen.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import co.juneai.june.ui.screen.settings.SettingsViewModel

@Preview
@Composable
fun ForgotPasswordScreen(
    onResetComplete: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val context = LocalContext.current
    val ForgotBg = MaterialTheme.colorScheme.background
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val LightGreen = MaterialTheme.colorScheme.tertiaryContainer
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val DotFilled = AccentGreen
    val DotEmpty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val KeyBg = MaterialTheme.colorScheme.surface
    val KeyBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    var step by remember { mutableIntStateOf(0) }
    // 0 = biometric verification, 1 = new passcode, 2 = confirm
    var revealedPasscode by remember { mutableStateOf<String?>(null) }
    var newPasscode by remember { mutableStateOf<String?>(null) }
    var digits by remember { mutableStateOf(listOf<Int>()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForgotBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = PrimaryText,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Reset Passcode",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Reset icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E2A1A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LockReset,
                contentDescription = "Reset",
                tint = AccentGreen,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (step) {
            0 -> {
                // Biometric verification step
                Text(
                    text = "Verify Your Identity",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Use Face or Fingerprint to confirm\nyour identity and create a new passcode",
                    fontSize = 14.sp,
                    color = SecondaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Biometric button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentGreen)
                        .clickable {
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Kiwi Photos Vault")
                                    .setSubtitle("Verify identity with Face or Fingerprint")
                                    .setAllowedAuthenticators(authenticators)
                                    .setNegativeButtonText("Cancel")
                                    .build()
                                val prompt = BiometricPrompt(
                                    activity,
                                    ContextCompat.getMainExecutor(context),
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                            step = 1
                                            error = null
                                        }
                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                            error = errString.toString()
                                        }
                                    }
                                )
                                prompt.authenticate(promptInfo)
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Verify with Face / Fingerprint",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                error?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Set Up New Passcode Directly",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen,
                    modifier = Modifier.clickable {
                        step = 1
                        error = null
                    }
                )
            }

            1, 2 -> {
                // Create / Confirm new passcode
                Text(
                    text = if (step == 1) "Create New Passcode" else "Confirm New Passcode",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (step == 1)
                        "Choose a new 6-digit passcode"
                    else
                        "Re-enter your new passcode to confirm",
                    fontSize = 14.sp,
                    color = SecondaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                error?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Dots
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(6) { index ->
                        val isFilled = index < digits.size
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) DotFilled else DotEmpty)
                                .then(
                                    if (!isFilled) Modifier.border(1.dp, KeyBorder, CircleShape)
                                    else Modifier
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Number pad
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { digit ->
                                ForgotNumberKey(digit = digit) {
                                    handleDigitInput(
                                        digit, digits, step, newPasscode,
                                        onDigitsUpdated = { digits = it },
                                        onStepAdvance = {
                                            newPasscode = digits.joinToString("")
                                            digits = emptyList()
                                            step = 2
                                            error = null
                                        },
                                        onResetComplete = {
                                            newPasscode?.let { settingsViewModel.savePasscode(it) }
                                            onResetComplete()
                                        },
                                        onError = {
                                            error = "Passcodes do not match. Try again."
                                            newPasscode = null
                                            digits = emptyList()
                                            step = 1
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onCancel
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CANCEL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecondaryText,
                                letterSpacing = 0.5.sp
                            )
                        }

                        ForgotNumberKey(digit = 0) {
                            handleDigitInput(
                                0, digits, step, newPasscode,
                                onDigitsUpdated = { digits = it },
                                onStepAdvance = {
                                    newPasscode = digits.joinToString("")
                                    digits = emptyList()
                                    step = 2
                                    error = null
                                },
                                onResetComplete = {
                                    newPasscode?.let { settingsViewModel.savePasscode(it) }
                                    onResetComplete()
                                },
                                onError = {
                                    error = "Passcodes do not match. Try again."
                                    newPasscode = null
                                    digits = emptyList()
                                    step = 1
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (digits.isNotEmpty()) {
                                            digits = digits.dropLast(1)
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = SecondaryText,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
            )
            Text(
                text = "Kiwi Gallery Security",
                fontSize = 12.sp,
                color = SecondaryText
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun handleDigitInput(
    digit: Int,
    currentDigits: List<Int>,
    step: Int,
    newPasscode: String?,
    onDigitsUpdated: (List<Int>) -> Unit,
    onStepAdvance: () -> Unit,
    onResetComplete: () -> Unit,
    onError: () -> Unit
) {
    if (currentDigits.size >= 6) return
    val updated = currentDigits + digit
    onDigitsUpdated(updated)
    if (updated.size == 6) {
        if (step == 1) {
            onStepAdvance()
        } else {
            val confirm = updated.joinToString("")
            if (confirm == newPasscode) {
                onResetComplete()
            } else {
                onError()
            }
        }
    }
}

@Composable
private fun ForgotNumberKey(digit: Int, onClick: () -> Unit) {
    val KeyBg = MaterialTheme.colorScheme.surface
    val KeyBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(KeyBg)
            .border(1.dp, KeyBorder, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            color = PrimaryText
        )
    }
}
