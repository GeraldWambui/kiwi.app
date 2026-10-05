package co.juneai.june.performance

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import co.juneai.june.data.model.ImageData

import co.juneai.june.ui.screen.settings.PasscodeScreen
import co.juneai.june.ui.screen.settings.SettingsScreen
import co.juneai.june.ui.screen.settings.StorageDetailsScreen
import co.juneai.june.ui.screen.trash.TrashBinScreen
import co.juneai.june.ui.theme.AppTheme
import co.juneai.june.ui.theme.KiwiPhotosTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI performance tests.
 *
 * These tests measure initial composition time and verify that screens
 * render within acceptable time budgets. They catch regressions where
 * heavy computation in @Composable functions blocks the UI thread.
 */
class ComposePerformanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Test: Settings screen renders quickly ───────────────

    @Test
    fun settingsScreen_initialComposition_completesUnder2Seconds() {
        val start = System.nanoTime()

        composeTestRule.setContent {
            KiwiPhotosTheme(theme = AppTheme.DARK) {
                SettingsScreen(
                    onNavigateToStorage = {},
                    onNavigateToVault = {},
                    onNavigateToTrash = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        // Settings screen should compose under 2 seconds on any device
        assert(elapsed < 2000.0) {
            "SettingsScreen composition took ${elapsed}ms, expected <2000ms"
        }

        // Verify key elements are rendered
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("ON-DEVICE AI & SEARCH ENGINE").assertIsDisplayed()
    }

    // ── Test: Passcode screen renders without lag ───────────

    @Test
    fun passcodeScreen_initialComposition_completesUnder2Seconds() {
        val start = System.nanoTime()

        composeTestRule.setContent {
            KiwiPhotosTheme(theme = AppTheme.DARK) {
                PasscodeScreen(
                    onSuccess = {},
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assert(elapsed < 2000.0) {
            "PasscodeScreen composition took ${elapsed}ms, expected <2000ms"
        }

        composeTestRule.onNodeWithText("Enter Passcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        composeTestRule.onNodeWithText("9").assertIsDisplayed()
        composeTestRule.onNodeWithText("CANCEL").assertIsDisplayed()
    }

    // ── Test: Storage Details screen renders quickly ─────────

    @Test
    fun storageDetailsScreen_initialComposition_completesUnder2Seconds() {
        val start = System.nanoTime()

        composeTestRule.setContent {
            KiwiPhotosTheme(theme = AppTheme.DARK) {
                StorageDetailsScreen(onBack = {})
            }
        }

        composeTestRule.waitForIdle()
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assert(elapsed < 2000.0) {
            "StorageDetailsScreen composition took ${elapsed}ms, expected <2000ms"
        }

        composeTestRule.onNodeWithText("Storage Details").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Usage").assertIsDisplayed()
    }

    // ── Test: Trash screen renders quickly (empty state) ────

    @Test
    fun trashScreen_emptyState_rendersQuickly() {
        val start = System.nanoTime()

        composeTestRule.setContent {
            KiwiPhotosTheme(theme = AppTheme.DARK) {
                TrashBinScreen(onBack = {})
            }
        }

        composeTestRule.waitForIdle()
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        assert(elapsed < 2000.0) {
            "TrashBinScreen composition took ${elapsed}ms, expected <2000ms"
        }

        composeTestRule.onNodeWithText("Trash").assertIsDisplayed()
    }

    // ── Test: Settings screen scroll stability ──────────────

    @Test
    fun settingsScreen_noRecompositionCrash_onMultipleIdleCycles() {
        composeTestRule.setContent {
            KiwiPhotosTheme(theme = AppTheme.DARK) {
                SettingsScreen(
                    onNavigateToStorage = {},
                    onNavigateToVault = {},
                    onNavigateToTrash = {},
                    onBack = {}
                )
            }
        }

        // Trigger multiple idle cycles to detect recomposition loops
        repeat(5) {
            composeTestRule.waitForIdle()
        }

        // If we get here without a crash, recomposition is stable
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
