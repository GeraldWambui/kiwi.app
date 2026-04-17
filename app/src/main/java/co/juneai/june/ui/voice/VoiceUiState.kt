package co.juneai.june.ui.voice

data class VoiceUiState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val finalText: String = "",
    val confidence: Float = 0f,
    val error: String? = null
)
