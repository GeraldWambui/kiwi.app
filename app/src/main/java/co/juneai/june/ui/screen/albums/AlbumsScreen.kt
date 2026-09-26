package co.juneai.june.ui.screen.albums

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.R
import co.juneai.june.data.model.AlbumItem
import co.juneai.june.data.model.AlbumType
import co.juneai.june.ui.theme.EarthCream
import co.juneai.june.ui.theme.EarthSand
import co.juneai.june.ui.theme.AccentColor
import co.juneai.june.ui.theme.BorderColor
import co.juneai.june.ui.theme.CardBg
import co.juneai.june.ui.theme.CardShadowColor
import co.juneai.june.ui.theme.CountColor
import co.juneai.june.ui.theme.TitleColor
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AlbumsScreen(
    initialAlbumName: String? = null,
    onAlbumClick: (AlbumItem) -> Unit = {},
    onCreateAlbum: () -> Unit = {},
    onNavigateToPhotos: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToPeople: () -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    onNavigateToCleaner: () -> Unit = {},
    onNavigateToAlbumDetail: (AlbumItem) -> Unit = {},
    onMediaClick: (Long, Long?) -> Unit = { _, _ -> },
    onNavigateToVideo: (uri: String, title: String?) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onVoiceSearch: () -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val albums = uiState.allAlbums
    val scope = rememberCoroutineScope()
    var showAllAlbums by remember { mutableStateOf(false) }
    val selectedAlbum = uiState.selectedAlbum
    val mainAlbums = albums.filter { it.type != AlbumType.MORE }
    val sharedAlbums = albums.filter { it.type == AlbumType.MORE }
    val displayAlbums = if (showAllAlbums) mainAlbums else mainAlbums.take(6)

    // Handle hardware/gesture back press when an album is open
    androidx.activity.compose.BackHandler(enabled = selectedAlbum != null) {
        viewModel.selectAlbum(null)
    }

    LaunchedEffect(initialAlbumName, albums) {
        if (initialAlbumName != null && selectedAlbum == null && albums.isNotEmpty()) {
            val album = albums.find { it.name.equals(initialAlbumName, ignoreCase = true) }
            if (album != null) {
                viewModel.selectAlbum(album)
            }
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = false,
        onRefresh = {
            scope.launch {
                delay(1000)
            }
            onNavigateToVault()
        }
    )

    if (selectedAlbum != null) {
        AlbumDetailView(
            album = selectedAlbum,
            viewModel = viewModel,
            onBack = { viewModel.selectAlbum(null) },
            onMediaClick = { mediaId ->
                val album = selectedAlbum
                val media = viewModel.uiState.value.filteredMedia.find { it.id == mediaId }
                if (media?.isVideo == true) {
                    onNavigateToVideo(media.uri, media.name.ifEmpty { null })
                } else {
                    onMediaClick(mediaId, album.id)
                }
            }
        )
        return
    }

    Box(
        modifier = Modifier.pullRefresh(pullRefreshState)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AlbumsTopBar(
                    onNavigateToPhotos = onNavigateToPhotos,
                    onNavigateToTrash = onNavigateToTrash,
                    onNavigateToSettings = onNavigateToSettings,
                    onVoiceSearch = onVoiceSearch
                )
            }

            items(displayAlbums, key = { it.id }) { album ->
                AlbumCard(album = album, onClick = {
                    viewModel.selectAlbum(album)
                })
            }

            // ── People & Pets Feature Tile ──────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onNavigateToPeople() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "People & Pets",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "View and organize detected face collections",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Photo Map Explorer Tile ─────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onNavigateToMap() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Photo Map Explorer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Explore your photos and travels geographically",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Storage Cleaner Tile ────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onNavigateToCleaner() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE91E63).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage Cleaner & Duplicates",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Find duplicate photos, large videos & free space",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!showAllAlbums && mainAlbums.size > 6) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { showAllAlbums = true }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.see_all_albums, mainAlbums.size),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (sharedAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(16.dp))
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SharedAlbumsSection(
                        albums = sharedAlbums,
                        onSeeAll = { showAllAlbums = true },
                        onAlbumClick = { album ->
                            viewModel.selectAlbum(album)
                        }
                    )
                }
            }
        }

        PullRefreshIndicator(
            refreshing = false,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
@Composable
fun AlbumDetailView(
    album: AlbumItem,
    viewModel: AlbumsViewModel,
    onBack: () -> Unit,
    onMediaClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewModel.selectAlbum(null)
                onBack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = album.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.items, uiState.filteredMedia.size),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(uiState.filteredMedia, key = { it.id }) { media ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onMediaClick(media.id) }
                ) {
                    AsyncImage(
                        model = media.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (media.isVideo) {
                        media.duration?.let { dur ->
                            val totalSec = dur / 1000
                            val m = totalSec / 60
                            val s = totalSec % 60

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
                                    text = String.format("%d:%02d", m, s),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun AlbumsTopBar(
    onNavigateToPhotos: () -> Unit,
    onNavigateToTrash: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onVoiceSearch: () -> Unit = {}
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val headerBgColor = MaterialTheme.colorScheme.background.copy(alpha = if (isLight) 0.8f else 1.0f)
    val headerBorderColor = MaterialTheme.colorScheme.outline
    val pillContainerBg = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f) else Color.White.copy(alpha=0.05f)
    val pillActiveBg = MaterialTheme.colorScheme.primary
    val pillActiveText = if (isLight) Color.White else MaterialTheme.colorScheme.onPrimary
    val pillInactiveText = if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f) else MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onVoiceSearch) {
                Icon(
                    painter = painterResource(R.drawable.ic_ios_mic),
                    contentDescription = stringResource(R.string.voice_search),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Text(
                text = "Collections",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateToTrash) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ios_trash),
                        contentDescription = stringResource(R.string.trash_bin),
                        tint = MaterialTheme.colorScheme.onSurface
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .background(pillContainerBg, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clickable { onNavigateToPhotos() }
                        .padding(horizontal = 32.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.photos),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pillInactiveText
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(pillActiveBg, CircleShape)
                        .padding(horizontal = 32.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.albums),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pillActiveText
                    )
                }
            }
        }
        HorizontalDivider(color = headerBorderColor, thickness = 1.dp)
    }
}

@Composable
fun AlbumCard(
    album: AlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = CardShadowColor,
                    spotColor = CardShadowColor
                )
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .border(
                    width = 1.dp,
                    color = BorderColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (album.hasMedia && album.coverUri != null) {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = album.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(EarthSand, EarthCream)
                            )
                        )
                )
            }

            if (album.isVideoFolder) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = album.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatCount(album.count),
                fontSize = 12.sp,
                color = CountColor,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
private fun SharedAlbumsSection(
    albums: List<AlbumItem>,
    onSeeAll: () -> Unit,
    onAlbumClick: (AlbumItem) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.shared_albums),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TitleColor
            )
            Text(
                text = stringResource(R.string.see_all),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AccentColor,
                letterSpacing = 0.4.sp,
                modifier = Modifier.clickable(onClick = onSeeAll)
            )
        }

        Spacer(Modifier.height(20.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                SharedAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) }
                )
            }
        }
    }
}

@Composable
private fun SharedAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(144.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = CardShadowColor,
                    spotColor = CardShadowColor
                )
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
        ) {
            if (album.hasMedia && album.coverUri != null) {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = album.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(EarthSand, EarthCream)
                            )
                        )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = album.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TitleColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@SuppressLint("DefaultLocale")
private fun formatCount(count: Int): String {
    return when {
        count >= 1000 -> String.format("%,d", count)
        else -> count.toString()
    }
}
