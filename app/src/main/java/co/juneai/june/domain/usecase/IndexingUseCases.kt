package co.juneai.june.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import co.juneai.june.data.local.db.GalleryDatabase
import co.juneai.june.data.local.entities.FaceEmbeddingEntity
import co.juneai.june.data.local.entities.MediaEmbeddingEntity
import co.juneai.june.data.local.entities.ObjectDetectionEntity
import co.juneai.june.data.repository.BlurDetectionProcessor
import co.juneai.june.data.repository.ColorAnalysisProcessor
import co.juneai.june.data.repository.FaceDetectionProcessor
import co.juneai.june.data.repository.NSFWDetectionProcessor
import co.juneai.june.data.repository.OCRProcessor
import co.juneai.june.data.repository.ObjectDetectionProcessor
import co.juneai.june.data.repository.PCACompressor
import co.juneai.june.domain.repository.EmbeddingProcessor
import co.juneai.june.domain.repository.EmbeddingRepository
import co.juneai.june.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class IndexingUseCases(
    private val mediaRepository: MediaRepository,
    private val embeddingProcessor: EmbeddingProcessor,
    private val embeddingRepository: EmbeddingRepository,
    private val faceDetectionProcessor: FaceDetectionProcessor,
    private val ocrProcessor: OCRProcessor,
    private val nsfwProcessor: NSFWDetectionProcessor,
    private val objectDetectionProcessor: ObjectDetectionProcessor,
    private val colorAnalysisProcessor: ColorAnalysisProcessor,
    private val blurDetectionProcessor: BlurDetectionProcessor,
    private val pcaCompressor: PCACompressor,
    private val database: GalleryDatabase,
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun indexNewMedia(onProgress: (current: Int, total: Int) -> Unit): Result<Int> =
        withContext(dispatcher) {
            try {
                val lastIndexedTime = getLastIndexedTime()
                val newMediaResult = mediaRepository.getNewMedia(lastIndexedTime)

                if (newMediaResult.isFailure) {
                    return@withContext Result.failure(
                        newMediaResult.exceptionOrNull() ?: Exception("Unknown error")
                    )
                }

                val newMedia = newMediaResult.getOrNull() ?: emptyList()
                if (newMedia.isEmpty()) {
                    return@withContext Result.success(0)
                }

                var indexed = 0
                newMedia.chunked(10).forEachIndexed { chunkIndex, chunk ->
                    chunk.forEach { media ->
                        try {
                            val bitmap = loadBitmap(media.uri) ?: return@forEach
                            
                            // Generate embeddings
                            val embedding = embeddingProcessor.getImageEmbedding(bitmap)
                            val pcaEmbedding = pcaCompressor.compress(embedding)

                            // Extract text (OCR)
                            val ocrResult = ocrProcessor.extractText(bitmap, media.id)
                            val textContent = ocrResult.getOrNull()?.text ?: ""

                            // NSFW detection
                            val nsfwResult = nsfwProcessor.detectNSFW(bitmap, media.id)
                            val nsfwScore = nsfwResult.getOrNull()?.score ?: 0f
                            val nsfwCategory = nsfwResult.getOrNull()?.category?.name ?: "SAFE"

                            // Detect objects
                            val objectsResult = objectDetectionProcessor.detectPrimaryObjects(bitmap, media.id)
                            val objects = objectsResult.getOrNull() ?: emptyList()

                            // Analyze colors
                            val colorResult = colorAnalysisProcessor.analyzeColors(bitmap, media.id)
                            val dominantColor = colorResult.getOrNull()?.dominantColor

                            // Detect blur
                            val blurResult = blurDetectionProcessor.detectBlur(bitmap)
                            val blurriness = blurResult.getOrNull() ?: 0f

                            // Detect faces
                            val facesResult = faceDetectionProcessor.detectFaces(bitmap, media.id)
                            val faces = facesResult.getOrNull() ?: emptyList()

                            // Save embedding
                            val entity = MediaEmbeddingEntity(
                                mediaId = media.id,
                                uri = media.uri,
                                fullEmbedding = floatArrayToByteArray(embedding),
                                pcaEmbedding = floatArrayToByteArray(pcaEmbedding),
                                textContent = textContent,
                                nsfwScore = nsfwScore,
                                nsfwCategory = nsfwCategory,
                                dominantColor = dominantColor,
                                fileSize = getMediaDimensions(context, media.uri.toUri())?.third!!,
                                takenDate = media.dateTaken,
                                width = getMediaDimensions(context, media.uri.toUri())?.first!!,
                                height = getMediaDimensions(context, media.uri.toUri())?.second!!,
                                blurriness = blurriness
                            )
                            embeddingRepository.saveEmbedding(entity)

                            // Save faces
                            faces.forEach { face ->
                                database.faceEmbeddingDao().insert(
                                    FaceEmbeddingEntity(
                                        mediaId = media.id,
                                        embedding = floatArrayToByteArray(face.embeddings),
                                        boundingBoxLeft = face.boundingBox.left,
                                        boundingBoxTop = face.boundingBox.top,
                                        boundingBoxRight = face.boundingBox.right,
                                        boundingBoxBottom = face.boundingBox.bottom,
                                        confidence = face.confidence
                                    )
                                )
                            }

                            // Save objects
                            objects.forEach { obj ->
                                database.objectDetectionDao().insert(
                                    ObjectDetectionEntity(
                                        mediaId = media.id,
                                        label = obj.label,
                                        confidence = obj.confidence,
                                        boundingBoxLeft = obj.boundingBox.left,
                                        boundingBoxTop = obj.boundingBox.top,
                                        boundingBoxRight = obj.boundingBox.right,
                                        boundingBoxBottom = obj.boundingBox.bottom
                                    )
                                )
                            }

                            indexed++
                            bitmap.recycle()
                        } catch (e: Exception) {
                            Log.e("Indexing", "Failed to index ${media.uri}", e)
                        }
                    }
                    onProgress(chunkIndex * 10 + chunk.size, newMedia.size)
                }

                saveLastIndexedTime(System.currentTimeMillis())
                Result.success(indexed)
            } catch (e: Exception) {
                Log.e("IndexingUseCase", "Error during indexing", e)
                Result.failure(e)
            }
        }

    private fun loadBitmap(uri: String): Bitmap? {
        return try {
            val parsedUri = Uri.parse(uri)
            BitmapFactory.decodeStream(context.contentResolver.openInputStream(parsedUri))
        } catch (e: Exception) {
            Log.e("Indexing", "Failed to load bitmap from $uri", e)
            null
        }
    }

    private fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(floats.size * 4)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }

    private fun getLastIndexedTime(): Long {
        return context.getSharedPreferences("gallery", Context.MODE_PRIVATE)
            .getLong("last_indexed", 0)
    }

    private fun saveLastIndexedTime(time: Long) {
        context.getSharedPreferences("gallery", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_indexed", time)
            .apply()
    }

    fun getMediaDimensions(context: Context, uri: Uri): Triple<Int, Int, Long>? {
        return try {
            val contentResolver = context.contentResolver

            // Get file size
            val sizeInBytes = contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L

            // Get dimensions using ContentResolver
            val cursor = contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT
                ),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val widthIndex = it.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightIndex = it.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                    val width = if (widthIndex >= 0) it.getInt(widthIndex) else 0
                    val height = if (heightIndex >= 0) it.getInt(heightIndex) else 0

                    return Triple(width, height, sizeInBytes)
                }
            }

            // Fallback: use BitmapFactory just to get dimensions
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                return Triple(options.outWidth, options.outHeight, sizeInBytes)
            }

            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to get media dimensions")
            null
        }
    }
}