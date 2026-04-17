package co.juneai.june.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val mediaId: Long,
    val encryptedUri: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true
)