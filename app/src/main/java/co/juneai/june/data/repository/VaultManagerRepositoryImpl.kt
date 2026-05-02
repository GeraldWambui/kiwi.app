package co.juneai.june.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import co.juneai.june.data.local.db.KiwiPhotosDatabase
import co.juneai.june.data.local.extensions.fromEntity
import co.juneai.june.data.local.extensions.toEntity
import co.juneai.june.data.model.MediaMetadata
import co.juneai.june.data.model.VaultMedia
import co.juneai.june.data.model.VaultMediaType
import co.juneai.june.data.model.VaultStatistics
import co.juneai.june.domain.model.VaultAuthMode
import co.juneai.june.domain.repository.VaultManagerRepository
import co.juneai.june.ui.screen.vault.VaultException
import co.juneai.june.ui.screen.vault.VaultResult
import co.juneai.june.utils.CryptoManager
import co.juneai.june.utils.SecurityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.inject.Inject
import javax.inject.Singleton

// FIX: preferencesDataStore MUST be a file-level extension property.
// Defining it inside a class body compiles but creates a new DataStore
// instance per call-site, corrupting reads and writes under concurrent access.
private val Context.vaultDataStore by preferencesDataStore(name = "vault_settings")

@Singleton
class VaultManagerRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: KiwiPhotosDatabase,
    private val securityHelper: SecurityHelper
) : VaultManagerRepository {

    private val dao = database.vaultDao()

    private val dataStore = context.vaultDataStore

    companion object {
        private val VAULT_ENABLED    = booleanPreferencesKey("vault_enabled")
        private val VAULT_LOCKED     = booleanPreferencesKey("vault_locked")
        private val VAULT_CONFIGURED = booleanPreferencesKey("vault_configured")
        private val VAULT_PIN_HASH   = stringPreferencesKey("vault_pin_hash")
        private val VAULT_PIN_SALT   = stringPreferencesKey("vault_pin_salt")
        private val VAULT_AUTH_MODE  = stringPreferencesKey("vault_auth_mode")
        private val VAULT_CREATED_AT = longPreferencesKey("vault_created_at")
        private val VAULT_MEDIA_COUNT = longPreferencesKey("media_count")

        private const val MAX_VAULT_SIZE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
    }

    // ─────────────────────────── Vault lifecycle ───────────────────────────

    override suspend fun enableVault() {
        dataStore.edit { it[VAULT_ENABLED] = true }
    }

    /**
     * Call once after PIN (and optional biometric) are fully configured.
     * Records the creation timestamp and marks the vault as ready.
     */
    override suspend fun markVaultConfigured() {
        dataStore.edit {
            it[VAULT_CONFIGURED] = true
            it[VAULT_CREATED_AT] = System.currentTimeMillis()
        }
    }

    override fun isVaultConfigured(): Flow<Boolean> =
        dataStore.data.map { it[VAULT_CONFIGURED] ?: false }

    override suspend fun setVaultLocked(locked: Boolean) {
        dataStore.edit { it[VAULT_LOCKED] = locked }
    }

    override fun isVaultEnabled(): Flow<Boolean> =
        dataStore.data.map { it[VAULT_ENABLED] ?: false }

    override fun isVaultLocked(): Flow<Boolean> =
        dataStore.data.map { it[VAULT_LOCKED] ?: true }

    // ─────────────────────────── Auth mode ───────────────────────────

    override suspend fun setAuthMode(mode: VaultAuthMode) {
        dataStore.edit { it[VAULT_AUTH_MODE] = mode.name }
    }

    override fun getAuthMode(): Flow<VaultAuthMode?> =
        dataStore.data.map { prefs ->
            prefs[VAULT_AUTH_MODE]
                ?.let { runCatching { VaultAuthMode.valueOf(it) }.getOrNull() }
        }

    // ─────────────────────────── PIN ───────────────────────────

    override suspend fun setVaultPin(pin: String): VaultResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val pinHash = securityHelper.hashPin(pin)
                dataStore.edit {
                    it[VAULT_PIN_HASH] = pinHash.hash
                    it[VAULT_PIN_SALT] = pinHash.salt
                }
                VaultResult.Success(Unit)
            } catch (e: Exception) {
                VaultResult.Error(
                    VaultException(VaultException.ErrorType.SECURITY, "Failed to set PIN", e)
                )
            }
        }

    override suspend fun verifyVaultPin(pin: String): VaultResult<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val prefs = dataStore.data.first()
                val hash  = prefs[VAULT_PIN_HASH] ?: return@withContext VaultResult.Success(false)
                val salt  = prefs[VAULT_PIN_SALT] ?: return@withContext VaultResult.Success(false)
                VaultResult.Success(securityHelper.verifyPin(pin, hash, salt))
            } catch (e: Exception) {
                VaultResult.Error(
                    VaultException(VaultException.ErrorType.SECURITY, "PIN verification failed", e)
                )
            }
        }

    // ─────────────────────────── Media CRUD ───────────────────────────

    override suspend fun addToVault(
        originalFile: File,
        encryptedFile: File,
        type: VaultMediaType,
        metadata: MediaMetadata?
    ): VaultResult<VaultMedia> = withContext(Dispatchers.IO) {
        try {
            val media = VaultMedia(
                id           = UUID.randomUUID().toString(),
                originalPath = originalFile.absolutePath,
                vaultPath    = encryptedFile.absolutePath,
                type         = type,
                timestamp    = System.currentTimeMillis(),
                fileName     = originalFile.name,
                fileSize     = originalFile.length(),
                isEncrypted  = true,
                mimeType     = metadata?.mimeType,
                width        = metadata?.width,
                height       = metadata?.height,
                duration     = metadata?.duration
            )
            dao.insertMedia(media.toEntity())
            updateMediaCount()
            VaultResult.Success(media)
        } catch (e: Exception) {
            VaultResult.Error(
                VaultException(VaultException.ErrorType.STORAGE, "Failed to add media to vault", e)
            )
        }
    }

    override fun observeVaultMedia(): Flow<VaultResult<List<VaultMedia>>> =
        dao.getAllMedia()
            .map<_, VaultResult<List<VaultMedia>>> { entities ->
                VaultResult.Success(entities.map { it.fromEntity() })
            }
            .catch { e ->
                emit(
                    VaultResult.Error(
                        VaultException(VaultException.ErrorType.IO, "Database error", e)
                    )
                )
            }

    /**
     * Removes the DB record AND deletes the encrypted file.
     * Callers must NOT separately delete the file to avoid double-deletion.
     */
    override suspend fun removeFromVault(mediaId: String): VaultResult<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val entity = dao.getMediaById(mediaId)
                    ?: return@withContext VaultResult.Error(
                        VaultException(VaultException.ErrorType.NOT_FOUND, "Media not found in vault")
                    )
                File(entity.vaultPath).delete()
                dao.deleteMedia(entity)
                updateMediaCount()
                VaultResult.Success(true)
            } catch (e: Exception) {
                VaultResult.Error(
                    VaultException(VaultException.ErrorType.IO, "Failed to remove media", e)
                )
            }
        }

    override suspend fun getVaultStatistics(): VaultStatistics = withContext(Dispatchers.IO) {
        val count     = dao.getMediaCount()
        val totalSize = dao.getTotalSize() ?: 0L
        val images    = dao.getMediaByType(VaultMediaType.IMAGE).first().size
        val videos    = dao.getMediaByType(VaultMediaType.VIDEO).first().size
        VaultStatistics(
            totalItems     = count,
            imagesCount    = images,
            videosCount    = videos,
            totalSize      = totalSize,
            remainingSpace = MAX_VAULT_SIZE_BYTES - totalSize
        )
    }

    // ─────────────────────────── Bulk operations ───────────────────────────

    override suspend fun addMultipleToVault(
        files: List<Pair<File, VaultMediaType>>,
        onProgress: (Int, Int) -> Unit
    ): VaultResult<List<VaultMedia>> = coroutineScope {
        val results   = Collections.synchronizedList(mutableListOf<VaultMedia>())
        val total     = files.size
        val completed = AtomicInteger(0)
        try {
            files.map { (file, type) ->
                async(Dispatchers.IO) {
                    runCatching {
                        val outFile = createVaultFile()
                        encryptFile(file, outFile)
                        addToVault(file, outFile, type)
                    }.getOrNull()?.let {
                        if (it is VaultResult.Success) results.add(it.data)
                    }
                    withContext(Dispatchers.Main) {
                        onProgress(completed.incrementAndGet(), total)
                    }
                }
            }.awaitAll()
            VaultResult.Success(results)
        } catch (e: Exception) {
            VaultResult.Error(
                VaultException(VaultException.ErrorType.STORAGE, "Batch add failed", e)
            )
        }
    }

    // ─────────────────────────── Encryption ───────────────────────────

    /**
     * AES-256-GCM streaming encryption with atomic write (temp → rename).
     * IV length is stored as a leading byte so decryption can recover it.
     */
    override suspend fun encryptFile(input: File, output: File) = withContext(Dispatchers.IO) {
        try {
            val temp   = File("${output.absolutePath}.tmp")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, CryptoManager.getSecretKey())
            }
            val iv = cipher.iv

            FileOutputStream(temp).use { fos ->
                fos.write(iv.size)
                fos.write(iv)
                CipherOutputStream(BufferedOutputStream(fos), cipher).use { cos ->
                    BufferedInputStream(FileInputStream(input)).copyTo(cos, bufferSize = 64 * 1024)
                }
                fos.fd.sync()
            }

            if (!temp.renameTo(output)) {
                temp.delete()
                throw IOException("Atomic rename failed for ${output.name}")
            }
        } catch (e: AEADBadTagException) {
            throw VaultException(
                VaultException.ErrorType.SECURITY, "File integrity compromised", e
            )
        }
    }

    // ─────────────────────────── Maintenance ───────────────────────────

    override suspend fun cleanupOrphanedFiles(): VaultResult<Int> = withContext(Dispatchers.IO) {
        try {
            val validPaths = dao.getAllMedia().first()
                .map { File(it.vaultPath).canonicalPath }
                .toSet()

            var count = 0
            File(context.filesDir, "vault").listFiles()?.forEach { file ->
                if (file.isFile && file.canonicalPath !in validPaths) {
                    if (file.delete()) count++
                }
            }
            VaultResult.Success(count)
        } catch (e: Exception) {
            VaultResult.Error(
                VaultException(VaultException.ErrorType.IO, "Cleanup failed", e)
            )
        }
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private fun createVaultFile(): File {
        val dir = File(context.filesDir, "vault").apply { mkdirs() }
        return File(dir, UUID.randomUUID().toString())
    }

    private suspend fun updateMediaCount() {
        val count = dao.getMediaCount()
        dataStore.edit { it[VAULT_MEDIA_COUNT] = count.toLong() }
    }
}