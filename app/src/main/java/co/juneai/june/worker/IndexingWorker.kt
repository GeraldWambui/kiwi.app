package co.juneai.june.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.juneai.june.data.repository.ml.ImageRepository
import co.juneai.june.ml.clip.ClipEmbedder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Long-running [CoroutineWorker] that indexes all unindexed images using CLIP.
 *
 * Annotated with [@HiltWorker] so Hilt injects [repository] and [embedder] directly —
 * no Application casting, no manual service locator. Requires [PicSachApplication] to
 * implement [Configuration.Provider] with [HiltWorkerFactory] (see PicSachApplication.kt).
 *
 * ─── Foreground promotion ──────────────────────────────────────────────────────
 * [doWork] calls [setForeground] immediately, before any heavy work. This promotes
 * the worker to a foreground service so the OS will not kill it when the app backgrounds.
 * WorkManager handles the service lifecycle; we only supply the [ForegroundInfo].
 *
 * ─── Progress reporting ────────────────────────────────────────────────────────
 * Progress is published via [setProgress] with keys [KEY_INDEXED], [KEY_TOTAL],
 * [KEY_FAILED]. The ViewModel observes these via [WorkManager.getWorkInfosForUniqueWorkFlow].
 *
 * ─── Output data ───────────────────────────────────────────────────────────────
 * On [Result.success]: [OUT_INDEXED] + [OUT_FAILED] are set for a post-run summary.
 * On [Result.failure]: "error" key holds the exception message.
 */
@HiltWorker
class IndexingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ImageRepository,
    private val embedder: ClipEmbedder,
) : CoroutineWorker(context, params) {

    companion object {
        /** Unique work name — pass to [WorkManager.enqueueUniqueWork] and observers. */
        const val WORK_NAME = "kiwi_clip_indexing"

        const val KEY_INDEXED = "indexed"
        const val KEY_TOTAL   = "total"
        const val KEY_FAILED  = "failed"
        const val OUT_INDEXED = "out_indexed"
        const val OUT_FAILED  = "out_failed"

        private const val CHANNEL_ID        = "kiwi_indexing_channel"
        private const val NOTIF_ID          = 1001
        private const val NOTIF_THROTTLE_MS = 500L
    }

    private val notifManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastNotifTime = 0L

    // ── Work entry point ──────────────────────────────────────────────────────

    override suspend fun doWork(): Result {
        // Promote to foreground service FIRST — before any IO — so the OS cannot kill
        // us during the gap between worker start and the first setForeground call.
        ensureChannel()
        setForeground(makeForegroundInfo("Preparing…", 0, 0))

        return try {
            // 1. Remove stale entries (images deleted from device since last run)
            repository.pruneStale()

            // 2. Determine work to do
            val pending = repository.unindexedImages()
            val total   = repository.totalMediaStoreCount()

            if (pending.isEmpty()) {
                return Result.success(workDataOf(OUT_INDEXED to 0, OUT_FAILED to 0))
            }

            // 3. Embed + store with progress
            val alreadyIndexed = repository.indexedCount()
            var currentFailed  = 0

            val newlyIndexed = repository.batchEmbedAndStore(
                images    = pending,
                embedder  = embedder,
                chunkSize = 32,
                onProgress = { batchIndexed, _, batchFailed ->
                    val overall = alreadyIndexed + batchIndexed
                    currentFailed = batchFailed
                    reportProgress(overall, total, batchFailed)
                },
            )

            Result.success(
                workDataOf(OUT_INDEXED to newlyIndexed, OUT_FAILED to currentFailed),
            )
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    /**
     * Called by WorkManager when the system needs [ForegroundInfo] before [doWork] starts
     * (e.g. on API 31+ when the work is expedited and the app is in the background).
     * Must return quickly — no IO here.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        makeForegroundInfo("Starting indexing…", 0, 0)

    // ── Progress helpers ──────────────────────────────────────────────────────

    private suspend fun reportProgress(indexed: Int, total: Int, failed: Int) {
        // setProgress updates WorkInfo which the ViewModel observes
        setProgress(workDataOf(KEY_INDEXED to indexed, KEY_TOTAL to total, KEY_FAILED to failed))

        // Update notification — throttled so NotificationManager isn't flooded
        val now = System.currentTimeMillis()
        if (now - lastNotifTime >= NOTIF_THROTTLE_MS) {
            val label = if (failed > 0) "Indexing $indexed / $total ($failed failed)"
                        else            "Indexing $indexed / $total"
            notifManager.notify(NOTIF_ID, buildNotification(label, indexed, total))
            lastNotifTime = now
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun makeForegroundInfo(text: String, progress: Int, max: Int): ForegroundInfo {
        val notification = buildNotification(text, progress, max)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(text: String, progress: Int, max: Int) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Kiwi — Indexing")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setProgress(max, progress, max == 0)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Image Indexing",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "CLIP background image indexing progress" },
            )
        }
    }
}