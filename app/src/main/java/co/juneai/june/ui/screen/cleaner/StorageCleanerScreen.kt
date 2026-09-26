package co.juneai.june.ui.screen.cleaner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import co.juneai.june.data.model.MediaItem
import co.juneai.june.data.repository.DuplicateGroup
import coil.compose.AsyncImage
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCleanerScreen(
    onNavigateBack: () -> Unit,
    onMediaClick: (Long, Long?) -> Unit = { _, _ -> },
    viewModel: StorageCleanerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val result = uiState.analysisResult

    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            viewModel.onOsTrashApproved()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.pendingTrashIntentSender.collect { intentSender ->
            trashLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Storage Cleaner",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startScan() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF141218)
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.selectedItemsToTrash.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    color = Color(0xFF1E1C22),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${uiState.selectedItemsToTrash.size} items selected",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Ready to move to Trash",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.clearSelection() }) {
                                Text("Clear", color = Color.White.copy(alpha = 0.7f))
                            }
                            Button(
                                onClick = { viewModel.showConfirmTrashDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Trash Selected", color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF141218)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category Tabs
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = Color(0xFF141218),
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp
            ) {
                CleanerTab.values().forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = {
                            Text(
                                text = tab.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                fontSize = 13.sp,
                                fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isScanning -> {
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
                                text = "Scanning gallery for reclaimable storage...",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Analyzing exact byte checksums and visual embeddings",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    result != null -> {
                        when (uiState.selectedTab) {
                            CleanerTab.OVERVIEW -> {
                                OverviewTabContent(
                                    result = result,
                                    onSelectTab = { viewModel.setTab(it) },
                                    onCleanAllDuplicates = { viewModel.selectAllRedundantDuplicates() },
                                    formatBytes = viewModel::formatBytes
                                )
                            }
                            CleanerTab.EXACT_DUPLICATES -> {
                                DuplicateGroupsList(
                                    groups = result.exactDuplicates,
                                    selectedIds = uiState.selectedItemsToTrash,
                                    onToggleSelect = viewModel::toggleSelectItem,
                                    onMediaClick = onMediaClick,
                                    formatBytes = viewModel::formatBytes,
                                    emptyMessage = "No exact duplicates found!"
                                )
                            }
                            CleanerTab.SIMILAR_PHOTOS -> {
                                DuplicateGroupsList(
                                    groups = result.similarPhotoGroups,
                                    selectedIds = uiState.selectedItemsToTrash,
                                    onToggleSelect = viewModel::toggleSelectItem,
                                    onMediaClick = onMediaClick,
                                    formatBytes = viewModel::formatBytes,
                                    emptyMessage = "No similar burst photos found!"
                                )
                            }
                            CleanerTab.LARGE_VIDEOS -> {
                                MediaItemsGrid(
                                    items = result.largeVideos,
                                    selectedIds = uiState.selectedItemsToTrash,
                                    onToggleSelect = viewModel::toggleSelectItem,
                                    onMediaClick = onMediaClick,
                                    formatBytes = viewModel::formatBytes,
                                    emptyMessage = "No large videos found!"
                                )
                            }
                            CleanerTab.BLURRY -> {
                                MediaItemsGrid(
                                    items = result.blurryPhotos,
                                    selectedIds = uiState.selectedItemsToTrash,
                                    onToggleSelect = viewModel::toggleSelectItem,
                                    onMediaClick = onMediaClick,
                                    formatBytes = viewModel::formatBytes,
                                    emptyMessage = "No blurry photos detected!"
                                )
                            }
                            CleanerTab.SCREENSHOTS -> {
                                MediaItemsGrid(
                                    items = result.oldScreenshots,
                                    selectedIds = uiState.selectedItemsToTrash,
                                    onToggleSelect = viewModel::toggleSelectItem,
                                    onMediaClick = onMediaClick,
                                    formatBytes = viewModel::formatBytes,
                                    emptyMessage = "No old screenshots found!"
                                )
                            }
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No analysis results.", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    if (uiState.showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmDialog() },
            title = { Text("Move to Trash?", color = Color.White) },
            text = {
                Text(
                    "Moving ${uiState.selectedItemsToTrash.size} items to Kiwi's Trash bin. You can restore them anytime within 30 days before permanent deletion.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmTrashSelected() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmDialog() }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF2C2B2F)
        )
    }
}

@Composable
private fun OverviewTabContent(
    result: co.juneai.june.data.repository.CleanerAnalysisResult,
    onSelectTab: (CleanerTab) -> Unit,
    onCleanAllDuplicates: () -> Unit,
    formatBytes: (Long) -> String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF233B6E), Color(0xFF141F3C))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = Color(0xFF6EA8FE),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reclaimable Storage",
                            color = Color(0xFFBACCEB),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatBytes(result.totalReclaimableBytes),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Can be safely freed from duplicates and clutter",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onCleanAllDuplicates,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3872E0)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select All Duplicates for Clean", color = Color.White)
                    }
                }
            }
        }

        // Breakdown Cards
        item {
            CategoryTile(
                title = "Exact Duplicate Photos",
                subtitle = "${result.exactDuplicates.size} groups • ${formatBytes(result.exactDuplicates.sumOf { it.reclaimableBytes })} reclaimable",
                icon = Icons.Default.ContentCopy,
                color = Color(0xFF4E82EA),
                onClick = { onSelectTab(CleanerTab.EXACT_DUPLICATES) }
            )
        }

        item {
            CategoryTile(
                title = "Similar & Burst Photos",
                subtitle = "${result.similarPhotoGroups.size} groups • ${formatBytes(result.similarPhotoGroups.sumOf { it.reclaimableBytes })} reclaimable",
                icon = Icons.Default.PhotoLibrary,
                color = Color(0xFF9C6AE1),
                onClick = { onSelectTab(CleanerTab.SIMILAR_PHOTOS) }
            )
        }

        item {
            CategoryTile(
                title = "Large Video Files",
                subtitle = "${result.largeVideos.size} videos • ${formatBytes(result.largeVideos.sumOf { it.size })} total",
                icon = Icons.Default.SmartDisplay,
                color = Color(0xFFEA5B5B),
                onClick = { onSelectTab(CleanerTab.LARGE_VIDEOS) }
            )
        }

        item {
            CategoryTile(
                title = "Blurry / Poor Quality",
                subtitle = "${result.blurryPhotos.size} photos • ${formatBytes(result.blurryPhotos.sumOf { it.size })} total",
                icon = Icons.Default.VisibilityOff,
                color = Color(0xFFEAB14E),
                onClick = { onSelectTab(CleanerTab.BLURRY) }
            )
        }

        item {
            CategoryTile(
                title = "Old Screenshots",
                subtitle = "${result.oldScreenshots.size} screenshots • ${formatBytes(result.oldScreenshots.sumOf { it.size })} total",
                icon = Icons.Default.Image,
                color = Color(0xFF4EEAC6),
                onClick = { onSelectTab(CleanerTab.SCREENSHOTS) }
            )
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C22)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DuplicateGroupsList(
    groups: List<DuplicateGroup>,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onMediaClick: (Long, Long?) -> Unit,
    formatBytes: (Long) -> String,
    emptyMessage: String
) {
    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(groups, key = { it.groupId }) { group ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C22)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${group.items.size} Photos in Group",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${formatBytes(group.reclaimableBytes)} potential saving",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(group.items, key = { it.id }) { item ->
                                val isBest = item.id == group.bestItemId
                                val isSelected = selectedIds.contains(item.id)

                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isBest) 2.dp else if (isSelected) 2.dp else 0.dp,
                                            color = if (isBest) Color(0xFF4CAF50) else if (isSelected) MaterialTheme.colorScheme.error else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onMediaClick(item.id, null) }
                                ) {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = item.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    if (isBest) {
                                        Surface(
                                            color = Color(0xFF4CAF50),
                                            shape = RoundedCornerShape(bottomEnd = 6.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "BEST",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleSelect(item.id) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.error,
                                            uncheckedColor = Color.White.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .padding(4.dp)
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

@Composable
private fun MediaItemsGrid(
    items: List<MediaItem>,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onMediaClick: (Long, Long?) -> Unit,
    formatBytes: (Long) -> String,
    emptyMessage: String
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { item ->
                val isSelected = selectedIds.contains(item.id)
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.error else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onMediaClick(item.id, null) }
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(topStart = 4.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = formatBytes(item.size),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect(item.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.error,
                            uncheckedColor = Color.White.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}
