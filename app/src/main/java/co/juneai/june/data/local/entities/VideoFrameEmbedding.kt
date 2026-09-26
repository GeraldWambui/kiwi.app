package co.juneai.june.data.local.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class VideoFrameEmbedding(
    @Id var id: Long = 0,
    @Index var mediaStoreId: Long = 0,
    var timestampMs: Long = 0,
    var frameIndex: Int = 0,
    @HnswIndex(dimensions = 512)
    var embedding: FloatArray? = null,
    var ocrText: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VideoFrameEmbedding
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
