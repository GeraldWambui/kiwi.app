package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_audio",
    indices = [
        Index(value = ["mediaStoreId"])
    ]
)
data class VideoAudioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaStoreId: Long,
    val timestampMs: Long,
    val durationMs: Long = 0,
    val transcript: String = "",
    val audioEvents: String = "",
    val confidence: Float = 1.0f,
    val indexedAt: Long = System.currentTimeMillis()
)
