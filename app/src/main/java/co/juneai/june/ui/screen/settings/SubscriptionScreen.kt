package co.juneai.june.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme

@Composable
fun SubscriptionScreen(
    onBack: () -> Unit = {}
) {
    // Theme-sourced colors
    val SubscriptionBg = MaterialTheme.colorScheme.background
    val CardBg = MaterialTheme.colorScheme.surface
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SubscriptionBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top bar ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = PrimaryText,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Cloud Storage",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Hero Banner ────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E3A14),
                                CardBg
                            )
                        )
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentGreen.copy(alpha = 0.25f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "LIMITED OFFER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Preserve your\nmemories in full\nresolution.",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Never worry about space again.\nSecure, encrypted, and lightning fast.",
                    fontSize = 13.sp,
                    color = SecondaryText,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Choose your sanctuary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── STARTER ─────────────────────────────────────
            SubPlanCard(
                name = "STARTER",
                price = "$1.99",
                subtitle = "Perfect for hobbyists",
                ctaLabel = "Get Started",
                ctaColor = AccentGreen,
                ctaFilled = false,
                features = listOf("100GB Storage", "Advanced Search", "Secure Vault"),
                badge = null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── ENTHUSIAST ──────────────────────────────────
            SubPlanCard(
                name = "ENTHUSIAST",
                price = "$2.99",
                subtitle = "Our most balanced plan",
                ctaLabel = "Upgrade to Pro",
                ctaColor = AccentGreen,
                ctaFilled = true,
                features = listOf("200GB Storage", "AI Image Recognition", "Shared Family Vault", "Prioritized Uploads"),
                badge = "MOST POPULAR"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── COLLECTOR ───────────────────────────────────
            SubPlanCard(
                name = "COLLECTOR",
                price = "$9.99",
                subtitle = "Unlimited peace of mind",
                ctaLabel = "Go Unlimited",
                ctaColor = AccentGreen,
                ctaFilled = false,
                features = listOf("2TB Storage", "Full Res Backups", "Unlimited Vault Space", "VIP Support"),
                badge = null,
                hasVipFeature = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Bottom Icons ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SubFeatureIcon(icon = Icons.Default.Lock, label = "END-TO-END\nENCRYPTION")
                SubFeatureIcon(icon = Icons.Default.Cached, label = "AUTO-SYNC")
                SubFeatureIcon(icon = Icons.Default.AutoAwesome, label = "NO\nCOMPRESSION")
                SubFeatureIcon(icon = Icons.Default.Cloud, label = "ALL\nDEVICES")
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ── Plan Card ────────────────────────────────────────────
@Composable
private fun SubPlanCard(
    name: String,
    price: String,
    subtitle: String,
    ctaLabel: String,
    ctaColor: Color,
    ctaFilled: Boolean = false,
    features: List<String>,
    badge: String?,
    hasVipFeature: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (badge != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = price,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " /mo",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (ctaFilled) Modifier.background(ctaColor)
                        else Modifier
                            .background(Color.Transparent)
                            .border(1.dp, ctaColor, RoundedCornerShape(10.dp))
                    )
                    .clickable { /* Purchase flow */ }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ctaLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ctaFilled) Color.White else ctaColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            features.forEachIndexed { idx, feature ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasVipFeature && idx == features.lastIndex) Color(0xFFFF6B35)
                                else MaterialTheme.colorScheme.tertiary
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = feature,
                        fontSize = 14.sp,
                        color = if (hasVipFeature && idx == features.lastIndex) Color(0xFFFF6B35)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SubFeatureIcon(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}
