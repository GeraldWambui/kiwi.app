package co.juneai.june.data.model

data class EmbeddingTriplet(
    val embedding512: ByteArray,
    val embedding128: ByteArray,
    val imageId: Long
)