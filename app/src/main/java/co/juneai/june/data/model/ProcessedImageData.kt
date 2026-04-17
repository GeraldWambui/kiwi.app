package co.juneai.june.data.model

/**
 * Data class for processed image results
 */
data class ProcessedImageData(
    val embedding512: FloatArray,
    val embedding128: FloatArray,
    val semanticTags: List<String>,
    val primaryObject: String?,
    val objectConfidence: Float,
    val faceCount: Int,
    val ocrText: String?,
    val ocrTextHash: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProcessedImageData

        if (!embedding512.contentEquals(other.embedding512)) return false
        if (!embedding128.contentEquals(other.embedding128)) return false
        if (semanticTags != other.semanticTags) return false
        if (primaryObject != other.primaryObject) return false
        if (objectConfidence != other.objectConfidence) return false
        if (faceCount != other.faceCount) return false
        if (ocrText != other.ocrText) return false
        if (ocrTextHash != other.ocrTextHash) return false

        return true
    }

    override fun hashCode(): Int {
        var result = embedding512.contentHashCode()
        result = 31 * result + embedding128.contentHashCode()
        result = 31 * result + semanticTags.hashCode()
        result = 31 * result + (primaryObject?.hashCode() ?: 0)
        result = 31 * result + objectConfidence.hashCode()
        result = 31 * result + faceCount
        result = 31 * result + (ocrText?.hashCode() ?: 0)
        result = 31 * result + (ocrTextHash?.hashCode() ?: 0)
        return result
    }
}