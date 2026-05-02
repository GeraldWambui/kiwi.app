package co.juneai.june.ui.screen.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// SecureVaultScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureVaultScreen(
    onBack: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel()
) {
    val items              by viewModel.vaultItems.collectAsStateWithLifecycle()
    val isLoading          by viewModel.isLoading.collectAsStateWithLifecycle()
    val selectedItems      by viewModel.selectedItems.collectAsStateWithLifecycle()
    val isSelectionMode    by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val toastMessage       by viewModel.toastMessage.collectAsStateWithLifecycle()
    val errorMessage       by viewModel.errorMessage.collectAsStateWithLifecycle()
    val showDeleteConfirm  by viewModel.showDeleteConfirmation.collectAsStateWithLifecycle()
    val itemToDelete       by viewModel.itemToDelete.collectAsStateWithLifecycle()
    val selectedVaultItem  by viewModel.selectedVaultItem.collectAsStateWithLifecycle()
    val showDetailsDialog  by viewModel.showDetailsDialog.collectAsStateWithLifecycle()

    // Theme colours
    val colorBg         = MaterialTheme.colorScheme.background
    val colorOnSurface  = MaterialTheme.colorScheme.onSurface
    val colorMuted      = MaterialTheme.colorScheme.onSurfaceVariant
    val colorPrimary    = MaterialTheme.colorScheme.primary
    val colorTertiary   = MaterialTheme.colorScheme.tertiary
    val colorError      = MaterialTheme.colorScheme.error

    var showMenu by remember { mutableStateOf(false) }
    // Local state for the full-screen detail overlay
    var detailItem by remember { mutableStateOf<VaultItem?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    // Show snackbar for toast / error messages
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colorBg
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            Column(modifier = Modifier.fillMaxSize()) {

                // ── Top Bar ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isSelectionMode) viewModel.clearSelection() else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = colorOnSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = if (isSelectionMode)
                            "${selectedItems.size} selected"
                        else
                            "Secure Vault",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorOnSurface
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = colorOnSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isSelectionMode) "Exit selection" else "Select items") },
                                onClick = { viewModel.toggleSelectionMode(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Select all") },
                                onClick = { viewModel.selectAll(); showMenu = false }
                            )
                            if (isSelectionMode && selectedItems.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete selected permanently",
                                            color = colorError
                                        )
                                    },
                                    onClick = { viewModel.deleteMultiplePermanently(); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove selected from vault") },
                                    onClick = { viewModel.removeSelectedFromVault(); showMenu = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = { showMenu = false }
                            )
                        }
                    }
                }

                // ── Encryption notice ──────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = colorMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Items in your vault are end-to-end encrypted",
                        fontSize = 12.sp,
                        color = colorMuted
                    )
                }

                // ── Selection action bar ───────────────────────────────────
                AnimatedVisibility(
                    visible = isSelectionMode && selectedItems.isNotEmpty(),
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    SelectionActionBar(
                        count = selectedItems.size,
                        onRestoreAll = { viewModel.removeSelectedFromVault() },
                        onDeleteAll  = { viewModel.deleteMultiplePermanently() },
                        onClear      = { viewModel.clearSelection() }
                    )
                }

                // ── Content ────────────────────────────────────────────────
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colorPrimary)
                        }
                    }

                    items.isEmpty() -> {
                        EmptyVaultPlaceholder(colorMuted)
                    }

                    else -> {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp)
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp
                        ) {
                            items(items, key = { it.id }) { item ->
                                VaultGridItem(
                                    item = item,
                                    isSelected = item.id in selectedItems,
                                    isSelectionMode = isSelectionMode,
                                    onTap = {
                                        if (isSelectionMode) {
                                            viewModel.toggleItemSelection(item.id)
                                        } else {
                                            detailItem = item
                                        }
                                    },
                                    onLongPress = {
                                        if (!isSelectionMode) viewModel.toggleSelectionMode()
                                        viewModel.toggleItemSelection(item.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Full-screen detail overlay ─────────────────────────────────
            detailItem?.let { item ->
                MediaDetailOverlay(
                    item         = item,
                    details      = viewModel.getMediaDetails(item),
                    onClose      = { detailItem = null },
                    onDelete     = { viewModel.confirmDelete(item); detailItem = null },
                    onRestore    = {
                        viewModel.restoreToGallery(item) { success ->
                            if (success) detailItem = null
                        }
                    }
                )
            }
        }
    }

    if (showDeleteConfirm && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = colorError
                )
            },
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    "\"${itemToDelete!!.fileName}\" will be permanently deleted " +
                            "and cannot be recovered.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deletePermanently(itemToDelete!!) },
                    colors  = ButtonDefaults.buttonColors(containerColor = colorError)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VaultGridItem
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VaultGridItem(
    item: VaultItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val colorTertiary = MaterialTheme.colorScheme.tertiary
    val colorPrimary  = MaterialTheme.colorScheme.primary

    val aspectRatio = remember(item.id) {
        when ((item.id % 4).toInt()) {
            0    -> 0.75f
            1    -> 1.2f
            2    -> 1.0f
            else -> 0.85f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected)
                    Modifier.border(3.dp, colorTertiary, RoundedCornerShape(12.dp))
                else
                    Modifier
            )
            .pointerInput(isSelectionMode) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.fileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Encrypted-verified badge (top end)
        if (item.isVerified) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colorPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector    = Icons.Default.Shield,
                    contentDescription = "Encrypted",
                    tint           = Color.White,
                    modifier       = Modifier.size(13.dp)
                )
            }
        }

        // Selection checkbox (top start)
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) colorTertiary
                        else Color.White.copy(alpha = 0.55f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector    = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint           = Color.White,
                        modifier       = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MediaDetailOverlay – full-screen lightbox + action sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaDetailOverlay(
    item: VaultItem,
    details: MediaDetails,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit
) {
    val colorBg      = MaterialTheme.colorScheme.background
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurf  = MaterialTheme.colorScheme.onSurface
    val colorMuted   = MaterialTheme.colorScheme.onSurfaceVariant
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorError   = MaterialTheme.colorScheme.error

    var showInfoSheet by remember { mutableStateOf(false) }
    val sheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ── Full-resolution image ──────────────────────────────────────────
        AsyncImage(
            model              = item.uri,
            contentDescription = item.fileName,
            modifier           = Modifier
                .fillMaxSize()
                .clickable { /* consume tap so grid doesn't register */ },
            contentScale       = ContentScale.Fit
        )

        // ── Close button ───────────────────────────────────────────────────
        IconButton(
            onClick  = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector    = Icons.Default.Close,
                contentDescription = "Close",
                tint           = Color.White,
                modifier       = Modifier.size(20.dp)
            )
        }

        // ── File name label ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            Text(
                text     = item.fileName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color    = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.40f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // ── Bottom action bar ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.60f))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Info row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector    = Icons.Default.Shield,
                    contentDescription = null,
                    tint           = Color.White.copy(alpha = 0.7f),
                    modifier       = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text     = if (details.isEncrypted) "AES-256-GCM encrypted" else "Not encrypted",
                    fontSize = 12.sp,
                    color    = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text     = details.fileSize,
                    fontSize = 12.sp,
                    color    = Color.White.copy(alpha = 0.7f)
                )
            }

            // Action buttons
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info / details
                OutlinedButton(
                    onClick  = { showInfoSheet = true },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border   = BorderStroke(
                        1.dp, Color.White.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Details", fontSize = 13.sp)
                }

                // Restore to gallery
                Button(
                    onClick  = onRestore,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = colorPrimary)
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore", fontSize = 13.sp)
                }

                // Delete permanently
                Button(
                    onClick  = onDelete,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = colorError)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete", fontSize = 13.sp)
                }
            }
        }
    }

    // ── Info bottom sheet ──────────────────────────────────────────────────
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() }
        ) {
            MediaInfoSheetContent(details = details, onDismiss = { showInfoSheet = false })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MediaInfoSheetContent – metadata table inside bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediaInfoSheetContent(
    details: MediaDetails,
    onDismiss: () -> Unit
) {
    val colorMuted   = MaterialTheme.colorScheme.onSurfaceVariant
    val colorOnSurf  = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text       = "File details",
            fontSize   = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color      = colorOnSurf,
            modifier   = Modifier.padding(bottom = 16.dp)
        )

        InfoRow(label = "File name",  value = details.fileName)
        InfoRow(label = "Type",       value = details.type.name)
        InfoRow(label = "Size",       value = details.fileSize)
        InfoRow(label = "Resolution", value = details.resolution)
        InfoRow(label = "MIME type",  value = details.mimeType)
        InfoRow(label = "Added",      value = details.addedDate)
        InfoRow(label = "Encrypted",  value = if (details.isEncrypted) "Yes (AES-256-GCM)" else "No")
        InfoRow(label = "Vault path", value = details.path, isLast = true)

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick  = onDismiss,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isLast: Boolean = false) {
    val colorMuted  = MaterialTheme.colorScheme.onSurfaceVariant
    val colorOnSurf = MaterialTheme.colorScheme.onSurface
    val colorDiv    = MaterialTheme.colorScheme.outlineVariant

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text     = label,
                fontSize = 14.sp,
                color    = colorMuted,
                modifier = Modifier.weight(0.38f)
            )
            Text(
                text      = value,
                fontSize  = 14.sp,
                color     = colorOnSurf,
                modifier  = Modifier.weight(0.62f),
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis
            )
        }
        if (!isLast) {
            HorizontalDivider(color = colorDiv, thickness = 0.5.dp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SelectionActionBar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelectionActionBar(
    count: Int,
    onRestoreAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onClear: () -> Unit
) {
    val colorError   = MaterialTheme.colorScheme.error
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surfaceVariant
    val colorOnSurf  = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = colorOnSurf, modifier = Modifier.size(18.dp))
        }
        Text(
            text = "$count item${if (count != 1) "s" else ""} selected",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colorOnSurf,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = onRestoreAll,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Remove", fontSize = 12.sp, color = colorPrimary)
        }
        Button(
            onClick = onDeleteAll,
            colors = ButtonDefaults.buttonColors(containerColor = colorError),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Delete", fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EmptyVaultPlaceholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyVaultPlaceholder(colorMuted: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector    = Icons.Default.Shield,
                contentDescription = null,
                tint           = colorMuted.copy(alpha = 0.4f),
                modifier       = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text       = "Your vault is empty",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Medium,
                color      = colorMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text     = "Add photos here to keep them secure",
                fontSize = 14.sp,
                color    = colorMuted.copy(alpha = 0.6f)
            )
        }
    }
}