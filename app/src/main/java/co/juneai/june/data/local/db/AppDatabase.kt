package co.juneai.june.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import co.juneai.june.data.local.daos.ImageEmbeddingDao
import co.juneai.june.data.local.entities.ImageEmbeddingEntity
import co.juneai.june.data.local.entities.UserAlbumEntity

@Database(entities = [ImageEmbeddingEntity::class, UserAlbumEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageEmbeddingDao(): ImageEmbeddingDao
}