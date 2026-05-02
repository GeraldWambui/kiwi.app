package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import co.juneai.june.data.model.VaultMediaType

@Entity(tableName = "vault_media")
data class VaultMediaEntity(
    @PrimaryKey
    val id: String,
    val originalPath: String,
    val vaultPath: String,
    val type: VaultMediaType,
    val timestamp: Long,
    val fileName: String,
    val fileSize: Long,
    val isEncrypted: Boolean,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Long? = null,
    val thumbnail: String? = null
)