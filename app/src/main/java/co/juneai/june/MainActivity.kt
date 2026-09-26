package co.juneai.june

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.juneai.june.ml.model.ModelManager
import co.juneai.june.navigation.KiwiPhotosNavigation
import co.juneai.june.ui.screen.download.ModelAssetDownloadScreen
import co.juneai.june.ui.theme.KiwiPhotosTheme
import co.juneai.june.ui.theme.ThemePreferences
import co.juneai.june.utils.PermissionHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var modelManager: ModelManager

    companion object {
        @Volatile
        var isVideoPlaying = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.initialize(this)
        enableEdgeToEdge()
        setContent {
            KiwiPhotosTheme(theme = ThemePreferences.currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionHandler {
                        var isModelReady by remember {
                            mutableStateOf(modelManager.isPrimaryModelDownloaded())
                        }
                        var hasSkippedDownload by remember { mutableStateOf(false) }

                        if (isModelReady || hasSkippedDownload) {
                            KiwiPhotosNavigation()
                        } else {
                            ModelAssetDownloadScreen(
                                onDownloadComplete = {
                                    isModelReady = true
                                },
                                onSkip = {
                                    hasSkippedDownload = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}