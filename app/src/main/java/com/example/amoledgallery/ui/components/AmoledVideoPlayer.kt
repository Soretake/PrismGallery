package com.example.amoledgallery.ui.components
import com.example.amoledgallery.ui.i18n.strings

import android.view.ViewGroup
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.theme.AmoledBlack
import com.example.amoledgallery.ui.theme.PureWhite
import com.example.amoledgallery.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * Native Media3 ExoPlayer video viewer in pure AMOLED black:
 * - Direct hardware decoding via Media3
 * - Minimalist center Play/Pause control
 * - Minimalist bottom scrubber and timestamp overlay
 * - Plays as soon as it opens; stays paused only when paused with the button
  * - Controls that belong to the viewer's: they appear and fade with its top and bottom bars
 * - Proper lifecycle cleanup
 */
@OptIn(UnstableApi::class)
@Composable
fun AmoledVideoPlayer(
    item: MediaItem,
    isActivePage: Boolean,
    /** Play button and scrubber follow the viewer's bars, so everything fades together. */
    controlsVisible: Boolean,
    onTap: () -> Unit,
    /** Pausing brings the controls back, however they were hidden. */
    onShowControls: () -> Unit,
    onPlayingChange: (Boolean) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isActivePage) {
        // Offscreen page in HorizontalPager: Render static thumbnail without allocating ExoPlayer or MediaCodec
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = item.thumbnail ?: item.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    ActiveVideoPlayer(
        item = item,
        controlsVisible = controlsVisible,
        onTap = onTap,
        onShowControls = onShowControls,
        onPlayingChange = onPlayingChange,
        onScrubbingChange = onScrubbingChange,
        modifier = modifier
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun ActiveVideoPlayer(
    item: MediaItem,
    controlsVisible: Boolean,
    onTap: () -> Unit,
    onShowControls: () -> Unit,
    onPlayingChange: (Boolean) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var isFirstFrameRendered by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(item.durationMs) }
    var isScrubbing by remember { mutableStateOf(false) }
    /** Paused with the button: then it stays paused, also after coming back to the app. */
    var pausedByUser by remember { mutableStateOf(false) }

    var speed by remember { mutableStateOf(VideoSettings.speed) }
    var looping by remember { mutableStateOf(VideoSettings.loop) }
    var muted by remember { mutableStateOf(VideoSettings.muted) }
    val exoPlayer = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            repeatMode = if (VideoSettings.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setPlaybackSpeed(VideoSettings.speed)
            volume = if (VideoSettings.muted) 0f else 1f
            // Opening a video in the viewer plays it straight away.
            playWhenReady = true
            prepare()
        }
    }

    LaunchedEffect(isPlaying) { onPlayingChange(isPlaying) }
    LaunchedEffect(isScrubbing) { onScrubbingChange(isScrubbing) }
    val reportStopped by rememberUpdatedState(onPlayingChange)
    DisposableEffect(Unit) { onDispose { reportStopped(false) } }

    // Attach listener for playback state changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0) durationMs = d
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Pause when the app goes to the background; the player otherwise kept playing (and looping audio)
    // after leaving the app until the viewer was closed.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                // Back in the app: carry on, unless the user had paused it themselves.
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (!pausedByUser) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Continuous progress tracking while playing
    LaunchedEffect(isPlaying, isScrubbing) {
        while (isActive && isPlaying && !isScrubbing) {
            currentPositionMs = exoPlayer.currentPosition
            com.example.amoledgallery.ui.features.ViewerState.videoPositionMs = currentPositionMs
            delay(150)
        }
        com.example.amoledgallery.ui.features.ViewerState.videoPositionMs = exoPlayer.currentPosition
    }
    // Speed, loop and sound stay as chosen for the next video too.
    LaunchedEffect(speed) { exoPlayer.setPlaybackSpeed(speed); VideoSettings.speed = speed }
    LaunchedEffect(looping) {
        exoPlayer.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        VideoSettings.loop = looping
    }
    LaunchedEffect(muted) { exoPlayer.volume = if (muted) 0f else 1f; VideoSettings.muted = muted }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Media3 Player Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Instant preview thumbnail before first frame is rendered
        if (!isFirstFrameRendered) {
            AsyncImage(
                model = item.thumbnail ?: item.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay Controls (Center Play/Pause + Bottom Scrubber)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                // Center Play/Pause Button
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                            pausedByUser = true
                            onShowControls()
                        } else {
                            pausedByUser = false
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) strings.pause else strings.play,
                        tint = PureWhite,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Bottom Scrubber Bar & Time Info (Positioned above Detail Bar). Capped in width so it
                // does not stretch across the whole screen in landscape.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(bottom = 76.dp)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                ) {
                    // Speed, loop and sound.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VideoChip(if (speed == 1f) "1×" else "${speed}×".replace(".0×", "×")) {
                            val speeds = listOf(0.25f, 0.5f, 1f, 1.5f, 2f)
                            speed = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
                        }
                        VideoChip(if (looping) "↻ ${com.example.amoledgallery.ui.i18n.F.loop}" else "${com.example.amoledgallery.ui.i18n.F.loop}: ${com.example.amoledgallery.ui.i18n.F.off}") { looping = !looping }
                        VideoChip(if (muted) com.example.amoledgallery.ui.i18n.F.unmute else com.example.amoledgallery.ui.i18n.F.mute) { muted = !muted }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimestamp(currentPositionMs),
                            color = PureWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTimestamp(durationMs),
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val maxVal = durationMs.toFloat().coerceAtLeast(1f)
                    val sliderVal = currentPositionMs.toFloat().coerceIn(0f, maxVal)

                    Slider(
                        value = sliderVal,
                        onValueChange = { newPos ->
                            isScrubbing = true
                            currentPositionMs = newPos.toLong()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(currentPositionMs)
                            isScrubbing = false
                        },
                        valueRange = 0f..maxVal,
                        colors = SliderDefaults.colors(
                            thumbColor = PureWhite,
                            activeTrackColor = PureWhite,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Speed, loop and sound of the viewer's video player, kept while the app runs. */
object VideoSettings {
    var speed = 1f
    var loop = true
    var muted = false
}

@Composable
private fun VideoChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = PureWhite,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
