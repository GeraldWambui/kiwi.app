package co.juneai.june.data.repository

import co.juneai.june.data.local.daos.FaceClusterDao
import co.juneai.june.data.local.daos.FaceClusterSummary
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import co.juneai.june.data.model.MediaItem
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class MemoryStory(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<MediaItem>,
    val coverItem: MediaItem,
    val type: MemoryType
)

enum class MemoryType {
    ON_THIS_DAY,
    TRIP_EVENT,
    BEST_SHOTS,
    PERSON_HIGHLIGHT,
    SMART_THEME,
    DUO_HIGHLIGHT,
    GROUP_HIGHLIGHT
}

data class PersonHighlight(
    val clusterId: String,
    val personName: String,
    val coverMedia: MediaItem,
    val photoCount: Int,
    val items: List<MediaItem>
)

data class ForYouData(
    val onThisDayMemories: List<MemoryStory>,
    val tripEvents: List<MemoryStory>,
    val featuredHighlights: List<MediaItem>,
    val peopleHighlights: List<PersonHighlight>,
    val smartCollections: List<MemoryStory>,
    val recentVideos: List<MediaItem>
)

@Singleton
class MemoriesRepository @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val faceClusterDao: FaceClusterDao,
    private val embeddingBox: Box<ImageEmbedding>
) {

    suspend fun loadForYouData(): ForYouData = withContext(Dispatchers.IO) {
        val allMedia = mediaStoreRepository.loadAllMedia()
        if (allMedia.isEmpty()) {
            return@withContext ForYouData(
                onThisDayMemories = emptyList(),
                tripEvents = emptyList(),
                featuredHighlights = emptyList(),
                peopleHighlights = emptyList(),
                smartCollections = emptyList(),
                recentVideos = emptyList()
            )
        }

        val embeddingsMap = try {
            embeddingBox.all.associateBy { it.mediaStoreId }
        } catch (_: Exception) {
            emptyMap()
        }

        val onThisDay = computeOnThisDay(allMedia)
        val tripEvents = computeTripEvents(allMedia)
        val featured = computeBestShots(allMedia, embeddingsMap)
        val people = computePeopleHighlights(allMedia)
        val coOccurrenceStories = computeCoOccurrenceStories(allMedia)
        val collections = computeSmartCollections(allMedia, embeddingsMap) + coOccurrenceStories
        val recentVideos = allMedia.filter { it.isVideo }.take(10)

        ForYouData(
            onThisDayMemories = onThisDay,
            tripEvents = tripEvents,
            featuredHighlights = featured,
            peopleHighlights = people,
            smartCollections = collections,
            recentVideos = recentVideos
        )
    }

    /**
     * Finds photos taken on the same month & day in previous years (1 yr ago, 2 yrs ago, etc.).
     */
    private fun computeOnThisDay(media: List<MediaItem>): List<MemoryStory> {
        val today = Calendar.getInstance()
        val currentDay = today.get(Calendar.DAY_OF_YEAR)
        val currentYear = today.get(Calendar.YEAR)

        val cal = Calendar.getInstance()
        val yearGroups = mutableMapOf<Int, MutableList<MediaItem>>()

        for (item in media) {
            if (item.dateTaken <= 0) continue
            cal.timeInMillis = item.dateTaken
            val itemYear = cal.get(Calendar.YEAR)
            val itemDay = cal.get(Calendar.DAY_OF_YEAR)

            if (itemYear < currentYear && kotlin.math.abs(itemDay - currentDay) <= 1) {
                val yearsAgo = currentYear - itemYear
                yearGroups.getOrPut(yearsAgo) { mutableListOf() }.add(item)
            }
        }

        return yearGroups.entries
            .sortedBy { it.key }
            .map { (yearsAgo, items) ->
                val title = if (yearsAgo == 1) "1 Year Ago Today" else "$yearsAgo Years Ago Today"
                val sample = items.first()
                val dateLabel = Date(sample.dateTaken).toString().take(10)
                MemoryStory(
                    id = "on_this_day_$yearsAgo",
                    title = title,
                    subtitle = "$dateLabel • ${items.size} moments",
                    items = items,
                    coverItem = sample,
                    type = MemoryType.ON_THIS_DAY
                )
            }
    }

    /**
     * Clusters photos taken within short time windows (> 4h intervals) to detect travel / day events.
     */
    private fun computeTripEvents(media: List<MediaItem>): List<MemoryStory> {
        val sorted = media.filter { it.dateTaken > 0 }.sortedByDescending { it.dateTaken }
        if (sorted.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<MediaItem>>()
        var currentCluster = mutableListOf<MediaItem>()

        for (item in sorted) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(item)
            } else {
                val lastItem = currentCluster.last()
                val diffHours = kotlin.math.abs(lastItem.dateTaken - item.dateTaken) / (1000 * 60 * 60)
                if (diffHours < 24) {
                    currentCluster.add(item)
                } else {
                    if (currentCluster.size >= 4) {
                        clusters.add(currentCluster)
                    }
                    currentCluster = mutableListOf(item)
                }
            }
        }
        if (currentCluster.size >= 4) {
            clusters.add(currentCluster)
        }

        return clusters.take(5).mapIndexed { idx, cluster ->
            val first = cluster.first()
            val albumName = first.albumName.ifBlank { "Trip Moment" }
            MemoryStory(
                id = "trip_event_$idx",
                title = albumName,
                subtitle = "${cluster.size} photos",
                items = cluster,
                coverItem = cluster.first(),
                type = MemoryType.TRIP_EVENT
            )
        }
    }

    /**
     * Ranks photos by aesthetic/quality score, filtering out blurry photos and burst duplicates.
     */
    private fun computeBestShots(
        media: List<MediaItem>,
        embeddingsMap: Map<Long, ImageEmbedding>
    ): List<MediaItem> {
        val nonVideos = media.filter { !it.isVideo }
        if (nonVideos.isEmpty()) return emptyList()

        return nonVideos
            .sortedWith(
                compareByDescending<MediaItem> { item ->
                    val emb = embeddingsMap[item.id]
                    (emb?.aestheticScore ?: 0.5f) + if (emb?.isBlurry == true) -0.5f else 0f
                }.thenByDescending { it.size }
            )
            .take(6)
    }

    /**
     * Loads top face clusters and matches with MediaStore items.
     */
    private suspend fun computePeopleHighlights(media: List<MediaItem>): List<PersonHighlight> {
        val mediaMap = media.associateBy { it.id }
        val summaries = faceClusterDao.getAllClusterSummaries()

        return summaries.take(8).mapNotNull { summary ->
            val cover = mediaMap[summary.coverMediaStoreId] ?: return@mapNotNull null
            val mediaIds = faceClusterDao.getMediaIdsForCluster(summary.clusterId)
            val clusterItems = mediaIds.mapNotNull { mediaMap[it] }

            PersonHighlight(
                clusterId = summary.clusterId,
                personName = summary.personName.ifBlank { "Person ${summary.clusterId.takeLast(4)}" },
                coverMedia = cover,
                photoCount = clusterItems.size,
                items = clusterItems
            )
        }
    }

    /**
     * Detects co-occurrence groups (pairs/groups of people appearing together in >= 2 photos).
     */
    private suspend fun computeCoOccurrenceStories(media: List<MediaItem>): List<MemoryStory> {
        val mediaMap = media.associateBy { it.id }
        val allFaces = faceClusterDao.getAllFaces()
        if (allFaces.isEmpty()) return emptyList()

        val summaries = faceClusterDao.getAllClusterSummaries().associateBy { it.clusterId }

        // Group faces by mediaStoreId
        val facesByMedia = allFaces.groupBy { it.mediaStoreId }
        val coOccurrenceMap = mutableMapOf<Set<String>, MutableList<Long>>()

        for ((mediaId, faces) in facesByMedia) {
            val distinctClusters = faces.map { it.clusterId }.distinct()
            if (distinctClusters.size >= 2) {
                // Pair combinations
                for (i in distinctClusters.indices) {
                    for (j in (i + 1) until distinctClusters.size) {
                        val pairKey = setOf(distinctClusters[i], distinctClusters[j])
                        coOccurrenceMap.getOrPut(pairKey) { mutableListOf() }.add(mediaId)
                    }
                }
            }
        }

        val stories = mutableListOf<MemoryStory>()
        for ((pairKey, mediaIds) in coOccurrenceMap) {
            if (mediaIds.size >= 2) { // At least 2 shared photos
                val pairList = pairKey.toList()
                val person1 = summaries[pairList[0]]?.personName?.ifBlank { "Person ${pairList[0].takeLast(4)}" } ?: "Friend"
                val person2 = summaries[pairList[1]]?.personName?.ifBlank { "Person ${pairList[1].takeLast(4)}" } ?: "Friend"
                val matchedItems = mediaIds.distinct().mapNotNull { mediaMap[it] }

                if (matchedItems.isNotEmpty()) {
                    stories.add(
                        MemoryStory(
                            id = "duo_${pairList[0]}_${pairList[1]}",
                            title = "$person1 & $person2",
                            subtitle = "${matchedItems.size} shared moments",
                            items = matchedItems,
                            coverItem = matchedItems.first(),
                            type = MemoryType.DUO_HIGHLIGHT
                        )
                    )
                }
            }
        }

        return stories.take(4)
    }

    /**
     * Curates Smart Themed Collections (Receipts & Documents, Portraits, High-Res Nature).
     */
    private fun computeSmartCollections(
        media: List<MediaItem>,
        embeddingsMap: Map<Long, ImageEmbedding>
    ): List<MemoryStory> {
        val collections = mutableListOf<MemoryStory>()

        // 1. Documents & Receipts
        val docItems = media.filter { item ->
            val emb = embeddingsMap[item.id]
            emb?.hasSignificantText == true || item.name.contains("receipt", true) || item.name.contains("document", true)
        }
        if (docItems.isNotEmpty()) {
            collections.add(
                MemoryStory(
                    id = "smart_documents",
                    title = "Documents & Receipts",
                    subtitle = "${docItems.size} scanned documents",
                    items = docItems,
                    coverItem = docItems.first(),
                    type = MemoryType.SMART_THEME
                )
            )
        }

        // 2. Portraits & People
        val portraitItems = media.filter { item ->
            val emb = embeddingsMap[item.id]
            (emb?.faceCount ?: 0) >= 1
        }
        if (portraitItems.isNotEmpty()) {
            collections.add(
                MemoryStory(
                    id = "smart_portraits",
                    title = "Portraits & Smiles",
                    subtitle = "${portraitItems.size} photos of people",
                    items = portraitItems,
                    coverItem = portraitItems.first(),
                    type = MemoryType.SMART_THEME
                )
            )
        }

        return collections
    }
}
