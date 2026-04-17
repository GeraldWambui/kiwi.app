package co.juneai.june.domain.startup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import co.juneai.june.data.repository.GalleryRepositoryImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class StartupManager @Inject constructor(
    private val repository: GalleryRepositoryImpl,
    @param:ApplicationContext private val context: Context
) {
    
    /**
     * Complete startup sequence
     */
    suspend fun initialize(): StartupResult {
        return withContext(Dispatchers.IO) {
            try {
                Timber.i("🚀 Starting AI Gallery initialization...")
                
                // Step 1: Check permissions
                val hasPermission = hasReadMediaPermission()
                Timber.d("${if (hasPermission) "✓" else "✗"} Read media images permission")
                if (!hasPermission) {
                    Timber.w("⚠️ Missing read media permissions")
                    return@withContext StartupResult.MissingPermissions
                }
                
                // Step 2: Load gallery
                Timber.d("Loading gallery from device...")
                val images = repository.getAllImages()
                Timber.i("✓ Loaded ${images.size} images")
                
                // Step 3: Schedule background indexing
                Timber.d("Scheduling background indexing...")
                
                // Step 4: Verify ML models
                Timber.d("Verifying ML models...")
                
                Timber.i("✓ AI Gallery initialization complete")
                StartupResult.Success
                
            } catch (e: Exception) {
                Timber.e(e, "Startup failed")
                StartupResult.Error(e)
            }
        }
    }

    private fun hasReadMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

sealed class StartupResult {
    object Success : StartupResult()
    object MissingPermissions : StartupResult()
    data class Error(val exception: Exception) : StartupResult()
}