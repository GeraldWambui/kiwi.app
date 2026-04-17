package co.juneai.june.ui.screen.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import co.juneai.june.data.model.AlbumItem2
import co.juneai.june.data.model.MediaItem2
import co.juneai.june.data.model.SearchResult
import co.juneai.june.data.repository.nsfw.NsfwFlagStore
import co.juneai.june.data.repository.MediaContentObserver
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.data.search.SearchOrchestrator
import co.juneai.june.data.search.SearchOutcome
import co.juneai.june.ml.MobileCLIPPipeline
import co.juneai.june.ui.screen.search.SearchMode
import co.juneai.june.worker.NsfwScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    application: Application,
    private val repository: MediaStoreRepository,
    private val searchOrchestrator: SearchOrchestrator,
    private val mobileCLIPPipeline: MobileCLIPPipeline,
    private val nsfwFlagStore: NsfwFlagStore
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
        loadModels()
        observeMediaChanges()
        loadNsfwProtectionState()
        observeNsfwScanProgress()
    }

    fun refresh() {
        loadAllData(isRefreshing = true)
    }

    fun loadModels() {
        viewModelScope.launch {
            mobileCLIPPipeline.ensureInitialized()
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeMediaChanges() {
        MediaContentObserver.observeMediaChanges(getApplication())
            .debounce(1000)
            .onEach {
                loadAllData(isRefreshing = false)
                if (nsfwFlagStore.isProtectionEnabled) {
                    scheduleNsfwScan()
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadAllData(isRefreshing: Boolean = false) {
        viewModelScope.launch {
            if (isRefreshing) {
                _uiState.update { it.copy(isRefreshing = true) }
            } else if (_uiState.value.allMedia.isEmpty()) {
                _uiState.update { it.copy(isLoading = true) }
            }

            try {
                val media = repository.loadAllMedia()

                val flaggedMedia = if (nsfwFlagStore.isProtectionEnabled) {
                    media.map { item ->
                        if (nsfwFlagStore.isNsfw(item.id)) item.copy(isNsfw = true) else item
                    }
                } else {
                    media
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        allMedia = flaggedMedia,
                        filteredMedia = if (it.selectedAlbum == null) flaggedMedia else filterMediaForAlbum(flaggedMedia, it.selectedAlbum),
                        error = null,
                        nsfwProtectionEnabled = nsfwFlagStore.isProtectionEnabled,
                        nsfwFlaggedCount = nsfwFlagStore.getFlaggedCount()
                    )
                }

                val systemAlbums = repository.buildSystemAlbums(media)
                val customAlbums = repository.loadAlbums(media)

                val systemAlbumNames = systemAlbums.map { it.name.lowercase() }.toSet()
                val uniqueCustomAlbums = customAlbums.filter {
                    it.name.lowercase() !in systemAlbumNames
                }
                val allAlbums = systemAlbums + uniqueCustomAlbums

                _uiState.update {
                    it.copy(allAlbums = allAlbums)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Failed to load data"
                    )
                }
            }
        }
    }
    private fun loadNsfwProtectionState() {
        _uiState.update {
            it.copy(
                nsfwProtectionEnabled = nsfwFlagStore.isProtectionEnabled,
                nsfwFlaggedCount = nsfwFlagStore.getFlaggedCount()
            )
        }
    }

    private fun observeNsfwScanProgress() {
        val workManager = WorkManager.getInstance(getApplication())
        workManager.getWorkInfosForUniqueWorkLiveData(NsfwScanWorker.WORK_NAME)
            .observeForever { workInfos ->
                val workInfo = workInfos?.firstOrNull() ?: return@observeForever
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val current = workInfo.progress.getInt(NsfwScanWorker.PROGRESS_CURRENT, 0)
                        val total = workInfo.progress.getInt(NsfwScanWorker.PROGRESS_TOTAL, 0)
                        val flagged = workInfo.progress.getInt(NsfwScanWorker.PROGRESS_FLAGGED, 0)
                        _uiState.update {
                            it.copy(
                                isNsfwScanning = true,
                                nsfwScanCurrent = current,
                                nsfwScanTotal = total,
                                nsfwScanProgress = if (total > 0) current.toFloat() / total else 0f,
                                nsfwFlaggedCount = flagged
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _uiState.update {
                            it.copy(
                                isNsfwScanning = false,
                                nsfwScanProgress = 1f,
                                nsfwFlaggedCount = nsfwFlagStore.getFlaggedCount()
                            )
                        }
                        loadAllData(isRefreshing = false)
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        _uiState.update { it.copy(isNsfwScanning = false) }
                    }
                    else -> { /* ENQUEUED, BLOCKED */ }
                }
            }
    }

    fun scheduleNsfwScan() {
        val workManager = WorkManager.getInstance(getApplication())
        val scanRequest = OneTimeWorkRequestBuilder<NsfwScanWorker>().build()
        workManager.enqueueUniqueWork(
            NsfwScanWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            scanRequest
        )
        Timber.d("NSFW scan scheduled")
    }

    /** Schedule NSFW scan for specific new media IDs */
    fun scheduleNsfwScanForMedia(mediaIds: List<Long>) {
        if (!nsfwFlagStore.isProtectionEnabled || mediaIds.isEmpty()) return
        val workManager = WorkManager.getInstance(getApplication())
        val scanRequest = OneTimeWorkRequestBuilder<NsfwScanWorker>()
            .setInputData(
                workDataOf(NsfwScanWorker.KEY_MEDIA_IDS to mediaIds.toLongArray())
            )
            .build()
        workManager.enqueueUniqueWork(
            NsfwScanWorker.WORK_NAME_NEW_MEDIA,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            scanRequest
        )
    }

    fun toggleNsfwProtection() {
        val newState = !nsfwFlagStore.isProtectionEnabled
        nsfwFlagStore.isProtectionEnabled = newState
        _uiState.update { it.copy(nsfwProtectionEnabled = newState) }
        loadAllData(isRefreshing = false)
        if (newState) {
            scheduleNsfwScan()
        }
    }

    fun selectAlbum(album: AlbumItem2?) {
        _uiState.update { currentState ->
            val filteredMedia = if (album == null) {
                currentState.allMedia
            } else {
                filterMediaForAlbum(currentState.allMedia, album)
            }

            currentState.copy(
                selectedAlbum = album,
                filteredMedia = filteredMedia,
                selectedItems = emptySet(),
                isSelectionMode = false
            )
        }
    }

    fun toggleSelection(itemId: Long) {
        _uiState.update { currentState ->
            val newSelection = if (itemId in currentState.selectedItems) {
                currentState.selectedItems - itemId
            } else {
                currentState.selectedItems + itemId
            }

            currentState.copy(
                selectedItems = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedItems = emptySet(),
                isSelectionMode = false
            )
        }
    }

    fun selectAllItems() {
        _uiState.update { currentState ->
            currentState.copy(
                selectedItems = currentState.filteredMedia.map { it.id }.toSet(),
                isSelectionMode = true
            )
        }
    }

    fun setCurrentTab(tab: GalleryTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setCurrentView(viewType: ViewType2) {
        _uiState.update { it.copy(currentView = viewType) }
    }

    suspend fun onSearchTyping(query: String) {
        if (query.isBlank()) {
            _uiState.update {
                it.copy(searchMode = SearchMode.None, searchResults = emptyList())
            }
            return
        }

        val results = repository.textSearch(query)

        _uiState.update {
            it.copy(
                searchMode = SearchMode.TextRefinement(query),
                searchResults = results
            )
        }
    }

    fun onSearchSubmit(query: String) {
        viewModelScope.launch {
            val outcome = searchOrchestrator.handleQuery(query)

            when (outcome) {
                is SearchOutcome.Results -> {
                    val mediaItems = outcome.items.map { searchResult ->
                        MediaItem2(
                            id = searchResult.image.id,
                            uri = searchResult.image.uri.toString(),
                            mimeType = searchResult.image.mimeType,
                            dateTaken = searchResult.image.dateTaken!!,
                            duration = 1000L,
                            albumId = searchResult.image.id,
                            albumName = searchResult.image.folderName,
                            relativePath = searchResult.image.filePath
                        )
                    }

                    _uiState.update {
                        it.copy(
                            searchMode = SearchMode.Semantic(query),
                            searchResults = mediaItems
                        )
                    }
                }

                is SearchOutcome.ConfirmationRequired -> {
                    val mediaItems = outcome.items.map { searchResult ->
                        MediaItem2(
                            id = searchResult.image.id,
                            uri = searchResult.image.uri.toString(),
                            mimeType = searchResult.image.mimeType,
                            dateTaken = searchResult.image.dateTaken!!,
                            duration = 1000L,
                            albumId = searchResult.image.id,
                            albumName = searchResult.image.folderName,
                            relativePath = searchResult.image.filePath
                        )
                    }

                    val searchItems = mediaItems.map { res ->
                        SearchResult(
                            image = outcome.items.first().image,
                            similarity = 0.5f,
                            explanation = "",
                            matchedTags = emptyList()
                        )
                    }

                    _uiState.update {
                        it.copy(
                            searchMode = SearchMode.ConfirmDelete(searchItems)
                        )
                    }
                }

                else -> Unit
            }
        }
    }

    fun deleteItems(items: List<MediaItem2>) {
        viewModelScope.launch {
            try {
                items.forEach { item ->
                    repository.deleteMedia(item.id)
                }
                _uiState.update { currentState ->
                    val updatedAllMedia = currentState.allMedia.filter { media ->
                        items.none { it.id == media.id }
                    }
                    val updatedFilteredMedia = currentState.filteredMedia.filter { media ->
                        items.none { it.id == media.id }
                    }

                    currentState.copy(
                        allMedia = updatedAllMedia,
                        filteredMedia = updatedFilteredMedia,
                        searchMode = SearchMode.None,
                        searchResults = emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to delete items: ${e.message}")
                }
            }
        }
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(
                searchMode = SearchMode.None,
                searchResults = emptyList()
            )
        }
    }


    private fun filterMediaForAlbum(
        media: List<MediaItem2>,
        album: AlbumItem2
    ): List<MediaItem2> {
        return when (album.name) {
            "All Photos" -> media
            "Camera" -> media.filter {
                it.relativePath.contains("DCIM", true) ||
                        it.albumName.contains("Camera", true)
            }
            "Videos" -> media.filter { it.isVideo }
            "Screenshots" -> media.filter {
                it.relativePath.contains("Screenshot", true) ||
                        it.albumName.contains("Screenshot", true)
            }
            else -> media.filter { it.albumId == album.id }
        }
    }
}