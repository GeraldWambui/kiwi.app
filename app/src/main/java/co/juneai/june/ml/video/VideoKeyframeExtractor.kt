package co.juneai.june.ml.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import co.juneai.june.ml.clip.ClipEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

data class ExtractedKeyframe(
    val timestampMs: Long,
    val frameIndex: Int,
    val embedding: FloatArray
)

data class VideoExtractionResult(
    val mediaStoreId: Long,
    val keyframes: List<ExtractedKeyframe>,
    val meanPooledEmbedding: FloatArray
)

@Singleton
class VideoKeyframeExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clipEmbedder: ClipEmbedder
) {

    /**
     * Extracts keyframes from a video file with adaptive temporal sampling,
     * generates 512-dim MobileCLIP-S2 embeddings, and computes a mean-pooled summary vector.
     */
    fun processVideo(mediaStoreId: Long, uriString: String): VideoExtractionResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: return null
            if (durationMs <= 0L) return null

            // Adaptive sampling interval based on video duration
            val stepMs = when {
                durationMs <= 15_000L -> 1_000L      // 1.0s step for short clips
                durationMs <= 120_000L -> 2_000L     // 2.0s step for medium clips
                else -> 4_000L                       // 4.0s step for longer videos
            }

            val maxKeyframes = 40
            val timestamps = mutableListOf<Long>()
            var currTime = 0L
            while (currTime < durationMs && timestamps.size < maxKeyframes) {
                timestamps.add(currTime)
                currTime += stepMs
            }

            val extractedKeyframes = mutableListOf<ExtractedKeyframe>()
            var prevLuminance: Float? = null

            for ((idx, timeMs) in timestamps.withIndex()) {
                val timeUs = timeMs * 1000L
                val frameBitmap = retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    224,
                    224
                ) ?: retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    224,
                    224
                ) ?: continue

                // Check for visual duplicate / static scene (luminance delta < 6%)
                val luminance = computeAverageLuminance(frameBitmap)
                if (prevLuminance != null && abs(luminance - prevLuminance) < 0.06f && extractedKeyframes.isNotEmpty()) {
                    frameBitmap.recycle()
                    continue
                }
                prevLuminance = luminance

                // Run neural embedding pass
                val embedding = clipEmbedder.embedImage(frameBitmap)
                frameBitmap.recycle()

                if (embedding != null && embedding.isNotEmpty()) {
                    extractedKeyframes.add(
                        ExtractedKeyframe(
                            timestampMs = timeMs,
                            frameIndex = idx,
                            embedding = embedding
                        )
                    )
                }
            }

            if (extractedKeyframes.isEmpty()) return null

            // Compute L2-normalized mean-pooled video summary vector
            val meanPooled = computeMeanPooledVector(extractedKeyframes.map { it.embedding })

            VideoExtractionResult(
                mediaStoreId = mediaStoreId,
                keyframes = extractedKeyframes,
                meanPooledEmbedding = meanPooled
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract keyframes from video: $uriString")
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun computeAverageLuminance(bitmap: Bitmap): Float {
        var sum = 0f
        val w = bitmap.width
        val h = bitmap.height
        val step = 8 // Subsample for fast luminance check
        var count = 0

        for (x in 0 until w step step) {
            for (y in 0 until h step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF) / 255.0f
                val g = (pixel shr 8 and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                sum += (0.299f * r + 0.587f * g + 0.114f * b)
                count++
            }
        }
        return if (count > 0) sum / count else 0.5f
    }

    fun computeMeanPooledVector(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(512)
        val dim = vectors.first().size
        val sum = FloatArray(dim)

        for (vec in vectors) {
            for (i in 0 until dim) {
                sum[i] += vec[i]
            }
        }

        var normSq = 0f
        for (v in sum) {
            normSq += v * v
        }
        val norm = sqrt(normSq.toDouble()).toFloat()

        return if (norm > 0f) {
            FloatArray(dim) { i -> sum[i] / norm }
        } else {
            sum
        }
    }
}
