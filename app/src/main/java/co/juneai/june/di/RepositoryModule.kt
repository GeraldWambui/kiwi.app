package co.juneai.june.di

import android.content.Context
import co.juneai.june.data.local.db.KiwiPhotosDatabase
import co.juneai.june.data.repository.KiwiPhotosRepositoryImpl
import co.juneai.june.ml.PCACompression
import co.juneai.june.ml.ZeroShotTagger
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.data.repository.VaultManagerRepositoryImpl
import co.juneai.june.domain.repository.GalleryRepository
import co.juneai.june.domain.repository.VaultManagerRepository
import co.juneai.june.utils.SecurityHelper
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
    fun provideKiwiPhotosRepository(): GalleryRepository = KiwiPhotosRepositoryImpl()

    @Provides
    @Singleton
    fun provideVaultManagerRepository(
        @ApplicationContext context: Context,
        database: KiwiPhotosDatabase,
        securityHelper: SecurityHelper
    ): VaultManagerRepository {
        return VaultManagerRepositoryImpl(context, database, securityHelper)
    }

    @Provides
    @Singleton
    fun provideSecurityHelper(): SecurityHelper = SecurityHelper()

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
    fun provideMediaStoreRepository(
       @ApplicationContext context: Context
    ): MediaStoreRepository {
        return MediaStoreRepository(context)
    }
}