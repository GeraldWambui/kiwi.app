package co.juneai.june.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow

class BlurDetectionProcessor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun detectBlur(bitmap: Bitmap): Result<Float> {
        return withContext(dispatcher) {
            try {
                val gray = toLuminance(bitmap)
                val variance = laplacianVariance(gray, bitmap.width, bitmap.height)

                // Normalize variance → blur score
                val blurScore = varianceToBlurScore(variance)

                Result.success(blurScore)

            } catch (e: Exception) {
                Result.success(0f) // Fail-safe: treat as sharp
            }
        }
    }

    /**
     * Converts bitmap to grayscale luminance array (FAST).
     */
    private fun toLuminance(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val luminance = FloatArray(pixels.size)

        for (i in pixels.indices) {
            val c = pixels[i]
            luminance[i] =
                0.299f * Color.red(c) +
                        0.587f * Color.green(c) +
                        0.114f * Color.blue(c)
        }
        return luminance
    }

    /**
     * Computes Laplacian variance (true blur metric).
     */
    private fun laplacianVariance(
        gray: FloatArray,
        width: Int,
        height: Int
    ): Float {

        var sum = 0f
        var sumSq = 0f
        var count = 0

        // Laplacian kernel:
        //  0  1  0
        //  1 -4  1
        //  0  1  0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = gray[y * width + x]

                val laplacian =
                    gray[(y - 1) * width + x] +
                            gray[(y + 1) * width + x] +
                            gray[y * width + (x - 1)] +
                            gray[y * width + (x + 1)] -
                            4f * center

                val absLap = abs(laplacian)

                sum += absLap
                sumSq += absLap * absLap
                count++
            }
        }

        val mean = sum / count
        return (sumSq / count) - mean.pow(2)
    }

    /**
     * Maps Laplacian variance to [0–1] blur score.
     */
    private fun varianceToBlurScore(variance: Float): Float {
        // Empirically tuned values
        val sharpThreshold = 150f
        val blurryThreshold = 20f

        return when {
            variance >= sharpThreshold -> 0f
            variance <= blurryThreshold -> 1f
            else -> {
                val normalized =
                    (sharpThreshold - variance) /
                            (sharpThreshold - blurryThreshold)
                normalized.coerceIn(0f, 1f)
            }
        }
    }
}
