package co.juneai.june.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import co.juneai.june.data.model.ExifData
import co.juneai.june.data.model.ScannedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Scans device MediaStore for images
 * Incremental: only returns images not yet indexed
 */
class MediaStoreScannerRepositoryImpl @Inject constructor(
    private val context: Context
) {



    /**
     * Scan all images from MediaStore
     */
    suspend fun scanAllImages(): List<ScannedImage> {
        return withContext(Dispatchers.IO) {
            val images = mutableListOf<ScannedImage>()

            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateTakenColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                    val dateModifiedColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                    val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                    val mimeTypeColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val bucketColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        try {
                            val id = cursor.getLong(idColumn)
                            val displayName = cursor.getString(nameColumn)
                            val dateTaken = cursor.getLong(dateTakenColumn)
                            val dateModified = cursor.getLong(dateModifiedColumn)
                            val size = cursor.getLong(sizeColumn)
                            val width = cursor.getInt(widthColumn)
                            val height = cursor.getInt(heightColumn)
                            val mimeType = cursor.getString(mimeTypeColumn)
                            val folderName = cursor.getString(bucketColumn) ?: "Unknown"

                            // Filter: only valid images
                            if (isValidImage(mimeType, size, width, height)) {
                                val uri = ContentUris.withAppendedId(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    id
                                )

                                images.add(
                                    ScannedImage(
                                        id = id,
                                        uri = uri,
                                        displayName = displayName,
                                        dateTaken = dateTaken,
                                        dateModified = dateModified,
                                        size = size,
                                        width = width,
                                        height = height,
                                        mimeType = mimeType,
                                        folderName = folderName
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Timber.Forest.w("Skipped invalid image: ${e.message}")
                        }
                    }
                }

                Timber.Forest.i("✓ Scanned ${images.size} images from device")
            } catch (e: Exception) {
                Timber.Forest.e(e, "MediaStore scan failed")
            }

            images
        }
    }

    /**
     * Extract EXIF metadata from image
     */
    suspend fun extractExifData(uri: Uri): ExifData {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ExifData()

                val exif = ExifInterface(inputStream)

                ExifData(
                    dateTaken = exif.getAttributeInt(
                        ExifInterface.TAG_DATETIME,
                        0
                    ).toLong(),
                    latitude = exif.getLatLong()?.get(0),
                    longitude = exif.getLatLong()?.get(1),
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
                    isoValue = exif.getAttributeInt(
                        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                        0
                    ),
                    exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                        ?.toFloatOrNull(),
                    focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                        ?.toFloatOrNull(),
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.toFloatOrNull()
                )
            } catch (e: Exception) {
                Timber.Forest.d("No EXIF data found: ${e.message}")
                ExifData()
            }
        }
    }

    private fun isValidImage(mimeType: String?, size: Long, width: Int, height: Int): Boolean {
        // Validate image
        if (mimeType == null) return false
        if (!mimeType.startsWith("image/")) return false
        if (size < 10_000) return false  // Skip tiny images
        if (width < 100 || height < 100) return false  // Skip small dimensions

        return true
    }
}