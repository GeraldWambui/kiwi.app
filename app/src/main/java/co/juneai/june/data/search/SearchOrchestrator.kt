package co.juneai.june.data.search

import co.juneai.june.ml.MobileCLIPPipeline
import co.juneai.june.data.search.intent.IntentParser
import co.juneai.june.data.search.intent.SearchIntent
import javax.inject.Inject

class SearchOrchestrator @Inject constructor(
    private val searchEngine: TwoStageSearchEngine,
    private val mlPipeline: MobileCLIPPipeline,
    private val intentParser: IntentParser,
) {

    suspend fun handleQuery(
        rawInput: String
    ): SearchOutcome {

        val intent = intentParser.parse(rawInput)

        return when (intent) {

            is SearchIntent.SemanticSearch -> {
                val results = searchEngine.searchWithML(
                    query = intent.query,
                    mlPipeline = mlPipeline
                )
                SearchOutcome.Results(results)
            }

            is SearchIntent.DeleteCommand -> {
                val candidates = searchEngine.searchWithML(
                    query = intent.query,
                    mlPipeline = mlPipeline,
                    limit = 500
                )
                SearchOutcome.ConfirmationRequired(candidates)
            }

            else -> SearchOutcome.Empty
        }
    }
}
