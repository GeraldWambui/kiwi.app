package co.juneai.june.ui.screen.mediadetail.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.R
import co.juneai.june.ui.screen.mediadetail.MediaDetailViewModel
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@SuppressLint("AutoboxingStateCreation", "NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    mediaId: Long? = null,
    albumId: Long? = null,
    viewModel: MediaDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToMedia: (Long) -> Unit = {},
    onNavigateToVideo: (uri: String, title: String?) -> Unit = { _, _ -> }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Initial load — runs once when mediaId changes
    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId!!)
        viewModel.preloadAdjacentMedia(mediaId, albumId)
    }

    // FIX: was LaunchedEffect(Unit) which fired before media loaded and always saw null.
    // Now keys on state.media so it fires as soon as the media object is available.
    LaunchedEffect(state.media) {
        state.media?.let { media ->
            if (media.isVideo && media.id == mediaId) {
                onNavigateToVideo(media.uri.toString(), media.title)
            }
        }
    }

    // FIX: Navigate back after successful delete. We track whether media was ever loaded
    // so we don't trigger onBack() on the initial null state before the first load.
    var hasLoadedMedia by remember { mutableStateOf(false) }
    LaunchedEffect(state.media, state.isLoading) {
        if (state.media != null) {
            hasLoadedMedia = true
        }
        // Deleted: media became null after we had loaded it, and no error is set
        if (hasLoadedMedia && state.media == null && !state.isLoading && state.error == null) {
            onBack()
        }
    }

    // FIX: Only navigate back for fatal load errors (media is null + error present).
    // Previously this was an unconditional expression in the composition block which
    // would call onBack() on every recompose while any error (even non-fatal GPS errors)
    // was set. Now it's a one-shot LaunchedEffect scoped to fatal failures only.
    LaunchedEffect(state.error) {
        if (state.error != null && state.media == null && !state.isLoading) {
            onBack()
        }
    }

    // FIX: Clear wallpaper intent after consuming it to prevent re-firing on recompose.
    LaunchedEffect(state.wallaperIntent) {
        state.wallaperIntent?.let { intent ->
            context.startActivity(intent)
            viewModel.dismissWallpaperIntent()
        }
    }

    LaunchedEffect(state.shareIntent) {
        state.shareIntent?.let { intent ->
            context.startActivity(Intent.createChooser(intent, "Share via"))
            viewModel.dismissShareSheet()
        }
    }

    LaunchedEffect(state.mapIntent) {
        state.mapIntent?.let { intent ->
            try {
                context.startActivity(intent)
                viewModel.dismissMapIntent()
                viewModel.dismissAllDialogs()
            } catch (_: ActivityNotFoundException) {
                viewModel.showToast(context, "Google Maps not installed")
            }
        }
    }

    val pagerState = rememberPagerState(
        pageCount = { if (state.mediaIds.isEmpty()) 1 else state.mediaIds.size },
        initialPage = 0 // always start at 0; the LaunchedEffect below scrolls to the real index
    )

    // FIX: rememberPagerState's initialPage is captured at first composition before
    // preloadAdjacentMedia resolves the real index. Scroll to the correct page as soon
    // as mediaIds (and therefore currentIndex) are known. One-shot via initialScrollDone.
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.mediaIds) {
        if (state.mediaIds.isNotEmpty() && !initialScrollDone) {
            val target = state.currentIndex.coerceIn(0, state.mediaIds.lastIndex)
            pagerState.scrollToPage(target)
            initialScrollDone = true
        }
    }

    // FIX: Replaced the fragile currentMediaId/LaunchedEffect(state.media?.id) tracking
    // with a simple lastHandledPage guard. The ViewModel's cache check prevents duplicate
    // work if the same page is somehow triggered twice.
    var lastHandledPage by remember { mutableStateOf(-1) }
    LaunchedEffect(pagerState.currentPage) {
        if (state.mediaIds.isNotEmpty() &&
            pagerState.currentPage in state.mediaIds.indices &&
            pagerState.currentPage != lastHandledPage
        ) {
            lastHandledPage = pagerState.currentPage
            viewModel.onPageChanged(pagerState.currentPage, albumId)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            val dateText = remember(state.media) {
                state.media?.dateTime?.let { timestamp ->
                    try {
                        val instant = Instant.ofEpochMilli(
                            if (timestamp < 10000000000L) timestamp * 1000 else timestamp
                        )
                        val zdt = instant.atZone(ZoneId.systemDefault())
                        zdt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                    } catch (_: Exception) { "" }
                } ?: ""
            }
            val timeText = remember(state.media) {
                state.media?.dateTime?.let { timestamp ->
                    try {
                        val instant = Instant.ofEpochMilli(
                            if (timestamp < 10000000000L) timestamp * 1000 else timestamp
                        )
                        val zdt = instant.atZone(ZoneId.systemDefault())
                        zdt.format(DateTimeFormatter.ofPattern("h:mm"))
                    } catch (_: Exception) { "" }
                } ?: ""
            }

            MediaDetailTopBar(
                onBack = { onBack() },
                onShare = { viewModel.onEvent(MediaDetailEvent.Share) },
                onMoreOptions = { viewModel.onEvent(MediaDetailEvent.MoreOptions) },
                dateText = dateText,
                timeText = timeText
            )
        },
        bottomBar = {
            MediaDetailBottomBar(
                onEvent = viewModel::onEvent,
                isFavorite = state.media?.isFavorite ?: false,
                onMap = { viewModel.showOnMap() }
            )
            if (state.showEditDialog) {
                EditMediaDialog(
                    media = state.media,
                    onSave = { title, description ->
                        viewModel.saveMediaEdit(title, description)
                    },
                    onDismiss = { viewModel.dismissEditDialog() }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.mediaIds.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageMediaId = state.mediaIds.getOrNull(page)
                    val pageMedia = if (pageMediaId != null) {
                        if (pageMediaId == state.media?.id) state.media
                        else state.cachedMediaMap[pageMediaId]
                    } else null

                    if (pageMedia != null) {
                        if (pageMedia.isVideo && pageMediaId != mediaId) {
                            // Adjacent video — show thumbnail with play overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .clickable {
                                        onNavigateToVideo(
                                            pageMedia.uri.toString(),
                                            pageMedia.title
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = pageMedia.uri,
                                    contentDescription = pageMedia.title ?: "Video",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_video_play),
                                        contentDescription = "Play video",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        } else {
                            MediaContent(pageMedia)
                        }
                    } else {
                        // Placeholder while this page's data is being fetched
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        )
                    }
                }
            } else {
                // mediaIds not yet populated — render the primary media directly
                MediaContent(state.media)
            }

            if (state.showMoreOptionsDialog) {
                MoreOptionsDialog(
                    onSetWallpaper = { viewModel.setAsWallpaper() },
                    onUseAsContact = { },
                    onPrint = { },
                    onRotateLeft = { viewModel.rotateImage(clockwise = false) },
                    onRotateRight = { viewModel.rotateImage(clockwise = true) },
                    onCrop = { },
                    onShowMap = { viewModel.showOnMap() },
                    onCopy = { viewModel.copyToClipboard() },
                    onAddToAlbum = { },
                    onDismiss = { viewModel.dismissAllDialogs() }
                )
            }

            if (state.showDeleteDialog) {
                DeleteConfirmationDialog(
                    onConfirm = { viewModel.confirmDelete() },
                    onDismiss = { viewModel.dismissDeleteDialog() }
                )
            }

            if (state.showInfoSheet) {
                MediaInfoBottomSheet(
                    media = state.media,
                    onDismiss = viewModel::dismissInfo
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Media content
// ---------------------------------------------------------------------------

@Composable
fun MediaContent(media: MediaDetail?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (media != null) {
            if (media.isVideo) {
                VideoPlayer(media)
            } else {
                ImageViewer(media)
            }
        }
    }
}

/**
 * FIX: Removed detectTransformGestures / graphicsLayer entirely.
 *
 * The original implementation consumed ALL pointer input (including horizontal drags)
 * via detectTransformGestures, which prevented the HorizontalPager from receiving swipe
 * events — making the image appear "draggable" instead of swiping to the next page.
 * Pan had no bounds clamping either, so the image could be dragged fully off-screen.
 *
 * Now the image is a plain, non-interactive AsyncImage. The pager owns all swipe gestures.
 */
@Composable
fun ImageViewer(media: MediaDetail) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = media.uri,
            contentDescription = media.title ?: "Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun VideoPlayer(media: MediaDetail) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = media.uri,
            contentDescription = media.title ?: "Video",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_video_play),
                contentDescription = "Play video",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        media.duration?.let { duration ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = formatDuration(duration),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top / bottom bars
// ---------------------------------------------------------------------------

@Composable
fun MediaDetailTopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
    onMoreOptions: () -> Unit,
    dateText: String = "",
    timeText: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ios_back),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            if (dateText.isNotEmpty()) {
                Text(
                    text = dateText,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (timeText.isNotEmpty()) {
                    Text(
                        text = timeText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        IconButton(onClick = onMoreOptions) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ios_more),
                contentDescription = "More",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun MediaDetailBottomBar(
    onEvent: (MediaDetailEvent) -> Unit,
    isFavorite: Boolean,
    onMap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onEvent(MediaDetailEvent.Share) }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ios_share),
                contentDescription = "Share",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = { onEvent(MediaDetailEvent.Edit) }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ios_edit),
                contentDescription = "Edit",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = { onEvent(MediaDetailEvent.ToggleFavorite) }) {
            Icon(
                painter = painterResource(
                    id = if (isFavorite) R.drawable.ic_ios_heart_filled
                    else R.drawable.ic_ios_heart
                ),
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFF3B30) else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = { onEvent(MediaDetailEvent.Delete) }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ios_trash),
                contentDescription = "Delete",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = { onEvent(MediaDetailEvent.ToggleInfo) }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ios_info),
                contentDescription = "Info",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = { onEvent(MediaDetailEvent.MoreOptions) }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ios_more),
                contentDescription = "More",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun BottomActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    iconTint: Color = Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Info bottom sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoBottomSheet(
    media: MediaDetail?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1B1F),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Details",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (media != null) {
                InfoSection(title = "File") {
                    InfoRow(label = "Name", value = media.title ?: "Untitled")
                    InfoRow(label = "Size", value = media.size ?: "Unknown")
                    InfoRow(label = "Type", value = if (media.isVideo) "Video" else "Image")
                    media.resolution?.let {
                        InfoRow(label = "Resolution", value = it)
                    }
                }

                InfoSection(title = "Capture") {
                    InfoRow(label = "Date", value = formatDate(media.dateTime))
                    InfoRow(label = "Time", value = formatTime(media.dateTime))
                    media.location?.let {
                        InfoRow(label = "Location", value = it)
                    }
                }

                InfoSection(title = "Storage") {
                    InfoRow(label = "Album", value = media.albumName ?: "No album")
                    // FIX: Show the actual path from the media object instead of a hardcoded string
                    InfoRow(label = "Path", value = media.path ?: "Unknown")
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun InfoSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        content()
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete photo?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = "This photo will be deleted from your device. This action cannot be undone.",
                fontSize = 14.sp,
                color = Color.Gray
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(
                    text = "DELETE",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        },
        containerColor = Color.White,
        tonalElevation = 0.dp
    )
}

@Composable
fun MoreOptionsDialog(
    onSetWallpaper: () -> Unit,
    onUseAsContact: () -> Unit,
    onPrint: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onCrop: () -> Unit,
    onShowMap: () -> Unit,
    onCopy: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("More Options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoreOptionItem("Set as wallpaper", Icons.Default.Wallpaper, onSetWallpaper)
                MoreOptionItem("Use as contact photo", Icons.Default.Person, onUseAsContact)
                MoreOptionItem("Print", Icons.Default.Print, onPrint)
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                MoreOptionItem("Rotate left", Icons.AutoMirrored.Filled.RotateLeft, onRotateLeft)
                MoreOptionItem("Rotate right", Icons.AutoMirrored.Filled.RotateRight, onRotateRight)
                MoreOptionItem("Crop", Icons.Default.Crop, onCrop)
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                MoreOptionItem("Show on map", Icons.Default.Map, onShowMap)
                MoreOptionItem("Copy", Icons.Default.ContentCopy, onCopy)
                MoreOptionItem("Add to album", Icons.Default.Album, onAddToAlbum)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun MoreOptionItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun EditMediaDialog(
    media: MediaDetail?,
    onSave: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(media?.title ?: "") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Media") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, description.ifEmpty { null }) },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@SuppressLint("NewApi")
private fun formatDate(timestamp: Long): String {
    return try {
        val date = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    } catch (_: Exception) { "" }
}

@SuppressLint("NewApi")
private fun formatTime(timestamp: Long): String {
    return try {
        val time = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        time.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { "" }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
    } else {
        String.format("%d:%02d", minutes, seconds % 60)
    }
}