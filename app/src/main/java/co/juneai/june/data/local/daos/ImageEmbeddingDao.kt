package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import co.juneai.june.data.local.entities.ImageEmbeddingEntity

@Dao
interface ImageEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertEmbedding(entity: ImageEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertEmbeddings(entities: List<ImageEmbeddingEntity>)

    @Update
    suspend fun updateEmbedding(entity: ImageEmbeddingEntity)

    @Query("SELECT * FROM image_embeddings WHERE `indexed` = 1 AND isTrashed = 0")
    suspend fun getAllActiveEmbeddings(): List<ImageEmbeddingEntity>

    @Query("SELECT * FROM image_embeddings WHERE folderName = :folderName AND `indexed` = 1 AND isTrashed = 0 ORDER BY dateAdded DESC")
    suspend fun getEmbeddingsByFolder(folderName: String): List<ImageEmbeddingEntity>

    @Query("SELECT DISTINCT folderName FROM image_embeddings WHERE `indexed` = 1 AND isTrashed = 0 ORDER BY folderName")
    suspend fun getAllActiveFolders(): List<String>

    @Query("SELECT * FROM image_embeddings WHERE mediaStoreId = :id")
    suspend fun getEmbeddingById(id: Long): ImageEmbeddingEntity?

    @Query("UPDATE image_embeddings SET isTrashed = 1, trashedAt = :trashedAt WHERE mediaStoreId IN (:ids)")
    suspend fun moveToTrash(ids: List<Long>, trashedAt: Long = System.currentTimeMillis())

    @Query("UPDATE image_embeddings SET isTrashed = 0 WHERE mediaStoreId IN (:ids)")
    suspend fun restoreFromTrash(ids: List<Long>)

    @Query("SELECT * FROM image_embeddings WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    suspend fun getTrashBin(): List<ImageEmbeddingEntity>

    @Query("DELETE FROM image_embeddings WHERE isTrashed = 1 AND trashedAt < :cutoffTime")
    suspend fun permanentlyDeleteOldTrashed(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM image_embeddings WHERE `indexed` = 1 AND isTrashed = 0")
    suspend fun getActiveImageCount(): Int

    @Query("DELETE FROM image_embeddings WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}