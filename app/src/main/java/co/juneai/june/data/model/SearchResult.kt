package co.juneai.june.data.model

data class SearchResult(
    val image: ImageData,
    val similarity: Float,
    val explanation: String,
    val matchedTags: List<String> = emptyList()
)