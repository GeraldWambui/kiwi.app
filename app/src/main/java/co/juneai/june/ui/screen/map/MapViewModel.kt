package co.juneai.june.ui.screen.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.data.model.MediaItem
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.utils.GeoLocation
import co.juneai.june.utils.GpsExtractor
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
import javax.inject.Inject
import kotlin.math.roundToInt

data class MapPhotoCluster(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val photoCount: Int,
    val coverItem: MediaItem,
    val photos: List<MediaItem>
)

data class MapUiState(
    val clusters: List<MapPhotoCluster> = emptyList(),
    val totalGeotaggedPhotos: Int = 0,
    val isLoading: Boolean = false,
    val selectedCluster: MapPhotoCluster? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreRepository: MediaStoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadMapClusters()
    }

    fun loadMapClusters() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val allMedia = mediaStoreRepository.loadAllMedia()
                val geotaggedItems = mutableListOf<Pair<MediaItem, GeoLocation>>()

                for (item in allMedia) {
                    if (item.isVideo) continue
                    val location = GpsExtractor.extractLocation(context, item.uri)
                    if (location != null) {
                        geotaggedItems.add(item to location)
                    }
                }

                // Grid-based spatial clustering (~0.05 degrees ~ 5.5km)
                val gridResolution = 0.05
                val gridGroups = geotaggedItems.groupBy { (_, loc) ->
                    val latCell = (loc.latitude / gridResolution).roundToInt()
                    val lngCell = (loc.longitude / gridResolution).roundToInt()
                    "${latCell}_$lngCell"
                }

                val clusters = gridGroups.map { (key, itemsWithLoc) ->
                    val avgLat = itemsWithLoc.map { it.second.latitude }.average()
                    val avgLng = itemsWithLoc.map { it.second.longitude }.average()
                    val sortedPhotos = itemsWithLoc.map { it.first }.sortedByDescending { it.dateTaken }

                    MapPhotoCluster(
                        id = key,
                        latitude = avgLat,
                        longitude = avgLng,
                        photoCount = sortedPhotos.size,
                        coverItem = sortedPhotos.first(),
                        photos = sortedPhotos
                    )
                }.sortedByDescending { it.photoCount }

                _uiState.update {
                    it.copy(
                        clusters = clusters,
                        totalGeotaggedPhotos = geotaggedItems.size,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load map clusters")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectCluster(cluster: MapPhotoCluster?) {
        _uiState.update { it.copy(selectedCluster = cluster) }
    }
}
