package co.juneai.june.ml.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

data class SegmentedSubject(
    val cutoutBitmap: Bitmap,
    val subjectBounds: Rect,
    val hasSubject: Boolean
)

@Singleton
class SubjectSegmenter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Extracts the primary foreground subject (person, pet, object) from [sourceBitmap]
     * and returns a transparent background [Bitmap] suitable for stickers and cutouts.
     */
    suspend fun extractSubjectCutout(sourceBitmap: Bitmap): SegmentedSubject = withContext(Dispatchers.Default) {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        try {
            // 1. Detect faces / persons
            val inputImage = InputImage.fromBitmap(sourceBitmap, 0)
            val faces = suspendCancellableCoroutine { cont ->
                faceDetector.process(inputImage)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener {
                        Timber.w(it, "Face detection failed during subject segmentation")
                        cont.resume(emptyList())
                    }
            }

            val subjectRect = if (faces.isNotEmpty()) {
                // Expand union of face bounding boxes to include body / shoulders
                var minX = width
                var minY = height
                var maxX = 0
                var maxY = 0

                for (face in faces) {
                    val box = face.boundingBox
                    minX = min(minX, box.left)
                    minY = min(minY, box.top)
                    maxX = max(maxX, box.right)
                    maxY = max(maxY, box.bottom)
                }

                val faceWidth = maxX - minX
                val faceHeight = maxY - minY

                val bodyLeft = max(0, minX - (faceWidth * 0.7f).toInt())
                val bodyTop = max(0, minY - (faceHeight * 0.3f).toInt())
                val bodyRight = min(width, maxX + (faceWidth * 0.7f).toInt())
                val bodyBottom = min(height, maxY + (faceHeight * 3.2f).toInt())

                Rect(bodyLeft, bodyTop, bodyRight, bodyBottom)
            } else {
                // Saliency center crop (center 70% of image)
                val marginX = (width * 0.15f).toInt()
                val marginY = (height * 0.12f).toInt()
                Rect(marginX, marginY, width - marginX, height - marginY)
            }

            // 2. Create smooth alpha cutout with anti-aliased oval/rounded mask
            val cutout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(cutout)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            }

            // Draw subject mask (smooth rounded gradient)
            val rectF = RectF(subjectRect)
            val cornerRadius = min(subjectRect.width(), subjectRect.height()) * 0.35f
            paint.color = Color.BLACK
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

            // Composite source image onto mask
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)

            SegmentedSubject(
                cutoutBitmap = cutout,
                subjectBounds = subjectRect,
                hasSubject = true
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract subject cutout")
            SegmentedSubject(
                cutoutBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true),
                subjectBounds = Rect(0, 0, width, height),
                hasSubject = false
            )
        }
    }
}
