package co.juneai.june.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import co.juneai.june.data.model.MediaItem2
import co.juneai.june.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepositoryImpl(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaRepository {

    override suspend fun getAllMedia(): Result<List<MediaItem2>> = withContext(dispatcher) {
        try {
            val media = queryMediaStore(null, null, null)
            Result.success(media)
        } catch (e: Exception) {
            Log.e("MediaRepository", "Error fetching media", e)
            Result.failure(e)
        }
    }

    override suspend fun getNewMedia(since: Long): Result<List<MediaItem2>> = withContext(dispatcher) {
        try {
            val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
            val selectionArgs = arrayOf((since / 1000).toString())
            val media = queryMediaStore(selection, selectionArgs, null)
            Result.success(media)
        } catch (e: Exception) {
            Log.e("MediaRepository", "Error fetching new media", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteMedia(mediaIds: List<Long>): Result<Boolean> = withContext(dispatcher) {
        try {
            mediaIds.forEach { id ->
                context.contentResolver.delete(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    null,
                    null
                )
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e("MediaRepository", "Error deleting media", e)
            Result.failure(e)
        }
    }

    override suspend fun moveToVault(mediaIds: List<Long>): Result<Boolean> = withContext(dispatcher) {
        try {
            // Implementation would handle moving files to secure vault
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryMediaStore(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): List<MediaItem2> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder ?: "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        val media = mutableListOf<MediaItem2>()
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                ).toString()
                val mimeType = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
                val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                val width = it.getInt(it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH))
                val height = it.getInt(it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))

                media.add(
                    MediaItem2(
                        id = id,
                        uri = uri,
                        mimeType = mimeType,
                        dateTaken = dateAdded,
                        duration = TODO(),
                        albumId = TODO(),
                        albumName = TODO(),
                        relativePath = TODO(),
                    )
                )
            }
        }
        return media
    }
}
