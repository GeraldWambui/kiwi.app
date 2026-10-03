package co.juneai.june.ml.vector

import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.SingularValueDecomposition
import kotlin.math.sqrt

class PCACompression {
    
    private val outputDim = 128
    private var mean: DoubleArray? = null
    private var components: Array<DoubleArray>? = null
    
    fun fit(embeddings: List<FloatArray>) {
        if (embeddings.isEmpty()) return
        
        val data = embeddings.map { it.map { f -> f.toDouble() }.toDoubleArray() }.toTypedArray()
        
        mean = DoubleArray(data[0].size)
        for (i in data[0].indices) {
            mean!![i] = data.map { it[i] }.average()
        }
        
        val centered = data.map { row ->
            row.mapIndexed { i, v -> v - mean!![i] }.toDoubleArray()
        }.toTypedArray()
        
        val m = MatrixUtils.createRealMatrix(centered)
        val cov = m.transpose().multiply(m).scalarMultiply(1.0 / data.size)
        
        val svd = SingularValueDecomposition(cov)
        val eigenvectors = svd.ut.data
        
        components = eigenvectors.take(outputDim).toTypedArray()
    }
    
    fun compress(embedding: FloatArray): FloatArray {
        if (components == null || mean == null) return FloatArray(outputDim)
        
        val centered = embedding.mapIndexed { i, v -> v - mean!![i].toFloat() }.toFloatArray()
        val compressed = FloatArray(outputDim)
        
        for (i in 0 until outputDim) {
            compressed[i] = centered.indices.sumOf { j ->
                (centered[j] * components!![i][j])
            }.toFloat()
        }
        
        return normalize(compressed)
    }
    
    private fun normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0f) vector.map { it / norm }.toFloatArray() else vector
    }
}
