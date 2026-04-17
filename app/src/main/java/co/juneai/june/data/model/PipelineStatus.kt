package co.juneai.june.data.model
/**
 * Pipeline status information
 */
data class PipelineStatus(
    val isInitialized: Boolean,
    val isClosed: Boolean,
    val cacheSize: Int,
    val modelLoaded: Boolean
)