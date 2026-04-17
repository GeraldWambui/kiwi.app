package co.juneai.june.data.search.intent

sealed class SearchIntent {
    data class SemanticSearch(val query: String) : SearchIntent()
    data class TextSearch(val text: String) : SearchIntent()
    data class DeleteCommand(val query: String) : SearchIntent()
    data class BulkAction(
        val action: BulkActionType,
        val query: String
    ) : SearchIntent()
}

enum class BulkActionType {
    DELETE,
    SHARE,
    MOVE,
    FAVORITE
}
