package co.juneai.june.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.hilt.work.HiltWorker
import co.juneai.june.data.repository.nsfw.NsfwFlagStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber

/**
 * WorkManager worker that scans all media for NSFW content.
 * Shows a foreground notification with progress.
 * Flags items in NsfwFlagStore for immediate blur on next gallery load.
 */
@HiltWorker
class NsfwScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val nsfwFlagStore: NsfwFlagStore
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "NsfwScanChannel"
        const val NOTIFICATION_ID = 2001
        const val WORK_NAME = "NsfwScanWork"
        const val WORK_NAME_NEW_MEDIA = "NsfwScanNewMedia"

        // Input/output keys
        const val KEY_MEDIA_IDS = "KEY_MEDIA_IDS"
        const val PROGRESS_TOTAL = "PROGRESS_TOTAL"
        const val PROGRESS_CURRENT = "PROGRESS_CURRENT"
        const val PROGRESS_FLAGGED = "PROGRESS_FLAGGED"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Check if protection is enabled
        if (!nsfwFlagStore.isProtectionEnabled) {
            Timber.d("NSFW protection disabled, skipping scan")
            return@withContext Result.success()
        }

        // Get specific media IDs from input, or scan all media
        val specificIds = inputData.getLongArray(KEY_MEDIA_IDS)
        val mediaItems = if (specificIds != null && specificIds.isNotEmpty()) {
            specificIds.toList()
        } else {
            getAllMediaIds(applicationContext)
        }

        // Filter out already-scanned items (unless specific IDs were given)
        val toScan = if (specificIds != null) {
            mediaItems // Always re-scan specific items
        } else {
            mediaItems.filter { !nsfwFlagStore.isScanned(it) }
        }

        if (toScan.isEmpty()) {
            Timber.d("No new media to scan for NSFW content")
            nsfwFlagStore.isInitialScanComplete = true
            return@withContext Result.success()
        }

        Timber.d("Starting NSFW scan for ${toScan.size} items")
        createNotificationChannel()

        // Set as foreground work
        setForeground(getForegroundInfo())

        var flaggedCount = 0
        val batchResults = mutableMapOf<Long, Boolean>()

        toScan.forEachIndexed { index, mediaId ->
            if (isStopped) {
                Timber.w("NSFW scan worker stopped prematurely after $index items")
                // Persist whatever we have so far
                if (batchResults.isNotEmpty()) {
                    nsfwFlagStore.markScannedBatch(batchResults)
                }
                return@withContext Result.failure()
            }

            try {
                val bitmap = loadThumbnail(applicationContext, mediaId)
                if (bitmap != null) {
                    val isNsfw = false //change in prod
                    batchResults[mediaId] = isNsfw
                    if (isNsfw) flaggedCount++
                    bitmap.recycle()
                } else {
                    batchResults[mediaId] = false
                }
            } catch (e: Exception) {
                Timber.e(e, "Error scanning media $mediaId")
                batchResults[mediaId] = false
            }

            // Update progress notification every 5 items
            if (index % 5 == 0 || index == toScan.size - 1) {
                updateProgress(index + 1, toScan.size, flaggedCount)
            }

            // Batch persist every 50 items
            if (batchResults.size >= 50) {
                nsfwFlagStore.markScannedBatch(batchResults)
                batchResults.clear()
            }

            yield() // Cooperative cancellation
        }

        // Persist remaining batch
        if (batchResults.isNotEmpty()) {
            nsfwFlagStore.markScannedBatch(batchResults)
        }

        nsfwFlagStore.isInitialScanComplete = true
        notificationManager.cancel(NOTIFICATION_ID)

        Timber.d("NSFW scan complete: ${toScan.size} scanned, $flaggedCount flagged")

        Result.success(
            workDataOf(
                PROGRESS_TOTAL to toScan.size,
                PROGRESS_FLAGGED to flaggedCount
            )
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return ForegroundInfo(
            NOTIFICATION_ID,
            createNotification(0, 100, 0),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private suspend fun updateProgress(current: Int, total: Int, flagged: Int) {
        setProgress(
            workDataOf(
                PROGRESS_CURRENT to current,
                PROGRESS_TOTAL to total,
                PROGRESS_FLAGGED to flagged
            )
        )
        notificationManager.notify(NOTIFICATION_ID, createNotification(current, total, flagged))
    }

    private fun createNotification(current: Int, total: Int, flagged: Int): Notification {
        val title = "Scanning for sensitive content"
        val text = if (total > 0) "Processed $current of $total items" else "Preparing scan..."

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setColor(0xFF2E7D32.toInt())
            .setOngoing(true)
            .setProgress(total, current, total == 0)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NSFW Content Scan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of background NSFW content scanning"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Load a small thumbnail for efficient scanning.
     */
    private fun loadThumbnail(context: Context, mediaId: Long): Bitmap? {
        return try {
            // Try loading image thumbnail
            val imageUri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId
            )
            loadBitmapFromUri(context, imageUri)
        } catch (e: Exception) {
            try {
                // Try as video
                val videoUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId
                )
                loadVideoThumbnail(context, videoUri)
            } catch (e2: Exception) {
                Timber.w("Could not load thumbnail for media $mediaId")
                null
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)

            // Calculate sample size for ~320px
            val targetSize = 320
            options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            context.contentResolver.openInputStream(uri)?.use { newStream ->
                BitmapFactory.decodeStream(newStream, null, options)
            }
        }
    }

    private fun loadVideoThumbnail(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            retriever.release()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Get all image and video media IDs from MediaStore.
     */
    private fun getAllMediaIds(context: Context): List<Long> {
        val ids = mutableListOf<Long>()

        // Images
        val imageProjection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(idCol))
            }
        }

        // Videos
        val videoProjection = arrayOf(MediaStore.Video.Media._ID)
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(idCol))
            }
        }

        return ids
    }
}
