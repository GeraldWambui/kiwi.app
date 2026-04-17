package co.juneai.june.domain.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import co.juneai.june.data.model.SearchImageEntity
import co.juneai.june.ml.clip.ClipEmbedder
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.IOException
import javax.inject.Inject

class ImageIndexingService @Inject constructor(
    private val context: Context,
    private val boxStore: BoxStore,
    private val clipEngine: ClipEmbedder
) {
    private val imageBox: Box<SearchImageEntity> by lazy {
        boxStore.boxFor(SearchImageEntity::class.java)
    }
    
    private val _indexingProgress = MutableStateFlow(IndexingProgress())
    val indexingProgress: StateFlow<IndexingProgress> = _indexingProgress.asStateFlow()
    
    data class IndexingProgress(
        val total: Int = 0,
        val processed: Int = 0,
        val currentFile: String = "",
        val isComplete: Boolean = false
    )
    
    suspend fun indexImage(uri: Uri): Result<SearchImageEntity> = withContext(Dispatchers.IO) {
        try {
            // Load bitmap efficiently
            val bitmap = loadBitmapWithSampling(uri, 224, 224)
            
            // Generate embedding
            val embedding = clipEngine.embedImage(bitmap)
            
            // Create entity
            val entity = SearchImageEntity(
                filePath = uri.toString(),
                fileName = uri.lastPathSegment ?: "unknown",
                fileSize = 0L, // Implement getFileSize
                mimeType = context.contentResolver.getType(uri) ?: "",
                width = bitmap.width,
                height = bitmap.height,
                embedding = embedding
            )
            
            // Store in database
            imageBox.put(entity)
            
            bitmap.recycle()
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun indexAllImages(imageUris: List<Uri>) = withContext(Dispatchers.IO) {
        _indexingProgress.value = IndexingProgress(total = imageUris.size)
        
        imageUris.forEachIndexed { index, uri ->
            _indexingProgress.value = _indexingProgress.value.copy(
                processed = index,
                currentFile = uri.toString()
            )
            
            indexImage(uri)
            
            // Yield to prevent ANR or blocking
            yield()
        }
        
        _indexingProgress.value = _indexingProgress.value.copy(isComplete = true)
    }
    
    private fun loadBitmapWithSampling(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)
            
            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            
            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565
            
            context.contentResolver.openInputStream(uri)?.use { newStream ->
                return BitmapFactory.decodeStream(newStream, null, options)!!
            }
        }
        throw IOException("Failed to load bitmap from $uri")
    }
    
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && 
                   halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
}
