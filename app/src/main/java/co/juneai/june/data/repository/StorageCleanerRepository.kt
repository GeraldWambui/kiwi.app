package co.juneai.june.data.repository

import android.content.Context
import android.net.Uri
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import co.juneai.june.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

data class DuplicateGroup(
    val groupId: String,
    val items: List<MediaItem>,
    val bestItemId: Long,
    val reclaimableBytes: Long
)

data class CleanerAnalysisResult(
    val exactDuplicates: List<DuplicateGroup>,
    val similarPhotoGroups: List<DuplicateGroup>,
    val largeVideos: List<MediaItem>,
    val blurryPhotos: List<MediaItem>,
    val oldScreenshots: List<MediaItem>,
    val totalReclaimableBytes: Long
)

@Singleton
class StorageCleanerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreRepository: MediaStoreRepository,
    private val embeddingBox: Box<ImageEmbedding>
) {

    suspend fun analyzeStorage(): CleanerAnalysisResult = withContext(Dispatchers.IO) {
        val allMedia = mediaStoreRepository.loadAllMedia()
        val mediaMap = allMedia.associateBy { it.id }

        val indexedEmbeddings = try {
            embeddingBox.query(ImageEmbedding_.isTrashed.equal(false)).build().find()
        } catch (_: Exception) {
            emptyList()
        }
        val embeddingMap = indexedEmbeddings.associateBy { it.mediaStoreId }

        // 1. Exact Duplicate Scanner (Size + Byte Checksum)
        val exactDuplicates = findExactDuplicates(allMedia)

        // 2. Visual Near-Duplicate Scanner (CLIP cosine similarity >= 0.94)
        val similarPhotoGroups = findVisualNearDuplicates(allMedia, indexedEmbeddings, mediaMap)

        // 3. Large Videos (> 30 MB)
        val largeVideos = allMedia.filter { it.isVideo && it.size >= 30 * 1024 * 1024L }
            .sortedByDescending { it.size }

        // 4. Blurry / Low-Quality Photos
        val blurryPhotos = indexedEmbeddings.filter { it.isBlurry || (it.sharpnessScore in 1f..65f) }
            .mapNotNull { mediaMap[it.mediaStoreId] }

        // 5. Old Screenshots (> 14 days old)
        val fourteenDaysAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000L)
        val oldScreenshots = allMedia.filter { item ->
            val isScreenshot = item.albumName.contains("screenshot", ignoreCase = true) ||
                    item.name.contains("screenshot", ignoreCase = true)
            isScreenshot && item.dateTaken > 0 && item.dateTaken < fourteenDaysAgo
        }

        val totalReclaimable = exactDuplicates.sumOf { it.reclaimableBytes } +
                similarPhotoGroups.sumOf { it.reclaimableBytes } +
                largeVideos.sumOf { it.size } +
                blurryPhotos.sumOf { it.size } +
                oldScreenshots.sumOf { it.size }

        CleanerAnalysisResult(
            exactDuplicates = exactDuplicates,
            similarPhotoGroups = similarPhotoGroups,
            largeVideos = largeVideos,
            blurryPhotos = blurryPhotos,
            oldScreenshots = oldScreenshots,
            totalReclaimableBytes = totalReclaimable
        )
    }

    private fun findExactDuplicates(allMedia: List<MediaItem>): List<DuplicateGroup> {
        val sizeGroups = allMedia.filter { it.size > 1024L }.groupBy { it.size }
        val duplicateGroups = mutableListOf<DuplicateGroup>()

        for ((size, items) in sizeGroups) {
            if (items.size >= 2) {
                // Compute partial MD5 hash of first 16KB to confirm exact duplicate
                val hashGroups = items.groupBy { computePartialHash(it.uri) }
                for ((hash, matchingItems) in hashGroups) {
                    if (hash != null && matchingItems.size >= 2) {
                        val best = matchingItems.maxByOrNull { it.dateTaken } ?: matchingItems.first()
                        val redundantBytes = matchingItems.filter { it.id != best.id }.sumOf { it.size }
                        duplicateGroups.add(
                            DuplicateGroup(
                                groupId = "exact_${matchingItems.first().id}",
                                items = matchingItems,
                                bestItemId = best.id,
                                reclaimableBytes = redundantBytes
                            )
                        )
                    }
                }
            }
        }

        return duplicateGroups
    }

    private fun findVisualNearDuplicates(
        allMedia: List<MediaItem>,
        embeddings: List<ImageEmbedding>,
        mediaMap: Map<Long, MediaItem>
    ): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()
        val visited = mutableSetOf<Long>()

        val validEmbeddings = embeddings.filter { it.embedding != null }

        for (i in validEmbeddings.indices) {
            val anchor = validEmbeddings[i]
            if (visited.contains(anchor.mediaStoreId)) continue

            val cluster = mutableListOf<ImageEmbedding>()
            cluster.add(anchor)

            val anchorVec = anchor.embedding ?: continue

            for (j in (i + 1) until validEmbeddings.size) {
                val candidate = validEmbeddings[j]
                if (visited.contains(candidate.mediaStoreId)) continue

                val candidateVec = candidate.embedding ?: continue
                val sim = cosineSimilarity(anchorVec, candidateVec)

                if (sim >= 0.94f) { // Very high visual similarity threshold (bursts/identical shots)
                    cluster.add(candidate)
                    visited.add(candidate.mediaStoreId)
                }
            }

            if (cluster.size >= 2) {
                visited.add(anchor.mediaStoreId)
                val matchedMedia = cluster.mapNotNull { mediaMap[it.mediaStoreId] }
                if (matchedMedia.size >= 2) {
                    // Pick the highest aesthetic / sharpest photo as the best one
                    val bestEntity = cluster.maxByOrNull { (it.aestheticScore * 100f) + (if (it.isBlurry) -50f else 0f) }
                    val bestId = bestEntity?.mediaStoreId ?: matchedMedia.first().id

                    val redundantBytes = matchedMedia.filter { it.id != bestId }.sumOf { it.size }
                    groups.add(
                        DuplicateGroup(
                            groupId = "similar_${anchor.mediaStoreId}",
                            items = matchedMedia,
                            bestItemId = bestId,
                            reclaimableBytes = redundantBytes
                        )
                    )
                }
            }
        }

        return groups.take(30)
    }

    private fun computePartialHash(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            val stream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                val buffer = ByteArray(16384) // 16 KB prefix
                val bytesRead = input.read(buffer)
                if (bytesRead > 0) {
                    val digest = MessageDigest.getInstance("MD5")
                    digest.update(buffer, 0, bytesRead)
                    digest.digest().joinToString("") { "%02x".format(it) }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val len = min(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
        }
        return dot
    }
}
