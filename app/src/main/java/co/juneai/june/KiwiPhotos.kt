package co.juneai.june

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import co.juneai.june.data.local.entities.MyObjectBox
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import io.objectbox.BoxStore
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class KiwiPhotos : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var boxStore: BoxStore
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        try {
            val trashWork = androidx.work.PeriodicWorkRequestBuilder<co.juneai.june.service.TrashCleanupWorker>(
                1, java.util.concurrent.TimeUnit.DAYS
            ).setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            ).build()

            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                co.juneai.june.service.TrashCleanupWorker.WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                trashWork
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule TrashCleanupWorker")
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.40)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.08)
                    .build()
            }
            .crossfade(false)
            .allowHardware(true)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}