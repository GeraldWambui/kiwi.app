package co.juneai.june.ui.screen.videoplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import co.juneai.june.MainActivity
import co.juneai.june.R
import kotlinx.coroutines.delay

@SuppressLint("AutoboxingStateCreation")
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: String,
    videoTitle: String?,
    startTimeMs: Long = 0L,
    onBack: () -> Unit,
    onShare: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isInPipMode = remember(configuration) {
        (context as? Activity)?.isInPictureInPictureMode == true
    }

    // Theme-sourced colors
    val ControlBg = MaterialTheme.colorScheme.surfaceVariant
    val AccentGreen = MaterialTheme.colorScheme.tertiary
    val TabIconColor = MaterialTheme.colorScheme.onSurfaceVariant

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri.toUri()))
            prepare()
            if (startTimeMs > 0L) {
                seekTo(startTimeMs)
            }
            playWhenReady = true
        }
    }

    var showJumpPill by remember { mutableStateOf(startTimeMs > 0L) }
    LaunchedEffect(startTimeMs) {
        if (startTimeMs > 0L) {
            delay(3500)
            showJumpPill = false
        }
    }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var hasEnded by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var isShuffled by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var seekDirection by remember { mutableStateOf<SeekDirection?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val notifyUserInteraction = {
        lastInteractionTime = System.currentTimeMillis()
        if (!isInPipMode) {
            showControls = true
        }
    }

    val togglePlayPause = {
        if (player.playbackState == Player.STATE_ENDED || hasEnded) {
            hasEnded = false
            player.seekTo(0)
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        notifyUserInteraction()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_ENDED) {
                    hasEnded = true
                    isPlaying = false
                    showControls = true
                } else if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                currentPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Set the isVideoPlaying flag for auto-PiP on home button
    DisposableEffect(Unit) {
        MainActivity.isVideoPlaying = true
        onDispose {
            MainActivity.isVideoPlaying = false
            player.release()
        }
    }

    // Position updater coroutine when active
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(200)
        }
    }

    // Auto-hide controls timer with clean reset on interaction
    LaunchedEffect(showControls, isPlaying, lastInteractionTime, isInPipMode) {
        if (showControls && isPlaying && !isInPipMode) {
            delay(3500)
            showControls = false
        }
    }

    // In PiP mode, always hide controls
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            showControls = false
        }
    }

    // Handle seek animation
    LaunchedEffect(seekDirection) {
        seekDirection?.let {
            delay(600)
            seekDirection = null
        }
    }

    BackHandler {
        if (isLandscape) {
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )

        // Double-tap zones for seek
        Row(modifier = Modifier.fillMaxSize()) {
            // Left half — rewind 10s
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                hasEnded = false
                                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                seekDirection = SeekDirection.BACKWARD
                                notifyUserInteraction()
                            },
                            onTap = {
                                if (!isInPipMode) {
                                    if (showControls) {
                                        showControls = false
                                    } else {
                                        notifyUserInteraction()
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = seekDirection == SeekDirection.BACKWARD,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Text("10s", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Right half — forward 10s
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                hasEnded = false
                                player.seekTo(
                                    (player.currentPosition + 10_000)
                                        .coerceAtMost(player.duration)
                                )
                                seekDirection = SeekDirection.FORWARD
                                notifyUserInteraction()
                            },
                            onTap = {
                                if (!isInPipMode) {
                                    if (showControls) {
                                        showControls = false
                                    } else {
                                        notifyUserInteraction()
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = seekDirection == SeekDirection.FORWARD,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Text("10s", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Jump-to-Moment Pill Notification
        androidx.compose.animation.AnimatedVisibility(
            visible = showJumpPill,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { -it },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1C22).copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Jumped to matching scene (${formatTime(startTimeMs)})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Controls overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
            ) {
                // ── Top Bar: Close | [spacer] | PiP | More ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ✕ Close button
                    IconButton(onClick = {
                        if (isLandscape) {
                            (context as? Activity)?.requestedOrientation =
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } else {
                            onBack()
                        }
                    }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ControlBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ios_close),
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // PiP button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(onClick = { enterPiPMode(context) }) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(ControlBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_ios_pip),
                                    contentDescription = "Picture in Picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // ⋯ More menu
                    IconButton(onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, videoUri.toUri())
                            type = "video/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "Share video")
                        )
                    }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ControlBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ios_more),
                                contentDescription = "More",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // ── Center: Large Play/Pause ──
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isPlaying) R.drawable.ic_ios_pause else R.drawable.ic_ios_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // ── Bottom Controls ──
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    // Title + subtitle
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = videoTitle ?: "Video",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Gallery",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Seekbar with green accent
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { fraction ->
                            hasEnded = false
                            val seekPos = (fraction * duration).toLong()
                            player.seekTo(seekPos)
                            currentPosition = seekPos
                            notifyUserInteraction()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = AccentGreen,
                            inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                        )
                    )

                    // Time labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "-${formatTime((duration - currentPosition).coerceAtLeast(0))}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Playback controls: Shuffle | Prev | Play/Pause | Next | Repeat ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle
                        IconButton(onClick = {
                            isShuffled = !isShuffled
                            notifyUserInteraction()
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ios_forward),
                                contentDescription = "Shuffle",
                                tint = if (isShuffled) AccentGreen else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Previous
                        IconButton(onClick = {
                            hasEnded = false
                            player.seekTo(0)
                            notifyUserInteraction()
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ios_backward),
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Center Play/Pause (pill)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.ic_ios_pause else R.drawable.ic_ios_play
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Next
                        IconButton(onClick = {
                            hasEnded = false
                            player.seekTo((player.currentPosition + 30_000).coerceAtMost(player.duration))
                            notifyUserInteraction()
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ios_forward),
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Repeat / Loop
                        IconButton(onClick = {
                            isLooping = !isLooping
                            player.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            notifyUserInteraction()
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ios_loop),
                                contentDescription = "Loop",
                                tint = if (isLooping) AccentGreen else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Bottom Tabs: Audio | Subs | Speed | Share ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomTab(
                            icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Audio", tint = TabIconColor, modifier = Modifier.size(22.dp)) },
                            label = "AUDIO",
                            onClick = { notifyUserInteraction() }
                        )
                        BottomTab(
                            icon = { Icon(Icons.Default.ClosedCaption, contentDescription = "Subs", tint = TabIconColor, modifier = Modifier.size(22.dp)) },
                            label = "SUBS",
                            onClick = { notifyUserInteraction() }
                        )
                        BottomTab(
                            icon = { Icon(Icons.Default.Speed, contentDescription = "Speed", tint = TabIconColor, modifier = Modifier.size(22.dp)) },
                            label = "SPEED",
                            onClick = {
                                notifyUserInteraction()
                                // Cycle through speeds: 1.0 → 1.25 → 1.5 → 2.0 → 0.5 → 0.75 → 1.0
                                val speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.5f, 0.75f)
                                val currentIdx = speeds.indexOf(playbackSpeed)
                                val nextIdx = if (currentIdx == -1) 1 else (currentIdx + 1) % speeds.size
                                playbackSpeed = speeds[nextIdx]
                                player.setPlaybackSpeed(playbackSpeed)
                            }
                        )
                        BottomTab(
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_ios_share),
                                    contentDescription = "Share",
                                    tint = TabIconColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = "SHARE",
                            onClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, videoUri.toUri())
                                    type = "video/*"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun BottomTab(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    val TabIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = label,
            color = TabIconColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
@SuppressLint("DefaultLocale")
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun enterPiPMode(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = context as? Activity ?: return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }
}
