package co.juneai.june.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manages the "Hey Kiwi" voice activation system.
 * Listens for the hotword "hey kiwi", then captures user commands.
 */
class KiwiVoiceService(private val context: Context) {

    enum class VoiceState {
        IDLE,           // Not listening
        HOTWORD_LISTENING, // Listening for "hey kiwi"
        COMMAND_LISTENING, // Hotword detected, capturing command
        PROCESSING     // Parsing and executing command
    }

    data class VoiceResult(
        val state: VoiceState = VoiceState.IDLE,
        val partialText: String = "",
        val finalText: String = "",
        val command: KiwiCommand? = null,
        val error: String? = null,
        val rmsLevel: Float = 0f
    )

    sealed class KiwiCommand {
        data class Search(val query: String) : KiwiCommand()
        object OpenSettings : KiwiCommand()
        object OpenAlbums : KiwiCommand()
        object OpenPhotos : KiwiCommand()
        object OpenVault : KiwiCommand()
        object OpenTrash : KiwiCommand()
        data class PlayVideo(val query: String) : KiwiCommand()
        data class Unknown(val text: String) : KiwiCommand()
    }

    private val _voiceResult = MutableStateFlow(VoiceResult())
    val voiceResult: StateFlow<VoiceResult> = _voiceResult.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningForHotword = false

    fun startHotwordListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        isListeningForHotword = true
        _voiceResult.value = VoiceResult(state = VoiceState.HOTWORD_LISTENING)

        Handler(Looper.getMainLooper()).post {
            initRecognizer(isHotword = true)
        }
    }

    fun startCommandListening() {
        isListeningForHotword = false
        _voiceResult.value = VoiceResult(state = VoiceState.COMMAND_LISTENING)

        Handler(Looper.getMainLooper()).post {
            initRecognizer(isHotword = false)
        }
    }

    fun stopListening() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _voiceResult.value = VoiceResult(state = VoiceState.IDLE)
        isListeningForHotword = false
    }

    private fun initRecognizer(isHotword: Boolean) {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    _voiceResult.value = _voiceResult.value.copy(rmsLevel = rmsdB)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: return
                    Timber.d("Voice partial: $text")

                    if (!isHotword) {
                        _voiceResult.value = _voiceResult.value.copy(partialText = text)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Timber.d("Voice results: $text")

                    if (isHotword) {
                        val lower = text.lowercase()
                        if (lower.contains("kiwi") ||
                            lower.contains("kiwee") ||
                            lower.contains("kivi") ||
                            lower.contains("key we") ||
                            lower.contains("keywee") ||
                            lower.contains("q v")
                        ) {
                            Timber.d("Hotword detected in results: $text")
                            startCommandListening()
                        } else {
                            // Stop looping indefinitely to avoid mic hogging
                            stopListening()
                        }
                    } else {
                        val command = parseCommand(text)
                        _voiceResult.value = VoiceResult(
                            state = VoiceState.PROCESSING,
                            finalText = text,
                            command = command
                        )
                    }
                }

                override fun onError(error: Int) {
                    Timber.d("Voice error: $error")
                    if (isHotword) {
                        // Stop looping indefinitely to avoid mic hogging
                        stopListening()
                    } else {
                        _voiceResult.value = _voiceResult.value.copy(
                            state = VoiceState.IDLE,
                            error = "Voice recognition error: $error"
                        )
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, !isHotword)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun parseCommand(text: String): KiwiCommand {
        val lower = text.lowercase().trim()
        return when {
            lower.startsWith("search for") || lower.startsWith("search ") || lower.startsWith("find ") ->
                KiwiCommand.Search(lower.removePrefix("search for ").removePrefix("search ").removePrefix("find ").trim())
            lower.contains("setting") || lower.contains("preferences") ->
                KiwiCommand.OpenSettings
            lower.contains("album") ->
                KiwiCommand.OpenAlbums
            lower.contains("photo") || lower.contains("picture") || lower.contains("gallery") ->
                KiwiCommand.OpenPhotos
            lower.contains("vault") || lower.contains("secure") || lower.contains("lock") ->
                KiwiCommand.OpenVault
            lower.contains("trash") || lower.contains("deleted") || lower.contains("bin") ->
                KiwiCommand.OpenTrash
            lower.startsWith("play") || lower.contains("video") ->
                KiwiCommand.PlayVideo(lower.removePrefix("play ").removePrefix("video ").trim())
            else -> KiwiCommand.Unknown(text)
        }
    }
}
