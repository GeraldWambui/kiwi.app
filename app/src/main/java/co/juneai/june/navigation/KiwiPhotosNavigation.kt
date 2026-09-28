package co.juneai.june.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.CompositionLocalProvider
import co.juneai.june.ui.animation.LocalNavAnimatedVisibilityScope
import co.juneai.june.ui.animation.SharedElementContainer
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import co.juneai.june.ui.ext.KiwiPhotosBottomNavBar
import co.juneai.june.ui.ext.bottomNavItems
import co.juneai.june.ui.screen.albums.AlbumsScreen
import co.juneai.june.ui.screen.foryou.ForYouScreen
import co.juneai.june.ui.screen.mediadetail.MediaDetailScreen
import co.juneai.june.ui.screen.backup.SelfHostedBackupScreen
import co.juneai.june.ui.screen.cleaner.StorageCleanerScreen
import co.juneai.june.ui.screen.map.PhotoMapScreen
import co.juneai.june.ui.screen.people.PeopleScreen
import co.juneai.june.ui.screen.photos.PhotoScreen
import co.juneai.june.ui.screen.referral.ReferralScreen
import co.juneai.june.ui.screen.search.SearchScreen
import co.juneai.june.ui.screen.settings.PasscodeScreen
import co.juneai.june.ui.screen.settings.SettingsScreen
import co.juneai.june.ui.screen.settings.StorageDetailsScreen
import co.juneai.june.ui.screen.subscriptions.SubscriptionScreen
import co.juneai.june.ui.screen.trash.TrashBinScreen
import co.juneai.june.ui.screen.vault.ForgotPasswordScreen
import co.juneai.june.ui.screen.vault.SecureVaultScreen
import co.juneai.june.ui.screen.vault.VaultSetupScreen
import co.juneai.june.ui.screen.videoplayer.VideoPlayerScreen
import co.juneai.june.ui.voice.KiwiListeningOverlay
import co.juneai.june.ui.voice.KiwiVoiceService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiwiPhotosNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomBarRoutes = bottomNavItems.map { it.route }
    val showBottomBar = currentRoute?.substringBefore("?") in bottomBarRoutes
    val activity = LocalActivity.current
    val intentUri = activity?.intent?.data?.toString()

    // Voice activation state
    val context = LocalContext.current
    val voiceService = remember { KiwiVoiceService(context) }
    val voiceResult by voiceService.voiceResult.collectAsState()
    var showVoiceOverlay by remember { mutableStateOf(false) }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showVoiceOverlay = true
            voiceService.startCommandListening()
        }
    }

    // Handle mic tap from child screens
    val onVoiceSearchTap: () -> Unit = {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMicPermission) {
            showVoiceOverlay = true
            voiceService.startCommandListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(intentUri) {
        if (intentUri != null) {
            when (intentUri) {
                "june://recents", "june://photos" -> {
                    navController.navigate(Screens.Library.route) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    }
                }
                "june://camera_album" -> {
                    navController.navigate(Screens.Albums.route + "?autoSelect=Camera") {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    }
                }
                "june://videos" -> {
                    navController.navigate(Screens.Albums.route + "?autoSelect=Videos") {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    }
                }
            }
            activity.intent?.data = null
        }
    }


    // Handle voice commands
    LaunchedEffect(voiceResult.command) {
        val cmd = voiceResult.command ?: return@LaunchedEffect
        showVoiceOverlay = false
        voiceService.stopListening()
        when (cmd) {
            is KiwiVoiceService.KiwiCommand.OpenSettings ->
                navController.navigate(Screens.Settings.route)

            is KiwiVoiceService.KiwiCommand.OpenAlbums ->
                navController.navigate(Screens.Albums.route)

            is KiwiVoiceService.KiwiCommand.OpenPhotos ->
                navController.navigate(Screens.Library.route)

            is KiwiVoiceService.KiwiCommand.OpenVault ->
                navController.navigate("vault_setup")

            is KiwiVoiceService.KiwiCommand.OpenTrash ->
                navController.navigate("trash")

            is KiwiVoiceService.KiwiCommand.Search -> { 
                navController.navigate(Screens.Search.route + "?query=${Uri.encode(cmd.query)}") {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            is KiwiVoiceService.KiwiCommand.PlayVideo -> {
                    /* Could search videos */
            }

            is KiwiVoiceService.KiwiCommand.Unknown -> {
                    /* No action */
            }
        }
    }

    Box {
        Scaffold(
            modifier = Modifier,
            bottomBar = {
                if (showBottomBar) {
                    KiwiPhotosBottomNavBar(
                        currentRoute = currentRoute,
                        onTabSelected = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            },
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                SharedElementContainer {
                    NavHost(
                        navController = navController,
                        startDestination = Screens.Library.route,
                        enterTransition = {
                            fadeIn(animationSpec = tween(220)) +
                                    scaleIn(initialScale = 0.96f, animationSpec = tween(220))
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180))
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(220))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(180)) +
                                    scaleOut(targetScale = 0.96f, animationSpec = tween(180))
                        }
                    ) {
                        composable(Screens.Library.route) {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                                PhotoScreen(
                                    onMediaClick = { mediaId, albumId ->
                                        navController.navigate("detail/$mediaId?albumId=${albumId ?: 0}")
                                    },
                                    onNavigateToVideo = { uri, title ->
                                        val encodedUri = URLEncoder.encode(uri, "UTF-8")
                                        val encodedTitle = URLEncoder.encode(title ?: "Video", "UTF-8")
                                        navController.navigate("video/$encodedUri/$encodedTitle")
                                    },
                                    onNavigateToAlbums = {
                                        navController.navigate(Screens.Albums.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate(Screens.Settings.route)
                                    },
                                    onVoiceSearch = onVoiceSearchTap
                                )
                            }
                        }

                        composable(Screens.ForYou.route) {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                                ForYouScreen(
                                    onMediaClick = { mediaId ->
                                        navController.navigate("detail/$mediaId?albumId=0")
                                    }
                                )
                            }
                        }

                    // ── Search ────────────────────
                    composable(
                        route = Screens.Search.route + "?query={query}",
                        arguments = listOf(navArgument("query") { defaultValue = "" })
                    ) { backStackEntry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val initialQuery = backStackEntry.arguments?.getString("query") ?: ""
                            SearchScreen(
                                initialQuery = initialQuery,
                                onVoiceSearch = onVoiceSearchTap,
                                onNavigateToMedia = { mediaId, albumId ->
                                    navController.navigate("detail/$mediaId?albumId=${albumId ?: 0}")
                                },
                                onNavigateToVideo = { uri, title, startTimeMs ->
                                    val encodedUri = URLEncoder.encode(uri, "UTF-8")
                                    val encodedTitle = URLEncoder.encode(title ?: "Video", "UTF-8")
                                    navController.navigate("video/$encodedUri/$encodedTitle?startTimeMs=${startTimeMs ?: 0L}")
                                },
                                onNavigateToRoute = { route ->
                                    navController.navigate(route)
                                }
                            )
                        }
                    }

                    // ── Albums ──────────────────────────────────
                    composable(
                        route = Screens.Albums.route + "?autoSelect={autoSelect}",
                        arguments = listOf(navArgument("autoSelect") { defaultValue = "" })
                    ) { backStackEntry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val autoSelect = backStackEntry.arguments?.getString("autoSelect").takeIf { it?.isNotEmpty() == true }
                            AlbumsScreen(
                                initialAlbumName = autoSelect,
                                onAlbumClick = { album ->
                                    // Album detail handled in AlbumsScreen locally
                                },
                                onNavigateToVault = {
                                    val isVaultConfigured = navController.context
                                        .getSharedPreferences("june_vault", 0)
                                        .getBoolean("vault_configured", false)
                                    if (isVaultConfigured) {
                                        navController.navigate(Screens.Passcode.route)
                                    } else {
                                        navController.navigate(Screens.VaultSetup.route)
                                    }
                                },
                                onNavigateToTrash = {
                                    navController.navigate(Screens.Trash.route)
                                },
                                onNavigateToPeople = {
                                    navController.navigate(Screens.People.route)
                                },
                                onNavigateToMap = {
                                    navController.navigate(Screens.PhotoMap.route)
                                },
                                onNavigateToCleaner = {
                                    navController.navigate(Screens.StorageCleaner.route)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screens.Settings.route)
                                },
                                onNavigateToPhotos = {
                                    navController.navigate(Screens.Library.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onMediaClick = { mediaId, albumId ->
                                    navController.navigate("detail/$mediaId?albumId=${albumId ?: 0}")
                                },
                                onNavigateToVideo = { uri, title ->
                                    val encodedUri = URLEncoder.encode(uri, "UTF-8")
                                    val encodedTitle = URLEncoder.encode(title ?: "Video", "UTF-8")
                                    navController.navigate("video/$encodedUri/$encodedTitle")
                                },
                                onVoiceSearch = onVoiceSearchTap
                            )
                        }
                    }

                    // ── Settings ─────────────────────────────────
                    composable(Screens.Settings.route) {
                        SettingsScreen(
                            onNavigateToStorage = {
                                navController.navigate(Screens.StorageDetails.route)
                            },
                            onNavigateToVault = {
                                navController.navigate(Screens.Passcode.route)
                            },
                            onNavigateToVaultSetup = {
                                navController.navigate(Screens.VaultSetup.route)
                            },
                            onNavigateToTrash = {
                                navController.navigate(Screens.Trash.route)
                            },
                            onNavigateToCleaner = {
                                navController.navigate(Screens.StorageCleaner.route)
                            },
                            onNavigateToBackup = {
                                navController.navigate(Screens.SelfHostedBackup.route)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // ── Self-Hosted Backup ───────────────────────
                    composable(Screens.SelfHostedBackup.route) {
                        SelfHostedBackupScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // ── Storage Details ──────────────────────────
                    composable(Screens.StorageDetails.route) {
                        StorageDetailsScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToSubscription = {
                                navController.navigate(Screens.Subscription.route)
                            },
                            onNavigateToCleaner = {
                                navController.navigate(Screens.StorageCleaner.route)
                            }
                        )
                    }

                    // ── Subscription ─────────────────────────────
                    composable(Screens.Subscription.route) {
                        SubscriptionScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // ── Passcode ─────────────────────────────────
                    composable(Screens.Passcode.route) {
                        PasscodeScreen(
                            onSuccess = {
                                navController.navigate(Screens.SecureVault.route) {
                                    popUpTo(Screens.Passcode.route) { inclusive = true }
                                }
                            },
                            onCancel = { navController.popBackStack() },
                            onForgotPasscode = {
                                navController.navigate(Screens.ForgotPassword.route)
                            }
                        )
                    }

                    // ── Vault Setup (first time) ─────────────────
                    composable(Screens.VaultSetup.route) {
                        VaultSetupScreen(
                            onSetupComplete = {
                                navController.navigate(Screens.SecureVault.route) {
                                    popUpTo(Screens.VaultSetup.route) { inclusive = true }
                                }
                            },
                            onCancel = { navController.popBackStack() }
                        )
                    }

                    // ── Forgot Password ──────────────────────────
                    composable(Screens.ForgotPassword.route) {
                        ForgotPasswordScreen(
                            onResetComplete = {
                                navController.navigate(Screens.Passcode.route) {
                                    popUpTo(Screens.ForgotPassword.route) { inclusive = true }
                                }
                            },
                            onCancel = { navController.popBackStack() }
                        )
                    }

                    // ── Trash ────────────────────────────────────
                    composable(Screens.Trash.route) {
                        TrashBinScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // ── Secure Vault ─────────────────────────────
                    composable(Screens.SecureVault.route) {
                        SecureVaultScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // ── Referral ─────────────────────────────────
                    composable(Screens.Referral.route) {
                        ReferralScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // ── Detail ──────────────────────────────────
                    composable(
                        route = "detail/{mediaId}?albumId={albumId}",
                        arguments = listOf(
                            navArgument("mediaId") { type = NavType.LongType },
                            navArgument("albumId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            }
                        ),
                        enterTransition = {
                            fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing))
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing))
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing))
                        }
                    ) { backStackEntry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val mediaId = backStackEntry.arguments?.getLong("mediaId")
                            if (mediaId != null && mediaId != 0L) {
                                val albumId = backStackEntry.arguments?.getLong("albumId") ?: -1L

                                MediaDetailScreen(
                                    mediaId = mediaId,
                                    albumId = if (albumId != -1L) albumId else null,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToMedia = { },
                                    onNavigateToVideo = { uri, title ->
                                        val encodedUri = URLEncoder.encode(uri, "UTF-8")
                                        val encodedTitle = URLEncoder.encode(title ?: "Video", "UTF-8")
                                        navController.navigate("video/$encodedUri/$encodedTitle")
                                    }
                                )
                            }
                        }
                    }

                    // ── Video Player ────────────────────
                    composable(
                        route = "video/{videoUri}/{videoTitle}?startTimeMs={startTimeMs}",
                        arguments = listOf(
                            navArgument("videoUri") { type = NavType.StringType },
                            navArgument("videoTitle") {
                                type = NavType.StringType
                                defaultValue = "Video"
                            },
                            navArgument("startTimeMs") {
                                type = NavType.LongType
                                defaultValue = 0L
                            }
                        ),
                        enterTransition = {
                            fadeIn(animationSpec = tween(240)) +
                                    scaleIn(initialScale = 0.94f, animationSpec = tween(240))
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(180)) +
                                    scaleOut(targetScale = 0.94f, animationSpec = tween(180))
                        }
                    ) { backStackEntry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val rawVideoUri = backStackEntry.arguments?.getString("videoUri")
                            if (!rawVideoUri.isNullOrEmpty()) {
                                val videoUri = URLDecoder.decode(rawVideoUri, "UTF-8")
                                val videoTitle = URLDecoder.decode(
                                    backStackEntry.arguments?.getString("videoTitle") ?: "Video",
                                    "UTF-8"
                                )
                                val startTimeMs = backStackEntry.arguments?.getLong("startTimeMs") ?: 0L

                                VideoPlayerScreen(
                                    videoUri = videoUri,
                                    videoTitle = videoTitle,
                                    startTimeMs = startTimeMs,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                    // ── People & Pets ────────────────────────────
                    composable(Screens.People.route) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            PeopleScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onMediaClick = { mediaId, albumId ->
                                    navController.navigate("detail/$mediaId?albumId=${albumId ?: 0}")
                                }
                            )
                        }
                    }

                    // ── Photo Map ────────────────────────────────
                    composable(Screens.PhotoMap.route) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            PhotoMapScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onMediaClick = { mediaId, albumId ->
                                    navController.navigate("detail/$mediaId?albumId=${albumId ?: 0}")
                                }
                            )
                        }
                    }

                    // ── Storage Cleaner ──────────────────────────
                    composable(Screens.StorageCleaner.route) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            StorageCleanerScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onMediaClick = { mediaId, albumId ->
                                    navController.navigate("detail/$mediaId?albumId=${albumId ?: 0}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

        // Voice overlay — always on top
        KiwiListeningOverlay(
            isVisible = showVoiceOverlay,
            state = voiceResult.state,
            partialText = voiceResult.partialText,
            finalText = voiceResult.finalText,
            rmsLevel = voiceResult.rmsLevel,
            onDismiss = {
                showVoiceOverlay = false
                voiceService.stopListening()
            }
        )
    }
}