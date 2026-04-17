package co.juneai.june.data.model

import android.net.Uri
import java.util.Date

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Date,
    val dateTaken: Date?,
    val size: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val duration: Long? = null, // For videos in milliseconds
    val type: MediaType,
    val path: String,
    val isFavorite: Boolean = false,
    val albumName: String? = null
) {
    val isVideo: Boolean
        get() = type == MediaType.VIDEO
    
    val dimensionString: String
        get() = "${width}x${height}px"
    
    val sizeString: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.2fMB", mb)
            } else {
                String.format("%.2fKB", kb)
            }
        }
    
    val durationString: String?
        get() = duration?.let {
            val totalSeconds = it / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format("%d:%02d", minutes, seconds)
        }
}