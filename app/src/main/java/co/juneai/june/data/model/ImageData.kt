package co.juneai.june.data.model

import android.net.Uri

data class ImageData(
    val id: Long,
    val uri: Uri,
    val filePath: String,
    val fileName: String,
    val folderName: String,
    val mimeType: String,

    // Timestamps
    val dateAdded: Long,
    val dateModified: Long,
    val dateTaken: Long? = null,

    // Dimensions & size
    val width: Int,
    val height: Int,
    val fileSize: Long,

    // Location (optional)
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Camera metadata (optional)
    val cameraModel: String? = null,
    val isoValue: Int? = null,
    val exposureTime: Float? = null,
    val focalLength: Float? = null,
    val aperture: Float? = null,
    val flashFired: Boolean = false,

    // AI Features
    val embedding512: ByteArray? = null,
    val embedding128: ByteArray? = null,
    val semanticTags: List<String> = emptyList(),
    val primaryObject: String? = null,
    val objectConfidence: Float = 0f,
    val faceCount: Int = 0,
    val ocrText: String? = null,

    // Status
    val indexed: Boolean = false,
    val indexedAt: Long = 0,
    val isSelected: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null
)