package co.juneai.june.data.repository

import android.graphics.Bitmap
import android.util.Log
import co.juneai.june.data.model.ColorAnalysis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class ColorAnalysisProcessor(private val dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    suspend fun analyzeColors(bitmap: Bitmap, mediaId: Long): Result<ColorAnalysis> {
        return withContext(dispatcher) {
            try {
                val dominantColor = extractDominantColor(bitmap)
                val palette = extractColorPalette(bitmap)

                Result.success(
                    ColorAnalysis(
                        dominantColor = dominantColor,
                        colorPalette = palette,
                        mediaId = mediaId
                    )
                )
            } catch (e: Exception) {
                Log.e("ColorAnalysis", "Error analyzing colors", e)
                Result.failure(e)
            }
        }
    }

    private fun extractDominantColor(bitmap: Bitmap): Int {
        val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, true)
        val colors = mutableMapOf<Int, Int>()

        for (x in 0 until scaled.width) {
            for (y in 0 until scaled.height) {
                val color = scaled.getPixel(x, y)
                colors[color] = (colors[color] ?: 0) + 1
            }
        }

        return colors.maxByOrNull { it.value }?.key ?: android.graphics.Color.GRAY
    }

    private fun extractColorPalette(bitmap: Bitmap): List<Int> {
        val scaled = Bitmap.createScaledBitmap(bitmap, 50, 50, true)
        val colors = mutableMapOf<Int, Int>()

        for (x in 0 until scaled.width) {
            for (y in 0 until scaled.height) {
                val color = scaled.getPixel(x, y)
                colors[color] = (colors[color] ?: 0) + 1
            }
        }

        return colors.entries.sortedByDescending { it.value }.take(5).map { it.key }
    }
}