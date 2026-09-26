package co.juneai.june.ml.portrait

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import co.juneai.june.ml.segmentation.SubjectSegmenter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PortraitLightingStyle(val displayName: String) {
    NATURAL_BOKEH("Natural Bokeh"),
    STAGE_MONO("Stage Mono (B&W)"),
    STUDIO_SPOTLIGHT("Studio Spotlight"),
    WARM_SUNSET("Golden Hour")
}

@Singleton
class PortraitBokehRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subjectSegmenter: SubjectSegmenter
) {

    /**
     * Renders a studio-quality Portrait photo with variable aperture bokeh blur
     * and studio lighting effects.
     *
     * @param sourceBitmap The original full-resolution photo.
     * @param blurIntensity Range from 0.0f (no blur, f/16) to 1.0f (maximum DSLR bokeh, f/1.4).
     * @param lightingStyle The studio lighting preset.
     */
    suspend fun renderPortrait(
        sourceBitmap: Bitmap,
        blurIntensity: Float,
        lightingStyle: PortraitLightingStyle = PortraitLightingStyle.NATURAL_BOKEH
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        try {
            // 1. Extract subject cutout and bounds
            val segmented = subjectSegmenter.extractSubjectCutout(sourceBitmap)
            val subjectBounds = segmented.subjectBounds

            // 2. Generate blurred background
            val backgroundBitmap = createBlurredBackground(sourceBitmap, blurIntensity, lightingStyle)

            // 3. Composite foreground subject over blurred background
            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)

            // Draw blurred background
            canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)

            // Draw sharp foreground subject
            val subjectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            }

            if (lightingStyle == PortraitLightingStyle.WARM_SUNSET) {
                // Apply golden hour tint to subject
                val warmMatrix = ColorMatrix(
                    floatArrayOf(
                        1.15f, 0f, 0f, 0f, 15f,
                        0f, 1.05f, 0f, 0f, 5f,
                        0f, 0f, 0.90f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                subjectPaint.colorFilter = ColorMatrixColorFilter(warmMatrix)
            } else if (lightingStyle == PortraitLightingStyle.STUDIO_SPOTLIGHT) {
                // Apply subtle exposure boost to subject
                val boostMatrix = ColorMatrix(
                    floatArrayOf(
                        1.08f, 0f, 0f, 0f, 10f,
                        0f, 1.08f, 0f, 0f, 10f,
                        0f, 0f, 1.08f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                subjectPaint.colorFilter = ColorMatrixColorFilter(boostMatrix)
            }

            canvas.drawBitmap(segmented.cutoutBitmap, 0f, 0f, subjectPaint)

            backgroundBitmap.recycle()
            segmented.cutoutBitmap.recycle()

            resultBitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed to render portrait bokeh")
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun createBlurredBackground(
        source: Bitmap,
        blurIntensity: Float,
        lightingStyle: PortraitLightingStyle
    ): Bitmap {
        val width = source.width
        val height = source.height

        // Downscale for fast multi-pass separable blur
        val scale = 0.25f
        val smallW = max(1, (width * scale).toInt())
        val smallH = max(1, (height * scale).toInt())
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)

        val radius = (blurIntensity * 18f).roundToInt().coerceIn(1, 25)
        val blurredSmall = fastStackBlur(small, radius)

        val blurred = Bitmap.createScaledBitmap(blurredSmall, width, height, true)
        small.recycle()
        blurredSmall.recycle()

        // Apply lighting matrix to background
        if (lightingStyle == PortraitLightingStyle.STAGE_MONO) {
            val monoMatrix = ColorMatrix().apply { setSaturation(0f) }
            val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(monoMatrix)
            }
            val monoBg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(monoBg).drawBitmap(blurred, 0f, 0f, monoPaint)
            blurred.recycle()
            return monoBg
        } else if (lightingStyle == PortraitLightingStyle.STUDIO_SPOTLIGHT) {
            // Darken background for studio spotlight effect
            val dimMatrix = ColorMatrix(
                floatArrayOf(
                    0.65f, 0f, 0f, 0f, -20f,
                    0f, 0.65f, 0f, 0f, -20f,
                    0f, 0f, 0.65f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(dimMatrix)
            }
            val dimBg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(dimBg).drawBitmap(blurred, 0f, 0f, dimPaint)
            blurred.recycle()
            return dimBg
        }

        return blurred
    }

    /**
     * Fast O(1) multi-pass Stack Blur algorithm.
     */
    private fun fastStackBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return src
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (curY in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (curI in -radius..radius) {
                p = pix[yi + min(wm, max(curI, 0))]
                sir = stack[curI + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(curI)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (curI > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (curX in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (curY == 0) {
                    vmin[curX] = min(curX + radius + 1, wm)
                }
                p = pix[yw + vmin[curX]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (curX in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (curI in -radius..radius) {
                yi = max(0, yp) + curX
                sir = stack[curI + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(curI)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (curI > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (curI < hm) {
                    yp += w
                }
            }
            yi = curX
            stackpointer = radius
            for (curY in 0 until h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (curX == 0) {
                    vmin[curY] = min(curY + r1, hm) * w
                }
                p = curX + vmin[curY]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pix, 0, w, 0, 0, w, h)
        return result
    }
}
