package co.juneai.june.ui.screen.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.juneai.june.data.local.entities.BackupRecord
import co.juneai.june.data.local.entities.BackupRecord_
import co.juneai.june.data.local.prefs.SelfHostedConfig
import co.juneai.june.data.local.prefs.SelfHostedConfigManager
import co.juneai.june.data.local.prefs.SelfHostedProvider
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.network.backup.ConnectionResult
import co.juneai.june.network.backup.SelfHostedClientFactory
import co.juneai.june.service.SelfHostedBackupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val version: String, val latencyMs: Long) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

data class BackupUiProgress(
    val isRunning: Boolean = false,
    val progressPercent: Int = 0,
    val totalItems: Int = 0,
    val backedUpItems: Int = 0,
    val currentFileName: String = ""
)

@HiltViewModel
class SelfHostedBackupViewModel @Inject constructor(
    private val configManager: SelfHostedConfigManager,
    private val workManager: WorkManager,
    private val mediaStoreRepository: MediaStoreRepository,
    private val backupBox: Box<BackupRecord>
) : ViewModel() {

    val config: StateFlow<SelfHostedConfig> = configManager.configFlow

    private val _connectionState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionState: StateFlow<ConnectionTestState> = _connectionState.asStateFlow()

    private val _backupProgress = MutableStateFlow(BackupUiProgress())
    val backupProgress: StateFlow<BackupUiProgress> = _backupProgress.asStateFlow()

    private val _totalMediaOnDevice = MutableStateFlow(0)
    val totalMediaOnDevice: StateFlow<Int> = _totalMediaOnDevice.asStateFlow()

    private val _backedUpCount = MutableStateFlow(0)
    val backedUpCount: StateFlow<Int> = _backedUpCount.asStateFlow()

    init {
        loadCounts()
        observeBackupWork()
    }

    fun loadCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val media = mediaStoreRepository.loadAllMedia()
                _totalMediaOnDevice.value = media.size
                val backedUp = backupBox.query().build().count().toInt()
                _backedUpCount.value = backedUp
            } catch (e: Exception) {
                Timber.e(e, "Failed to load backup stats")
            }
        }
    }

    private fun observeBackupWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(SelfHostedBackupWorker.WORK_NAME).collect { workInfos ->
                val active = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                if (active != null) {
                    val progress = active.progress.getInt(SelfHostedBackupWorker.KEY_PROGRESS, 0)
                    val total = active.progress.getInt(SelfHostedBackupWorker.KEY_TOTAL, _totalMediaOnDevice.value)
                    val backedUp = active.progress.getInt(SelfHostedBackupWorker.KEY_BACKED_UP_COUNT, _backedUpCount.value)
                    val currentFile = active.progress.getString(SelfHostedBackupWorker.KEY_CURRENT_FILE) ?: ""

                    _backupProgress.value = BackupUiProgress(
                        isRunning = true,
                        progressPercent = progress,
                        totalItems = total,
                        backedUpItems = backedUp,
                        currentFileName = currentFile
                    )
                } else {
                    _backupProgress.value = BackupUiProgress(isRunning = false)
                    loadCounts()
                }
            }
        }
    }

    fun testConnection(tempConfig: SelfHostedConfig? = null) {
        val targetConfig = tempConfig ?: config.value
        if (targetConfig.serverUrl.isBlank()) {
            _connectionState.value = ConnectionTestState.Error("Please enter a server URL")
            return
        }

        viewModelScope.launch {
            _connectionState.value = ConnectionTestState.Testing
            val client = SelfHostedClientFactory.create(targetConfig)
            val result = client.testConnection()
            if (result.success) {
                _connectionState.value = ConnectionTestState.Success(
                    version = result.serverVersion,
                    latencyMs = result.latencyMs
                )
            } else {
                _connectionState.value = ConnectionTestState.Error(
                    message = result.errorMessage ?: "Failed to connect to server"
                )
            }
        }
    }

    fun saveConfig(newConfig: SelfHostedConfig) {
        configManager.saveConfig(newConfig)
        if (newConfig.autoBackupEnabled) {
            schedulePeriodicBackup(newConfig)
        } else {
            workManager.cancelUniqueWork("self_hosted_periodic_backup")
        }
    }

    fun triggerBackupNow() {
        val cfg = config.value
        if (!cfg.isConfigured) return

        val constraints = Constraints.Builder().apply {
            if (cfg.backupWifiOnly) {
                setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
            if (cfg.backupChargingOnly) {
                setRequiresCharging(true)
            }
        }.build()

        val request = OneTimeWorkRequestBuilder<SelfHostedBackupWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            SelfHostedBackupWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelBackup() {
        workManager.cancelUniqueWork(SelfHostedBackupWorker.WORK_NAME)
        _backupProgress.value = BackupUiProgress(isRunning = false)
    }

    private fun schedulePeriodicBackup(cfg: SelfHostedConfig) {
        val constraints = Constraints.Builder().apply {
            if (cfg.backupWifiOnly) setRequiredNetworkType(NetworkType.UNMETERED)
            else setRequiredNetworkType(NetworkType.CONNECTED)
            if (cfg.backupChargingOnly) setRequiresCharging(true)
        }.build()

        val periodicWork = PeriodicWorkRequestBuilder<SelfHostedBackupWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "self_hosted_periodic_backup",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    fun resetBackupRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            backupBox.removeAll()
            loadCounts()
        }
    }
}
