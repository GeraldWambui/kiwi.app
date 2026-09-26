package co.juneai.june.ui.screen.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.ml.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelDownloadUiState(
    val isDownloading: Boolean = true,
    val progress: Float = 0f,
    val statusMessage: String = "Connecting to Cloudflare CDN...",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = ModelManager.MOBILECLIP_S2_SIZE_BYTES,
    val speedFormatted: String = "",
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ModelAssetDownloadViewModel @Inject constructor(
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelDownloadUiState())
    val uiState: StateFlow<ModelDownloadUiState> = _uiState.asStateFlow()

    init {
        checkAndStartDownload()
    }

    fun checkAndStartDownload() {
        if (modelManager.isPrimaryModelDownloaded()) {
            _uiState.value = _uiState.value.copy(
                isDownloading = false,
                progress = 1f,
                downloadedBytes = _uiState.value.totalBytes,
                isSuccess = true,
                statusMessage = "Assets Verified"
            )
            return
        }
        startDownload()
    }

    fun startDownload() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                errorMessage = null,
                statusMessage = "Connecting to Cloudflare CDN...",
                progress = 0f
            )

            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            val success = modelManager.downloadModel(
                modelId = ModelManager.MODEL_MOBILECLIP_S2,
                onProgressUpdate = { progress, downloaded, total, partIdx ->
                    val currentTime = System.currentTimeMillis()
                    val timeDelta = (currentTime - lastTime) / 1000.0
                    val speed = if (timeDelta >= 0.5 && downloaded > lastBytes) {
                        val bytesPerSec = ((downloaded - lastBytes) / timeDelta).toLong()
                        lastTime = currentTime
                        lastBytes = downloaded
                        formatSpeed(bytesPerSec)
                    } else _uiState.value.speedFormatted

                    val message = if (partIdx >= 0) {
                        "Downloading Assets (Part ${partIdx + 1} of 3)..."
                    } else {
                        "Downloading Assets..."
                    }

                    _uiState.value = _uiState.value.copy(
                        isDownloading = true,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedFormatted = speed,
                        statusMessage = message
                    )
                }
            )

            if (success) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    progress = 1f,
                    downloadedBytes = _uiState.value.totalBytes,
                    isSuccess = true,
                    statusMessage = "Assets Complete",
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    errorMessage = "Download was interrupted. Please check your connection."
                )
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return if (bytesPerSec > 1024 * 1024) {
            String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        } else if (bytesPerSec > 1024) {
            String.format("%.0f KB/s", bytesPerSec / 1024.0)
        } else {
            ""
        }
    }
}
