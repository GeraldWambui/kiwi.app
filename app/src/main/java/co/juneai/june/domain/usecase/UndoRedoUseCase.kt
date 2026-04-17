package co.juneai.june.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.ListenableWorker
import co.juneai.june.data.local.db.GalleryDatabase
import co.juneai.june.data.local.entities.UndoRedoEntity
import co.juneai.june.data.model.UndoRedoAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


class UndoRedoUseCase {

    private val database: GalleryDatabase
    private val dispatcher: CoroutineDispatcher

    constructor(database: GalleryDatabase, dispatcher: CoroutineDispatcher = Dispatchers.Default) {
        this.database = database
        this.dispatcher = dispatcher
    }

    suspend fun recordAction(action: UndoRedoAction): Result<Unit> = withContext(dispatcher) {
        try {
            database.undoRedoDao().insert(
                UndoRedoEntity(
                    actionId = action.id,
                    actionType = action.type,
                    originalStateJson = action.originalState.toString()
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun undo(): Unit = withContext(dispatcher) {
        try {
            val lastAction = database.undoRedoDao().getLastAction()
            Result.success(lastAction?.let { entity ->
                UndoRedoAction(
                    id = entity.actionId,
                    type = entity.actionType,
                    originalState = emptyMap()
                )
            })
        } catch (e: Exception) {
            ListenableWorker.Result.failure()
        }
    }


}