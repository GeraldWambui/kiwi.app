package co.juneai.june.ui.viewmodel

data class IndexingUiState(
    val isIndexing: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0,
    val lastIndexedTime: Long = 0L,
    val error: String? = null
)
