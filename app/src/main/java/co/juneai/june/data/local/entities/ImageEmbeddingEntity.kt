package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "image_embeddings",
    indices = [
        Index("folderName"),
        Index("indexed"),
        Index("isTrashed"),
        Index("dateAdded"),
        Index("primaryObject")
    ]
)
data class ImageEmbeddingEntity(
    @PrimaryKey val mediaStoreId: Long,
    val uri: String,
    val filePath: String,
    val fileName: String,
    val folderName: String,
    val mimeType: String,

    val dateAdded: Long,
    val dateModified: Long,
    val dateTaken: Long? = null,

    val width: Int,
    val height: Int,
    val fileSize: Long,

    val latitude: Double? = null,
    val longitude: Double? = null,

    val cameraModel: String? = null,
    val isoValue: Int? = null,
    val exposureTime: Float? = null,
    val focalLength: Float? = null,
    val aperture: Float? = null,
    val flashFired: Boolean = false,

    val embedding512: ByteArray,
    val embedding128: ByteArray,
    val semanticTags: String,
    val primaryObject: String? = null,
    val objectConfidence: Float = 0f,
    val faceCount: Int = 0,
    val ocrText: String? = null,

    val indexed: Boolean = true,
    val indexedAt: Long = System.currentTimeMillis(),
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null
)