package co.juneai.june.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Beautiful nature-inspired listening overlay for Hey Kiwi voice activation.
 * Features organic pulsing circles, particle effects, and kiwi-green gradients.
 */
@Composable
fun KiwiListeningOverlay(
    isVisible: Boolean,
    state: KiwiVoiceService.VoiceState,
    partialText: String = "",
    finalText: String = "",
    rmsLevel: Float = 0f,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)) + scaleIn(tween(400)),
        exit = fadeOut(tween(300)) + scaleOut(tween(300))
    ) {
        val kiwiGreen = MaterialTheme.colorScheme.tertiary
        val kiwiDark = MaterialTheme.colorScheme.onTertiary
        val kiwiLight = MaterialTheme.colorScheme.tertiaryContainer
        val bgColor = MaterialTheme.colorScheme.background

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // ── Animated concentric pulse rings ──
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulse1 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
                label = "pulse1"
            )
            val pulse2 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2400, delayMillis = 800, easing = LinearEasing), RepeatMode.Restart),
                label = "pulse2"
            )
            val pulse3 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2400, delayMillis = 1600, easing = LinearEasing), RepeatMode.Restart),
                label = "pulse3"
            )

            // Organic floating particles
            val particleRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
                label = "particles"
            )

            // RMS-responsive scale
            val rmsScale by animateFloatAsState(
                targetValue = 1f + (rmsLevel.coerceIn(0f, 10f) / 30f),
                animationSpec = tween(100),
                label = "rms"
            )

            Canvas(modifier = Modifier.size(320.dp)) {
                val center = this.center
                val maxRadius = size.minDimension / 2f

                // Draw 3 concentric pulse rings
                drawPulseRing(center, maxRadius, pulse1, kiwiGreen, kiwiLight)
                drawPulseRing(center, maxRadius, pulse2, kiwiGreen, kiwiLight)
                drawPulseRing(center, maxRadius, pulse3, kiwiGreen, kiwiLight)

                // Draw organic particles
                val particleCount = 12
                for (i in 0 until particleCount) {
                    val angle = (particleRotation + i * (360f / particleCount)) * PI.toFloat() / 180f
                    val radius = maxRadius * 0.55f + (sin(angle * 3) * maxRadius * 0.1f)
                    val x = center.x + cos(angle) * radius
                    val y = center.y + sin(angle) * radius
                    val particleSize = 3.dp.toPx() + (sin(angle * 2) * 2.dp.toPx())
                    drawCircle(
                        color = kiwiLight.copy(alpha = 0.5f + sin(angle) * 0.3f),
                        radius = particleSize,
                        center = Offset(x, y)
                    )
                }
            }

            // ── Center kiwi circle ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((100 * rmsScale).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(kiwiGreen, kiwiDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🥝",
                        fontSize = 42.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Status text
                Text(
                    text = when (state) {
                        KiwiVoiceService.VoiceState.HOTWORD_LISTENING -> "Say \"Hey Kiwi\"..."
                        KiwiVoiceService.VoiceState.COMMAND_LISTENING -> "Listening..."
                        KiwiVoiceService.VoiceState.PROCESSING -> "Got it!"
                        else -> ""
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Show partial/final recognized text
                val displayText = finalText.ifEmpty { partialText }
                if (displayText.isNotEmpty()) {
                    Text(
                        text = "\"$displayText\"",
                        fontSize = 16.sp,
                        color = kiwiGreen,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Dismiss hint
                Text(
                    text = "Tap anywhere to cancel",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DrawScope.drawPulseRing(
    center: Offset,
    maxRadius: Float,
    progress: Float,
    color: Color,
    lightColor: Color
) {
    val radius = maxRadius * 0.3f + (maxRadius * 0.7f * progress)
    val alpha = (1f - progress).coerceIn(0f, 0.4f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                lightColor.copy(alpha = alpha * 0.3f),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
