package co.juneai.june.data.search

import android.net.Uri
import co.juneai.june.data.local.daos.ImageEmbeddingDao
import co.juneai.june.data.local.entities.ImageEmbeddingEntity
import co.juneai.june.ml.MobileCLIPPipeline
import co.juneai.june.data.model.ImageData
import co.juneai.june.data.model.SearchResult
import co.juneai.june.utils.toFloatArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.sqrt

class TwoStageSearchEngine @Inject constructor(
    private val dao: ImageEmbeddingDao
) {
    
    /**
     * Two-stage search: Fast filtering + Precise ranking
     * Returns <200ms for 10k+ images
     */
    suspend fun search(
        queryEmbedding512: FloatArray,
        queryEmbedding128: FloatArray,
        limit: Int = 50
    ): List<SearchResult> {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            // Get all indexed images
            val allImages = dao.getAllActiveEmbeddings()
            if (allImages.isEmpty()) {
                Timber.w("No indexed images to search")
                return@withContext emptyList()
            }
            
            // STAGE 1: Fast filtering with 128D embeddings
            val stage1Start = System.currentTimeMillis()
            val candidates = allImages
                .mapNotNull { entity ->
                    val embedding128 = entity.embedding128.toFloatArray()
                    val score = cosineSimilarity(queryEmbedding128, embedding128)
                    Pair(entity, score)
                }
                .sortedByDescending { it.second }
                .take(200)
            
            val stage1Time = System.currentTimeMillis() - stage1Start
            Timber.d("Stage 1: ${candidates.size} candidates in ${stage1Time}ms")
            
            // STAGE 2: Precise ranking with 512D embeddings
            val stage2Start = System.currentTimeMillis()
            val results = candidates
                .map { (entity, _) ->
                    val embedding512 = entity.embedding512.toFloatArray()
                    val score = cosineSimilarity(queryEmbedding512, embedding512)
                    
                    SearchResult(
                        image = entity.toImageData(),
                        similarity = score,
                        explanation = generateExplanation(entity),
                        matchedTags = entity.semanticTags.split(",").filter { it.isNotEmpty() }.take(3)
                    )
                }
                .sortedByDescending { it.similarity }
                .take(limit)
            
            val stage2Time = System.currentTimeMillis() - stage2Start
            val totalTime = System.currentTimeMillis() - startTime
            
            Timber.i("✓ Search: ${results.size} results in ${totalTime}ms (S1: ${stage1Time}ms, S2: ${stage2Time}ms)")
            
            results
        }
    }

    suspend fun searchWithML(
        query: String,
        mlPipeline: MobileCLIPPipeline,
        limit: Int = 50
    ): List<SearchResult> {
        return withContext(Dispatchers.Default) {
            try {
                // Encode query
                val queryEmbedding512 = mlPipeline.encodeText(query)
                val queryEmbedding128 = mlPipeline.compressEmbedding(queryEmbedding512)

                // Run two-stage search
                search(queryEmbedding512, queryEmbedding128, limit)
            } catch (e: Exception) {
                Timber.e(e, "ML search failed")
                emptyList()
            }
        }
    }
    
    private fun generateExplanation(entity: ImageEmbeddingEntity): String {
        return if (entity.primaryObject != null && entity.objectConfidence > 0.7f) {
            "Primary object: ${entity.primaryObject}"
        } else {
            val tags = entity.semanticTags.split(",").filter { it.isNotEmpty() }.take(2)
            "Matched: ${tags.joinToString(", ")}"
        }
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        return if (normA > 0 && normB > 0) dot / (sqrt(normA) * sqrt(normB)) else 0f
    }
}

// Extension to convert Entity to ImageData
fun ImageEmbeddingEntity.toImageData(): ImageData {
    return ImageData(
        id = this.mediaStoreId,
        uri = Uri.parse(this.uri),
        filePath = this.filePath,
        fileName = this.fileName,
        folderName = this.folderName,
        mimeType = this.mimeType,
        dateAdded = this.dateAdded,
        dateModified = this.dateModified,
        dateTaken = this.dateTaken,
        width = this.width,
        height = this.height,
        fileSize = this.fileSize,
        latitude = this.latitude,
        longitude = this.longitude,
        cameraModel = this.cameraModel,
        isoValue = this.isoValue,
        exposureTime = this.exposureTime,
        focalLength = this.focalLength,
        aperture = this.aperture,
        flashFired = this.flashFired,
        embedding512 = this.embedding512,
        embedding128 = this.embedding128,
        semanticTags = this.semanticTags.split(",").filter { it.isNotEmpty() },
        primaryObject = this.primaryObject,
        objectConfidence = this.objectConfidence,
        faceCount = this.faceCount,
        ocrText = this.ocrText,
        indexed = this.indexed,
        indexedAt = this.indexedAt,
        isTrashed = this.isTrashed,
        trashedAt = this.trashedAt
    )
}