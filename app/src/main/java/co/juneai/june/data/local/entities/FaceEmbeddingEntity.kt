package co.juneai.june.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_embeddings")
data class FaceEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val embedding: ByteArray,
    val boundingBoxLeft: Int,
    val boundingBoxTop: Int,
    val boundingBoxRight: Int,
    val boundingBoxBottom: Int,
    val confidence: Float,
    val clusterId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)