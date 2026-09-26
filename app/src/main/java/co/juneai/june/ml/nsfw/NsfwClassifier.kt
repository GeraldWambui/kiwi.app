package co.juneai.june.ml.nsfw

import android.content.Context
import android.graphics.Bitmap
import co.juneai.june.ml.clip.ClipEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max

/**
 * On-device NSFW content classifier.
 *
 * Uses zero-shot CLIP embedding classification comparing image embeddings against
 * calibrated explicit/sensitive prompt vectors and neutral prompt vectors.
 */
@Singleton
class NsfwClassifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clipEmbedder: ClipEmbedder
) {

    data class NsfwResult(
        val isNsfw: Boolean,
        val score: Float,
        val label: String
    )

    private val explicitPrompts = listOf(
        "a photo of explicit adult nudity or sexual content",
        "pornography, explicit nude person, nsfw",
        "erotic sensitive suggestive adult content",
        "exposed intimate body parts"
    )

    private val neutralPrompts = listOf(
        "a regular everyday photo of people wearing clothes",
        "a scenic landscape, nature, plants, or mountains",
        "an everyday object, animal, vehicle, food, or street scene",
        "a document, receipt, text, or artwork"
    )

    @Volatile
    private var explicitEmbeddings: List<FloatArray>? = null

    @Volatile
    private var neutralEmbeddings: List<FloatArray>? = null

    /**
     * Pre-warms prompt embeddings on a background thread.
     */
    suspend fun prepare() = withContext(Dispatchers.Default) {
        if (explicitEmbeddings != null && neutralEmbeddings != null) return@withContext

        try {
            val explicit = explicitPrompts.map { clipEmbedder.embedText(it) }
            val neutral = neutralPrompts.map { clipEmbedder.embedText(it) }
            explicitEmbeddings = explicit
            neutralEmbeddings = neutral
        } catch (e: Exception) {
            Timber.e(e, "Failed to prepare NSFW prompt embeddings")
        }
    }

    /**
     * Classifies an image using its 512-dim CLIP embedding.
     *
     * @param embedding 512-dimensional normalized float array
     * @param threshold sensitivity threshold between 0.0 and 1.0 (default 0.55f)
     */
    fun classifyEmbedding(embedding: FloatArray, threshold: Float = 0.55f): NsfwResult {
        var explicit = explicitEmbeddings
        var neutral = neutralEmbeddings

        if (explicit == null || neutral == null) {
            try {
                explicit = explicitPrompts.map { clipEmbedder.embedText(it) }
                neutral = neutralPrompts.map { clipEmbedder.embedText(it) }
                explicitEmbeddings = explicit
                neutralEmbeddings = neutral
            } catch (e: Exception) {
                Timber.e(e, "Error computing prompt embeddings in classifyEmbedding")
                return NsfwResult(isNsfw = false, score = 0f, label = "safe")
            }
        }

        var maxExplicitSim = -1f
        for (expVec in explicit) {
            val sim = dotProduct(embedding, expVec)
            if (sim > maxExplicitSim) maxExplicitSim = sim
        }

        var maxNeutralSim = -1f
        for (neuVec in neutral) {
            val sim = dotProduct(embedding, neuVec)
            if (sim > maxNeutralSim) maxNeutralSim = sim
        }

        // Temperature-scaled softmax over maximum similarities
        val temperature = 0.07f
        val expExplicit = exp(maxExplicitSim / temperature)
        val expNeutral = exp(maxNeutralSim / temperature)
        val nsfwProbability = (expExplicit / (expExplicit + expNeutral)).coerceIn(0f, 1f)

        val isNsfw = nsfwProbability >= threshold
        return NsfwResult(
            isNsfw = isNsfw,
            score = nsfwProbability,
            label = if (isNsfw) "sensitive" else "safe"
        )
    }

    /**
     * Classifies an image [Bitmap].
     */
    suspend fun classifyBitmap(bitmap: Bitmap, threshold: Float = 0.55f): NsfwResult =
        withContext(Dispatchers.Default) {
            try {
                val embedding = clipEmbedder.embedImage(bitmap)
                classifyEmbedding(embedding, threshold)
            } catch (e: Exception) {
                Timber.e(e, "Error classifying bitmap for NSFW")
                NsfwResult(isNsfw = false, score = 0f, label = "error")
            }
        }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            sum += a[i] * b[i]
        }
        return sum
    }
}
