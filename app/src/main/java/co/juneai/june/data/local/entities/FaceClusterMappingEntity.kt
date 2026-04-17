package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "face_cluster_mapping",
    foreignKeys = [
        ForeignKey(
            FaceClusterEntity::class,
            ["clusterId"],
            ["clusterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(FaceEmbeddingEntity::class, ["id"], ["id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("clusterId"), Index("faceId")]
)
data class FaceClusterMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clusterId: Long,
    val faceId: Long
)






