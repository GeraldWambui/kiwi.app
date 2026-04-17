package co.juneai.june.ui.screen.vault

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class VaultItem(
    val id: Long,
    val uri: Uri,
    val fileName: String,
    val isVerified: Boolean = false,
    val fileSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "image/jpeg"
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val _vaultItems = MutableStateFlow<List<VaultItem>>(emptyList())
    val vaultItems: StateFlow<List<VaultItem>> = _vaultItems.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItems: StateFlow<Set<Long>> = _selectedItems.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    init {
        loadVaultItems()
    }

    private fun loadVaultItems() {
        viewModelScope.launch {
            _isLoading.value = true
            val items = withContext(Dispatchers.IO) {
                queryVaultMedia()
            }
            _vaultItems.value = items
            _isLoading.value = false
        }
    }

    /**
     * Query media from a "Vault" folder or tagged items.
     * Falls back to latest favorites as vault demo data.
     */
    private fun queryVaultMedia(): List<VaultItem> {
        val items = mutableListOf<VaultItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )

        // Try vault folder first, then fall back to favorites
        val selection = "${MediaStore.Images.Media.IS_FAVORITE} = 1"
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            application.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "$sortOrder LIMIT 20"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    items.add(
                        VaultItem(
                            id = id,
                            uri = Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id.toString()
                            ),
                            fileName = cursor.getString(nameCol) ?: "vault_$id",
                            isVerified = items.size == 0, // First item gets verified badge
                            fileSize = cursor.getLong(sizeCol),
                            width = cursor.getInt(widthCol),
                            height = cursor.getInt(heightCol),
                            mimeType = cursor.getString(mimeCol) ?: "image/jpeg"
                        )
                    )
                }
            }
        } catch (_: Exception) { }

        return items
    }

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedItems.value = emptySet()
        }
    }

    fun toggleItemSelection(id: Long) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedItems.value = current
    }

    fun selectAll() {
        _selectedItems.value = _vaultItems.value.map { it.id }.toSet()
    }

    fun removeSelectedFromVault() {
        val selected = _selectedItems.value
        _vaultItems.value = _vaultItems.value.filterNot { it.id in selected }
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    fun refresh() {
        loadVaultItems()
    }
}
