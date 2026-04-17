package co.juneai.june.domain.usecase

import android.util.Log
import co.juneai.june.data.local.entities.MediaEmbeddingEntity
import co.juneai.june.data.model.MatchType
import co.juneai.june.data.model.SearchFilters
import co.juneai.june.data.model.SearchResultItem
import co.juneai.june.data.repository.PCACompressor
import co.juneai.june.domain.repository.EmbeddingProcessor
import co.juneai.june.domain.repository.EmbeddingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class SearchUseCases(
    private val embeddingRepository: EmbeddingRepository,
    private val embeddingProcessor: EmbeddingProcessor,
    private val pcaCompressor: PCACompressor,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun performSemanticSearch(
        query: String,
        limit: Int = 50,
        filters: SearchFilters = SearchFilters()
    ): Result<List<SearchResultItem>> = withContext(dispatcher) {
        try {
            val queryEmbedding = embeddingProcessor.getTextEmbedding(query)
            val allEmbeddings = embeddingRepository.getAllEmbeddings()

            val results = allEmbeddings.mapNotNull { embedding ->
                val similarity = cosineSimilarity(
                    queryEmbedding,
                    byteArrayToFloatArray(embedding.fullEmbedding)
                )

                val meetsFilter = applyFilters(embedding, filters)

                if (meetsFilter && similarity > 0.3f) {
                    SearchResultItem(
                        mediaId = embedding.mediaId,
                        uri = embedding.uri,
                        similarity = similarity,
                        matchType = MatchType.SEMANTIC
                    )
                } else null
            }
                .sortedByDescending { it.similarity }
                .take(limit)

            Result.success(results)
        } catch (e: Exception) {
            Log.e("SearchUseCase", "Error performing search", e)
            Result.failure(e)
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Arrays must be same size" }
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return if (normA > 0 && normB > 0) {
            dot / (sqrt(normA) * sqrt(normB))
        } else 0f
    }

    private fun applyFilters(embedding: MediaEmbeddingEntity, filters: SearchFilters): Boolean {
        if (filters.excludeNSFW && embedding.nsfwScore > 0.5f) return false
        if (filters.minWidth != null && embedding.width < filters.minWidth) return false
        if (filters.startDate != null && embedding.takenDate != null && embedding.takenDate < filters.startDate) return false
        if (filters.endDate != null && embedding.takenDate != null && embedding.takenDate > filters.endDate) return false
        return true
    }

    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val floatArray = FloatArray(bytes.size / 4)
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        buffer.asFloatBuffer().get(floatArray)
        return floatArray
    }
}