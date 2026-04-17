package co.juneai.june.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import co.juneai.june.data.model.FaceDetectionResult
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

class FaceDetectionProcessor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    suspend fun detectFaces(bitmap: Bitmap, mediaId: Long): Result<List<FaceDetectionResult>> {
        return withContext(dispatcher) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val faces = Tasks.await(faceDetector.process(image))

                val detectedFaces = faces.mapIndexed { index, face ->
                    FaceDetectionResult(
                        faceId = "$mediaId-$index",
                        embeddings = generateFaceEmbedding(face, bitmap),
                        boundingBox = Rect(
                            face.boundingBox.left,
                            face.boundingBox.top,
                            face.boundingBox.right,
                            face.boundingBox.bottom
                        ),
                        confidence = face.headEulerAngleY,
                        mediaId = mediaId
                    )
                }

                Result.success(detectedFaces)
            } catch (e: Exception) {
                Log.e("FaceDetection", "Error detecting faces", e)
                Result.failure(e)
            }
        }
    }

    private fun generateFaceEmbedding(face: Face, bitmap: Bitmap): FloatArray {
        // Extract face region and generate embedding
        val faceRect = face.boundingBox
        val faceBitmap = Bitmap.createBitmap(
            bitmap,
            faceRect.left.coerceAtLeast(0),
            faceRect.top.coerceAtLeast(0),
            (faceRect.right - faceRect.left).coerceAtMost(bitmap.width),
            (faceRect.bottom - faceRect.top).coerceAtMost(bitmap.height)
        )
        // Return placeholder embedding (in production, use actual face embedding model)
        return FloatArray(128) { sin(it.toFloat()) }
    }
}