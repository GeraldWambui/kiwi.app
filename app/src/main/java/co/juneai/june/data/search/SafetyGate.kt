package co.juneai.june.data.search

import co.juneai.june.data.model.SearchResult

class SafetyGate {

    fun requireConfirmation(
        items: List<SearchResult>
    ): Boolean {
        return items.size > 0
    }
}
