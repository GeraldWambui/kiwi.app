package co.juneai.june.data.model

import java.io.File

data class VaultMedia(
    val id: String,
    val originalPath: String,
    val vaultPath: String,
    val type: VaultMediaType,
    val timestamp: Long,
    val thumbnail: String? = null,
    val fileName: String = "",
    val fileSize: Long = 0,
    val isEncrypted: Boolean = true,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Long? = null // For videos
) {
    // Convenience constructor for existing code
    constructor(
        id: String,
        originalPath: String,
        vaultPath: String,
        type: VaultMediaType,
        timestamp: Long,
        thumbnail: String?
    ) : this(
        id = id,
        originalPath = originalPath,
        vaultPath = vaultPath,
        type = type,
        timestamp = timestamp,
        thumbnail = thumbnail,
        fileName = File(originalPath).name,
        fileSize = File(originalPath).length(),
        isEncrypted = true,
        mimeType = null,
        width = null,
        height = null,
        duration = null
    )

    // Get display name
    fun getDisplayName(): String = fileName.ifEmpty { File(originalPath).name }

    // Check if file exists in vault
    fun existsInVault(): Boolean = File(vaultPath).exists()

    // Get formatted file size
    fun getFormattedSize(): String = when {
        fileSize < 1024 -> "$fileSize B"
        fileSize < 1024 * 1024 -> String.format("%.2f KB", fileSize / 1024.0)
        fileSize < 1024 * 1024 * 1024 -> String.format("%.2f MB", fileSize / (1024.0 * 1024))
        else -> String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024))
    }
}
