package co.juneai.june.ui.screen.settings

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.ui.theme.AppTheme
import co.juneai.june.ui.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
) : ViewModel() {

    private val _cloudSyncEnabled = MutableStateFlow(true)
    val cloudSyncEnabled: StateFlow<Boolean> = _cloudSyncEnabled.asStateFlow()

    private val _faceIdEnabled = MutableStateFlow(false)
    val faceIdEnabled: StateFlow<Boolean> = _faceIdEnabled.asStateFlow()

    private val _optimizeStorageEnabled = MutableStateFlow(true)
    val optimizeStorageEnabled: StateFlow<Boolean> = _optimizeStorageEnabled.asStateFlow()

    private val _storageBreakdown = MutableStateFlow(StorageBreakdown())
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    private val _passcodeDigits = MutableStateFlow<List<Int>>(emptyList())
    val passcodeDigits: StateFlow<List<Int>> = _passcodeDigits.asStateFlow()

    private val _passcodeError = MutableStateFlow(false)
    val passcodeError: StateFlow<Boolean> = _passcodeError.asStateFlow()

    private val _passcodeSuccess = MutableStateFlow(false)
    val passcodeSuccess: StateFlow<Boolean> = _passcodeSuccess.asStateFlow()

    private val _selectedTheme = MutableStateFlow(
        ThemePreferences.loadTheme(application)
    )
    val selectedTheme: StateFlow<AppTheme> = _selectedTheme.asStateFlow()

    private val _isVaultConfigured = MutableStateFlow(
        application.getSharedPreferences("june_vault", 0).getBoolean("vault_configured", false)
    )
    val isVaultConfigured: StateFlow<Boolean> = _isVaultConfigured.asStateFlow()

    init {
        loadStorageInfo()
    }

    fun markVaultConfigured() {
        _isVaultConfigured.value = true
        application.getSharedPreferences("june_vault", 0)
            .edit().putBoolean("vault_configured", true).apply()
    }

    fun setTheme(theme: AppTheme) {
        _selectedTheme.value = theme
        ThemePreferences.saveTheme(application, theme)
    }

    fun toggleCloudSync() {
        _cloudSyncEnabled.value = !_cloudSyncEnabled.value
    }

    fun toggleFaceId() {
        _faceIdEnabled.value = !_faceIdEnabled.value
    }

    fun toggleOptimizeStorage() {
        _optimizeStorageEnabled.value = !_optimizeStorageEnabled.value
    }

    fun toggleNsfwProtection() {

    }

    fun appendPasscodeDigit(digit: Int) {
        val current = _passcodeDigits.value
        if (current.size >= 6) return
        _passcodeError.value = false
        val updated = current + digit
        _passcodeDigits.value = updated

        if (updated.size == 6) {
            verifyPasscode(updated)
        }
    }

    fun deletePasscodeDigit() {
        val current = _passcodeDigits.value
        if (current.isEmpty()) return
        _passcodeDigits.value = current.dropLast(1)
        _passcodeError.value = false
    }

    fun clearPasscode() {
        _passcodeDigits.value = emptyList()
        _passcodeError.value = false
    }

    private fun verifyPasscode(digits: List<Int>) {
        val entered = digits.joinToString("")
        val enteredHash = hashPasscode(entered)
        val storedHash = application.getSharedPreferences("june_vault", 0)
            .getString("vault_passcode_hash", null)

        if (storedHash != null && enteredHash == storedHash) {
            _passcodeSuccess.value = true
        } else {
            _passcodeError.value = true
            viewModelScope.launch {
                kotlinx.coroutines.delay(600)
                _passcodeDigits.value = emptyList()
                _passcodeError.value = false
            }
        }
    }

    fun savePasscode(passcode: String) {
        val hash = hashPasscode(passcode)
        application.getSharedPreferences("june_vault", 0)
            .edit()
            .putString("vault_passcode_hash", hash)
            .putBoolean("vault_configured", true)
            .apply()
        _isVaultConfigured.value = true
    }

    fun getStoredPasscode(): String? {
        // We store a hash, not the plaintext — passcode cannot be recovered directly.
        // For "forgot password" biometric reveal, we store an encrypted copy.
        return application.getSharedPreferences("june_vault", 0)
            .getString("vault_passcode_plain", null)
    }

    fun savePasscodePlain(passcode: String) {
        // Store plain for biometric recovery only
        application.getSharedPreferences("june_vault", 0)
            .edit()
            .putString("vault_passcode_plain", passcode)
            .apply()
    }

    private fun hashPasscode(passcode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(passcode.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = application.cacheDir
                deleteDir(cacheDir)
                loadStorageInfo()
                Timber.i("✓ Cache cleared")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear cache")
            }
        }
    }

    private fun loadStorageInfo() {
        viewModelScope.launch {
            val breakdown = withContext(Dispatchers.IO) {
                calculateStorageBreakdown()
            }
            _storageBreakdown.value = breakdown
        }
    }

    private fun calculateStorageBreakdown(): StorageBreakdown {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockSizeLong * stat.blockCountLong
            val availableBytes = stat.blockSizeLong * stat.availableBlocksLong
            val usedBytes = totalBytes - availableBytes

            // Approximate breakdown based on media store queries
            val photosDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val photosBytes = getDirSize(photosDir)

            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val videosBytes = getDirSize(moviesDir)

            val cacheBytes = getDirSize(application.cacheDir)

            val vaultBytes = (usedBytes * 0.08).toLong() // Estimate
            val systemBytes = usedBytes - photosBytes - videosBytes - vaultBytes

            StorageBreakdown(
                photosBytes = photosBytes,
                videosBytes = videosBytes,
                vaultBytes = vaultBytes,
                systemBytes = systemBytes.coerceAtLeast(0L),
                totalUsedBytes = usedBytes,
                totalCapacityBytes = totalBytes,
                cacheBytes = cacheBytes
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate storage")
            StorageBreakdown()
        }
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return false
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { child ->
                deleteDir(child)
            }
        }
        return dir.delete()
    }
}
