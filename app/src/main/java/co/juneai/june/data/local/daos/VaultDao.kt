package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.juneai.june.data.local.entities.VaultItemEntity

@Dao
interface VaultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity)

    @Query("SELECT * FROM vault_items ORDER BY addedAt DESC")
    suspend fun getAllVaultItems(): List<VaultItemEntity>

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun getVaultItemsCount(): Int

    @Delete
    suspend fun delete(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)
}