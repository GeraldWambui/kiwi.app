package co.juneai.june.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_embeddings")
data class MediaEmbeddingEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val fullEmbedding: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val pcaEmbedding: ByteArray,
    val textContent: String? = null,
    val nsfwScore: Float = 0f,
    val nsfwCategory: String = "SAFE",
    val dominantColor: Int? = null,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val colorPalette: ByteArray? = null,
    val indexedAt: Long = System.currentTimeMillis(),
    val fileSize: Long,
    val takenDate: Long?,
    val width: Int,
    val height: Int,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationName: String? = null,
    val blurriness: Float = 0f // 0.0 = sharp, 1.0 = blurry
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaEmbeddingEntity) return false
        return mediaId == other.mediaId
    }

    override fun hashCode(): Int = mediaId.hashCode()
}

