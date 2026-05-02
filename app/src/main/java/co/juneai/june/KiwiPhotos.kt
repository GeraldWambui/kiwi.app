package co.juneai.june

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import co.juneai.june.ml.clip.ClipEmbedder
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

    lateinit var boxStore: BoxStore
    lateinit var embedder2: ClipEmbedder
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