package co.juneai.june.ui.screen.foryou

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import co.juneai.june.ui.screen.albums.AlbumsViewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.R
import co.juneai.june.data.model.MediaItem
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ForYouScreen(
    onMediaClick: (Long) -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val media = uiState.allMedia

    val recentMemories = remember(media) {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        media.filter { it.dateTaken > sevenDaysAgo }.take(10)
    }

    val featured = remember(media) {
        media.filter { !it.isVideo }
            .sortedByDescending { it.size }
            .take(4)
    }

    val videos = remember(media) {
        media.filter { it.isVideo }.take(8)
    }

    val albumGroups = remember(media) {
        media.filter { it.albumName.isNotBlank() }
            .groupBy { it.albumName }
            .entries
            .sortedByDescending { it.value.size }
            .take(4)
            .map { (name, items) -> name to items.take(6) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item { ForYouTopBar() }

        if (recentMemories.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.recent_memories),
                    stringResource(R.string.memories, recentMemories.size)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentMemories) { item ->
                        MemoryCard(item, onClick = { onMediaClick(item.id) })
                    }
                }
            }
        }

        if (featured.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionHeader(
                    stringResource(R.string.featured_photos),
                    stringResource(R.string.featured, featured.size)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    featured.forEachIndexed { index, item ->
                        FeaturedPhotoCard(
                            mediaItem = item,
                            label = if (index == 0) stringResource(R.string.best_shot) else stringResource(
                                R.string.highlight
                            ),
                            onClick = { onMediaClick(item.id) }
                        )
                    }
                }
            }
        }

        if (videos.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionHeader(
                    stringResource(R.string.videos),
                    stringResource(R.string.clips, videos.size)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(videos) { item ->
                        VideoCard(item, onClick = { onMediaClick(item.id) })
                    }
                }
            }
        }

        if (albumGroups.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))

                SectionHeader(
                    stringResource(R.string.albums_in_section),
                    stringResource(R.string.collections, albumGroups.size)
                )
            }
            albumGroups.forEach { (albumName, albumMedia) ->
                item {
                    AlbumRow(
                        albumName = albumName,
                        items = albumMedia,
                        onMediaClick = onMediaClick
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        if (media.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.no_media_yet),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.take_some_photos_to_see_them_here),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForYouTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_ios_search),
            contentDescription = stringResource(R.string.search),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(R.string.for_you),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = stringResource(R.string.more),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SectionHeader(title: String, badge: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (badge.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = badge,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        Text(
            text = stringResource(R.string.see_all),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MemoryCard(mediaItem: MediaItem, onClick: () -> Unit) {
    val dateStr = remember(mediaItem.dateTaken) {
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        sdf.format(Date(mediaItem.dateTaken))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(mediaItem.uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(12.dp))
        )
        Text(
            text = mediaItem.albumName.ifBlank { mediaItem.name.ifBlank { stringResource(R.string.photo) } },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = dateStr,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun FeaturedPhotoCard(
    mediaItem: MediaItem,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(mediaItem.uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        ),
                        startY = 100f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = mediaItem.albumName.ifBlank { mediaItem.name.ifBlank { stringResource(R.string.untitled) } },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VideoCard(mediaItem: MediaItem, onClick: () -> Unit) {
    val durationStr = remember(mediaItem.duration) {
        mediaItem.duration?.let { ms ->
            val secs = (ms / 1000).toInt()
            String.format(Locale.US, "%d:%02d", secs / 60, secs % 60)
        } ?: ""
    }

    Box(
        modifier = Modifier
            .width(180.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(mediaItem.uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.play),
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
        )

        if (durationStr.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = durationStr,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AlbumRow(albumName: String, items: List<MediaItem>, onMediaClick: (Long) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = albumName,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onMediaClick(item.id) }
                )
            }
        }
    }
}