package co.juneai.june.domain.repository

import co.juneai.june.data.model.ImageData

interface TrashManagerRepository {
    suspend fun moveToTrash(imageIds: List<Long>)
    suspend fun restore(imageIds: List<Long>)
    suspend fun getTrashBin(): List<ImageData>
    suspend fun cleanupOldTrashed()
    suspend fun getDaysUntilDelete(imageId: Long): Int?
    suspend fun permanentlyDelete(imageIds: List<Long>)
}