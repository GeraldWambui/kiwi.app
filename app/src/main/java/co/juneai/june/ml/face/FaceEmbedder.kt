package co.juneai.june.ml.face

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * MobileFaceNet 128-dimensional face embedding extractor.
 * Takes a 112x112 cropped face bitmap and outputs an L2-normalized 128-dim FloatArray.
 */
@Singleton
class FaceEmbedder @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AutoCloseable {

    companion object {
        const val EMBEDDING_DIM = 128
        const val INPUT_WIDTH = 112
        const val INPUT_HEIGHT = 112
        private const val MODEL_NAME = "mobilefacenet.tflite"
    }

    @Volatile
    private var interpreter: Interpreter? = null
    private val initLock = Any()

    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 * INPUT_HEIGHT * INPUT_WIDTH * 3 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val outputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 * EMBEDDING_DIM * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val intValues = IntArray(INPUT_WIDTH * INPUT_HEIGHT)

    private fun getOrInitInterpreter(): Interpreter {
        interpreter?.let { return it }
        return synchronized(initLock) {
            interpreter ?: run {
                val opts = Interpreter.Options().apply {
                    numThreads = 2
                    useXNNPACK = true
                }
                val interp = Interpreter(loadModel(context, MODEL_NAME), opts)
                interpreter = interp
                interp
            }
        }
    }

    /**
     * Extracts 128-d L2-normalized embedding from a cropped face bitmap.
     */
    fun embedFace(faceBitmap: Bitmap): FloatArray {
        val interp = getOrInitInterpreter()
        val scaled = if (faceBitmap.width != INPUT_WIDTH || faceBitmap.height != INPUT_HEIGHT) {
            Bitmap.createScaledBitmap(faceBitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
        } else {
            faceBitmap
        }

        return synchronized(initLock) {
            inputBuffer.clear()
            scaled.getPixels(intValues, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)

            for (pixel in intValues) {
                // MobileFaceNet normalization: (x - 127.5) / 128.0 -> range [-1, 1]
                val r = ((pixel shr 16 and 0xFF) - 127.5f) / 128f
                val g = ((pixel shr 8 and 0xFF) - 127.5f) / 128f
                val b = ((pixel and 0xFF) - 127.5f) / 128f

                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }

            inputBuffer.rewind()
            outputBuffer.clear()

            interp.run(inputBuffer, outputBuffer)

            val embedding = FloatArray(EMBEDDING_DIM)
            outputBuffer.rewind()
            outputBuffer.asFloatBuffer().get(embedding)

            if (scaled != faceBitmap) {
                scaled.recycle()
            }

            l2Normalize(embedding)
        }
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sum = 0.0
        for (v in vec) sum += v * v
        val norm = sqrt(sum).toFloat()
        if (norm > 1e-8f) {
            val inv = 1f / norm
            for (i in vec.indices) vec[i] *= inv
        }
        return vec
    }

    private fun loadModel(context: Context, fileName: String): MappedByteBuffer {
        val fd = context.assets.openFd(fileName)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    override fun close() {
        synchronized(initLock) {
            interpreter?.close()
            interpreter = null
        }
    }
}
