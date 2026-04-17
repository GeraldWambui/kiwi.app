package co.juneai.june.ui.screen.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material3.MaterialTheme
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun SecureVaultScreen(
    viewModel: VaultViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onItemClick: (Long) -> Unit = {}
) {
    val items by viewModel.vaultItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    // Theme-sourced colors
    val VaultBg = MaterialTheme.colorScheme.background
    val VaultHeaderText = MaterialTheme.colorScheme.onSurface
    val VaultSubtitle = MaterialTheme.colorScheme.onSurfaceVariant
    val ShieldBg = MaterialTheme.colorScheme.primary
    val SelectionBorder = MaterialTheme.colorScheme.tertiary

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBg)
            .statusBarsPadding()
    ) {
        // ── Top Bar ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = VaultHeaderText,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Secure Vault",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VaultHeaderText
            )

            Spacer(modifier = Modifier.weight(1f))

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = VaultHeaderText,
                        modifier = Modifier.size(24.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Select items") },
                        onClick = {
                            viewModel.toggleSelectionMode()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        onClick = {
                            viewModel.selectAll()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove selected") },
                        onClick = {
                            viewModel.removeSelectedFromVault()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        onClick = {
                            viewModel.refresh()
                            showMenu = false
                        }
                    )
                }
            }
        }

        // ── Encryption Notice ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Encrypted",
                tint = VaultSubtitle,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Items in your vault are end-to-end encrypted",
                fontSize = 13.sp,
                color = VaultSubtitle
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Items Grid ─────────────────────────────────────
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ShieldBg)
            }
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Empty vault",
                        tint = VaultSubtitle.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your vault is empty",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = VaultSubtitle
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add photos here to keep them secure",
                        fontSize = 14.sp,
                        color = VaultSubtitle.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                items(items, key = { it.id }) { item ->
                    VaultGridItem(
                        item = item,
                        isSelected = item.id in selectedItems,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleItemSelection(item.id)
                            } else {
                                onItemClick(item.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultGridItem(
    item: VaultItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit
) {
    val SelectionBorder = MaterialTheme.colorScheme.tertiary
    val ShieldBg = MaterialTheme.colorScheme.primary
    // Random aspect ratios for staggered look
    val aspectRatio = remember(item.id) {
        when ((item.id % 4).toInt()) {
            0 -> 0.75f  // Taller
            1 -> 1.2f   // Wider
            2 -> 1.0f   // Square
            else -> 0.85f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.border(3.dp, SelectionBorder, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.fileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Shield verification badge
        if (item.isVerified) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ShieldBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Verified",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Selection check mark
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) SelectionBorder else Color.White.copy(alpha = 0.6f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
