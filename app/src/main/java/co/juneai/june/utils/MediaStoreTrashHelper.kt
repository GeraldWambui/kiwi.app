package co.juneai.june.utils

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import timber.log.Timber

object MediaStoreTrashHelper {

    /**
     * Creates an OS-level batch trash request IntentSender on Android 11+ (API 30+).
     * Returns null on Android 10 and below, or if the request cannot be created.
     */
    fun createTrashRequest(
        context: Context,
        uris: List<Uri>,
        isTrash: Boolean = true
    ): PendingIntent? {
        if (uris.isEmpty()) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                MediaStore.createTrashRequest(context.contentResolver, uris, isTrash)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create MediaStore.createTrashRequest for ${uris.size} URIs")
                null
            }
        } else {
            null
        }
    }

    /**
     * Creates an OS-level permanent deletion request IntentSender on Android 11+ (API 30+).
     */
    fun createDeleteRequest(
        context: Context,
        uris: List<Uri>
    ): PendingIntent? {
        if (uris.isEmpty()) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                MediaStore.createDeleteRequest(context.contentResolver, uris)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create MediaStore.createDeleteRequest for ${uris.size} URIs")
                null
            }
        } else {
            null
        }
    }

    /**
     * Fallback soft-delete for pre-Android 11 or internal database sync.
     */
    fun directDelete(context: Context, uris: List<Uri>): Int {
        var deletedCount = 0
        for (uri in uris) {
            try {
                val count = context.contentResolver.delete(uri, null, null)
                if (count > 0) deletedCount++
            } catch (e: Exception) {
                Timber.w("Direct delete failed for URI: $uri")
            }
        }
        return deletedCount
    }
}
