package co.juneai.june.ml.tagging

class ZeroShotTagger {
    
    private val tagPrompts = listOf(
        "a photo of a beach",
        "a photo of mountains",
        "a photo of food",
        "a photo of a person",
        "a photo of a dog",
        "a photo of a cat",
        "a photo of a car",
        "a photo of a building",
        "a photo of nature",
        "a photo of flowers",
        "a photo of water",
        "a photo of sunset",
        "a photo of sunrise",
        "a photo of snow",
        "a photo of rain",
        "a photo of trees",
        "a photo of sky",
        "a photo of clouds",
        "a document",
        "a screenshot",
        "text on paper",
        "a receipt",
        "a note",
        "handwriting"
    )
    
    fun generateTags(imageEmbedding: FloatArray, textEncoder: (String) -> FloatArray): List<String> {
        return try {
            val similarities = tagPrompts.map { prompt ->
                val tagEmbedding = textEncoder(prompt)
                val sim = cosineSimilarity(imageEmbedding, tagEmbedding)
                prompt to sim
            }
            
            similarities
                .sortedByDescending { it.second }
                .take(5)
                .map { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dotProduct = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
        }
        
        return dotProduct
    }
}
