package co.juneai.june.ui.screen.people

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.data.local.daos.FaceClusterDao
import co.juneai.june.data.local.daos.FaceClusterSummary
import co.juneai.june.data.model.MediaItem
import co.juneai.june.data.repository.MediaStoreRepository
import android.content.ContentUris
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import co.juneai.june.ml.face.FaceDetectorAndClusterer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class PeoplePersonItem(
    val clusterId: String,
    val personName: String,
    val photoCount: Int,
    val coverMediaStoreId: Long,
    val avatarFile: File? = null
)

data class PeopleUiState(
    val people: List<PeoplePersonItem> = emptyList(),
    val isLoading: Boolean = false,
    val selectedPerson: PeoplePersonItem? = null,
    val selectedPersonPhotos: List<MediaItem> = emptyList()
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val faceClusterDao: FaceClusterDao,
    private val mediaStoreRepository: MediaStoreRepository,
    private val faceDetectorAndClusterer: FaceDetectorAndClusterer
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleUiState())
    val uiState: StateFlow<PeopleUiState> = _uiState.asStateFlow()

    init {
        loadPeople()
    }

    fun loadPeople() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val summaries = faceClusterDao.getAllClusterSummaries()
                val avatarDir = File(context.filesDir, "avatars")

                val peopleItems = summaries.map { summary ->
                    val avatarFile = File(avatarDir, "avatar_${summary.clusterId}.jpg").takeIf { it.exists() }
                    val displayName = summary.personName.ifBlank { "Person ${summary.clusterId.takeLast(4)}" }
                    PeoplePersonItem(
                        clusterId = summary.clusterId,
                        personName = displayName,
                        photoCount = summary.photoCount,
                        coverMediaStoreId = summary.coverMediaStoreId,
                        avatarFile = avatarFile
                    )
                }

                _uiState.update {
                    it.copy(
                        people = peopleItems,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load people")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun renamePerson(clusterId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                faceClusterDao.updatePersonName(clusterId, newName.trim())
                loadPeople()
            } catch (e: Exception) {
                Timber.e(e, "Failed to rename person")
            }
        }
    }

    fun mergePeople(sourceClusterId: String, targetClusterId: String) {
        if (sourceClusterId == targetClusterId) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                faceClusterDao.mergeClusters(sourceClusterId, targetClusterId)
                loadPeople()
            } catch (e: Exception) {
                Timber.e(e, "Failed to merge clusters")
            }
        }
    }

    fun deletePerson(clusterId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                faceClusterDao.deleteCluster(clusterId)
                loadPeople()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete cluster")
            }
        }
    }

    fun selectPerson(person: PeoplePersonItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, selectedPerson = person) }
            try {
                val allMedia = mediaStoreRepository.loadAllMedia().associateBy { it.id }
                val mediaIds = faceClusterDao.getMediaIdsForCluster(person.clusterId)
                val photos = mediaIds.mapNotNull { allMedia[it] }

                _uiState.update {
                    it.copy(
                        selectedPersonPhotos = photos,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load photos for person")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearSelectedPerson() {
        _uiState.update { it.copy(selectedPerson = null, selectedPersonPhotos = emptyList()) }
    }

    fun recluster() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val allMedia = mediaStoreRepository.loadAllMedia().filter { !it.isVideo }
                val mediaIdsAlreadyClustered = faceClusterDao.getAllFaces().map { it.mediaStoreId }.toHashSet()
                val pendingMedia = allMedia.filter { it.id !in mediaIdsAlreadyClustered }

                for (media in pendingMedia) {
                    try {
                        val uri = if (media.uri.isNotEmpty()) Uri.parse(media.uri) else {
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, media.id)
                        }
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            if (bitmap != null) {
                                val results = faceDetectorAndClusterer.processFaces(media.id, bitmap)
                                for (res in results) {
                                    faceClusterDao.insert(res.entity)
                                }
                                bitmap.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Error clustering media ${media.id}")
                    }
                }
                loadPeople()
            } catch (e: Exception) {
                Timber.e(e, "Reclustering failed")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
