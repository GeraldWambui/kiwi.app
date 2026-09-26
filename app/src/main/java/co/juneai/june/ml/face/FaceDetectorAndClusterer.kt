package co.juneai.june.ml.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import co.juneai.june.data.local.daos.FaceClusterDao
import co.juneai.june.data.local.entities.FaceClusterEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class DetectedFaceResult(
    val entity: FaceClusterEntity,
    val faceCountInPhoto: Int,
    val maxSmileProb: Float
)

@Singleton
class FaceDetectorAndClusterer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val faceEmbedder: FaceEmbedder,
    private val faceClusterDao: FaceClusterDao
) {

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.12f)
            .build()
        FaceDetection.getClient(options)
    }

    private val similarityThreshold = 0.72f // Cosine threshold for same person clustering

    /**
     * Detects faces in [bitmap], extracts 128-d embeddings, assigns cluster centroids, and returns results.
     */
    suspend fun processFaces(
        mediaStoreId: Long,
        bitmap: Bitmap
    ): List<DetectedFaceResult> = withContext(Dispatchers.IO) {
        val faces = detectFaces(bitmap)
        if (faces.isEmpty()) return@withContext emptyList()

        val allKnownFaces = faceClusterDao.getAllFaces()
        val clusterCentroids = buildClusterCentroids(allKnownFaces)

        val results = mutableListOf<DetectedFaceResult>()
        val maxSmileInPhoto = faces.mapNotNull { it.smilingProbability }.maxOrNull() ?: 0f

        faces.forEachIndexed { index, face ->
            val boundingBox = face.boundingBox
            val croppedFace = cropFaceBitmap(bitmap, boundingBox) ?: return@forEachIndexed

            // Pose & Quality Filtering
            val yaw = face.headEulerAngleY ?: 0f
            val pitch = face.headEulerAngleX ?: 0f
            val isExtremeAngle = abs(yaw) > 38f || abs(pitch) > 28f
            val isFrontal = !isExtremeAngle

            val embedding = try {
                faceEmbedder.embedFace(croppedFace)
            } catch (e: Exception) {
                Timber.w(e, "Failed to embed face for media $mediaStoreId")
                null
            } ?: run {
                if (croppedFace != bitmap) croppedFace.recycle()
                return@forEachIndexed
            }

            // Find best matching cluster using Centroid Prototype matching to prevent drift
            val matchedClusterId = findMatchingCentroid(embedding, clusterCentroids)
                ?: "person_${UUID.randomUUID().toString().take(8)}"

            val smileProb = face.smilingProbability ?: 0f
            val leftEye = face.leftEyeOpenProbability ?: 0f
            val rightEye = face.rightEyeOpenProbability ?: 0f

            // Cache avatar thumbnail if it's a clear frontal face
            if (isFrontal) {
                saveAvatarThumbnail(context, matchedClusterId, croppedFace)
            }

            if (croppedFace != bitmap) {
                croppedFace.recycle()
            }

            val boxString = "${boundingBox.left},${boundingBox.top},${boundingBox.right},${boundingBox.bottom}"

            val entity = FaceClusterEntity(
                clusterId = matchedClusterId,
                personName = "",
                mediaStoreId = mediaStoreId,
                faceEmbedding = embedding,
                boundingBox = boxString,
                smileProbability = smileProb,
                leftEyeOpenProb = leftEye,
                rightEyeOpenProb = rightEye,
                isPrimaryInPhoto = index == 0 && isFrontal
            )

            results.add(
                DetectedFaceResult(
                    entity = entity,
                    faceCountInPhoto = faces.size,
                    maxSmileProb = maxSmileInPhoto
                )
            )
        }

        return@withContext results
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (cont.isActive) cont.resume(faces)
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "ML Kit Face detection failed")
                    if (cont.isActive) cont.resume(emptyList())
                }
        } catch (e: Exception) {
            Timber.w(e, "Face detection exception")
            if (cont.isActive) cont.resume(emptyList())
        }
    }

    private fun cropFaceBitmap(bitmap: Bitmap, box: Rect): Bitmap? {
        return try {
            // Add a 15% margin around the face bounding box for hair/face context
            val marginX = (box.width() * 0.15f).toInt()
            val marginY = (box.height() * 0.15f).toInt()

            val left = max(0, box.left - marginX)
            val top = max(0, box.top - marginY)
            val right = min(bitmap.width, box.right + marginX)
            val bottom = min(bitmap.height, box.bottom + marginY)

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) return null

            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            Timber.w(e, "Failed to crop face bitmap")
            null
        }
    }

    /**
     * Builds running normalized centroids (mean representative vectors) for each cluster to eliminate cluster drift.
     */
    private fun buildClusterCentroids(existingFaces: List<FaceClusterEntity>): Map<String, FloatArray> {
        val clusterMap = existingFaces.groupBy { it.clusterId }
        val centroids = mutableMapOf<String, FloatArray>()

        for ((clusterId, faces) in clusterMap) {
            val validEmbeddings = faces.mapNotNull { it.faceEmbedding }
            if (validEmbeddings.isEmpty()) continue

            val dim = validEmbeddings.first().size
            val sumVec = FloatArray(dim)

            for (emb in validEmbeddings) {
                for (i in 0 until min(dim, emb.size)) {
                    sumVec[i] += emb[i]
                }
            }

            // Normalize centroid
            var normSq = 0f
            for (v in sumVec) {
                normSq += v * v
            }
            val norm = sqrt(normSq.toDouble()).toFloat()
            if (norm > 1e-6f) {
                for (i in sumVec.indices) {
                    sumVec[i] /= norm
                }
            }
            centroids[clusterId] = sumVec
        }

        return centroids
    }

    private fun findMatchingCentroid(
        targetEmbedding: FloatArray,
        clusterCentroids: Map<String, FloatArray>
    ): String? {
        var bestSimilarity = 0f
        var bestClusterId: String? = null

        for ((clusterId, centroid) in clusterCentroids) {
            val sim = cosineSimilarity(targetEmbedding, centroid)
            if (sim > bestSimilarity && sim >= similarityThreshold) {
                bestSimilarity = sim
                bestClusterId = clusterId
            }
        }

        return bestClusterId
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val len = min(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
        }
        return dot
    }

    private fun saveAvatarThumbnail(context: Context, clusterId: String, faceBitmap: Bitmap) {
        try {
            val avatarDir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
            val avatarFile = File(avatarDir, "avatar_${clusterId}.jpg")
            if (!avatarFile.exists()) {
                FileOutputStream(avatarFile).use { out ->
                    faceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to save avatar thumbnail for cluster $clusterId")
        }
    }
}
