package co.juneai.june.data.model

data class MediaItem(
    val id: Long,
    val uri: String, // This should be String, not Uri
    val mimeType: String,
    val dateTaken: Long,
    val duration: Long? = null,
    val albumId: Long? = null,
    val albumName: String = "",
    val relativePath: String = "",
    val name: String = "",
    val size: Long = 0,
    val dateAdded: Long = 0,
    val isVideo: Boolean = false,
    val isNsfw: Boolean = false
)

