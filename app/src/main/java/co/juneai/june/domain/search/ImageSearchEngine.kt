package co.juneai.june.domain.search

import android.content.Context
import android.net.Uri
import co.juneai.june.data.model.SearchImageEntity
import co.juneai.june.data.model.SearchImageEntity_
import co.juneai.june.ml.clip.ClipEmbedder
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class ImageSearchEngine @Inject constructor(
    private val context: Context,
    private val boxStore: BoxStore,
    private val clipEngine: ClipEmbedder
) {
    private val imageBox: Box<SearchImageEntity> by lazy {
        boxStore.boxFor(SearchImageEntity::class.java)
    }

    // In-memory cache for ultra-fast repeated queries
    private val queryCache = ConcurrentHashMap<String, FloatArray>()
    
    data class SearchResult(
        val image: SearchImageEntity,
        val score: Float,
        val distance: Float
    )
    
    suspend fun searchByText(
        query: String,
        maxResults: Int = 20,
        minScore: Float = 0.5f,
        filters: SearchFilters? = null
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        // Generate or retrieve query embedding
        val standardizedQuery = query.trim().lowercase()
        val queryEmbedding = queryCache.getOrPut(standardizedQuery) {
            val encoded = clipEngine.embedText(query)
            // Memory bound the cache
            if (queryCache.size > 100) {
                val iterator = queryCache.keys.iterator()
                for (i in 0 until 10) { if (iterator.hasNext()) iterator.next(); iterator.remove() }
            }
            encoded
        }
        
        // Build query with optional filters
        val queryBuilder = imageBox.query()
        
        filters?.let {
            if (it.hasFace != null) {
                queryBuilder.equal(SearchImageEntity_.hasFace, it.hasFace)
            }
        }
        
        // Perform nearest neighbor search using ObjectBox HNSW Index
        val results = queryBuilder
            .nearestNeighbors(SearchImageEntity_.embedding, queryEmbedding, maxResults)
            .build()
            .findWithScores()
        
        // Convert to search results
        val searchResults = results.map { result ->
            SearchResult(
                image = result.get(),
                score = calculateSimilarityScore(result.score.toFloat()),
                distance = result.score.toFloat()
            )
        }.filter { it.score >= minScore }
        
        val searchTime = System.currentTimeMillis() - startTime
        Timber.d("Search completed in ${searchTime}ms, found ${searchResults.size} results")
        
        return@withContext searchResults
    }
    
    private fun calculateSimilarityScore(distance: Float): Float {
        // Convert cosine distance to similarity score (0-1)
        return (1f - distance).coerceIn(0f, 1f)
    }
    
    data class SearchFilters(
        val hasFace: Boolean? = null
    )
}
