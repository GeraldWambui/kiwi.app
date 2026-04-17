package co.juneai.june.data.search.intent

import javax.inject.Inject

class IntentParser @Inject constructor() {

    fun parse(input: String): SearchIntent {
        val normalized = input.lowercase().trim()

        return when {
            normalized.startsWith("delete ") ->
                SearchIntent.DeleteCommand(
                    query = normalized.removePrefix("delete ")
                )

            normalized.startsWith("remove ") ->
                SearchIntent.DeleteCommand(
                    query = normalized.removePrefix("remove ")
                )

            normalized.startsWith("find ") ->
                SearchIntent.SemanticSearch(
                    query = normalized.removePrefix("find ")
                )

            normalized.startsWith("show ") ->
                SearchIntent.SemanticSearch(
                    query = normalized.removePrefix("show ")
                )

            else ->
                SearchIntent.SemanticSearch(normalized)
        }
    }
}
