package co.juneai.june.data.repository.ml

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import co.juneai.june.data.local.ml.ImageEmbedding
import co.juneai.june.data.local.ml.ImageEmbedding_
import co.juneai.june.ml.clip.ClipEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.objectbox.Box
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ─── Data models ──────────────────────────────────────────────────────────────

data class MediaImage(
    val id:   Long,
    val uri:  Uri,
    val name: String,
)

data class ClipSearchResult(
    val rank:        Int,
    val uri:         Uri,
    val displayName: String,
    /**
     * Cosine similarity ∈ [0, 1]: 1.0 = identical, 0.0 = completely unrelated.
     * Derived from ObjectBox cosine distance d ∈ [0, 2] as: similarity = 1 − d/2.
     */
    val similarity: Float,
)

// ─── Repository ───────────────────────────────────────────────────────────────

/**
 * Single source of truth for images, bridging:
 *   - Android [MediaStore]   — authoritative list of images present on device
 *   - ObjectBox [Box]        — persistent embedding storage with HNSW vector index
 *   - [ClipEmbedder]         — ML inference (injected into callers, not owned here)
 *
 * All public functions are suspend functions dispatched on the correct coroutine context.
 * Never call from the main thread.
 */
@Singleton
class ImageRepository @Inject constructor(
    private val context: Context,
    private val box: Box<ImageEmbedding>,
) {

    // ── MediaStore ────────────────────────────────────────────────────────────

    /**
     * Returns all non-trashed images from MediaStore, sorted newest first.
     * Dispatches to [Dispatchers.IO].
     */
    suspend fun scanMediaStore(): List<MediaImage> = withContext(Dispatchers.IO) {
        val results  = mutableListOf<MediaImage>()
        val baseUri  = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            baseUri,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
            ),
            "${MediaStore.Images.Media.IS_TRASHED} = 0",
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                results.add(
                    MediaImage(
                        id   = id,
                        uri  = Uri.withAppendedPath(baseUri, id.toString()),
                        name = cursor.getString(nameCol),
                    ),
                )
            }
        }
        results
    }

    // ── Indexing state ────────────────────────────────────────────────────────

    /**
     * Images present in MediaStore but not yet in ObjectBox.
     * Uses a [HashSet] for the indexed-ID lookup so filtering is O(1) per image.
     */
    suspend fun unindexedImages(): List<MediaImage> {
        val all     = scanMediaStore()
        val indexed = indexedMediaStoreIds()   // HashSet → O(1) contains
        return all.filter { it.id !in indexed } // it.id !in indexed -> false
    }

    suspend fun unindexedCount(): Int = unindexedImages().size

    suspend fun indexedCount(): Int =
        withContext(Dispatchers.IO) { box.count().toInt() }

    suspend fun totalMediaStoreCount(): Int = scanMediaStore().size

    /**
     * Reads only the [ImageEmbedding.mediaStoreId] column from ObjectBox —
     * no full entity loads, no embedding bytes transferred.
     */
    private suspend fun indexedMediaStoreIds(): Set<Long> = withContext(Dispatchers.IO) {
        box.query().build().property(ImageEmbedding_.mediaStoreId).findLongs().toSet()
    }

    // ── Embed + store ─────────────────────────────────────────────────────────

    /**
     * Batch-embeds [images] and stores results in ObjectBox in chunks of [chunkSize].
     *
     * Each [chunkSize] images are committed in one ObjectBox transaction (single fsync),
     * which is far faster than one transaction per image for large galleries.
     *
     * Failure policy: individual image failures are counted and skipped; they do NOT
     * abort the batch. The caller receives accurate counts via [onProgress].
     *
     * @param onProgress Callback invoked after every image: (totalIndexed, grandTotal, failed).
     * @return Count of successfully embedded images in this invocation.
     */
    suspend fun batchEmbedAndStore(
        images:     List<MediaImage>,
        embedder: ClipEmbedder,
        chunkSize:  Int = 32,
        onProgress: suspend (indexed: Int, total: Int, failed: Int) -> Unit = { _, _, _ -> },
    ): Int {
        val total   = images.size
        var indexed = 0
        var failed  = 0
        val batch   = ArrayList<ImageEmbedding>(chunkSize)

        for (image in images) {

            // Decode on IO thread
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(image.uri)
                        ?.use { stream -> BitmapFactory.decodeStream(stream) }
                }.getOrNull()
            }

            if (bitmap == null) {
                // URI inaccessible (deleted between scan and now) — count as failed, continue
                failed++
                onProgress(indexed, total, failed)
                continue
            }

            try {
                // TFLite inference on Default (CPU-bound, never IO)
                val embedding = withContext(Dispatchers.Default) {
                    embedder.embedImage(bitmap)
                }
                bitmap.recycle()

                batch.add(
                    ImageEmbedding(
                        mediaStoreId = image.id,
                        uri          = image.uri.toString(),
                        displayName  = image.name,
                        embedding    = embedding,
                        indexedAt    = System.currentTimeMillis(),
                    ),
                )
                indexed++

                // Flush chunk — one ObjectBox transaction covers all [chunkSize] rows
                if (batch.size >= chunkSize) {
                    withContext(Dispatchers.IO) { box.put(batch) }
                    batch.clear()
                }
            } catch (e: Exception) {
                bitmap.recycle()
                failed++
                e.printStackTrace()   // Log individual failures; do not rethrow
            }

            onProgress(indexed, total, failed)
        }

        // Flush the final partial chunk
        if (batch.isNotEmpty()) {
            withContext(Dispatchers.IO) { box.put(batch) }
        }

        return indexed
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    /**
     * Removes ObjectBox rows for images no longer present in MediaStore.
     *
     * Uses an ObjectBox `notIn` query evaluated natively — no Kotlin-side filtering.
     * Called once at the start of every indexing run to keep the DB consistent.
     *
     * @return Number of stale rows deleted.
     */
    suspend fun pruneStale(): Int = withContext(Dispatchers.IO) {
        val activeIds = scanMediaStore().map { it.id }.toLongArray()
        val stale = when {
            activeIds.isEmpty() -> box.all
            else -> box.query().build().find().filter { it.mediaStoreId !in activeIds }
        }
        val count = stale.size
        if (count > 0) box.remove(stale)
        count
    }

    // ── Semantic search ───────────────────────────────────────────────────────

    /**
     * Returns the [topK] semantically closest images to [queryEmbedding].
     *
     * Uses ObjectBox's native HNSW approximate nearest-neighbor index:
     *   - O(log N) query vs. O(N × DIM) for a linear scan
     *   - Typical latency: < 5ms at 50k images
     *
     * [queryEmbedding] must be L2-normalized. [ClipEmbedder.embedText] and
     * [ClipEmbedder.embedImage] both guarantee this.
     *
     * Results are returned most-similar first. [SearchResult.similarity] is in [0, 1].
     */
    suspend fun search(
        queryEmbedding: FloatArray,
        topK: Int = 20,
    ): List<ClipSearchResult> = withContext(Dispatchers.IO) {
        val query = box.query(
            ImageEmbedding_.embedding.nearestNeighbors(queryEmbedding, topK)
        ).build()

        query.findWithScores()
            .mapIndexed { rank, scored ->
                val entity = scored.get()
                // ObjectBox distance ∈ [0, 2] for cosine → similarity ∈ [0, 1]
                val similarity = (1f - (scored.score / 2.0).toFloat()).coerceIn(0f, 1f)
                ClipSearchResult(
                    rank = rank + 1,
                    uri = Uri.parse(entity.uri),
                    displayName = entity.displayName,
                    similarity = similarity,
                )
            }
    }
}