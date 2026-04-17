package co.juneai.june.ml.clip

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * Preprocesses bitmaps for CLIP ViT-B/32 inference.
 *
 * Pipeline matches the OpenAI reference implementation:
 *   1. Shortest-side resize to 224 (high-quality Paint + BitmapShader)
 *   2. Center-crop to 224×224
 *   3. Per-channel normalization with CLIP mean/std
 *   4. Output: flat CHW float array (3 × 224 × 224 = 150 528 values)
 *
 * Key performance decisions:
 *  - [Bitmap.getPixels] bulk read replaces per-pixel [Bitmap.getPixel] calls (~50× faster)
 *  - INV_STD pre-computed so normalization uses multiply instead of divide
 *  - [processInto] writes directly to a caller-supplied [FloatBuffer], eliminating one
 *    FloatArray allocation per image during batch indexing
 */
class ClipImageProcessor {

    companion object {
        const val SIZE       = 224
        const val CHANNELS   = 3
        const val INPUT_SIZE = CHANNELS * SIZE * SIZE   // 150 528

        // OpenAI CLIP ViT-B/32 normalization constants
        private val MEAN    = floatArrayOf(0.48145466f,  0.4578275f,   0.40821073f)
        // Pre-compute 1/std — one multiply is cheaper than one divide in a hot loop
        private val INV_STD = floatArrayOf(
            1f / 0.26862954f,   // R  ≈ 3.7245
            1f / 0.26130258f,   // G  ≈ 3.8268
            1f / 0.27577711f,   // B  ≈ 3.6263
        )
    }

    // Reusable pixel buffer — eliminates repeated IntArray allocations in hot paths.
    // NOT thread-safe; use one ClipImageProcessor instance per thread/coroutine.
    private val pixelBuf = IntArray(SIZE * SIZE)

    /**
     * Preprocesses [bitmap] and writes the result directly into [dst] starting
     * at its current position.  Position is advanced by [INPUT_SIZE] after return.
     *
     * [dst] must have at least [INPUT_SIZE] floats of remaining capacity.
     * This path has zero FloatArray allocation overhead.
     */
    fun processInto(bitmap: Bitmap, dst: FloatBuffer) {
        val cropped   = centerCrop(bitmap)
        val basePos   = dst.position()
        val planeSize = SIZE * SIZE

        cropped.getPixels(pixelBuf, 0, SIZE, 0, 0, SIZE, SIZE)
        if (cropped !== bitmap) cropped.recycle()

        // Write CHW layout: all R values, then all G, then all B
        for (i in 0 until planeSize) {
            val p = pixelBuf[i]
            dst.put(basePos + i,              (((p shr 16) and 0xFF) * (1f / 255f) - MEAN[0]) * INV_STD[0])
            dst.put(basePos + planeSize + i,  (((p shr  8) and 0xFF) * (1f / 255f) - MEAN[1]) * INV_STD[1])
            dst.put(basePos + 2 * planeSize + i, ((p and 0xFF)       * (1f / 255f) - MEAN[2]) * INV_STD[2])
        }
        dst.position(basePos + INPUT_SIZE)
    }

    /**
     * Convenience wrapper — allocates a new [FloatArray].
     * Prefer [processInto] in batch/hot paths.
     */
    fun processFlat(bitmap: Bitmap): FloatArray {
        val arr = FloatArray(INPUT_SIZE)
        processInto(bitmap, FloatBuffer.wrap(arr))
        return arr
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Scales [src] so its shortest side == [SIZE], then center-crops to [SIZE]×[SIZE].
     *
     * Uses Paint + BitmapShader instead of [Bitmap.createScaledBitmap] for
     * better interpolation quality (closer to CLIP's bicubic reference).
     *
     * Recycles any intermediate bitmaps it creates; never recycles [src].
     */
    private fun centerCrop(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w == SIZE && h == SIZE) return src

        // Step 1 — scale short side to SIZE
        val scale   = SIZE.toFloat() / min(w, h)
        val scaledW = (w * scale + 0.5f).toInt()
        val scaledH = (h * scale + 0.5f).toInt()

        val scaled = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).let { paint ->
            paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                .also { it.setLocalMatrix(Matrix().apply { setScale(scale, scale) }) }
            Canvas(scaled).drawRect(0f, 0f, scaledW.toFloat(), scaledH.toFloat(), paint)
        }

        // Step 2 — center-crop to SIZE×SIZE
        if (scaledW == SIZE && scaledH == SIZE) return scaled

        val left    = (scaledW - SIZE) / 2
        val top     = (scaledH - SIZE) / 2
        val cropped = Bitmap.createBitmap(scaled, left, top, SIZE, SIZE)
        scaled.recycle()
        return cropped
    }
}