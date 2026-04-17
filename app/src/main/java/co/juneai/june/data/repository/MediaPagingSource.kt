package co.juneai.june.data.repository

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import co.juneai.june.data.model.MediaItem2

/**
 * PagingSource that loads media from MediaStore in pages,
 * sorted by date taken descending, for fast initial display.
 */
class MediaPagingSource(
    private val context: Context,
    private val albumFilter: String? = null
) : PagingSource<Int, MediaItem2>() {

    override fun getRefreshKey(state: PagingState<Int, MediaItem2>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    @SuppressLint("InlinedApi", "Range")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem2> {
        val page = params.key ?: 0
        val pageSize = params.loadSize

        return try {
            val items = mutableListOf<MediaItem2>()

            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
            )

            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.RELATIVE_PATH
            )

            val selection = albumFilter?.let {
                "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            }
            val selectionArgs = albumFilter?.let { arrayOf(it) }

            // Load images
            val imageSortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC LIMIT $pageSize OFFSET ${page * pageSize}"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection, selection, selectionArgs, imageSortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                        items.add(
                            MediaItem2(
                                id = id,
                                uri = uri.toString(),
                                name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "",
                                dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)),
                                mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: "image/*",
                                duration = null,
                                albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)),
                                albumName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)) ?: "Unknown",
                                relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)) ?: ""
                            )
                        )
                    } catch (_: Exception) { }
                }
            }

            // Load videos
            val videoSortOrder = "${MediaStore.Video.Media.DATE_TAKEN} DESC LIMIT $pageSize OFFSET ${page * pageSize}"
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection, selection, selectionArgs, videoSortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                        items.add(
                            MediaItem2(
                                id = id,
                                uri = uri.toString(),
                                name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "",
                                dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)),
                                mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)) ?: "video/*",
                                duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
                                albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)),
                                albumName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)) ?: "Unknown",
                                relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)) ?: ""
                            )
                        )
                    } catch (_: Exception) { }
                }
            }

            // Sort by date descending
            items.sortByDescending { it.dateTaken }

            val nextKey = if (items.size < pageSize) null else page + 1
            val prevKey = if (page == 0) null else page - 1

            LoadResult.Page(data = items, prevKey = prevKey, nextKey = nextKey)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
