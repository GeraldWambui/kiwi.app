package co.juneai.june.data.model

data class FolderData(
    val name: String,
    val path: String,
    val imageCount: Int,
    val lastModified: Long,
    val thumbnailPath: String? = null
)