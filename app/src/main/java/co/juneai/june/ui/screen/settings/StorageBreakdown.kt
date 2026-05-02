package co.juneai.june.ui.screen.settings

data class StorageBreakdown(
    val photosBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val vaultBytes: Long = 0L,
    val systemBytes: Long = 0L,
    val totalUsedBytes: Long = 0L,
    val totalCapacityBytes: Long = 0L,
    val cacheBytes: Long = 0L
)