package co.juneai.june.domain.model

import android.net.Uri

data class MediaDetail(
    val id: Long,
    val uri: Uri,
    val title: String? = null,
    val dateTime: Long,
    val location: String? = null,
    val size: String? = null,
    val resolution: String? = null,
    val isFavorite: Boolean = false,
    val isVideo: Boolean = false,
    val duration: Long? = null,
    val albumName: String? = null,
    val path: String? = null
)