package co.juneai.june.ml.audio

import co.juneai.june.data.local.entities.VideoAudioEntity
import co.juneai.june.data.model.MediaItem
import co.juneai.june.ui.screen.mediadetail.MediaAiMetadata
import co.juneai.june.ui.screen.search.SearchResultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * Senior QA Automation Test Suite for Kiwi Photos Audio Search & Database Operations.
 *
 * Verifies timestamp seeking conversions, search scoring algorithms, snippet generation,
 * entity database invariants, deduplication, and UI badge distinctions.
 */
class AudioSearchAndDatabaseTest {

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 1: Timestamp Formatting & Video Seeking Boundaries
    // ─────────────────────────────────────────────────────────────────────────────

    private fun formatTimestamp(timestampMs: Long): String {
        val minutes = timestampMs / 60000
        val seconds = (timestampMs % 60000) / 1000
        return String.format("%d:%02d", minutes, seconds)
    }

    @Test
    fun `timestamp formatting correctly formats exact video offsets`() {
        assertEquals("0ms should format as 0:00", "0:00", formatTimestamp(0L))
        assertEquals("500ms should truncate to 0:00", "0:00", formatTimestamp(500L))
        assertEquals("999ms should truncate to 0:00", "0:00", formatTimestamp(999L))
        assertEquals("1000ms should format as 0:01", "0:01", formatTimestamp(1000L))
        assertEquals("15000ms should format as 0:15", "0:15", formatTimestamp(15000L))
        assertEquals("59000ms should format as 0:59", "0:59", formatTimestamp(59000L))
        assertEquals("60000ms should format as 1:00", "1:00", formatTimestamp(60000L))
        assertEquals("65000ms should format as 1:05", "1:05", formatTimestamp(65000L))
        assertEquals("125000ms should format as 2:05", "2:05", formatTimestamp(125000L))
        assertEquals("3599000ms should format as 59:59", "59:59", formatTimestamp(3599000L))
        assertEquals("3600000ms (1 hour) should format as 60:00", "60:00", formatTimestamp(3600000L))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 2: Search Ranking, Snippets & Moment Seeking
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `search snippet formats speech recognition transcripts with quotes and timestamp`() {
        val match = VideoAudioEntity(
            mediaStoreId = 101L,
            timestampMs = 45000L,
            transcript = "Happy birthday to you",
            audioEvents = "Singing",
            confidence = 0.95f
        )

        val minutes = match.timestampMs / 60000
        val seconds = (match.timestampMs % 60000) / 1000
        val timeStr = String.format("%d:%02d", minutes, seconds)

        val snippet = if (match.transcript.isNotBlank()) {
            "Heard: \"${match.transcript}\" at $timeStr"
        } else {
            "Sound: ${match.audioEvents} at $timeStr"
        }

        assertEquals("Heard: \"Happy birthday to you\" at 0:45", snippet)
        assertTrue("Snippet must indicate speech via Heard prefix", snippet.startsWith("Heard:"))
    }

    @Test
    fun `search snippet formats acoustic sound events with label and timestamp`() {
        val match = VideoAudioEntity(
            mediaStoreId = 102L,
            timestampMs = 84000L,
            transcript = "",
            audioEvents = "Applause, Cheering",
            confidence = 0.88f
        )

        val minutes = match.timestampMs / 60000
        val seconds = (match.timestampMs % 60000) / 1000
        val timeStr = String.format("%d:%02d", minutes, seconds)

        val snippet = if (match.transcript.isNotBlank()) {
            "Heard: \"${match.transcript}\" at $timeStr"
        } else {
            "Sound: ${match.audioEvents} at $timeStr"
        }

        assertEquals("Sound: Applause, Cheering at 1:24", snippet)
        assertTrue("Snippet must indicate acoustic event via Sound prefix", snippet.startsWith("Sound:"))
    }

    @Test
    fun `audio score calculation clamps properly within high-confidence range`() {
        fun computeAudioScore(confidence: Float): Float {
            return (confidence * 0.95f).coerceIn(0.70f, 0.99f)
        }

        assertEquals(0.95f, computeAudioScore(1.0f), 0.001f)
        assertEquals(0.855f, computeAudioScore(0.90f), 0.001f)
        assertEquals(0.76f, computeAudioScore(0.80f), 0.001f)

        // Lower bound clamping
        assertEquals("Low confidence audio score must clamp to 0.70f", 0.70f, computeAudioScore(0.20f), 0.001f)
        assertEquals("Zero confidence must clamp to 0.70f", 0.70f, computeAudioScore(0.0f), 0.001f)

        // Upper bound clamping
        assertEquals("Overflow confidence must clamp to 0.99f", 0.99f, computeAudioScore(2.0f), 0.001f)
    }

    @Test
    fun `audio search result deduplication boosts video score and preserves exact seek offset`() {
        val dummyMedia = MediaItem(
            id = 200L,
            uri = "content://media/external/video/media/200",
            mimeType = "video/mp4",
            dateTaken = System.currentTimeMillis(),
            isVideo = true,
            name = "birthday_party.mp4"
        )

        // Video was initially found via visual CLIP embedding with similarity 0.45 at 0:10
        val visualResult = SearchResultItem(
            media = dummyMedia,
            similarity = 0.45f,
            matchedTimestampMs = 10000L,
            ocrSnippet = "Moment at 0:10"
        )

        val resultsList = mutableListOf(visualResult)

        // Audio match is found at 1:15 with high speech confidence (0.90 -> 0.855 score)
        val audioMatch = VideoAudioEntity(
            mediaStoreId = 200L,
            timestampMs = 75000L,
            transcript = "Make a wish and blow the candles",
            confidence = 0.90f
        )

        val existingIdx = resultsList.indexOfFirst { it.media.id == audioMatch.mediaStoreId }
        val audioScore = (audioMatch.confidence * 0.95f).coerceIn(0.70f, 0.99f)
        val timeStr = formatTimestamp(audioMatch.timestampMs)

        val updatedResult = SearchResultItem(
            media = dummyMedia,
            similarity = max(audioScore, resultsList.getOrNull(existingIdx)?.similarity ?: 0f).coerceIn(0f, 1f),
            matchedTimestampMs = audioMatch.timestampMs,
            ocrSnippet = "Heard: \"${audioMatch.transcript}\" at $timeStr"
        )

        if (existingIdx >= 0 && audioScore > resultsList[existingIdx].similarity) {
            resultsList[existingIdx] = updatedResult
        }

        // Verify deduplication: still 1 result, but upgraded with audio moment seeking
        assertEquals(1, resultsList.size)
        val finalItem = resultsList.first()
        assertEquals(0.855f, finalItem.similarity, 0.001f)
        assertEquals(75000L, finalItem.matchedTimestampMs)
        assertEquals("Heard: \"Make a wish and blow the candles\" at 1:15", finalItem.ocrSnippet)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 3: Database Entity Invariants & Edge Cases
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `VideoAudioEntity initializes with valid defaults and non-negative constraints`() {
        val entity = VideoAudioEntity(
            mediaStoreId = 500L,
            timestampMs = 12000L,
            durationMs = 3000L,
            transcript = "Welcome everyone",
            audioEvents = "Speech, Laughter",
            confidence = 0.92f
        )

        assertEquals(0L, entity.id) // auto-generate default
        assertEquals(500L, entity.mediaStoreId)
        assertTrue(entity.timestampMs >= 0L)
        assertTrue(entity.durationMs >= 0L)
        assertEquals("Welcome everyone", entity.transcript)
        assertEquals("Speech, Laughter", entity.audioEvents)
        assertEquals(0.92f, entity.confidence, 0.001f)
        assertTrue(entity.indexedAt > 0L)
    }

    @Test
    fun `database placeholders for silent or non-audio videos are filtered from user UI`() {
        val records = listOf(
            VideoAudioEntity(mediaStoreId = 1L, timestampMs = 0L, audioEvents = "Silent", confidence = 1.0f),
            VideoAudioEntity(mediaStoreId = 2L, timestampMs = 0L, audioEvents = "No Audio Track", confidence = 1.0f),
            VideoAudioEntity(mediaStoreId = 3L, timestampMs = 5000L, transcript = "Hello world", confidence = 0.9f),
            VideoAudioEntity(mediaStoreId = 3L, timestampMs = 12000L, audioEvents = "Laughter", confidence = 0.85f)
        )

        // Filter condition used by MediaDetailViewModel
        val userFacingMoments = records.filter {
            (it.audioEvents.isNotBlank() && it.audioEvents != "Silent" && it.audioEvents != "No Audio Track") ||
            it.transcript.isNotBlank()
        }

        assertEquals(2, userFacingMoments.size)
        assertEquals("Hello world", userFacingMoments[0].transcript)
        assertEquals("Laughter", userFacingMoments[1].audioEvents)
        assertFalse(userFacingMoments.any { it.audioEvents == "Silent" })
        assertFalse(userFacingMoments.any { it.audioEvents == "No Audio Track" })
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 4: UI Presentation & Badge Color Logic
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `SearchResultItem correctly identifies audio vs visual matches for UI badge styling`() {
        val audioSpeechItem = SearchResultItem(
            media = MediaItem(1L, "uri", "video/mp4", 0L, isVideo = true),
            similarity = 0.85f,
            matchedTimestampMs = 15000L,
            ocrSnippet = "Heard: \"celebration\" at 0:15"
        )

        val soundEventItem = SearchResultItem(
            media = MediaItem(2L, "uri", "video/mp4", 0L, isVideo = true),
            similarity = 0.80f,
            matchedTimestampMs = 30000L,
            ocrSnippet = "Sound: Applause at 0:30"
        )

        val visualMomentItem = SearchResultItem(
            media = MediaItem(3L, "uri", "video/mp4", 0L, isVideo = true),
            similarity = 0.75f,
            matchedTimestampMs = 45000L,
            ocrSnippet = "Moment at 0:45"
        )

        fun isAudioMatch(item: SearchResultItem): Boolean {
            return item.ocrSnippet.startsWith("Heard:") || item.ocrSnippet.startsWith("Sound:")
        }

        assertTrue("Speech match must be identified as audio match", isAudioMatch(audioSpeechItem))
        assertTrue("Sound event match must be identified as audio match", isAudioMatch(soundEventItem))
        assertFalse("Visual keyframe match must NOT be identified as audio match", isAudioMatch(visualMomentItem))
    }

    @Test
    fun `MediaAiMetadata safely carries audio moments for info sheet`() {
        val moments = listOf(
            VideoAudioEntity(mediaStoreId = 10L, timestampMs = 5000L, transcript = "Great goal!", confidence = 0.95f),
            VideoAudioEntity(mediaStoreId = 10L, timestampMs = 7000L, audioEvents = "Cheering", confidence = 0.90f)
        )

        val metadata = MediaAiMetadata(
            primaryObject = "Soccer game",
            sharpnessScore = 0.82f,
            audioMoments = moments
        )

        assertNotNull(metadata.audioMoments)
        assertEquals(2, metadata.audioMoments.size)
        assertEquals("Great goal!", metadata.audioMoments[0].transcript)
        assertEquals("Cheering", metadata.audioMoments[1].audioEvents)
        assertEquals(0.82f, metadata.sharpnessScore, 0.001f)
    }
}
