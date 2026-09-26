package co.juneai.june.ui.screen.settings

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import co.juneai.june.ml.model.ModelManager
import co.juneai.june.service.IndexImagesWorker
import co.juneai.june.ui.theme.AppTheme
import co.juneai.june.ui.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val application: Context,
    private val modelManager: ModelManager,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>,
    private val embeddingBox: Box<ImageEmbedding>
) : ViewModel() {

    companion object {
        val KEY_NSFW_PROTECTION = booleanPreferencesKey("nsfw_protection_enabled")
        val KEY_NSFW_THRESHOLD = floatPreferencesKey("nsfw_sensitivity_threshold")
        val KEY_INDEX_CHARGING_ONLY = booleanPreferencesKey("index_charging_only")
        val KEY_INDEX_VIDEOS = booleanPreferencesKey("index_videos_enabled")
        val KEY_TRASH_RETENTION = intPreferencesKey("trash_retention_days")
        val KEY_VIDEO_AUTOPLAY = booleanPreferencesKey("video_autoplay_enabled")
    }

    private val _cloudSyncEnabled = MutableStateFlow(false)
    val cloudSyncEnabled: StateFlow<Boolean> = _cloudSyncEnabled.asStateFlow()

    private val _faceIdEnabled = MutableStateFlow(false)
    val faceIdEnabled: StateFlow<Boolean> = _faceIdEnabled.asStateFlow()

    private val _optimizeStorageEnabled = MutableStateFlow(true)
    val optimizeStorageEnabled: StateFlow<Boolean> = _optimizeStorageEnabled.asStateFlow()

    private val _storageBreakdown = MutableStateFlow(StorageBreakdown())
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    private val _isNsfwProtectionEnabled = MutableStateFlow(true)
    val isNsfwProtectionEnabled: StateFlow<Boolean> = _isNsfwProtectionEnabled.asStateFlow()

    private val _nsfwThreshold = MutableStateFlow(0.55f)
    val nsfwThreshold: StateFlow<Float> = _nsfwThreshold.asStateFlow()

    private val _indexChargingOnly = MutableStateFlow(true)
    val indexChargingOnly: StateFlow<Boolean> = _indexChargingOnly.asStateFlow()

    private val _indexVideosEnabled = MutableStateFlow(true)
    val indexVideosEnabled: StateFlow<Boolean> = _indexVideosEnabled.asStateFlow()

    private val _trashRetentionDays = MutableStateFlow(30)
    val trashRetentionDays: StateFlow<Int> = _trashRetentionDays.asStateFlow()

    private val _videoAutoPlayEnabled = MutableStateFlow(true)
    val videoAutoPlayEnabled: StateFlow<Boolean> = _videoAutoPlayEnabled.asStateFlow()

    val activeModelId: StateFlow<String> = modelManager.activeModelIdFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ModelManager.DEFAULT_MODEL_ID
    )

    private val _availableModels = MutableStateFlow(modelManager.getAvailableModels())
    val availableModels: StateFlow<List<ModelManager.ModelInfo>> = _availableModels.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    private val _indexingProgress = MutableStateFlow(0)
    val indexingProgress: StateFlow<Int> = _indexingProgress.asStateFlow()

    private val _totalIndexed = MutableStateFlow(0)
    val totalIndexed: StateFlow<Int> = _totalIndexed.asStateFlow()

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

    private val _batteryState = MutableStateFlow(co.juneai.june.utils.BatteryStatusManager.getBatteryState(application))
    val batteryState: StateFlow<co.juneai.june.utils.BatteryState> = _batteryState.asStateFlow()

    fun refreshBatteryState(): co.juneai.june.utils.BatteryState {
        val state = co.juneai.june.utils.BatteryStatusManager.getBatteryState(application)
        _batteryState.value = state
        return state
    }

    init {
        loadStorageInfo()
        loadPreferences()
        observeWorkManager()
        updateIndexedCount()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _isNsfwProtectionEnabled.value = prefs[KEY_NSFW_PROTECTION] ?: true
                _nsfwThreshold.value = prefs[KEY_NSFW_THRESHOLD] ?: 0.55f
                _indexChargingOnly.value = prefs[KEY_INDEX_CHARGING_ONLY] ?: true
                _indexVideosEnabled.value = prefs[KEY_INDEX_VIDEOS] ?: true
                _trashRetentionDays.value = prefs[KEY_TRASH_RETENTION] ?: 30
                _videoAutoPlayEnabled.value = prefs[KEY_VIDEO_AUTOPLAY] ?: true
            }
        }
    }

    private fun observeWorkManager() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(IndexImagesWorker.WORK_NAME).collect { workInfos ->
                val activeInfo = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                if (activeInfo != null) {
                    _isIndexing.value = true
                    _indexingProgress.value = activeInfo.progress.getInt(IndexImagesWorker.KEY_PROGRESS, 0)
                } else {
                    _isIndexing.value = false
                    updateIndexedCount()
                }
            }
        }
    }

    fun updateIndexedCount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = embeddingBox.query(ImageEmbedding_.embedding.notNull()).build().count().toInt()
                _totalIndexed.value = count
            } catch (e: Exception) {
                Timber.e(e, "Failed to count indexed embeddings")
            }
        }
    }

    fun triggerReindex(forceAll: Boolean = false, ignoreConstraints: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<IndexImagesWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    IndexImagesWorker.KEY_FORCE_REINDEX to forceAll,
                    IndexImagesWorker.KEY_IGNORE_CONSTRAINTS to ignoreConstraints
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            IndexImagesWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        _isIndexing.value = true
        _indexingProgress.value = 0
    }

    fun triggerIndexNowIgnoringConstraints() {
        triggerReindex(forceAll = false, ignoreConstraints = true)
    }

    private fun loadStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = Environment.getDataDirectory()
                val stat = StatFs(path.path)
                val blockSize = stat.blockSizeLong
                val totalBlocks = stat.blockCountLong
                val availableBlocks = stat.availableBlocksLong

                val totalCapacity = totalBlocks * blockSize
                val free = availableBlocks * blockSize
                val totalUsed = totalCapacity - free

                val photosEst = (totalUsed * 0.40).toLong()
                val videosEst = (totalUsed * 0.35).toLong()
                val otherEst = (totalUsed * 0.25).toLong()

                _storageBreakdown.value = StorageBreakdown(
                    photosBytes = photosEst,
                    videosBytes = videosEst,
                    otherBytes = otherEst,
                    freeBytes = free,
                    totalCapacityBytes = totalCapacity,
                    totalUsedBytes = totalUsed
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load storage breakdown")
            }
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            modelManager.setActiveModel(modelId)
            _availableModels.value = modelManager.getAvailableModels()
        }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            _downloadProgress.value = 0f
            val success = modelManager.downloadModel(modelId) { progress ->
                _downloadProgress.value = progress
            }
            _downloadProgress.value = null
            _availableModels.value = modelManager.getAvailableModels()
        }
    }

    fun toggleNsfwProtection() {
        viewModelScope.launch {
            val newValue = !_isNsfwProtectionEnabled.value
            _isNsfwProtectionEnabled.value = newValue
            dataStore.edit { prefs ->
                prefs[KEY_NSFW_PROTECTION] = newValue
            }
        }
    }

    fun setNsfwThreshold(threshold: Float) {
        viewModelScope.launch {
            _nsfwThreshold.value = threshold
            dataStore.edit { prefs ->
                prefs[KEY_NSFW_THRESHOLD] = threshold
            }
        }
    }

    fun setIndexChargingOnly(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[KEY_INDEX_CHARGING_ONLY] = enabled }
            _indexChargingOnly.value = enabled
        }
    }

    fun setIndexVideosEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[KEY_INDEX_VIDEOS] = enabled }
            _indexVideosEnabled.value = enabled
        }
    }

    fun setTrashRetentionDays(days: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[KEY_TRASH_RETENTION] = days }
            _trashRetentionDays.value = days
        }
    }

    fun setVideoAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[KEY_VIDEO_AUTOPLAY] = enabled }
            _videoAutoPlayEnabled.value = enabled
        }
    }

    fun clearAppCache(): Long {
        return try {
            val cacheDir = application.cacheDir
            val size = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            loadStorageInfo()
            size
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear app cache")
            0L
        }
    }

    fun clearCache(): Long = clearAppCache()

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
            .putString("vault_passcode_plain", passcode)
            .putBoolean("vault_configured", true)
            .apply()
        _isVaultConfigured.value = true
    }

    fun getStoredPasscode(): String? {
        return application.getSharedPreferences("june_vault", 0)
            .getString("vault_passcode_plain", null)
    }

    fun savePasscodePlain(passcode: String) {
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
}
