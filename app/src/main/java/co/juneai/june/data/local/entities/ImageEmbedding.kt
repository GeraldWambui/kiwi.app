package co.juneai.june.data.local.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class ImageEmbedding(
    @Id var id: Long = 0,
    @Index var mediaStoreId: Long = 0,
    var uri: String = "",
    var displayName: String = "",
    @HnswIndex(dimensions = 512)
    var embedding: FloatArray? = null,
    var ocrEmbedding: FloatArray? = null,
    var indexedAt: Long = 0,
    var isNsfw: Boolean = false,
    var nsfwScore: Float = 0f,
    var isTrashed: Boolean = false,
    var trashedAt: Long = 0,
    var sharpnessScore: Float = 0f,
    var isBlurry: Boolean = false,
    var aestheticScore: Float = 0f,
    var faceCount: Int = 0,
    var hasSignificantText: Boolean = false,
    var primaryObject: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImageEmbedding
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
