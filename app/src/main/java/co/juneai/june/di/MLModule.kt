package co.juneai.june.di

import android.content.Context
import co.juneai.june.data.repository.MediaStoreScannerRepositoryImpl
import co.juneai.june.ml.MobileCLIPPipeline
import co.juneai.june.ml.PrimaryObjectDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import co.juneai.june.domain.search.ImageIndexingService
import co.juneai.june.domain.search.ImageSearchEngine
import co.juneai.june.ml.clip.ClipEmbedder
import io.objectbox.BoxStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MLModule {

    @Provides
    @Singleton
    fun provideClipEmbedder(@ApplicationContext context: Context): ClipEmbedder =
        ClipEmbedder(context.applicationContext)

    @Provides
    @Singleton
    fun provideImageSearchEngine(
        @ApplicationContext context: Context,
        boxStore: BoxStore,
        clipEngine: ClipEmbedder
    ): ImageSearchEngine{
        return ImageSearchEngine(context, boxStore, clipEngine)
    }

    @Provides
    @Singleton
    fun provideImageIndexingService(
        @ApplicationContext context: Context,
        boxStore: BoxStore,
        clipEngine: ClipEmbedder
    ): ImageIndexingService{
        return ImageIndexingService(context, boxStore, clipEngine)
    }
    @Provides
    @Singleton
    fun provideMobileCLIPPipeline(
        @ApplicationContext context: Context,
        primaryObjectDetector: PrimaryObjectDetector
    ): MobileCLIPPipeline {
        return MobileCLIPPipeline(context, primaryObjectDetector)
    }
    
    @Provides
    @Singleton
    fun provideMediaStoreScanner(
        @ApplicationContext context: Context
    ): MediaStoreScannerRepositoryImpl {
        return MediaStoreScannerRepositoryImpl(context)
    }
}