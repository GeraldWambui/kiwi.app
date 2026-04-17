package co.juneai.june.domain.usecase

import android.util.Log
import co.juneai.june.data.local.db.GalleryDatabase
import co.juneai.june.data.local.entities.FaceClusterEntity
import co.juneai.june.data.local.entities.FaceEmbeddingEntity
import co.juneai.june.data.model.FaceClusterData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.filter
import kotlin.math.sqrt

class FaceClusteringUseCase(
    private val database: GalleryDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun clusterFaces(): Result<Map<Long, FaceClusterData>> = withContext(dispatcher) {
        try {
            val allFaces = database.faceEmbeddingDao().getAllFaces()
            if (allFaces.isEmpty()) return@withContext Result.success(emptyMap())

            val clusters = dbscanClustering(allFaces, epsilon = 0.4f, minPoints = 2)
            val mainUserClusterId = identifyMainUser(clusters)

            clusters.forEach { (clusterId, faceIds) ->
                val isMainUser = clusterId == mainUserClusterId
                val cluster = FaceClusterEntity(
                    clusterId = clusterId,
                    personName = if (isMainUser) "Me" else null,
                    centroid = calculateCentroid(faceIds, allFaces),
                    mediaCount = faceIds.size,
                    confidence = 0.8f,
                    isMainUser = isMainUser
                )
                database.faceClusterDao().insertCluster(cluster)
            }

            val result = clusters.mapValues { (clusterId, faceIds) ->
                FaceClusterData(
                    clusterId = clusterId,
                    mediaIds = faceIds.mapNotNull { faceId ->
                        allFaces.find { it.id == faceId }?.mediaId
                    },
                    confidence = 0.8f,
                    isMainUser = clusterId == mainUserClusterId
                )
            }

            Result.success(result)
        } catch (e: Exception) {
            Log.e("FaceClustering", "Error clustering faces", e)
            Result.failure(e)
        }
    }

    private fun dbscanClustering(
        faces: List<FaceEmbeddingEntity>,
        epsilon: Float,
        minPoints: Int
    ): Map<Long, List<Long>> {
        val clusters = mutableMapOf<Long, MutableList<Long>>()
        val visited = mutableSetOf<Long>()
        var clusterId = 0L

        faces.forEach { face ->
            if (!visited.contains(face.id)) {
                visited.add(face.id)
                val neighbors = findNeighbors(face, faces, epsilon)

                if (neighbors.size >= minPoints) {
                    val cluster = mutableListOf(face.id)
                    val queue = neighbors.toMutableList()

                    while (queue.isNotEmpty()) {
                        val point = queue.removeAt(0)
                        if (!visited.contains(point)) {
                            visited.add(point)
                            cluster.add(point)
                            val pointNeighbors = findNeighbors(
                                faces.find { it.id == point } ?: return@forEach,
                                faces,
                                epsilon
                            )
                            if (pointNeighbors.size >= minPoints) {
                                queue.addAll(pointNeighbors.filter { !visited.contains(it) })
                            }
                        }
                    }

                    clusters[clusterId++] = cluster
                }
            }
        }

        return clusters
    }

    private fun findNeighbors(
        face: FaceEmbeddingEntity,
        allFaces: List<FaceEmbeddingEntity>,
        epsilon: Float
    ): List<Long> {
        val faceEmbedding = byteArrayToFloatArray(face.embedding)
        return allFaces.filter { other ->
            val otherEmbedding = byteArrayToFloatArray(other.embedding)
            val distance = euclideanDistance(faceEmbedding, otherEmbedding)
            distance <= epsilon
        }.map { it.id }
    }

    private fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    private fun calculateCentroid(faceIds: List<Long>, allFaces: List<FaceEmbeddingEntity>): ByteArray {
        val embeddings = faceIds.mapNotNull { faceId ->
            allFaces.find { it.id == faceId }?.embedding?.let { byteArrayToFloatArray(it) }
        }

        val centroid = FloatArray(embeddings.firstOrNull()?.size ?: 128)
        embeddings.forEach { embedding ->
            for (i in centroid.indices) {
                centroid[i] += embedding[i]
            }
        }

        if (embeddings.isNotEmpty()) {
            for (i in centroid.indices) {
                centroid[i] /= embeddings.size
            }
        }

        return floatArrayToByteArray(centroid)
    }

    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val floatArray = FloatArray(bytes.size / 4)
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        buffer.asFloatBuffer().get(floatArray)
        return floatArray
    }

    private fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(floats.size * 4)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }

    private fun identifyMainUser(clusters: Map<Long, List<Long>>): Long {
        return clusters.maxByOrNull { it.value.size }?.key ?: -1L
    }
}