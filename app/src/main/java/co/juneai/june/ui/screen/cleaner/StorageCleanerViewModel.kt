package co.juneai.june.ui.screen.cleaner

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.data.repository.CleanerAnalysisResult
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.data.repository.StorageCleanerRepository
import co.juneai.june.domain.repository.GalleryRepository
import co.juneai.june.utils.MediaStoreTrashHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class CleanerTab {
    OVERVIEW,
    EXACT_DUPLICATES,
    SIMILAR_PHOTOS,
    LARGE_VIDEOS,
    BLURRY,
    SCREENSHOTS
}

data class CleanerUiState(
    val isScanning: Boolean = false,
    val analysisResult: CleanerAnalysisResult? = null,
    val selectedTab: CleanerTab = CleanerTab.OVERVIEW,
    val selectedItemsToTrash: Set<Long> = emptySet(),
    val isTrashing: Boolean = false,
    val lastTrashedCount: Int = 0,
    val showConfirmDialog: Boolean = false
)

@HiltViewModel
class StorageCleanerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageCleanerRepository: StorageCleanerRepository,
    private val galleryRepository: GalleryRepository,
    private val mediaStoreRepository: MediaStoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    private val _pendingTrashIntentSender = MutableSharedFlow<IntentSender>()
    val pendingTrashIntentSender: SharedFlow<IntentSender> = _pendingTrashIntentSender.asSharedFlow()

    init {
        startScan()
    }

    fun startScan() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanning = true, selectedItemsToTrash = emptySet()) }
            try {
                val result = storageCleanerRepository.analyzeStorage()
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        analysisResult = result
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Storage cleaner scan failed")
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun setTab(tab: CleanerTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleSelectItem(mediaId: Long) {
        _uiState.update { state ->
            val updated = state.selectedItemsToTrash.toMutableSet()
            if (updated.contains(mediaId)) {
                updated.remove(mediaId)
            } else {
                updated.add(mediaId)
            }
            state.copy(selectedItemsToTrash = updated)
        }
    }

    fun selectAllRedundantDuplicates() {
        val result = _uiState.value.analysisResult ?: return
        val redundantIds = mutableSetOf<Long>()

        for (group in result.exactDuplicates) {
            for (item in group.items) {
                if (item.id != group.bestItemId) {
                    redundantIds.add(item.id)
                }
            }
        }

        for (group in result.similarPhotoGroups) {
            for (item in group.items) {
                if (item.id != group.bestItemId) {
                    redundantIds.add(item.id)
                }
            }
        }

        _uiState.update { it.copy(selectedItemsToTrash = redundantIds) }
    }

    fun selectAllInCurrentTab() {
        val result = _uiState.value.analysisResult ?: return
        val idsToSelect = when (_uiState.value.selectedTab) {
            CleanerTab.EXACT_DUPLICATES -> result.exactDuplicates.flatMap { g -> g.items.filter { it.id != g.bestItemId }.map { it.id } }
            CleanerTab.SIMILAR_PHOTOS -> result.similarPhotoGroups.flatMap { g -> g.items.filter { it.id != g.bestItemId }.map { it.id } }
            CleanerTab.LARGE_VIDEOS -> result.largeVideos.map { it.id }
            CleanerTab.BLURRY -> result.blurryPhotos.map { it.id }
            CleanerTab.SCREENSHOTS -> result.oldScreenshots.map { it.id }
            CleanerTab.OVERVIEW -> emptyList()
        }
        _uiState.update { it.copy(selectedItemsToTrash = idsToSelect.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItemsToTrash = emptySet(), showConfirmDialog = false) }
    }

    fun showConfirmTrashDialog() {
        if (_uiState.value.selectedItemsToTrash.isNotEmpty()) {
            _uiState.update { it.copy(showConfirmDialog = true) }
        }
    }

    fun dismissConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun confirmTrashSelected() {
        val toTrash = _uiState.value.selectedItemsToTrash.toList()
        if (toTrash.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTrashing = true, showConfirmDialog = false) }
            try {
                val allMedia = mediaStoreRepository.loadAllMedia().associateBy { it.id }
                val uris = toTrash.mapNotNull { allMedia[it]?.uri?.let { u -> Uri.parse(u) } }

                val pendingIntent = MediaStoreTrashHelper.createTrashRequest(context, uris, isTrash = true)
                if (pendingIntent != null) {
                    _pendingTrashIntentSender.emit(pendingIntent.intentSender)
                } else {
                    galleryRepository.moveToTrash(toTrash)
                    _uiState.update { it.copy(isTrashing = false, lastTrashedCount = toTrash.size, selectedItemsToTrash = emptySet()) }
                    startScan()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to trash items in storage cleaner")
                _uiState.update { it.copy(isTrashing = false) }
            }
        }
    }

    fun onOsTrashApproved() {
        val toTrash = _uiState.value.selectedItemsToTrash.toList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                galleryRepository.moveToTrash(toTrash)
                _uiState.update { it.copy(isTrashing = false, lastTrashedCount = toTrash.size, selectedItemsToTrash = emptySet()) }
                startScan()
            } catch (e: Exception) {
                Timber.e(e, "Failed to complete post-OS trash sync")
                _uiState.update { it.copy(isTrashing = false) }
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> "${String.format("%.2f", bytes / 1_000_000_000.0)} GB"
            bytes >= 1_000_000L -> "${String.format("%.1f", bytes / 1_000_000.0)} MB"
            bytes >= 1_000L -> "${bytes / 1_000L} KB"
            else -> "$bytes B"
        }
    }
}
