package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.juneai.june.data.local.entities.VaultItemEntity
import co.juneai.june.data.local.entities.VaultMediaEntity
import co.juneai.june.data.model.VaultMediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_media ORDER BY timestamp DESC")
    fun getAllMedia(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE id = :id")
    suspend fun getMediaById(id: String): VaultMediaEntity?

    @Query("SELECT * FROM vault_media WHERE type = :type")
    fun getMediaByType(type: VaultMediaType): Flow<List<VaultMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: VaultMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMultipleMedia(mediaList: List<VaultMediaEntity>)

    @Delete
    suspend fun deleteMedia(media: VaultMediaEntity)

    @Query("DELETE FROM vault_media WHERE id IN (:ids)")
    suspend fun deleteMultipleMedia(ids: List<String>)

    @Query("SELECT COUNT(*) FROM vault_media")
    suspend fun getMediaCount(): Int

    @Query("SELECT SUM(fileSize) FROM vault_media")
    suspend fun getTotalSize(): Long?

    @Query("SELECT * FROM vault_media WHERE timestamp > :since")
    fun getMediaSince(since: Long): Flow<List<VaultMediaEntity>>

    @Query("DELETE FROM vault_media")
    suspend fun clearAll()
}
