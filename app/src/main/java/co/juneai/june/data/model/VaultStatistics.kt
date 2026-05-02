package co.juneai.june.data.model

data class VaultStatistics(
    val totalItems: Int,
    val imagesCount: Int,
    val videosCount: Int,
    val totalSize: Long,
    val remainingSpace: Long
) {
    fun getFormattedSize(): String = formatSize(totalSize)
    fun getFormattedRemainingSpace(): String = formatSize(remainingSpace)
    
    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}