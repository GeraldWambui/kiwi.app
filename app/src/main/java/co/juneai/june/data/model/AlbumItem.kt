package co.juneai.june.data.model

data class AlbumItem(
    val id: Long,
    val name: String,
    val count: Int,
    val coverUri: String?,
    val isVideoFolder: Boolean,
    val hasMedia: Boolean,
    val type: AlbumType
)