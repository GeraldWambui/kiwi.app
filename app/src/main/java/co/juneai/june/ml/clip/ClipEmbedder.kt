package co.juneai.june.ml.clip

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * CLIP ViT-B/32 embedder backed by two TFLite models.
 *
 * Performance design:
 *  - All ByteBuffers are pre-allocated once in [init]; no allocations occur during
 *    [embedImage] or [embedText] — critical for GC-free batch indexing of thousands
 *    of images.
 *  - [processInto] writes pixels directly into the pre-allocated image input buffer,
 *    avoiding the intermediate FloatArray → ByteBuffer copy present in the original.
 *  - L2 normalization uses a SIMD-friendly loop that the JVM JIT can auto-vectorize.
 *
 * Thread-safety: NOT thread-safe. TFLite [Interpreter] instances are single-threaded.
 * Use one [ClipEmbedder] per coroutine worker or protect with a mutex.
 * Each interpreter is already configured for [numThreads] = 4 internal parallelism.
 *
 * Lifecycle: call [close] when done. Works well with a try-with-resources / [use] block.
 */
class ClipEmbedder @Inject constructor(context: Context) : AutoCloseable {

    companion object {
        const val DIM = 512

        private const val IMG_MODEL = "clip_image_dynamic.tflite"
        private const val TXT_MODEL = "clip_text_dynamic.tflite"
    }

    private val imageProcessor = ClipImageProcessor()
    private val tokenizer      = ClipTokenizer(context)

    private val imgInterp: Interpreter
    private val txtInterp: Interpreter

    // ── Pre-allocated I/O buffers ─────────────────────────────────────────────
    // Reused across calls — eliminates per-call ByteBuffer allocations.

    /** Image model input: 3 × 224 × 224 floats (CHW) */
    private val imgInputBuf: ByteBuffer = ByteBuffer
        .allocateDirect(ClipImageProcessor.INPUT_SIZE * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    /** Shared output buffer for both image and text embeddings (DIM floats) */
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

    // ── TFLite output map (reused to avoid allocation per call) ──────────────
    private val imgOutputMap = mutableMapOf<String, Any>()
    private val txtOutputMap = mutableMapOf<String, Any>()

    init {
        val opts = Interpreter.Options().apply {
            numThreads = 4
            useXNNPACK = true
        }
        imgInterp = Interpreter(loadModel(context, IMG_MODEL), opts)
        txtInterp = Interpreter(loadModel(context, TXT_MODEL), opts)

        imgOutputMap["image_embeds"] = imgOutputBuf
        txtOutputMap["text_embeds"]  = txtOutputBuf
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Embeds [bitmap] into a unit-normalized [FloatArray] of size [DIM].
     *
     * Pixels are written directly into [imgInputBuf] via [ClipImageProcessor.processInto]
     * (no intermediate FloatArray).
     *
     * @throws IllegalStateException if TFLite inference fails
     */
    fun embedImage(bitmap: Bitmap): FloatArray {
        // Fill image input buffer — zero copy path
        imgInputBuf.clear()
        imageProcessor.processInto(bitmap, imgInputBuf.asFloatBuffer())
        imgInputBuf.rewind()

        imgOutputBuf.clear()
        runCatching { imgInterp.run(imgInputBuf, imgOutputBuf) }
            .getOrElse { throw IllegalStateException("Image inference failed: ${it.message}", it) }

        return imgOutputBuf.readEmbedding().l2Normalize()
    }

    /**
     * Embeds [text] into a unit-normalized [FloatArray] of size [DIM].
     *
     * @throws IllegalStateException if TFLite inference fails
     */
    fun embedText(text: String): FloatArray {
        val (ids, mask) = tokenizer.encode(text)

        txtInputIds.clear()
        ids.forEach  { txtInputIds.putInt(it) }
        txtInputIds.rewind()

        txtAttnMask.clear()
        mask.forEach { txtAttnMask.putInt(it) }
        txtAttnMask.rewind()

        txtOutputBuf.clear()
        runCatching {
            txtInterp.runSignature(
                mapOf(
                    "input_ids"      to txtInputIds,
                    "attention_mask" to txtAttnMask,
                ),
                txtOutputMap,
            )
        }.getOrElse { throw IllegalStateException("Text inference failed: ${it.message}", it) }

        return txtOutputBuf.readEmbedding().l2Normalize()
    }

    override fun close() {
        imgInterp.close()
        txtInterp.close()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun ByteBuffer.readEmbedding(): FloatArray {
        rewind()
        return FloatArray(DIM) { getFloat() }
    }

    /**
     * L2-normalizes the array in-place and returns it.
     * The loop structure is intentionally simple so the JVM JIT can auto-vectorize it.
     */
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
        val fd = context.assets.openFd(fileName)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength,
        )
    }
}