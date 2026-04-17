package co.juneai.june.di

import android.content.Context
import co.juneai.june.data.local.daos.ImageEmbeddingDao
import co.juneai.june.ml.PCACompression
import co.juneai.june.ml.ZeroShotTagger
import co.juneai.june.data.repository.GalleryRepositoryImpl
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.data.search.TwoStageSearchEngine
import co.juneai.june.data.search.intent.IntentParser
import co.juneai.june.data.repository.TrashManagerRepositoryImpl
import co.juneai.june.domain.repository.GalleryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providePCACompression(): PCACompression {
        return PCACompression()
    }
    
    @Provides
    @Singleton
    fun provideZeroShotTagger(): ZeroShotTagger {
        return ZeroShotTagger()
    }

    @Provides
    @Singleton
    fun provideSearchIntent(): IntentParser = IntentParser()

    @Provides
    @Singleton
    fun provideSearchEngine(dao: ImageEmbeddingDao): TwoStageSearchEngine {
        return TwoStageSearchEngine(dao)
    }

    @Provides
    @Singleton
    fun provideTrashManager(dao: ImageEmbeddingDao): TrashManagerRepositoryImpl {
        return TrashManagerRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideGalleryRepository(
        dao: ImageEmbeddingDao,
        searchEngine: TwoStageSearchEngine,
        trashManager: TrashManagerRepositoryImpl
    ): GalleryRepository {
        return GalleryRepositoryImpl(dao, searchEngine, trashManager)
    }

    @Provides
    @Singleton
    fun provideMediaStoreRepository(
       @ApplicationContext context: Context
    ): MediaStoreRepository {
        return MediaStoreRepository(context)
    }
}