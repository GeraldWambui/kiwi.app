package co.juneai.june.data.repository

import android.graphics.Bitmap
import android.util.Log
import co.juneai.june.data.model.TextDetectionResult
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OCRProcessor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val textRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    suspend fun extractText(
        bitmap: Bitmap,
        mediaId: Long
    ): Result<TextDetectionResult> = withContext(dispatcher) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(textRecognizer.process(image))

            val text = visionText.text

            val boundingBoxes = visionText.textBlocks.mapNotNull { block ->
                block.boundingBox
            }

            Result.success(
                TextDetectionResult(
                    text = text,
                    confidence = calculateConfidence(visionText),
                    boundingBoxes = boundingBoxes,
                    mediaId = mediaId
                )
            )

        } catch (e: Exception) {
            Log.e("OCRProcessor", "OCR failed", e)
            Result.failure(e)
        }
    }

    private fun calculateConfidence(visionText: com.google.mlkit.vision.text.Text): Float {
        if (visionText.textBlocks.isEmpty()) return 0f

        val avgConfidence = visionText.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .mapNotNull { it.confidence }
            .average()

        return if (avgConfidence.isNaN()) 0.8f else avgConfidence.toFloat()
    }
}
