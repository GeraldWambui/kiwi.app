package co.juneai.june.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_clusters")
data class FaceClusterEntity(
    @PrimaryKey(autoGenerate = true) val clusterId: Long = 0,
    val personName: String? = null,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val centroid: ByteArray,
    val mediaCount: Int,
    val confidence: Float,
    val isMainUser: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
