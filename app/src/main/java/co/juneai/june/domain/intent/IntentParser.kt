package co.juneai.june.domain.intent

import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentParser @Inject constructor() {

    companion object {
        // Location extraction regex (e.g. "in Nairobi", "at Paris", "from London")
        private val LOCATION_REGEX = Pattern.compile(
            "\\b(?:in|at|from|around)\\s+([a-zA-Z\\s]{2,20})\\b",
            Pattern.CASE_INSENSITIVE
        )

        // Negation extraction regex (e.g. "without people", "but not blurry", "except receipts")
        private val NEGATION_REGEX = Pattern.compile(
            "\\b(?:without|except|excluding|but not|not)\\s+([a-zA-Z\\s]{2,25})\\b",
            Pattern.CASE_INSENSITIVE
        )
    }

    fun parse(input: String): UserIntent {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. Navigation Commands
        if (lower.startsWith("open ") || lower.startsWith("go to ") || lower.startsWith("show me ")) {
            val target = lower.removePrefix("open ").removePrefix("go to ").removePrefix("show me ").trim()
            when {
                target.contains("setting") || target.contains("preferences") ->
                    return UserIntent.Navigate("settings")
                target.contains("people") || target.contains("person") || target.contains("faces") || target.contains("pets") ->
                    return UserIntent.Navigate("people")
                target.contains("trash") || target.contains("bin") || target.contains("deleted") ->
                    return UserIntent.Navigate("trash")
                target.contains("vault") || target.contains("secret") || target.contains("locked") ->
                    return UserIntent.Navigate("vault_setup")
                target.contains("album") ->
                    return UserIntent.Navigate("albums")
                target == "photos" || target == "library" || target == "gallery" ->
                    return UserIntent.Navigate("library")
                target.contains("for you") || target.contains("memory") || target.contains("memories") ->
                    return UserIntent.Navigate("foryou")
            }
        }

        // 2. Extract Date Range if present
        val (queryAfterDate, dateRange) = DateRangeParser.parseDateRange(trimmed)

        // 3. Extract Negation Logic (e.g. "dogs without people", "sunset not blurry")
        var queryAfterNegation = queryAfterDate
        var negatedQuery: String? = null
        var negatedCategory: CategoryFilter? = null

        val negMatcher = NEGATION_REGEX.matcher(queryAfterDate)
        if (negMatcher.find()) {
            val negTerm = negMatcher.group(1).trim().lowercase()
            negatedQuery = negTerm
            queryAfterNegation = queryAfterDate.replace(negMatcher.group(), "").trim()

            when {
                negTerm.contains("blurry") || negTerm.contains("blur") ->
                    negatedCategory = CategoryFilter.BLURRY
                negTerm.contains("people") || negTerm.contains("person") || negTerm.contains("face") || negTerm.contains("selfie") ->
                    negatedCategory = CategoryFilter.PORTRAITS
                negTerm.contains("video") ->
                    negatedCategory = CategoryFilter.VIDEOS
                negTerm.contains("screenshot") ->
                    negatedCategory = CategoryFilter.SCREENSHOTS
                negTerm.contains("receipt") || negTerm.contains("document") ->
                    negatedCategory = CategoryFilter.RECEIPTS_DOCUMENTS
            }
        }

        // 4. Extract Location Filter (e.g. "in Nairobi", "at Paris")
        var queryAfterLocation = queryAfterNegation
        var locationFilter: String? = null

        val locMatcher = LOCATION_REGEX.matcher(queryAfterNegation)
        if (locMatcher.find()) {
            val locCandidate = locMatcher.group(1).trim()
            // Ignore common false positives for prepositions (e.g. "in photos", "in videos", "in camera")
            val locLower = locCandidate.lowercase()
            if (locLower !in setOf("photos", "videos", "camera", "gallery", "library", "album", "albums", "trash", "vault")) {
                locationFilter = locCandidate
                queryAfterLocation = queryAfterNegation.replace(locMatcher.group(), "").trim()
            }
        }

        // 5. Extract Category Filter
        val lowerAfterLoc = queryAfterLocation.lowercase()
        var categoryFilter = CategoryFilter.ALL
        var cleanCategoryQuery = queryAfterLocation

        when {
            lowerAfterLoc.contains("blurry") || lowerAfterLoc.contains("bad quality") -> {
                categoryFilter = CategoryFilter.BLURRY
                cleanCategoryQuery = cleanCategoryQuery.replace("blurry", "", ignoreCase = true)
                    .replace("bad quality", "", ignoreCase = true).trim()
            }
            lowerAfterLoc.contains("screenshot") || lowerAfterLoc.contains("screen shot") -> {
                categoryFilter = CategoryFilter.SCREENSHOTS
                cleanCategoryQuery = cleanCategoryQuery.replace("screenshots", "", ignoreCase = true)
                    .replace("screenshot", "", ignoreCase = true).trim()
            }
            lowerAfterLoc.contains("receipt") || lowerAfterLoc.contains("document") || lowerAfterLoc.contains("invoice") || lowerAfterLoc.contains("bill") -> {
                categoryFilter = CategoryFilter.RECEIPTS_DOCUMENTS
                cleanCategoryQuery = cleanCategoryQuery.replace("receipts", "", ignoreCase = true)
                    .replace("receipt", "", ignoreCase = true)
                    .replace("documents", "", ignoreCase = true)
                    .replace("document", "", ignoreCase = true).trim()
            }
            lowerAfterLoc.contains("portrait") || lowerAfterLoc.contains("selfie") -> {
                categoryFilter = CategoryFilter.PORTRAITS
                cleanCategoryQuery = cleanCategoryQuery.replace("portraits", "", ignoreCase = true)
                    .replace("portrait", "", ignoreCase = true)
                    .replace("selfies", "", ignoreCase = true)
                    .replace("selfie", "", ignoreCase = true).trim()
            }
        }

        val lowerCleanCat = cleanCategoryQuery.lowercase()

        // 6. Delete / Trash Commands (Destructive Intent)
        if (lowerCleanCat.startsWith("delete ") || lowerCleanCat.startsWith("remove ") || lowerCleanCat.startsWith("trash ") || lowerCleanCat == "delete" || lowerCleanCat == "trash") {
            val target = cleanCategoryQuery
                .removePrefix("delete ")
                .removePrefix("Delete ")
                .removePrefix("remove ")
                .removePrefix("trash ")
                .removePrefix("Trash ")
                .trim()

            return UserIntent.Delete(
                targetQuery = target.ifBlank { if (categoryFilter != CategoryFilter.ALL) categoryFilter.name else "all matching photos" },
                categoryFilter = categoryFilter,
                dateRange = dateRange,
                locationFilter = locationFilter,
                confirmationRequired = true
            )
        }

        // 7. Move to Vault / Hide Commands
        if (lowerCleanCat.startsWith("move to vault") || lowerCleanCat.startsWith("hide in vault") || lowerCleanCat.startsWith("lock in vault") || lowerCleanCat.startsWith("hide ") || lowerCleanCat.startsWith("vault ")) {
            val target = cleanCategoryQuery
                .removePrefix("move to vault ")
                .removePrefix("Move to vault ")
                .removePrefix("hide in vault ")
                .removePrefix("Hide in vault ")
                .removePrefix("lock in vault ")
                .removePrefix("hide ")
                .removePrefix("Hide ")
                .removePrefix("vault ")
                .trim()

            return UserIntent.MoveToVault(
                targetQuery = target.ifBlank { if (categoryFilter != CategoryFilter.ALL) categoryFilter.name else "photos" },
                categoryFilter = categoryFilter,
                dateRange = dateRange,
                locationFilter = locationFilter,
                confirmationRequired = true
            )
        }

        // 8. Similarity Commands
        if (lowerCleanCat.startsWith("find similar to ") || lowerCleanCat.startsWith("show similar ") || lowerCleanCat.startsWith("more like ")) {
            val target = cleanCategoryQuery
                .removePrefix("find similar to ")
                .removePrefix("show similar ")
                .removePrefix("more like ")
                .trim()
            return UserIntent.ShowSimilar(queryDescription = target)
        }

        // 9. Search with Media Type filters
        var mediaTypeFilter = MediaTypeFilter.ALL
        var cleanQuery = cleanCategoryQuery

        if (lowerCleanCat.startsWith("videos of ") || lowerCleanCat.startsWith("videos with ") || lowerCleanCat.startsWith("video of ")) {
            mediaTypeFilter = MediaTypeFilter.VIDEOS_ONLY
            cleanQuery = cleanCategoryQuery.removePrefix("videos of ").removePrefix("Videos of ")
                .removePrefix("videos with ").removePrefix("video of ")
        } else if (lowerCleanCat.startsWith("photos of ") || lowerCleanCat.startsWith("pictures of ") || lowerCleanCat.startsWith("photos with ")) {
            mediaTypeFilter = MediaTypeFilter.PHOTOS_ONLY
            cleanQuery = cleanCategoryQuery.removePrefix("photos of ").removePrefix("Photos of ")
                .removePrefix("pictures of ").removePrefix("photos with ")
        } else if (lowerCleanCat.startsWith("find ") || lowerCleanCat.startsWith("search for ") || lowerCleanCat.startsWith("search ")) {
            cleanQuery = cleanCategoryQuery.removePrefix("find ").removePrefix("Find ")
                .removePrefix("search for ").removePrefix("Search for ")
                .removePrefix("search ").removePrefix("Search ")
        }

        return UserIntent.Search(
            query = cleanQuery.ifBlank { if (categoryFilter != CategoryFilter.ALL) categoryFilter.name.lowercase() else trimmed },
            mediaTypeFilter = mediaTypeFilter,
            categoryFilter = categoryFilter,
            dateRange = dateRange,
            locationFilter = locationFilter,
            negatedQuery = negatedQuery,
            negatedCategory = negatedCategory
        )
    }
}
