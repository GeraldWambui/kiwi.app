package co.juneai.june.data.repository.nsfw

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists NSFW flags for media items using SharedPreferences.
 * Uses a Set<String> of flagged media IDs for O(1) lookup.
 * Also stores the user's NSFW protection toggle preference.
 */
@Singleton
class NsfwFlagStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "nsfw_flags"
        private const val KEY_FLAGGED_IDS = "flagged_media_ids"
        private const val KEY_SCANNED_IDS = "scanned_media_ids"
        private const val KEY_PROTECTION_ENABLED = "nsfw_protection_enabled"
        private const val KEY_INITIAL_SCAN_COMPLETE = "initial_scan_complete"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // In-memory cache for fast lookup
    private val flaggedIds: MutableSet<Long> by lazy {
        prefs.getStringSet(KEY_FLAGGED_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toMutableSet()
            ?: mutableSetOf()
    }

    private val scannedIds: MutableSet<Long> by lazy {
        prefs.getStringSet(KEY_SCANNED_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toMutableSet()
            ?: mutableSetOf()
    }

    /**
     * Check if a media item is flagged as NSFW.
     */
    fun isNsfw(mediaId: Long): Boolean = flaggedIds.contains(mediaId)

    /**
     * Check if a media item has already been scanned.
     */
    fun isScanned(mediaId: Long): Boolean = scannedIds.contains(mediaId)

    /**
     * Mark a media item as scanned, optionally flagging it as NSFW.
     */
    fun markScanned(mediaId: Long, isNsfw: Boolean) {
        scannedIds.add(mediaId)
        if (isNsfw) {
            flaggedIds.add(mediaId)
        } else {
            flaggedIds.remove(mediaId)
        }
        persistFlags()
    }

    /**
     * Mark multiple items at once (batch operation).
     */
    fun markScannedBatch(results: Map<Long, Boolean>) {
        results.forEach { (mediaId, nsfw) ->
            scannedIds.add(mediaId)
            if (nsfw) flaggedIds.add(mediaId) else flaggedIds.remove(mediaId)
        }
        persistFlags()
    }

    /**
     * Get all flagged (NSFW) media IDs.
     */
    fun getAllFlaggedIds(): Set<Long> = flaggedIds.toSet()

    /**
     * Get total count of flagged items.
     */
    fun getFlaggedCount(): Int = flaggedIds.size

    /**
     * Get total count of scanned items.
     */
    fun getScannedCount(): Int = scannedIds.size

    /**
     * Whether NSFW protection is enabled by the user.
     */
    var isProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, true) // Default ON
        set(value) {
            prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()
        }

    /**
     * Whether the initial full scan has been completed.
     */
    var isInitialScanComplete: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_SCAN_COMPLETE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INITIAL_SCAN_COMPLETE, value).apply()
        }

    /**
     * Remove flag for a media item (e.g., if media was deleted).
     */
    fun removeFlag(mediaId: Long) {
        flaggedIds.remove(mediaId)
        scannedIds.remove(mediaId)
        persistFlags()
    }

    /**
     * Clear all flags and scanned records.
     */
    fun clearAll() {
        flaggedIds.clear()
        scannedIds.clear()
        prefs.edit()
            .remove(KEY_FLAGGED_IDS)
            .remove(KEY_SCANNED_IDS)
            .putBoolean(KEY_INITIAL_SCAN_COMPLETE, false)
            .apply()
        Timber.d("All NSFW flags cleared")
    }

    private fun persistFlags() {
        prefs.edit()
            .putStringSet(KEY_FLAGGED_IDS, flaggedIds.map { it.toString() }.toSet())
            .putStringSet(KEY_SCANNED_IDS, scannedIds.map { it.toString() }.toSet())
            .apply()
    }
}
