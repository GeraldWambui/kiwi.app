package co.juneai.june.data.local.extensions

import co.juneai.june.data.local.entities.VaultMediaEntity
import co.juneai.june.data.model.VaultMedia
import java.io.File

fun VaultMedia.toEntity(): VaultMediaEntity = VaultMediaEntity(
    id = id,
    originalPath = originalPath,
    vaultPath = vaultPath,
    type = type,
    timestamp = timestamp,
    thumbnail = thumbnail,
    fileName = fileName,
    fileSize = fileSize,
    isEncrypted = isEncrypted,
    mimeType = mimeType,
    width = width,
    height = height,
    duration = duration
)
