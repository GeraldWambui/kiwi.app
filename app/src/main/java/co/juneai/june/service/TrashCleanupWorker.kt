package co.juneai.june.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val embeddingBox: Box<ImageEmbedding>
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "trash_cleanup_periodic_work"
        const val DEFAULT_RETENTION_DAYS = 30
        const val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.d("Running TrashCleanupWorker")
        try {
            val retentionDays = inputData.getInt("retention_days", DEFAULT_RETENTION_DAYS)
            val expiryThreshold = System.currentTimeMillis() - (retentionDays * MILLIS_IN_DAY)

            val expiredItems = embeddingBox.query(
                ImageEmbedding_.isTrashed.equal(true)
                    .and(ImageEmbedding_.trashedAt.less(expiryThreshold))
            ).build().find()

            Timber.d("Found ${expiredItems.size} expired items in trash for permanent deletion")

            for (item in expiredItems) {
                try {
                    // Delete from MediaStore if uri is present
                    if (item.uri.isNotEmpty()) {
                        val uri = Uri.parse(item.uri)
                        try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (se: SecurityException) {
                            Timber.w("SecurityException deleting MediaStore uri: $uri")
                        }
                    }
                    embeddingBox.remove(item)
                } catch (e: Exception) {
                    Timber.e(e, "Error permanently deleting trashed item ${item.id}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error in TrashCleanupWorker")
            Result.failure()
        }
    }
}
