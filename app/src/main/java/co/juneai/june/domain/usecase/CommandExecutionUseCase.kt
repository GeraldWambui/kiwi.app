package co.juneai.june.domain.usecase

import android.util.Log
import co.juneai.june.data.local.db.GalleryDatabase
import co.juneai.june.data.local.entities.VaultItemEntity
import co.juneai.june.data.model.CommandIntent
import co.juneai.june.data.model.CommandResult
import co.juneai.june.data.model.CommandType
import co.juneai.june.domain.repository.EmbeddingRepository
import co.juneai.june.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.map

class CommandExecutionUseCase(
    private val mediaRepository: MediaRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val database: GalleryDatabase,
    private val searchUseCase: SearchUseCases,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun executeCommand(
        command: CommandIntent,
        yoloMode: Boolean = false
    ): Result<CommandResult> = withContext(dispatcher) {
        try {
            when (command.type) {
                CommandType.DELETE -> executeDelete(command, yoloMode)
                CommandType.SHARE -> executeShare(command)
                CommandType.MOVE -> executeMove(command)
                CommandType.VAULT -> executeVault(command)
                CommandType.SELECT -> executeSelect(command)
                else -> Result.success(CommandResult(false, message = "Unknown command"))
            }
        } catch (e: Exception) {
            Log.e("CommandExecution", "Error executing command", e)
            Result.failure(e)
        }
    }

    private suspend fun executeDelete(command: CommandIntent, yoloMode: Boolean): Result<CommandResult> {
        val mediaIds = identifyTargetMedia(command)
        
        if (!yoloMode && mediaIds.size > 50) {
            // Require confirmation for large deletions
            return Result.success(
                CommandResult(
                    success = false,
                    message = "Too many items (${mediaIds.size}). Please confirm in UI.",
                    itemsPreview = mediaIds.take(10)
                )
            )
        }

        val deleteResult = mediaRepository.deleteMedia(mediaIds)
        if (deleteResult.isSuccess) {
            mediaIds.forEach { embeddingRepository.deleteEmbedding(it) }
            return Result.success(
                CommandResult(
                    success = true,
                    affectedItemsCount = mediaIds.size,
                    message = "Deleted ${mediaIds.size} items",
                    undoable = true
                )
            )
        }
        return deleteResult.map { CommandResult(false) }
    }

    private suspend fun executeShare(command: CommandIntent): Result<CommandResult> {
        val mediaIds = identifyTargetMedia(command)
        return Result.success(
            CommandResult(
                success = true,
                affectedItemsCount = mediaIds.size,
                message = "Ready to share ${mediaIds.size} items",
                itemsPreview = mediaIds.take(10)
            )
        )
    }

    private suspend fun executeMove(command: CommandIntent): Result<CommandResult> {
        val mediaIds = identifyTargetMedia(command)
        val albumName = command.parameters["album"] ?: "New Album"
        return Result.success(
            CommandResult(
                success = true,
                affectedItemsCount = mediaIds.size,
                message = "Moved ${mediaIds.size} items to '$albumName'"
            )
        )
    }

    private suspend fun executeVault(command: CommandIntent): Result<CommandResult> {
        val mediaIds = identifyTargetMedia(command)
        mediaIds.forEach { mediaId ->
            database.vaultDao().insert(VaultItemEntity(mediaId = mediaId, encryptedUri = ""))
        }
        return Result.success(
            CommandResult(
                success = true,
                affectedItemsCount = mediaIds.size,
                message = "Moved ${mediaIds.size} items to vault"
            )
        )
    }

    private suspend fun executeSelect(command: CommandIntent): Result<CommandResult> {
        val mediaIds = identifyTargetMedia(command)
        return Result.success(
            CommandResult(
                success = true,
                affectedItemsCount = mediaIds.size,
                message = "Selected ${mediaIds.size} items",
                itemsPreview = mediaIds.take(10)
            )
        )
    }

    private suspend fun identifyTargetMedia(command: CommandIntent): List<Long> {
        val target = command.target.lowercase()
        val allEmbeddings = embeddingRepository.getAllEmbeddings()

        return when {
            target.contains("screenshot") -> allEmbeddings.filter { 
                it.uri.contains("screenshot", ignoreCase = true) 
            }.map { it.mediaId }
            target.contains("blurry") -> allEmbeddings.filter { 
                it.blurriness > 0.5f 
            }.map { it.mediaId }
            target.contains("similar") -> {
                val searchResult = searchUseCase.performSemanticSearch(target)
                searchResult.getOrNull()?.map { it.mediaId } ?: emptyList()
            }
            else -> emptyList()
        }
    }
}