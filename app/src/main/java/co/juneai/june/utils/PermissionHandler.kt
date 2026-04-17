package co.juneai.june.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PermissionHandler(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(checkPermissions(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = checkPermissions(context)
        if (!hasPermission) {
            permissionDenied = true
        }
    }

    // Re-check on resume (user may have granted in Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = checkPermissions(context)
                hasPermission = granted
                if (granted) permissionDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-request on first launch
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            requestPermissions(permissionLauncher)
        }
    }

    if (hasPermission) {
        content()
    } else {
        // Show a blank background while the system dialog is up
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        )
    }

    // iOS-style non-dismissible dialog when permission is denied
    if (permissionDenied && !hasPermission) {
        Dialog(
            onDismissRequest = { /* Non-dismissible */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "\"June\" Would Like to\nAccess Your Photos",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "This app needs access to your photo library to browse and manage your photos and videos.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF666666),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(20.dp))

                HorizontalDivider(Modifier, thickness = 0.5.dp, color = Color(0xFFE0E0E0))

                // Open Settings button
                TextButton(
                    onClick = { openAppSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Text(
                        text = "Open Settings",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF007AFF)
                    )
                }

                HorizontalDivider(Modifier, thickness = 0.5.dp, color = Color(0xFFE0E0E0))

                // Try Again button
                TextButton(
                    onClick = {
                        permissionDenied = false
                        requestPermissions(permissionLauncher)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                ) {
                    Text(
                        text = "Try Again",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF007AFF)
                    )
                }
            }
        }
    }
}

private fun checkPermissions(context: Context): Boolean {
    val readImages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val readVideo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val hasImageAccess = ContextCompat.checkSelfPermission(context, readImages) == PackageManager.PERMISSION_GRANTED
    val hasVideoAccess = ContextCompat.checkSelfPermission(context, readVideo) == PackageManager.PERMISSION_GRANTED

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val hasPartialAccess = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        return (hasImageAccess && hasVideoAccess) || hasPartialAccess
    }

    return hasImageAccess && hasVideoAccess
}

private fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        )
    } else {
        launcher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}