package co.juneai.june.di

import android.content.Context
import androidx.room.Room
import co.juneai.june.data.local.db.KiwiPhotosDatabase
import co.juneai.june.data.local.daos.VaultDao
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
        return Room.databaseBuilder(
            context,
            KiwiPhotosDatabase::class.java,
            "kiwi_photos_db"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideVaultDao(
        db: KiwiPhotosDatabase
    ): VaultDao {
        return db.vaultDao()
    }
}