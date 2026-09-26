package co.juneai.june.di

import android.content.Context
import androidx.work.WorkManager
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.MyObjectBox
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.objectbox.Box
import io.objectbox.BoxStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ObjectBoxModule {

    @Provides
    @Singleton
    fun provideBoxStore(@ApplicationContext context: Context): BoxStore {
        return MyObjectBox.builder()
            .androidContext(context)
            .build()
    }

    @Provides
    @Singleton
    fun provideImageEmbeddingBox(boxStore: BoxStore): Box<ImageEmbedding> {
        return boxStore.boxFor(ImageEmbedding::class.java)
    }

    @Provides
    @Singleton
    fun provideVideoFrameEmbeddingBox(boxStore: BoxStore): Box<co.juneai.june.data.local.entities.VideoFrameEmbedding> {
        return boxStore.boxFor(co.juneai.june.data.local.entities.VideoFrameEmbedding::class.java)
    }

    @Provides
    @Singleton
    fun provideBackupRecordBox(boxStore: BoxStore): Box<co.juneai.june.data.local.entities.BackupRecord> {
        return boxStore.boxFor(co.juneai.june.data.local.entities.BackupRecord::class.java)
    }

    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
