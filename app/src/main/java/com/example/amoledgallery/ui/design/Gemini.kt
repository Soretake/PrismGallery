package com.example.amoledgallery.ui.design

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.isLight
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Gemini's brand colours besides the accent, which takes the place of Gemini blue. */
object GeminiColors {
    val Pink = Color(0xFFE8546A)
    val Red = Color(0xFFF14438)
    val Yellow = Color(0xFFF4B400)
    val Green = Color(0xFF34A853)
}

/**
 * Below this saturation an accent counts as neutral (white, grey, black). Its hue is meaningless then - HSV reports
 * 0, which is red - so neutral accents must never be given saturation.
 */
private const val NEUTRAL_SATURATION = 0.12f

/** The accent, saturated and bright enough to glow like Gemini blue does; neutral accents stay neutral. */
fun vividAccent(accent: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    if (hsv[1] < NEUTRAL_SATURATION) {
        hsv[1] = 0f
        hsv[2] = hsv[2].coerceIn(0.55f, 0.95f)
    } else {
        hsv[1] = hsv[1].coerceIn(0.65f, 0.9f)
        hsv[2] = hsv[2].coerceAtLeast(0.88f)
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** A colour with the accent's hue at the given saturation and brightness (backgrounds, glows); grey for neutral accents. */
fun accentTone(accent: Color, saturation: Float, value: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    val neutral = hsv[1] < NEUTRAL_SATURATION
    hsv[1] = if (neutral) 0f else saturation
    // A grey glow reads much brighter than a coloured one of the same value, so neutral glows are kept dimmer.
    hsv[2] = if (neutral && value < 0.7f) value * 0.5f else value
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** True for white, grey and black accents. */
fun isNeutralColor(color: Color): Boolean {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[1] < NEUTRAL_SATURATION
}

/** Readable icon colour on a filled [background]. */
fun contentOn(background: Color): Color =
    if (background.luminance() > 0.55f) Color(0xFF111111) else Color.White

/** Page visits: every change wakes all glass surfaces on screen once (a page or tab was opened). */
object GeminiVisits {
    val tick = mutableIntStateOf(0)
    fun bump() {
        tick.intValue++
    }
}

private const val ANIMATION_COUNT = 5

/**
 * The animation state of ONE glass surface. A touch on that surface (or opening its page) gives it energy and,
 * when it was at rest, picks one of five animations. A second after the last touch the energy drains and the
 * movement eases to a stop; nothing is redrawn while it rests.
 */
@Stable
class GeminiPulse(seed: Int) {
    val phase = mutableFloatStateOf(0f)
    val energy = mutableFloatStateOf(0f)
    val animation = mutableIntStateOf(0)
    val origin = mutableFloatStateOf(0.5f)
    private val random = Random(seed)
    private var lastPokeNanos = 0L
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun poke(originX: Float? = null) {
        if (energy.floatValue < 0.2f) {
            var next = random.nextInt(ANIMATION_COUNT)
            if (next == animation.intValue) next = (next + 1 + random.nextInt(ANIMATION_COUNT - 1)) % ANIMATION_COUNT
            animation.intValue = next
            phase.floatValue = 0f
            origin.floatValue = originX ?: (0.2f + random.nextFloat() * 0.6f)
        }
        lastPokeNanos = System.nanoTime()
        wake.trySend(Unit)
    }

    suspend fun run() {
        while (true) {
            wake.receive()
            var last = withFrameNanos { it }
            do {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    last = now
                    val sincePoke = (System.nanoTime() - lastPokeNanos) / 1_000_000_000f
                    var e = energy.floatValue
                    e = if (sincePoke < 1.0f) e + (1f - e) * (1f - exp(-dt / 0.2f)) else e * exp(-dt / 1.4f)
                    energy.floatValue = if (e < 0.004f) 0f else e
                    phase.floatValue += dt * (0.35f + 0.65f * e)
                }
            } while (energy.floatValue > 0f)
        }
    }
}

/** The Gemini gradient colours for the active accent. */
@Composable
@ReadOnlyComposable
fun geminiGlowColors(): List<Color> {
    val accent = LocalAppTheme.current.palette.accent
    return listOf(vividAccent(accent), GeminiColors.Pink, GeminiColors.Yellow, GeminiColors.Green)
}

/** Smooth 1D value noise over wrapping control points. */
private fun smoothNoise(points: FloatArray, x: Float): Float {
    val n = points.size
    val fx = floor(x)
    val i = fx.toInt()
    val f = x - fx
    val a = points[((i % n) + n) % n]
    val b = points[(((i + 1) % n) + n) % n]
    val t = (1f - cos(f * PI.toFloat())) / 2f
    return a + (b - a) * t
}

/**
 * Height of the glow band at [u] (0..1 across the width), in px: a thin, even band over most of the width that
 * only rises near the edges (like Gemini's answer box), with a gentle per-surface wobble and the running animation.
 */
private fun glowHeight(u: Float, points: FloatArray, pulse: GeminiPulse, midPx: Float, edgePx: Float): Float {
    val t = pulse.phase.floatValue
    val e = pulse.energy.floatValue
    val edge = abs(u - 0.5f) * 2f
    val rise = ((edge - 0.62f) / 0.38f).coerceIn(0f, 1f)
    val curve = (rise * rise * (3f - 2f * rise)).pow(1.25f)
    var noise = smoothNoise(points, u * 5f)
    var h = midPx + (edgePx - midPx) * curve
    when (pulse.animation.intValue) {
        // Waves: the uneven profile rolls sideways.
        0 -> noise += (smoothNoise(points, u * 5f + t * 1.3f) - noise) * e
        // Breathing: the whole band slowly swells and settles.
        1 -> h *= 1f + 0.5f * e * (0.5f - 0.5f * cos(t * 2f * PI.toFloat() * 0.55f))
        // Carousel: the colours travel (see geminiGlow), the band only sways a little.
        2 -> h *= 1f + 0.1f * e * sin(t * 3f + u * 6f)
        // Ripple: a soft swell runs outward from where the surface was touched.
        3 -> {
            val front = t * 0.8f
            val d = abs(u - pulse.origin.floatValue)
            val bump = exp(-((d - front) / 0.14f).pow(2))
            h *= 1f + 0.8f * e * bump
        }
        // Tide: a faint, even ripple travels across the whole bar.
        else -> h *= 1f + 0.22f * e * sin(2f * PI.toFloat() * (u * 2.2f - t * 0.7f))
    }
    return (h * (0.86f + 0.28f * noise)).coerceAtLeast(1f)
}

/**
 * Gemini's glowing edge, drawn as a soft band of colour rising from the bottom of [shape] with an uneven,
 * living top edge, plus a fine rim. Colours: the accent on the left through pink and yellow to green.
 */
fun Modifier.geminiGlow(
    shape: Shape,
    colors: List<Color>,
    pulse: GeminiPulse,
    points: FloatArray,
    strength: Float = 1f,
    /**
     * Gemini's answer card: the colour fills the lower part and climbs the sides, the top is shaded a little and
     * a soft light rim runs all the way round.
     */
    vivid: Boolean = false
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val clip = Path().apply { addOutline(outline) }
    val w = size.width
    val hgt = size.height
    // Low on small bars, a little higher on large cards; the edges always rise clearly above the middle.
    val midPx = if (vivid) hgt * 0.42f else (hgt * 0.13f).coerceIn(5.dp.toPx(), 16.dp.toPx())
    val edgePx = if (vivid) hgt * 0.92f else (hgt * 0.5f).coerceIn(14.dp.toPx(), 50.dp.toPx())
    val layerAlpha = if (vivid) 0.036f else 0.055f
    val shade = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.10f), 0.5f to Color.Transparent)
    val sheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 1f to Color.White.copy(alpha = 0.06f))
    val samples = 32
    // The tall vivid glow needs finer steps, or its layered edge shows as bands.
    val layers = if (vivid) 28 else 14
    val rim = Stroke(width = 1.dp.toPx() * 2f)
    // The rim fades out towards the top. Multiplied into its colours in the shader itself: fading it with a
    // DstIn rectangle needed an offscreen layer per surface on every frame.
    val fade = (Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Transparent, 1f to Color.Black) as ShaderBrush)
        .createShader(size)
    val band = Path()
    val heights = FloatArray(samples + 1)
    onDrawWithContent {
        val t = pulse.phase.floatValue
        val e = pulse.energy.floatValue
        val shift = if (pulse.animation.intValue == 2) t * 0.22f else 0.03f * e * sin(t * 1.3f)
        val tile = w * 1.25f
        val start = -shift * tile
        val gradient = Brush.linearGradient(
            0f to colors[0],
            0.45f to colors[1],
            0.62f to colors[2],
            0.8f to colors[3],
            1f to colors[0],
            start = Offset(start, 0f),
            end = Offset(start + tile, 0f),
            tileMode = TileMode.Repeated
        ) as ShaderBrush
        for (i in 0..samples) heights[i] = glowHeight(i / samples.toFloat(), points, pulse, midPx, edgePx)
        val lift = strength * (0.9f + 0.35f * e)
        val layerOpacity = (layerAlpha * lift).coerceIn(0f, 1f)
        clipPath(clip) {
            if (vivid) drawRect(shade)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                drawRect(GeminiGlowShader.brush(gradient.createShader(size), size, heights, layers, layerOpacity))
            } else {
                // Stacked, shrinking copies of the band: strongest at the bottom, fading softly towards its top edge.
                for (layer in 1..layers) {
                    val s = layer / layers.toFloat()
                    band.reset()
                    band.moveTo(0f, hgt)
                    for (i in 0..samples) band.lineTo(w * i / samples, hgt - heights[i] * s)
                    band.lineTo(w, hgt)
                    band.close()
                    drawPath(band, gradient, alpha = layerOpacity)
                }
            }
        }
        val fadedGradient = android.graphics.ComposeShader(fade, gradient.createShader(size), android.graphics.BlendMode.SRC_IN)
        drawOutline(outline, ShaderBrush(fadedGradient), alpha = (0.4f * lift).coerceIn(0f, 1f), style = rim)
        if (vivid) drawOutline(outline, sheen, style = Stroke(width = 1.dp.toPx()))
        drawContent()
    }
}

/**
 * The glow band in a single pass (Android 13+). It used to be drawn as up to 28 stacked, shrinking copies of the
 * band at low opacity, which filled each glow pixel up to 28 times on every frame of the animation - the main
 * reason Gemini dropped frames on budget GPUs. The shader works out per pixel how many of those copies cover it
 * and blends that many, so the colour comes out the same. Shared, so it is compiled once; every draw sets its own
 * uniforms right before, and the drawing keeps a copy of them. Main thread only, like all drawing.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object GeminiGlowShader {
    private val shader by lazy(LazyThreadSafetyMode.NONE) { RuntimeShader(GLOW_SHADER) }

    fun brush(gradient: android.graphics.Shader, size: Size, heights: FloatArray, layers: Int, layerAlpha: Float): ShaderBrush {
        shader.setInputShader("gradient", gradient)
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("heights", heights)
        shader.setFloatUniform("layers", layers.toFloat())
        shader.setFloatUniform("layerAlpha", layerAlpha)
        return ShaderBrush(shader)
    }
}

// heights: the band's height at 33 points across the width (the outline the stacked copies were drawn from).
// A copy at scale k / layers covers a pixel when that pixel lies below its top edge; the last copy's edge is
// anti-aliased like a path's.
private const val GLOW_SHADER = """
uniform shader gradient;
uniform float2 size;
uniform float heights[33];
uniform float layers;
uniform float layerAlpha;

half4 main(float2 p) {
    float u = clamp(p.x / size.x, 0.0, 1.0) * 32.0;
    float h = heights[0];
    for (int k = 0; k < 32; k++) {
        if (u >= float(k)) {
            h = mix(heights[k], heights[k + 1], clamp(u - float(k), 0.0, 1.0));
        }
    }
    float d = size.y - p.y;
    float covered = clamp(layers * (1.0 - d / max(h, 0.001)), 0.0, layers - 1.0) + clamp(h - d + 0.5, 0.0, 1.0);
    float a = 1.0 - pow(1.0 - layerAlpha, covered);
    return gradient.eval(p) * half(a);
}
"""

/**
 * Gemini's translucent box: frosted glass over the content behind it with the living gradient glow. The glow
 * moves when its page opens and when this surface itself is touched.
 */
@Composable
fun Modifier.geminiGlass(
    shape: Shape,
    state: HazeState? = LocalContentHaze.current,
    tintAlpha: Float = 0.55f,
    glow: Float = 1f,
    blur: Dp = 26.dp,
    /** The floating pills' look: clear glass with the colour glowing up from below (see [geminiGlow]). */
    vivid: Boolean = false
): Modifier {
    val palette = LocalAppTheme.current.palette
    val light = palette.isLight
    val base = if (light) Color.White else Color(0xFF131416)
    val colors = geminiGlowColors()
    val seed = remember { Random.nextInt() }
    val pulse = remember { GeminiPulse(seed) }
    val points = remember { Random(seed).let { r -> FloatArray(7) { r.nextFloat() } } }
    LaunchedEffect(pulse) { pulse.run() }
    val visit = GeminiVisits.tick.intValue
    // Surfaces on the hidden tabs stay composed; waking them too animated every one of them for seconds unseen.
    val visible = LocalTabVisible.current
    LaunchedEffect(visit) { if (visible) pulse.poke() }
    return this
        .then(
            if (light) {
                Modifier.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            } else {
                Modifier
            }
        )
        .glass(
            shape,
            GlassSpec(
                tint = base.copy(alpha = if (light) (tintAlpha + 0.25f).coerceAtMost(0.92f) else tintAlpha),
                fallback = base.copy(alpha = if (light) 0.96f else (tintAlpha + 0.25f).coerceAtMost(0.94f)),
                blur = blur,
                rim = GlassRim.None
            ),
            state
        )
        .then(if (light) Modifier.border(1.dp, Color.Black.copy(alpha = 0.06f), shape) else Modifier)
        // The glow and the surface's content go into a cached layer, so the renderer redraws them only when they
        // change - not on every frame something else on screen moves, like the grid scrolling behind the tab bar.
        // The glass is clipped to the shape already, so nothing is lost.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .pointerInput(pulse) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.pressed) {
                        pulse.poke((change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
                    }
                }
            }
        }
        .geminiGlow(shape, colors, pulse, points, if (light) glow * 0.7f else glow, vivid)
}

/**
 * Tint of the floating pills (tab bar, selection bar, viewer actions): practically clear, like Gemini's answer
 * card - the content behind stays visible and only the glow and rim mark the surface.
 */
const val GEMINI_PILL_TINT = 0.05f

/** The pills' blur: just enough distortion to set the surface apart, not a frost. */
val GeminiPillBlur = 5.dp

/**
 * Gemini's quiet controls (the round buttons and the title chip): a dark grey fill, or white with a hairline
 * border and a soft shadow in light mode.
 */
@Composable
fun Modifier.geminiChip(shape: Shape): Modifier {
    val palette = LocalAppTheme.current.palette
    return if (palette.isLight) {
        this
            .shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = 0.08f), shape)
    } else {
        this
            .clip(shape)
            .background(Color(0xFF2A2B2E).copy(alpha = 0.88f))
    }
}

/**
 * Gemini's sparkle: four points joined by concave curves (each side is a quarter of a circle centred on the
 * bounding square's corner), mostly the accent blue with red glowing in from the top, yellow from the left and
 * green from the bottom, as in the current Gemini logo.
 */
@Composable
fun GeminiSparkle(modifier: Modifier = Modifier) {
    val colors = geminiGlowColors()
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        // A quarter circle of radius r around a corner, drawn as a cubic (k = circle approximation constant).
        val k = 0.5523f * r
        val path = Path().apply {
            moveTo(cx, cy - r)
            cubicTo(cx, cy - r + k, cx + r - k, cy, cx + r, cy)
            cubicTo(cx + r - k, cy, cx, cy + r - k, cx, cy + r)
            cubicTo(cx, cy + r - k, cx - r + k, cy, cx - r, cy)
            cubicTo(cx - r + k, cy, cx, cy - r + k, cx, cy - r)
            close()
        }
        clipPath(path) {
            drawRect(colors[0])
            drawCircle(
                Brush.radialGradient(listOf(GeminiColors.Red, Color.Transparent), center = Offset(cx - r * 0.15f, cy - r), radius = r * 1.05f),
                radius = r * 1.05f,
                center = Offset(cx - r * 0.15f, cy - r)
            )
            drawCircle(
                Brush.radialGradient(listOf(colors[2], Color.Transparent), center = Offset(cx - r, cy - r * 0.05f), radius = r * 0.95f),
                radius = r * 0.95f,
                center = Offset(cx - r, cy - r * 0.05f)
            )
            drawCircle(
                Brush.radialGradient(listOf(colors[3], Color.Transparent), center = Offset(cx - r * 0.2f, cy + r), radius = r * 0.95f),
                radius = r * 0.95f,
                center = Offset(cx - r * 0.2f, cy + r)
            )
        }
    }
}

/** An icon filled with the Gemini gradient (e.g. the heart on the empty favourites page). */
@Composable
fun GeminiIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = geminiGlowColors()
    Icon(
        icon,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    0f to colors[0],
                    0.45f to colors[1],
                    0.7f to colors[2],
                    1f to colors[3],
                    // Spans the glyph itself (not the icon's padded box), so blue and green both show.
                    start = Offset(size.width * 0.2f, size.height * 0.8f),
                    end = Offset(size.width * 0.8f, size.height * 0.2f)
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush, blendMode = BlendMode.SrcIn)
                }
            }
    )
}

/**
 * A glowing storage ring: a faint track with a thin orbit and the used part as a Gemini gradient arc (accent,
 * green, yellow, pink) that grows in, then slowly cycles its colours along the arc like a carousel.
 */
@Composable
fun GeminiStorageRing(
    usedFraction: Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val palette = LocalAppTheme.current.palette
    val glow = geminiGlowColors()
    // Cycle of colours along the arc; it wraps from pink back to the accent.
    val cycle = listOf(glow[0], glow[3], glow[2], glow[1])
    val grow = remember { Animatable(0f) }
    val carousel = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    // The menu tab stays composed behind the others, and this loop asks for a frame on every vsync: left
    // running it redrew the whole window nonstop, even while scrolling the grid or viewing a photo.
    val visible = LocalTabVisible.current
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { grow.value >= 1f }.first { it }
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                carousel.floatValue = (carousel.floatValue + (now - last) / 1_000_000_000f / 20f) % 1f
                last = now
            }
        }
    }
    val track = palette.onBackground.copy(alpha = if (palette.isLight) 0.07f else 0.10f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val radius = size.minDimension / 2f - stroke * 1.9f
            val center = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            drawCircle(track, radius = radius, center = center, style = Stroke(stroke))
            drawCircle(track, radius = radius + stroke * 1.25f, center = center, style = Stroke(1.dp.toPx()))

            val f = usedFraction.coerceIn(0f, 1f) * grow.value
            if (f <= 0.002f) return@Canvas
            val offset = carousel.floatValue
            fun colorAt(x: Float): Color {
                val p = (((x - offset) % 1f) + 1f) % 1f * cycle.size
                val i = floor(p).toInt()
                return lerp(cycle[i % cycle.size], cycle[(i + 1) % cycle.size], p - i)
            }
            val samples = 28
            val stops = ArrayList<Pair<Float, Color>>()
            for (i in 0..samples) stops += (f * i / samples) to colorAt(i / samples.toFloat())
            // Past the arc's end: keep the end colour for the rounded end cap, then the start colour for the start cap.
            val gap = f + (1f - f) / 2f
            stops += gap to colorAt(1f)
            stops += gap to colorAt(0f)
            stops += 1f to colorAt(0f)
            val brush = Brush.sweepGradient(*stops.toTypedArray(), center = center)
            val sweep = 360f * f
            rotate(-90f, center) {
                drawArc(brush, 0f, sweep, false, topLeft, arcSize, alpha = 0.06f, style = Stroke(stroke * 3.2f, cap = StrokeCap.Round))
                drawArc(brush, 0f, sweep, false, topLeft, arcSize, alpha = 0.12f, style = Stroke(stroke * 1.9f, cap = StrokeCap.Round))
                drawArc(brush, 0f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            val endAngle = (sweep - 90f) * PI.toFloat() / 180f
            val dot = Offset(center.x + radius * cos(endAngle), center.y + radius * sin(endAngle))
            drawCircle(Color.White.copy(alpha = 0.3f), radius = stroke * 0.9f, center = dot)
            drawCircle(Color.White, radius = stroke * 0.32f, center = dot)
        }
        content()
    }
}

/**
 * Gemini's selected pill on a glass bar: darker than the bar around it (as in Prism Camera), rather
 * than a lighter lozenge. [geminiSelectionEdge] keeps it visible where the scene behind is dark too.
 */
fun geminiSelectionFill(light: Boolean): Color =
    if (light) Color.Black.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.22f)

fun geminiSelectionEdge(light: Boolean): Color =
    if (light) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f)

/** The same selection on the plain background, where darker than black is not possible: the dark chip grey. */
fun geminiSelectionOnBackground(light: Boolean): Color =
    if (light) Color.Black.copy(alpha = 0.08f) else Color(0xFF2A2B2E)
