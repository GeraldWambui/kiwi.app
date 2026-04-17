package co.juneai.june.data.repository

import co.juneai.june.data.local.db.GalleryDatabase
import co.juneai.june.data.local.entities.MediaEmbeddingEntity
import co.juneai.june.domain.repository.EmbeddingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmbeddingRepositoryImpl(
    private val database: GalleryDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : EmbeddingRepository {

    override suspend fun saveEmbedding(embedding: MediaEmbeddingEntity) = withContext(dispatcher) {
        database.mediaEmbeddingDao().insert(embedding)
    }

    override suspend fun getEmbedding(mediaId: Long): MediaEmbeddingEntity? = withContext(dispatcher) {
        database.mediaEmbeddingDao().getEmbedding(mediaId)
    }

    override suspend fun getAllEmbeddings(): List<MediaEmbeddingEntity> = withContext(dispatcher) {
        database.mediaEmbeddingDao().getAllEmbeddings()
    }

    override suspend fun deleteEmbedding(mediaId: Long) = withContext(dispatcher) {
        database.mediaEmbeddingDao().delete(mediaId)
    }
}