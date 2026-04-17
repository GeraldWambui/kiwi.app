package co.juneai.june.domain.repository

import android.graphics.Bitmap

interface EmbeddingProcessor {
    suspend fun getImageEmbedding(bitmap: Bitmap): FloatArray
    suspend fun getTextEmbedding(text: String): FloatArray
    fun isReady(): Boolean
}