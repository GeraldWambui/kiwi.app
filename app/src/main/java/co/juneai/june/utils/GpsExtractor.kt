package co.juneai.june.utils

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber

data class GeoLocation(
    val latitude: Double,
    val longitude: Double
)

object GpsExtractor {

    /**
     * Extracts latitude and longitude from a media content URI or file path.
     */
    fun extractLocation(context: Context, uriString: String): GeoLocation? {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use { stream ->
                val exifInterface = ExifInterface(stream)
                val latLong = exifInterface.latLong
                if (latLong != null && latLong.size >= 2) {
                    val lat = latLong[0]
                    val lng = latLong[1]
                    if (lat != 0.0 || lng != 0.0) {
                        GeoLocation(lat, lng)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Timber.v("No GPS metadata found for $uriString: ${e.message}")
            null
        }
    }
}
