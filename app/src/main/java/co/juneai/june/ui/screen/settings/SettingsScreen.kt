package co.juneai.june.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.R
import co.juneai.june.ml.model.ModelManager

@Composable
fun SettingsScreen(
    onNavigateToStorage: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToVaultSetup: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToCleaner: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val faceIdEnabled by viewModel.faceIdEnabled.collectAsStateWithLifecycle()
    val storage by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val isNsfwProtectionEnabled by viewModel.isNsfwProtectionEnabled.collectAsStateWithLifecycle()
    val nsfwThreshold by viewModel.nsfwThreshold.collectAsStateWithLifecycle()
    val indexChargingOnly by viewModel.indexChargingOnly.collectAsStateWithLifecycle()
    val indexVideosEnabled by viewModel.indexVideosEnabled.collectAsStateWithLifecycle()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val videoAutoPlayEnabled by viewModel.videoAutoPlayEnabled.collectAsStateWithLifecycle()
    val activeModelId by viewModel.activeModelId.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isIndexing by viewModel.isIndexing.collectAsStateWithLifecycle()
    val indexingProgress by viewModel.indexingProgress.collectAsStateWithLifecycle()
    val totalIndexed by viewModel.totalIndexed.collectAsStateWithLifecycle()
    val isVaultConfigured by viewModel.isVaultConfigured.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()

    // Theme colors
    val SettingsBg = MaterialTheme.colorScheme.background
    val CardBg = MaterialTheme.colorScheme.surface
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

    val batteryState by viewModel.batteryState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBg)
    ) {
        SettingsTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ═════════════════════════════════════════════════════════════
            // SECTION 1: ON-DEVICE AI & SEARCH ENGINE
            // ═════════════════════════════════════════════════════════════
            SectionHeader("ON-DEVICE AI & SEARCH ENGINE")
            Spacer(modifier = Modifier.height(8.dp))

            // Indexing Status Card - Responsive & Organized for all screen sizes
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Row: Status Indicator + Title on Left, Status Pill or Spinner on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isIndexing) AccentGreen
                                        else if (batteryState.isEligibleForIndexing) AccentGreen
                                        else Color(0xFFFFB74D)
                                    )
                            )
                            Text(
                                text = if (isIndexing) "Indexing Media ($indexingProgress%)" else "Neural Index Engine",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryText
                            )
                        }

                        if (isIndexing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AccentGreen
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (batteryState.isEligibleForIndexing) AccentGreen.copy(alpha = 0.15f)
                                        else Color(0xFFFFB74D).copy(alpha = 0.15f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (batteryState.isEligibleForIndexing) "Active" else "Power Guard",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (batteryState.isEligibleForIndexing) AccentGreen else Color(0xFFFFB74D)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (!batteryState.isEligibleForIndexing && !isIndexing)
                            "$totalIndexed items indexed. Battery at ${batteryState.batteryPercent}% (plug in charger to auto-resume, or tap Index Now to override)."
                        else
                            "$totalIndexed photos & videos indexed for 100% private semantic search & acoustic classification.",
                        fontSize = 12.sp,
                        color = if (!batteryState.isEligibleForIndexing && !isIndexing) Color(0xFFFFB74D) else SecondaryText,
                        lineHeight = 16.sp
                    )

                    if (isIndexing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { indexingProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dedicated full-width action button row (responsive across all screen widths)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val currentBat = viewModel.refreshBatteryState()
                                if (!currentBat.isEligibleForIndexing) {
                                    Toast.makeText(
                                        context,
                                        "Battery is at ${currentBat.batteryPercent}%. Connect charger or use 'Index Now' to override.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    viewModel.triggerReindex(forceAll = true)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Re-Index", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                viewModel.triggerIndexNowIgnoringConstraints()
                                Toast.makeText(
                                    context,
                                    "Indexing started immediately in background",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = Color.Black
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Index Now", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Unrestricted Background Indexing & Battery Optimization Exemption
            var isBatteryOptIgnored by remember {
                mutableStateOf(viewModel.isBatteryOptimizationIgnored())
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Indexing Exemption",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryText
                        )
                        Text(
                            text = if (isBatteryOptIgnored)
                                "Exempt from battery saver • Indexes seamlessly while Kiwi is minimized"
                            else
                                "Exempt Kiwi from OS sleep limits to complete neural indexing in background",
                            fontSize = 11.sp,
                            color = SecondaryText,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isBatteryOptIgnored) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Exempt",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Open system battery settings to exempt Kiwi", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                isBatteryOptIgnored = viewModel.isBatteryOptimizationIgnored()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Exempt", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Battery Saver Indexing Toggle
            SettingsToggleRow(
                icon = Icons.Default.BatteryChargingFull,
                iconBg = IconBgColor,
                title = "Index While Charging Only",
                subtitle = "Pause neural indexing on battery to preserve power",
                checked = indexChargingOnly,
                onCheckedChange = { viewModel.setIndexChargingOnly(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Video & Audio Semantic Indexing Toggle
            SettingsToggleRow(
                icon = Icons.Default.Videocam,
                iconBg = IconBgColor,
                title = "Video Semantic & Audio Indexing",
                subtitle = "Keyframes, acoustic events (YAMNet), and speech transcripts (Whisper)",
                checked = indexVideosEnabled,
                onCheckedChange = { viewModel.setIndexVideosEnabled(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // On-Device Neural AI Models (MobileCLIP, YAMNet, Whisper Tiny, Zero-Shot Guardian)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "On-Device Neural AI Models",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryText
                    )
                    Text(
                        text = "Vision embeddings, acoustic sound classifier & speech-to-text models",
                        fontSize = 12.sp,
                        color = SecondaryText
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    availableModels.forEachIndexed { index, model ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = DividerColor
                            )
                        }

                        val isSelected = model.id == activeModelId
                        val isVisionModel = model.id == ModelManager.MODEL_MOBILECLIP_S2 || model.id == "mobileclip_s0"

                        val categoryTag = when (model.id) {
                            ModelManager.MODEL_MOBILECLIP_S2, "mobileclip_s0" -> "Vision AI"
                            "yamnet" -> "Audio AI"
                            "whisper_tiny" -> "Speech ASR"
                            else -> "Guardian"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected && isVisionModel) AccentGreen.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable(enabled = isVisionModel && (model.isDownloaded || model.isBundled)) {
                                    viewModel.selectModel(model.id)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected || model.isDownloaded) FontWeight.SemiBold else FontWeight.Medium,
                                        color = PrimaryText
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when {
                                                    isVisionModel -> AccentGreen.copy(alpha = 0.15f)
                                                    model.id == "yamnet" -> Color(0xFF4FC3F7).copy(alpha = 0.15f)
                                                    model.id == "whisper_tiny" -> Color(0xFFBA68C8).copy(alpha = 0.15f)
                                                    else -> AccentGreen.copy(alpha = 0.15f)
                                                }
                                            )
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = categoryTag,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isVisionModel -> AccentGreen
                                                model.id == "yamnet" -> Color(0xFF4FC3F7)
                                                model.id == "whisper_tiny" -> Color(0xFFBA68C8)
                                                else -> AccentGreen
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${model.description} • ${model.sizeFormatted}",
                                    fontSize = 11.sp,
                                    color = SecondaryText,
                                    lineHeight = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (model.isDownloaded || model.isBundled) {
                                if (isVisionModel) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = AccentGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AccentGreen.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Select", fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AccentGreen.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Ready",
                                            fontSize = 11.sp,
                                            color = AccentGreen,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                if (downloadProgress != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = AccentGreen
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "${(downloadProgress!! * 100).toInt()}%",
                                            fontSize = 11.sp,
                                            color = AccentGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            viewModel.downloadModel(model.id)
                                            Toast.makeText(context, "Downloading ${model.displayName} from Cloudflare CDN...", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download Model",
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Download", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═════════════════════════════════════════════════════════════
            // SECTION 2: PRIVACY & CONTENT GUARDIAN
            // ═════════════════════════════════════════════════════════════
            SectionHeader("PRIVACY & CONTENT GUARDIAN")
            Spacer(modifier = Modifier.height(8.dp))

            // 100% Offline Promise Card
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AccentGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "100% On-Device Privacy",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = "Zero bytes leave your device. All neural models and vector search execute entirely on local hardware.",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zero-Shot Content Protection Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Sensitive Content Blurring",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryText
                                )
                                Text(
                                    text = "Auto-blur sensitive photos with tap-to-reveal",
                                    fontSize = 12.sp,
                                    color = SecondaryText
                                )
                            }
                        }

                        Switch(
                            checked = isNsfwProtectionEnabled,
                            onCheckedChange = { viewModel.toggleNsfwProtection() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.scale(0.85f)
                        )
                    }

                    AnimatedVisibility(visible = isNsfwProtectionEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sensitivity: ${(1f - nsfwThreshold).let { if (it > 0.5f) "Strict" else if (it > 0.35f) "Balanced" else "Permissive" }}",
                                fontSize = 13.sp,
                                color = SecondaryText
                            )
                            Slider(
                                value = nsfwThreshold,
                                onValueChange = { viewModel.setNsfwThreshold(it) },
                                valueRange = 0.30f..0.80f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentGreen,
                                    activeTrackColor = AccentGreen
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═════════════════════════════════════════════════════════════
            // SECTION 3: SECURE VAULT & BIOMETRICS
            // ═════════════════════════════════════════════════════════════
            SectionHeader("SECURE VAULT & SECURITY")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsRow(
                icon = Icons.Default.Lock,
                iconBg = IconBgColor,
                title = "Encrypted Vault",
                subtitle = if (isVaultConfigured) "AES-256 hidden space configured" else "Tap to set up vault PIN & security",
                showArrow = true,
                onClick = {
                    if (isVaultConfigured) onNavigateToVault()
                    else onNavigateToVaultSetup()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggleRow(
                icon = Icons.Default.Fingerprint,
                iconBg = IconBgColor,
                title = "Biometric Lock",
                subtitle = "Require fingerprint / face recognition for vault",
                checked = faceIdEnabled,
                onCheckedChange = { viewModel.toggleFaceId() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ═════════════════════════════════════════════════════════════
            // SECTION 4: STORAGE & AUTOMATED CLEANER
            // ═════════════════════════════════════════════════════════════
            SectionHeader("STORAGE & AUTOMATED CLEANER")
            Spacer(modifier = Modifier.height(8.dp))

            StorageRow(
                usedGb = storageUsedGb,
                totalGb = storageTotalGb,
                progress = storageProgress,
                onClick = onNavigateToStorage
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Storage Cleaner Shortcut
            SettingsRow(
                icon = Icons.Default.CleaningServices,
                iconBg = IconBgColor,
                title = "Storage Cleaner",
                subtitle = "Find duplicates, similar shots, and large video files",
                showArrow = true,
                onClick = onNavigateToCleaner
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Trash Bin & Retention Policy
            SettingsRow(
                icon = Icons.Default.Delete,
                iconBg = IconBgColor,
                title = "Trash Bin",
                subtitle = "${trashRetentionDays}-day auto-delete retention period",
                showArrow = true,
                onClick = onNavigateToTrash
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Clear App Cache Button
            SettingsRow(
                icon = Icons.Default.Refresh,
                iconBg = IconBgColor,
                title = "Clear App Cache",
                subtitle = "Reclaim thumbnail and temporary cache space",
                showArrow = false,
                onClick = {
                    val reclaimed = viewModel.clearAppCache()
                    val reclaimedMb = String.format("%.1f", reclaimed / (1024.0 * 1024))
                    Toast.makeText(context, "Reclaimed $reclaimedMb MB cache space", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ═════════════════════════════════════════════════════════════
            // SECTION: SELF-HOSTED BACKUP & SYNC
            // ═════════════════════════════════════════════════════════════
            SectionHeader("SELF-HOSTED BACKUP & SYNC")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsRow(
                icon = Icons.Default.CloudUpload,
                iconBg = IconBgColor,
                title = "Self-Hosted Cloud Backup",
                subtitle = "Sync to your private Immich, Nextcloud, or WebDAV NAS",
                showArrow = true,
                onClick = onNavigateToBackup
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ═════════════════════════════════════════════════════════════
            // SECTION 5: GALLERY DISPLAY & PLAYBACK
            // ═════════════════════════════════════════════════════════════
            SectionHeader("GALLERY DISPLAY & PLAYBACK")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggleRow(
                icon = Icons.Default.PlayCircleOutline,
                iconBg = IconBgColor,
                title = "Autoplay Videos in Feed",
                subtitle = "Silently preview videos while scrolling timeline",
                checked = videoAutoPlayEnabled,
                onCheckedChange = { viewModel.setVideoAutoPlay(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Theme Palette Selector
            val themes = listOf(
                co.juneai.june.ui.theme.AppTheme.LIGHT to "Light",
                co.juneai.june.ui.theme.AppTheme.DARK to "OLED Dark",
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
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
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

            // ═════════════════════════════════════════════════════════════
            // SECTION 6: ABOUT & PRIVACY POLICY
            // ═════════════════════════════════════════════════════════════
            SectionHeader("ABOUT KIWI")
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App Version",
                    fontSize = 15.sp,
                    color = PrimaryText
                )
                Text(
                    text = "v2.4.0 (Offline Neural Build)",
                    fontSize = 13.sp,
                    color = SecondaryText
                )
            }

            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

            val uriHandler = LocalUriHandler.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://github.com") }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Privacy Architecture & Zero-Cloud Guarantee",
                    fontSize = 15.sp,
                    color = PrimaryText
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open",
                    tint = SecondaryText,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    showArrow: Boolean = false,
    onClick: () -> Unit = {}
) {
    val CardBg = MaterialTheme.colorScheme.surface
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val ArrowColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                }
            }

            if (showArrow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ArrowColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

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
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentGreen = MaterialTheme.colorScheme.tertiary

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.scale(0.85f)
            )
        }
    }
}

@Composable
private fun StorageRow(
    usedGb: String,
    totalGb: String,
    progress: Float,
    onClick: () -> Unit
) {
    val CardBg = MaterialTheme.colorScheme.surface
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val ArrowColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val ProgressTrackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Storage Details",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$usedGb GB of $totalGb GB",
                        fontSize = 13.sp,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ArrowColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentGreen,
                trackColor = ProgressTrackColor
            )
        }
    }
}
