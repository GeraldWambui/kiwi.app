package co.juneai.june.ui.screen.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.data.repository.ForYouData
import co.juneai.june.data.repository.MemoriesRepository
import co.juneai.june.data.repository.MemoryStory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ForYouUiState(
    val isLoading: Boolean = true,
    val data: ForYouData? = null,
    val activeStory: MemoryStory? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val memoriesRepository: MemoriesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForYouUiState())
    val uiState: StateFlow<ForYouUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val data = memoriesRepository.loadForYouData()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    data = data,
                    errorMessage = null
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load For You memories")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load memories. Please try again."
                )
            }
        }
    }

    fun openStory(story: MemoryStory) {
        _uiState.value = _uiState.value.copy(activeStory = story)
    }

    fun closeStory() {
        _uiState.value = _uiState.value.copy(activeStory = null)
    }
}
