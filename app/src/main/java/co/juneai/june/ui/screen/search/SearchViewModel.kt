package co.juneai.june.ui.screen.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()


    fun search(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {

            } catch (e: Exception) {
                Timber.e(e, "Search failed for query: $query")
                _searchError.value = "Images may still be indexing. Try again shortly."
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearResults() {
        _searchError.value = null
    }
}
