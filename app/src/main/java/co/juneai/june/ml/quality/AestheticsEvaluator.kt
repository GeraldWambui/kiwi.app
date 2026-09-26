package co.juneai.june.ml.quality

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

data class PhotoQualityScore(
    val sharpnessScore: Float, // Higher is sharper (> 150 = crisp, < 60 = blurry)
    val isBlurry: Boolean,
    val aestheticScore: Float  // 0.0 to 1.0 overall score for ranking best shots
)

@Singleton
class AestheticsEvaluator @Inject constructor() {

    companion object {
        private const val BLUR_THRESHOLD = 75f
        private const val SAMPLE_SIZE = 128 // Downsample for fast blur calculation
    }

    /**
     * Evaluates sharpness via discrete Laplacian variance and computes overall aesthetic score.
     */
    fun evaluate(
        bitmap: Bitmap,
        smileProbability: Float = 0f,
        hasFaces: Boolean = false
    ): PhotoQualityScore {
        val sharpness = computeLaplacianVariance(bitmap)
        val isBlurry = sharpness < BLUR_THRESHOLD

        // Base score normalized from sharpness (0.0 to 0.6)
        val sharpnessFactor = (sharpness / 400f).coerceIn(0f, 0.6f)

        // Facial expression bonus (0.0 to 0.25)
        val faceBonus = if (hasFaces) {
            0.1f + (smileProbability * 0.15f)
        } else {
            0.05f
        }

        // Aspect ratio harmony (0.0 to 0.15)
        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val aspectBonus = if (aspect in 0.6f..1.8f) 0.15f else 0.05f

        val totalAesthetic = (sharpnessFactor + faceBonus + aspectBonus).coerceIn(0f, 1f)

        return PhotoQualityScore(
            sharpnessScore = sharpness,
            isBlurry = isBlurry,
            aestheticScore = totalAesthetic
        )
    }

    private fun computeLaplacianVariance(bitmap: Bitmap): Float {
        val width = min(bitmap.width, SAMPLE_SIZE)
        val height = min(bitmap.height, SAMPLE_SIZE)

        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, false)
        } else {
            bitmap
        }

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        if (scaled != bitmap) {
            scaled.recycle()
        }

        // Convert to grayscale 2D array
        val gray = FloatArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Apply 3x3 Laplacian filter:
        // [ 0,  1,  0 ]
        // [ 1, -4,  1 ]
        // [ 0,  1,  0 ]
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            val yOffset = y * width
            for (x in 1 until width - 1) {
                val center = gray[yOffset + x]
                val top = gray[yOffset - width + x]
                val bottom = gray[yOffset + width + x]
                val left = gray[yOffset + x - 1]
                val right = gray[yOffset + x + 1]

                val laplacian = top + bottom + left + right - 4f * center
                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0f

        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)

        return variance.toFloat().coerceAtLeast(0f)
    }
}
