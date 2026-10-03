package co.juneai.june.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.juneai.june.data.local.daos.VideoAudioDao
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import co.juneai.june.data.local.entities.VideoAudioEntity
import co.juneai.june.data.local.entities.VideoFrameEmbedding
import co.juneai.june.data.local.entities.VideoFrameEmbedding_
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.ml.audio.AudioTrackExtractor
import co.juneai.june.ml.audio.YamnetAudioClassifier
import co.juneai.june.ml.audio.WhisperSpeechTranscriber
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
    private val audioTrackExtractor: AudioTrackExtractor,
    private val yamnetAudioClassifier: YamnetAudioClassifier,
    private val whisperSpeechTranscriber: WhisperSpeechTranscriber,
    private val videoAudioDao: VideoAudioDao,
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
            val indexedVisualVideoIds: Set<Long> = try {
                videoFrameBox.query()
                    .build()
                    .property(VideoFrameEmbedding_.mediaStoreId)
                    .findLongs()
                    .toHashSet()
            } catch (_: Exception) {
                emptySet()
            }

            val indexedAudioVideoIds: Set<Long> = try {
                videoAudioDao.getIndexedVideoIds().toHashSet()
            } catch (_: Exception) {
                emptySet()
            }

            val unindexedVideos = videos.filter { it.id !in indexedVisualVideoIds || it.id !in indexedAudioVideoIds }
            val fullyIndexedCount = videos.count { it.id in indexedVisualVideoIds && it.id in indexedAudioVideoIds }

            Timber.i("Videos total: $total, Fully indexed: $fullyIndexedCount, Pending: ${unindexedVideos.size}")

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

                // 1. Visual Keyframe Extraction & Embedding
                if (video.id !in indexedVisualVideoIds) {
                    val result = videoKeyframeExtractor.processVideo(video.id, video.uri)
                    if (result != null) {
                        // Insert individual temporal frame embeddings
                        val frameEntities = result.keyframes.map { kf ->
                            VideoFrameEmbedding(
                                mediaStoreId = video.id,
                                timestampMs = kf.timestampMs,
                                frameIndex = kf.frameIndex,
                                embedding = kf.embedding
                            )
                        }
                        videoFrameBox.put(frameEntities)

                        // Save/update the video-level summary embedding in ImageEmbedding Box
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
                }

                // 2. Audio Extraction, Acoustic Classification (YAMNet) & Speech Transcription (Whisper)
                if (video.id !in indexedAudioVideoIds) {
                    try {
                        val extractedAudio = audioTrackExtractor.extractAudio(video.uri)
                        if (extractedAudio != null && extractedAudio.samples.isNotEmpty()) {
                            val audioEntities = mutableListOf<VideoAudioEntity>()

                            // 2a. Acoustic Event Detection (music, laughter, cheering, pets, vehicles, etc.)
                            val classification = yamnetAudioClassifier.classifyAudio(extractedAudio)
                            for (event in classification.events) {
                                audioEntities.add(
                                    VideoAudioEntity(
                                        mediaStoreId = video.id,
                                        timestampMs = event.startMs,
                                        durationMs = (event.endMs - event.startMs).coerceAtLeast(0L),
                                        audioEvents = event.label,
                                        confidence = event.confidence
                                    )
                                )
                            }

                            // 2b. Automatic Speech Recognition (transcribing spoken dialogue/words)
                            val speechSegments = whisperSpeechTranscriber.transcribe(extractedAudio.samples)
                            for (segment in speechSegments) {
                                audioEntities.add(
                                    VideoAudioEntity(
                                        mediaStoreId = video.id,
                                        timestampMs = segment.startMs,
                                        durationMs = (segment.endMs - segment.startMs).coerceAtLeast(0L),
                                        transcript = segment.text,
                                        confidence = segment.confidence
                                    )
                                )
                            }

                            if (audioEntities.isNotEmpty()) {
                                videoAudioDao.deleteByMediaId(video.id)
                                videoAudioDao.insertAll(audioEntities)
                                Timber.i("✓ Indexed ${audioEntities.size} audio/speech moments for video ${video.id} (${video.name})")
                            } else {
                                // Insert placeholder to indicate audio track was scanned and silent/empty
                                videoAudioDao.insertAll(
                                    listOf(
                                        VideoAudioEntity(
                                            mediaStoreId = video.id,
                                            timestampMs = 0L,
                                            audioEvents = "Silent",
                                            confidence = 1.0f
                                        )
                                    )
                                )
                            }
                        } else {
                            // Video has no audio track, mark as indexed
                            videoAudioDao.insertAll(
                                listOf(
                                    VideoAudioEntity(
                                        mediaStoreId = video.id,
                                        timestampMs = 0L,
                                        audioEvents = "No Audio Track",
                                        confidence = 1.0f
                                    )
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to extract/index audio for video ${video.id} (${video.name})")
                    }
                }

                newlyIndexedCount++

                val currentProcessed = fullyIndexedCount + newlyIndexedCount
                val progress = (currentProcessed * 100 / total).coerceIn(0, 100)
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to progress,
                        KEY_TOTAL to total,
                        KEY_INDEXED_COUNT to (fullyIndexedCount + newlyIndexedCount)
                    )
                )
            }

            Timber.i("✓ IndexVideosWorker complete. Total indexed: ${fullyIndexedCount + newlyIndexedCount}")
            Result.success(workDataOf(KEY_INDEXED_COUNT to (fullyIndexedCount + newlyIndexedCount)))
        } catch (e: Exception) {
            Timber.e(e, "Error in IndexVideosWorker")
            Result.retry()
        }
    }
}
