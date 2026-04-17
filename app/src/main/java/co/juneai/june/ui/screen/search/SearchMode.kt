package co.juneai.june.ui.screen.search

import co.juneai.june.data.model.SearchResult

sealed class SearchMode {
    object None : SearchMode()
    data class TextRefinement(val query: String) : SearchMode()
    data class Semantic(val query: String) : SearchMode()
    data class ConfirmDelete(val items: List<SearchResult>) : SearchMode()
}