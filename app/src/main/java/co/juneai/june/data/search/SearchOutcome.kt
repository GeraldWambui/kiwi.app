package co.juneai.june.data.search

import co.juneai.june.data.model.SearchResult

sealed class SearchOutcome {
    data class Results(val items: List<SearchResult>) : SearchOutcome()
    data class ConfirmationRequired(val items: List<SearchResult>) : SearchOutcome()
    object Empty : SearchOutcome()
}
