package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import co.juneai.june.data.local.entities.UserAlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAlbumDao {

    @Query("SELECT * FROM user_albums ORDER BY createdAt DESC")
    fun observeAlbums(): Flow<List<UserAlbumEntity>>

    @Insert
    suspend fun insert(album: UserAlbumEntity)
}