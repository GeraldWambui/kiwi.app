package co.juneai.june.data.repository

class PCACompressor(private val nComponents: Int = 64) {
    fun compress(embedding: FloatArray): FloatArray {
        return if (nComponents >= embedding.size) {
            embedding.clone()
        } else {
            embedding.copyOf(nComponents)
        }
    }

    fun decompress(compressed: FloatArray, originalSize: Int): FloatArray {
        return FloatArray(originalSize) { i ->
            if (i < compressed.size) compressed[i] else 0f
        }
    }
}