package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.juneai.june.data.local.entities.MediaEmbeddingEntity

@Dao
interface MediaEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: MediaEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<MediaEmbeddingEntity>)

    @Query("SELECT * FROM media_embeddings WHERE mediaId = :mediaId")
    suspend fun getEmbedding(mediaId: Long): MediaEmbeddingEntity?

    @Query("SELECT * FROM media_embeddings ORDER BY takenDate DESC")
    suspend fun getAllEmbeddings(): List<MediaEmbeddingEntity>

    @Query("SELECT * FROM media_embeddings WHERE nsfwScore > :threshold")
    suspend fun getNSFWImages(threshold: Float = 0.5f): List<MediaEmbeddingEntity>

    @Query("SELECT * FROM media_embeddings WHERE blurriness > :threshold")
    suspend fun getBlurryImages(threshold: Float = 0.5f): List<MediaEmbeddingEntity>

    @Query("SELECT * FROM media_embeddings WHERE takenDate BETWEEN :start AND :end")
    suspend fun getMediaInDateRange(start: Long, end: Long): List<MediaEmbeddingEntity>

    @Query("SELECT DISTINCT dominantColor FROM media_embeddings WHERE dominantColor IS NOT NULL")
    suspend fun getUniqueDominantColors(): List<Int>

    @Query("DELETE FROM media_embeddings WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)

    @Query("DELETE FROM media_embeddings WHERE mediaId IN (:mediaIds)")
    suspend fun deleteMultiple(mediaIds: List<Long>)

    @Query("SELECT COUNT(*) FROM media_embeddings")
    suspend fun getTotalCount(): Int

    @Query("""
        SELECT * FROM media_embeddings 
        WHERE locationLatitude IS NOT NULL AND locationLongitude IS NOT NULL
        ORDER BY takenDate DESC
    """)
    suspend fun getGeoTaggedMedia(): List<MediaEmbeddingEntity>
}