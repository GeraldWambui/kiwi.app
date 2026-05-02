package co.juneai.june.domain.repository

import co.juneai.june.data.model.MediaMetadata
import co.juneai.june.data.model.VaultMedia
import co.juneai.june.data.model.VaultMediaType
import co.juneai.june.data.model.VaultStatistics
import co.juneai.june.domain.model.VaultAuthMode
import co.juneai.june.ui.screen.vault.VaultResult
import kotlinx.coroutines.flow.Flow
import java.io.File

interface VaultManagerRepository {
    suspend fun enableVault()
    suspend fun addMultipleToVault(files: List<Pair<File, VaultMediaType>>, onProgress: (Int, Int) -> Unit = { _, _ -> }): VaultResult<List<VaultMedia>>
    suspend fun setVaultLocked(locked: Boolean)
    suspend fun verifyVaultPin(pin: String): VaultResult<Boolean>
    suspend fun setVaultPin(pin: String): VaultResult<Unit>
    fun isVaultEnabled(): Flow<Boolean>
    fun isVaultLocked(): Flow<Boolean>
    suspend fun addToVault(
        originalFile: File,
        encryptedFile: File,
        type: VaultMediaType,
        metadata: MediaMetadata? = null
    ): VaultResult<VaultMedia>
    suspend fun getVaultStatistics(): VaultStatistics
    suspend fun encryptFile(input: File, output: File)
    suspend fun cleanupOrphanedFiles(): VaultResult<Int>
    suspend fun removeFromVault(mediaId: String): VaultResult<Boolean>
    fun observeVaultMedia(): Flow<VaultResult<List<VaultMedia>>>

    suspend fun setAuthMode(mode: VaultAuthMode)
    fun getAuthMode(): Flow<VaultAuthMode?>
    fun isVaultConfigured(): Flow<Boolean>
    suspend fun markVaultConfigured()
}