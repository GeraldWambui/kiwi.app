package co.juneai.june.domain.intent

enum class CategoryFilter {
    ALL,
    BLURRY,
    SCREENSHOTS,
    RECEIPTS_DOCUMENTS,
    PORTRAITS,
    VIDEOS
}

sealed class UserIntent {
    data class Search(
        val query: String,
        val mediaTypeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
        val categoryFilter: CategoryFilter = CategoryFilter.ALL,
        val dateRange: DateRangeFilter? = null,
        val locationFilter: String? = null,
        val negatedQuery: String? = null,
        val negatedCategory: CategoryFilter? = null
    ) : UserIntent()

    data class Delete(
        val targetQuery: String,
        val categoryFilter: CategoryFilter = CategoryFilter.ALL,
        val dateRange: DateRangeFilter? = null,
        val locationFilter: String? = null,
        val confirmationRequired: Boolean = true
    ) : UserIntent()

    data class MoveToVault(
        val targetQuery: String,
        val categoryFilter: CategoryFilter = CategoryFilter.ALL,
        val dateRange: DateRangeFilter? = null,
        val locationFilter: String? = null,
        val confirmationRequired: Boolean = true
    ) : UserIntent()

    data class ShowSimilar(
        val mediaId: Long? = null,
        val queryDescription: String? = null
    ) : UserIntent()

    data class Navigate(
        val destinationRoute: String
    ) : UserIntent()
}

enum class MediaTypeFilter {
    ALL,
    PHOTOS_ONLY,
    VIDEOS_ONLY
}
