package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_clusters",
    indices = [
        Index(value = ["clusterId"]),
        Index(value = ["mediaStoreId"])
    ]
)
data class FaceClusterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clusterId: String,
    val personName: String = "",
    val mediaStoreId: Long,
    val faceEmbedding: FloatArray? = null,
    val boundingBox: String = "", // "left,top,right,bottom"
    val smileProbability: Float = 0f,
    val leftEyeOpenProb: Float = 0f,
    val rightEyeOpenProb: Float = 0f,
    val isPrimaryInPhoto: Boolean = true,
    val detectedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceClusterEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
