package co.juneai.june.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.content.ContentUris
import android.content.ContentValues
import android.provider.MediaStore
import co.juneai.june.data.model.AlbumItem
import co.juneai.june.data.model.AlbumType
import co.juneai.june.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaStoreRepository(
    @param:ApplicationContext val context: Context
) {


    @SuppressLint("InlinedApi", "Range")
    suspend fun loadAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC"
        )?.use { cursor ->

            // Get column indices safely
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val durCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
            val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getLong(idCol)
                    val mime = cursor.getString(mimeCol) ?: continue
                    val dateTaken = cursor.getLong(dateCol)
                    val dateAdded = cursor.getLong(dateAddedCol)

                    val date = if (dateTaken > 0) dateTaken else dateAdded * 1000

                    // Get duration safely
                    val duration = if (mime.startsWith("video") && durCol != -1) {
                        cursor.getLong(durCol)
                    } else null

                    // Get bucket info safely
                    val bucketId = if (bucketIdCol != -1) cursor.getLong(bucketIdCol) else 0L
                    val bucketName = if (bucketNameCol != -1) {
                        cursor.getString(bucketNameCol) ?: "Unknown"
                    } else "Unknown"

                    // Get path safely
                    val path = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""

                    // Get name safely
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""

                    // Get size safely
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                    // Create the proper content URI as STRING
                    val uriString = if (mime.startsWith("video")) {
                        ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()
                    } else {
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()
                    }

                    items += MediaItem(
                        id = id,
                        uri = uriString, // Now it's a String
                        mimeType = mime,
                        dateTaken = date,
                        duration = duration,
                        albumId = bucketId,
                        albumName = bucketName,
                        relativePath = path,
                        name = name,
                        size = size,
                        dateAdded = dateAdded,
                        isVideo = mime.startsWith("video")
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Skipped invalid media item")
                }
            }
        }

        Timber.d("Loaded ${items.size} media items")
        items
    }

    suspend fun loadAlbums(media: List<MediaItem>? = null): List<AlbumItem> = withContext(Dispatchers.IO) {
        val items = media ?: loadAllMedia()

        items
            .groupBy { it.albumId }
            .map { (albumId, items) ->
                val newest = items.maxByOrNull { it.dateTaken }

                AlbumItem(
                    id = albumId ?: items.first().id,
                    name = items.first().albumName,
                    count = items.size,
                    coverUri = newest?.uri,
                    isVideoFolder = items.all { it.isVideo },
                    hasMedia = items.isNotEmpty(),
                    type = classifyAlbum(items)
                )
            }
            .sortedWith(compareBy<AlbumItem> { it.type.ordinal }.thenBy { it.name })
    }

    private fun classifyAlbum(items: List<MediaItem>): AlbumType {
        if (items.isEmpty()) return AlbumType.MORE

        val path = items.first().relativePath.lowercase()
        val albumName = items.first().albumName.lowercase()

        return when {
            albumName.contains("camera", true) ||
                    path.contains("dcim/camera") ||
                    path.contains("/camera/") -> AlbumType.SYSTEM

            albumName.contains("screenshot", true) ||
                    path.contains("screenshots") -> AlbumType.SYSTEM

            albumName.contains("screenrecord", true) ||
                    path.contains("screenrecord") -> AlbumType.SYSTEM

            albumName.contains("download", true) ||
                    path.contains("download") -> AlbumType.SYSTEM

            items.all { it.isVideo } -> AlbumType.SYSTEM

            albumName.contains("creative", true) ||
                    albumName.contains("favorite", true) ||
                    albumName.contains("edited", true) -> AlbumType.USER

            else -> AlbumType.MORE
        }
    }

    suspend fun buildSystemAlbums(mediaList: List<MediaItem>? = null): List<AlbumItem> = withContext(Dispatchers.IO) {
        val media = mediaList ?: loadAllMedia()

        val allPhotosAlbum = AlbumItem(
            id = -1L,
            name = "All Photos",
            count = media.size,
            coverUri = media.firstOrNull()?.uri,
            isVideoFolder = false,
            hasMedia = media.isNotEmpty(),
            type = AlbumType.SYSTEM
        )

        val cameraItems = media.filter {
            val path = it.relativePath.lowercase()
            path.contains("dcim/camera") || path.contains("/camera/")
        }

        val cameraAlbum = AlbumItem(
            id = -2L,
            name = "Camera",
            count = cameraItems.size,
            coverUri = cameraItems.firstOrNull()?.uri,
            isVideoFolder = false,
            hasMedia = cameraItems.isNotEmpty(),
            type = AlbumType.SYSTEM
        )

        val videoItems = media.filter { it.isVideo }
        val videosAlbum = AlbumItem(
            id = -3L,
            name = "Videos",
            count = videoItems.size,
            coverUri = videoItems.firstOrNull()?.uri,
            isVideoFolder = true,
            hasMedia = videoItems.isNotEmpty(),
            type = AlbumType.SYSTEM
        )

        val screenshotItems = media.filter {
            val path = it.relativePath.lowercase()
            val name = it.albumName.lowercase()
            path.contains("screenshot") || name.contains("screenshot") ||
                    path.contains("screenrecord") || name.contains("screenrecord")
        }


        return@withContext listOf(
            allPhotosAlbum,
            cameraAlbum,
            videosAlbum,
        )
    }

    suspend fun buildAllPhotosAlbum(mediaList: List<MediaItem>? = null): AlbumItem {
        val media = mediaList ?: loadAllMedia()

        return AlbumItem(
            id = -1,
            name = "All Photos",
            count = media.size,
            coverUri = media.firstOrNull()?.uri,
            isVideoFolder = false,
            hasMedia = media.isNotEmpty(),
            type = AlbumType.SYSTEM
        )
    }

    suspend fun buildCameraAlbum(mediaList: List<MediaItem>? = null): AlbumItem {
        val media = mediaList ?: loadAllMedia()

        val cameraItems = media.filter {
            it.relativePath.contains("dcim", true) || it.relativePath.contains("/camera/", true)
        }

        return AlbumItem(
            id = -2,
            name = "Camera",
            count = cameraItems.size,
            coverUri = cameraItems.firstOrNull()?.uri,
            isVideoFolder = false,
            hasMedia = cameraItems.isNotEmpty(),
            type = AlbumType.SYSTEM
        )
    }



    suspend fun deleteMedia(mediaId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediaId
            )
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    // In MediaStoreRepository.kt
    suspend fun getMediaByIds(ids: List<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()

        val selection = "${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",") { "?" }})"
        val selectionArgs = ids.map { it.toString() }.toTypedArray()

        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH
            ),
            selection,
            selectionArgs,
            null
        )

        cursor?.use { cursor ->
            val mediaItems = mutableListOf<MediaItem>()
            while (cursor.moveToNext()) {
                mediaItems.add(createMediaItemFromCursor(cursor))
            }
            return@withContext mediaItems
        }

        emptyList()
    }
    @SuppressLint("Range")
    suspend fun getMediaById(mediaId: Long): MediaItem = withContext(Dispatchers.IO) {
        val imageUri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaId
        )

        val imageItem = tryLoadFromUri(imageUri, isVideo = false)
        if (imageItem != null) return@withContext imageItem

        val videoUri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            mediaId
        )

        val videoItem = tryLoadFromUri(videoUri, isVideo = true)
        if (videoItem != null) return@withContext videoItem

        throw NoSuchElementException("Media not found: $mediaId")
    }

    private fun tryLoadFromUri(uri: Uri, isVideo: Boolean): MediaItem? {
        return try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
            )

            context.contentResolver.query(
                uri,
                projection,
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
                    val dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)) * 1000
                    val bucketId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID))
                    val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME))
                    val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))

                    MediaItem(
                        id = id,
                        uri = uri.toString(),
                        mimeType = mimeType,
                        dateTaken = dateTaken,
                        duration = if (isVideo) getVideoDuration(id) else null,
                        albumId = bucketId,
                        albumName = bucketName,
                        relativePath = relativePath
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getVideoDuration(videoId: Long): Long? {
        return try {
            val videoUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoId
            )

            val projection = arrayOf(MediaStore.Video.VideoColumns.DURATION)

            context.contentResolver.query(
                videoUri,
                projection,
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION))
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("Range")
    suspend fun updateMediaTitle(mediaId: Long, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newTitle)
            }

            val imageUri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediaId
            )

            val imageRows = context.contentResolver.update(imageUri, values, null, null)
            if (imageRows > 0) return@withContext true

            val videoUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                mediaId
            )

            val videoRows = context.contentResolver.update(videoUri, values, null, null)
            return@withContext videoRows > 0

        } catch (e: SecurityException) {
            throw SecurityException("Permission denied: ${e.message}")
        } catch (e: Exception) {
            throw Exception("Failed to update media title: ${e.message}")
        }
    }

    @SuppressLint("Range")
    suspend fun getMediaIdsForAlbum(albumId: Long): List<Long> = withContext(Dispatchers.IO) {
        val mediaIds = mutableListOf<Long>()

        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            while (cursor.moveToNext()) {
                mediaIds.add(cursor.getLong(idColumn))
            }
        }

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            while (cursor.moveToNext()) {
                mediaIds.add(cursor.getLong(idColumn))
            }
        }

        return@withContext mediaIds.distinct()
    }

    @SuppressLint("Range", "InlinedApi")
    suspend fun getMediaForAlbum(albumId: Long): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaItems = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC"

        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val durCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val mime = cursor.getString(mimeCol) ?: "image/*"
                val date = cursor.getLong(dateCol)
                val duration = if (durCol != -1 && mime.startsWith("video")) {
                    cursor.getLong(durCol)
                } else null
                val bucketId = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val path = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""

                val contentUri = if (mime.startsWith("video")) {
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                } else {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }

                mediaItems.add(
                    MediaItem(
                        id,
                        contentUri.toString(),
                        mime,
                        date,
                        duration,
                        bucketId,
                        bucketName,
                        path
                    )
                )
            }
        }

        return@withContext mediaItems
    }

    suspend fun getAllMediaIds(): List<Long> = withContext(Dispatchers.IO) {
        val mediaIds = mutableListOf<Long>()

        val imageProjection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                mediaIds.add(cursor.getLong(idColumn))
            }
        }

        val videoProjection = arrayOf(MediaStore.Video.Media._ID)
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                mediaIds.add(cursor.getLong(idColumn))
            }
        }

        mediaIds
    }


    suspend fun textSearch(query: String): List<MediaItem> {
        return withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val allMedia = loadAllMedia()
            val lowerQuery = query.lowercase()
            allMedia.filter { item ->
                item.name.lowercase().contains(lowerQuery) ||
                item.albumName.lowercase().contains(lowerQuery) ||
                item.relativePath.lowercase().contains(lowerQuery)
            }
        }
    }


    private fun createMediaItemFromCursor(cursor: android.database.Cursor): MediaItem {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
        val dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
        val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION))
        val albumName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME))
        val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))
        val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
        val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))

        val isVideo = mimeType?.startsWith("video/") == true
        val uri = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id
        )


        return MediaItem(
            id = id,
            uri = uri.toString(),
            name = displayName,
            dateTaken = dateTaken,
            size = size,
            mimeType = mimeType ?: "",
            isVideo = isVideo,
            duration = if (isVideo) duration else null,
            albumName = albumName,
            relativePath = relativePath,
        )
    }


}