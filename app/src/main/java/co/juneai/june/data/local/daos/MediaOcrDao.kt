package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.juneai.june.data.local.entities.MediaOcrEntity

@Dao
interface MediaOcrDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: MediaOcrEntity): Long

    @Query("SELECT * FROM media_ocr WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun getOcrByMediaId(mediaStoreId: Long): MediaOcrEntity?

    @Query("SELECT mediaStoreId FROM media_ocr JOIN media_ocr_fts ON media_ocr.id = media_ocr_fts.rowid WHERE media_ocr_fts MATCH :query")
    suspend fun searchOcrFts(query: String): List<Long>

    @Query("SELECT mediaStoreId FROM media_ocr WHERE extractedText LIKE '%' || :query || '%'")
    suspend fun searchOcrLike(query: String): List<Long>

    @Query("SELECT * FROM media_ocr")
    suspend fun getAllOcr(): List<MediaOcrEntity>

    @Query("DELETE FROM media_ocr WHERE mediaStoreId = :mediaStoreId")
    suspend fun deleteByMediaId(mediaStoreId: Long)

    @Query("DELETE FROM media_ocr")
    suspend fun clearAll()
}
