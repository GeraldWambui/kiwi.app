package co.juneai.june.data.model

data class VaultMediaJson(
    val id: String,
    val originalPath: String,
    val vaultPath: String,
    val type: String,
    val timestamp: Long,
    val thumbnail: String?,
    val fileName: String?,
    val fileSize: Long,
    val isEncrypted: Boolean
)