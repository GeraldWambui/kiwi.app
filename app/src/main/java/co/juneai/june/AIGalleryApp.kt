package co.juneai.june

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.hilt.work.HiltWorkerFactory
import co.juneai.june.data.MyObjectBox
import co.juneai.june.ml.clip.ClipEmbedder
import co.juneai.june.worker.MediaIndexingWorker
import co.juneai.june.worker.NsfwScanWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.decode.VideoFrameDecoder
import io.objectbox.BoxStore

@HiltAndroidApp
class AIGalleryApp : Application(), ImageLoaderFactory, Configuration.Provider {

    lateinit var boxStore: BoxStore
    lateinit var embedder2: ClipEmbedder
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    val embedder: ClipEmbedder by lazy { ClipEmbedder(this) }


    override fun onCreate() {
        super.onCreate()
        boxStore = MyObjectBox.builder()
            .androidContext(this)
            .build()
        embedder2 = ClipEmbedder(this)
        Timber.plant(Timber.DebugTree())

        scheduleInitialIndexAndScan()

        scheduleMediaObserver()

        Timber.i("AI Gallery App initialized")
        Timber.i("Models: MobileCLIP INT8")
    }

    /**
     * Chain: MediaIndexingWorker (full index) → NsfwScanWorker
     * Both run as foreground workers with notification progress.
     * Uses KEEP policy so re-launches don't restart a running chain.
     */
    private fun scheduleInitialIndexAndScan() {
        try {
            val workManager = WorkManager.getInstance(this)

            val indexRequest = OneTimeWorkRequestBuilder<MediaIndexingWorker>()
                .addTag("initial_index")
                .build()

            val nsfwRequest = OneTimeWorkRequestBuilder<NsfwScanWorker>()
                .addTag("initial_nsfw")
                .build()

            // Chain: index first, then NSFW scan
            workManager.beginUniqueWork(
                "InitialIndexAndScan",
                ExistingWorkPolicy.KEEP,
                indexRequest
            ).then(nsfwRequest)
             .enqueue()

            Timber.d("✓ Initial index + NSFW scan chain scheduled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule initial index chain")
        }
    }

    /**
     * Observe MediaStore for new images → re-index + re-scan
     */
    private fun scheduleMediaObserver() {
        try {
            val workManager = WorkManager.getInstance(this)

            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .build()

            val indexWorkRequest = OneTimeWorkRequestBuilder<MediaIndexingWorker>()
                .setConstraints(constraints)
                .addTag("media_observer")
                .build()

            workManager.enqueueUniqueWork(
                "MediaStoreIndexObserver",
                ExistingWorkPolicy.REPLACE,
                indexWorkRequest
            )

            Timber.d("✓ MediaStore observer scheduled with ContentUriTrigger")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule media observer")
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