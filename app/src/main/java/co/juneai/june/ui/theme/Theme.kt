package co.juneai.june.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class AppTheme {
    LIGHT, DARK, SYSTEM, OCEAN, FOREST, SUNSET, ALPINE
}

// CompositionLocal so any part of the tree can check if current theme is dark
val LocalIsDarkTheme = compositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    onBackground = LightOnSurface,
    surfaceVariant = EarthSand,
    onSurfaceVariant = NavUnselected,
    outline = EarthSand,
    inverseSurface = KiwiDark,
    inverseOnSurface = EarthCream,
    primaryContainer = Kiwi,
    onPrimaryContainer = SelectedText,
    tertiary = KiwiAccent,
    onTertiary = Color(0xFF1A2E14),
    tertiaryContainer = KiwiAccentLight,
    onTertiaryContainer = KiwiAccentDark
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkMutedText,
    background = DarkBackground,
    surface = DarkCard,
    onSurface = DarkOnSurface,
    onBackground = DarkOnSurface,
    surfaceVariant = DarkBorder,
    onSurfaceVariant = DarkNavUnselected,
    outline = DarkBorder,
    inverseSurface = Color.White,
    inverseOnSurface = DarkBg,
    primaryContainer = DarkActiveBtn,
    onPrimaryContainer = DarkActiveTxt,
    tertiary = KiwiAccent,
    onTertiary = DarkBg,
    tertiaryContainer = KiwiAccentDark,
    onTertiaryContainer = KiwiAccentLight
)

private val OceanColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = OceanOnPrimary,
    secondary = OceanSecondary,
    background = OceanBackground,
    surface = OceanSurface,
    onSurface = OceanOnSurface,
    onBackground = OceanOnBackground,
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = OceanSecondary,
    outline = OceanOutline,
    inverseSurface = OceanInverseSurface,
    inverseOnSurface = OceanInverseOnSurface,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OceanOnPrimaryContainer,
    tertiary = KiwiAccent,
    onTertiary = Color(0xFF0A3A00),
    tertiaryContainer = KiwiAccentLight,
    onTertiaryContainer = KiwiAccentDark
)

private val ForestColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = ForestOnPrimary,
    secondary = ForestSecondary,
    background = ForestBackground,
    surface = ForestSurface,
    onSurface = ForestOnSurface,
    onBackground = ForestOnBackground,
    surfaceVariant = ForestSurfaceVariant,
    onSurfaceVariant = ForestSecondary,
    outline = ForestOutline,
    inverseSurface = ForestInverseSurface,
    inverseOnSurface = ForestInverseOnSurface,
    primaryContainer = ForestPrimaryContainer,
    onPrimaryContainer = ForestOnPrimaryContainer,
    tertiary = KiwiAccent,
    onTertiary = Color(0xFF1A2E14),
    tertiaryContainer = KiwiAccentLight,
    onTertiaryContainer = KiwiAccentDark
)

private val SunsetColorScheme = lightColorScheme(
    primary = SunsetPrimary,
    onPrimary = SunsetOnPrimary,
    secondary = SunsetSecondary,
    background = SunsetBackground,
    surface = SunsetSurface,
    onSurface = SunsetOnSurface,
    onBackground = SunsetOnBackground,
    surfaceVariant = SunsetSurfaceVariant,
    onSurfaceVariant = SunsetSecondary,
    outline = SunsetOutline,
    inverseSurface = SunsetInverseSurface,
    inverseOnSurface = SunsetInverseOnSurface,
    primaryContainer = SunsetPrimaryContainer,
    onPrimaryContainer = SunsetOnPrimaryContainer,
    tertiary = KiwiAccent,
    onTertiary = Color(0xFF2E1500),
    tertiaryContainer = KiwiAccentLight,
    onTertiaryContainer = KiwiAccentDark
)

private val AlpineColorScheme = lightColorScheme(
    primary = Alpine334155,
    onPrimary = AlpineFFFFFF,
    secondary = Alpine64748B,
    background = AlpineE2E8F0,
    surface = AlpineF1F5F9,
    onSurface = Alpine1E293B,
    onBackground = Alpine0F172A,
    surfaceVariant = Alpine475569,
    onSurfaceVariant = Alpine94A3B8,
    outline = Alpine94A3B8,
    inverseSurface = Alpine0F172A,
    inverseOnSurface = AlpineF8FAFC,
    primaryContainer = Alpine0F172A,
    onPrimaryContainer = AlpineFFFFFF,
    tertiary = KiwiAccent,
    onTertiary = Alpine0F172A,
    tertiaryContainer = KiwiAccentLight,
    onTertiaryContainer = KiwiAccentDark
)

@Composable
fun KiwiPhotosTheme(
    theme: AppTheme = AppTheme.LIGHT,
    content: @Composable () -> Unit
) {
    val isDark = when (theme) {
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        else -> false
    }

    val colorScheme = when (theme) {
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.SYSTEM -> if (isDark) DarkColorScheme else LightColorScheme
        AppTheme.OCEAN -> OceanColorScheme
        AppTheme.FOREST -> ForestColorScheme
        AppTheme.SUNSET -> SunsetColorScheme
        AppTheme.ALPINE -> AlpineColorScheme
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIsDarkTheme provides isDark
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Helper to persist and read the selected theme from SharedPreferences.
 */
object ThemePreferences {
    private const val PREFS_NAME = "june_theme_prefs"
    private const val KEY_THEME = "selected_theme"

    /** Global observable state so Compose recomposes when theme changes */
    var currentTheme by mutableStateOf(AppTheme.LIGHT)
        private set

    /** Call once from Activity.onCreate to seed from SharedPreferences */
    fun initialize(context: Context) {
        currentTheme = loadTheme(context)
    }

    fun saveTheme(context: Context, theme: AppTheme) {
        currentTheme = theme
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.name)
            .apply()
    }

    fun loadTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME, AppTheme.LIGHT.name) ?: AppTheme.LIGHT.name
        return try {
            AppTheme.valueOf(name)
        } catch (_: Exception) {
            AppTheme.LIGHT
        }
    }
}