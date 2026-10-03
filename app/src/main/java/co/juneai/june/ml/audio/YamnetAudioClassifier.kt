package co.juneai.june.ml.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class AudioEvent(
    val label: String,
    val confidence: Float,
    val startMs: Long,
    val endMs: Long
)

data class VideoAudioClassificationResult(
    val events: List<AudioEvent>,
    val dominantTags: Set<String>,
    val summary: String
)

/**
 * High-performance on-device acoustic event classifier using Google YAMNet.
 * Classifies 521 audio categories (instruments, laughter, applause, pets, vehicles, nature).
 */
@Singleton
class YamnetAudioClassifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AutoCloseable {

    companion object {
        const val MODEL_NAME = "yamnet.tflite"
        const val CLASS_MAP_NAME = "yamnet_class_map.csv"
        const val CDN_URL = "https://kiwi-model-server.kighurugerald.workers.dev/yamnet.tflite"

        // YAMNet requires exactly 15,360 samples (0.96s at 16,000 Hz)
        const val WINDOW_SIZE = 15360
        // 50% temporal overlap (0.48s step)
        const val HOP_SIZE = 7680
        const val NUM_CLASSES = 521
        const val CONFIDENCE_THRESHOLD = 0.25f

        // Generic audio labels to ignore or suppress from search tags
        private val SUPPRESSED_LABELS = setOf(
            "Silence", "Sound", "Noise", "White noise", "Pink noise",
            "Static", "Inside, small room", "Inside, large room or hall",
            "Outside, rural or natural", "Outside, urban or manmade",
            "Reverberation", "Echo"
        )
    }

    private var interpreter: Interpreter? = null
    private val interpreterLock = Any()
    private val classLabels by lazy { loadClassMap() }

    private fun getModelFile(): File {
        return File(context.filesDir, "models/$MODEL_NAME")
    }

    fun isModelReady(): Boolean {
        val file = getModelFile()
        if (file.exists() && file.length() >= 3_000_000L) return true
        return try {
            context.assets.openFd(MODEL_NAME).use { it.declaredLength > 0 }
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureInterpreter(): Interpreter? {
        if (interpreter != null) return interpreter

        synchronized(interpreterLock) {
            if (interpreter != null) return interpreter

            try {
                val file = getModelFile()
                val mappedByteBuffer = if (file.exists() && file.length() >= 3_000_000L) {
                    FileInputStream(file).channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
                } else {
                    val fd = context.assets.openFd(MODEL_NAME)
                    FileInputStream(fd.fileDescriptor).channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength
                    )
                }

                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    setUseXNNPACK(true)
                }

                interpreter = Interpreter(mappedByteBuffer, options)
                Timber.d("YAMNet audio classifier initialized successfully")
            } catch (e: Exception) {
                Timber.w(e, "Failed to initialize YAMNet interpreter: ${e.message}")
            }
            return interpreter
        }
    }

    private fun loadClassMap(): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        try {
            val inputStream = try {
                context.assets.open(CLASS_MAP_NAME)
            } catch (_: Exception) {
                val file = File(context.filesDir, "models/$CLASS_MAP_NAME")
                if (file.exists()) FileInputStream(file) else null
            } ?: return emptyMap()

            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.forEachLine { line ->
                    if (line.isNotBlank() && !line.startsWith("index")) {
                        val parts = parseCsvLine(line)
                        if (parts.size >= 3) {
                            val index = parts[0].toIntOrNull()
                            val displayName = parts[2].trim()
                            if (index != null && displayName.isNotEmpty()) {
                                map[index] = displayName
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load YAMNet class map")
        }
        return map
    }

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

    /**
     * Classifies 16kHz mono audio samples into time-stamped acoustic events.
     */
    suspend fun classifyAudio(extractedAudio: ExtractedAudio): VideoAudioClassificationResult = withContext(Dispatchers.Default) {
        val samples = extractedAudio.samples
        if (samples.size < WINDOW_SIZE) {
            return@withContext VideoAudioClassificationResult(emptyList(), emptySet(), "")
        }

        val interp = ensureInterpreter() ?: run {
            Timber.w("YAMNet model not ready for classification")
            return@withContext VideoAudioClassificationResult(emptyList(), emptySet(), "")
        }

        val rawEvents = mutableListOf<AudioEvent>()
        val inputBuffer = ByteBuffer.allocateDirect(WINDOW_SIZE * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        val outputScores = Array(1) { FloatArray(NUM_CLASSES) }
        val outputEmbeddings = Array(1) { FloatArray(1024) }
        val outputSpectrogram = Array(1) { Array(96) { FloatArray(64) } }

        val outputMap = mapOf(
            0 to outputScores,
            1 to outputEmbeddings,
            2 to outputSpectrogram
        )

        var offset = 0
        while (offset + WINDOW_SIZE <= samples.size) {
            val startMs = (offset * 1000L) / extractedAudio.sampleRate
            val endMs = ((offset + WINDOW_SIZE) * 1000L) / extractedAudio.sampleRate

            inputBuffer.clear()
            for (i in 0 until WINDOW_SIZE) {
                inputBuffer.putFloat(samples[offset + i])
            }
            inputBuffer.rewind()

            synchronized(interpreterLock) {
                try {
                    interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)
                } catch (e: Exception) {
                    Timber.w(e, "YAMNet window inference error at ${startMs}ms")
                }
            }

            val scores = outputScores[0]
            var maxScore = 0f
            var maxIndex = -1

            for (i in 0 until NUM_CLASSES) {
                val score = scores[i]
                if (score > maxScore) {
                    maxScore = score
                    maxIndex = i
                }
            }

            if (maxScore >= CONFIDENCE_THRESHOLD && maxIndex >= 0) {
                val label = classLabels[maxIndex] ?: "Event #$maxIndex"
                if (!SUPPRESSED_LABELS.contains(label)) {
                    rawEvents.add(
                        AudioEvent(
                            label = label,
                            confidence = maxScore,
                            startMs = startMs,
                            endMs = endMs
                        )
                    )
                }
            }

            offset += HOP_SIZE
        }

        val aggregatedEvents = aggregateEvents(rawEvents)
        val dominantTags = aggregatedEvents.map { it.label }.toSet()

        val summary = aggregatedEvents.take(5).joinToString(", ") { ev ->
            val min = ev.startMs / 60000
            val sec = (ev.startMs % 60000) / 1000
            "${ev.label} (${min}:${String.format("%02d", sec)})"
        }

        VideoAudioClassificationResult(
            events = aggregatedEvents,
            dominantTags = dominantTags,
            summary = summary
        )
    }

    /**
     * Merges adjacent window detections of the same label into continuous events.
     */
    private fun aggregateEvents(events: List<AudioEvent>): List<AudioEvent> {
        if (events.isEmpty()) return emptyList()

        val merged = mutableListOf<AudioEvent>()
        var current = events.first()

        for (i in 1 until events.size) {
            val next = events[i]
            if (next.label == current.label && next.startMs <= current.endMs + 600L) {
                current = current.copy(
                    endMs = next.endMs,
                    confidence = max(current.confidence, next.confidence)
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        return merged.sortedByDescending { it.confidence }
    }

    override fun close() {
        synchronized(interpreterLock) {
            try {
                interpreter?.close()
                interpreter = null
            } catch (_: Exception) {}
        }
    }
}
