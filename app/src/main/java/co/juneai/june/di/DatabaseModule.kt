package co.juneai.june.di

import android.content.Context
import androidx.room.Room
import co.juneai.june.data.local.daos.FaceClusterDao
import co.juneai.june.data.local.daos.MediaOcrDao
import co.juneai.june.data.local.daos.UndoRedoDao
import co.juneai.june.data.local.daos.VaultDao
import co.juneai.june.data.local.daos.VideoAudioDao
import co.juneai.june.data.local.db.KiwiPhotosDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): KiwiPhotosDatabase {
        return KiwiPhotosDatabase.getInstance(context)
    }

    @Provides
    fun provideVaultDao(
        db: KiwiPhotosDatabase
    ): VaultDao {
        return db.vaultDao()
    }

    @Provides
    fun provideUndoRedoDao(
        db: KiwiPhotosDatabase
    ): UndoRedoDao {
        return db.undoRedoDao()
    }

    @Provides
    fun provideMediaOcrDao(
        db: KiwiPhotosDatabase
    ): MediaOcrDao {
        return db.mediaOcrDao()
    }

    @Provides
    fun provideFaceClusterDao(
        db: KiwiPhotosDatabase
    ): FaceClusterDao {
        return db.faceClusterDao()
    }

    @Provides
    fun provideVideoAudioDao(
        db: KiwiPhotosDatabase
    ): VideoAudioDao {
        return db.videoAudioDao()
    }
}