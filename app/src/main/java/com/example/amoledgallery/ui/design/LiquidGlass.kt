package com.example.amoledgallery.ui.design

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.isLight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import android.graphics.RenderEffect as AndroidRenderEffect

/*
 * iOS Liquid Glass. Instead of scattering light with a blur, the glass bends it: a backdrop source records
 * its content into a GraphicsLayer every frame, and each glass control re-draws that content through an AGSL
 * lens shader. A rounded-rectangle distance field gives a circular lens profile at the rim, which pulls the
 * content from just outside the glass into its edge; the whole pane magnifies slightly, the edge splits the
 * colour channels a little, and the glass bulges around the finger while pressed. Touches make it swell on a
 * bouncy spring and light up from the fingertip outwards.
 */

/** Refraction needs RuntimeShader (API 33+) and is part of the HIGH/ULTRA effect budget, iOS design only. */
@Composable
@ReadOnlyComposable
internal fun liquidRefractionEnabled(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        glassEnabled() &&
        LocalAppTheme.current.language == DesignLanguage.IOS_LIQUID_GLASS

/** The recorded content of one backdrop source and where it sits on screen. */
internal class LiquidBackdrop(val layer: GraphicsLayer) {
    var position by mutableStateOf(Offset.Zero)

    /**
     * Called by the source every time it records its content. The recording is drawn a second time inside each
     * lens, but the renderer tracks what changed inside it in the coordinates of its first (real) position, so a
     * lens repainted only the strip matching that position and kept old content in the rest. Each lens records
     * itself again and swaps in a new render effect here, which makes the renderer repaint the whole lens in the
     * same frame.
     */
    val lenses = java.util.Collections.newSetFromMap(java.util.WeakHashMap<LensRefresher, Boolean>())
}

/** Repaints one lens completely; see [LiquidBackdrop.lenses]. */
internal fun interface LensRefresher {
    fun refresh()
}

/** What a lens last drew, so the source can have it recorded again without drawing the lens itself. */
private class LensRecording {
    var backdrop: LiquidBackdrop? = null
    var density: Density? = null
    var layoutDirection = LayoutDirection.Ltr
    var size = IntSize.Zero
    var pad = 0f
    var offset = Offset.Zero

    // The lens's shader uniforms; see [LensShader].
    var width = 0f
    var height = 0f
    var radius = 0f
    var thickness = 0f
    var refraction = 0f
    var dispersion = 0f
    var touch = Offset.Zero
    var press = 0f
}

/**
 * One compiled lens program for every glass surface. Each control used to compile its own, 0.2-0.9 ms on the
 * main thread apiece - six at once whenever the photo viewer opened. A render effect keeps a copy of the
 * uniforms it was created with, so lenses share the shader by setting theirs right before creating one.
 * Main thread only, like all drawing.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object LensShader {
    val shader by lazy(LazyThreadSafetyMode.NONE) { RuntimeShader(LIQUID_GLASS_SHADER) }

    fun effect(lens: LensRecording): AndroidRenderEffect {
        val pad = lens.pad
        shader.setFloatUniform("box", pad, pad, lens.width, lens.height)
        shader.setFloatUniform("radius", lens.radius)
        shader.setFloatUniform("thickness", lens.thickness)
        shader.setFloatUniform("refraction", lens.refraction)
        shader.setFloatUniform("dispersion", lens.dispersion)
        shader.setFloatUniform("touch", lens.touch.x + pad, lens.touch.y + pad)
        shader.setFloatUniform("press", lens.press)
        return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}

/** Backdrop recordings by the HazeState that names the source, so glass can find what lies behind it. */
internal object LiquidBackdrops {
    private val backdrops = java.util.WeakHashMap<HazeState, LiquidBackdrop>()

    operator fun get(state: HazeState): LiquidBackdrop? = backdrops[state]

    fun register(state: HazeState, backdrop: LiquidBackdrop) {
        backdrops[state] = backdrop
    }

    fun unregister(state: HazeState, backdrop: LiquidBackdrop) {
        if (backdrops[state] === backdrop) backdrops.remove(state)
    }
}

/** Records the content into a layer every frame (and draws it as usual) for the glass that refracts it. */
@Composable
internal fun Modifier.liquidBackdropSource(state: HazeState): Modifier {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { LiquidBackdrop(layer) }
    DisposableEffect(state, backdrop) {
        LiquidBackdrops.register(state, backdrop)
        onDispose { LiquidBackdrops.unregister(state, backdrop) }
    }
    return this
        .onGloballyPositioned { backdrop.position = it.positionInRoot() }
        .drawWithContent {
            layer.record(this, layoutDirection, IntSize(ceil(size.width).toInt(), ceil(size.height).toInt())) {
                this@drawWithContent.drawContent()
            }
            drawLayer(layer)
            backdrop.lenses.toList().forEach { it.refresh() }
        }
}

/** Touch state of one glass surface: finger position, press (0 rest .. 1 pressed, overshoots) and ripple (0..1). */
@Stable
internal class LiquidTouch {
    var point by mutableStateOf(Offset.Unspecified)
    val press = Animatable(0f)
    val ripple = Animatable(1f)
}

/**
 * A Liquid Glass surface in [shape]: refraction of [state]'s content where available, a light blur or the
 * solid fallback otherwise, the glass rim, and the touch response.
 */
@Composable
internal fun Modifier.liquidGlass(shape: Shape, spec: GlassSpec, state: HazeState?): Modifier {
    val light = LocalAppTheme.current.palette.isLight
    val palette = LocalAppTheme.current.palette
    val touch = remember { LiquidTouch() }
    val position = remember { mutableStateOf(Offset.Zero) }
    val refract = state != null && liquidRefractionEnabled()
    val blur = state != null && glassEnabled()

    var result = this
        .onGloballyPositioned { position.value = it.positionInRoot() }
        .liquidPressScale(touch)
        .clip(shape)
        .liquidTouchInput(touch)
    result = when {
        refract && state != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            result.liquidLens(shape, state, spec, touch, position)
        blur && state != null -> result.hazeChild(
            state,
            HazeStyle(
                backgroundColor = palette.background,
                tint = HazeTint(spec.tint),
                blurRadius = 4.dp,
                fallbackTint = HazeTint(spec.fallback)
            )
        )
        else -> result.background(spec.fallback)
    }
    return result
        .glassRim(shape, GlassRim.Liquid, light)
        .liquidTouchGlow(touch)
}

/** Swells on press and bounces back past its size on release, like a drop of gel. */
private fun Modifier.liquidPressScale(touch: LiquidTouch): Modifier = graphicsLayer {
    val p = touch.press.value
    val grow = 9.dp.toPx()
    scaleX = 1f + p * (grow / size.width.coerceAtLeast(1f)).coerceAtMost(0.14f)
    scaleY = 1f + p * (grow / size.height.coerceAtLeast(1f)).coerceAtMost(0.14f)
}

/** Watches touches without consuming them, so the control's own click handling is untouched. */
private fun Modifier.liquidTouchInput(touch: LiquidTouch): Modifier = pointerInput(touch) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touch.point = down.position
            launch {
                touch.ripple.snapTo(0f)
                touch.ripple.animateTo(1f, tween(700, easing = LinearOutSlowInEasing))
            }
            launch { touch.press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f)) }
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                touch.point = change.position
            }
            launch { touch.press.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 360f)) }
        }
    }
}

/** Light that starts under the fingertip and spreads through the glass as a fading ring. */
private fun Modifier.liquidTouchGlow(touch: LiquidTouch): Modifier = drawWithContent {
    val ripple = touch.ripple.value
    val press = touch.press.value.coerceAtLeast(0f)
    if (ripple < 1f || press > 0.01f) {
        val center = if (touch.point.isSpecified) touch.point else Offset(size.width / 2f, size.height / 2f)
        val maxDim = size.maxDimension
        val glowRadius = maxDim * (0.35f + ripple * 0.9f)
        val glowAlpha = ((1f - ripple) * 0.30f + press * 0.12f).coerceIn(0f, 0.45f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = glowAlpha), Color.Transparent),
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center
        )
        if (ripple < 1f) {
            drawCircle(
                color = Color.White.copy(alpha = (1f - ripple) * 0.5f),
                radius = maxDim * ripple * 1.1f,
                center = center,
                style = Stroke(width = 2.dp.toPx() * (1f - ripple))
            )
        }
    }
    drawContent()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.liquidLens(
    shape: Shape,
    state: HazeState,
    spec: GlassSpec,
    touch: LiquidTouch,
    position: MutableState<Offset>
): Modifier {
    val lensLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    // Barely any blur: Liquid Glass bends light rather than scattering it. A small saturation boost keeps the
    // content behind it vivid.
    val baseEffect = remember(density) {
        val blurPx = with(density) { 1.5.dp.toPx() }
        AndroidRenderEffect.createChainEffect(
            AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.3f) })),
            AndroidRenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        )
    }
    // Most of the tint, not all of it: the bent content stays visible, but white labels stay readable over
    // bright photos (at less than half of it the tab labels vanished over sky and snow).
    val tint = spec.tint.copy(alpha = spec.tint.alpha * 0.8f)
    val recording = remember { LensRecording() }
    // A new display list makes the renderer redraw the lens's content in full, whatever its change tracking saw,
    // and a fresh effect object carries the lens's current uniforms.
    val refresher = remember(lensLayer, baseEffect) {
        LensRefresher {
            val backdrop = recording.backdrop ?: return@LensRefresher
            val recordDensity = recording.density ?: return@LensRefresher
            if (lensLayer.isReleased) return@LensRefresher
            val offset = recording.offset
            val pad = recording.pad
            lensLayer.record(recordDensity, recording.layoutDirection, recording.size) {
                translate(offset.x + pad, offset.y + pad) { drawLayer(backdrop.layer) }
            }
            // Refract first, then blur: the renderer only blurs the part of the content it expects to be read
            // (about the clipped glass), so a lens fed the blurred content read uninitialised memory wherever its
            // rim reached further out - a strip of coloured garbage along the bottom edge of the tab bar.
            lensLayer.renderEffect = AndroidRenderEffect.createChainEffect(baseEffect, LensShader.effect(recording))
                .asComposeRenderEffect()
        }
    }
    DisposableEffect(state, refresher) {
        onDispose {
            LiquidBackdrops[state]?.lenses?.remove(refresher)
            recording.backdrop = null
        }
    }

    return drawWithContent {
        val backdrop = LiquidBackdrops[state]
        if (backdrop == null) {
            drawRect(spec.fallback)
            drawContent()
            return@drawWithContent
        }
        backdrop.lenses.add(refresher)
        val minDim = size.minDimension
        val thickness = minOf(minDim * 0.5f, 22.dp.toPx())
        val refraction = 0.75f
        val dispersion = 0.12f
        // The lens reaches outside the glass, so the layer covers a margin around it.
        val pad = ceil(thickness * refraction * (1f + dispersion) + 4.dp.toPx())
        val outline = shape.createOutline(size, layoutDirection, this)
        val radius = ((outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f).coerceAtMost(minDim / 2f)

        recording.width = size.width
        recording.height = size.height
        recording.radius = radius
        recording.thickness = thickness
        recording.refraction = refraction
        recording.dispersion = dispersion
        recording.touch = if (touch.point.isSpecified) touch.point else Offset(size.width / 2f, size.height / 2f)
        recording.press = touch.press.value
        recording.backdrop = backdrop
        // The backdrop's origin in this surface's coordinates. Read here, in the lens's own draw, so moving
        // either one redraws the lens (and not the whole source, which also calls the refresher).
        recording.offset = backdrop.position - position.value
        recording.density = density
        recording.layoutDirection = layoutDirection
        recording.size = IntSize(ceil(size.width + pad * 2f).toInt(), ceil(size.height + pad * 2f).toInt())
        recording.pad = pad
        refresher.refresh()
        translate(-pad, -pad) { drawLayer(lensLayer) }
        drawRect(tint)
        drawContent()
    }
}

private const val LIQUID_GLASS_SHADER = """
uniform shader content;
uniform float4 box;
uniform float radius;
uniform float thickness;
uniform float refraction;
uniform float dispersion;
uniform float2 touch;
uniform float press;

float sdRoundBox(float2 p, float2 c, float2 b, float r) {
    float2 q = abs(p - c) - b + float2(r, r);
    return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 p) {
    float2 c = box.xy + box.zw * 0.5;
    float2 b = box.zw * 0.5;
    float d = sdRoundBox(p, c, b, radius);
    if (d > 0.5) {
        return content.eval(p);
    }
    float2 grad = float2(
        sdRoundBox(p + float2(1.0, 0.0), c, b, radius) - sdRoundBox(p - float2(1.0, 0.0), c, b, radius),
        sdRoundBox(p + float2(0.0, 1.0), c, b, radius) - sdRoundBox(p - float2(0.0, 1.0), c, b, radius)
    );
    float gl = length(grad);
    float2 n = gl > 0.0001 ? grad / gl : float2(0.0, 0.0);

    // Circular lens profile: 1 at the rim, 0 once the glass is a full thickness deep.
    float t = clamp(-d / thickness, 0.0, 1.0);
    float u = 1.0 - t;
    float edge = 1.0 - sqrt(max(1.0 - u * u, 0.0));

    // Slight magnification of the whole pane, plus a bulge around the finger while pressed.
    float2 base = c + (p - c) * (1.0 - 0.035 * refraction);
    float2 toTouch = p - touch;
    float reach = max(min(box.z, box.w), 1.0);
    float fall = clamp(1.0 - length(toTouch) / reach, 0.0, 1.0);
    base -= toTouch * fall * fall * 0.22 * press;

    // The rim pulls in content from outside the glass; red and blue bend slightly differently.
    float2 shift = n * edge * thickness * refraction;
    half4 green = content.eval(base + shift);
    half red = content.eval(base + shift * (1.0 + dispersion)).r;
    half blue = content.eval(base + shift * (1.0 - dispersion)).b;
    return half4(red, green.g, blue, green.a);
}
"""
