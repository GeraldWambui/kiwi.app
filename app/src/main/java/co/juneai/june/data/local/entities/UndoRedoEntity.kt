package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "undo_redo_stack")
data class UndoRedoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionId: String,
    val actionType: String,
    val originalStateJson: String,
    val isUndo: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)