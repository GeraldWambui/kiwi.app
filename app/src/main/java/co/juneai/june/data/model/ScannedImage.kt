package co.juneai.june.data.model

import android.net.Uri

data class ScannedImage(
        val id: Long,
        val uri: Uri,
        val displayName: String,
        val dateTaken: Long,
        val dateModified: Long,
        val size: Long,
        val width: Int,
        val height: Int,
        val mimeType: String,
        val folderName: String
    )