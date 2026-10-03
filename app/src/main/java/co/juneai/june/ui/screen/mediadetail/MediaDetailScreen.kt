package co.juneai.june.ui.screen.mediadetail

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import co.juneai.june.ml.portrait.PortraitLightingStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import co.juneai.june.domain.model.MediaDetail
import coil.compose.AsyncImage
import co.juneai.june.ui.animation.sharedMediaElement
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
    LaunchedEffect(state.mediaIds, state.currentIndex) {
        if (state.mediaIds.isNotEmpty() && !initialScrollDone) {
            val target = state.currentIndex.coerceIn(0, state.mediaIds.lastIndex)
            if (pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
            initialScrollDone = true
        }
    }

    var lastHandledPage by remember { mutableStateOf(-1) }
    LaunchedEffect(pagerState.currentPage, initialScrollDone) {
        if (initialScrollDone &&
            state.mediaIds.isNotEmpty() &&
            pagerState.currentPage in state.mediaIds.indices &&
            pagerState.currentPage != lastHandledPage
        ) {
            lastHandledPage = pagerState.currentPage
            val pageMediaId = state.mediaIds.getOrNull(pagerState.currentPage)
            if (pageMediaId != null && pageMediaId != state.media?.id) {
                viewModel.onPageChanged(pagerState.currentPage, albumId)
            }
        }
    }

    var showControls by remember { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                        zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
                    } catch (_: Exception) { "" }
                } ?: ""
            }
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
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedMediaElement(key = "media_${pageMedia.id}", zIndexInOverlay = 1f),
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
                            MediaContent(
                                media = pageMedia,
                                onToggleControls = { showControls = !showControls }
                            )
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
                MediaContent(
                    media = state.media,
                    onToggleControls = { showControls = !showControls }
                )
            }

            // ── Floating Frosted Top Bar ────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                MediaDetailTopBar(
                    onBack = { onBack() },
                    onShare = { viewModel.onEvent(MediaDetailEvent.Share) },
                    onInfo = { viewModel.onEvent(MediaDetailEvent.ToggleInfo) },
                    onMoreOptions = { viewModel.onEvent(MediaDetailEvent.MoreOptions) },
                    dateText = dateText,
                    timeText = timeText
                )
            }

            // ── Floating Frosted Bottom Bar ─────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                MediaDetailBottomBar(
                    onEvent = viewModel::onEvent,
                    isFavorite = state.media?.isFavorite ?: false,
                    onMap = { viewModel.showOnMap() },
                    onLiftSubject = {
                        state.media?.uri?.let { uri ->
                            try {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    val bitmap = BitmapFactory.decodeStream(stream)
                                    if (bitmap != null) {
                                        viewModel.liftSubjectFromBitmap(bitmap)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    },
                    onPortraitMode = {
                        state.media?.uri?.let { uri ->
                            try {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    val bitmap = BitmapFactory.decodeStream(stream)
                                    if (bitmap != null) {
                                        viewModel.openPortraitMode(bitmap)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                )
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
                    aiMetadata = state.aiMetadata,
                    onCopyText = { text -> viewModel.copyOcrText(text) },
                    onDismiss = viewModel::dismissInfo
                )
            }

            // ── Subject Lift / Sticker Maker Sheet ──────────
            if (state.isSubjectLiftActive && state.subjectCutoutBitmap != null) {
                SubjectLiftBottomSheet(
                    cutoutBitmap = state.subjectCutoutBitmap!!,
                    onShareSticker = {
                        viewModel.saveCutoutToGallery(state.subjectCutoutBitmap!!) { uri ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Sticker"))
                        }
                    },
                    onSaveCutout = {
                        viewModel.saveCutoutToGallery(state.subjectCutoutBitmap!!) {
                            Toast.makeText(context, "Sticker saved to KiwiStickers folder", Toast.LENGTH_SHORT).show()
                            viewModel.dismissSubjectLift()
                        }
                    },
                    onDismiss = { viewModel.dismissSubjectLift() }
                )
            }

            // ── Studio Portrait / Depth Bokeh Sheet ─────────
            if (state.isPortraitModeActive) {
                PortraitBokehBottomSheet(
                    renderedBitmap = state.portraitRenderedBitmap,
                    blurIntensity = state.portraitBlurIntensity,
                    currentStyle = state.portraitLightingStyle,
                    isRendering = state.isPortraitRendering,
                    onBlurChange = { blur ->
                        state.media?.uri?.let { uri ->
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)?.let { bmp ->
                                    viewModel.updatePortraitSettings(bmp, blur, state.portraitLightingStyle)
                                }
                            }
                        }
                    },
                    onStyleChange = { style ->
                        state.media?.uri?.let { uri ->
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)?.let { bmp ->
                                    viewModel.updatePortraitSettings(bmp, state.portraitBlurIntensity, style)
                                }
                            }
                        }
                    },
                    onSaveCopy = {
                        state.portraitRenderedBitmap?.let { bmp ->
                            viewModel.savePortraitEnhancedCopy(bmp) {
                                Toast.makeText(context, "Enhanced portrait saved to gallery", Toast.LENGTH_SHORT).show()
                                viewModel.closePortraitMode()
                            }
                        }
                    },
                    onDismiss = { viewModel.closePortraitMode() }
                )
            }
        }
    }
}

@Composable
fun MediaContent(
    media: MediaDetail?,
    onToggleControls: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls
            ),
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
    var isRevealed by remember(media.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = media.uri,
            contentDescription = media.title ?: "Image",
            modifier = Modifier
                .fillMaxSize()
                .sharedMediaElement(key = "media_${media.id}", zIndexInOverlay = 1f),
            contentScale = ContentScale.Fit
        )

        if (media.isNsfw && !isRevealed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { isRevealed = true },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Sensitive content",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sensitive Content Hidden",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This media was flagged by on-device content protection.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { isRevealed = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Tap to Reveal", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
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

@Composable
fun MediaDetailTopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit = {},
    onMoreOptions: () -> Unit,
    dateText: String = "",
    timeText: String = ""
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF1E1C22).copy(alpha = 0.82f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ios_back),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                if (dateText.isNotEmpty()) {
                    Text(
                        text = dateText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (timeText.isNotEmpty()) {
                        Text(
                            text = timeText,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            IconButton(onClick = onShare) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ios_share),
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onInfo) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ios_info),
                    contentDescription = "Info",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onMoreOptions) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ios_more),
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MediaDetailBottomBar(
    onEvent: (MediaDetailEvent) -> Unit,
    isFavorite: Boolean,
    onMap: () -> Unit,
    onLiftSubject: () -> Unit = {},
    onPortraitMode: () -> Unit = {}
) {
    val heartScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFavorite) 1.25f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "heartScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF1E1C22).copy(alpha = 0.90f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onEvent(MediaDetailEvent.Share) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ios_share),
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // ── Computational Photography: Subject Lift ──
            IconButton(onClick = onLiftSubject) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8E8CD8).copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Lift Subject",
                        tint = Color(0xFFC7C5FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Computational Photography: Studio Portrait ──
            IconButton(onClick = onPortraitMode) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Portrait Blur",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Interactive Heart Favorite with Spring Bounce ──
            IconButton(onClick = { onEvent(MediaDetailEvent.ToggleFavorite) }) {
                Icon(
                    painter = painterResource(
                        id = if (isFavorite) R.drawable.ic_ios_heart_filled
                        else R.drawable.ic_ios_heart
                    ),
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFF3B30) else Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .scale(heartScale)
                )
            }

            IconButton(onClick = { onEvent(MediaDetailEvent.Delete) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ios_trash),
                    contentDescription = "Delete",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Subject Lift (Sticker Maker) Bottom Sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectLiftBottomSheet(
    cutoutBitmap: Bitmap,
    onShareSticker: () -> Unit,
    onSaveCutout: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16151A),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✨ Isolated Subject Cutout",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Subject isolated on-device. Ready to share as transparent sticker.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Cutout Preview with glowing shimmer border
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF222028))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    bitmap = cutoutBitmap.asImageBitmap(),
                    contentDescription = "Subject Cutout",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onShareSticker,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E8CD8))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Sticker", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onSaveCutout,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Cutout", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Studio Portrait Mode (Depth Bokeh) Bottom Sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortraitBokehBottomSheet(
    renderedBitmap: Bitmap?,
    blurIntensity: Float,
    currentStyle: PortraitLightingStyle,
    isRendering: Boolean,
    onBlurChange: (Float) -> Unit,
    onStyleChange: (PortraitLightingStyle) -> Unit,
    onSaveCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16151A),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }

                Text(
                    text = "Studio Portrait Blur",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = onSaveCopy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Save Copy", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live Preview Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (renderedBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = renderedBitmap.asImageBitmap(),
                        contentDescription = "Portrait Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                if (isRendering) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Rendering...", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Depth Bokeh Slider
            val fStop = when {
                blurIntensity > 0.8f -> "f/1.4"
                blurIntensity > 0.6f -> "f/2.0"
                blurIntensity > 0.4f -> "f/2.8"
                blurIntensity > 0.2f -> "f/4.0"
                else -> "f/8.0"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aperture Depth ($fStop)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(blurIntensity * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = blurIntensity,
                onValueChange = onBlurChange,
                valueRange = 0.05f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                    inactiveTrackColor = Color(0xFF2C2A34)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Studio Lighting Styles Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PortraitLightingStyle.values().forEach { style ->
                    val isSelected = currentStyle == style
                    FilterChip(
                        selected = isSelected,
                        onClick = { onStyleChange(style) },
                        label = {
                            Text(
                                text = when (style) {
                                    PortraitLightingStyle.NATURAL_BOKEH -> "Natural"
                                    PortraitLightingStyle.STAGE_MONO -> "Stage Mono"
                                    PortraitLightingStyle.STUDIO_SPOTLIGHT -> "Spotlight"
                                    PortraitLightingStyle.WARM_SUNSET -> "Warm Sunset"
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF24222C),
                            labelColor = Color.White.copy(alpha = 0.75f)
                        ),
                        border = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
    aiMetadata: MediaAiMetadata? = null,
    onCopyText: (String) -> Unit = {},
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

                // ── On-Device AI Insights ──────────────────
                if (aiMetadata != null) {
                    InfoSection(title = "On-Device AI Intelligence") {
                        aiMetadata.primaryObject?.let { primaryObj ->
                            InfoRow(
                                label = "Primary Subject / Scene",
                                value = primaryObj.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            )
                        }
                        if (aiMetadata.faceCount > 0) {
                            val peopleLabel = if (aiMetadata.personNames.isNotEmpty()) {
                                "${aiMetadata.faceCount} (${aiMetadata.personNames.joinToString(", ")})"
                            } else {
                                "${aiMetadata.faceCount} detected"
                            }
                            InfoRow(label = "People / Faces", value = peopleLabel)
                        }
                        if (aiMetadata.sharpnessScore > 0f) {
                            val qualityLabel = if (aiMetadata.isBlurry) "Motion Blur / Soft" else "Crisp (${(aiMetadata.aestheticScore * 100).toInt()}%)"
                            InfoRow(label = "Photo Quality", value = qualityLabel)
                        }
                        InfoRow(
                            label = "Content Safety",
                            value = if (aiMetadata.isNsfw) "Sensitive Content" else "Safe"
                        )

                        // Extracted OCR Text with Copy Action
                        aiMetadata.ocrText?.let { text ->
                            if (text.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Extracted Text (OCR)",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF2C2B2F), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = text.take(300) + if (text.length > 300) "..." else "",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = { onCopyText(text) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Copy Text",
                                                    fontSize = 12.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Audio & Speech Recognition Moments
                        if (aiMetadata.audioMoments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Audio & Speech Recognition",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2C2B2F), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                aiMetadata.audioMoments.take(15).forEach { moment ->
                                    val minutes = moment.timestampMs / 60000
                                    val seconds = (moment.timestampMs % 60000) / 1000
                                    val timeStr = String.format("%d:%02d", minutes, seconds)
                                    val isSpeech = moment.transcript.isNotBlank()

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            color = if (isSpeech) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF007AFF).copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isSpeech) Icons.Default.Mic else Icons.Default.SmartDisplay,
                                                    contentDescription = null,
                                                    tint = if (isSpeech) Color(0xFF34D399) else Color(0xFF60A5FA),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = timeStr,
                                                    color = if (isSpeech) Color(0xFF34D399) else Color(0xFF60A5FA),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isSpeech) "\"${moment.transcript}\"" else moment.audioEvents,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                onClick = { onSave(title, if (description.isBlank()) null else description) },
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