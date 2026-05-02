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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.R

@Composable
fun SettingsScreen(
    onNavigateToStorage: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToVaultSetup: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToReferral: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val cloudSyncEnabled by viewModel.cloudSyncEnabled.collectAsStateWithLifecycle()
    val faceIdEnabled by viewModel.faceIdEnabled.collectAsStateWithLifecycle()
    val storage by viewModel.storageBreakdown.collectAsStateWithLifecycle()

    // Theme-sourced colors
    val SettingsBg = MaterialTheme.colorScheme.background
    val CardBg = MaterialTheme.colorScheme.surface
    val SectionHeaderColor = MaterialTheme.colorScheme.tertiary
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val IconBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val DividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    val scrollState = rememberScrollState()

    val storageUsedGb = remember(storage.totalUsedBytes) {
        String.format("%.1f", storage.totalUsedBytes / (1024.0 * 1024 * 1024))
    }
    val storageTotalGb = remember(storage.totalCapacityBytes) {
        String.format("%.0f", storage.totalCapacityBytes / (1024.0 * 1024 * 1024))
    }
    val storageProgress = remember(storage.totalUsedBytes, storage.totalCapacityBytes) {
        if (storage.totalCapacityBytes > 0) {
            (storage.totalUsedBytes.toFloat() / storage.totalCapacityBytes).coerceIn(0f, 1f)
        } else 0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBg)
    ) {
        // ── Top Bar ────────────────────────────────────────
        SettingsTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── ACCOUNT Section ────────────────────────────
            SectionHeader("ACCOUNT")
            Spacer(modifier = Modifier.height(8.dp))

            // Profile Card
            SettingsRow(
                icon = Icons.Default.Person,
                iconBg = AccentGreen,
                title = "Alex Riverstone",
                subtitle = "Manage your personal information",
                showArrow = true,
                onClick = {},
                isProfile = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Email Card
            SettingsRow(
                icon = Icons.Default.Email,
                iconBg = IconBgColor,
                title = "Email",
                subtitle = "alex.kiwi@forest.com",
                showArrow = true,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── STORAGE Section ────────────────────────────
            SectionHeader("STORAGE")
            Spacer(modifier = Modifier.height(8.dp))

            // Manage Storage
            StorageRow(
                usedGb = storageUsedGb,
                totalGb = storageTotalGb,
                progress = storageProgress,
                onClick = onNavigateToStorage
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Cloud Sync Toggle
            SettingsToggleRow(
                icon = Icons.Default.CloudSync,
                iconBg = IconBgColor,
                title = "Cloud Sync",
                subtitle = "Keep photos updated across devices",
                checked = cloudSyncEnabled,
                onCheckedChange = { viewModel.toggleCloudSync() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── SECURITY & PRIVACY Section ─────────────────
            SectionHeader("SECURITY & PRIVACY")
            Spacer(modifier = Modifier.height(8.dp))

            // Vault Settings
            val isVaultConfigured by viewModel.isVaultConfigured.collectAsState()
            SettingsRow(
                icon = Icons.Default.Lock,
                iconBg = IconBgColor,
                title = "Vault Settings",
                subtitle = if (isVaultConfigured) "Encryption and auto-lock" else "Set up vault security",
                showArrow = true,
                onClick = {
                    if (isVaultConfigured) onNavigateToVault()
                    else onNavigateToVaultSetup()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Face ID / Passcode
            SettingsToggleRow(
                icon = Icons.Default.Fingerprint,
                iconBg = IconBgColor,
                title = "Face ID / Passcode",
                subtitle = "Protect app entry",
                checked = faceIdEnabled,
                onCheckedChange = { viewModel.toggleFaceId() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // NSFW Content Protection
            SettingsToggleRow(
                icon = Icons.Default.Shield,
                iconBg = AccentGreen.copy(alpha = 0.15f),
                title = "Content Protection",
                subtitle = "Blur sensitive / nude content",
                checked = true,//placeholder
                onCheckedChange = { viewModel.toggleNsfwProtection() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── THEME Section ───────────────────────────────
            SectionHeader("THEME")
            Spacer(modifier = Modifier.height(8.dp))

            val selectedTheme by viewModel.selectedTheme.collectAsState()
            val themes = listOf(
                co.juneai.june.ui.theme.AppTheme.LIGHT to "Light",
                co.juneai.june.ui.theme.AppTheme.DARK to "Dark",
                co.juneai.june.ui.theme.AppTheme.SYSTEM to "System",
                co.juneai.june.ui.theme.AppTheme.OCEAN to "Ocean",
                co.juneai.june.ui.theme.AppTheme.FOREST to "Forest",
                co.juneai.june.ui.theme.AppTheme.SUNSET to "Sunset",
                co.juneai.june.ui.theme.AppTheme.ALPINE to "Alpine",
            )

            val themeColors = mapOf(
                co.juneai.june.ui.theme.AppTheme.LIGHT to Color(0xFFEAE5D8),
                co.juneai.june.ui.theme.AppTheme.DARK to Color(0xFF0A0F09),
                co.juneai.june.ui.theme.AppTheme.SYSTEM to Color(0xFF666666),
                co.juneai.june.ui.theme.AppTheme.OCEAN to Color(0xFF00BCD4),
                co.juneai.june.ui.theme.AppTheme.FOREST to Color(0xFF4A6741),
                co.juneai.june.ui.theme.AppTheme.SUNSET to Color(0xFFFF5722),
                co.juneai.june.ui.theme.AppTheme.ALPINE to Color(0xFFE2E8F0)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                themes.forEach { (theme, label) ->
                    val isSelected = selectedTheme == theme
                    Column(
                        modifier = Modifier
                            .clickable { viewModel.setTheme(theme) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, AccentGreen, CircleShape)
                                    else Modifier.border(1.dp, DividerColor, CircleShape)
                                )
                                .background(themeColors[theme] ?: Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (theme == co.juneai.june.ui.theme.AppTheme.DARK) Color.White else Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = if (isSelected) AccentGreen else SecondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── ABOUT Section ──────────────────────────────
            SectionHeader("ABOUT")
            Spacer(modifier = Modifier.height(8.dp))

            // Referral Program
            SettingsRow(
                icon = Icons.Default.Redeem,
                iconBg = Color(0xFF2A1A0A),
                title = "Referral Program",
                subtitle = "Give 5GB, Get 5GB",
                showArrow = true,
                onClick = onNavigateToReferral
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Version
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Version",
                    fontSize = 16.sp,
                    color = PrimaryText
                )
                Text(
                    text = "2.4.0 (Nature Build)",
                    fontSize = 14.sp,
                    color = SecondaryText
                )
            }

            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

            // Privacy Policy
            val uriHandler = LocalUriHandler.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://example.com/privacy") }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Privacy Policy",
                    fontSize = 16.sp,
                    color = PrimaryText
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open",
                    tint = SecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Bottom padding for nav bar
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

// ── Top Bar ──────────────────────────────────────────────
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Back",
                tint = PrimaryText,
                modifier = Modifier
                    .size(28.dp)
                    .scale(scaleX = -1f, scaleY = 1f) // Flip for back arrow
            )
        }

        Text(
            text = stringResource(R.string.kiwi_gallery),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryText
        )

        IconButton(onClick = { /* Search from Settings */ }) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = PrimaryText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ── Section Header ──────────────────────────────────────
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
        letterSpacing = 1.sp
    )
}

// ── Standard Settings Row ───────────────────────────────
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    showArrow: Boolean = false,
    onClick: () -> Unit,
    isProfile: Boolean = false
) {
    val CardBg = MaterialTheme.colorScheme.surface
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(if (isProfile) 48.dp else 40.dp)
                .clip(if (isProfile) CircleShape else RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isProfile) Color.White else AccentGreen,
                modifier = Modifier.size(if (isProfile) 24.dp else 20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryText
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = SecondaryText,
                lineHeight = 16.sp
            )
        }

        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigate",
                tint = SecondaryText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ── Storage Row with Progress ───────────────────────────
@Composable
private fun StorageRow(
    usedGb: String,
    totalGb: String,
    progress: Float,
    onClick: () -> Unit
) {
    val CardBg = MaterialTheme.colorScheme.surface
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val IconBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(IconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Storage",
                tint = AccentGreen,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Manage Storage",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF2A3527))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AccentGreen)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$usedGb GB of $totalGb GB used",
                fontSize = 12.sp,
                color = SecondaryText
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate",
            tint = SecondaryText,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ── Toggle Row ──────────────────────────────────────────
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val CardBg = MaterialTheme.colorScheme.surface
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
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
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AccentGreen,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryText
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = SecondaryText
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentGreen,
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF333333),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
