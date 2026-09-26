package co.juneai.june.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class SelfHostedProvider(val displayName: String, val defaultPort: Int) {
    IMMICH("Immich", 2283),
    NEXTCLOUD("Nextcloud", 443),
    WEBDAV("Generic WebDAV / NAS", 443)
}

data class SelfHostedConfig(
    val provider: SelfHostedProvider = SelfHostedProvider.IMMICH,
    val serverUrl: String = "",
    val apiKeyOrToken: String = "",
    val username: String = "",
    val password: String = "",
    val backupWifiOnly: Boolean = true,
    val backupChargingOnly: Boolean = false,
    val backupVideos: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    val targetDirectory: String = "KiwiPhotos"
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && when (provider) {
            SelfHostedProvider.IMMICH -> apiKeyOrToken.isNotBlank()
            SelfHostedProvider.NEXTCLOUD, SelfHostedProvider.WEBDAV -> 
                username.isNotBlank() && (password.isNotBlank() || apiKeyOrToken.isNotBlank())
        }
}

@Singleton
class SelfHostedConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "kiwi_self_hosted_secure_prefs"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_WIFI_ONLY = "backup_wifi_only"
        private const val KEY_CHARGING_ONLY = "backup_charging_only"
        private const val KEY_BACKUP_VIDEOS = "backup_videos"
        private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
        private const val KEY_TARGET_DIR = "target_directory"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Fallback to standard shared preferences")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<SelfHostedConfig> = _configFlow.asStateFlow()

    fun loadConfig(): SelfHostedConfig {
        val providerStr = prefs.getString(KEY_PROVIDER, SelfHostedProvider.IMMICH.name)
        val provider = try {
            SelfHostedProvider.valueOf(providerStr ?: SelfHostedProvider.IMMICH.name)
        } catch (_: Exception) {
            SelfHostedProvider.IMMICH
        }

        return SelfHostedConfig(
            provider = provider,
            serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: "",
            apiKeyOrToken = prefs.getString(KEY_API_KEY, "") ?: "",
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            backupWifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
            backupChargingOnly = prefs.getBoolean(KEY_CHARGING_ONLY, false),
            backupVideos = prefs.getBoolean(KEY_BACKUP_VIDEOS, true),
            autoBackupEnabled = prefs.getBoolean(KEY_AUTO_BACKUP, false),
            targetDirectory = prefs.getString(KEY_TARGET_DIR, "KiwiPhotos") ?: "KiwiPhotos"
        )
    }

    fun saveConfig(config: SelfHostedConfig) {
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_SERVER_URL, config.serverUrl.trim().removeSuffix("/"))
            .putString(KEY_API_KEY, config.apiKeyOrToken.trim())
            .putString(KEY_USERNAME, config.username.trim())
            .putString(KEY_PASSWORD, config.password)
            .putBoolean(KEY_WIFI_ONLY, config.backupWifiOnly)
            .putBoolean(KEY_CHARGING_ONLY, config.backupChargingOnly)
            .putBoolean(KEY_BACKUP_VIDEOS, config.backupVideos)
            .putBoolean(KEY_AUTO_BACKUP, config.autoBackupEnabled)
            .putString(KEY_TARGET_DIR, config.targetDirectory.trim())
            .apply()

        _configFlow.value = config
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
        _configFlow.value = SelfHostedConfig()
    }
}
