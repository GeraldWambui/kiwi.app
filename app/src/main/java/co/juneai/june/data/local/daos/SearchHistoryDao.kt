package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import co.juneai.june.data.local.entities.SearchHistoryEntity

@Dao
interface SearchHistoryDao {
    @Insert
    suspend fun insert(history: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSearches(limit: Int = 20): List<SearchHistoryEntity>

    @Query("SELECT DISTINCT `query`  FROM search_history WHERE isCommand = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentQueryTerms(limit: Int = 10): List<String>

    @Query("SELECT COUNT(*) FROM search_history WHERE isCommand = 0")
    suspend fun getTotalSearchCount(): Int

    @Query("DELETE FROM search_history")
    suspend fun clear()
}