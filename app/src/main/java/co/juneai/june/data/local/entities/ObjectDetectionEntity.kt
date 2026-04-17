package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "object_detections")
data class ObjectDetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val label: String,
    val confidence: Float,
    val boundingBoxLeft: Int,
    val boundingBoxTop: Int,
    val boundingBoxRight: Int,
    val boundingBoxBottom: Int,
    val timestamp: Long = System.currentTimeMillis()
)