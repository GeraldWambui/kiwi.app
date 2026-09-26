package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_ocr",
    indices = [
        Index(value = ["mediaStoreId"], unique = true)
    ]
)
data class MediaOcrEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaStoreId: Long,
    val extractedText: String,
    val textSnippet: String = "",
    val confidence: Float = 1.0f,
    val indexedAt: Long = System.currentTimeMillis()
)
