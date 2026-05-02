package co.juneai.june.ui.screen.albums

import co.juneai.june.data.model.AlbumItem
import co.juneai.june.data.model.MediaItem
import co.juneai.june.ui.screen.search.SearchMode

data class AlbumsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val allMedia: List<MediaItem> = emptyList(),
    val allAlbums: List<AlbumItem> = emptyList(),
    val filteredMedia: List<MediaItem> = emptyList(),
    val selectedAlbum: AlbumItem? = null,
    val error: String? = null,
    val selectedItems: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val currentTab: AlbumsTab = AlbumsTab.PHOTOS,
    val currentView: ViewType = ViewType.GRID,
    val searchMode: SearchMode = SearchMode.None,
    val searchResults: List<MediaItem> = emptyList(),
    // NSFW scan state
    val isNsfwScanning: Boolean = false,
    val nsfwScanProgress: Float = 0f,
    val nsfwScanTotal: Int = 0,
    val nsfwScanCurrent: Int = 0,
    val nsfwFlaggedCount: Int = 0,
    val nsfwProtectionEnabled: Boolean = true
)