package co.juneai.june.ui.screen.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Preview
@Composable
fun PasscodeScreen(
    onSuccess: () -> Unit = {},
    onCancel: () -> Unit = {},
    onForgotPasscode: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val digits by viewModel.passcodeDigits.collectAsStateWithLifecycle()
    val hasError by viewModel.passcodeError.collectAsStateWithLifecycle()
    val isSuccess by viewModel.passcodeSuccess.collectAsStateWithLifecycle()
    val faceIdEnabled by viewModel.faceIdEnabled.collectAsStateWithLifecycle()

    val authenticators = remember {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun launchBiometricPrompt() {
        val activity = context as? FragmentActivity ?: return
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Kiwi Photos Vault")
            .setSubtitle("Unlock using Face ID or Fingerprint")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Use Passcode")
            .build()

        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.markPasscodeSuccess()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.d("PasscodeScreen", "Biometric dismissed: $errString")
                }
            }
        ).authenticate(promptInfo)
    }

    LaunchedEffect(biometricAvailable, faceIdEnabled) {
        if (biometricAvailable && faceIdEnabled) {
            launchBiometricPrompt()
        }
    }

    // Theme-sourced colors
    val PasscodeBg = MaterialTheme.colorScheme.background
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val LightGreen = MaterialTheme.colorScheme.tertiaryContainer
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val DotFilled = AccentGreen
    val DotEmpty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val KeyBg = MaterialTheme.colorScheme.surface
    val KeyBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    // Shake animation for error
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(hasError) {
        if (hasError) {
            for (i in 0 until 4) {
                shakeOffset.animateTo(10f, animationSpec = tween(50))
                shakeOffset.animateTo(-10f, animationSpec = tween(50))
            }
            shakeOffset.animateTo(0f, animationSpec = tween(50))
        }
    }

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            onSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PasscodeBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // ── Lock Icon ─────────────────────────────────────
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E2A1A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock",
                tint = AccentGreen,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Title ─────────────────────────────────────────
        Text(
            text = "Enter Passcode",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter your 6-digit passcode to unlock\nthe Kiwi Photos vault",
            fontSize = 14.sp,
            color = SecondaryText,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        if (biometricAvailable) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AccentGreen.copy(alpha = 0.12f))
                    .clickable { launchBiometricPrompt() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Biometrics",
                    tint = AccentGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Unlock with Face / Fingerprint",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Dots ──────────────────────────────────────────
        Row(
            modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(6) { index ->
                val isFilled = index < digits.size
                val isErrorDot = hasError
                val dotColor = when {
                    isErrorDot -> Color(0xFFFF5252)
                    isFilled -> DotFilled
                    else -> DotEmpty
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(1.dp, if (isFilled || isErrorDot) Color.Transparent else KeyBorder, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Number Pad ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1-3
            NumberPadRow(listOf(1, 2, 3), viewModel)
            // Row 4-6
            NumberPadRow(listOf(4, 5, 6), viewModel)
            // Row 7-9
            NumberPadRow(listOf(7, 8, 9), viewModel)
            // Bottom row: Cancel, 0, Backspace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                viewModel.clearPasscode()
                                onCancel()
                            }
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

                // 0
                NumberKey(digit = 0, onClick = { viewModel.appendPasscodeDigit(0) })

                // Backspace
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.deletePasscodeDigit() }
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

        // ── Forgot Passcode ───────────────────────────────
        Text(
            text = "Forgot Passcode?",
            fontSize = 14.sp,
            color = LightGreen,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onForgotPasscode() }
        )

        Spacer(modifier = Modifier.height(8.dp))

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
private fun NumberPadRow(digits: List<Int>, viewModel: SettingsViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        digits.forEach { digit ->
            NumberKey(digit = digit, onClick = { viewModel.appendPasscodeDigit(digit) })
        }
    }
}

@Composable
private fun NumberKey(digit: Int, onClick: () -> Unit) {
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
