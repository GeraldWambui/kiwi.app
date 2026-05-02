package co.juneai.june.ui.screen.albums

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.data.model.AlbumItem
import co.juneai.june.data.model.MediaItem
import co.juneai.june.data.repository.MediaContentObserver
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.ui.screen.search.SearchMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaStoreRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumsUiState())
    val uiState: StateFlow<AlbumsUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
        loadModels()
        observeMediaChanges()
        loadNsfwProtectionState()
    }

    fun refresh() {
        loadAllData(isRefreshing = true)
    }

    fun loadModels() {
        viewModelScope.launch {
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeMediaChanges() {
        MediaContentObserver.observeMediaChanges(context)
            .debounce(1000)
            .onEach {
                loadAllData(isRefreshing = false)
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

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        allMedia = media,
                        filteredMedia = media,
                        error = null,
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

    }

    fun selectAlbum(album: AlbumItem?) {
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

    fun setCurrentTab(tab: AlbumsTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setCurrentView(viewType: ViewType) {
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

    fun deleteItems(items: List<MediaItem>) {
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
        media: List<MediaItem>,
        album: AlbumItem
    ): List<MediaItem> {
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