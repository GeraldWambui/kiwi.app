package co.juneai.june.ui.screen.people

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onNavigateBack: () -> Unit,
    onMediaClick: (Long, Long?) -> Unit = { _, _ -> },
    viewModel: PeopleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var renamingPerson by remember { mutableStateOf<PeoplePersonItem?>(null) }
    var mergingSourcePerson by remember { mutableStateOf<PeoplePersonItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.selectedPerson != null) uiState.selectedPerson!!.personName else "People & Pets",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectedPerson != null) {
                            viewModel.clearSelectedPerson()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (uiState.selectedPerson != null) {
                        IconButton(onClick = { renamingPerson = uiState.selectedPerson }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { mergingSourcePerson = uiState.selectedPerson }) {
                            Icon(
                                imageVector = Icons.Default.CallMerge,
                                contentDescription = "Merge",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = {
                            uiState.selectedPerson?.let {
                                viewModel.deletePerson(it.clusterId)
                                viewModel.clearSelectedPerson()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.recluster() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-Cluster",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF141218)
                )
            )
        },
        containerColor = Color(0xFF141218)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.people.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                uiState.selectedPerson != null -> {
                    // Person Details Grid
                    PersonPhotosGrid(
                        photos = uiState.selectedPersonPhotos,
                        onMediaClick = onMediaClick
                    )
                }

                uiState.people.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Faces Detected Yet",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Photos containing faces will automatically be grouped here after offline indexing.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    // People Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.people, key = { it.clusterId }) { person ->
                            PersonGridItem(
                                person = person,
                                onClick = { viewModel.selectPerson(person) },
                                onRenameClick = { renamingPerson = person },
                                onMergeClick = { mergingSourcePerson = person }
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    renamingPerson?.let { person ->
        var newName by remember { mutableStateOf(person.personName) }
        AlertDialog(
            onDismissRequest = { renamingPerson = null },
            title = { Text("Name Person", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Person Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renamePerson(person.clusterId, newName)
                        renamingPerson = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingPerson = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF2C2B2F)
        )
    }

    // Merge Dialog
    mergingSourcePerson?.let { source ->
        val candidates = uiState.people.filter { it.clusterId != source.clusterId }
        AlertDialog(
            onDismissRequest = { mergingSourcePerson = null },
            title = { Text("Merge '${source.personName}' with...", color = Color.White) },
            text = {
                if (candidates.isEmpty()) {
                    Text("No other people to merge with.", color = Color.White.copy(alpha = 0.7f))
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(candidates) { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.mergePeople(source.clusterId, target.clusterId)
                                        mergingSourcePerson = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarImage(
                                    avatarFile = target.avatarFile,
                                    coverMediaStoreId = target.coverMediaStoreId,
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = target.personName,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${target.photoCount} photos",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { mergingSourcePerson = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF2C2B2F)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PersonGridItem(
    person: PeoplePersonItem,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onMergeClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AvatarImage(
                avatarFile = person.avatarFile,
                coverMediaStoreId = person.coverMediaStoreId,
                size = 90.dp
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2B2F))
                    .clickable { onRenameClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = person.personName,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${person.photoCount} photos",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        androidx.compose.material3.DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color(0xFF2C2B2F))
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Rename", color = Color.White) },
                onClick = {
                    showMenu = false
                    onRenameClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                }
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Merge with Another Person", color = Color.White) },
                onClick = {
                    showMenu = false
                    onMergeClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.CallMerge, contentDescription = null, tint = Color.White)
                }
            )
        }
    }
}

@Composable
private fun AvatarImage(
    avatarFile: java.io.File?,
    coverMediaStoreId: Long,
    size: androidx.compose.ui.unit.Dp
) {
    val model = avatarFile ?: Uri.parse("content://media/external/images/media/$coverMediaStoreId")
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF2C2B2F))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Avatar",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun PersonPhotosGrid(
    photos: List<co.juneai.june.data.model.MediaItem>,
    onMediaClick: (Long, Long?) -> Unit
) {
    if (photos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(photos, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onMediaClick(item.id, null) }
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
