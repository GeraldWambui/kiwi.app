package co.juneai.june.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import co.juneai.june.data.model.FolderData
import co.juneai.june.data.model.ImageData
import co.juneai.june.data.model.MediaItem
import co.juneai.june.data.model.SearchResult
import co.juneai.june.domain.repository.GalleryRepository
import co.juneai.june.ml.clip.ClipEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KiwiPhotosRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaStoreRepository: MediaStoreRepository,
    private val clipEmbedder: ClipEmbedder,
    private val embeddingBox: Box<ImageEmbedding>
) : GalleryRepository {

    /**
     * Get all active images (not trashed)
     */
    override suspend fun getAllImages(): List<ImageData> = withContext(Dispatchers.IO) {
        try {
            val trashedIds = embeddingBox.query(ImageEmbedding_.isTrashed.equal(true))
                .build()
                .find()
                .map { it.mediaStoreId }
                .toSet()

            val mediaList = mediaStoreRepository.loadAllMedia()
            mediaList
                .filter { it.id !in trashedIds }
                .map { it.toImageData() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load all images")
            emptyList()
        }
    }

    /**
     * Get all folders with image counts
     */
    override suspend fun getFolders(): List<FolderData> = withContext(Dispatchers.IO) {
        try {
            val albums = mediaStoreRepository.loadAlbums()
            albums.map { album ->
                FolderData(
                    name = album.name,
                    path = album.id.toString(),
                    imageCount = album.count,
                    lastModified = System.currentTimeMillis(),
                    thumbnailPath = album.coverUri
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load folders")
            emptyList()
        }
    }

    /**
     * Get images in specific folder
     */
    override suspend fun getImagesByFolder(folderName: String): List<ImageData> = withContext(Dispatchers.IO) {
        try {
            val trashedIds = embeddingBox.query(ImageEmbedding_.isTrashed.equal(true))
                .build()
                .find()
                .map { it.mediaStoreId }
                .toSet()

            val allMedia = mediaStoreRepository.loadAllMedia()
            allMedia
                .filter { it.albumName.equals(folderName, ignoreCase = true) && it.id !in trashedIds }
                .map { it.toImageData() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load images from folder: $folderName")
            emptyList()
        }
    }

    /**
     * Search by text query using vector embeddings
     */
    override suspend fun searchByText(query: String): List<SearchResult> = withContext(Dispatchers.Default) {
        try {
            val textVec = clipEmbedder.embedText(query)
            val activeEmbeddings = embeddingBox.query(ImageEmbedding_.isTrashed.equal(false)).build().find()
            val mediaMap = mediaStoreRepository.loadAllMedia().associateBy { it.id }

            val results = mutableListOf<SearchResult>()
            for (emb in activeEmbeddings) {
                val vec = emb.embedding ?: continue
                val media = mediaMap[emb.mediaStoreId] ?: continue
                val score = dotProduct(textVec, vec)
                if (score >= 0.18f) {
                    results.add(
                        SearchResult(
                            image = media.toImageData(),
                            similarity = score,
                            explanation = "Semantic similarity ${(score * 100).toInt()}%"
                        )
                    )
                }
            }
            results.sortedByDescending { it.similarity }
        } catch (e: Exception) {
            Timber.e(e, "Search failed for: $query")
            emptyList()
        }
    }

    override suspend fun searchByPrimaryObject(objectName: String): List<SearchResult> {
        return searchByText(objectName)
    }

    /**
     * Move images to trash (soft-delete)
     */
    override suspend fun moveToTrash(imageIds: List<Long>) = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            for (id in imageIds) {
                var existing = embeddingBox.query(ImageEmbedding_.mediaStoreId.equal(id)).build().findFirst()
                if (existing == null) {
                    existing = ImageEmbedding(
                        mediaStoreId = id,
                        isTrashed = true,
                        trashedAt = now
                    )
                } else {
                    existing.isTrashed = true
                    existing.trashedAt = now
                }
                embeddingBox.put(existing)
            }
            Timber.i("✓ Moved ${imageIds.size} images to trash")
        } catch (e: Exception) {
            Timber.e(e, "Failed to move to trash")
        }
    }

    /**
     * Restore images from trash
     */
    override suspend fun restoreFromTrash(imageIds: List<Long>) = withContext(Dispatchers.IO) {
        try {
            for (id in imageIds) {
                val existing = embeddingBox.query(ImageEmbedding_.mediaStoreId.equal(id)).build().findFirst()
                if (existing != null) {
                    existing.isTrashed = false
                    existing.trashedAt = 0
                    embeddingBox.put(existing)
                }
            }
            Timber.i("✓ Restored ${imageIds.size} images from trash")
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore from trash")
        }
    }

    /**
     * Get trash bin contents
     */
    override suspend fun getTrash(): List<ImageData> = withContext(Dispatchers.IO) {
        try {
            val trashedEntities = embeddingBox.query(ImageEmbedding_.isTrashed.equal(true))
                .orderDesc(ImageEmbedding_.trashedAt)
                .build()
                .find()

            val mediaMap = mediaStoreRepository.loadAllMedia().associateBy { it.id }

            trashedEntities.mapNotNull { entity ->
                val media = mediaMap[entity.mediaStoreId]
                if (media != null) {
                    media.toImageData().copy(
                        isTrashed = true,
                        trashedAt = entity.trashedAt
                    )
                } else if (entity.uri.isNotEmpty()) {
                    ImageData(
                        id = entity.mediaStoreId,
                        uri = Uri.parse(entity.uri),
                        filePath = entity.uri,
                        fileName = entity.displayName.ifBlank { "Media" },
                        folderName = "Trash",
                        mimeType = "image/*",
                        dateAdded = entity.indexedAt,
                        dateModified = entity.indexedAt,
                        width = 0,
                        height = 0,
                        fileSize = 0,
                        isTrashed = true,
                        trashedAt = entity.trashedAt
                    )
                } else null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load trash")
            emptyList()
        }
    }

    /**
     * Permanently delete images
     */
    override suspend fun permanentlyDelete(imageIds: List<Long>) = withContext(Dispatchers.IO) {
        try {
            for (id in imageIds) {
                val existing = embeddingBox.query(ImageEmbedding_.mediaStoreId.equal(id)).build().findFirst()
                if (existing != null) {
                    if (existing.uri.isNotEmpty()) {
                        try {
                            context.contentResolver.delete(Uri.parse(existing.uri), null, null)
                        } catch (se: SecurityException) {
                            Timber.w("SecurityException deleting MediaStore uri: ${existing.uri}")
                        }
                    }
                    embeddingBox.remove(existing)
                }
            }
            Timber.i("⚠️ Permanently deleted ${imageIds.size} images")
        } catch (e: Exception) {
            Timber.e(e, "Failed to permanently delete")
        }
    }

    /**
     * Get days until permanent deletion (30 days total)
     */
    override suspend fun getDaysUntilDelete(imageId: Long): Int? = withContext(Dispatchers.IO) {
        try {
            val entity = embeddingBox.query(ImageEmbedding_.mediaStoreId.equal(imageId)).build().findFirst()
            if (entity?.isTrashed == true && entity.trashedAt > 0) {
                val daysPassed = ((System.currentTimeMillis() - entity.trashedAt) / (24 * 60 * 60 * 1000L)).toInt()
                (30 - daysPassed).coerceAtLeast(0)
            } else {
                30
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get days until delete")
            null
        }
    }

    private fun MediaItem.toImageData(): ImageData {
        return ImageData(
            id = id,
            uri = if (uri.isNotEmpty()) Uri.parse(uri) else ContentUris.withAppendedId(
                if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id
            ),
            filePath = relativePath,
            fileName = name.ifBlank { "Media $id" },
            folderName = albumName,
            mimeType = mimeType,
            dateAdded = dateAdded,
            dateModified = dateTaken,
            dateTaken = dateTaken,
            width = 0,
            height = 0,
            fileSize = size
        )
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            sum += a[i] * b[i]
        }
        return sum
    }
}