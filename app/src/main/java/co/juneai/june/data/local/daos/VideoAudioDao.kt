package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.juneai.june.data.local.entities.VideoAudioEntity

@Dao
interface VideoAudioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<VideoAudioEntity>)

    @Query("SELECT * FROM video_audio WHERE mediaStoreId = :mediaStoreId ORDER BY timestampMs ASC")
    suspend fun getAudioByMediaId(mediaStoreId: Long): List<VideoAudioEntity>

    @Query("""
        SELECT video_audio.* FROM video_audio 
        JOIN video_audio_fts ON video_audio.id = video_audio_fts.rowid 
        WHERE video_audio_fts MATCH :query
        ORDER BY video_audio.confidence DESC
    """)
    suspend fun searchAudioFts(query: String): List<VideoAudioEntity>

    @Query("""
        SELECT * FROM video_audio 
        WHERE transcript LIKE '%' || :query || '%' 
           OR audioEvents LIKE '%' || :query || '%'
        ORDER BY confidence DESC
    """)
    suspend fun searchAudioLike(query: String): List<VideoAudioEntity>

    @Query("SELECT DISTINCT mediaStoreId FROM video_audio")
    suspend fun getIndexedVideoIds(): List<Long>

    @Query("DELETE FROM video_audio WHERE mediaStoreId = :mediaStoreId")
    suspend fun deleteByMediaId(mediaStoreId: Long)

    @Query("DELETE FROM video_audio")
    suspend fun clearAll()
}
