package co.juneai.june.data.repository.nsfw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Preprocesses images for the NudeNet ONNX model.
 * Handles resizing with letterboxing and NCHW float array conversion.
 */
object ImagePreprocessor {

    private const val MODEL_INPUT_SIZE = 320

    data class PreprocessResult(
        val inputArray: FloatArray,
        val scale: Float,
        val padX: Int,
        val padY: Int,
        val originalWidth: Int,
        val originalHeight: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PreprocessResult) return false
            return inputArray.contentEquals(other.inputArray) &&
                    scale == other.scale && padX == other.padX && padY == other.padY
        }

        override fun hashCode(): Int = inputArray.contentHashCode()
    }

    /**
     * Preprocess a bitmap into a float array suitable for ONNX inference.
     * - Resizes to 320x320 with letterboxing (gray padding)
     * - Converts to NCHW format with normalization to [0, 1]
     */
    fun preprocess(bitmap: Bitmap): PreprocessResult {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // Calculate scale to fit within MODEL_INPUT_SIZE
        val scale = minOf(
            MODEL_INPUT_SIZE.toFloat() / originalWidth,
            MODEL_INPUT_SIZE.toFloat() / originalHeight
        )

        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()

        val padX = (MODEL_INPUT_SIZE - scaledWidth) / 2
        val padY = (MODEL_INPUT_SIZE - scaledHeight) / 2

        // Create letterboxed bitmap
        val letterboxed = Bitmap.createBitmap(
            MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(letterboxed)
        canvas.drawColor(Color.rgb(114, 114, 114)) // Gray padding

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        canvas.drawBitmap(scaledBitmap, padX.toFloat(), padY.toFloat(), Paint())
        scaledBitmap.recycle()

        // Convert to NCHW float array normalized to [0, 1]
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        letterboxed.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        letterboxed.recycle()

        val floatArray = FloatArray(3 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        val channelSize = MODEL_INPUT_SIZE * MODEL_INPUT_SIZE

        for (i in pixels.indices) {
            val pixel = pixels[i]
            floatArray[i] = Color.red(pixel) / 255.0f               // R channel
            floatArray[channelSize + i] = Color.green(pixel) / 255.0f // G channel
            floatArray[2 * channelSize + i] = Color.blue(pixel) / 255.0f // B channel
        }

        return PreprocessResult(
            inputArray = floatArray,
            scale = scale,
            padX = padX,
            padY = padY,
            originalWidth = originalWidth,
            originalHeight = originalHeight
        )
    }
}
