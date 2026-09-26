package co.juneai.june.ml.ocr

import android.graphics.Bitmap
import co.juneai.june.data.local.daos.MediaOcrDao
import co.juneai.june.data.local.entities.MediaOcrEntity
import co.juneai.june.ml.clip.ClipEmbedder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class OcrScanResult(
    val mediaStoreId: Long,
    val extractedText: String,
    val textSnippet: String,
    val ocrTextEmbedding: FloatArray? = null,
    val hasSignificantText: Boolean
)

@Singleton
class OcrScanner @Inject constructor(
    private val mediaOcrDao: MediaOcrDao,
    private val clipEmbedder: ClipEmbedder
) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Scans [bitmap] for text via ML Kit OCR, stores text in Room SQLite FTS,
     * and optionally embeds high-confidence text in CLIP space.
     */
    suspend fun scanAndStore(
        mediaStoreId: Long,
        bitmap: Bitmap
    ): OcrScanResult {
        val detectedText = recognizeText(bitmap)
        val trimmed = detectedText.trim()

        if (trimmed.isBlank()) {
            return OcrScanResult(
                mediaStoreId = mediaStoreId,
                extractedText = "",
                textSnippet = "",
                hasSignificantText = false
            )
        }

        val snippet = trimmed.lines().filter { it.isNotBlank() }.take(3).joinToString(" | ")
        val hasSignificantText = trimmed.length >= 8

        // Store in Room FTS Database
        try {
            val entity = MediaOcrEntity(
                mediaStoreId = mediaStoreId,
                extractedText = trimmed,
                textSnippet = snippet,
                confidence = 1.0f,
                indexedAt = System.currentTimeMillis()
            )
            mediaOcrDao.insertOrUpdate(entity)
        } catch (e: Exception) {
            Timber.w(e, "Failed to persist OCR text for media $mediaStoreId")
        }

        // Embed high-confidence OCR text into CLIP text space if significant
        val ocrEmbedding = if (hasSignificantText) {
            try {
                val cleanSnippet = snippet.take(75)
                clipEmbedder.embedText(cleanSnippet)
            } catch (e: Exception) {
                Timber.w(e, "Failed to embed OCR text for media $mediaStoreId")
                null
            }
        } else {
            null
        }

        return OcrScanResult(
            mediaStoreId = mediaStoreId,
            extractedText = trimmed,
            textSnippet = snippet,
            ocrTextEmbedding = ocrEmbedding,
            hasSignificantText = hasSignificantText
        )
    }

    private suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (cont.isActive) cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    Timber.w(e, "OCR recognition error")
                    if (cont.isActive) cont.resume("")
                }
        } catch (e: Exception) {
            Timber.w(e, "OCR exception")
            if (cont.isActive) cont.resume("")
        }
    }
}
