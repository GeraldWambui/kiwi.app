package co.juneai.june.ui.screen.trash

import android.text.format.DateUtils
import androidx.appcompat.R
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.juneai.june.data.model.ImageData
import coil.compose.AsyncImage
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TrashBinScreen(
    onBack: () -> Unit = {},
    viewModel: TrashViewModel = hiltViewModel()
) {
    // Theme-sourced colors
    val TrashBg = MaterialTheme.colorScheme.background
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val DividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    var showEmptyConfirm by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(Unit) {
        viewModel.loadTrash()
    }

    val trashImages by viewModel.trashImages.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrashBg)
    ) {
        // ── Top Bar ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = PrimaryText,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = if (selectedItemIds.isNotEmpty()) "${selectedItemIds.size} Selected" else "Trash",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )

            if (trashImages.isNotEmpty()) {
                TextButton(onClick = { showEmptyConfirm = true }) {
                    Text(
                        text = "Empty",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Box(modifier = Modifier.size(48.dp))
            }
        }

        // ── Subtitle ──────────────────────────────────────
        if (trashImages.isNotEmpty()) {
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            Text(
                text = "Items show remaining days before 30-day permanent auto-delete. Tap to select items to restore or delete.",
                fontSize = 13.sp,
                color = SecondaryText,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        }

        // ── Content ───────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (trashImages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(co.juneai.june.R.drawable.ic_ios_trash),
                            contentDescription = "Empty trash",
                            tint = AccentGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Trash is empty",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Deleted items will appear here",
                            fontSize = 14.sp,
                            color = SecondaryText
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = trashImages,
                        key = { _, item -> item.id }
                    ) { _, image ->
                        val isSelected = image.id in selectedItemIds
                        TrashGridThumbnail(
                            image = image,
                            isSelected = isSelected,
                            onToggleSelect = {
                                selectedItemIds = if (isSelected) {
                                    selectedItemIds - image.id
                                } else {
                                    selectedItemIds + image.id
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Bottom Action Bar when items are selected ──
        if (selectedItemIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        viewModel.restoreItems(selectedItemIds.toList())
                        selectedItemIds = emptySet()
                    }
                ) {
                    Text(
                        "Restore (${selectedItemIds.size})",
                        color = AccentGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        viewModel.emptyTrash() // Or permanent delete of selected items
                        selectedItemIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            }
        }
    }

    // ── Empty Trash Confirmation ──────────────────────────
    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = {
                Text(
                    "Empty Trash?",
                    color = PrimaryText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "All ${trashImages.size} items will be permanently deleted immediately. This cannot be undone.",
                    color = SecondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.emptyTrash()
                        selectedItemIds = emptySet()
                        showEmptyConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("Cancel", color = AccentGreen)
                }
            }
        )
    }
}

@Composable
private fun TrashGridThumbnail(
    image: ImageData,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    val daysLabel by remember(image.trashedAt) {
        derivedStateOf {
            val trashedTime = image.trashedAt ?: System.currentTimeMillis()
            val deleteTime = trashedTime + 30L * 24 * 60 * 60 * 1000L // 30 days retention
            val remainingMs = deleteTime - System.currentTimeMillis()

            when {
                remainingMs <= 0 -> "Expired"
                remainingMs < 24 * 60 * 60 * 1000L -> "< 1 day"
                else -> "${remainingMs / (24 * 60 * 60 * 1000L)} days left"
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onToggleSelect() }
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.fileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        // Bottom gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        // Days remaining label
        Text(
            text = daysLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
        )
    }
}