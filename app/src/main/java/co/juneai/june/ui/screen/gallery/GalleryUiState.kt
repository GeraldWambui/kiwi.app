package co.juneai.june.ui.screen.gallery

import co.juneai.june.data.model.AlbumItem2
import co.juneai.june.data.model.MediaItem2
import co.juneai.june.ui.screen.search.SearchMode

enum class ViewType2 { GRID, LIST }

data class GalleryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val allMedia: List<MediaItem2> = emptyList(),
    val allAlbums: List<AlbumItem2> = emptyList(),
    val filteredMedia: List<MediaItem2> = emptyList(),
    val selectedAlbum: AlbumItem2? = null,
    val error: String? = null,
    val selectedItems: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val currentTab: GalleryTab = GalleryTab.PHOTOS,
    val currentView: ViewType2 = ViewType2.GRID,
    val searchMode: SearchMode = SearchMode.None,
    val searchResults: List<MediaItem2> = emptyList(),
    // NSFW scan state
    val isNsfwScanning: Boolean = false,
    val nsfwScanProgress: Float = 0f,
    val nsfwScanTotal: Int = 0,
    val nsfwScanCurrent: Int = 0,
    val nsfwFlaggedCount: Int = 0,
    val nsfwProtectionEnabled: Boolean = true
)