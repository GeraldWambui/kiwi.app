package co.juneai.june.service

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.juneai.june.data.local.daos.FaceClusterDao
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.ml.tagging.PrimaryObjectDetector
import co.juneai.june.ml.tagging.ZeroShotTagger
import co.juneai.june.ml.clip.ClipEmbedder
import co.juneai.june.ml.face.FaceDetectorAndClusterer
import co.juneai.june.ml.nsfw.NsfwClassifier
import co.juneai.june.ml.ocr.OcrScanner
import co.juneai.june.ml.quality.AestheticsEvaluator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream

@HiltWorker
class IndexImagesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val mediaStoreRepository: MediaStoreRepository,
    private val clipEmbedder: ClipEmbedder,
    private val nsfwClassifier: NsfwClassifier,
    private val ocrScanner: OcrScanner,
    private val faceDetectorAndClusterer: FaceDetectorAndClusterer,
    private val faceClusterDao: FaceClusterDao,
    private val aestheticsEvaluator: AestheticsEvaluator,
    private val zeroShotTagger: ZeroShotTagger,
    private val primaryObjectDetector: PrimaryObjectDetector,
    private val embeddingBox: Box<ImageEmbedding>
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
        const val KEY_INDEXED_COUNT = "indexed_count"
        const val KEY_FORCE_REINDEX = "force_reindex"
        const val KEY_IGNORE_CONSTRAINTS = "ignore_constraints"
        const val WORK_NAME = "index_images_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val forceReindex = inputData.getBoolean(KEY_FORCE_REINDEX, false)
        val ignoreConstraints = inputData.getBoolean(KEY_IGNORE_CONSTRAINTS, false)
        Timber.d("Starting IndexImagesWorker (forceReindex=$forceReindex, ignoreConstraints=$ignoreConstraints)")

        try {
            // Check battery threshold (Charging OR >= 60%), unless forceReindex or ignoreConstraints is set
            val batteryState = co.juneai.june.utils.BatteryStatusManager.getBatteryState(context)
            if (!batteryState.isEligibleForIndexing && !forceReindex && !ignoreConstraints) {
                Timber.w("IndexImagesWorker deferred: Battery is at ${batteryState.batteryPercent}% and not charging. Plug in charger or charge above 60% to index.")
                return@withContext Result.retry()
            }

            nsfwClassifier.prepare()

            val mediaList = mediaStoreRepository.loadAllMedia()
            if (mediaList.isEmpty()) {
                setProgress(workDataOf(KEY_PROGRESS to 100, KEY_TOTAL to 0, KEY_INDEXED_COUNT to 0))
                return@withContext Result.success()
            }

            val total = mediaList.size

            // Fast O(1) indexed ID lookup without instantiating entity objects
            val alreadyIndexedIds: Set<Long> = if (forceReindex) {
                emptySet()
            } else {
                try {
                    embeddingBox.query(ImageEmbedding_.embedding.notNull())
                        .build()
                        .property(ImageEmbedding_.mediaStoreId)
                        .findLongs()
                        .toHashSet()
                } catch (_: Exception) {
                    emptySet()
                }
            }

            val pendingMedia = if (forceReindex) mediaList else mediaList.filter { it.id !in alreadyIndexedIds }
            val alreadyIndexedCount = total - pendingMedia.size

            Timber.i("Total media: $total, Already indexed: $alreadyIndexedCount, Pending: ${pendingMedia.size}")

            if (pendingMedia.isEmpty()) {
                setProgress(workDataOf(KEY_PROGRESS to 100, KEY_TOTAL to total, KEY_INDEXED_COUNT to total))
                return@withContext Result.success(workDataOf(KEY_INDEXED_COUNT to total))
            }

            var newlyIndexedCount = 0
            val batchEntities = mutableListOf<ImageEmbedding>()
            val BATCH_SIZE = 15

            for ((idx, media) in pendingMedia.withIndex()) {
                // Graceful cancellation & instant checkpoint flush
                if (isStopped) {
                    if (batchEntities.isNotEmpty()) {
                        embeddingBox.put(batchEntities)
                        batchEntities.clear()
                    }
                    Timber.i("IndexImagesWorker paused gracefully. Checkpointed $newlyIndexedCount items saved.")
                    return@withContext Result.retry()
                }

                val uri = if (media.uri.isNotEmpty()) Uri.parse(media.uri) else {
                    if (media.isVideo) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, media.id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, media.id)
                    }
                }

                val bitmap = loadBitmapForMedia(uri, media.isVideo)
                if (bitmap != null) {
                    try {
                        // 1. CLIP visual embedding
                        val visualEmbedding = clipEmbedder.embedImage(bitmap)
                        val nsfwResult = nsfwClassifier.classifyEmbedding(visualEmbedding)

                        // 2. ML Kit OCR text recognition & CLIP text embedding
                        val ocrResult = ocrScanner.scanAndStore(media.id, bitmap)

                        // 3. ML Kit Face Detection + MobileFaceNet 128-d embedding & clustering
                        val faceResults = faceDetectorAndClusterer.processFaces(media.id, bitmap)
                        if (faceResults.isNotEmpty()) {
                            try {
                                faceClusterDao.deleteByMediaId(media.id)
                                faceClusterDao.insertAll(faceResults.map { it.entity })
                            } catch (fe: Exception) {
                                Timber.w(fe, "Failed to persist face clusters for media ${media.id}")
                            }
                        }
                        val maxSmile = faceResults.maxOfOrNull { it.entity.smileProbability } ?: 0f

                        // 4. Aesthetics & Sharpness (Laplacian variance) evaluation
                        val quality = aestheticsEvaluator.evaluate(
                            bitmap = bitmap,
                            smileProbability = maxSmile,
                            hasFaces = faceResults.isNotEmpty()
                        )

                        // 5. Zero-Shot Tagging & Primary Object Detection
                        val tags = zeroShotTagger.generateTags(visualEmbedding) { text ->
                            try { clipEmbedder.embedText(text) } catch (_: Exception) { FloatArray(ClipEmbedder.DIM) }
                        }
                        val tagScores = tags.associateWith { 0.85f }
                        val (primaryObj, _) = primaryObjectDetector.detectPrimaryObject(
                            imageEmbedding = visualEmbedding,
                            detectedObjects = tagScores,
                            faceCount = faceResults.size,
                            hasOCR = ocrResult.hasSignificantText,
                            ocrDominance = if (ocrResult.hasSignificantText) 0.85f else 0f,
                            textEncoder = { text ->
                                try { clipEmbedder.embedText(text) } catch (_: Exception) { FloatArray(ClipEmbedder.DIM) }
                            }
                        )

                        val existing = embeddingBox.query(ImageEmbedding_.mediaStoreId.equal(media.id)).build().findFirst()
                        val entity = existing ?: ImageEmbedding(
                            mediaStoreId = media.id,
                            uri = uri.toString(),
                            displayName = media.name
                        )

                        entity.embedding = visualEmbedding
                        entity.ocrEmbedding = ocrResult.ocrTextEmbedding
                        entity.indexedAt = System.currentTimeMillis()
                        entity.isNsfw = nsfwResult.isNsfw
                        entity.nsfwScore = nsfwResult.score
                        entity.sharpnessScore = quality.sharpnessScore
                        entity.isBlurry = quality.isBlurry
                        entity.aestheticScore = quality.aestheticScore
                        entity.faceCount = faceResults.size
                        entity.hasSignificantText = ocrResult.hasSignificantText
                        entity.primaryObject = primaryObj ?: (tags.firstOrNull() ?: "")

                        batchEntities.add(entity)
                        newlyIndexedCount++

                        // Flush batch atomically
                        if (batchEntities.size >= BATCH_SIZE) {
                            embeddingBox.put(batchEntities)
                            batchEntities.clear()
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to compute enriched indexing for media ${media.id}")
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    // Corrupted or unreadable image - mark timestamp to avoid crash loop on restart
                    try {
                        val placeholder = ImageEmbedding(
                            mediaStoreId = media.id,
                            uri = uri.toString(),
                            displayName = media.name,
                            indexedAt = System.currentTimeMillis()
                        )
                        batchEntities.add(placeholder)
                    } catch (_: Exception) {}
                }

                val currentTotalProcessed = alreadyIndexedCount + (idx + 1)
                val progressPercent = (currentTotalProcessed * 100 / total).coerceIn(0, 100)
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to progressPercent,
                        KEY_TOTAL to total,
                        KEY_INDEXED_COUNT to (alreadyIndexedCount + newlyIndexedCount)
                    )
                )
            }

            // Flush final batch
            if (batchEntities.isNotEmpty()) {
                embeddingBox.put(batchEntities)
                batchEntities.clear()
            }

            Timber.i("✓ IndexImagesWorker finished successfully. Total indexed: ${alreadyIndexedCount + newlyIndexedCount}")
            Result.success(
                workDataOf(
                    KEY_PROGRESS to 100,
                    KEY_TOTAL to total,
                    KEY_INDEXED_COUNT to (alreadyIndexedCount + newlyIndexedCount)
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "IndexImagesWorker encountered an error")
            Result.retry()
        }
    }

    private fun loadBitmapForMedia(uri: Uri, isVideo: Boolean): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(384, 384), null)
            } else {
                var stream: InputStream? = null
                try {
                    stream = context.contentResolver.openInputStream(uri)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeStream(stream, null, options)
                } finally {
                    stream?.close()
                }
            }
        } catch (e: Exception) {
            Timber.w("Failed to load thumbnail for $uri: ${e.message}")
            null
        }
    }
}
