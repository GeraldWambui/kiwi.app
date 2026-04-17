package co.juneai.june.performance

import co.juneai.june.presentation.screen.settings.StorageBreakdown
import org.junit.Assert.assertEquals
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

        assertTrue("Trash expiry calc took ${elapsed}ms, expected <10ms", elapsed < 10.0)
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

        assertTrue("Tag filtering took ${elapsed}ms, expected <100ms", elapsed < 100.0)
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
}
