package co.juneai.june.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import co.juneai.june.data.model.ObjectDetectionResult
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ObjectDetectionProcessor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    suspend fun detectPrimaryObjects(
        bitmap: Bitmap,
        mediaId: Long
    ): Result<List<ObjectDetectionResult>> = withContext(dispatcher) {

        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val detections = Tasks.await(detector.process(image))

            val imageArea = bitmap.width * bitmap.height

            val hasPerson = detections.any {
                it.labels.any { l -> l.text.equals("Person", true) }
            }

            val results = detections.mapNotNull { detection ->
                val label = detection.labels.maxByOrNull { it.confidence }?.text
                    ?: return@mapNotNull null

                val confidence = detection.labels.maxByOrNull { it.confidence }!!.confidence
                val box = detection.boundingBox

                val areaRatio =
                    (box.width() * box.height()).toFloat() / imageArea

                val centerDistance = centerDistanceScore(
                    box, bitmap.width, bitmap.height
                )

                val priority = objectPriority(label)

                val primaryScore =
                    (areaRatio * 0.45f) +
                            (confidence * 0.35f) +
                            (centerDistance * 0.15f) +
                            (priority * 0.05f)

                val isPrimary =
                    primaryScore >= 0.45f &&
                            !(label.equals("Shoes", true) && hasPerson)

                ObjectDetectionResult(
                    label = label.lowercase(),
                    confidence = confidence,
                    boundingBox = box,
                    areaRatio = areaRatio,
                    isPrimary = isPrimary,
                    mediaId = mediaId
                )
            }

            Result.success(results)

        } catch (e: Exception) {
            Log.e("ObjectDetection", "Detection failed", e)
            Result.failure(e)
        }
    }

    private fun centerDistanceScore(
        box: Rect,
        width: Int,
        height: Int
    ): Float {
        val cx = box.centerX().toFloat() / width
        val cy = box.centerY().toFloat() / height
        val dx = kotlin.math.abs(cx - 0.5f)
        val dy = kotlin.math.abs(cy - 0.5f)
        return 1f - (dx + dy)
    }

    private fun objectPriority(label: String): Float =
        when (label.lowercase()) {
            "person" -> 1.0f
            "shoe", "shoes", "bag", "watch" -> 0.6f
            "chair", "table", "car" -> 0.5f
            else -> 0.4f
        }
}

