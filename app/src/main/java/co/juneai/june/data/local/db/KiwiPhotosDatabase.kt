package co.juneai.june.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import co.juneai.june.data.local.converters.Converters
import co.juneai.june.data.local.daos.FaceClusterDao
import co.juneai.june.data.local.daos.MediaOcrDao
import co.juneai.june.data.local.daos.UndoRedoDao
import co.juneai.june.data.local.daos.VaultDao
import co.juneai.june.data.local.entities.FaceClusterEntity
import co.juneai.june.data.local.entities.MediaOcrEntity
import co.juneai.june.data.local.entities.MediaOcrFtsEntity
import co.juneai.june.data.local.entities.UndoRedoEntity
import co.juneai.june.data.local.entities.VaultItemEntity
import co.juneai.june.data.local.entities.VaultMediaEntity

@Database(
    entities = [
        UndoRedoEntity::class,
        VaultItemEntity::class,
        VaultMediaEntity::class,
        MediaOcrEntity::class,
        MediaOcrFtsEntity::class,
        FaceClusterEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class KiwiPhotosDatabase : RoomDatabase() {
    abstract fun undoRedoDao(): UndoRedoDao
    abstract fun vaultDao(): VaultDao
    abstract fun mediaOcrDao(): MediaOcrDao
    abstract fun faceClusterDao(): FaceClusterDao

    companion object {
        @Volatile
        private var instance: KiwiPhotosDatabase? = null

        fun getInstance(context: Context): KiwiPhotosDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    KiwiPhotosDatabase::class.java,
                    "kiwi_photos_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}