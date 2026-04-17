package co.juneai.june.ui.screen.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.data.model.ImageData
import co.juneai.june.domain.repository.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: GalleryRepository
) : ViewModel() {

    private val _trashImages = MutableStateFlow<List<ImageData>>(emptyList())
    val trashImages: StateFlow<List<ImageData>> = _trashImages.asStateFlow()

    fun loadTrash() {
        viewModelScope.launch {
            try {
                _trashImages.value = repository.getTrash()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load trash")
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            try {
                val ids = _trashImages.value.map { it.id }
                repository.permanentlyDelete(ids)
                _trashImages.value = emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to empty trash")
            }
        }
    }

    fun restoreItems(imageIds: List<Long>) {
        viewModelScope.launch {
            try {
                repository.restoreFromTrash(imageIds)
                loadTrash()
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore items")
            }
        }
    }
}
