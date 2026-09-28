package co.juneai.june.ui.screen.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.juneai.june.navigation.Screens
import coil.compose.AsyncImage
import coil.request.ImageRequest
import co.juneai.june.ui.animation.sharedMediaElement
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onVoiceSearch: () -> Unit = {},
    onNavigateToMedia: (Long, Long?) -> Unit = { _, _ -> },
    onNavigateToVideo: (uri: String, title: String?, startTimeMs: Long?) -> Unit = { _, _, _ -> },
    onNavigateToRoute: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val pendingIntentAction by viewModel.pendingIntentAction.collectAsStateWithLifecycle()
    val isNsfwProtectionEnabled by viewModel.isNsfwProtectionEnabled.collectAsStateWithLifecycle()
    val peopleClusters by viewModel.peopleClusters.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf(initialQuery) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateTo -> onNavigateToRoute(event.route)
                is NavigationEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = if (event.undoAction != null) "Undo" else null,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        event.undoAction?.invoke()
                    }
                }
            }
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            searchQuery = initialQuery
            viewModel.search(initialQuery)
        }
    }

    // Filter results according to selectedCategory
    val filteredResults = remember(searchResults, selectedCategory) {
        when (selectedCategory) {
            SearchCategoryFilter.ALL -> searchResults
            SearchCategoryFilter.PHOTOS -> searchResults.filter { !it.media.isVideo }
            SearchCategoryFilter.VIDEOS -> searchResults.filter { it.media.isVideo }
            SearchCategoryFilter.DOCUMENTS -> searchResults.filter { it.ocrSnippet.isNotBlank() }
            SearchCategoryFilter.SCREENSHOTS -> searchResults.filter {
                it.media.albumName.contains("screenshot", true) || it.media.name.contains("screenshot", true)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF141218)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Top Search Bar (Google Photos Style) ────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search photos, people, places, 'July 2023'...",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.clearResults()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            IconButton(onClick = onVoiceSearch) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice search",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.search(searchQuery.trim())
                                keyboardController?.hide()
                            }
                        }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF2C2B2F),
                        focusedContainerColor = Color(0xFF1E1C22),
                        unfocusedContainerColor = Color(0xFF1E1C22),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Intent Action Confirmation Dialogs
            pendingIntentAction?.let { action ->
                when (action) {
                    is IntentAction.ConfirmDelete -> {
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissIntentAction() },
                            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            title = { Text("Move to Trash", color = Color.White) },
                            text = {
                                Column {
                                    Text("Found ${action.items.size} matching items for '${action.targetQuery}'. Move to Trash?", color = Color.White.copy(alpha = 0.85f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(action.items.take(8)) { item ->
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(item.uri).crossfade(true).build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.executePendingDelete(action.items) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Move ${action.items.size} to Trash")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.dismissIntentAction() }) {
                                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                                }
                            },
                            containerColor = Color(0xFF2C2B2F)
                        )
                    }
                    is IntentAction.ConfirmMoveToVault -> {
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissIntentAction() },
                            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            title = { Text("Move to Secure Vault", color = Color.White) },
                            text = {
                                Text("Found ${action.items.size} items matching '${action.targetQuery}'. Move to Secure Vault?", color = Color.White.copy(alpha = 0.85f))
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.executePendingMoveToVault(action.items) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Move to Vault")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.dismissIntentAction() }) {
                                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                                }
                            },
                            containerColor = Color(0xFF2C2B2F)
                        )
                    }
                }
            }

            // ── Main Content Area ──────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isSearching -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Searching offline intelligence...",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }

                    searchResults.isNotEmpty() -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Filter Chips Row
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(SearchCategoryFilter.values()) { category ->
                                    val isSelected = selectedCategory == category
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setCategoryFilter(category) },
                                        label = {
                                            Text(
                                                text = category.name.lowercase().replaceFirstChar { it.uppercase() },
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = Color.White,
                                            containerColor = Color(0xFF1E1C22),
                                            labelColor = Color.White.copy(alpha = 0.8f)
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }

                            // Results Grid
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredResults, key = { it.media.id }) { resultItem ->
                                    val item = resultItem.media
                                    val isBlurred = resultItem.isNsfw && isNsfwProtectionEnabled && !resultItem.isRevealed

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                if (isBlurred) {
                                                    viewModel.toggleReveal(item.id)
                                                } else if (item.isVideo) {
                                                    onNavigateToVideo(
                                                        item.uri,
                                                        item.name.ifEmpty { null },
                                                        resultItem.matchedTimestampMs
                                                    )
                                                } else {
                                                    onNavigateToMedia(item.id, null)
                                                }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(item.uri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .sharedMediaElement(key = "media_${item.id}")
                                        )

                                        if (isBlurred) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.65f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Shield,
                                                        contentDescription = "Sensitive",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Sensitive",
                                                        color = Color.White,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        }

                                        // Matched Moment in Video Pill
                                        if (resultItem.matchedTimestampMs != null) {
                                            val minutes = resultItem.matchedTimestampMs / 60000
                                            val seconds = (resultItem.matchedTimestampMs % 60000) / 1000
                                            val timeStr = String.format("%d:%02d", minutes, seconds)
                                            androidx.compose.material3.Surface(
                                                color = Color(0xFF007AFF).copy(alpha = 0.90f),
                                                shape = RoundedCornerShape(topStart = 6.dp),
                                                modifier = Modifier.align(Alignment.BottomEnd)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.SmartDisplay,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = timeStr,
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        if (resultItem.similarity in 0.01f..0.99f && !isBlurred && resultItem.matchedTimestampMs == null) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .background(
                                                        Color.Black.copy(alpha = 0.65f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                val percent = (resultItem.similarity * 100).toInt()
                                                Text(
                                                    text = "$percent%",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    searchError != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = searchError ?: "No results found",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 15.sp
                            )
                        }
                    }

                    else -> {
                        // ── Google Photos Search Home Feed ─────────
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // 1. People & Pets Row
                            if (peopleClusters.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "People & Pets",
                                                color = Color.White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            TextButton(onClick = { onNavigateToRoute(Screens.People.route) }) {
                                                Text("View all", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                            }
                                        }

                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            items(peopleClusters, key = { it.clusterId }) { cluster ->
                                                val avatarFile = File(context.filesDir, "avatars/avatar_${cluster.clusterId}.jpg")
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .width(72.dp)
                                                        .clickable {
                                                            searchQuery = cluster.personName
                                                            viewModel.searchByPerson(cluster.clusterId, cluster.personName)
                                                        }
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(68.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2C2B2F))
                                                            .border(1.5.dp, Color(0xFF3E3D42), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (avatarFile.exists()) {
                                                            AsyncImage(
                                                                model = avatarFile,
                                                                contentDescription = cluster.personName,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.Person,
                                                                contentDescription = null,
                                                                tint = Color.White.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(32.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = cluster.personName,
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Your Map Explorer Banner
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF1E3A2B), Color(0xFF13221A))
                                            )
                                        )
                                        .clickable { onNavigateToRoute(Screens.PhotoMap.route) }
                                        .padding(18.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Map,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Your Photo Map",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Explore your photos and travels geographically",
                                                color = Color(0xFFBCE3C8),
                                                fontSize = 12.sp
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // 3. Categories & Things Grid
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = "Categories & Things",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    val categories = listOf(
                                        Triple("Screenshots", Icons.Default.Image, Color(0xFF3872E0)),
                                        Triple("Videos", Icons.Default.Videocam, Color(0xFFE03838)),
                                        Triple("Documents & Receipts", Icons.Default.Description, Color(0xFF38B2E0)),
                                        Triple("Nature & Travel", Icons.Default.Landscape, Color(0xFF4CAF50)),
                                        Triple("Portraits & Selfies", Icons.Default.Face, Color(0xFFE09A38)),
                                        Triple("Blurry & Low Quality", Icons.Default.VisibilityOff, Color(0xFF9C38E0))
                                    )

                                    categories.chunked(2).forEach { row ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            row.forEach { (title, icon, color) ->
                                                Card(
                                                    onClick = {
                                                        searchQuery = title
                                                        viewModel.search(title)
                                                    },
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C22)),
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(14.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(color.copy(alpha = 0.2f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = null,
                                                                tint = color,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = title,
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
