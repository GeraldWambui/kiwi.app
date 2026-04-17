package co.juneai.june.worker

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.juneai.june.data.model.SearchImageEntity
import co.juneai.june.data.model.SearchImageEntity_
import co.juneai.june.domain.search.ImageIndexingService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber

@HiltWorker
class MediaIndexingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val indexingService: ImageIndexingService,
    private val boxStore: BoxStore
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "MediaIndexingChannel"
        const val NOTIFICATION_ID = 1001

        const val KEY_IMAGE_URIS = "KEY_IMAGE_URIS"
        const val PROGRESS_TOTAL = "PROGRESS_TOTAL"
        const val PROGRESS_CURRENT = "PROGRESS_CURRENT"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val urisListStr = inputData.getStringArray(KEY_IMAGE_URIS)

            val targetUris = if (!urisListStr.isNullOrEmpty()) {
                urisListStr.map { Uri.parse(it) }
            } else {
                getAllImageUris(applicationContext)
            }

            if (targetUris.isEmpty()) {
                Log.e("Scan","No images to index")
                return@withContext Result.success()
            }

            // Get already-indexed URIs to skip them
            val imageBox = boxStore.boxFor(SearchImageEntity::class.java)
            val indexedPaths = imageBox.query()
                .build()
                .property(SearchImageEntity_.filePath)
                .findStrings()
                .toSet()

            val toIndex = targetUris.filter { it.toString() !in indexedPaths }

            if (toIndex.isEmpty()) {
                Timber.d("All ${targetUris.size} images already indexed, skipping")
                return@withContext Result.success()
            }

            Timber.Forest.d("Starting indexing: ${toIndex.size} new images (${indexedPaths.size} already indexed)")

            // Show foreground notification
            createNotificationChannel()
            setForeground(getForegroundInfo())

            var successCount = 0
            var failCount = 0

            toIndex.forEachIndexed { index, uri ->
                if (isStopped) {
                    Timber.Forest.w("Indexing worker stopped after $index items")
                    return@withContext Result.failure()
                }

                // Update notification progress
                updateProgress(index + 1, toIndex.size)

                try {
                    val result = indexingService.indexImage(uri)
                    if (result.isSuccess) successCount++ else failCount++
                } catch (e: Exception) {
                    Timber.Forest.e(e, "Failed to index: $uri")
                    failCount++
                }

                yield()
            }

            Timber.Forest.d("Indexing complete: $successCount successes, $failCount failures out of ${toIndex.size}")

            // Show completion notification briefly
            notificationManager.notify(NOTIFICATION_ID, createCompletionNotification(successCount))

            // Play success chime
            playSuccessChime()

            // Dismiss after 3 seconds
            delay(3000)
            notificationManager.cancel(NOTIFICATION_ID)

            Result.success(
                workDataOf(
                    PROGRESS_TOTAL to toIndex.size,
                    PROGRESS_CURRENT to toIndex.size
                )
            )
        } catch (e: Exception) {
            Timber.Forest.e(e, "Indexing worker failed")
            notificationManager.cancel(NOTIFICATION_ID)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return ForegroundInfo(
            NOTIFICATION_ID,
            createNotification(0, 100),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private suspend fun updateProgress(current: Int, total: Int) {
        setProgress(workDataOf(PROGRESS_CURRENT to current, PROGRESS_TOTAL to total))
        notificationManager.notify(NOTIFICATION_ID, createNotification(current, total))
    }

    private fun createNotification(current: Int, total: Int): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Indexing Media")
            .setContentText("Processed $current of $total images")
            .setSmallIcon(R.drawable.ic_menu_gallery)
            .setColor(0xFF2E7D32.toInt())
            .setOngoing(true)
            .setProgress(total, current, false)
            .setSilent(true)
            .build()
    }

    private fun createCompletionNotification(count: Int): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Indexing Complete")
            .setContentText("$count images indexed for search")
            .setSmallIcon(R.drawable.ic_menu_gallery)
            .setColor(0xFF2E7D32.toInt())
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Indexing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of background image indexing"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun playSuccessChime() {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone.play()
        } catch (e: Exception) {
            Timber.Forest.e(e, "Failed to play success chime")
        }
    }

    private fun getAllImageUris(context: Context): List<Uri> {
        val uriList = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                uriList.add(contentUri)
            }
        }
        return uriList
    }
}