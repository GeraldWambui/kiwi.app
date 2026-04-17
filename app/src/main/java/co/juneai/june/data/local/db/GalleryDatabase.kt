package co.juneai.june.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import co.juneai.june.data.local.daos.FaceClusterDao
import co.juneai.june.data.local.daos.FaceEmbeddingDao
import co.juneai.june.data.local.daos.MediaEmbeddingDao
import co.juneai.june.data.local.daos.ObjectDetectionDao
import co.juneai.june.data.local.daos.SearchHistoryDao
import co.juneai.june.data.local.daos.UndoRedoDao
import co.juneai.june.data.local.daos.VaultDao
import co.juneai.june.data.local.entities.FaceEmbeddingEntity
import co.juneai.june.data.local.entities.MediaEmbeddingEntity
import co.juneai.june.data.local.entities.FaceClusterEntity
import co.juneai.june.data.local.entities.FaceClusterMappingEntity
import co.juneai.june.data.local.entities.ObjectDetectionEntity
import co.juneai.june.data.local.entities.SearchHistoryEntity
import co.juneai.june.data.local.entities.UndoRedoEntity
import co.juneai.june.data.local.entities.VaultItemEntity

@Database(
    entities = [
        MediaEmbeddingEntity::class,
        FaceEmbeddingEntity::class,
        FaceClusterEntity::class,
        FaceClusterMappingEntity::class,
        ObjectDetectionEntity::class,
        SearchHistoryEntity::class,
        UndoRedoEntity::class,
        VaultItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun mediaEmbeddingDao(): MediaEmbeddingDao
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao
    abstract fun faceClusterDao(): FaceClusterDao
    abstract fun objectDetectionDao(): ObjectDetectionDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun undoRedoDao(): UndoRedoDao
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var instance: GalleryDatabase? = null

        fun getInstance(context: Context): GalleryDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "june_gallery_search_db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }
        }
    }
}