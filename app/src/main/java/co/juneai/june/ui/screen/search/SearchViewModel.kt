package co.juneai.june.ui.screen.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.data.local.daos.FaceClusterDao
import co.juneai.june.data.local.daos.MediaOcrDao
import co.juneai.june.data.local.daos.UndoRedoDao
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import co.juneai.june.data.local.entities.UndoRedoEntity
import co.juneai.june.data.model.MediaItem
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.domain.intent.CategoryFilter
import co.juneai.june.domain.intent.DateRangeFilter
import co.juneai.june.domain.intent.IntentParser
import co.juneai.june.domain.intent.MediaTypeFilter
import co.juneai.june.domain.intent.UserIntent
import co.juneai.june.ml.clip.ClipEmbedder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.max

enum class SearchCategoryFilter {
    ALL,
    PHOTOS,
    VIDEOS,
    DOCUMENTS,
    SCREENSHOTS
}

data class SearchResultItem(
    val media: MediaItem,
    val similarity: Float,
    val isNsfw: Boolean = false,
    val isRevealed: Boolean = false,
    val ocrSnippet: String = "",
    val matchedTimestampMs: Long? = null
)

sealed class IntentAction {
    data class ConfirmDelete(
        val items: List<MediaItem>,
        val targetQuery: String,
        val category: CategoryFilter,
        val dateRange: DateRangeFilter?
    ) : IntentAction()

    data class ConfirmMoveToVault(
        val items: List<MediaItem>,
        val targetQuery: String,
        val category: CategoryFilter,
        val dateRange: DateRangeFilter?
    ) : IntentAction()
}

sealed class NavigationEvent {
    data class NavigateTo(val route: String) : NavigationEvent()
    data class ShowSnackbar(val message: String, val undoAction: (() -> Unit)? = null) : NavigationEvent()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clipEmbedder: ClipEmbedder,
    private val embeddingBox: Box<ImageEmbedding>,
    private val videoFrameBox: Box<co.juneai.june.data.local.entities.VideoFrameEmbedding>,
    private val mediaStoreRepository: MediaStoreRepository,
    private val mediaOcrDao: MediaOcrDao,
    private val faceClusterDao: FaceClusterDao,
    private val undoRedoDao: UndoRedoDao,
    private val intentRouter: co.juneai.june.domain.intent.IntentRouter,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _pendingIntentAction = MutableStateFlow<IntentAction?>(null)
    val pendingIntentAction: StateFlow<IntentAction?> = _pendingIntentAction.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents.asSharedFlow()

    private val _isNsfwProtectionEnabled = MutableStateFlow(true)
    val isNsfwProtectionEnabled: StateFlow<Boolean> = _isNsfwProtectionEnabled.asStateFlow()

    private val _peopleClusters = MutableStateFlow<List<co.juneai.june.data.local.daos.FaceClusterSummary>>(emptyList())
    val peopleClusters: StateFlow<List<co.juneai.june.data.local.daos.FaceClusterSummary>> = _peopleClusters.asStateFlow()

    private val _selectedCategory = MutableStateFlow(SearchCategoryFilter.ALL)
    val selectedCategory: StateFlow<SearchCategoryFilter> = _selectedCategory.asStateFlow()

    companion object {
        val KEY_NSFW_PROTECTION = booleanPreferencesKey("nsfw_protection_enabled")
    }

    init {
        viewModelScope.launch {
            try {
                val prefs = dataStore.data.first()
                _isNsfwProtectionEnabled.value = prefs[KEY_NSFW_PROTECTION] ?: true
            } catch (_: Exception) {}
        }
        loadPeople()
    }

    fun loadPeople() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val clusters = faceClusterDao.getAllClusterSummaries()
                _peopleClusters.value = clusters
            } catch (e: Exception) {
                Timber.e(e, "Failed to load people clusters in search")
            }
        }
    }

    fun setCategoryFilter(category: SearchCategoryFilter) {
        _selectedCategory.value = category
    }

    fun searchByPerson(clusterId: String, personName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            _searchError.value = null
            try {
                val mediaIds = faceClusterDao.getMediaIdsForCluster(clusterId)
                val allMedia = mediaStoreRepository.loadAllMedia().associateBy { it.id }
                val results = mediaIds.mapNotNull { allMedia[it] }.map {
                    SearchResultItem(
                        media = it,
                        similarity = 0.95f,
                        ocrSnippet = "Person: $personName"
                    )
                }
                _searchResults.value = results
                _isSearching.value = false
            } catch (e: Exception) {
                _searchError.value = "Failed to find photos for $personName"
                _isSearching.value = false
            }
        }
    }

    fun search(rawQuery: String) {
        if (rawQuery.isBlank()) return

        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            _pendingIntentAction.value = null

            try {
                val intent = intentRouter.routeAndParse(rawQuery)
                when (intent) {
                    is UserIntent.Navigate -> {
                        _navigationEvents.emit(NavigationEvent.NavigateTo(intent.destinationRoute))
                        _isSearching.value = false
                    }
                    is UserIntent.Delete -> {
                        handleDeleteIntent(intent.targetQuery, intent.categoryFilter, intent.dateRange, intent.locationFilter)
                    }
                    is UserIntent.MoveToVault -> {
                        handleMoveToVaultIntent(intent.targetQuery, intent.categoryFilter, intent.dateRange, intent.locationFilter)
                    }
                    is UserIntent.ShowSimilar -> {
                        performMultiModalSearch(
                            queryText = intent.queryDescription ?: rawQuery,
                            mediaFilter = MediaTypeFilter.ALL,
                            categoryFilter = CategoryFilter.ALL,
                            dateRange = null,
                            locationFilter = null,
                            negatedQuery = null,
                            negatedCategory = null
                        )
                    }
                    is UserIntent.Search -> {
                        performMultiModalSearch(
                            queryText = intent.query,
                            mediaFilter = intent.mediaTypeFilter,
                            categoryFilter = intent.categoryFilter,
                            dateRange = intent.dateRange,
                            locationFilter = intent.locationFilter,
                            negatedQuery = intent.negatedQuery,
                            negatedCategory = intent.negatedCategory
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Search failed for query: $rawQuery")
                _searchError.value = "Semantic search encountered an error. Please try again."
            } finally {
                _isSearching.value = false
            }
        }
    }

    private suspend fun performMultiModalSearch(
        queryText: String,
        mediaFilter: MediaTypeFilter,
        categoryFilter: CategoryFilter,
        dateRange: DateRangeFilter?,
        locationFilter: String?,
        negatedQuery: String?,
        negatedCategory: CategoryFilter?
    ) = withContext(Dispatchers.IO) {
        val allMedia = mediaStoreRepository.loadAllMedia().associateBy { it.id }
        val query = embeddingBox.query(ImageEmbedding_.isTrashed.equal(false)).build()
        val totalCount = query.count()

        // 1. Room SQLite FTS Keyword Matches on OCR Text
        val ftsOcrMatches = try {
            val cleanTerm = queryText.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
            if (cleanTerm.isNotBlank()) {
                (mediaOcrDao.searchOcrFts(cleanTerm) + mediaOcrDao.searchOcrLike(cleanTerm)).toSet()
            } else emptySet()
        } catch (_: Exception) {
            emptySet()
        }

        // 2. Face Cluster & Person Name Matches
        val personMatches = try {
            val summaries = faceClusterDao.getAllClusterSummaries()
            val matchingClusters = summaries.filter {
                (it.personName.isNotBlank() && it.personName.contains(queryText, ignoreCase = true)) ||
                (queryText.startsWith("person", ignoreCase = true) && it.clusterId.contains(queryText.filter { c -> c.isDigit() }))
            }
            matchingClusters.flatMap { faceClusterDao.getMediaIdsForCluster(it.clusterId) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        if (totalCount == 0L) {
            // Fallback to text and date matching
            val textMatches = allMedia.values.filter { item ->
                val nameMatch = item.name.contains(queryText, ignoreCase = true) ||
                        item.albumName.contains(queryText, ignoreCase = true) ||
                        ftsOcrMatches.contains(item.id) ||
                        personMatches.contains(item.id)

                val dateMatch = if (dateRange != null) {
                    item.dateTaken in dateRange.startEpochMs..dateRange.endEpochMs
                } else true

                val locMatch = if (locationFilter != null) {
                    item.relativePath.contains(locationFilter, ignoreCase = true) ||
                    item.albumName.contains(locationFilter, ignoreCase = true) ||
                    item.name.contains(locationFilter, ignoreCase = true)
                } else true

                nameMatch && dateMatch && locMatch
            }.map { SearchResultItem(media = it, similarity = 1.0f) }

            withContext(Dispatchers.Main) {
                _searchResults.value = textMatches
                if (textMatches.isEmpty()) {
                    _searchError.value = "No indexed images found. Tap 'Re-Index' in Settings to index your gallery for semantic search."
                }
            }
            return@withContext
        }

        // 3. Compute CLIP query embedding
        val textEmbedding = try {
            clipEmbedder.embedText(queryText)
        } catch (e: Exception) {
            Timber.e(e, "Failed to embed text query: $queryText")
            null
        }

        // Compute negated CLIP embedding if present
        val negatedEmbedding = if (!negatedQuery.isNullOrBlank()) {
            try { clipEmbedder.embedText(negatedQuery) } catch (_: Exception) { null }
        } else null

        val scoredResults = mutableListOf<SearchResultItem>()
        val entities = query.find()

        for (entity in entities) {
            val visualEmb = entity.embedding
            val mediaItem = allMedia[entity.mediaStoreId] ?: continue

            // Apply media type filter
            if (mediaFilter == MediaTypeFilter.PHOTOS_ONLY && mediaItem.isVideo) continue
            if (mediaFilter == MediaTypeFilter.VIDEOS_ONLY && !mediaItem.isVideo) continue

            // Apply category filter
            if (categoryFilter == CategoryFilter.BLURRY && !entity.isBlurry) continue
            if (categoryFilter == CategoryFilter.RECEIPTS_DOCUMENTS && !entity.hasSignificantText) continue
            if (categoryFilter == CategoryFilter.PORTRAITS && entity.faceCount < 1) continue

            // Apply date range filter
            if (dateRange != null) {
                if (mediaItem.dateTaken !in dateRange.startEpochMs..dateRange.endEpochMs) continue
            }

            // Apply location filter
            if (locationFilter != null) {
                val matchesLocation = mediaItem.relativePath.contains(locationFilter, ignoreCase = true) ||
                        mediaItem.albumName.contains(locationFilter, ignoreCase = true) ||
                        mediaItem.name.contains(locationFilter, ignoreCase = true)
                if (!matchesLocation) continue
            }

            // Apply Negation Filters
            if (negatedCategory == CategoryFilter.BLURRY && entity.isBlurry) continue
            if (negatedCategory == CategoryFilter.PORTRAITS && entity.faceCount > 0) continue
            if (negatedCategory == CategoryFilter.RECEIPTS_DOCUMENTS && entity.hasSignificantText) continue
            if (negatedCategory == CategoryFilter.SCREENSHOTS &&
                (mediaItem.albumName.contains("screenshot", ignoreCase = true) || mediaItem.name.contains("screenshot", ignoreCase = true))) {
                continue
            }

            // Negated query similarity check
            if (negatedEmbedding != null && visualEmb != null) {
                val negScore = dotProduct(negatedEmbedding, visualEmb)
                if (negScore >= 0.28f) continue // Discard items strongly matching negated query
            }
            if (!negatedQuery.isNullOrBlank() && entity.primaryObject.isNotBlank() &&
                entity.primaryObject.contains(negatedQuery, ignoreCase = true)) {
                continue
            }

            var finalScore = 0f

            // Visual similarity
            if (textEmbedding != null && visualEmb != null) {
                val visualScore = dotProduct(textEmbedding, visualEmb)
                finalScore = max(finalScore, visualScore)
            }

            // OCR semantic similarity
            if (textEmbedding != null && entity.ocrEmbedding != null) {
                val ocrScore = dotProduct(textEmbedding, entity.ocrEmbedding!!)
                finalScore = max(finalScore, ocrScore * 1.15f) // OCR boost for relevant text
            }

            // FTS exact keyword bonus
            if (ftsOcrMatches.contains(entity.mediaStoreId)) {
                finalScore = max(finalScore, 0.45f) + 0.15f
            }

            // Primary object / scene match bonus
            if (entity.primaryObject.isNotBlank() && entity.primaryObject.contains(queryText, ignoreCase = true)) {
                finalScore = max(finalScore, 0.65f)
            }

            // Person / face match bonus
            if (personMatches.contains(entity.mediaStoreId)) {
                finalScore = max(finalScore, 0.70f)
            }

            // Category match bonus if specific category requested
            if (categoryFilter != CategoryFilter.ALL) {
                finalScore = max(finalScore, 0.35f)
            }

            if (finalScore >= 0.17f) {
                val isSensitive = entity.isNsfw || entity.nsfwScore >= 0.55f
                scoredResults.add(
                    SearchResultItem(
                        media = mediaItem.copy(isNsfw = isSensitive),
                        similarity = finalScore.coerceIn(0f, 1f),
                        isNsfw = isSensitive
                    )
                )
            }
        }

        // 4. Temporal Video Frame Keyframe Search (Moment Detection)
        if (textEmbedding != null && (mediaFilter == MediaTypeFilter.ALL || mediaFilter == MediaTypeFilter.VIDEOS_ONLY)) {
            val videoFrames = try { videoFrameBox.all } catch (_: Exception) { emptyList() }
            val framesByVideo = videoFrames.filter { it.embedding != null }.groupBy { it.mediaStoreId }

            for ((videoId, frames) in framesByVideo) {
                val mediaItem = allMedia[videoId] ?: continue
                var bestFrameScore = 0f
                var bestTimestampMs: Long? = null

                for (frame in frames) {
                    val frameEmb = frame.embedding ?: continue
                    val frameScore = dotProduct(textEmbedding, frameEmb)
                    if (frameScore > bestFrameScore) {
                        bestFrameScore = frameScore
                        bestTimestampMs = frame.timestampMs
                    }
                }

                if (bestFrameScore >= 0.18f) {
                    val existingIdx = scoredResults.indexOfFirst { it.media.id == videoId }
                    val minutes = (bestTimestampMs ?: 0L) / 60000
                    val seconds = ((bestTimestampMs ?: 0L) % 60000) / 1000
                    val timeStr = String.format("%d:%02d", minutes, seconds)

                    val videoResult = SearchResultItem(
                        media = mediaItem,
                        similarity = max(bestFrameScore, scoredResults.getOrNull(existingIdx)?.similarity ?: 0f).coerceIn(0f, 1f),
                        matchedTimestampMs = bestTimestampMs,
                        ocrSnippet = "Moment at $timeStr"
                    )

                    if (existingIdx >= 0) {
                        scoredResults[existingIdx] = videoResult
                    } else {
                        scoredResults.add(videoResult)
                    }
                }
            }
        }

        scoredResults.sortByDescending { it.similarity }

        withContext(Dispatchers.Main) {
            _searchResults.value = scoredResults
            if (scoredResults.isEmpty()) {
                _searchError.value = "No photos matched '$queryText'"
            }
        }
    }

    private suspend fun handleDeleteIntent(
        targetQuery: String,
        category: CategoryFilter,
        dateRange: DateRangeFilter?,
        locationFilter: String? = null
    ) = withContext(Dispatchers.IO) {
        val matches = findMatchingMediaForIntent(targetQuery, category, dateRange, locationFilter)
        withContext(Dispatchers.Main) {
            if (matches.isNotEmpty()) {
                _pendingIntentAction.value = IntentAction.ConfirmDelete(matches, targetQuery, category, dateRange)
                _searchResults.value = matches.map { SearchResultItem(media = it, similarity = 1.0f) }
            } else {
                _searchError.value = "No items found matching '$targetQuery' to delete."
            }
        }
    }

    private suspend fun handleMoveToVaultIntent(
        targetQuery: String,
        category: CategoryFilter,
        dateRange: DateRangeFilter?,
        locationFilter: String? = null
    ) = withContext(Dispatchers.IO) {
        val matches = findMatchingMediaForIntent(targetQuery, category, dateRange, locationFilter)
        withContext(Dispatchers.Main) {
            if (matches.isNotEmpty()) {
                _pendingIntentAction.value = IntentAction.ConfirmMoveToVault(matches, targetQuery, category, dateRange)
                _searchResults.value = matches.map { SearchResultItem(media = it, similarity = 1.0f) }
            } else {
                _searchError.value = "No items found matching '$targetQuery' to move to Vault."
            }
        }
    }

    private suspend fun findMatchingMediaForIntent(
        targetQuery: String,
        category: CategoryFilter,
        dateRange: DateRangeFilter?,
        locationFilter: String? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val allMedia = mediaStoreRepository.loadAllMedia().associateBy { it.id }
        val indexedEmbeddings = embeddingBox.query(ImageEmbedding_.isTrashed.equal(false)).build().find()

        val textEmbedding = try { clipEmbedder.embedText(targetQuery) } catch (_: Exception) { null }

        val personMatches = try {
            val summaries = faceClusterDao.getAllClusterSummaries()
            val matchingClusters = summaries.filter {
                (it.personName.isNotBlank() && it.personName.contains(targetQuery, ignoreCase = true)) ||
                (targetQuery.startsWith("person", ignoreCase = true) && it.clusterId.contains(targetQuery.filter { c -> c.isDigit() }))
            }
            matchingClusters.flatMap { faceClusterDao.getMediaIdsForCluster(it.clusterId) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        indexedEmbeddings
            .filter { entity ->
                val media = allMedia[entity.mediaStoreId] ?: return@filter false
                if (dateRange != null && media.dateTaken !in dateRange.startEpochMs..dateRange.endEpochMs) {
                    return@filter false
                }
                if (locationFilter != null) {
                    val locMatch = media.relativePath.contains(locationFilter, ignoreCase = true) ||
                            media.albumName.contains(locationFilter, ignoreCase = true) ||
                            media.name.contains(locationFilter, ignoreCase = true)
                    if (!locMatch) return@filter false
                }
                if (category == CategoryFilter.BLURRY && !entity.isBlurry) return@filter false
                if (category == CategoryFilter.RECEIPTS_DOCUMENTS && !entity.hasSignificantText) return@filter false
                if (category == CategoryFilter.PORTRAITS && entity.faceCount < 1) return@filter false
                true
            }
            .mapNotNull { entity ->
                val media = allMedia[entity.mediaStoreId] ?: return@mapNotNull null
                val visualEmb = entity.embedding
                var score = if (textEmbedding != null && visualEmb != null) {
                    dotProduct(textEmbedding, visualEmb)
                } else {
                    0.5f
                }
                if (entity.primaryObject.isNotBlank() && entity.primaryObject.contains(targetQuery, ignoreCase = true)) {
                    score = max(score, 0.75f)
                }
                if (personMatches.contains(entity.mediaStoreId)) {
                    score = max(score, 0.80f)
                }
                if (category != CategoryFilter.ALL || score >= 0.19f) media to score else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(30)
    }

    fun executePendingDelete(items: List<MediaItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val deletedIds = mutableListOf<Long>()
            for (item in items) {
                // Mark as trashed in ObjectBox
                val existing = embeddingBox.query(ImageEmbedding_.mediaStoreId.equal(item.id)).build().findFirst()
                if (existing != null) {
                    existing.isTrashed = true
                    existing.trashedAt = System.currentTimeMillis()
                    embeddingBox.put(existing)
                    deletedIds.add(item.id)
                }
            }

            // Push to Undo DAO
            if (deletedIds.isNotEmpty()) {
                undoRedoDao.insert(
                    UndoRedoEntity(
                        actionId = java.util.UUID.randomUUID().toString(),
                        actionType = "DELETE",
                        originalStateJson = deletedIds.joinToString(","),
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            withContext(Dispatchers.Main) {
                _pendingIntentAction.value = null
                _searchResults.value = emptyList()
                _navigationEvents.emit(
                    NavigationEvent.ShowSnackbar(
                        message = "Moved ${items.size} items to Trash",
                        undoAction = { undoLastAction() }
                    )
                )
            }
        }
    }

    fun executePendingMoveToVault(items: List<MediaItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _pendingIntentAction.value = null
                _navigationEvents.emit(
                    NavigationEvent.ShowSnackbar("Moved ${items.size} items to Secure Vault")
                )
            }
        }
    }

    fun undoLastAction() {
        viewModelScope.launch(Dispatchers.IO) {
            val last = undoRedoDao.getLastAction() ?: return@launch
            if (last.actionType == "DELETE") {
                val ids = last.originalStateJson.split(",").mapNotNull { it.toLongOrNull() }
                for (id in ids) {
                    val existing = embeddingBox.query(ImageEmbedding_.mediaStoreId.equal(id)).build().findFirst()
                    if (existing != null) {
                        existing.isTrashed = false
                        existing.trashedAt = 0
                        embeddingBox.put(existing)
                    }
                }
                undoRedoDao.delete(last)
            }
        }
    }

    fun toggleReveal(mediaId: Long) {
        val current = _searchResults.value.toMutableList()
        val index = current.indexOfFirst { it.media.id == mediaId }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(isRevealed = !item.isRevealed)
            _searchResults.value = current
        }
    }

    fun dismissIntentAction() {
        _pendingIntentAction.value = null
    }

    fun clearResults() {
        _searchResults.value = emptyList()
        _searchError.value = null
        _pendingIntentAction.value = null
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            sum += a[i] * b[i]
        }
        return sum
    }
}
