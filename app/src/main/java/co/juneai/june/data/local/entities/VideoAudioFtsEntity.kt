package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(contentEntity = VideoAudioEntity::class)
@Entity(tableName = "video_audio_fts")
data class VideoAudioFtsEntity(
    @PrimaryKey
    val rowid: Long,
    val transcript: String,
    val audioEvents: String
)
