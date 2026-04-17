package co.juneai.june.di

import android.content.Context
import androidx.room.Room
import co.juneai.june.data.local.daos.ImageEmbeddingDao
import co.juneai.june.data.local.db.AppDatabase
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
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "june_ai_gallery.db"
        ).build()
    }
    
    @Provides
    @Singleton
    fun provideImageEmbeddingDao(database: AppDatabase): ImageEmbeddingDao {
        return database.imageEmbeddingDao()
    }
}