package co.juneai.june.ml.clip

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * High-performance on-device CLIP / MobileCLIP-S2 embedder.
 *
 * Performance & Stability design:
 *  - Models and Tokenizer are initialized LAZILY on first inference call so that
 *    constructing [ClipEmbedder] takes 0ms on the main thread and avoids ANRs.
 *  - Pre-allocated direct ByteBuffers are reused across calls under synchronization.
 *  - Dynamic fallback: loads downloaded MobileCLIP-S2 from internal storage (`files/models/`),
 *    or fallback asset models if present.
 *  - Non-crashing graceful degradation: returns empty 512-d zero vector if model is downloading or not ready.
 *  - SIMD-friendly L2 normalization for vector embeddings.
 */
class ClipEmbedder @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AutoCloseable {

    companion object {
        const val DIM = 512

        private const val S2_MODEL = "mobileclip_s2.tflite"
        private const val IMG_MODEL = "clip_image_dynamic.tflite"
        private const val TXT_MODEL = "clip_text_dynamic.tflite"
    }

    private val imageProcessor = ClipImageProcessor()
    private val tokenizer by lazy { ClipTokenizer(context) }

    @Volatile
    private var imgInterp: Interpreter? = null

    @Volatile
    private var txtInterp: Interpreter? = null

    private val interpLock = Any()

    // ── Pre-allocated I/O buffers ─────────────────────────────────────────────
    private val imgInputBuf: ByteBuffer = ByteBuffer
        .allocateDirect(ClipImageProcessor.INPUT_SIZE * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val imgOutputBuf: ByteBuffer = ByteBuffer
        .allocateDirect(DIM * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val txtInputIds: ByteBuffer = ByteBuffer
        .allocateDirect(ClipTokenizer.MAX_LEN * Int.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val txtAttnMask: ByteBuffer = ByteBuffer
        .allocateDirect(ClipTokenizer.MAX_LEN * Int.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val txtOutputBuf: ByteBuffer = ByteBuffer
        .allocateDirect(DIM * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val imgOutputMap = mutableMapOf<String, Any>()
    private val txtOutputMap = mutableMapOf<String, Any>()

    fun isModelReady(): Boolean {
        val s2 = File(context.filesDir, "models/$S2_MODEL")
        if (s2.exists() && s2.length() >= 10_000_000L) return true
        val img = File(context.filesDir, "models/$IMG_MODEL")
        if (img.exists() && img.length() > 0) return true

        return try {
            context.assets.openFd(S2_MODEL).use { it.declaredLength > 0 }
        } catch (_: Exception) {
            try {
                context.assets.openFd(IMG_MODEL).use { it.declaredLength > 0 }
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun resolveModelFileName(preferred: String): String {
        val s2 = File(context.filesDir, "models/$S2_MODEL")
        if (s2.exists() && s2.length() >= 10_000_000L) return S2_MODEL
        val pref = File(context.filesDir, "models/$preferred")
        if (pref.exists() && pref.length() > 0) return preferred

        return try {
            context.assets.openFd(S2_MODEL).close()
            S2_MODEL
        } catch (_: Exception) {
            preferred
        }
    }

    private fun getOrInitImageInterpreter(): Interpreter {
        imgInterp?.let { return it }
        return synchronized(interpLock) {
            imgInterp ?: run {
                val opts = Interpreter.Options().apply {
                    numThreads = 4
                    useXNNPACK = true
                }
                val modelName = resolveModelFileName(IMG_MODEL)
                val interp = Interpreter(loadModel(context, modelName), opts)
                imgOutputMap["image_embeds"] = imgOutputBuf
                imgOutputMap["output_0"] = imgOutputBuf
                imgInterp = interp
                interp
            }
        }
    }

    private fun getOrInitTextInterpreter(): Interpreter {
        txtInterp?.let { return it }
        return synchronized(interpLock) {
            txtInterp ?: run {
                val opts = Interpreter.Options().apply {
                    numThreads = 4
                    useXNNPACK = true
                }
                val modelName = resolveModelFileName(TXT_MODEL)
                val interp = Interpreter(loadModel(context, modelName), opts)
                txtOutputMap["text_embeds"] = txtOutputBuf
                txtOutputMap["output_0"] = txtOutputBuf
                txtInterp = interp
                interp
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Embeds [bitmap] into a unit-normalized [FloatArray] of size [DIM].
     *
     * Pixels are written directly into [imgInputBuf] via [ClipImageProcessor.processInto]
     * (no intermediate FloatArray).
     */
    fun embedImage(bitmap: Bitmap): FloatArray {
        if (!isModelReady()) {
            Timber.w("ClipEmbedder: Model assets not ready yet")
            return FloatArray(DIM)
        }
        return try {
            val interp = getOrInitImageInterpreter()
            synchronized(interpLock) {
                imgInputBuf.clear()
                imageProcessor.processInto(bitmap, imgInputBuf.asFloatBuffer())
                imgInputBuf.rewind()

                imgOutputBuf.clear()
                try {
                    interp.run(imgInputBuf, imgOutputBuf)
                } catch (re: Exception) {
                    if (interp.signatureKeys.isNotEmpty()) {
                        interp.runSignature(mapOf("args_0" to imgInputBuf), imgOutputMap)
                    } else throw re
                }

                imgOutputBuf.readEmbedding().l2Normalize()
            }
        } catch (e: Exception) {
            Timber.w(e, "Image inference failed")
            FloatArray(DIM)
        }
    }

    /**
     * Embeds [text] into a unit-normalized [FloatArray] of size [DIM].
     */
    fun embedText(text: String): FloatArray {
        if (!isModelReady()) {
            Timber.w("ClipEmbedder: Model assets not ready yet")
            return FloatArray(DIM)
        }
        return try {
            val interp = getOrInitTextInterpreter()
            val (ids, mask) = tokenizer.encode(text)

            synchronized(interpLock) {
                txtInputIds.clear()
                ids.forEach { txtInputIds.putInt(it) }
                txtInputIds.rewind()

                txtAttnMask.clear()
                mask.forEach { txtAttnMask.putInt(it) }
                txtAttnMask.rewind()

                txtOutputBuf.clear()
                try {
                    interp.runSignature(
                        mapOf(
                            "input_ids" to txtInputIds,
                            "attention_mask" to txtAttnMask,
                        ),
                        txtOutputMap,
                    )
                } catch (re: Exception) {
                    try {
                        interp.runSignature(mapOf("args_1" to txtInputIds), txtOutputMap)
                    } catch (_: Exception) {
                        interp.run(txtInputIds, txtOutputBuf)
                    }
                }

                txtOutputBuf.readEmbedding().l2Normalize()
            }
        } catch (e: Exception) {
            Timber.w(e, "Text inference failed")
            FloatArray(DIM)
        }
    }

    override fun close() {
        synchronized(interpLock) {
            imgInterp?.close()
            txtInterp?.close()
            imgInterp = null
            txtInterp = null
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun ByteBuffer.readEmbedding(): FloatArray {
        rewind()
        return FloatArray(DIM) { getFloat() }
    }

    private fun FloatArray.l2Normalize(): FloatArray {
        var sumSq = 0.0
        for (v in this) sumSq += v * v
        val norm = sqrt(sumSq).toFloat()
        if (norm > 1e-8f) {
            val inv = 1f / norm
            for (i in indices) this[i] *= inv
        }
        return this
    }

    private fun loadModel(context: Context, fileName: String): MappedByteBuffer {
        val customFile = File(context.filesDir, "models/$fileName")
        if (customFile.exists() && customFile.length() > 0) {
            return FileInputStream(customFile).channel.map(
                FileChannel.MapMode.READ_ONLY,
                0,
                customFile.length()
            )
        }
        val fd = context.assets.openFd(fileName)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength,
        )
    }
}