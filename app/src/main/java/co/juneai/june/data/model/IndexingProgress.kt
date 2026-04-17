package co.juneai.june.data.model

data class IndexingProgress(
    val currentCount: Int,
    val totalCount: Int,
    val isComplete: Boolean = currentCount >= totalCount
)