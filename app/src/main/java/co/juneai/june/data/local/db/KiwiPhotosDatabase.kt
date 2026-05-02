package co.juneai.june.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import co.juneai.june.data.local.daos.UndoRedoDao
import co.juneai.june.data.local.daos.VaultDao
import co.juneai.june.data.local.entities.UndoRedoEntity
import co.juneai.june.data.local.entities.VaultItemEntity
import co.juneai.june.data.local.entities.VaultMediaEntity

@Database(
    entities = [
        UndoRedoEntity::class,
        VaultItemEntity::class,
        VaultMediaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KiwiPhotosDatabase : RoomDatabase() {
    abstract fun undoRedoDao(): UndoRedoDao
    abstract fun vaultDao(): VaultDao

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
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }
        }
    }
}