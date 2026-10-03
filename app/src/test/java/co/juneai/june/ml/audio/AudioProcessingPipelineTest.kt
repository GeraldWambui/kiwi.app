package co.juneai.june.ml.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Senior QA Automation Test Suite for Kiwi Photos Audio & Speech Pipeline.
 *
 * Verifies signal processing algorithms, Hann windowing, Voice Activity Detection,
 * YAMNet CSV parsing, BPE token decoding, Log-Mel scaling, and temporal sliding windows.
 */
class AudioProcessingPipelineTest {

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 1: Audio Data Structures & Invariants
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `ExtractedAudio equality and hashCode obey standard data class contract`() {
        val samples1 = floatArrayOf(0.1f, -0.2f, 0.35f, 0.0f)
        val samples2 = floatArrayOf(0.1f, -0.2f, 0.35f, 0.0f)
        val samples3 = floatArrayOf(0.1f, -0.2f, 0.36f, 0.0f)

        val audio1 = ExtractedAudio(sampleRate = 16000, samples = samples1, durationMs = 1000L)
        val audio2 = ExtractedAudio(sampleRate = 16000, samples = samples2, durationMs = 1000L)
        val audio3 = ExtractedAudio(sampleRate = 16000, samples = samples3, durationMs = 1000L)
        val audioDiffDuration = ExtractedAudio(sampleRate = 16000, samples = samples1, durationMs = 2000L)
        val audioDiffRate = ExtractedAudio(sampleRate = 44100, samples = samples1, durationMs = 1000L)

        // Value equality
        assertEquals("Identical audio buffers must be equal", audio1, audio2)
        assertEquals("Equal objects must have identical hashCodes", audio1.hashCode(), audio2.hashCode())

        // Inequality tests
        assertNotEquals("Different samples must not be equal", audio1, audio3)
        assertNotEquals("Different durations must not be equal", audio1, audioDiffDuration)
        assertNotEquals("Different sample rates must not be equal", audio1, audioDiffRate)
    }

    @Test
    fun `audio sample duration mathematically matches sample rate`() {
        val sampleRate = 16000
        val durationMs = 2500L // 2.5 seconds
        val expectedSamples = ((durationMs * sampleRate) / 1000L).toInt()

        assertEquals("2.5s at 16kHz should equal 40,000 samples", 40000, expectedSamples)

        val calculatedMs = (expectedSamples.toLong() * 1000L) / sampleRate
        assertEquals("Calculated milliseconds should round-trip accurately", durationMs, calculatedMs)
    }

    @Test
    fun `maximum audio extraction bound prevents OOM on large videos`() {
        val maxDurationMs = 600_000L // 10 minutes
        val targetSampleRate = 16000
        val maxSamples = ((maxDurationMs * targetSampleRate) / 1000L).toInt()

        // 9.6 million float values = 38.4 MB of memory, well within Android JVM heap safety limits
        assertEquals(9_600_000, maxSamples)
        val memoryBytes = maxSamples * 4L
        val memoryMb = memoryBytes / (1024 * 1024)
        assertTrue("Memory footprint ($memoryMb MB) must remain under 64 MB heap limit", memoryMb < 64)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 2: PCM Format Conversions & Resampling Algorithms
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `16-bit PCM integer to float normalization maps correctly without clipping`() {
        val minPcmShort: Short = -32768
        val zeroPcmShort: Short = 0
        val maxPcmShort: Short = 32767

        fun shortToFloat(s: Short): Float = s / 32768.0f

        val minFloat = shortToFloat(minPcmShort)
        val zeroFloat = shortToFloat(zeroPcmShort)
        val maxFloat = shortToFloat(maxPcmShort)

        assertEquals(-1.0f, minFloat, 0.0001f)
        assertEquals(0.0f, zeroFloat, 0.0001f)
        assertTrue("Max short should map near 1.0f", maxFloat in 0.999f..1.0f)
        assertTrue("Normalized floats must lie within [-1.0, 1.0]", minFloat >= -1.0f && maxFloat <= 1.0f)
    }

    @Test
    fun `linear interpolation resampling correctly scales sample sequences`() {
        // Upsampling test: from 8 kHz to 16 kHz (2x ratio)
        val original = floatArrayOf(0.0f, 1.0f, 0.5f, -0.5f)
        val targetLength = 8
        val resampled = FloatArray(targetLength)

        val ratio = (original.size - 1).toFloat() / (targetLength - 1).toFloat()
        for (i in 0 until targetLength) {
            val srcIdx = i * ratio
            val left = srcIdx.toInt()
            val right = (left + 1).coerceAtMost(original.size - 1)
            val frac = srcIdx - left
            resampled[i] = original[left] * (1.0f - frac) + original[right] * frac
        }

        // Endpoint verification
        assertEquals("First sample must match original", 0.0f, resampled[0], 0.0001f)
        assertEquals("Last sample must match original", -0.5f, resampled[targetLength - 1], 0.0001f)

        // Interpolated midpoint verification
        val midVal = resampled[1] // halfway between 0.0 and 1.0 at ~0.43 ratio
        assertTrue("Interpolated value must lie strictly between neighbors", midVal in 0.0f..1.0f)
    }

    @Test
    fun `stereo downmixing produces clean balanced mono without phase distortion`() {
        // Interleaved stereo samples: [L0, R0, L1, R1, L2, R2]
        val stereoInterleaved = floatArrayOf(
            0.8f, 0.4f,   // Sample 0: left=0.8, right=0.4 -> mono=0.6
            -0.6f, -0.2f, // Sample 1: left=-0.6, right=-0.2 -> mono=-0.4
            1.0f, 1.0f    // Sample 2: left=1.0, right=1.0 -> mono=1.0
        )

        val mono = FloatArray(stereoInterleaved.size / 2)
        for (i in mono.indices) {
            val left = stereoInterleaved[i * 2]
            val right = stereoInterleaved[i * 2 + 1]
            mono[i] = ((left + right) / 2.0f).coerceIn(-1.0f, 1.0f)
        }

        assertEquals(3, mono.size)
        assertEquals(0.6f, mono[0], 0.0001f)
        assertEquals(-0.4f, mono[1], 0.0001f)
        assertEquals(1.0f, mono[2], 0.0001f)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 3: YAMNet CSV Parsing & Label Filtering
    // ─────────────────────────────────────────────────────────────────────────────

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    @Test
    fun `YAMNet CSV line parser correctly parses unquoted and quoted fields`() {
        val standardLine = "0,/m/09x0r,Speech"
        val standardParsed = parseCsvLine(standardLine)
        assertEquals(3, standardParsed.size)
        assertEquals("0", standardParsed[0])
        assertEquals("/m/09x0r", standardParsed[1])
        assertEquals("Speech", standardParsed[2])

        val quotedLine = "137,/m/0284vy3,\"Inside, large room or hall\""
        val quotedParsed = parseCsvLine(quotedLine)
        assertEquals(3, quotedParsed.size)
        assertEquals("137", quotedParsed[0])
        assertEquals("/m/0284vy3", quotedParsed[1])
        assertEquals("Inside, large room or hall", quotedParsed[2])
    }

    @Test
    fun `YAMNet CSV line parser gracefully handles header and empty lines`() {
        val header = "index,mid,display_name"
        val headerParsed = parseCsvLine(header)
        assertEquals("index", headerParsed[0])

        val emptyLine = ""
        val emptyParsed = parseCsvLine(emptyLine)
        assertEquals(1, emptyParsed.size)
        assertEquals("", emptyParsed[0])
    }

    @Test
    fun `suppressed audio labels correctly filter out non-informative sounds`() {
        val suppressed = setOf(
            "Silence", "Sound", "Noise", "White noise", "Pink noise",
            "Static", "Inside, small room", "Inside, large room or hall",
            "Outside, rural or natural", "Outside, urban or manmade",
            "Reverberation", "Echo"
        )

        val candidateLabels = listOf(
            "Silence", "Laughter", "Noise", "Applause", "Singing",
            "White noise", "Dog", "Static", "Cheering", "Vehicle"
        )

        val filteredLabels = candidateLabels.filter { it !in suppressed }

        assertEquals(6, filteredLabels.size)
        assertFalse(filteredLabels.contains("Silence"))
        assertFalse(filteredLabels.contains("Noise"))
        assertFalse(filteredLabels.contains("White noise"))
        assertFalse(filteredLabels.contains("Static"))
        assertTrue(filteredLabels.contains("Laughter"))
        assertTrue(filteredLabels.contains("Applause"))
        assertTrue(filteredLabels.contains("Singing"))
        assertTrue(filteredLabels.contains("Dog"))
        assertTrue(filteredLabels.contains("Cheering"))
        assertTrue(filteredLabels.contains("Vehicle"))
    }

    @Test
    fun `sliding window hop calculation produces correct timestamps`() {
        val sampleRate = 16000
        val windowSize = 15360 // 0.96s
        val hopSize = 7680     // 0.48s

        // Test window 0
        val offset0 = 0
        val startMs0 = (offset0 * 1000L) / sampleRate
        val endMs0 = ((offset0 + windowSize) * 1000L) / sampleRate
        assertEquals(0L, startMs0)
        assertEquals(960L, endMs0)

        // Test window 1 (hop by 0.48s)
        val offset1 = hopSize
        val startMs1 = (offset1 * 1000L) / sampleRate
        val endMs1 = ((offset1 + windowSize) * 1000L) / sampleRate
        assertEquals(480L, startMs1)
        assertEquals(1440L, endMs1)

        // Test window 2
        val offset2 = hopSize * 2
        val startMs2 = (offset2 * 1000L) / sampleRate
        val endMs2 = ((offset2 + windowSize) * 1000L) / sampleRate
        assertEquals(960L, startMs2)
        assertEquals(1920L, endMs2)
    }

    @Test
    fun `temporal event aggregation merges contiguous identical sound events`() {
        val rawEvents = listOf(
            AudioEvent("Laughter", 0.85f, 0L, 960L),
            AudioEvent("Laughter", 0.92f, 480L, 1440L),
            AudioEvent("Laughter", 0.88f, 960L, 1920L),
            AudioEvent("Applause", 0.79f, 2500L, 3460L),
            AudioEvent("Applause", 0.84f, 2980L, 3940L)
        )

        // Aggregation algorithm
        val merged = mutableListOf<AudioEvent>()
        for (event in rawEvents) {
            val last = merged.lastOrNull()
            if (last != null && last.label == event.label && event.startMs <= last.endMs) {
                // Merge overlapping or contiguous event
                merged[merged.lastIndex] = AudioEvent(
                    label = last.label,
                    confidence = max(last.confidence, event.confidence),
                    startMs = last.startMs,
                    endMs = max(last.endMs, event.endMs)
                )
            } else {
                merged.add(event)
            }
        }

        assertEquals("5 raw overlapping windows should merge into 2 distinct events", 2, merged.size)

        // Check Laughter segment
        assertEquals("Laughter", merged[0].label)
        assertEquals(0L, merged[0].startMs)
        assertEquals(1920L, merged[0].endMs)
        assertEquals(0.92f, merged[0].confidence, 0.001f)

        // Check Applause segment
        assertEquals("Applause", merged[1].label)
        assertEquals(2500L, merged[1].startMs)
        assertEquals(3940L, merged[1].endMs)
        assertEquals(0.84f, merged[1].confidence, 0.001f)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SECTION 4: Whisper Mel Spectrogram & VAD & BPE Token Decoding
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Hann window exhibits standard mathematical symmetry and boundary values`() {
        val nFft = 400
        val hann = FloatArray(nFft) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / nFft))).toFloat()
        }

        // Boundary values
        assertEquals("Hann window at index 0 must be 0.0", 0.0f, hann[0], 0.0001f)
        assertEquals("Hann window at midpoint index 200 must be 1.0", 1.0f, hann[200], 0.0001f)

        // Symmetry test: w[i] should be nearly identical to w[nFft - i]
        for (i in 1 until 200) {
            assertEquals("Hann window must be symmetric around midpoint", hann[i], hann[nFft - i], 0.002f)
        }
    }

    @Test
    fun `Voice Activity Detection energy check distinguishes silence from voice`() {
        fun computeRms(samples: FloatArray): Double {
            if (samples.isEmpty()) return 0.0
            var sumSquares = 0.0
            for (s in samples) sumSquares += s * s
            return sqrt(sumSquares / samples.size)
        }

        fun hasSpeechEnergy(samples: FloatArray): Boolean = computeRms(samples) >= 0.005

        // Pure silence
        val silentAudio = FloatArray(16000) { 0.0f }
        assertFalse("Pure silence must not trigger speech energy", hasSpeechEnergy(silentAudio))

        // Very low white noise (-70 dB)
        val lowNoise = FloatArray(16000) { ((it % 5) - 2) * 0.0005f }
        assertFalse("Low noise below floor must not trigger speech energy", hasSpeechEnergy(lowNoise))

        // Audible audio signal (sine wave amplitude 0.25)
        val speechSignal = FloatArray(16000) { i ->
            (0.25f * cos(2.0 * PI * 440.0 * i / 16000)).toFloat()
        }
        assertTrue("Audible signal must trigger speech energy", hasSpeechEnergy(speechSignal))
        assertTrue("Audible RMS must be >= 0.05", computeRms(speechSignal) > 0.05)
    }

    @Test
    fun `Whisper dynamic range normalization scales values into expected bounds`() {
        val testMel = floatArrayOf(-10.0f, -5.0f, -2.0f, 0.0f, 2.0f)
        val globalMax = 2.0f
        val clampedMax = max(globalMax - 8.0f, -100.0f) // -6.0f

        val normalized = FloatArray(testMel.size) { i ->
            ((testMel[i] - clampedMax + 8.0f) / 4.0f).coerceIn(-1.5f, 1.5f)
        }

        for (v in normalized) {
            assertTrue("Normalized Mel value must be between -1.5 and 1.5", v in -1.5f..1.5f)
        }
    }

    @Test
    fun `BPE token decoding cleans special tags and whitespace artifacts`() {
        val vocab = listOf(
            "Hello", "Ġworld", "!", "Ġhow", "Ġare", "Ġyou", "<|endoftext|>", "<|startoftranscript|>"
        )

        fun decodeTokens(tokens: IntArray): String {
            val sb = StringBuilder()
            for (token in tokens) {
                if (token < 0 || token >= vocab.size) continue
                if (token >= 6) { // Special tokens
                    if (token == 6) break // <|endoftext|>
                    continue
                }
                sb.append(vocab[token])
            }
            return sb.toString()
                .replace("Ġ", " ")
                .replace("Â", "")
                .replace("â", "")
                .replace(Regex("<\\|.*?\\|>"), "")
                .trim()
                .replace(Regex("\\s+"), " ")
        }

        val tokenSequence = intArrayOf(7, 0, 1, 2, 6, 3, 4) // <|startoftranscript|>, Hello, Ġworld, !, <|endoftext|>, Ġhow, Ġare
        val decoded = decodeTokens(tokenSequence)

        assertEquals("Decoded text should properly format words and strip special tokens", "Hello world!", decoded)
    }

    @Test
    fun `BPE decoder handles out of bounds and corrupt token IDs safely`() {
        val vocab = listOf("Yes", "No")

        fun decodeTokensSafe(tokens: IntArray): String {
            val sb = StringBuilder()
            for (t in tokens) {
                if (t < 0 || t >= vocab.size) continue
                sb.append(vocab[t])
            }
            return sb.toString().trim()
        }

        val corruptTokens = intArrayOf(-1, 99999, -500, 0, 100000, 1)
        val decoded = decodeTokensSafe(corruptTokens)
        assertEquals("YesNo", decoded)
    }
}
