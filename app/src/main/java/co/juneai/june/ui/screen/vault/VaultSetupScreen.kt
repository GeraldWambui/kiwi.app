package co.juneai.june.ui.screen.vault

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.MaterialTheme
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import co.juneai.june.ui.screen.settings.SettingsViewModel


@Composable
fun VaultSetupScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current

    // Theme-sourced colors
    val VaultBg = MaterialTheme.colorScheme.background
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val DotFilled = AccentGreen
    val DotEmpty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val KeyBg = MaterialTheme.colorScheme.surface
    val KeyBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    var digits by remember { mutableStateOf(listOf<Int>()) }
    var confirmDigits by remember { mutableStateOf<List<Int>?>(null) }
    var firstPasscode by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val isConfirmStep = firstPasscode != null

    val biometricAvailable = remember {
        val bm = BiometricManager.from(context)
        bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Shield icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E2A1A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Vault",
                tint = AccentGreen,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isConfirmStep) "Confirm Passcode" else "Create Vault Passcode",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isConfirmStep)
                "Re-enter your 6-digit passcode to confirm"
            else
                "Create a 6-digit passcode to protect\nyour Kiwi Gallery vault",
            fontSize = 14.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        // Error text
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

        // Biometric option (if available and not in confirm step)
        if (biometricAvailable && !isConfirmStep) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1C18))
                    .clickable {
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Kiwi Vault")
                                .setSubtitle("Use biometrics to unlock vault")
                                .setNegativeBtnText("Use Passcode")
                                .build()
                            val prompt = BiometricPrompt(
                                activity,
                                ContextCompat.getMainExecutor(context),
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        onSetupComplete()
                                    }
                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        // User cancelled or error — stay on screen
                                    }
                                }
                            )
                            prompt.authenticate(promptInfo)
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Biometric",
                    tint = AccentGreen,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Use Biometrics Instead",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

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
                        SetupNumberKey(digit = digit) {
                            if (digits.size < 6) {
                                val updated = digits + digit
                                digits = updated
                                if (updated.size == 6) {
                                    if (firstPasscode == null) {
                                        firstPasscode = updated.joinToString("")
                                        digits = emptyList()
                                        error = null
                                    } else {
                                        val confirm = updated.joinToString("")
                                        if (confirm == firstPasscode) {
                                            settingsViewModel.savePasscode(confirm)
                                            settingsViewModel.savePasscodePlain(confirm)
                                            settingsViewModel.markVaultConfigured()
                                            onSetupComplete()
                                        } else {
                                            error = "Passcodes do not match. Try again."
                                            firstPasscode = null
                                            digits = emptyList()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Bottom row
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

                SetupNumberKey(digit = 0) {
                    if (digits.size < 6) {
                        val updated = digits + 0
                        digits = updated
                        if (updated.size == 6) {
                            if (firstPasscode == null) {
                                firstPasscode = updated.joinToString("")
                                digits = emptyList()
                                error = null
                            } else {
                                val confirm = updated.joinToString("")
                                if (confirm == firstPasscode) {
                                    onSetupComplete()
                                } else {
                                    error = "Passcodes do not match. Try again."
                                    firstPasscode = null
                                    digits = emptyList()
                                }
                            }
                        }
                    }
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

        Spacer(modifier = Modifier.height(32.dp))

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

@Composable
private fun SetupNumberKey(digit: Int, onClick: () -> Unit) {
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

private fun BiometricPrompt.PromptInfo.Builder.setNegativeBtnText(text: String): BiometricPrompt.PromptInfo.Builder {
    return setNegativeButtonText(text)
}
