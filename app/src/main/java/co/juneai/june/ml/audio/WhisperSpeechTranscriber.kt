package co.juneai.june.ml.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class SpeechSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 0.9f
)

/**
 * 100% on-device Automatic Speech Recognition (ASR) engine powered by OpenAI Whisper Tiny INT8 TFLite.
 * Transcribes spoken audio into timestamped text for instant search seeking.
 *
 * Privacy Invariant:
 * Zero audio bytes leave the device. All acoustic processing, Mel spectrogram extraction,
 * and autoregressive token decoding execute exclusively on-device.
 */
@Singleton
class WhisperSpeechTranscriber @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AutoCloseable {

    companion object {
        const val MODEL_NAME = "whisper-tiny.en.tflite"
        const val VOCAB_NAME = "filters_vocab_en.bin"
        const val CDN_MODEL_URL = "https://kiwi-model-server.kighurugerald.workers.dev/whisper-tiny.en.tflite"
        const val CDN_VOCAB_URL = "https://kiwi-model-server.kighurugerald.workers.dev/filters_vocab_en.bin"

        const val SAMPLE_RATE = 16000
        const val N_FFT = 400
        const val HOP_LENGTH = 160
        const val N_MELS = 80
        const val N_FFT_BINS = 201 // N_FFT / 2 + 1
        const val CHUNK_FRAMES = 3000 // 30 seconds of audio
        const val CHUNK_SAMPLES = CHUNK_FRAMES * HOP_LENGTH // 480,000 samples
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    // Mel filterbank weights: [N_MELS, N_FFT_BINS]
    private var melFilters: FloatArray? = null

    // BPE token vocabulary
    private val vocabList = ArrayList<String>()

    // Pre-computed Hann window for N_FFT = 400
    private val hannWindow = FloatArray(N_FFT) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / N_FFT))).toFloat()
    }

    // Pre-computed DFT trigonometric tables
    private val cosTable = Array(N_FFT_BINS) { k ->
        FloatArray(N_FFT) { n -> cos(2.0 * PI * k * n / N_FFT).toFloat() }
    }
    private val sinTable = Array(N_FFT_BINS) { k ->
        FloatArray(N_FFT) { n -> sin(2.0 * PI * k * n / N_FFT).toFloat() }
    }

    /**
     * Initializes the TFLite interpreter and loads the Mel filterbanks & vocabulary.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && interpreter != null) return@withContext true

        val modelFile = ensureAsset(MODEL_NAME, CDN_MODEL_URL)
        val vocabFile = ensureAsset(VOCAB_NAME, CDN_VOCAB_URL)

        if (modelFile == null || !modelFile.exists() || vocabFile == null || !vocabFile.exists()) {
            Timber.w("Whisper assets missing (model: ${modelFile?.exists()}, vocab: ${vocabFile?.exists()})")
            return@withContext false
        }

        try {
            loadVocabAndFilters(vocabFile)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            }

            val channel = FileInputStream(modelFile).channel
            val modelBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            interpreter = Interpreter(modelBuffer, options)
            isInitialized = true
            Timber.i("✓ WhisperSpeechTranscriber initialized successfully (${vocabList.size} tokens loaded)")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize WhisperSpeechTranscriber")
            false
        }
    }

    /**
     * Transcribes 16kHz mono PCM float audio into timestamped speech segments.
     * Automatically splits long audio into 30-second windows and skips silence.
     */
    suspend fun transcribe(audioSamples: FloatArray): List<SpeechSegment> = withContext(Dispatchers.Default) {
        if (audioSamples.isEmpty()) return@withContext emptyList()

        if (!isInitialized || interpreter == null) {
            val ok = initialize()
            if (!ok || interpreter == null) return@withContext emptyList()
        }

        val results = mutableListOf<SpeechSegment>()
        val totalSamples = audioSamples.size
        val windowHop = CHUNK_SAMPLES // 30-second stride

        var startSample = 0
        while (startSample < totalSamples) {
            val endSample = (startSample + CHUNK_SAMPLES).coerceAtMost(totalSamples)
            val chunkLen = endSample - startSample
            val chunk = FloatArray(chunkLen)
            System.arraycopy(audioSamples, startSample, chunk, 0, chunkLen)

            // Voice Activity Detection (VAD) energy check to skip non-speech/silence
            if (hasSpeechEnergy(chunk)) {
                val startMs = (startSample.toLong() * 1000L) / SAMPLE_RATE
                val endMs = (endSample.toLong() * 1000L) / SAMPLE_RATE

                val text = transcribeChunk(chunk)
                if (text.isNotBlank()) {
                    results.add(
                        SpeechSegment(
                            text = text,
                            startMs = startMs,
                            endMs = endMs,
                            confidence = 0.88f
                        )
                    )
                }
            }

            startSample += windowHop
        }

        results
    }

    /**
     * Runs Whisper inference on a single audio chunk (up to 30 seconds).
     */
    private fun transcribeChunk(chunk: FloatArray): String {
        val inter = interpreter ?: return ""
        val filters = melFilters ?: return ""

        try {
            // Compute Log-Mel Spectrogram [1, N_MELS (80), CHUNK_FRAMES (3000)]
            val mel = computeLogMelSpectrogram(chunk, filters)

            // Prepare TFLite input ByteBuffer
            val inputBuffer = ByteBuffer.allocateDirect(1 * N_MELS * CHUNK_FRAMES * 4).apply {
                order(ByteOrder.nativeOrder())
                rewind()
            }
            for (f in mel) {
                inputBuffer.putFloat(f)
            }
            inputBuffer.rewind()

            // Output tensor: [1, max_tokens] (INT32)
            val outputTensor = inter.getOutputTensor(0)
            val maxTokens = outputTensor.shape().lastOrNull() ?: 448
            val outputTokens = Array(1) { IntArray(maxTokens) }

            inter.run(inputBuffer, outputTokens)

            // Decode output tokens to text
            return decodeTokens(outputTokens[0])
        } catch (e: Exception) {
            Timber.e(e, "Error transcribing audio chunk")
            return ""
        }
    }

    /**
     * Computes the 80-channel Log-Mel Spectrogram matching OpenAI Whisper specification.
     */
    private fun computeLogMelSpectrogram(samples: FloatArray, filters: FloatArray): FloatArray {
        val melOutput = FloatArray(N_MELS * CHUNK_FRAMES)
        val numFrames = (samples.size / HOP_LENGTH).coerceAtMost(CHUNK_FRAMES)
        val frameWindow = FloatArray(N_FFT)
        val powerSpectrum = FloatArray(N_FFT_BINS)

        var globalMax = -1e9f

        // Process available frames
        for (f in 0 until numFrames) {
            val frameOffset = f * HOP_LENGTH

            // 1. Windowing with Hann window
            for (n in 0 until N_FFT) {
                val sampleIdx = frameOffset + n
                val sample = if (sampleIdx < samples.size) samples[sampleIdx] else 0f
                frameWindow[n] = sample * hannWindow[n]
            }

            // 2. DFT for 201 frequency bins
            for (k in 0 until N_FFT_BINS) {
                val cosK = cosTable[k]
                val sinK = sinTable[k]
                var real = 0f
                var imag = 0f
                for (n in 0 until N_FFT) {
                    val w = frameWindow[n]
                    real += w * cosK[n]
                    imag -= w * sinK[n]
                }
                powerSpectrum[k] = real * real + imag * imag
            }

            // 3. Mel filterbank dot product
            for (m in 0 until N_MELS) {
                var melVal = 0f
                val filterOffset = m * N_FFT_BINS
                for (k in 0 until N_FFT_BINS) {
                    melVal += filters[filterOffset + k] * powerSpectrum[k]
                }
                val logMel = log10(max(melVal, 1e-5f))
                melOutput[m * CHUNK_FRAMES + f] = logMel
                if (logMel > globalMax) globalMax = logMel
            }
        }

        // Fill remaining frames with padding value
        val padVal = -1e5f
        for (m in 0 until N_MELS) {
            for (f in numFrames until CHUNK_FRAMES) {
                melOutput[m * CHUNK_FRAMES + f] = padVal
            }
        }

        // 4. Dynamic range normalization as per Whisper specification:
        // (log_mel - max + 8.0) / 4.0
        val clampedMax = max(globalMax - 8.0f, -100.0f)
        for (i in melOutput.indices) {
            val normalized = (melOutput[i] - clampedMax + 8.0f) / 4.0f
            melOutput[i] = normalized.coerceIn(-1.5f, 1.5f)
        }

        return melOutput
    }

    /**
     * Decodes integer BPE tokens into English text, filtering out special tokens.
     */
    private fun decodeTokens(tokens: IntArray): String {
        val sb = StringBuilder()
        for (token in tokens) {
            if (token < 0 || token >= vocabList.size) continue
            // Whisper special tokens are 50257+ (<|endoftext|>, <|startoftranscript|>, etc.)
            if (token >= 50256) {
                if (token == 50256 || token == 50257) break // End of text
                continue
            }
            val piece = vocabList[token]
            sb.append(piece)
        }

        // Clean up Byte-Pair Encoding space artifacts ('Ġ' or leading spaces)
        return sb.toString()
            .replace("Ġ", " ")
            .replace("Â", "")
            .replace("â", "")
            .replace(Regex("<\\|.*?\\|>"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Voice Activity Detection (VAD) energy threshold.
     */
    private fun hasSpeechEnergy(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return false
        var sumSquares = 0.0
        for (s in samples) {
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / samples.size)
        return rms >= 0.005 // Skip frames below audible noise floor
    }

    /**
     * Reads `filters_vocab_en.bin` binary structure:
     * Header: uint32 magic (0x5553454e), uint32 n_mel (80), uint32 n_fft_bins (201)
     * Float array: 80 * 201 = 16,080 floats
     * Vocab count: uint32 n_vocab
     * For each token: uint32 len, bytes[len]
     */
    private fun loadVocabAndFilters(file: File) {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = buffer.int
        val nMel = buffer.int
        val nFftBins = buffer.int

        if (nMel != N_MELS || nFftBins != N_FFT_BINS) {
            Timber.w("Unexpected Mel dimensions in vocab file: nMel=$nMel, nFftBins=$nFftBins")
        }

        val filterCount = nMel * nFftBins
        val filters = FloatArray(filterCount)
        for (i in 0 until filterCount) {
            filters[i] = buffer.float
        }
        this.melFilters = filters

        val nVocab = buffer.int
        vocabList.clear()
        vocabList.ensureCapacity(nVocab)

        for (i in 0 until nVocab) {
            val len = buffer.int
            val tokenBytes = ByteArray(len)
            buffer.get(tokenBytes)
            val tokenStr = String(tokenBytes, Charsets.UTF_8)
            vocabList.add(tokenStr)
        }

        Timber.i("Loaded Whisper filterbank ($filterCount weights) and $nVocab vocabulary tokens")
    }

    /**
     * Ensures an AI asset is present in the internal app storage.
     * Downloads on-demand from Cloudflare CDN if missing.
     */
    private fun ensureAsset(name: String, cdnUrl: String): File? {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val destFile = File(modelsDir, name)
        if (destFile.exists() && destFile.length() > 1000) {
            return destFile
        }

        Timber.i("Downloading Whisper asset $name from Cloudflare CDN: $cdnUrl")
        val tempFile = File(modelsDir, "$name.tmp")
        try {
            val url = URL(cdnUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.requestMethod = "GET"
            conn.connect()

            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                if (tempFile.renameTo(destFile)) {
                    Timber.i("✓ Whisper asset $name downloaded successfully (${destFile.length()} bytes)")
                    return destFile
                }
            } else {
                Timber.e("Failed to download $name, HTTP response code: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading Whisper asset $name")
            tempFile.delete()
        }

        return if (destFile.exists() && destFile.length() > 1000) destFile else null
    }

    override fun close() {
        try {
            interpreter?.close()
        } catch (_: Exception) {}
        interpreter = null
        isInitialized = false
    }
}
