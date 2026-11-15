package com.example.amoledgallery.ui.components

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import coil3.compose.AsyncImage
import com.example.amoledgallery.model.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Speed, loop and sound of the viewer's video player, kept while the app runs. */
object VideoSettings {
    var speed = 1f
    var loop = true
    var muted = false
}

/**
 * What the viewer's controls need from the video on screen. The controls are drawn by the viewer, on top of its
 * glass source and outside the zoom, so they talk to the player on the current page through this.
 */
@OptIn(UnstableApi::class)
@Stable
class VideoController {
    private var player: ExoPlayer? = null

    var isPlaying by mutableStateOf(false)
        private set
    var ended by mutableStateOf(false)
        private set
    var scrubbing by mutableStateOf(false)
        private set
    /** Read while drawing wherever possible: it changes on every frame while the controls are visible. */
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var speed by mutableStateOf(VideoSettings.speed)
        private set
    var looping by mutableStateOf(VideoSettings.loop)
        private set
    var muted by mutableStateOf(VideoSettings.muted)
        private set

    /** Paused with the button: then it stays paused, also after coming back to the app. */
    var pausedByUser by mutableStateOf(false)
        private set
    private var resumeAfterScrub = false

    /** The icon to show: scrubbing pauses the video only for as long as the finger is down. */
    val showsPause: Boolean get() = isPlaying || (scrubbing && resumeAfterScrub)

    internal fun attach(player: ExoPlayer, fallbackDurationMs: Long) {
        this.player = player
        isPlaying = player.isPlaying
        ended = false
        scrubbing = false
        pausedByUser = false
        positionMs = 0L
        durationMs = fallbackDurationMs
    }

    internal fun detach(player: ExoPlayer) {
        if (this.player !== player) return
        this.player = null
        isPlaying = false
        scrubbing = false
    }

    internal fun onIsPlayingChanged(playing: Boolean) {
        isPlaying = playing
    }

    internal fun onPlaybackStateChanged(player: ExoPlayer, state: Int) {
        if (state == Player.STATE_READY && player.duration > 0) durationMs = player.duration
        ended = state == Player.STATE_ENDED
    }

    internal fun syncPosition(player: ExoPlayer) {
        if (!scrubbing) positionMs = player.currentPosition
    }

    fun togglePlay() {
        val player = player ?: return
        if (player.isPlaying) {
            pausedByUser = true
            player.pause()
        } else {
            pausedByUser = false
            // At the end of a video that doesn't loop, playing starts it over.
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    /** While the finger is down the picture follows it: key frames only, which is what can keep up. */
    fun beginScrub() {
        val player = player ?: return
        if (scrubbing) return
        resumeAfterScrub = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        scrubbing = true
        player.pause()
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }

    fun scrubTo(fraction: Float) {
        val player = player ?: return
        positionMs = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
        player.seekTo(positionMs)
    }

    fun endScrub() {
        val player = player ?: return
        if (!scrubbing) return
        player.setSeekParameters(SeekParameters.EXACT)
        player.seekTo(positionMs)
        scrubbing = false
        if (resumeAfterScrub) player.play()
    }

    fun changeSpeed(value: Float) {
        speed = value
        VideoSettings.speed = value
        player?.setPlaybackSpeed(value)
    }

    fun toggleLoop() {
        looping = !looping
        VideoSettings.loop = looping
        player?.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun toggleMute() {
        muted = !muted
        VideoSettings.muted = muted
        player?.volume = if (muted) 0f else 1f
    }
}

/**
 * Native Media3 ExoPlayer video page in the viewer: plays as soon as it opens, and zooms like a photo (pinch or double
 * tap, panning stops at the picture's edges). Offscreen pages show their thumbnail and hold no player or decoder.
 *
 * The picture is a TextureView: unlike the SurfaceView a PlayerView uses, it is drawn with the rest of the window, so
 * it follows the zoom and the pull-to-close, and the glass controls on top can blur it.
 */
@OptIn(UnstableApi::class)
@Composable
fun AmoledVideoPlayer(
    item: MediaItem,
    isActivePage: Boolean,
    controller: VideoController,
    /** The position only has to be fresh on every frame while the controls showing it are visible. */
    controlsVisible: Boolean,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // From the file's own metadata until the decoder knows better.
    var aspect by remember(item.id) { mutableFloatStateOf(item.displayAspectRatio) }
    ZoomableBox(
        modifier = modifier.background(Color.Black),
        contentAspectRatio = aspect,
        onSingleTap = onTap,
        onZoomChange = onZoomChange
    ) {
        if (isActivePage) {
            ActiveVideoPlayer(item, controller, controlsVisible, aspect) { aspect = it }
        } else {
            AsyncImage(
                model = item.thumbnail ?: item.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ActiveVideoPlayer(
    item: MediaItem,
    controller: VideoController,
    controlsVisible: Boolean,
    aspect: Float,
    onAspect: (Float) -> Unit
) {
    val context = LocalContext.current
    var isFirstFrameRendered by remember { mutableStateOf(false) }
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

    DisposableEffect(exoPlayer) {
        controller.attach(exoPlayer, item.durationMs)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) = controller.onIsPlayingChanged(playing)

            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) = controller.onPlaybackStateChanged(exoPlayer, playbackState)

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    onAspect(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            controller.detach(exoPlayer)
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
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (!controller.pausedByUser) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Every frame for a smooth seek bar while it is visible; otherwise only often enough for "save this frame".
    LaunchedEffect(exoPlayer, controlsVisible) {
        while (isActive) {
            controller.syncPosition(exoPlayer)
            com.example.amoledgallery.ui.features.ViewerState.videoPositionMs = controller.positionMs
            if (controlsVisible) withFrameMillis { } else delay(250)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { exoPlayer.setVideoTextureView(it) } },
            onRelease = { exoPlayer.clearVideoTextureView(it) },
            modifier = if (aspect > 0f) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize()
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
    }
}
