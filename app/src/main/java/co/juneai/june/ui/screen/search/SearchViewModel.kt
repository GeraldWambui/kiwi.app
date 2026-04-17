package co.juneai.june.ui.screen.search

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import co.juneai.june.domain.search.ImageIndexingService
import co.juneai.june.domain.search.ImageSearchEngine
import co.juneai.june.worker.MediaIndexingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val searchEngine: ImageSearchEngine,
    private val indexingService: ImageIndexingService
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<ImageSearchEngine.SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()

    val indexingProgress = indexingService.indexingProgress

    fun search(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {
                Timber.d("Searching for: $query")
                val results = searchEngine.searchByText(
                    query = query,
                    maxResults = 30,
                    minScore = 0.0f
                )
                _searchResults.value = results
                Timber.d("Search returned ${results.size} results")

                if (results.isEmpty()) {
                    Timber.d("No results — images may not be indexed yet")
                }
            } catch (e: Exception) {
                Timber.e(e, "Search failed for query: $query")
                _searchResults.value = emptyList()
                _searchError.value = "Images may still be indexing. Try again shortly."
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearResults() {
        _searchResults.value = emptyList()
        _searchError.value = null
    }

    fun indexNewImages(imageUris: List<Uri>) {
        val uriStrings = arrayOfNulls<String>(imageUris.size)
        imageUris.forEachIndexed { index, uri ->
            uriStrings[index] = uri.toString()
        }

        val inputData = Data.Builder()
            .putStringArray(MediaIndexingWorker.KEY_IMAGE_URIS, uriStrings)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MediaIndexingWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "ManualMediaIndexing",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
