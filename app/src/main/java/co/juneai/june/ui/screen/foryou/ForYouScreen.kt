package co.juneai.june.ui.screen.foryou

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.R
import co.juneai.june.data.model.MediaItem
import co.juneai.june.data.repository.MemoryStory
import co.juneai.june.data.repository.PersonHighlight
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import co.juneai.june.ui.animation.sharedMediaElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ForYouScreen(
    onMediaClick: (Long) -> Unit = {},
    viewModel: ForYouViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val data = uiState.data

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item { ForYouTopBar() }

        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        data?.let { forYouData ->
            // 1. "On This Day" Time Travel Memories
            if (forYouData.onThisDayMemories.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "On This Day",
                        badge = "${forYouData.onThisDayMemories.size} Memories",
                        icon = Icons.Default.AutoAwesome
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(forYouData.onThisDayMemories, key = { it.id }) { story ->
                            MemoryStoryCard(story = story, onClick = { onMediaClick(story.coverItem.id) })
                        }
                    }
                }
            }

            // 2. People & Faces Highlights (Google Photos Style Circle Avatars)
            if (forYouData.peopleHighlights.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(26.dp))
                    SectionHeader(
                        title = "People & Pets",
                        badge = "${forYouData.peopleHighlights.size} Identified",
                        icon = Icons.Default.Face
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(forYouData.peopleHighlights, key = { it.clusterId }) { person ->
                            PersonAvatarCard(person = person, onClick = { onMediaClick(person.coverMedia.id) })
                        }
                    }
                }
            }

            // 3. Spatio-Temporal Trips & Event Highlights
            if (forYouData.tripEvents.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(26.dp))
                    SectionHeader(
                        title = "Trips & Getaways",
                        badge = "${forYouData.tripEvents.size} Highlights"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(forYouData.tripEvents, key = { it.id }) { trip ->
                            TripHighlightCard(trip = trip, onClick = { onMediaClick(trip.coverItem.id) })
                        }
                    }
                }
            }

            // 4. Featured Best Shots & Aesthetic Highlights
            if (forYouData.featuredHighlights.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionHeader(
                        title = "Best Shots & Highlights",
                        badge = "${forYouData.featuredHighlights.size} Curated"
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        forYouData.featuredHighlights.take(3).forEachIndexed { index, item ->
                            FeaturedPhotoCard(
                                mediaItem = item,
                                label = if (index == 0) "BEST SHOT" else "FEATURED",
                                onClick = { onMediaClick(item.id) }
                            )
                        }
                    }
                }
            }

            // 5. Smart Themed Collections (Receipts & Documents, Portraits)
            if (forYouData.smartCollections.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionHeader(
                        title = "Smart Collections",
                        badge = "${forYouData.smartCollections.size} Collections"
                    )
                }
                forYouData.smartCollections.forEach { collection ->
                    item {
                        SmartCollectionRow(
                            story = collection,
                            onMediaClick = onMediaClick
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }

            // 6. Recent Videos & Clips
            if (forYouData.recentVideos.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionHeader(
                        title = "Videos & Clips",
                        badge = "${forYouData.recentVideos.size} Clips"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(forYouData.recentVideos) { item ->
                            VideoCard(item, onClick = { onMediaClick(item.id) })
                        }
                    }
                }
            }
        }

        if (!uiState.isLoading && (data == null || (data.onThisDayMemories.isEmpty() && data.featuredHighlights.isEmpty()))) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Kiwi Memories",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "As you take more photos, Kiwi will automatically generate trips, flashbacks, and best shots completely offline.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "For You",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = "Options",
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    badge: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (badge.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = badge,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryStoryCard(story: MemoryStory, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(230.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(story.coverItem.uri)
                .crossfade(true)
                .build(),
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .sharedMediaElement(key = "media_${story.coverItem.id}")
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 0f
                    )
                )
        )

        // Story Pill
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "FLASHBACK",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Title and Subtitle
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = story.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = story.subtitle,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PersonAvatarCard(person: PersonHighlight, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(person.coverMedia.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = person.personName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = person.personName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${person.photoCount} photos",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TripHighlightCard(trip: MemoryStory, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(trip.coverItem.uri)
                .crossfade(true)
                .build(),
            contentDescription = trip.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .sharedMediaElement(key = "media_${trip.coverItem.id}")
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        ),
                        startY = 40f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = trip.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trip.subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
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
            .clip(RoundedCornerShape(18.dp))
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
                .fillMaxSize()
                .sharedMediaElement(key = "media_${mediaItem.id}")
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
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
                text = mediaItem.albumName.ifBlank { mediaItem.name.ifBlank { "Untitled" } },
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
private fun SmartCollectionRow(story: MemoryStory, onMediaClick: (Long) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = story.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = story.subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(story.items.take(8)) { item ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(105.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .sharedMediaElement(key = "media_${item.id}")
                        .clickable { onMediaClick(item.id) }
                )
            }
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