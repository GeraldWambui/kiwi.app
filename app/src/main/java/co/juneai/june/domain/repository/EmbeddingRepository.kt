package co.juneai.june.domain.repository

import co.juneai.june.data.local.entities.MediaEmbeddingEntity

interface EmbeddingRepository {
    suspend fun saveEmbedding(embedding: MediaEmbeddingEntity)
    suspend fun getEmbedding(mediaId: Long): MediaEmbeddingEntity?
    suspend fun getAllEmbeddings(): List<MediaEmbeddingEntity>
    suspend fun deleteEmbedding(mediaId: Long)
}