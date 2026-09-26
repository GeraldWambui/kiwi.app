package co.juneai.june.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import co.juneai.june.data.local.entities.VideoFrameEmbedding
import co.juneai.june.data.local.entities.VideoFrameEmbedding_
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.ml.video.VideoKeyframeExtractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class IndexVideosWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val mediaStoreRepository: MediaStoreRepository,
    private val videoKeyframeExtractor: VideoKeyframeExtractor,
    private val videoFrameBox: Box<VideoFrameEmbedding>,
    private val embeddingBox: Box<ImageEmbedding>
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
        const val KEY_INDEXED_COUNT = "indexed_count"
        const val WORK_NAME = "index_videos_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.d("Starting IndexVideosWorker")
        try {
            val batteryState = co.juneai.june.utils.BatteryStatusManager.getBatteryState(applicationContext)
            if (!batteryState.isEligibleForIndexing) {
                Timber.w("IndexVideosWorker deferred: Battery is at ${batteryState.batteryPercent}% and not charging. Plug in charger or charge above 60% to index.")
                return@withContext Result.retry()
            }

            val allMedia = mediaStoreRepository.loadAllMedia()
            val videos = allMedia.filter { it.isVideo }

            if (videos.isEmpty()) {
                Timber.d("No videos found on device")
                return@withContext Result.success()
            }

            val total = videos.size

            // Fast primitive LongArray lookup without loading full entities into memory
            val indexedVideoIds: Set<Long> = try {
                videoFrameBox.query()
                    .build()
                    .property(VideoFrameEmbedding_.mediaStoreId)
                    .findLongs()
                    .toHashSet()
            } catch (_: Exception) {
                emptySet()
            }

            val unindexedVideos = videos.filter { it.id !in indexedVideoIds }
            val alreadyIndexedCount = total - unindexedVideos.size

            Timber.i("Videos total: $total, Already indexed: $alreadyIndexedCount, Pending: ${unindexedVideos.size}")

            if (unindexedVideos.isEmpty()) {
                setProgress(workDataOf(KEY_PROGRESS to 100, KEY_TOTAL to total, KEY_INDEXED_COUNT to total))
                return@withContext Result.success(workDataOf(KEY_INDEXED_COUNT to total))
            }

            var newlyIndexedCount = 0

            for ((idx, video) in unindexedVideos.withIndex()) {
                if (isStopped) {
                    Timber.i("IndexVideosWorker stopped by system. Checkpointed $newlyIndexedCount items saved.")
                    return@withContext Result.retry()
                }

                val result = videoKeyframeExtractor.processVideo(video.id, video.uri)
                if (result != null) {
                    // 1. Insert individual temporal frame embeddings
                    val frameEntities = result.keyframes.map { kf ->
                        VideoFrameEmbedding(
                            mediaStoreId = video.id,
                            timestampMs = kf.timestampMs,
                            frameIndex = kf.frameIndex,
                            embedding = kf.embedding
                        )
                    }
                    videoFrameBox.put(frameEntities)

                    // 2. Save/update the video-level summary embedding in ImageEmbedding Box
                    var existingEmbedding = embeddingBox.query(ImageEmbedding_.mediaStoreId.equal(video.id))
                        .build()
                        .findFirst()

                    if (existingEmbedding == null) {
                        existingEmbedding = ImageEmbedding(
                            mediaStoreId = video.id,
                            uri = video.uri,
                            displayName = video.name,
                            embedding = result.meanPooledEmbedding,
                            indexedAt = System.currentTimeMillis()
                        )
                    } else {
                        existingEmbedding.embedding = result.meanPooledEmbedding
                        existingEmbedding.indexedAt = System.currentTimeMillis()
                    }
                    embeddingBox.put(existingEmbedding)

                    newlyIndexedCount++
                } else {
                    // Mark as processed placeholder so corrupted video does not freeze worker
                    try {
                        val placeholder = ImageEmbedding(
                            mediaStoreId = video.id,
                            uri = video.uri,
                            displayName = video.name,
                            indexedAt = System.currentTimeMillis()
                        )
                        embeddingBox.put(placeholder)
                    } catch (_: Exception) {}
                }

                val currentProcessed = alreadyIndexedCount + (idx + 1)
                val progress = (currentProcessed * 100 / total).coerceIn(0, 100)
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to progress,
                        KEY_TOTAL to total,
                        KEY_INDEXED_COUNT to (alreadyIndexedCount + newlyIndexedCount)
                    )
                )
            }

            Timber.i("✓ IndexVideosWorker complete. Total indexed: ${alreadyIndexedCount + newlyIndexedCount}")
            Result.success(workDataOf(KEY_INDEXED_COUNT to (alreadyIndexedCount + newlyIndexedCount)))
        } catch (e: Exception) {
            Timber.e(e, "Error in IndexVideosWorker")
            Result.retry()
        }
    }
}
