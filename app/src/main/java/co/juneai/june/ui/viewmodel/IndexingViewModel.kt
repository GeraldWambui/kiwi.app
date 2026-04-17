package co.juneai.june.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.juneai.june.data.repository.ml.ClipSearchResult
import co.juneai.june.data.repository.ml.ImageRepository
import co.juneai.june.ml.clip.ClipEmbedder
import co.juneai.june.worker.IndexingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for both the indexing pipeline and semantic image search.
 *
 * ─── Indexing flow ──────────────────────────────────────────────────────────
 *  1. On [init], [observeWorkManager] wires [WorkManager.getWorkInfosForUniqueWorkFlow]
 *     to [indexingState]. This is the single source of truth for indexing progress —
 *     it correctly represents running workers from previous sessions too.
 *  2. [checkAndEnqueueIfNeeded] queries [ImageRepository.unindexedCount] on IO and
 *     enqueues [IndexingWorker] if there is unindexed work. WorkManager's
 *     [ExistingWorkPolicy.KEEP] prevents starting a duplicate if one is already running.
 *  3. The worker runs as an expedited foreground job. WorkManager survives the app
 *     going to the background; the worker continues until completion.
 *
 * ─── Search flow ────────────────────────────────────────────────────────────
 *  [searchQuery] is a [MutableStateFlow] debounced to 300ms. Collectors call
 *  [search] automatically — the UI just updates the query string.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class IndexingViewModel @Inject constructor(
    private val workManager:  WorkManager,
    private val repository: ImageRepository,
    private val embedder:     ClipEmbedder,
) : ViewModel() {

    // ── Indexing state ────────────────────────────────────────────────────────

    sealed class IndexingState {
        /** No unindexed images; nothing running. */
        object Idle : IndexingState()

        /** Work has been enqueued but the worker hasn't started yet. */
        object Enqueued : IndexingState()

        /** Worker is actively embedding images. */
        data class Running(
            val indexed: Int,
            val total:   Int,
            val failed:  Int,
        ) : IndexingState()

        /** Worker finished successfully. */
        data class Complete(val indexed: Int, val failed: Int) : IndexingState()

        /** Worker failed with a terminal error. */
        data class Error(val message: String) : IndexingState()
    }

    private val _indexingState = MutableStateFlow<IndexingState>(IndexingState.Idle)
    val indexingState: StateFlow<IndexingState> = _indexingState.asStateFlow()

    // ── Search state ──────────────────────────────────────────────────────────

    val searchQuery = MutableStateFlow("")

    private val _searchResults = MutableStateFlow<List<ClipSearchResult>>(emptyList())
    val searchResults: StateFlow<List<ClipSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // Wire WorkManager → indexingState FIRST, so any already-running worker's
        // state is reflected immediately (before checkAndEnqueueIfNeeded runs).
        observeWorkManager()

        // Enqueue work if needed (runs on IO dispatcher; does not block init)
        checkAndEnqueueIfNeeded()

        // Wire debounced search
        searchQuery
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query -> if (query.isBlank()) _searchResults.value = emptyList() else executeSearch(query) }
            .launchIn(viewModelScope)
    }

    // ── Indexing ──────────────────────────────────────────────────────────────

    /**
     * Subscribes to WorkManager state for [IndexingWorker.WORK_NAME] and maps it to
     * [IndexingState]. This Flow re-emits on every WorkInfo change, including progress
     * data updates from inside the worker.
     */
    private fun observeWorkManager() {
        workManager
            .getWorkInfosForUniqueWorkFlow(IndexingWorker.WORK_NAME)
            .onEach { infos ->
                val info = infos.firstOrNull()

                _indexingState.value = when {
                    info == null                            -> IndexingState.Idle
                    info.state == WorkInfo.State.RUNNING   -> {
                        IndexingState.Running(
                            indexed = info.progress.getInt(IndexingWorker.KEY_INDEXED, 0),
                            total   = info.progress.getInt(IndexingWorker.KEY_TOTAL,   0),
                            failed  = info.progress.getInt(IndexingWorker.KEY_FAILED,  0),
                        )
                    }
                    info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.BLOCKED   -> IndexingState.Enqueued
                    info.state == WorkInfo.State.SUCCEEDED -> {
                        IndexingState.Complete(
                            indexed = info.outputData.getInt(IndexingWorker.OUT_INDEXED, 0),
                            failed  = info.outputData.getInt(IndexingWorker.OUT_FAILED,  0),
                        )
                    }
                    info.state == WorkInfo.State.FAILED    -> {
                        IndexingState.Error(info.outputData.getString("error") ?: "Indexing failed")
                    }
                    info.state == WorkInfo.State.CANCELLED -> IndexingState.Idle
                    else                                   -> _indexingState.value
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Queries [ImageRepository.unindexedCount] and enqueues [IndexingWorker] if needed.
     *
     * [ExistingWorkPolicy.KEEP] means: if a worker is already RUNNING or ENQUEUED for
     * [IndexingWorker.WORK_NAME], do nothing. The existing worker handles the work.
     *
     * The worker is requested as expedited ([OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST]
     * is the fallback if the expedited quota is exhausted). This gives it higher scheduling
     * priority while the app is in the foreground.
     */
    private fun checkAndEnqueueIfNeeded() {
        viewModelScope.launch {
            val unindexed = repository.unindexedCount()
            if (unindexed > 0) {
                val request = OneTimeWorkRequestBuilder<IndexingWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresStorageNotLow(true)
                            .build(),
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    IndexingWorker.WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }

    /** Cancels any running or enqueued indexing work. */
    fun cancelIndexing() {
        workManager.cancelUniqueWork(IndexingWorker.WORK_NAME)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /** Update the search query. Results are debounced 300ms and emitted via [searchResults]. */
    fun updateQuery(query: String) {
        searchQuery.value = query
    }

    /** Clears the current search results and resets the query. */
    fun clearSearch() {
        searchQuery.value    = ""
        _searchResults.value = emptyList()
    }

    private fun executeSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val queryEmbedding = withContext(Dispatchers.Default) {
                    embedder.embedText(query)
                }
                _searchResults.value = repository.search(queryEmbedding, topK = 20)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                e.printStackTrace()
            } finally {
                _isSearching.value = false
            }
        }
    }
}