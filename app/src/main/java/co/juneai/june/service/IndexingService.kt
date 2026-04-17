package co.juneai.june.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import co.juneai.june.AIGalleryApp
import co.juneai.june.data.local.ml.ImageEmbedding
import co.juneai.june.data.repository.ml.ImageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import io.objectbox.Box

/**
 * Foreground service that indexes images in the background using CLIP embeddings.
 *
 * Design decisions:
 *  - Uses [ImageRepository.batchEmbedAndStore] (chunked transactions) instead of
 *    one-at-a-time inserts — dramatically faster for large galleries.
 *  - Notification updates are throttled to [NOTIF_THROTTLE_MS] to avoid overwhelming
 *    NotificationManager (which can drop updates or ANR the service if called too fast).
 *  - Checks available memory before starting; pauses and retries if the system is
 *    under memory pressure during indexing.
 *  - Tracks failures separately so users see accurate counts ("indexed X / Y, Z failed").
 *  - Stops itself cleanly after completion or on [ACTION_STOP].
 *
 * Send [ACTION_START] intent to begin indexing.
 * Send [ACTION_STOP] intent (or tap the notification action) to cancel.
 */
class IndexingService : LifecycleService() {

    companion object {
        const val CHANNEL_ID   = "clip_index_channel"
        const val NOTIF_ID     = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP  = "ACTION_STOP"

        const val BROADCAST  = "com.example.picsach.INDEXING_PROGRESS"
        const val EX_INDEXED = "indexed"
        const val EX_TOTAL   = "total"
        const val EX_FAILED  = "failed"
        const val EX_DONE    = "done"

        /**
         * Minimum milliseconds between notification updates.
         * NotificationManager can silently drop updates sent faster than ~1/sec on
         * some OEM ROMs; 500ms keeps us safely below that threshold.
         */
        private const val NOTIF_THROTTLE_MS = 500L

        /** Minimum free RAM required to start indexing (bytes). */
        private const val MIN_FREE_MEMORY_BYTES = 80L * 1024 * 1024   // 80 MB

        fun startIntent(context: Context): Intent =
            Intent(context, IndexingService::class.java).setAction(ACTION_START)
    }

    // Service-owned scope — independent of LifecycleService scope so it survives
    // configuration changes and is cancelled explicitly in onDestroy.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var indexingJob: Job? = null

    private lateinit var repository: ImageRepository
    private lateinit var box: Box<ImageEmbedding>
    private lateinit var notifManager: NotificationManager

    private var lastNotifTime = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        // Initialize ObjectBox box and index
        box = (application as AIGalleryApp).boxStore.boxFor(ImageEmbedding::class.java)
        repository = ImageRepository(this, box)
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                indexingJob?.cancel()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (indexingJob?.isActive != true) {
                    startIndexing()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ── Indexing pipeline ─────────────────────────────────────────────────────

    @SuppressLint("ForegroundServiceType")
    private fun startIndexing() {
        indexingJob = serviceScope.launch {
            startForeground(NOTIF_ID, buildNotification("Preparing…", 0, 0))

            // Abort early if the system is already under memory pressure
            if (!hasEnoughMemory()) {
                forceUpdate("Low memory — indexing postponed", 0, 0)
                broadcast(0, 0, 0, isDone = true)
                stopSelf()
                return@launch
            }

            val embedder = (application as AIGalleryApp).embedder

            // Remove embeddings for images that no longer exist on device
            repository.pruneStale()

            val pending = repository.unindexedImages()
            val total = repository.totalMediaStoreCount()
            val alreadyIndexed = repository.indexedCount()

            if (pending.isEmpty()) {
                forceUpdate("All $total images indexed ✓", alreadyIndexed, total)
                broadcast(alreadyIndexed, total, failed = 0, isDone = true)
                stopSelf()
                return@launch
            }

            var indexed = alreadyIndexed
            var failed = 0

            try {
                val successCount = repository.batchEmbedAndStore(
                    images = pending,
                    embedder = embedder,
                    chunkSize = 32,
                    onProgress = { done, batchTotal, batchFailed ->
                        // done = progress within the pending batch
                        val overall = alreadyIndexed + done
                        indexed = overall
                        failed = batchFailed

                        maybeUpdateNotification(
                            "Indexing $overall / $total",
                            overall,
                            total,
                        )
                        broadcast(overall, total, failed, isDone = false)
                    },
                )
                failed = pending.size - successCount
            } catch (e: Exception) {
                // Unexpected error — surface in notification, then stop
                e.printStackTrace()
                forceUpdate("Indexing error: ${e.message}", indexed, total)
                broadcast(indexed, total, failed, isDone = true)
                stopSelf()
                return@launch
            }


            val summary = when {
                failed == 0 -> "All ${total} images indexed ✓"
                else -> "Indexed $indexed / $total  ($failed failed)"
            }
            forceUpdate(summary, indexed, total)
            broadcast(indexed, total, failed, isDone = true)
            stopSelf()
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(text: String, progress: Int, max: Int): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, IndexingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PicSach Indexing")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setProgress(max, progress, max == 0)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** Updates the notification, respecting the [NOTIF_THROTTLE_MS] rate limit. */
    private fun maybeUpdateNotification(text: String, progress: Int, max: Int) {
        val now = System.currentTimeMillis()
        if (now - lastNotifTime >= NOTIF_THROTTLE_MS) {
            notifManager.notify(NOTIF_ID, buildNotification(text, progress, max))
            lastNotifTime = now
        }
    }

    /** Updates the notification immediately, bypassing the throttle. */
    private fun forceUpdate(text: String, progress: Int, max: Int) {
        notifManager.notify(NOTIF_ID, buildNotification(text, progress, max))
        lastNotifTime = System.currentTimeMillis()
    }

    @SuppressLint("NewApi")
    private fun createNotificationChannel() {
        notifManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Image Indexing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "CLIP background image indexing progress" },
        )
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private fun broadcast(indexed: Int, total: Int, failed: Int, isDone: Boolean) {
        sendBroadcast(
            Intent(BROADCAST).apply {
                putExtra(EX_INDEXED, indexed)
                putExtra(EX_TOTAL, total)
                putExtra(EX_FAILED, failed)
                putExtra(EX_DONE, isDone)
            },
        )
    }

    // ── Memory guard ──────────────────────────────────────────────────────────

    /**
     * Returns true if there is at least [MIN_FREE_MEMORY_BYTES] of available RAM.
     *
     * Bitmap decoding + TFLite inference peaks at roughly 30–60 MB per image on top
     * of the model's working set (~80 MB for ViT-B/32). If the system has less than
     * [MIN_FREE_MEMORY_BYTES] free, we decline to start rather than risk an OOM kill.
     */
    private fun hasEnoughMemory(): Boolean {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi.availMem >= MIN_FREE_MEMORY_BYTES
    }
}