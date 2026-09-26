package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import co.juneai.june.data.local.entities.UndoRedoEntity

@Dao
interface UndoRedoDao {
    @Insert
    suspend fun insert(action: UndoRedoEntity)

    @Delete
    suspend fun delete(action: UndoRedoEntity)

    @Query("SELECT * FROM undo_redo_stack ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastAction(): UndoRedoEntity?

    @Query("DELETE FROM undo_redo_stack WHERE timestamp < :olderThan")
    suspend fun clearOldActions(olderThan: Long)

    @Query("DELETE FROM undo_redo_stack")
    suspend fun clearAll()
}