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
import androidx.compose.ui.Modifier
import co.juneai.june.navigation.GalleryApp
import co.juneai.june.ui.theme.GalleryAppTheme
import co.juneai.june.ui.theme.ThemePreferences
import co.juneai.june.utils.PermissionHandler
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** Set to true by VideoPlayerScreen when video is actively playing */
        @Volatile
        var isVideoPlaying = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Seed the global theme state from SharedPreferences
        ThemePreferences.initialize(this)
        enableEdgeToEdge()
        setContent {
            // Reads the global mutableStateOf — recomposes when theme changes
            GalleryAppTheme(theme = ThemePreferences.currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionHandler {
                        GalleryApp()
                    }
                }
            }
        }
    }

    /**
     * Automatically enter PiP mode when the user presses the home button
     * while a video is playing. The PiP window is resizable with a 16:9 aspect ratio.
     */
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