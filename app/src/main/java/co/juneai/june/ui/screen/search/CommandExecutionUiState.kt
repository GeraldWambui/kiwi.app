package co.juneai.june.ui.screen.search

import co.juneai.june.data.model.CommandResult

data class CommandExecutionUiState(
    val isExecuting: Boolean = false,
    val result: CommandResult? = null,
    val requiresConfirmation: Boolean = false,
    val error: String? = null
)
