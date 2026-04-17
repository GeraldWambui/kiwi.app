package co.juneai.june.domain.repository

import co.juneai.june.data.model.MediaItem2

interface MediaRepository {
    suspend fun getAllMedia(): Result<List<MediaItem2>>
    suspend fun getNewMedia(since: Long): Result<List<MediaItem2>>
    suspend fun deleteMedia(mediaIds: List<Long>): Result<Boolean>
    suspend fun moveToVault(mediaIds: List<Long>): Result<Boolean>
}