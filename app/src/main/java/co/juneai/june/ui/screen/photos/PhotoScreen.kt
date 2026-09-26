package co.juneai.june.ui.screen.photos

import android.annotation.SuppressLint
import android.net.Uri
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.TextField
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CrueltyFree
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import co.juneai.june.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.data.model.AlbumItem
import co.juneai.june.data.model.MediaItem
import co.juneai.june.data.model.SearchResult
import co.juneai.june.domain.model.Particle
import co.juneai.june.ui.screen.albums.AlbumsViewModel
import co.juneai.june.ui.screen.search.SearchMode
import co.juneai.june.ui.screen.vault.VaultViewModel
import co.juneai.june.ui.theme.KiwiBright
import co.juneai.june.ui.theme.KiwiCore
import co.juneai.june.ui.theme.KiwiDeep
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@SuppressLint("AutoboxingStateCreation")
@Composable
fun PhotoScreen(
    onMediaClick: (Long, Long?) -> Unit,
    onNavigateToVideo: (uri: String, title: String?) -> Unit = { _, _ -> },
    onNavigateToVideoEditor: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAlbums: () -> Unit = {},
    onVoiceSearch: () -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSource by remember { mutableStateOf(MediaSource.ALL) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() }
    )

    val displayedMedia = remember(uiState.filteredMedia, selectedSource) {
        when (selectedSource) {
            MediaSource.ALL -> uiState.filteredMedia
            MediaSource.CAMERA -> uiState.filteredMedia.filter {
                it.albumName.contains("Camera", true)
            }
        }
    }

    val media = when (uiState.searchMode) {
        is SearchMode.None -> displayedMedia
        else -> uiState.searchResults
    }

    val albums = if (uiState.searchMode != SearchMode.None) {
        uiState.searchResults
            .groupBy { it.albumName }
    } else {
        uiState.allAlbums
    }

    var isScrolling by remember { mutableStateOf(false) }


    val groupedMedia = remember(media) { groupByDay(media) }


    val selection = remember { SelectionState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showMoveCopySheet by remember { mutableStateOf(false) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var openSettings by remember { mutableStateOf(false) }




    var albumName by remember { mutableStateOf("") }


    val filteredMedia = remember(media, selectedSource) {
        when (selectedSource) {
            MediaSource.ALL -> media
            MediaSource.CAMERA ->
                media.filter { it.albumName == "Camera" }
        }
    }


    Scaffold(
        topBar = {
            if (selection.isSelectionMode) {
                SelectionTopBar(
                    count = selection.selected.size,
                    totalCount = displayedMedia.size,
                    selection = selection,
                    allMediaIds = displayedMedia.map { it.id },
                    onClose = { selection.clear() }
                )
            } else {
                LibraryTopBar(
                    viewModel = viewModel,
                    onNavigateToAlbums = onNavigateToAlbums,
                    onNavigateToSettings = onNavigateToSettings,
                    onVoiceSearch = onVoiceSearch
                )
            }
        },
        bottomBar = {
            if (selection.isSelectionMode) {
                SelectionBottomBar(
                    displayedUri = displayedMedia.map { it.uri },
                    mimeType = displayedMedia.map { it.mimeType },
                )
            }
        }
    ) { padding ->

        val searchMode = uiState.searchMode

        if (searchMode is SearchMode.ConfirmDelete) {

            DeletePreviewSheet(
                items = searchMode.items,
                onConfirm = {
                    // Delete selected items viewModel.deleteItems(media)
                },
                onCancel = {
                    viewModel.clearSearch()
                }
            )
        }

        Box(modifier = Modifier.pullRefresh(pullRefreshState)) {
            val currentAlbum = uiState.selectedAlbum

            if (currentAlbum != null) {
                Column(modifier = Modifier.padding(padding)) {
                    AlbumHeader(
                        album = currentAlbum,
                        onBack = { viewModel.selectAlbum(null) }
                    )

                    PhotosGrid(
                        media = displayedMedia,
                        selection = selection,
                        modifier = Modifier.fillMaxSize(),
                        onScrollStateChange = { isScrolling = it },
                        onMediaClick = onMediaClick,
                        onNavigateToVideo = onNavigateToVideo
                    )
                }
            } else {
                PhotosGrid(
                    media = displayedMedia,
                    selection = selection,
                    modifier = Modifier.padding(padding),
                    onScrollStateChange = { isScrolling = it },
                    onMediaClick = onMediaClick,
                    onNavigateToVideo = onNavigateToVideo
                )
            }

            PullRefreshIndicator(
                refreshing = uiState.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (showCreateDialog) {
            CreateAlbumDialog(
                onDismiss = { showCreateDialog = false }
            ) { album ->
                showCreateDialog = false
                albumName = album
            }
        }

        if (showMoveCopySheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoveCopySheet = false }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Button(onClick = { /* move */ }) {
                        Text("Move")
                    }
                    Button(onClick = { /* copy */ }) {
                        Text("Copy")
                    }
                    TextButton(onClick = { showMoveCopySheet = false }) {
                        Text("Cancel")
                    }
                }
            }
        }


    }
}

@Composable
fun CreateAlbumDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var albumName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center
        ){
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Create an album",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = albumName,
                        onValueChange = { albumName = it },
                        label = { Text("Album name") }
                    )

                    Row {
                        TextButton(
                            onClick = onDismiss
                        ) {
                            Text(
                                "Cancel"
                            )
                        }

                        TextButton(
                            onClick = { onConfirm(albumName) }
                        ) {
                            Text(
                                "Ok"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionTopBar(
    count: Int,
    totalCount: Int,
    selection: SelectionState,
    allMediaIds: List<Long>,
    onClose: () -> Unit
) {
    val isAllSelected = selection.isAllSelected(totalCount)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, null)
        }
        Text(
            stringResource(R.string.items_selected, count),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.weight(1f))

        IconButton(onClick = {
            if (isAllSelected) {
                selection.clear()
            } else {
                selection.selectAll(allMediaIds)
            }
        }) {
            if(isAllSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_ios_select_all),
                    null,
                    tint = Color.Blue
                )
            }else{
                Icon(
                    painter = painterResource(R.drawable.ic_ios_select_all),
                    null
                )
            }
        }
    }
}

@Composable
fun SelectionBottomBar(
    viewModel: VaultViewModel = hiltViewModel(),
    displayedUri: List<String>,
    mimeType: List<String>
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SelectionAction(Icons.Default.Share, "Share", onClick = {})
        SelectionAction(Icons.Default.CrueltyFree, "Creativity", onClick = {})
        SelectionAction(Icons.Default.Lock, "Add to Vault", onClick = {})
        SelectionAction(Icons.Default.Delete, "Delete", onClick = {})
    }
}

@Composable
fun MediaGridItem(
    item: MediaItem,
    selection: SelectionState,
    onMediaClick: (MediaItem) -> Unit
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = {
                    if (selection.selected.isNotEmpty()) {
                        selection.toggle(item.id)
                    } else {
                        onMediaClick(item)
                    }
                },
                onLongClick = {
                    selection.toggle(item.id)
                }
            )

    ) {
        if (item.isNsfw) {
            // ── NSFW: Low-res pixelated thumbnail + glassmorphism overlay ──
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.parse(item.uri))
                    .size(24) // Tiny size for natural pixelation blur
                    .allowHardware(false)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Glassmorphism frosted overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .background(Color.Black.copy(alpha = 0.20f))
            )
            // Shield icon centered
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Sensitive content",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sensitive",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (item.isVideo) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.parse(item.uri))
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = rememberAsyncImagePainter(
                    model = item.uri
                )
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(
                    LocalContext.current
                )
                    .data(Uri.parse(item.uri))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Video duration badge (only for non-NSFW or show on top of blur)
        if (item.isVideo) {
            item.duration?.let { duration ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = formatDuration(duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        if (selection.selected.contains(item.id)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF007AFF),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        } else if (selection.isSelectionMode) {
            Icon(
                Icons.Default.RadioButtonUnchecked,
                null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        String.format("%d:%02d:%02d", hours, mins, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
private fun LibraryTopBar(
    viewModel: AlbumsViewModel,
    onNavigateToAlbums: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onVoiceSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val headerBgColor =
        MaterialTheme.colorScheme.background.copy(alpha = if (isLight) 0.8f else 1.0f)
    val headerBorderColor = MaterialTheme.colorScheme.outline
    // Stitch Pill Toggle colors
    val pillContainerBg =
        if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.White.copy(
            alpha = 0.05f
        )
    val pillActiveBg = MaterialTheme.colorScheme.primary // bg-kiwi / bg-[#6B8E23]
    val pillActiveText =
        if (isLight) Color.White else MaterialTheme.colorScheme.onPrimary // text-white / text-[#0A0F09]
    val pillInactiveText =
        if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.secondary // kiwi-dark/60 or kiwi-light


    // Debounce search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(300)
            viewModel.onSearchTyping(searchQuery)
        } else {
            viewModel.clearSearch()
        }
    }

    Column(
        modifier = Modifier
            .background(headerBgColor)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp)
    ) {
        AnimatedVisibility(
            visible = searchActive,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBgColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        searchActive = false
                        searchQuery = ""
                        viewModel.clearSearch()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search photos, videos…",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 16.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }

                // Search results summary
                val searchResults = uiState.searchResults
                if (searchQuery.isNotEmpty()) {
                    if (searchResults.isNotEmpty()) {
                        Text(
                            text = "${searchResults.size} results found",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (uiState.searchMode !is SearchMode.None) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "No results for \"$searchQuery\"",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = !searchActive) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { searchActive = true },
                        modifier = Modifier
                            .background(
                                color = if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color.White.copy(
                                    alpha = 0.1f
                                ),
                                shape = CircleShape
                            )
                            .size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ios_search),
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.kiwi_gallery),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onVoiceSearch() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ios_mic),
                                contentDescription = stringResource(R.string.voice_search),
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ios_account),
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                    // View Toggle Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .background(pillContainerBg, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Photos Pill (Active)
                            Box(
                                modifier = Modifier
                                    .background(pillActiveBg, CircleShape)
                                    .padding(horizontal = 32.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.photos),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = pillActiveText
                                )
                            }

                            // Albums Pill (Inactive)
                            Box(
                                modifier = Modifier
                                    .clickable { onNavigateToAlbums() }
                                    .padding(horizontal = 32.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.albums),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = pillInactiveText
                                )
                            }
                        }
                    }

                HorizontalDivider(color = headerBorderColor, thickness = 1.dp)
            }
        }
    }
}



@Composable
private fun GalleryOverflowMenu(
    expanded: Boolean,
    context: OverflowMenuContext,
    onDismiss: () -> Unit,
    onAction: (OverflowAction) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .width(220.dp)
            .background(Color.White, RoundedCornerShape(20.dp)),
        offset = DpOffset(x = (-8).dp, y = 8.dp)
    ) {
        when (context) {
            OverflowMenuContext.PHOTOS -> PhotosMenuItems(onAction)
            OverflowMenuContext.ALBUMS -> AlbumsMenuItems(onAction)
        }
    }
}

@Composable
private fun PhotosMenuItems(onAction: (OverflowAction) -> Unit) {
    MenuItem("Index") { onAction(OverflowAction.CreateAlbum) }
    MenuItem("Video editor") { onAction(OverflowAction.VideoEditor) }
    MenuItem("Collage") { onAction(OverflowAction.Collage) }
    MenuItem("Free up space") { onAction(OverflowAction.FreeUpSpace) }
    MenuItem("Sort and view") { onAction(OverflowAction.SortAndView) }
    MenuItem("Create an album") { onAction(OverflowAction.CreateAlbum) }
    MenuItem("Settings") { onAction(OverflowAction.Settings) }
}

@Composable
private fun AlbumsMenuItems(onAction: (OverflowAction) -> Unit) {
    MenuItem("Create an album") { onAction(OverflowAction.CreateAlbum) }
    MenuItem("Sort and view") { onAction(OverflowAction.SortAndView) }
    MenuItem("Settings") { onAction(OverflowAction.Settings) }
}



@Composable
private fun MenuItem(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        fontSize = 16.sp,
        color = Color.Black
    )
}

@SuppressLint("NewApi")
@Composable
private fun PhotosGrid(
    media: List<MediaItem>,
    selection: SelectionState,
    modifier: Modifier = Modifier,
    onScrollStateChange: (Boolean) -> Unit,
    onMediaClick: (Long, Long?) -> Unit,
    onNavigateToVideo: (uri: String, title: String?) -> Unit = { _, _ -> }
) {
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }
    val gridItems = remember(media) { buildGridItems(media) }
    val gridState = rememberLazyGridState()
    val (photoCount, videoCount) = remember(media) {
        val photos = media.count { !it.mimeType.startsWith("video") }
        val videos = media.count { it.mimeType.startsWith("video") }
        photos to videos
    }

    LaunchedEffect(gridState.isScrollInProgress) {
        onScrollStateChange(gridState.isScrollInProgress)
    }

    Box {
        Column(modifier = modifier) {

            Text(
                text = stringResource(R.string.photos_and_videos, photoCount, videoCount),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
                color = Color.Gray,
                fontSize = 13.sp
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalArrangement = Arrangement.spacedBy(1.5.dp),
                //contentPadding = PaddingValues(bottom = 72.dp),
                state = gridState
            ) {
                items(
                    items = gridItems,
                    key = { it ->
                        when (it) {
                            is GridItem.Header -> "header_${it.date}"
                            is GridItem.Media -> "media_${it.item.id}"
                        }
                    },
                    span = { item ->
                        if (item is GridItem.Header)
                            GridItemSpan(maxLineSpan)
                        else
                            GridItemSpan(1)
                    }
                ) { item ->

                    when (item) {
                        is GridItem.Header -> {
                            val itemsForDate = media.filter {
                                LocalDate.ofInstant(
                                    Instant.ofEpochMilli(
                                        if (it.dateTaken < 10000000000L) it.dateTaken * 1000 else it.dateTaken
                                    ),
                                    ZoneId.systemDefault()
                                ) == item.date
                            }

                            DateHeader(
                                header = item,
                                allItemsForDate = itemsForDate,
                                selection = selection
                            )
                        }


                        is GridItem.Media -> {
                            MediaGridItem(
                                item = item.item,
                                selection = selection,
                                onMediaClick = { clicked ->
                                    if (clicked.isVideo) {
                                        onNavigateToVideo(clicked.uri, clicked.name.ifEmpty { null })
                                    } else {
                                        onMediaClick(clicked.id, clicked.albumId)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            FastScroller(
                gridState = gridState,
                totalItems = gridItems.size
            )

            if (previewItem != null) {
                Dialog(onDismissRequest = { previewItem = null }) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable { previewItem = null },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = previewItem!!.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FastScroller(
    gridState: LazyGridState,
    totalItems: Int
) {
    val isScrolling by remember {
        derivedStateOf { gridState.isScrollInProgress }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        label = "scrollAlpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            ScrollThumb(
                gridState = gridState,
                totalItems = totalItems,
                alpha = alpha
            )
        }
    }
}

@SuppressLint("FrequentlyChangingValue")
@Composable
private fun ScrollThumb(
    gridState: LazyGridState,
    totalItems: Int,
    alpha: Float
) {
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .width(20.dp)
            .fillMaxHeight()
    ) {
        val heightPx = constraints.maxHeight.toFloat()

        val progress =
            gridState.firstVisibleItemIndex / maxOf(totalItems - 1, 1).toFloat()

        val offsetY = heightPx * progress

        Box(
            modifier = Modifier
                .offset { IntOffset(0, offsetY.toInt()) }
                .size(width = 12.dp, height = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = alpha))
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val newProgress = (offsetY + delta) / heightPx
                        val targetIndex =
                            (newProgress * totalItems).toInt()
                                .coerceIn(0, totalItems - 1)

                        scope.launch {
                            gridState.scrollToItem(targetIndex)
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    null,
                    tint = Color.Black
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    null,
                    tint = Color.Black
                )
            }
        }
    }
}

private sealed interface GridItem {
    data class Header(val date: LocalDate, val title: String) : GridItem
    data class Media(val item: MediaItem) : GridItem
}

@SuppressLint("NewApi")
private fun buildGridItems(media: List<MediaItem>): List<GridItem> {
    return media
        .groupBy { item ->
            val timestampMs = if (item.dateTaken < 10000000000L) {
                item.dateTaken * 1000
            } else {
                item.dateTaken
            }

            LocalDate.ofInstant(
                Instant.ofEpochMilli(timestampMs),
                ZoneId.systemDefault()
            )
        }
        .toSortedMap(compareByDescending { it })
        .flatMap { (date, items) ->
            buildList {
                val displayText = when {
                    date.isEqual(LocalDate.now()) -> "Today"
                    date.isEqual(LocalDate.now().minusDays(1)) -> "Yesterday"
                    else -> date.format(
                        DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                    )
                }

                add(GridItem.Header(date, displayText))
                items
                    .sortedByDescending { it.dateTaken }
                    .forEach { add(GridItem.Media(it)) }
            }
        }
}

@Composable
private fun DateHeader(
    header: GridItem.Header,
    allItemsForDate: List<MediaItem>,
    selection: SelectionState
) {
    val ids = remember(allItemsForDate) { allItemsForDate.map { it.id } }

    val allSelected = ids.isNotEmpty() && ids.all { selection.selected.contains(it) }
    val anySelected = ids.any { selection.selected.contains(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (anySelected) Color(0xFFE8F0FE) else Color.Transparent
            )
            .clickable {
                if (allSelected) {
                    ids.forEach { selection.toggle(it) }
                } else {
                    ids.forEach {
                        if (!selection.selected.contains(it)) {
                            selection.toggle(it)
                        }
                    }
                }
            }
            .padding(start = 12.dp, top = 12.dp, bottom = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = header.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        if (anySelected) {
            AnimatedCheckRipple(
                checked = allSelected,
                onToggle = {
                    if (allSelected) {
                        ids.forEach { selection.toggle(it) }
                    } else {
                        ids.forEach {
                            if (!selection.selected.contains(it)) {
                                selection.toggle(it)
                            }
                        }
                    }
                }
            )
        }

    }
}


@Composable
private fun AnimatedCheckRipple(
    checked: Boolean,
    onToggle: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.9f,
        animationSpec = spring(stiffness = 500f),
        label = "checkScale"
    )

    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (checked)
                Icons.Default.CheckCircle
            else
                Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) Color(0xFF1A73E8) else Color.Gray,
            modifier = Modifier.scale(scale)
        )
    }
}

@Composable
fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    var trigger by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .clickable {
                trigger++
                onClick()
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = label)
            Spacer(Modifier.height(4.dp))
            Text(text = label, fontSize = 12.sp)
        }

        CinematicParticleSystem(trigger = trigger)
    }
}


@Composable
private fun CinematicParticleSystem(trigger: Int) {

    if (trigger == 0) return

    val progress = remember { Animatable(0f) }

    val particleCount = 24

    val particles = remember(trigger) {
        List(particleCount) {
            val angle = Random.nextFloat() * 360f
            val size = Random.nextFloat() * 5f + 2f
            Particle(angle, size)
        }
    }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = 850,
                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
            )
        )
    }

    Canvas(
        modifier = Modifier
            .clipToBounds()
    ) {

        val center = Offset(size.width / 2, size.height / 2)

        // Max travel distance = half of shortest side
        val maxRadius = min(size.width, size.height) / 2f

        /* ------------------ Core Flash ------------------ */

        val coreRadius = max(0.1f, maxRadius * 0.6f * progress.value)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    KiwiBright.copy(alpha = 0.5f * (1f - progress.value)),
                    Color.Transparent
                ),
                center = center,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = center
        )

        /* ------------------ Shockwave ------------------ */

        drawCircle(
            color = KiwiBright.copy(alpha = 0.4f * (1f - progress.value)),
            radius = maxRadius * progress.value,
            center = center,
            style = Stroke(width = 4f * (1f - progress.value))
        )

        /* ------------------ Particles ------------------ */

        particles.forEach { particle ->

            val angleRad = Math.toRadians(particle.angle.toDouble())

            val travel = maxRadius * progress.value

            val x = center.x + (cos(angleRad) * travel).toFloat()
            val y = center.y + (sin(angleRad) * travel).toFloat()

            val alpha = 1f - progress.value
            val scale = 1.1f - progress.value

            // subtle streak
            drawLine(
                color = KiwiCore.copy(alpha = alpha * 0.4f),
                start = center,
                end = Offset(x, y),
                strokeWidth = 1.5f * scale
            )

            val particleRadius = max(0.1f, particle.size * 2f * scale)

            // glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        KiwiBright.copy(alpha = alpha),
                        KiwiDeep.copy(alpha = alpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = particleRadius * 2f
                ),
                radius = particleRadius * 2f,
                center = Offset(x, y)
            )

            // core
            drawCircle(
                color = KiwiBright.copy(alpha = alpha),
                radius = particleRadius,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun AlbumHeader(
    album: AlbumItem,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = album.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${album.count} items",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.LightGray.copy(alpha = 0.3f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletePreviewSheet(
    items: List<SearchResult>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onCancel) {
        Column(Modifier.padding(16.dp)) {

            Text(
                "Delete ${items.size} items?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(300.dp)
            ) {
                items(items) { result ->
                    Box {
                        AsyncImage(
                            model = result.image.uri,
                            contentDescription = null
                        )

                        Text(
                            text = result.explanation,
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(4.dp)
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = onConfirm) { Text("Delete") }
            }
        }
    }
}


@SuppressLint("NewApi")
fun groupByDay(media: List<MediaItem>): Map<LocalDate, List<MediaItem>> {
    return media.groupBy {
        Instant.ofEpochMilli(it.dateTaken)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
}
