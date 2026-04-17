package co.juneai.june.ui.screen.sharing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * A themed sharing bottom sheet that matches the current app theme.
 * Shows a preview of the shared content, quick share targets, and share options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingBottomSheet(
    mediaUri: Uri? = null,
    mediaTitle: String? = null,
    onDismiss: () -> Unit,
    onShareVia: (Intent) -> Unit
) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bgColor,
        tonalElevation = 0.dp)
    {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ── Handle ───────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Preview Row ──────────────────────────────
            if (mediaUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardColor)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = mediaUri,
                        contentDescription = mediaTitle,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaTitle ?: "Photo",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Kiwi Gallery",
                            fontSize = 13.sp,
                            color = textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Quick Share Targets ─────────────────────
            Text(
                text = "SHARE VIA",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ShareTarget(
                    icon = Icons.AutoMirrored.Filled.Message,
                    label = "Message",
                    bgColor = accentColor,
                    textColor = textPrimary,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, mediaUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        onShareVia(intent)
                    }
                )
                ShareTarget(
                    icon = Icons.Default.Email,
                    label = "Email",
                    bgColor = accentColor,
                    textColor = textPrimary,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, mediaUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Shared from Kiwi Gallery")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        onShareVia(intent)
                    }
                )
                ShareTarget(
                    icon = Icons.Default.Link,
                    label = "Copy Link",
                    bgColor = accentColor,
                    textColor = textPrimary,
                    onClick = {
                        // Copy URI to clipboard
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            android.content.ClipData.newUri(context.contentResolver, "Shared Media", mediaUri)
                        )
                    }
                )
                ShareTarget(
                    icon = Icons.Default.MoreHoriz,
                    label = "More",
                    bgColor = accentColor,
                    textColor = textPrimary,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, mediaUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        onShareVia(Intent.createChooser(intent, "Share via"))
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = dividerColor)

            Spacer(modifier = Modifier.height(12.dp))

            // ── Additional Options ──────────────────────
            ShareOptionRow(
                icon = Icons.Default.ContentCopy,
                label = "Copy Photo",
                textColor = textPrimary,
                accentColor = accentColor,
                onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newUri(context.contentResolver, "Copied Media", mediaUri)
                    )
                    onDismiss()
                }
            )
            ShareOptionRow(
                icon = Icons.Default.Share,
                label = "AirDrop-style Share",
                textColor = textPrimary,
                accentColor = accentColor,
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, mediaUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    onShareVia(Intent.createChooser(intent, "Share"))
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShareTarget(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(bgColor.copy(alpha = 0.15f))
                .border(1.dp, bgColor.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = bgColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = textColor.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    label: String,
    textColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}
