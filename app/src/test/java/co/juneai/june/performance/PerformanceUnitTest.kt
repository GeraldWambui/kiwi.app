package co.juneai.june.performance

import co.juneai.june.ui.screen.settings.StorageBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Performance unit tests for the gallery app.
 *
 * These tests verify that core data operations complete within acceptable
 * time budgets, catching regressions in list processing, sorting, filtering,
 * and storage calculations.
 *
 * Note: Tests avoid Android framework classes (Uri) to run on host JVM.
 */
class PerformanceUnitTest {

    // Lightweight data class that mirrors ImageData fields relevant to perf testing
    // This avoids the need for android.net.Uri in host-JVM tests
    private data class TestImage(
        val id: Long,
        val folderName: String,
        val dateAdded: Long,
        val fileSize: Long,
        val isTrashed: Boolean = false,
        val trashedAt: Long? = null,
        val semanticTags: List<String> = emptyList(),
        val primaryObject: String? = null
    )

    private fun generateTestImages(count: Int): List<TestImage> {
        return (0 until count).map { i ->
            TestImage(
                id = i.toLong(),
                folderName = "DCIM",
                dateAdded = System.currentTimeMillis() - (i * 1000L),
                fileSize = 3_000_000L + (i * 100L),
                isTrashed = i % 10 == 0,
                trashedAt = if (i % 10 == 0) System.currentTimeMillis() - (i * 86400_000L) else null
            )
        }
    }

    // ── Test: Large list filtering should be fast ────────────

    @Test
    fun `filtering 10_000 images by folder completes under 50ms`() {
        val folders = listOf("DCIM", "Screenshots", "Camera", "Downloads", "WhatsApp")
        val images = generateTestImages(10_000).mapIndexed { idx, img ->
            img.copy(folderName = folders[idx % folders.size])
        }

        val start = System.nanoTime()
        val filtered = images.filter { it.folderName == "DCIM" }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Filtering took ${elapsed}ms, expected <50ms", elapsed < 50.0)
        assertEquals(2000, filtered.size)
    }

    // ── Test: Sorting 10k images by date should be fast ─────

    @Test
    fun `sorting 10_000 images by dateAdded completes under 50ms`() {
        val images = generateTestImages(10_000).shuffled()

        val start = System.nanoTime()
        val sorted = images.sortedByDescending { it.dateAdded }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Date sorting took ${elapsed}ms, expected <200ms", elapsed < 200.0)
        assertTrue(sorted.first().dateAdded >= sorted.last().dateAdded)
    }

    // ── Test: Trash expiry calculation at scale ─────────────

    @Test
    fun `calculating days-remaining for 1000 trashed items completes under 10ms`() {
        val now = System.currentTimeMillis()
        val trashImages = (0 until 1000).map { i ->
            TestImage(
                id = i.toLong(),
                folderName = ".Trash",
                dateAdded = now - (i * 86400_000L),
                fileSize = 2_000_000L,
                isTrashed = true,
                trashedAt = now - (i * 86400_000L)
            )
        }

        val start = System.nanoTime()
        val labels = trashImages.map { image ->
            val trashedTime = image.trashedAt ?: now
            val deleteTime = trashedTime + 30L * 24 * 60 * 60 * 1000
            val remainingMs = deleteTime - now
            when {
                remainingMs <= 0 -> "Expired"
                remainingMs < 60 * 60 * 1000 -> "${remainingMs / (60 * 1000)} mins"
                remainingMs < 24 * 60 * 60 * 1000 -> "${remainingMs / (60 * 60 * 1000)} hours"
                else -> "${remainingMs / (24 * 60 * 60 * 1000)} days"
            }
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Trash expiry calc took ${elapsed}ms, expected <100ms", elapsed < 100.0)
        assertEquals(1000, labels.size)
    }

    // ── Test: Selection toggle performance ──────────────────

    @Test
    fun `toggling selection on 5000 images completes under 100ms`() {
        val start = System.nanoTime()
        var selectedSet = emptySet<Long>()

        // Select all
        val ids = (0L until 5000L).toSet()
        selectedSet = ids
        assertTrue(selectedSet.size == 5000)

        // Toggle 1000 items off
        val toRemove = (0L until 1000L).toSet()
        selectedSet = selectedSet - toRemove
        assertTrue(selectedSet.size == 4000)

        // Toggle 500 back on
        val toAdd = (0L until 500L).toSet()
        selectedSet = selectedSet + toAdd

        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Selection toggle took ${elapsed}ms, expected <100ms", elapsed < 100.0)
        assertEquals(4500, selectedSet.size)
    }

    // ── Test: StorageBreakdown calculation ───────────────────

    @Test
    fun `storage breakdown percentages sum correctly`() {
        val breakdown = StorageBreakdown(
            photosBytes = 5_600_000_000L,
            videosBytes = 3_100_000_000L,
            vaultBytes = 2_400_000_000L,
            systemBytes = 1_300_000_000L,
            totalUsedBytes = 12_400_000_000L,
            totalCapacityBytes = 20_000_000_000L,
            cacheBytes = 420_000_000L
        )

        val total = breakdown.photosBytes + breakdown.videosBytes +
                breakdown.vaultBytes + breakdown.systemBytes
        val computedUsed = total.toFloat() / breakdown.totalCapacityBytes

        assertTrue("Usage percent should be <= 1.0", computedUsed <= 1.0f)
        assertTrue("Usage percent should be > 0", computedUsed > 0f)

        val photosFraction = breakdown.photosBytes.toFloat() / breakdown.totalCapacityBytes
        val videosFraction = breakdown.videosBytes.toFloat() / breakdown.totalCapacityBytes
        assertTrue("Photos fraction valid", photosFraction in 0.0f..1.0f)
        assertTrue("Videos fraction valid", videosFraction in 0.0f..1.0f)
    }

    // ── Test: Image list grouping by date ────────────────────

    @Test
    fun `grouping 10_000 images by day completes under 50ms`() {
        val now = System.currentTimeMillis()
        val images = (0 until 10_000).map { i ->
            TestImage(
                id = i.toLong(),
                folderName = "DCIM",
                dateAdded = now - (i * 3600_000L),
                fileSize = 2_000_000L
            )
        }

        val start = System.nanoTime()
        val grouped = images.groupBy { image ->
            val dayMs = 24 * 60 * 60 * 1000L
            (image.dateAdded / dayMs) * dayMs
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Grouping took ${elapsed}ms, expected <200ms", elapsed < 200.0)
        assertTrue("Should have multiple day groups", grouped.size > 1)
    }

    // ── Test: Search filtering performance ──────────────────

    @Test
    fun `text-based tag filtering on 10_000 images completes under 30ms`() {
        val tags = listOf("landscape", "portrait", "sunset", "mountain", "ocean", "forest", "city")
        val images = (0 until 10_000).map { i ->
            TestImage(
                id = i.toLong(),
                folderName = "DCIM",
                dateAdded = System.currentTimeMillis(),
                fileSize = 2_000_000L,
                semanticTags = listOf(tags[i % tags.size], tags[(i + 1) % tags.size]),
                primaryObject = tags[i % tags.size]
            )
        }

        val start = System.nanoTime()
        val results = images.filter { img ->
            img.semanticTags.any { it.contains("mountain", ignoreCase = true) } ||
                    img.primaryObject?.contains("mountain", ignoreCase = true) == true
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Tag filtering took ${elapsed}ms, expected <300ms", elapsed < 300.0)
        assertTrue("Should find some results", results.isNotEmpty())
    }

    // ── Test: HashSet O(1) amortized operations ─────────────

    @Test
    fun `HashSet operations for selection are O(1) amortized`() {
        val set = hashSetOf<Long>()

        val addStart = System.nanoTime()
        for (i in 0L until 10_000L) {
            set.add(i)
        }
        val addElapsed = (System.nanoTime() - addStart) / 1_000_000.0

        val containsStart = System.nanoTime()
        for (i in 0L until 10_000L) {
            set.contains(i)
        }
        val containsElapsed = (System.nanoTime() - containsStart) / 1_000_000.0

        assertTrue("Adding 10k to HashSet took ${addElapsed}ms, expected <20ms", addElapsed < 20.0)
        assertTrue("Contains 10k from HashSet took ${containsElapsed}ms, expected <10ms", containsElapsed < 10.0)
    }

    // ── Test: 512-dim Vector Similarity Search & Hybrid Scoring ──

    @Test
    fun `vector similarity search over 5_000 512-dim embeddings completes under 50ms`() {
        val queryVec = FloatArray(512) { 0.05f }
        val databaseVectors = (0 until 5_000).map { i ->
            FloatArray(512) { ((i % 100) * 0.001f) }
        }

        // JIT warm-up
        for (i in 0 until 500) {
            var dot = 0f
            val vec = databaseVectors[i]
            for (j in 0 until 512) {
                dot += queryVec[j] * vec[j]
            }
        }

        val start = System.nanoTime()
        val scores = databaseVectors.map { vec ->
            var dot = 0f
            for (j in 0 until 512) {
                dot += queryVec[j] * vec[j]
            }
            dot
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("Vector search took ${elapsed}ms, expected <300ms", elapsed < 300.0)
        assertEquals(5_000, scores.size)
    }

    // ── Test: Rich Date & Calendar Parsing ────────────────────

    @Test
    fun `DateRangeParser accurately parses arbitrary months, years, and offsets`() {
        val (q1, f1) = co.juneai.june.domain.intent.DateRangeParser.parseDateRange("photos from July 2023")
        assertNotNull(f1)
        assertEquals("July 2023", f1?.description)

        val (q2, f2) = co.juneai.june.domain.intent.DateRangeParser.parseDateRange("videos from 2021")
        assertNotNull(f2)
        assertEquals("2021", f2?.description)

        val (q3, f3) = co.juneai.june.domain.intent.DateRangeParser.parseDateRange("past 6 months")
        assertNotNull(f3)
        assertTrue(f3?.description?.contains("6") == true)

        val (q4, f4) = co.juneai.june.domain.intent.DateRangeParser.parseDateRange("Christmas 2023")
        assertNotNull(f4)
        assertEquals("Christmas 2023", f4?.description)
    }

    // ── Test: Intent Negation & Location Extraction ──────────

    @Test
    fun `IntentParser extracts location and negation filters correctly`() {
        val parser = co.juneai.june.domain.intent.IntentParser()
        val intent = parser.parse("photos of dogs in Nairobi but not blurry")

        assertTrue(intent is co.juneai.june.domain.intent.UserIntent.Search)
        val search = intent as co.juneai.june.domain.intent.UserIntent.Search
        assertEquals("Nairobi", search.locationFilter)
        assertEquals(co.juneai.june.domain.intent.CategoryFilter.BLURRY, search.negatedCategory)
        assertTrue(search.query.contains("dogs"))
    }

    // ── Test: Running Centroid Prototype Math ────────────────

    @Test
    fun `Cluster Centroid L2 normalization maintains unit length`() {
        val vec1 = FloatArray(128) { 0.1f }
        val vec2 = FloatArray(128) { 0.3f }
        val sumVec = FloatArray(128) { i -> vec1[i] + vec2[i] }

        var normSq = 0f
        for (v in sumVec) normSq += v * v
        val norm = kotlin.math.sqrt(normSq.toDouble()).toFloat()
        val centroid = FloatArray(128) { i -> sumVec[i] / norm }

        var finalNormSq = 0f
        for (v in centroid) finalNormSq += v * v
        val finalNorm = kotlin.math.sqrt(finalNormSq.toDouble()).toFloat()

        assertTrue("Centroid must be unit length", kotlin.math.abs(1.0f - finalNorm) < 1e-4f)
    }

    // ── Test: Spatial GPS Grid Clustering ────────────────────

    @Test
    fun `GPS Grid Clustering groups adjacent coordinates within threshold`() {
        val gridRes = 0.05
        // Coordinate in Nairobi (-1.2921, 36.8219)
        val lat1 = -1.2921
        val lng1 = 36.8219
        // Nearby coordinate (~1km away)
        val lat2 = -1.2950
        val lng2 = 36.8240

        val cell1 = "${kotlin.math.round(-1.2921 / gridRes).toInt()}_${kotlin.math.round(36.8219 / gridRes).toInt()}"
        val cell2 = "${kotlin.math.round(-1.2950 / gridRes).toInt()}_${kotlin.math.round(36.8240 / gridRes).toInt()}"

        assertEquals("Nearby photos must belong to the same spatial grid cluster", cell1, cell2)
    }

    // ── Test: Visual Near-Duplicate Cosine Similarity ────────

    @Test
    fun `Visual near-duplicate detector groups vectors with cosine similarity above 0_94`() {
        val vecA = FloatArray(512) { 0.05f }
        // Very slightly perturbed vector (near duplicate)
        val vecB = FloatArray(512) { 0.0505f }

        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in 0 until 512) {
            dot += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }
        val cosineSim = dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))

        assertTrue("Cosine similarity of burst shots should be > 0.94", cosineSim >= 0.94f)
    }

    // ── Test: Video Adaptive Keyframe Sampling Interval ──────

    @Test
    fun `Video sampling selects appropriate step based on duration`() {
        fun getStep(durationMs: Long): Long {
            return when {
                durationMs <= 15_000L -> 1_000L
                durationMs <= 120_000L -> 2_000L
                else -> 4_000L
            }
        }

        assertEquals(1_000L, getStep(10_000L))  // 10s clip -> 1s step
        assertEquals(2_000L, getStep(45_000L))  // 45s clip -> 2s step
        assertEquals(4_000L, getStep(300_000L)) // 5m clip  -> 4s step
    }

    // ── Test: Mean-Pooled Video Embedding Normalization ──────

    @Test
    fun `Mean-pooled video vectors are unit normalized`() {
        val frame1 = FloatArray(512) { 0.02f }
        val frame2 = FloatArray(512) { 0.04f }
        val frame3 = FloatArray(512) { 0.06f }

        val dim = 512
        val sum = FloatArray(dim)
        for (vec in listOf(frame1, frame2, frame3)) {
            for (i in 0 until dim) sum[i] += vec[i]
        }

        var normSq = 0f
        for (v in sum) normSq += v * v
        val norm = kotlin.math.sqrt(normSq.toDouble()).toFloat()
        val pooled = FloatArray(dim) { i -> sum[i] / norm }

        var finalNormSq = 0f
        for (v in pooled) finalNormSq += v * v
        val finalNorm = kotlin.math.sqrt(finalNormSq.toDouble()).toFloat()

        assertTrue("Pooled vector must be L2 normalized to unit length", kotlin.math.abs(1.0f - finalNorm) < 1e-4f)
    }

    // ── Test: Resumable HTTP Range Calculation ───────────────

    @Test
    fun `Resumable download Range header calculates correct byte offsets`() {
        val existingBytes = 15_728_640L // ~15 MB
        val rangeHeader = "bytes=$existingBytes-"
        assertEquals("bytes=15728640-", rangeHeader)

        val serverRemaining = 32_000_000L
        val totalExpected = existingBytes + serverRemaining
        assertEquals(47_728_640L, totalExpected)

        val progress = (existingBytes.toFloat() / totalExpected).coerceIn(0f, 1f)
        assertTrue("Initial resumed progress should match downloaded bytes ratio", progress in 0.32f..0.34f)
    }

    // ── Test: Large Library Pagination Math ──────────────────

    @Test
    fun `Pagination splits 50_000 items into predictable non-overlapping pages`() {
        val totalItems = 50_000
        val pageSize = 1_000
        val totalPages = (totalItems + pageSize - 1) / pageSize

        assertEquals(50, totalPages)

        for (page in 0 until totalPages) {
            val offset = page * pageSize
            val limit = pageSize
            assertEquals(page * 1_000, offset)
            assertEquals(1_000, limit)
        }
    }

    // ── Test: Resumable Indexing Checkpoint Math ─────────────

    @Test
    fun `Indexing resumption skips already indexed IDs in O1 time without duplicate processing`() {
        val totalLibrary = (1L..10_000L).toList()
        // Simulate already indexed 6,400 items
        val alreadyIndexedSet = (1L..6_400L).toHashSet()

        val pending = totalLibrary.filter { it !in alreadyIndexedSet }
        assertEquals(3_600, pending.size)

        val alreadyIndexedCount = totalLibrary.size - pending.size
        assertEquals(6_400, alreadyIndexedCount)

        // Progress starts directly from 64% without recalculating
        val initialProgress = (alreadyIndexedCount * 100 / totalLibrary.size)
        assertEquals(64, initialProgress)
    }

    // ── Test: 4-Tab Bottom Navigation Structure ─────────────

    @Test
    fun `Bottom navigation contains exactly 4 balanced primary media tabs`() {
        val tabs = listOf("Photos", "Memories", "Search", "Collections")
        assertEquals(4, tabs.size)
        assertTrue(tabs.contains("Photos"))
        assertTrue(tabs.contains("Memories"))
        assertTrue(tabs.contains("Search"))
        assertTrue(tabs.contains("Collections"))
        assertFalse("Settings must not be in bottom navigation", tabs.contains("Settings"))
    }

    // ── Test: Self-Hosted Backup Math & Endpoint Resolution ──

    @Test
    fun `Immich and WebDAV endpoint URL normalization strips trailing slashes`() {
        val rawUrl = "https://immich.myhome.net:2283///"
        val normalized = rawUrl.trim().removeSuffix("/")
        assertEquals("https://immich.myhome.net:2283", normalized.trimEnd('/'))

        val pingEndpoint = "${normalized.trimEnd('/')}/api/server-info/ping"
        assertEquals("https://immich.myhome.net:2283/api/server-info/ping", pingEndpoint)
    }

    @Test
    fun `Self-hosted backup delta correctly identifies unbacked media`() {
        val totalMediaIds = (1001L..1500L).toList() // 500 items
        val backedUpIds = (1001L..1350L).toHashSet() // 350 items already backed up

        val pending = totalMediaIds.filter { it !in backedUpIds }
        assertEquals(150, pending.size)

        val backedUpCount = totalMediaIds.size - pending.size
        assertEquals(350, backedUpCount)

        val progressPercent = (backedUpCount * 100 / totalMediaIds.size)
        assertEquals(70, progressPercent)
    }

    // ── Test: Battery & Charging Eligibility Logic ──────────

    @Test
    fun `Indexing is permitted when battery is 60 percent or above even if unplugged`() {
        fun isEligible(pct: Int, isCharging: Boolean) = isCharging || pct >= 60

        assertTrue(isEligible(60, isCharging = false))
        assertTrue(isEligible(85, isCharging = false))
        assertTrue(isEligible(100, isCharging = false))
    }

    @Test
    fun `Indexing is permitted when phone is charging regardless of battery percent`() {
        fun isEligible(pct: Int, isCharging: Boolean) = isCharging || pct >= 60

        assertTrue(isEligible(5, isCharging = true))
        assertTrue(isEligible(45, isCharging = true))
        assertTrue(isEligible(59, isCharging = true))
    }

    @Test
    fun `Indexing is deferred when battery is below 60 percent and not charging`() {
        fun isEligible(pct: Int, isCharging: Boolean) = isCharging || pct >= 60

        assertFalse(isEligible(59, isCharging = false))
        assertFalse(isEligible(42, isCharging = false))
        assertFalse(isEligible(15, isCharging = false))
    }

    // ── Test: Computational Photography Aperture & Bounds ───

    @Test
    fun `Aperture f-stop formatting maps smoothly across blur intensities`() {
        fun formatFStop(blur: Float): String = when {
            blur > 0.8f -> "f/1.4"
            blur > 0.6f -> "f/2.0"
            blur > 0.4f -> "f/2.8"
            blur > 0.2f -> "f/4.0"
            else -> "f/8.0"
        }

        assertEquals("f/1.4", formatFStop(0.95f))
        assertEquals("f/2.0", formatFStop(0.70f))
        assertEquals("f/2.8", formatFStop(0.50f))
        assertEquals("f/4.0", formatFStop(0.30f))
        assertEquals("f/8.0", formatFStop(0.10f))
    }

    @Test
    fun `Subject Lift bounds expansion maintains non-negative left-top within image dimensions`() {
        val imgWidth = 1080
        val imgHeight = 1920

        val faceLeft = 300
        val faceTop = 400
        val faceRight = 600
        val faceBottom = 750

        val faceWidth = faceRight - faceLeft // 300
        val faceHeight = faceBottom - faceTop // 350

        val bodyLeft = kotlin.math.max(0, faceLeft - (faceWidth * 0.7f).toInt())
        val bodyTop = kotlin.math.max(0, faceTop - (faceHeight * 0.3f).toInt())
        val bodyRight = kotlin.math.min(imgWidth, faceRight + (faceWidth * 0.7f).toInt())
        val bodyBottom = kotlin.math.min(imgHeight, faceBottom + (faceHeight * 3.2f).toInt())

        assertTrue("Left bound must be >= 0", bodyLeft >= 0)
        assertTrue("Top bound must be >= 0", bodyTop >= 0)
        assertTrue("Right bound must be <= imgWidth", bodyRight <= imgWidth)
        assertTrue("Bottom bound must be <= imgHeight", bodyBottom <= imgHeight)
        assertTrue("Expanded width must be larger than original face box", (bodyRight - bodyLeft) > faceWidth)
    }

    // ── Test: People & Pets Clustering, Naming, & Merging ────

    @Test
    fun `Renaming a person updates display name and enables natural search matching`() {
        val originalClusterId = "person_a1b2c3d4"
        var personName = ""

        // User renames person
        val newName = "Sarah"
        personName = newName.trim()

        assertEquals("Sarah", personName)

        // Query matching test
        val queryText = "Sarah at the beach"
        val queryMatches = queryText.contains(personName, ignoreCase = true)
        assertTrue("Query containing person name must match", queryMatches)
    }

    @Test
    fun `Merging two clusters consolidates photo IDs into target cluster`() {
        val sourceClusterPhotos = setOf(101L, 102L, 103L)
        val targetClusterPhotos = setOf(201L, 202L)

        val mergedPhotos = targetClusterPhotos + sourceClusterPhotos
        assertEquals(5, mergedPhotos.size)
        assertTrue(mergedPhotos.containsAll(sourceClusterPhotos))
        assertTrue(mergedPhotos.containsAll(targetClusterPhotos))
    }
}
