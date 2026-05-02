package co.juneai.june.ui.screen.vault

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.data.model.VaultMedia
import co.juneai.june.data.model.VaultMediaType
import co.juneai.june.domain.model.VaultAuthMode
import co.juneai.june.domain.repository.VaultManagerRepository
import co.juneai.june.utils.formatDate
import co.juneai.june.utils.formatSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// VaultItem – the UI model.
//
// mediaId  : the canonical UUID string stored in the database; used for all
//            repository calls.  DO NOT pass `id.toString()` to the repo.
// id       : stable Long for Set<Long> selection tracking only.
// ─────────────────────────────────────────────────────────────────────────────

data class MediaDetails(
    val id: Long,
    val fileName: String,
    val fileSize: String,
    val type: VaultMediaType,
    val resolution: String,
    val path: String,
    val addedDate: String,
    val mimeType: String,
    val isEncrypted: Boolean
)

data class VaultUiStatistics(
    val totalItems: Int,
    val imagesCount: Int,
    val videosCount: Int,
    val totalSize: Long
) {
    fun getFormattedSize(): String = formatSize(totalSize)
}

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val application: Application,
    private val vaultManager: VaultManagerRepository
) : ViewModel() {

    // ── Vault content ──────────────────────────────────────────────────────
    private val _vaultItems = MutableStateFlow<List<VaultItem>>(emptyList())
    val vaultItems: StateFlow<List<VaultItem>> = _vaultItems.asStateFlow()

    // ── Locking state ─────────────────────────────────────────────────────
    private val _vaultLocked = MutableStateFlow(true)
    val vaultLocked: StateFlow<Boolean> = _vaultLocked.asStateFlow()

    private val _vaultConfigured = MutableStateFlow(false)
    val vaultConfigured: StateFlow<Boolean> = _vaultConfigured.asStateFlow()

    // ── Auth mode (for unlock screen to know what to show) ────────────────
    private val _authMode = MutableStateFlow<VaultAuthMode?>(null)
    val authMode: StateFlow<VaultAuthMode?> = _authMode.asStateFlow()

    // ── Loading / messages ────────────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // ── Selection ─────────────────────────────────────────────────────────
    private val _selectedItems = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItems: StateFlow<Set<Long>> = _selectedItems.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    // ── Detail / video overlay ─────────────────────────────────────────────
    private val _showDetailsDialog = MutableStateFlow(false)
    val showDetailsDialog: StateFlow<Boolean> = _showDetailsDialog.asStateFlow()

    private val _selectedVaultItem = MutableStateFlow<VaultItem?>(null)
    val selectedVaultItem: StateFlow<VaultItem?> = _selectedVaultItem.asStateFlow()

    private val _showVideoPlayer = MutableStateFlow(false)
    val showVideoPlayer: StateFlow<Boolean> = _showVideoPlayer.asStateFlow()

    private val _currentVideoUri = MutableStateFlow<Uri?>(null)
    val currentVideoUri: StateFlow<Uri?> = _currentVideoUri.asStateFlow()

    // ── Delete confirmation ───────────────────────────────────────────────
    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private val _itemToDelete = MutableStateFlow<VaultItem?>(null)
    val itemToDelete: StateFlow<VaultItem?> = _itemToDelete.asStateFlow()

    // ── Search / filter ───────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow<VaultMediaType?>(null)
    val filterType: StateFlow<VaultMediaType?> = _filterType.asStateFlow()

    // ── Derived: filtered items ───────────────────────────────────────────
    val filteredItems: StateFlow<List<VaultItem>> = combine(
        _vaultItems, _searchQuery, _filterType
    ) { items, query, type ->
        items
            .let { if (type != null) it.filter { i -> i.type == type } else it }
            .let {
                if (query.isNotBlank()) it.filter { i ->
                    i.fileName.contains(
                        query,
                        ignoreCase = true
                    )
                } else it
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Derived: statistics ───────────────────────────────────────────────
    val vaultStatistics: StateFlow<VaultUiStatistics?> = _vaultItems.map { items ->
        if (items.isNotEmpty()) VaultUiStatistics(
            totalItems = items.size,
            imagesCount = items.count { it.type == VaultMediaType.IMAGE },
            videosCount = items.count { it.type == VaultMediaType.VIDEO },
            totalSize = items.sumOf { it.fileSize }
        ) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ─────────────────────────── Init ───────────────────────────────────────

    init {
        // Observe configuration / auth-mode once.
        viewModelScope.launch {
            vaultManager.isVaultConfigured().collect { _vaultConfigured.value = it }
        }
        viewModelScope.launch {
            vaultManager.getAuthMode().collect { _authMode.value = it }
        }

        // FIX: Single reactive chain – replaces the two separate observers in
        //      the original code (one in init, one re-launched by loadVaultItems).
        //      flatMapLatest cancels the inner Flow automatically when lock state
        //      changes, so no stale collectors ever accumulate.
        viewModelScope.launch {
            vaultManager.isVaultLocked()
                .flatMapLatest { locked ->
                    _vaultLocked.value = locked
                    if (locked) {
                        // Vault is locked: clear items and stop observing DB.
                        _vaultItems.value = emptyList()
                        kotlinx.coroutines.flow.emptyFlow()
                    } else {
                        vaultManager.observeVaultMedia()
                    }
                }
                .collect { result ->
                    when (result) {
                        is VaultResult.Success -> _vaultItems.value = result.data.toVaultItems()
                        is VaultResult.Error -> _errorMessage.value = result.error.message
                    }
                }
        }
    }

    // ─────────────────────────── Vault setup ────────────────────────────────

    /**
     * Called by [VaultSetupScreen] after PIN has been confirmed (and biometric
     * registered when mode == BOTH).  Persists PIN hash, auth mode, and marks
     * the vault as configured/enabled atomically.
     */
    fun setupVaultWithPin(
        pin: String,
        mode: VaultAuthMode,
        onComplete: (success: Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val r = vaultManager.setVaultPin(pin)) {
                    is VaultResult.Error -> {
                        _errorMessage.value = r.error.message
                        onComplete(false)
                        return@launch
                    }

                    is VaultResult.Success -> Unit
                }
                vaultManager.setAuthMode(mode)
                vaultManager.enableVault()
                vaultManager.markVaultConfigured()
                onComplete(true)
            } catch (e: Exception) {
                _errorMessage.value = e.message
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Called by [VaultSetupScreen] for BIOMETRIC_ONLY mode after the system
     * biometric prompt succeeds (PIN is not set in this path).
     */
    fun setupVaultBiometricOnly(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                vaultManager.setAuthMode(VaultAuthMode.BIOMETRIC_ONLY)
                vaultManager.enableVault()
                vaultManager.markVaultConfigured()
                onComplete(true)
            } catch (e: Exception) {
                _errorMessage.value = e.message
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─────────────────────────── Lock / unlock ──────────────────────────────

    /**
     * Verifies [pin] (if provided), then sets the vault to unlocked.
     * The reactive chain in [init] reacts to the lock-state change and
     * automatically starts loading vault media.
     */
    fun unlockVault(pin: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (pin != null) {
                    when (val r = vaultManager.verifyVaultPin(pin)) {
                        is VaultResult.Error -> {
                            _errorMessage.value = "Authentication error"; return@launch
                        }

                        is VaultResult.Success -> if (!r.data) {
                            _errorMessage.value = "Invalid PIN"; return@launch
                        }
                    }
                }
                vaultManager.setVaultLocked(false)
                _errorMessage.value = null
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun lockVault() {
        viewModelScope.launch {
            vaultManager.setVaultLocked(true)
            _isSelectionMode.value = false
            _selectedItems.value = emptySet()
            _showVideoPlayer.value = false
            _showDetailsDialog.value = false
        }
    }

    // ─────────────────────────── Media management ───────────────────────────

    /**
     * Encrypts [file] via the repository's AES-GCM implementation and adds it
     * to the vault DB.
     *
     * FIX: The original ViewModel had a private encryptFile() that just *copied*
     *      the file unencrypted.  All encryption now goes through the repo.
     */
    fun addMedia(
        file: File,
        type: VaultMediaType,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val destDir = File(application.filesDir, "vault").apply { mkdirs() }
                val destFile = File(destDir, "${UUID.randomUUID()}.enc")

                // Real AES-GCM encryption – repository owns the key.
                vaultManager.encryptFile(file, destFile)

                when (val r = vaultManager.addToVault(file, destFile, type)) {
                    is VaultResult.Success -> {
                        _toastMessage.value = "Added to vault"; onComplete(true)
                    }

                    is VaultResult.Error -> {
                        _errorMessage.value = r.error.message; onComplete(false)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Removes a single item.
     *
     * FIX 1 – Uses [VaultItem.mediaId] (the UUID), not `item.id.toString()`.
     * FIX 2 – Does NOT delete the file itself; [VaultManagerRepositoryImpl.removeFromVault]
     *          already deletes the encrypted file as part of the transaction.
     */
    fun removeFromVault(item: VaultItem) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = vaultManager.removeFromVault(item.mediaId)) {
                is VaultResult.Success -> {
                    _toastMessage.value = "Removed from vault"
                    clearSelectionOf(item.id)
                }

                is VaultResult.Error -> _errorMessage.value = r.error.message
            }
            _isLoading.value = false
        }
    }

    fun restoreToGallery(item: VaultItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val source = File(item.vaultPath ?: item.uri.path ?: "")
                if (!source.exists()) {
                    _errorMessage.value = "File not found"; onComplete(false); return@launch
                }

                val restoredUri = saveToGallery(source, item)
                if (restoredUri != null) {
                    // Remove DB record + encrypted file via repo (no double-deletion).
                    vaultManager.removeFromVault(item.mediaId)
                    _toastMessage.value = "Restored to gallery"
                    onComplete(true)
                } else {
                    _errorMessage.value = "Failed to restore"
                    onComplete(false)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePermanently(item: VaultItem) {
        viewModelScope.launch {
            _isLoading.value = true
            // Repo deletes the file; no separate File.delete() needed here.
            when (val r = vaultManager.removeFromVault(item.mediaId)) {
                is VaultResult.Success -> {
                    _toastMessage.value = "Deleted permanently"
                    _showDeleteConfirmation.value = false
                    _itemToDelete.value = null
                }

                is VaultResult.Error -> _errorMessage.value = r.error.message
            }
            _isLoading.value = false
        }
    }

    fun deleteMultiplePermanently() {
        viewModelScope.launch {
            _isLoading.value = true
            val ids = _selectedItems.value.toList()
            try {
                // Match selected Long IDs back to mediaId strings.
                val mediaIds = _vaultItems.value
                    .filter { it.id in ids }
                    .map { it.mediaId }

                mediaIds.forEach { vaultManager.removeFromVault(it) }

                _selectedItems.value = emptySet()
                _isSelectionMode.value = false
                _toastMessage.value = "Deleted ${mediaIds.size} items"
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeSelectedFromVault() {
        viewModelScope.launch {
            _isLoading.value = true
            val ids = _selectedItems.value.toList()
            try {
                val mediaIds = _vaultItems.value
                    .filter { it.id in ids }
                    .map { it.mediaId }

                mediaIds.forEach { vaultManager.removeFromVault(it) }

                _selectedItems.value = emptySet()
                _isSelectionMode.value = false
                _toastMessage.value = "Removed ${mediaIds.size} items from vault"
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─────────────────────────── Selection ──────────────────────────────────

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) _selectedItems.value = emptySet()
    }

    fun toggleItemSelection(id: Long) {
        _selectedItems.value = _selectedItems.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        if (_selectedItems.value.isEmpty()) _isSelectionMode.value = false
    }

    fun selectAll() {
        _selectedItems.value = filteredItems.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    // ─────────────────────────── Detail / video ─────────────────────────────

    fun showMediaDetails(item: VaultItem) {
        _selectedVaultItem.value = item
        _showDetailsDialog.value = true
    }

    fun closeDetailsDialog() {
        _showDetailsDialog.value = false
        _selectedVaultItem.value = null
    }

    fun getMediaDetails(item: VaultItem) = MediaDetails(
        id = item.id,
        fileName = item.fileName,
        fileSize = formatSize(item.fileSize),
        type = item.type,
        resolution = if (item.width > 0 && item.height > 0) "${item.width}×${item.height}" else "Unknown",
        path = item.vaultPath ?: item.uri.toString(),
        addedDate = formatDate(item.timestamp),
        mimeType = item.mimeType,
        isEncrypted = item.isEncrypted
    )

    fun playVideo(item: VaultItem) {
        _currentVideoUri.value = item.vaultPath?.let { Uri.fromFile(File(it)) } ?: item.uri
        _showVideoPlayer.value = true
    }

    fun closeVideoPlayer() {
        _showVideoPlayer.value = false
        _currentVideoUri.value = null
    }

    // ─────────────────────────── Delete confirmation ────────────────────────

    fun confirmDelete(item: VaultItem) {
        _itemToDelete.value = item
        _showDeleteConfirmation.value = true
    }

    fun cancelDelete() {
        _showDeleteConfirmation.value = false
        _itemToDelete.value = null
    }

    // ─────────────────────────── Search / filter ────────────────────────────

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun setFilterType(type: VaultMediaType?) {
        _filterType.value = type
    }

    // ─────────────────────────── Messages ───────────────────────────────────

    fun clearMessages() {
        _errorMessage.value = null
        _toastMessage.value = null
    }

    // ─────────────────────────── Private helpers ────────────────────────────

    private fun List<VaultMedia>.toVaultItems(): List<VaultItem> =
        mapIndexed { index, media ->
            VaultItem(
                mediaId = media.id,
                uri = Uri.fromFile(File(media.vaultPath)),
                fileName = media.fileName,
                isVerified = index == 0,
                fileSize = media.fileSize,
                width = media.width ?: 0,
                height = media.height ?: 0,
                mimeType = media.mimeType ?: defaultMime(media.type),
                type = media.type,
                vaultPath = media.vaultPath,
                timestamp = media.timestamp,
                isEncrypted = media.isEncrypted
            )
        }

    private fun clearSelectionOf(id: Long) {
        if (_selectedItems.value.contains(id)) {
            _selectedItems.value = _selectedItems.value - id
            if (_selectedItems.value.isEmpty()) _isSelectionMode.value = false
        }
    }

    private suspend fun saveToGallery(file: File, item: VaultItem): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Vault")
                }
                val uri = if (item.type == VaultMediaType.IMAGE)
                    application.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                else
                    application.contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        values
                    )

                uri?.also { dest ->
                    application.contentResolver.openOutputStream(dest)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
            }.getOrNull()
        }

    private fun defaultMime(type: VaultMediaType) =
        if (type == VaultMediaType.IMAGE) "image/*" else "video/*"
}