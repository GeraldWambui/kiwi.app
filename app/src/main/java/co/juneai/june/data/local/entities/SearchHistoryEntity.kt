package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val isCommand: Boolean,
    val commandType: String? = null,
    val resultCount: Int,
    val executionTimeMs: Long,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)