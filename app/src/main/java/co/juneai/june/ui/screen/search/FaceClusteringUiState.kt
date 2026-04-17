package co.juneai.june.ui.screen.search

import co.juneai.june.data.model.FaceClusterData

data class FaceClusteringUiState(
    val isClustering: Boolean = false,
    val clusters: Map<Long, FaceClusterData> = emptyMap(),
    val mainUserId: Long? = null,
    val error: String? = null
)
