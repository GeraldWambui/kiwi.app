package co.juneai.june.ui.screen.vault

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.domain.model.VaultAuthMode

// ─────────────────────────────────────────────────────────────────────────────
// Step machine
// ─────────────────────────────────────────────────────────────────────────────

private sealed interface SetupStep {
    data object ModeSelection : SetupStep
    data object PinEntry : SetupStep
    data class  PinConfirm(val firstPin: String) : SetupStep
    // BOTH mode: PIN matched, biometric prompt is now showing.
    data class  BiometricAfterPin(val confirmedPin: String) : SetupStep
    data object Saving : SetupStep
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VaultSetupScreen(
    vaultViewModel: VaultViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current

    // ── All remember() calls MUST be at the top of the composable, never ──
    // ── below a conditional return, to satisfy Compose's rules of hooks.  ──
    var step       by remember { mutableStateOf<SetupStep>(SetupStep.ModeSelection) }
    var digits     by remember { mutableStateOf(listOf<Int>()) }
    var errorText  by remember { mutableStateOf<String?>(null) }
    // Remembers which mode card the user tapped before entering the PIN flow.
    var modeIntent by remember { mutableStateOf(VaultAuthMode.PIN_ONLY) }

    val isLoading by vaultViewModel.isLoading.collectAsStateWithLifecycle()

    val authenticators = remember {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    }

    val biometricAvailable = remember {
        BiometricManager.from(context)
            .canAuthenticate(authenticators) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    Log.d("Biometrics Available", "$biometricAvailable")

    // ── Biometric prompt helper ──────────────────────────────────────────
    fun launchBiometricPrompt(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = context as? FragmentActivity ?: run { onError("No host activity"); return }
        Log.d("Activity In Bio Prompt", "$activity")
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) =
                    onSuccess()

                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    // Non-fatal user dismissals → pass empty string so the caller
                    // can distinguish "cancelled" from "actual error".
                    onError(
                        if (code == BiometricPrompt.ERROR_USER_CANCELED ||
                            code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) "" else "Biometric error: $err"
                    )
                }
            }
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText("Cancel")
                .build()
        )
    }

    // ────────────────────────── Saving overlay ───────────────────────────
    if (step == SetupStep.Saving || isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text("Configuring vault…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    // ─────────── BiometricAfterPin: sequential BOTH-mode biometric ───────
    // The PIN was confirmed; now we trigger the system biometric dialog and
    // only call setupVaultWithPin once biometric succeeds (or falls back on cancel).
    if (step is SetupStep.BiometricAfterPin) {
        val confirmedPin = (step as SetupStep.BiometricAfterPin).confirmedPin

        LaunchedEffect(step) {
            launchBiometricPrompt(
                title    = "Enable Biometrics",
                subtitle = "Register your fingerprint / face alongside your PIN",
                onSuccess = {
                    step = SetupStep.Saving
                    vaultViewModel.setupVaultWithPin(confirmedPin, VaultAuthMode.BOTH) { ok ->
                        if (ok) onSetupComplete()
                        else {
                            errorText = "Setup failed. Please try again."
                            step   = SetupStep.PinEntry
                            digits = emptyList()
                        }
                    }
                },
                onError = { msg ->
                    // User cancelled biometric → fall back to PIN_ONLY silently.
                    if (msg.isNotEmpty()) errorText = msg
                    step = SetupStep.Saving
                    vaultViewModel.setupVaultWithPin(confirmedPin, VaultAuthMode.PIN_ONLY) { ok ->
                        if (ok) onSetupComplete()
                        else {
                            errorText = "Setup failed. Please try again."
                            step   = SetupStep.PinEntry
                            digits = emptyList()
                        }
                    }
                }
            )
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Waiting for biometric…", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = {
                    // Manual skip: save with PIN_ONLY.
                    step = SetupStep.Saving
                    vaultViewModel.setupVaultWithPin(confirmedPin, VaultAuthMode.PIN_ONLY) { ok ->
                        if (ok) onSetupComplete()
                        else {
                            errorText = "Setup failed."
                            step   = SetupStep.PinEntry
                            digits = emptyList()
                        }
                    }
                }) { Text("Skip – use PIN only") }
            }
        }
        return
    }

    // ── Main layout ──────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // ──────────────────── MODE SELECTION ─────────────────────────────
        if (step == SetupStep.ModeSelection) {

            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "Secure Your Vault",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Choose how to protect your private media.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            ModeCard(
                icon = Icons.Default.Pin,
                title = "PIN Only",
                description = "Protect with a 6-digit passcode."
            ) {
                modeIntent = VaultAuthMode.PIN_ONLY
                errorText  = null; digits = emptyList()
                step       = SetupStep.PinEntry
            }

            if (biometricAvailable) {
                Spacer(Modifier.height(12.dp))

                ModeCard(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometrics Only",
                    description = "Use fingerprint or face unlock."
                ) {
                    errorText = null
                    launchBiometricPrompt(
                        title    = "Enable Vault Biometrics",
                        subtitle = "Authenticate to protect your vault",
                        onSuccess = {
                            step = SetupStep.Saving
                            vaultViewModel.setupVaultBiometricOnly { ok ->
                                if (ok) onSetupComplete()
                                else { errorText = "Setup failed."; step = SetupStep.ModeSelection }
                            }
                        },
                        onError = { msg -> if (msg.isNotEmpty()) errorText = msg }
                    )
                }

                Spacer(Modifier.height(12.dp))

                ModeCard(
                    icon = Icons.Default.Check,
                    title = "PIN + Biometrics  ✦ Recommended",
                    description = "Set a PIN, then register your biometric.",
                    highlighted = true
                ) {
                    modeIntent = VaultAuthMode.BOTH
                    errorText  = null; digits = emptyList()
                    step       = SetupStep.PinEntry
                }
            }

            Spacer(Modifier.height(32.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }

        } else {

            // ──────────────────── PIN ENTRY / CONFIRM ────────────────────
            val isConfirmStep = step is SetupStep.PinConfirm

            Text(
                text = if (isConfirmStep) "Confirm Passcode" else "Create Passcode",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isConfirmStep) "Re-enter your 6-digit PIN."
                else "Choose a 6-digit PIN for your vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(32.dp))

            // Dot indicators
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < digits.size) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(visible = errorText != null) {
                Text(
                    text  = errorText ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(24.dp))

            // Digit entry logic extracted as a local lambda to stay DRY across
            // the number pad and the 0 key.
            val handleDigit: (Int) -> Unit = { digit ->
                if (digits.size < 6) {
                    val updated = digits + digit
                    digits     = updated
                    errorText  = null

                    if (updated.size == 6) {
                        val entered = updated.joinToString("")
                        when (val s = step) {

                            is SetupStep.PinEntry -> {
                                step   = SetupStep.PinConfirm(entered)
                                digits = emptyList()
                            }

                            is SetupStep.PinConfirm -> {
                                if (entered == s.firstPin) {
                                    digits = emptyList()
                                    when (modeIntent) {
                                        VaultAuthMode.BOTH -> {
                                            // Trigger sequential biometric after PIN match.
                                            step = SetupStep.BiometricAfterPin(entered)
                                        }
                                        else -> {
                                            step = SetupStep.Saving
                                            vaultViewModel.setupVaultWithPin(entered, modeIntent) { ok ->
                                                if (ok) onSetupComplete()
                                                else {
                                                    errorText = "Setup failed. Please try again."
                                                    step   = SetupStep.PinEntry
                                                    digits = emptyList()
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    errorText = "PINs do not match – try again"
                                    digits    = emptyList()
                                    step      = SetupStep.PinEntry
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            }

            // Number pad – 1..9
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { d -> NumberKey(d) { handleDigit(d) } }
                    }
                }

                // Bottom row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (isConfirmStep) {
                            step = SetupStep.PinEntry; digits = emptyList(); errorText = null
                        } else {
                            step = SetupStep.ModeSelection; digits = emptyList(); errorText = null
                        }
                    }) { Text(if (isConfirmStep) "Back" else "Cancel") }

                    NumberKey(0) { handleDigit(0) }

                    IconButton(onClick = {
                        if (digits.isNotEmpty()) { digits = digits.dropLast(1); errorText = null }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

// ─────────────────────────── Private composables ────────────────────────────

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val bg      = if (highlighted) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fgColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bg,
        tonalElevation = if (highlighted) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(32.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = fgColor)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = fgColor.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun NumberKey(digit: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(72.dp).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium)
        }
    }
}