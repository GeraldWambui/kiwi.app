package co.juneai.june.ui.screen.backup

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.data.local.prefs.SelfHostedConfig
import co.juneai.june.data.local.prefs.SelfHostedProvider

@Composable
fun SelfHostedBackupScreen(
    onBack: () -> Unit = {},
    viewModel: SelfHostedBackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val savedConfig by viewModel.config.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val totalMedia by viewModel.totalMediaOnDevice.collectAsStateWithLifecycle()
    val backedUpCount by viewModel.backedUpCount.collectAsStateWithLifecycle()

    // Local form state
    var selectedProvider by remember { mutableStateOf(savedConfig.provider) }
    var serverUrl by remember { mutableStateOf(savedConfig.serverUrl) }
    var apiKeyOrToken by remember { mutableStateOf(savedConfig.apiKeyOrToken) }
    var username by remember { mutableStateOf(savedConfig.username) }
    var password by remember { mutableStateOf(savedConfig.password) }
    var targetDirectory by remember { mutableStateOf(savedConfig.targetDirectory) }
    var backupWifiOnly by remember { mutableStateOf(savedConfig.backupWifiOnly) }
    var backupChargingOnly by remember { mutableStateOf(savedConfig.backupChargingOnly) }
    var backupVideos by remember { mutableStateOf(savedConfig.backupVideos) }
    var autoBackupEnabled by remember { mutableStateOf(savedConfig.autoBackupEnabled) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(savedConfig) {
        selectedProvider = savedConfig.provider
        serverUrl = savedConfig.serverUrl
        apiKeyOrToken = savedConfig.apiKeyOrToken
        username = savedConfig.username
        password = savedConfig.password
        targetDirectory = savedConfig.targetDirectory
        backupWifiOnly = savedConfig.backupWifiOnly
        backupChargingOnly = savedConfig.backupChargingOnly
        backupVideos = savedConfig.backupVideos
        autoBackupEnabled = savedConfig.autoBackupEnabled
    }

    val SettingsBg = MaterialTheme.colorScheme.background
    val CardBg = MaterialTheme.colorScheme.surface
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val IconBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val DividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    val currentConfig = remember(
        selectedProvider, serverUrl, apiKeyOrToken, username, password,
        targetDirectory, backupWifiOnly, backupChargingOnly, backupVideos, autoBackupEnabled
    ) {
        SelfHostedConfig(
            provider = selectedProvider,
            serverUrl = serverUrl,
            apiKeyOrToken = apiKeyOrToken,
            username = username,
            password = password,
            targetDirectory = targetDirectory,
            backupWifiOnly = backupWifiOnly,
            backupChargingOnly = backupChargingOnly,
            backupVideos = backupVideos,
            autoBackupEnabled = autoBackupEnabled
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBg)
    ) {
        // Top Bar
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
                    tint = PrimaryText
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Self-Hosted Backup",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ═════════════════════════════════════════════════════════════
            // HERO STATUS CARD
            // ═════════════════════════════════════════════════════════════
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (savedConfig.isConfigured) AccentGreen.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (backupProgress.isRunning) Icons.Default.CloudUpload
                                    else if (savedConfig.isConfigured) Icons.Default.CloudDone
                                    else Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = if (savedConfig.isConfigured) AccentGreen else SecondaryText,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (backupProgress.isRunning) "Uploading Media..."
                                    else if (savedConfig.isConfigured) "Connected to ${savedConfig.provider.displayName}"
                                    else "Not Connected",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Text(
                                    text = if (backupProgress.isRunning && backupProgress.currentFileName.isNotEmpty())
                                        backupProgress.currentFileName
                                    else "$backedUpCount of $totalMedia items backed up",
                                    fontSize = 12.sp,
                                    color = SecondaryText
                                )
                            }
                        }
                    }

                    if (backupProgress.isRunning) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { backupProgress.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (backupProgress.isRunning) {
                            OutlinedButton(
                                onClick = { viewModel.cancelBackup() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Pause Backup")
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (savedConfig.isConfigured) {
                                        viewModel.triggerBackupNow()
                                    } else {
                                        Toast.makeText(context, "Please configure and test connection first", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Backup Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═════════════════════════════════════════════════════════════
            // PLATFORM SELECTOR
            // ═════════════════════════════════════════════════════════════
            Text(
                text = "CHOOSE PLATFORM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGreen,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelfHostedProvider.values().forEach { provider ->
                    val isSelected = selectedProvider == provider
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedProvider = provider },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AccentGreen.copy(alpha = 0.12f) else CardBg
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AccentGreen) else null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = when (provider) {
                                    SelfHostedProvider.IMMICH -> Icons.Default.Cloud
                                    SelfHostedProvider.NEXTCLOUD -> Icons.Default.Dns
                                    SelfHostedProvider.WEBDAV -> Icons.Default.Storage
                                },
                                contentDescription = null,
                                tint = if (isSelected) AccentGreen else SecondaryText,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = when (provider) {
                                    SelfHostedProvider.IMMICH -> "Immich"
                                    SelfHostedProvider.NEXTCLOUD -> "Nextcloud"
                                    SelfHostedProvider.WEBDAV -> "WebDAV"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PrimaryText else SecondaryText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═════════════════════════════════════════════════════════════
            // SERVER CONNECTION CREDENTIALS
            // ═════════════════════════════════════════════════════════════
            Text(
                text = "SERVER CREDENTIALS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGreen,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Server URL
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL") },
                        placeholder = {
                            Text(
                                when (selectedProvider) {
                                    SelfHostedProvider.IMMICH -> "https://immich.myhome.net:2283"
                                    SelfHostedProvider.NEXTCLOUD -> "https://cloud.myserver.com"
                                    SelfHostedProvider.WEBDAV -> "https://nas.local:5006/photos"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = AccentGreen)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            focusedLabelColor = AccentGreen
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedProvider == SelfHostedProvider.IMMICH) {
                        // Immich API Key
                        OutlinedTextField(
                            value = apiKeyOrToken,
                            onValueChange = { apiKeyOrToken = it },
                            label = { Text("Immich API Key") },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, tint = AccentGreen)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                focusedLabelColor = AccentGreen
                            )
                        )
                    } else {
                        // Username
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = AccentGreen)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                focusedLabelColor = AccentGreen
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password / App Token
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password or App Token") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentGreen)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                focusedLabelColor = AccentGreen
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Target Directory
                        OutlinedTextField(
                            value = targetDirectory,
                            onValueChange = { targetDirectory = it },
                            label = { Text("Remote Target Folder") },
                            leadingIcon = {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = AccentGreen)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                focusedLabelColor = AccentGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Test Connection Result feedback
                    when (val state = connectionState) {
                        is ConnectionTestState.Testing -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentGreen)
                                Spacer(Modifier.width(10.dp))
                                Text("Pinging server...", fontSize = 13.sp, color = PrimaryText)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        is ConnectionTestState.Success -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentGreen.copy(alpha = 0.15f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Connected (${state.version}, ${state.latencyMs}ms latency)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        is ConnectionTestState.Error -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    state.message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        ConnectionTestState.Idle -> {}
                    }

                    // Test Connection & Save Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testConnection(currentConfig) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
                        ) {
                            Text("Test Ping")
                        }

                        Button(
                            onClick = {
                                viewModel.saveConfig(currentConfig)
                                Toast.makeText(context, "Server configuration saved", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Text("Save Server", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═════════════════════════════════════════════════════════════
            // SYNC & BACKUP PREFERENCES
            // ═════════════════════════════════════════════════════════════
            Text(
                text = "BACKUP RULES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGreen,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Auto Backup toggle
                    BackupToggleRow(
                        icon = Icons.Default.Refresh,
                        title = "Automatic Periodic Backup",
                        subtitle = "Sync newly captured photos every 6 hours",
                        checked = autoBackupEnabled,
                        onCheckedChange = {
                            autoBackupEnabled = it
                            viewModel.saveConfig(currentConfig.copy(autoBackupEnabled = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Wi-Fi Only toggle
                    BackupToggleRow(
                        icon = Icons.Default.Wifi,
                        title = "Backup over Wi-Fi Only",
                        subtitle = "Do not consume cellular mobile data",
                        checked = backupWifiOnly,
                        onCheckedChange = {
                            backupWifiOnly = it
                            viewModel.saveConfig(currentConfig.copy(backupWifiOnly = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Charging Only toggle
                    BackupToggleRow(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Backup while Charging Only",
                        subtitle = "Preserve device battery during large transfers",
                        checked = backupChargingOnly,
                        onCheckedChange = {
                            backupChargingOnly = it
                            viewModel.saveConfig(currentConfig.copy(backupChargingOnly = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Backup Videos toggle
                    BackupToggleRow(
                        icon = Icons.Default.Videocam,
                        title = "Include Video Files",
                        subtitle = "Upload large video files alongside photos",
                        checked = backupVideos,
                        onCheckedChange = {
                            backupVideos = it
                            viewModel.saveConfig(currentConfig.copy(backupVideos = it))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun BackupToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentGreen = MaterialTheme.colorScheme.tertiary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryText
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
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
            modifier = Modifier.scale(0.8f)
        )
    }
}
