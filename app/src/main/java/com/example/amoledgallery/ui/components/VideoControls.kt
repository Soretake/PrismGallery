package com.example.amoledgallery.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.design.GEMINI_PILL_TINT
import com.example.amoledgallery.ui.design.GeminiPillBlur
import com.example.amoledgallery.ui.design.GeminiSparkle
import com.example.amoledgallery.ui.design.barGlass
import com.example.amoledgallery.ui.design.geminiGlass
import com.example.amoledgallery.ui.design.geminiGlowColors
import com.example.amoledgallery.ui.design.geminiSelectionEdge
import com.example.amoledgallery.ui.design.geminiSelectionFill
import com.example.amoledgallery.ui.design.glass
import com.example.amoledgallery.ui.design.pressScale
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.isLight
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The video controls above the viewer's toolbar, each in its own gallery's form:
 * - One UI: Samsung Gallery's small dark pill ("▶ 00:03 / 00:12") over a strip of the video's frames that slides
 *   under a fixed white marker. Sizes and colours are the ones in Samsung Gallery's resources.
 * - Gemini: a glowing glass pill with the Gemini gradient as progress and the sparkle as the handle.
 * - iOS: Liquid Glass in the shape of its toolbar: a circle at each end and a capsule with the times between them.
 * - HyperOS: a flat bar joined to its toolbar: play, times and a hairline seek bar, as in Xiaomi Gallery's own player.
 *
 * Speed and looping are in the viewer's "more" menu, as in those apps.
 */
@Composable
fun VideoControls(
    controller: VideoController,
    item: MediaItem,
    language: DesignLanguage,
    lightChrome: Boolean,
    hazeState: HazeState?,
    modifier: Modifier = Modifier
) {
    when (language) {
        DesignLanguage.SAMSUNG_ONEUI -> OneUiControls(controller, item, modifier)
        DesignLanguage.GOOGLE_EXPRESSIVE -> GeminiControls(controller, hazeState, modifier)
        DesignLanguage.IOS_LIQUID_GLASS -> IosControls(controller, hazeState, modifier)
        DesignLanguage.XIAOMI_HYPEROS -> HyperOsControls(controller, lightChrome, modifier)
    }
}

/** The big play button in the middle of a paused video, in the designs whose galleries have one. */
@Composable
fun VideoCenterPlay(
    controller: VideoController,
    language: DesignLanguage,
    visible: Boolean,
    hazeState: HazeState?,
    modifier: Modifier = Modifier
) {
    if (language != DesignLanguage.GOOGLE_EXPRESSIVE && language != DesignLanguage.XIAOMI_HYPEROS) return
    AnimatedVisibility(
        visible = visible && (controller.pausedByUser || controller.ended),
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        val interaction = remember { MutableInteractionSource() }
        val base = Modifier
            .pressScale(interaction, 0.92f)
            .size(if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 76.dp else 64.dp)
        val surface = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) {
            base.geminiGlass(CircleShape, hazeState, tintAlpha = 0.34f, blur = 14.dp, vivid = true)
        } else {
            base.clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
        }
        Box(
            modifier = surface.clickable(interactionSource = interaction, indication = null, onClick = controller::togglePlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, strings.play, tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}

// ---- One UI ---------------------------------------------------------------------------------------

@Composable
private fun OneUiControls(controller: VideoController, item: MediaItem, modifier: Modifier) {
    val pillFill = Color(0x66000000)
    val pillEdge = Color.White.copy(alpha = 0.14f)
    val pill = RoundedCornerShape(percent = 50)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The padding is outside the 29 dp pill's drawing but inside its touch area.
            Row(
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = controller::togglePlay)
                    .padding(vertical = 8.dp)
                    .height(29.dp)
                    .clip(pill)
                    .background(pillFill)
                    .border(0.5.dp, pillEdge, pill)
                    .padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayPauseIcon(controller, Color.White, 22.dp)
                Spacer(modifier = Modifier.width(7.dp))
                ElapsedText(controller, Color(0xFFFCFCFC), 12.sp, width = null)
                Text(" / ", color = Color(0x99FCFCFC), fontSize = 12.sp)
                DurationText(controller, Color(0x99FCFCFC), 12.sp, width = null)
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = controller::toggleMute)
                    .padding(8.dp)
                    .size(29.dp)
                    .clip(CircleShape)
                    .background(pillFill)
                    .border(0.5.dp, pillEdge, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(muteIcon(controller), muteLabel(controller), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        OneUiFrameStrip(controller, item)
    }
}

private val StripFrameWidth = 52.dp
private val StripHeight = 49.dp

/** The video's frames for the strip, kept for the last few videos so swiping back doesn't extract them again. */
private val stripFrames = LruCache<Uri, Array<ImageBitmap?>>(4)

/** One frame per second and a half, within what fits the strip's scale and stays quick to extract. */
private fun stripFrameCount(durationMs: Long) = ceil(durationMs / 1500f).toInt().coerceIn(6, 40)

/**
 * Samsung Gallery's seek strip: the frames slide under a fixed marker in the middle, and dragging the strip seeks.
 * Everything position-dependent is read while drawing, so playing it recomposes nothing.
 */
@Composable
private fun OneUiFrameStrip(controller: VideoController, item: MediaItem) {
    val context = LocalContext.current
    val durationHint = item.durationMs.takeIf { it > 0 } ?: controller.durationMs
    val count = stripFrameCount(durationHint)
    val frames = remember(item.uri, count) {
        stripFrames.get(item.uri)?.takeIf { it.size == count } ?: arrayOfNulls<ImageBitmap>(count).also { stripFrames.put(item.uri, it) }
    }
    var loaded by remember(item.uri, count) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val frameWidthPx = with(density) { StripFrameWidth.toPx() }
    val heightPx = with(density) { StripHeight.toPx() }

    LaunchedEffect(item.uri, count) {
        if (frames.all { it != null } || durationHint <= 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, item.uri)
                for (i in 0 until count) {
                    coroutineContext.ensureActive()
                    if (frames[i] != null) continue
                    val timeUs = (i + 0.5f) / count * durationHint * 1000L
                    // Twice the frame's width is enough for the centre crop of a portrait video.
                    val frame = retriever.getScaledFrameAtTime(
                        timeUs.toLong(),
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        (frameWidthPx * 1.5f).toInt(),
                        (heightPx * 1.5f).toInt()
                    ) ?: continue
                    frames[i] = frame.asImageBitmap()
                    loaded++
                }
            } catch (_: RuntimeException) {
                // A file the extractor can't read keeps the plain strip; the seeking still works.
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    val clip = remember { Path() }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .pointerInput(controller, count) {
                val total = frameWidthPx * count
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val start = controller.positionMs / controller.durationMs.coerceAtLeast(1L).toFloat()
                    var dragging = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val dx = change.position.x - down.position.x
                            if (!dragging && abs(dx) > viewConfiguration.touchSlop) {
                                dragging = true
                                controller.beginScrub()
                            }
                            // The strip follows the finger, so dragging it left moves forward in the video.
                            if (dragging) controller.scrubTo(start - dx / total)
                        }
                    } finally {
                        if (dragging) controller.endScrub()
                    }
                }
            }
    ) {
        loaded // Redraw as the frames come in.
        val total = frameWidthPx * count
        val fraction = controller.positionMs / controller.durationMs.coerceAtLeast(1L).toFloat()
        val left = size.width / 2f - fraction.coerceIn(0f, 1f) * total
        clip.reset()
        clip.addRoundRect(RoundRect(left, 0f, left + total, size.height, CornerRadius(8.dp.toPx())))
        clipPath(clip) {
            drawRect(Color(0xFF2A2A2C), Offset(left, 0f), Size(total, size.height))
            val first = floor((-left / frameWidthPx)).toInt().coerceAtLeast(0)
            val last = ceil((size.width - left) / frameWidthPx).toInt().coerceAtMost(count)
            for (i in first until last) {
                val frame = frames[i] ?: continue
                drawFrame(frame, left + i * frameWidthPx, frameWidthPx)
            }
        }
        // The marker: 4 dp wide, near-white, with a fine dark edge against bright frames.
        val markerWidth = 4.dp.toPx()
        val markerLeft = size.width / 2f - markerWidth / 2f
        val radius = CornerRadius(2.dp.toPx())
        drawRoundRect(Color(0xFFFCFCFC), Offset(markerLeft, 0f), Size(markerWidth, size.height), radius)
        drawRoundRect(Color(0x7F000000), Offset(markerLeft, 0f), Size(markerWidth, size.height), radius, style = Stroke(0.5.dp.toPx()))
    }
}

/** Centre-cropped into one cell of the strip. */
private fun DrawScope.drawFrame(frame: ImageBitmap, left: Float, cellWidth: Float) {
    val cell = cellWidth / size.height
    val source = frame.width / frame.height.toFloat()
    val srcWidth = if (source > cell) (frame.height * cell).roundToInt() else frame.width
    val srcHeight = if (source > cell) frame.height else (frame.width / cell).roundToInt()
    drawImage(
        frame,
        srcOffset = IntOffset((frame.width - srcWidth) / 2, (frame.height - srcHeight) / 2),
        srcSize = IntSize(srcWidth, srcHeight),
        dstOffset = IntOffset(left.roundToInt(), 0),
        dstSize = IntSize(ceil(cellWidth).toInt(), size.height.roundToInt())
    )
}

// ---- Gemini ---------------------------------------------------------------------------------------

@Composable
private fun GeminiControls(controller: VideoController, hazeState: HazeState?, modifier: Modifier) {
    val palette = LocalAppTheme.current.palette
    val pill = RoundedCornerShape(percent = 50)
    Box(modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .height(64.dp)
                // A little more tint than the toolbar's pill: the times have to read over any video.
                .geminiGlass(pill, hazeState, tintAlpha = maxOf(GEMINI_PILL_TINT, 0.34f), blur = maxOf(GeminiPillBlur, 14.dp), vivid = true)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressScale(interaction, 0.9f)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(geminiSelectionFill(palette.isLight))
                    .border(0.8.dp, geminiSelectionEdge(palette.isLight), CircleShape)
                    .clickable(interactionSource = interaction, indication = ripple(color = palette.onBackground), onClick = controller::togglePlay),
                contentAlignment = Alignment.Center
            ) {
                PlayPauseIcon(controller, palette.onBackground, 28.dp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            ElapsedText(controller, palette.onBackground, 13.sp, width = 38.dp, align = TextAlign.Start)
            GeminiSeekTrack(controller, Modifier.weight(1f))
            DurationText(controller, palette.textMuted, 13.sp, width = 38.dp, align = TextAlign.End)
            ControlIcon(muteIcon(controller), muteLabel(controller), palette.onBackground, 42.dp, 23.dp, controller::toggleMute)
        }
    }
}

/** The Gemini gradient up to the position, with its soft glow, and the sparkle as the handle. */
@Composable
private fun GeminiSeekTrack(controller: VideoController, modifier: Modifier) {
    val palette = LocalAppTheme.current.palette
    val colors = geminiGlowColors()
    val track = palette.onBackground.copy(alpha = if (palette.isLight) 0.16f else 0.22f)
    val handle = 24.dp
    val grow by animateFloatAsState(if (controller.scrubbing) 1.3f else 1f, label = "gemini_handle")
    val density = LocalDensity.current
    val insetPx = with(density) { (handle / 2).toPx() }
    BoxWithConstraints(modifier = modifier.height(48.dp).seekGesture(controller, insetPx), contentAlignment = Alignment.CenterStart) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val y = size.height / 2f
            val fraction = fractionOf(controller)
            val width = size.width - insetPx * 2f
            val end = insetPx + width * fraction
            val thick = 4.dp.toPx()
            val gradient = Brush.linearGradient(
                0f to colors[0],
                0.45f to colors[1],
                0.7f to colors[2],
                1f to colors[3],
                start = Offset(insetPx, y),
                end = Offset(size.width - insetPx, y)
            )
            drawLine(track, Offset(insetPx, y), Offset(size.width - insetPx, y), thick, StrokeCap.Round)
            if (fraction > 0f) {
                drawLine(gradient, Offset(insetPx, y), Offset(end, y), thick * 2.6f, StrokeCap.Round, alpha = 0.28f)
                drawLine(gradient, Offset(insetPx, y), Offset(end, y), thick, StrokeCap.Round)
            }
        }
        val travelPx = with(density) { (maxWidth - handle).toPx() }
        GeminiSparkle(
            modifier = Modifier
                .size(handle)
                .graphicsLayer {
                    translationX = travelPx * fractionOf(controller)
                    scaleX = grow
                    scaleY = grow
                }
        )
    }
}

// ---- iOS ------------------------------------------------------------------------------------------

@Composable
private fun IosControls(controller: VideoController, hazeState: HazeState?, modifier: Modifier) {
    val palette = LocalAppTheme.current.palette
    val spec = barGlass()
    Box(modifier = modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .glass(CircleShape, spec, hazeState)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = controller::togglePlay),
                contentAlignment = Alignment.Center
            ) {
                PlayPauseIcon(controller, palette.onBackground, 26.dp)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .glass(RoundedCornerShape(percent = 50), spec, hazeState)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ElapsedText(controller, palette.onBackground, 12.sp, width = 36.dp, align = TextAlign.Start, compact = true)
                IosSeekTrack(controller, Modifier.weight(1f))
                RemainingText(controller, palette.onBackground, 12.sp, width = 40.dp)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .glass(CircleShape, spec, hazeState)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = controller::toggleMute),
                contentAlignment = Alignment.Center
            ) {
                Icon(muteIcon(controller), muteLabel(controller), tint = palette.onBackground, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** No handle: a filled line that thickens under the finger. */
@Composable
private fun IosSeekTrack(controller: VideoController, modifier: Modifier) {
    val palette = LocalAppTheme.current.palette
    val thickness by animateFloatAsState(if (controller.scrubbing) 9f else 5f, label = "ios_seek_thickness")
    Canvas(modifier = modifier.fillMaxHeight().seekGesture(controller, 0f)) {
        val y = size.height / 2f
        val thick = thickness.dp.toPx()
        val inset = thick / 2f
        drawLine(palette.onBackground.copy(alpha = 0.22f), Offset(inset, y), Offset(size.width - inset, y), thick, StrokeCap.Round)
        val end = inset + (size.width - thick) * fractionOf(controller)
        drawLine(palette.onBackground, Offset(inset, y), Offset(end, y), thick, StrokeCap.Round)
    }
}

// ---- HyperOS --------------------------------------------------------------------------------------

@Composable
private fun HyperOsControls(controller: VideoController, lightChrome: Boolean, modifier: Modifier) {
    val palette = LocalAppTheme.current.palette
    val chrome = if (lightChrome) palette.onBackground else Color.White.copy(alpha = 0.9f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (lightChrome) palette.background else Color.Black.copy(alpha = 0.55f))
            .height(54.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlIcon(
            if (controller.showsPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (controller.showsPause) strings.pause else strings.play,
            chrome,
            42.dp,
            26.dp,
            controller::togglePlay
        )
        ElapsedText(controller, chrome, 13.sp, width = 42.dp, align = TextAlign.Center)
        val thumb = 13.dp
        val insetPx = with(LocalDensity.current) { thumb.toPx() }
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight().seekGesture(controller, insetPx)) {
            val y = size.height / 2f
            val thick = 2.dp.toPx()
            val end = insetPx + (size.width - insetPx * 2f) * fractionOf(controller)
            drawLine(chrome.copy(alpha = 0.22f), Offset(insetPx, y), Offset(size.width - insetPx, y), thick, StrokeCap.Round)
            drawLine(chrome, Offset(insetPx, y), Offset(end, y), thick, StrokeCap.Round)
            drawCircle(chrome, thumb.toPx() / 2f, Offset(end, y))
        }
        DurationText(controller, chrome.copy(alpha = 0.6f), 13.sp, width = 42.dp, align = TextAlign.Center)
        ControlIcon(muteIcon(controller), muteLabel(controller), chrome, 42.dp, 22.dp, controller::toggleMute)
    }
}

// ---- Shared ---------------------------------------------------------------------------------------

private fun fractionOf(controller: VideoController): Float =
    (controller.positionMs / controller.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

/** Touching the track jumps there, and the picture follows the finger until it lets go. */
private fun Modifier.seekGesture(controller: VideoController, insetPx: Float): Modifier = pointerInput(controller, insetPx) {
    fun fraction(x: Float) = (x - insetPx) / (size.width - insetPx * 2f).coerceAtLeast(1f)
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        controller.beginScrub()
        try {
            controller.scrubTo(fraction(down.position.x))
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                controller.scrubTo(fraction(change.position.x))
            }
        } finally {
            controller.endScrub()
        }
    }
}

@Composable
private fun PlayPauseIcon(controller: VideoController, tint: Color, size: Dp) {
    val pause = controller.showsPause
    Icon(
        if (pause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        if (pause) strings.pause else strings.play,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

@Composable
private fun ControlIcon(icon: ImageVector, label: String, tint: Color, box: Dp, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(box)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = box / 2),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size))
    }
}

private fun muteIcon(controller: VideoController): ImageVector =
    if (controller.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp

private fun muteLabel(controller: VideoController): String = if (controller.muted) F.unmute else F.mute

/** Recomposes once a second, however often the position changes. */
@Composable
private fun ElapsedText(
    controller: VideoController,
    color: Color,
    fontSize: TextUnit,
    width: Dp?,
    align: TextAlign = TextAlign.Start,
    compact: Boolean = false
) {
    val seconds by remember(controller) { derivedStateOf { controller.positionMs / 1000 } }
    TimeText(formatTimestamp(seconds * 1000, compact), color, fontSize, width, align)
}

@Composable
private fun DurationText(controller: VideoController, color: Color, fontSize: TextUnit, width: Dp?, align: TextAlign = TextAlign.Start) {
    TimeText(formatTimestamp(controller.durationMs), color, fontSize, width, align)
}

/** iOS shows what is left, counting down. */
@Composable
private fun RemainingText(controller: VideoController, color: Color, fontSize: TextUnit, width: Dp) {
    val seconds by remember(controller) { derivedStateOf { (controller.durationMs - controller.positionMs).coerceAtLeast(0L) / 1000 } }
    TimeText("-" + formatTimestamp(seconds * 1000, compact = true), color, fontSize, width, TextAlign.End)
}

@Composable
private fun TimeText(text: String, color: Color, fontSize: TextUnit, width: Dp?, align: TextAlign) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        textAlign = align,
        maxLines = 1,
        modifier = if (width != null) Modifier.widthIn(min = width) else Modifier
    )
}

/** 00:03, or 0:03 where the design drops the leading zero. */
private fun formatTimestamp(ms: Long, compact: Boolean = false): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        compact -> String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        else -> String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
