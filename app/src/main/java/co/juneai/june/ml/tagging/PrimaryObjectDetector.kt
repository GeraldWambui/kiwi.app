package co.juneai.june.ml.tagging

import timber.log.Timber
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.sqrt

/**
 * PRIMARY OBJECT DETECTION
 * Prevents false positives like shoes in background of portraits
 */
class PrimaryObjectDetector @Inject constructor() {
    
    fun detectPrimaryObject(
        imageEmbedding: FloatArray,
        detectedObjects: Map<String, Float>,
        faceCount: Int,
        hasOCR: Boolean,
        ocrDominance: Float = 0f,
        textEncoder: (String) -> FloatArray
    ): Pair<String?, Float> {
        
        var finalScore = 0f
        var primaryObject: String? = null
        
        // Weight 1: Direct object confidence (30%)
        val topObject = detectedObjects.maxByOrNull { it.value }
        if (topObject != null) {
            finalScore = topObject.value
            primaryObject = topObject.key
        }
        
        // Weight 2: Face presence (35%) - faces override other objects
        if (faceCount > 0) {
            val faceEmbedding = textEncoder("a photo of a person")
            val faceSimilarity = cosineSimilarity(imageEmbedding, faceEmbedding)
            
            if (faceSimilarity > 0.65f && faceCount >= max(1, detectedObjects.size / 2)) {
                finalScore = faceSimilarity * 1.35f
                primaryObject = "person"
                Timber.d("✓ Primary: person (detected $faceCount faces)")
            }
        }
        
        // Weight 3: Scene detection
        if (primaryObject != null && isScene(primaryObject)) {
            finalScore *= 0.85f
        }
        
        // Weight 4: OCR dominance (15%)
        if (hasOCR && ocrDominance > 0.7f) {
            primaryObject = "document"
            finalScore = ocrDominance * 1.2f
            Timber.d("✓ Primary: document")
        }
        
        val confidence = (finalScore / 2.0f).coerceIn(0f, 1f)
        
        return primaryObject to confidence
    }
    
    private fun isScene(label: String): Boolean {
        return label in listOf(
            "landscape", "nature", "outdoor", "indoor",
            "room", "wall", "floor", "sky", "water"
        )
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denom = (sqrt(normA) * sqrt(normB))
        return if (denom > 0f) dot / denom else 0f
    }
}
