package co.juneai.june.domain.usecase

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import co.juneai.june.data.model.VoiceCommandResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class VoiceCommandUseCase(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    fun initializeSpeechRecognizer(context: Context): SpeechRecognizer {
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    fun startListening(
        speechRecognizer: SpeechRecognizer,
        onResult: (VoiceCommandResult) -> Unit
    ) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                onResult(VoiceCommandResult("", 0f, error = "Error code: $error"))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {

            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: 0f
                matches?.firstOrNull()?.let { text ->
                    onResult(VoiceCommandResult(text, confidence, isFinal = true))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partial?.firstOrNull()?.let { text ->
                    onResult(VoiceCommandResult(text, 0.5f, isFinal = false))
                }
            }
        })

        speechRecognizer.startListening(intent)
    }
}