package co.juneai.june.data.repository

import co.juneai.june.data.local.daos.ImageEmbeddingDao
import co.juneai.june.data.model.FolderData
import co.juneai.june.data.model.ImageData
import co.juneai.june.data.model.SearchResult
import co.juneai.june.data.search.TwoStageSearchEngine
import co.juneai.june.data.search.toImageData
import co.juneai.june.domain.repository.GalleryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class GalleryRepositoryImpl @Inject constructor(
    private val dao: ImageEmbeddingDao,
    private val searchEngine: TwoStageSearchEngine,
    private val trashManager: TrashManagerRepositoryImpl
) : GalleryRepository {
    
    /**
     * Get all active images (not trashed, indexed)
     */
    override suspend fun getAllImages(): List<ImageData> {
        return withContext(Dispatchers.IO) {
            try {
                val entities = dao.getAllActiveEmbeddings()
                val images = entities.map { it.toImageData() }
                Timber.d("✓ Loaded ${images.size} active images")
                images
            } catch (e: Exception) {
                Timber.e(e, "Failed to load all images")
                emptyList()
            }
        }
    }
    
    /**
     * Get all folders with image counts
     */
    override suspend fun getFolders(): List<FolderData> {
        return withContext(Dispatchers.IO) {
            try {
                val folderNames = dao.getAllActiveFolders()
                
                val folders = folderNames.mapNotNull { folderName ->
                    val images = dao.getEmbeddingsByFolder(folderName)
                    if (images.isEmpty()) return@mapNotNull null
                    
                    FolderData(
                        name = folderName,
                        path = "",  // Would extract from first image
                        imageCount = images.size,
                        lastModified = images.maxOf { it.dateModified },
                        thumbnailPath = images.firstOrNull()?.filePath
                    )
                }.sortedByDescending { it.lastModified }
                
                Timber.d("✓ Loaded ${folders.size} folders")
                folders
            } catch (e: Exception) {
                Timber.e(e, "Failed to load folders")
                emptyList()
            }
        }
    }
    
    /**
     * Get images in specific folder
     */
    override suspend fun getImagesByFolder(folderName: String): List<ImageData> {
        return withContext(Dispatchers.IO) {
            try {
                val entities = dao.getEmbeddingsByFolder(folderName)
                val images = entities.map { it.toImageData() }
                Timber.d("✓ Loaded ${images.size} images from folder: $folderName")
                images
            } catch (e: Exception) {
                Timber.e(e, "Failed to load images from folder: $folderName")
                emptyList()
            }
        }
    }
    
    /**
     * Search by text query using two-stage search
     */
    override suspend fun searchByText(query: String): List<SearchResult> {
        return withContext(Dispatchers.Default) {
            try {
                // Would need ML pipeline here - handled in Step 10
                Timber.d("Searching for: $query")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                emptyList()
            }
        }
    }
    
    /**
     * Search by primary object (for natural language commands)
     */
    override suspend fun searchByPrimaryObject(objectName: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val query = "photos of $objectName"
                // Placeholder - will be enhanced in Step 10
                Timber.d("✓ Searching by primary object: $objectName")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Primary object search failed")
                emptyList()
            }
        }
    }
    
    /**
     * Move images to trash
     */
    override suspend fun moveToTrash(imageIds: List<Long>) {
        withContext(Dispatchers.IO) {
            try {
                trashManager.moveToTrash(imageIds)
                Timber.i("✓ Moved ${imageIds.size} images to trash")
            } catch (e: Exception) {
                Timber.e(e, "Failed to move to trash")
            }
        }
    }
    
    /**
     * Restore images from trash
     */
    override suspend fun restoreFromTrash(imageIds: List<Long>) {
        withContext(Dispatchers.IO) {
            try {
                trashManager.restore(imageIds)
                Timber.i("✓ Restored ${imageIds.size} images from trash")
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore from trash")
            }
        }
    }
    
    /**
     * Get trash bin contents
     */
    override suspend fun getTrash(): List<ImageData> {
        return withContext(Dispatchers.IO) {
            try {
                val images = trashManager.getTrashBin()
                Timber.d("✓ Loaded ${images.size} trashed images")
                images
            } catch (e: Exception) {
                Timber.e(e, "Failed to load trash")
                emptyList()
            }
        }
    }
    
    /**
     * Permanently delete images
     */
    override suspend fun permanentlyDelete(imageIds: List<Long>) {
        withContext(Dispatchers.IO) {
            try {
                trashManager.permanentlyDelete(imageIds)
                Timber.i("⚠️ Permanently deleted ${imageIds.size} images")
            } catch (e: Exception) {
                Timber.e(e, "Failed to permanently delete")
            }
        }
    }
    
    /**
     * Get days until permanent deletion
     */
    override suspend fun getDaysUntilDelete(imageId: Long): Int? {
        return withContext(Dispatchers.IO) {
            try {
                trashManager.getDaysUntilDelete(imageId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get days until delete")
                null
            }
        }
    }
}