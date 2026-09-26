package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(contentEntity = MediaOcrEntity::class)
@Entity(tableName = "media_ocr_fts")
data class MediaOcrFtsEntity(
    @PrimaryKey
    val rowid: Long,
    val extractedText: String,
    val textSnippet: String
)
