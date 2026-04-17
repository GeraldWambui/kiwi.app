package co.juneai.june.domain.usecase

import android.util.Log
import co.juneai.june.data.model.CommandIntent
import co.juneai.june.data.model.CommandType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommandDetectionUseCase(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val commandPatterns = mapOf(
        Regex("delete|remove|clear|trash|erase", RegexOption.IGNORE_CASE) to CommandType.DELETE,
        Regex("share|send|upload|export", RegexOption.IGNORE_CASE) to CommandType.SHARE,
        Regex("move|organize|album|create album", RegexOption.IGNORE_CASE) to CommandType.MOVE,
        Regex("vault|secure|hide|lock|encrypt", RegexOption.IGNORE_CASE) to CommandType.VAULT,
        Regex("select|choose|pick", RegexOption.IGNORE_CASE) to CommandType.SELECT
    )

    suspend fun detectIntent(text: String): Result<CommandIntent> = withContext(dispatcher) {
        try {
            var detectedType: CommandType? = null
            var maxMatches = 0

            for ((pattern, type) in commandPatterns) {
                val matches = pattern.findAll(text).count()
                if (matches > maxMatches) {
                    maxMatches = matches
                    detectedType = type
                }
            }

            val intent = CommandIntent(
                type = detectedType ?: CommandType.SEARCH,
                target = extractTarget(text),
                confidence = if (detectedType != null) 0.8f else 0.2f,
                requiresConfirmation = detectedType != CommandType.SEARCH
            )

            Result.success(intent)
        } catch (e: Exception) {
            Log.e("CommandDetection", "Error detecting intent", e)
            Result.failure(e)
        }
    }

    private fun extractTarget(text: String): String {
        val words = text.split(" ").filter { it.length > 3 }
        return words.take(3).joinToString(" ")
    }
}