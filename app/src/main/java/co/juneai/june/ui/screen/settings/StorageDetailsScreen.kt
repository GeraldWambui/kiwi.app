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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StorageDetailsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToCleaner: () -> Unit = {}
) {
    val storage by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val optimizeEnabled by viewModel.optimizeStorageEnabled.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Theme-sourced colors
    val StorageBg = MaterialTheme.colorScheme.background
    val CardBg = MaterialTheme.colorScheme.surface
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val AccentTeal = MaterialTheme.colorScheme.primary
    val AccentYellow = MaterialTheme.colorScheme.tertiaryContainer
    val AccentGray = MaterialTheme.colorScheme.onSurfaceVariant
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val BarBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    val usedGb = remember(storage.totalUsedBytes) {
        storage.totalUsedBytes / (1024.0 * 1024 * 1024)
    }
    val totalGb = remember(storage.totalCapacityBytes) {
        storage.totalCapacityBytes / (1024.0 * 1024 * 1024)
    }
    val photosGb = remember(storage.photosBytes) { storage.photosBytes / (1024.0 * 1024 * 1024) }
    val videosGb = remember(storage.videosBytes) { storage.videosBytes / (1024.0 * 1024 * 1024) }
    val vaultGb = remember(storage.vaultBytes) { storage.vaultBytes / (1024.0 * 1024 * 1024) }
    val systemGb = remember(storage.systemBytes) { storage.systemBytes / (1024.0 * 1024 * 1024) }
    val cacheMb = remember(storage.cacheBytes) { storage.cacheBytes / (1024.0 * 1024) }

    val totalForBar = remember(storage.totalCapacityBytes) {
        if (storage.totalCapacityBytes > 0) storage.totalCapacityBytes.toFloat() else 1f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StorageBg)
    ) {
        // ── Top Bar ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
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
                text = "Storage Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Total Usage Card ──────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .padding(20.dp)
            ) {
                Text(
                    text = "Total Usage",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%.1f", usedGb),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GB",
                            fontSize = 16.sp,
                            color = SecondaryText,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        text = "of ${String.format("%.0f", totalGb)} GB used",
                        fontSize = 14.sp,
                        color = SecondaryText,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Segmented Progress Bar ────────────────
                val photosF = (storage.photosBytes / totalForBar).coerceIn(0f, 1f)
                val videosF = (storage.videosBytes / totalForBar).coerceIn(0f, 1f)
                val vaultF = (storage.vaultBytes / totalForBar).coerceIn(0f, 1f)
                val systemF = (storage.systemBytes / totalForBar).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(BarBg)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (photosF > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(photosF.coerceAtLeast(0.01f))
                                    .height(10.dp)
                                    .background(AccentYellow)
                            )
                        }
                        if (videosF > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(videosF.coerceAtLeast(0.01f))
                                    .height(10.dp)
                                    .background(AccentTeal)
                            )
                        }
                        if (vaultF > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(vaultF.coerceAtLeast(0.01f))
                                    .height(10.dp)
                                    .background(AccentGreen)
                            )
                        }
                        if (systemF > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(systemF.coerceAtLeast(0.01f))
                                    .height(10.dp)
                                    .background(AccentGray)
                            )
                        }
                        // Remaining space
                        val usedPercent = (photosF + videosF + vaultF + systemF)
                        if (usedPercent < 1f) {
                            Box(
                                modifier = Modifier
                                    .weight((1f - usedPercent).coerceAtLeast(0.01f))
                                    .height(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Breakdown Labels ──────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StorageLegendItem("Photos", String.format("%.1f GB", photosGb), AccentYellow)
                    StorageLegendItem("Videos", String.format("%.1f GB", videosGb), AccentTeal)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StorageLegendItem("Vault", String.format("%.1f GB", vaultGb), AccentGreen)
                    StorageLegendItem("System", String.format("%.1f GB", systemGb), AccentGray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Storage Cleaner Action Card ───────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF233B6E), Color(0xFF1B2644))))
                    .clickable { onNavigateToCleaner() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3872E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clean Up Storage", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Find duplicate photos, large videos & burst shots", fontSize = 13.sp, color = Color(0xFFBACCEB))
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── CLOUD MANAGEMENT ──────────────────────────
            Text(
                text = "CLOUD MANAGEMENT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SecondaryText,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Cloud Sync Card
            CloudManagementCard(
                icon = Icons.Default.CloudSync,
                title = "Cloud Sync",
                subtitle = "Last synced: 2 mins ago",
                showArrow = true,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Optimize Storage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Optimize",
                        tint = AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Optimize Storage", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = PrimaryText)
                    Text("Keep small versions on device", fontSize = 13.sp, color = SecondaryText)
                }
                Switch(
                    checked = optimizeEnabled,
                    onCheckedChange = { viewModel.toggleOptimizeStorage() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = Color(0xFF888888),
                        uncheckedTrackColor = Color(0xFF333333),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(16.dp))

            // ── Clear Cache ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.clearCache() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Cache",
                    tint = SecondaryText,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Clear Cache",
                    fontSize = 16.sp,
                    color = PrimaryText,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format("%.0f MB", cacheMb),
                    fontSize = 14.sp,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Promo Banner ──────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Running out of space?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Get 100GB extra storage and advanced editing features.",
                    fontSize = 14.sp,
                    color = SecondaryText,
                    lineHeight = 20.sp
                )
            }

            // ── Get Storage Button ────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentGreen, Color(0xFF2E8B57))
                        )
                    )
                    .clickable { onNavigateToSubscription() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Get Extra Storage",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ── Plan Card Composable ────────────────────────────────
@Composable
private fun PlanCard(
    name: String,
    price: String,
    subtitle: String,
    ctaLabel: String,
    ctaColor: Color,
    ctaTextColor: Color,
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
        // Badge
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

        Column(
            modifier = Modifier.padding(20.dp)
        ) {
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

            // CTA Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (ctaFilled)
                            Modifier.background(ctaColor)
                        else
                            Modifier
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

            // Features
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

// ── Bottom Feature Icon ─────────────────────────────────
@Composable
private fun FeatureIcon(icon: ImageVector, label: String) {
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun StorageLegendItem(label: String, value: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CloudManagementCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showArrow: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2A1A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
            )
        }
    }
}
