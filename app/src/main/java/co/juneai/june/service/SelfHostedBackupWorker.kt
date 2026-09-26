package co.juneai.june.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.juneai.june.data.local.entities.BackupRecord
import co.juneai.june.data.local.entities.BackupRecord_
import co.juneai.june.data.local.prefs.SelfHostedConfigManager
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.network.backup.SelfHostedClientFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest

@HiltWorker
class SelfHostedBackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val configManager: SelfHostedConfigManager,
    private val mediaStoreRepository: MediaStoreRepository,
    private val backupBox: Box<BackupRecord>
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "self_hosted_backup_work"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
        const val KEY_BACKED_UP_COUNT = "backed_up_count"
        const val KEY_CURRENT_FILE = "current_file"
        const val KEY_ERROR = "error_message"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val config = configManager.loadConfig()
        if (!config.isConfigured) {
            Timber.w("Self-hosted backup skipped: server configuration is missing or incomplete")
            return@withContext Result.failure(workDataOf(KEY_ERROR to "Server is not configured"))
        }

        Timber.i("Starting SelfHostedBackupWorker for provider ${config.provider.displayName} to ${config.serverUrl}")

        try {
            val client = SelfHostedClientFactory.create(config)

            // 1. Verify connection
            val pingResult = client.testConnection()
            if (!pingResult.success) {
                Timber.e("Self-hosted connection test failed: ${pingResult.errorMessage}")
                return@withContext Result.retry()
            }

            // 2. Resolve already backed up IDs in O(1) time
            val alreadyBackedUpIds: Set<Long> = try {
                backupBox.query()
                    .build()
                    .property(BackupRecord_.mediaStoreId)
                    .findLongs()
                    .toHashSet()
            } catch (_: Exception) {
                emptySet()
            }

            // 3. Load device media
            val allMedia = mediaStoreRepository.loadAllMedia()
            val filteredMedia = allMedia.filter { media ->
                if (!config.backupVideos && media.isVideo) return@filter false
                true
            }

            val total = filteredMedia.size
            val pendingMedia = filteredMedia.filter { it.id !in alreadyBackedUpIds }
            val alreadyBackedUpCount = total - pendingMedia.size

            Timber.i("SelfHostedBackup: Total: $total, Already backed up: $alreadyBackedUpCount, Pending: ${pendingMedia.size}")

            if (pendingMedia.isEmpty()) {
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to 100,
                        KEY_TOTAL to total,
                        KEY_BACKED_UP_COUNT to total
                    )
                )
                return@withContext Result.success(workDataOf(KEY_BACKED_UP_COUNT to total))
            }

            var newlyUploadedCount = 0

            for ((idx, media) in pendingMedia.withIndex()) {
                if (isStopped) {
                    Timber.i("SelfHostedBackupWorker stopped by system. Checkpointed $newlyUploadedCount items saved.")
                    return@withContext Result.retry()
                }

                val uri = if (media.uri.isNotEmpty()) Uri.parse(media.uri) else {
                    if (media.isVideo) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, media.id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, media.id)
                    }
                }

                val currentProcessed = alreadyBackedUpCount + idx + 1
                val progressPercent = (currentProcessed * 100 / total).coerceIn(0, 100)

                setProgress(
                    workDataOf(
                        KEY_PROGRESS to progressPercent,
                        KEY_TOTAL to total,
                        KEY_BACKED_UP_COUNT to (alreadyBackedUpCount + newlyUploadedCount),
                        KEY_CURRENT_FILE to media.name
                    )
                )

                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val length = try {
                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                        } catch (_: Exception) { 0L }

                        val uploadResult = client.uploadMedia(media, stream, length) { written, _ ->
                            // Sub-file streaming progress if needed
                        }

                        stream.close()

                        if (uploadResult.success) {
                            val record = BackupRecord(
                                mediaStoreId = media.id,
                                remoteAssetId = uploadResult.remoteAssetId,
                                providerName = config.provider.name,
                                backedUpAt = System.currentTimeMillis(),
                                fileSize = length,
                                isSuccess = true
                            )
                            backupBox.put(record)
                            newlyUploadedCount++
                        } else {
                            Timber.w("Failed to upload media ${media.id} (${media.name}): ${uploadResult.errorMessage}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error reading/uploading media ${media.id}")
                }
            }

            val finalCount = alreadyBackedUpCount + newlyUploadedCount
            Timber.i("✓ SelfHostedBackupWorker complete. Total backed up: $finalCount")
            Result.success(
                workDataOf(
                    KEY_PROGRESS to 100,
                    KEY_TOTAL to total,
                    KEY_BACKED_UP_COUNT to finalCount
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "SelfHostedBackupWorker encountered an unexpected error")
            Result.retry()
        }
    }
}
