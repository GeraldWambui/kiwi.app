package co.juneai.june.data.repository

import co.juneai.june.data.local.daos.ImageEmbeddingDao
import co.juneai.june.data.model.ImageData
import co.juneai.june.data.search.toImageData
import co.juneai.june.domain.repository.TrashManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class TrashManagerRepositoryImpl(
    private val dao: ImageEmbeddingDao
): TrashManagerRepository {

    companion object {
        const val TRASH_RETENTION_DAYS = 10
        const val TRASH_RETENTION_MS = TRASH_RETENTION_DAYS * 24 * 60 * 60 * 1000L
    }

    override suspend fun moveToTrash(imageIds: List<Long>) {
        withContext(Dispatchers.IO) {
            dao.moveToTrash(imageIds, trashedAt = System.currentTimeMillis())
            Timber.Forest.i("✓ Moved ${imageIds.size} images to trash (10-day retention)")
        }
    }


    override suspend fun restore(imageIds: List<Long>) {
        withContext(Dispatchers.IO) {
            dao.restoreFromTrash(imageIds)
            Timber.Forest.i("✓ Restored ${imageIds.size} images from trash")
        }
    }

    override suspend fun getTrashBin(): List<ImageData> {
        return withContext(Dispatchers.IO) {
            dao.getTrashBin().map { it.toImageData() }
        }
    }


    override suspend fun cleanupOldTrashed() {
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - TRASH_RETENTION_MS
            dao.permanentlyDeleteOldTrashed(cutoffTime)
            Timber.Forest.i("✓ Cleaned up old trashed items")
        }
    }

    override suspend fun getDaysUntilDelete(imageId: Long): Int? {
        return withContext(Dispatchers.IO) {
            val entity = dao.getEmbeddingById(imageId) ?: return@withContext null
            if (!entity.isTrashed) return@withContext null

            val trashedAt = entity.trashedAt ?: return@withContext null
            val ageMs = System.currentTimeMillis() - trashedAt
            val ageDays = ageMs / (24 * 60 * 60 * 1000)
            val daysRemaining = TRASH_RETENTION_DAYS - ageDays.toInt()

            return@withContext daysRemaining.coerceAtLeast(0)
        }
    }


    override suspend fun permanentlyDelete(imageIds: List<Long>) {
        withContext(Dispatchers.IO) {
            dao.deleteByIds(imageIds)
            Timber.Forest.i("⚠️ Permanently deleted ${imageIds.size} images")
        }
    }
}