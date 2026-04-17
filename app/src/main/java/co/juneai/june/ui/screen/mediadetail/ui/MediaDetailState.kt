package co.juneai.june.ui.screen.mediadetail.ui

import android.content.Intent

data class MediaDetailState(
    val media: MediaDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showInfoSheet: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showShareSheet: Boolean = false,
    val showMoreOptionsDialog: Boolean = false,
    val shareIntent: Intent? = null,
    val wallaperIntent: Intent? = null,
    val mapIntent: Intent? = null,
    val isRotating: Boolean = false,
    val mediaIds: List<Long> = emptyList(),
    val currentIndex: Int = 0,
    val cachedMediaMap: Map<Long, MediaDetail> = emptyMap()
)