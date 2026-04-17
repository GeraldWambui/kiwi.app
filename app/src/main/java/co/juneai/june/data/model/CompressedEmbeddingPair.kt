package co.juneai.june.data.model

data class CompressedEmbeddingPair(
    val embedding128: ByteArray,
    val imageId: Long
)