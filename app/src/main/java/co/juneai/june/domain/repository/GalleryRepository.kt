package co.juneai.june.domain.repository

import co.juneai.june.data.model.FolderData
import co.juneai.june.data.model.ImageData
import co.juneai.june.data.model.SearchResult

interface GalleryRepository {
    suspend fun getAllImages(): List<ImageData>
    suspend fun getFolders(): List<FolderData>
    suspend fun getImagesByFolder(folderName: String): List<ImageData>
    suspend fun searchByText(query: String): List<SearchResult>
    suspend fun searchByPrimaryObject(objectName: String): List<SearchResult>
    suspend fun moveToTrash(imageIds: List<Long>)
    suspend fun restoreFromTrash(imageIds: List<Long>)
    suspend fun getTrash(): List<ImageData>
    suspend fun permanentlyDelete(imageIds: List<Long>)
    suspend fun getDaysUntilDelete(imageId: Long): Int?
}