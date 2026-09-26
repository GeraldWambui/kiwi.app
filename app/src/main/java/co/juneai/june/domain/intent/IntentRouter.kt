package co.juneai.june.domain.intent

import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Contract for On-Device Small Language Model (Gemma 2 2B / SmolLM2 INT4) intent parsing.
 * Converts complex conversational natural language into structured UserIntent.
 */
interface OnDeviceSlmParser {
    val isModelAvailable: Boolean
    suspend fun parseWithSlm(prompt: String): UserIntent?
}

/**
 * Fallback / default implementation when on-demand SLM weight package is not downloaded.
 */
@Singleton
class DefaultSlmParser @Inject constructor() : OnDeviceSlmParser {
    override val isModelAvailable: Boolean = false

    override suspend fun parseWithSlm(prompt: String): UserIntent? {
        // Can be connected to LiteRT GenAI / MediaPipe LLM Inference when module is active
        return null
    }
}

/**
 * Intent Router routing between Fast Path (0ms Rule/Slot-filling) and Complex NL Path (SLM).
 */
@Singleton
class IntentRouter @Inject constructor(
    private val ruleSlotFiller: IntentParser,
    private val slmParser: OnDeviceSlmParser
) {

    suspend fun routeAndParse(input: String): UserIntent {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return UserIntent.Search("")

        // 1. Fast Path: Rule & Slot-Filling Grammar (0ms latency, deterministic)
        val ruleIntent = ruleSlotFiller.parse(trimmed)

        // Check if query is complex conversational NL (e.g. multi-clause sentences)
        val isComplexQuery = trimmed.split(" ").size > 6 &&
                (trimmed.contains(" and ", ignoreCase = true) ||
                 trimmed.contains(" when ", ignoreCase = true) ||
                 trimmed.contains(" that have ", ignoreCase = true))

        if (isComplexQuery && slmParser.isModelAvailable) {
            try {
                val slmIntent = slmParser.parseWithSlm(trimmed)
                if (slmIntent != null) {
                    Timber.d("Routed query to on-device SLM: $slmIntent")
                    return slmIntent
                }
            } catch (e: Exception) {
                Timber.w(e, "SLM parsing failed, falling back to rule slot-filler")
            }
        }

        return ruleIntent
    }
}
