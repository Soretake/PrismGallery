package com.example.amoledgallery.ui.design

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.amoledgallery.AmoledGalleryApp
import com.example.amoledgallery.PerformanceProfile
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.frost
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Backdrop of the tab content (the grid, album list, menu). Bottom bars and tab-level top bars blur
 * it. Provided by MainActivity.
 */
val LocalContentHaze = staticCompositionLocalOf<HazeState?> { null }

/** Backdrop of the whole app window - what dialogs, sheets and popup menus blur behind them. */
val LocalRootHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * Blur backdrops whose recorded content encloses the current composition. Haze throws if a glass
 * surface draws inside the very backdrop it blurs, so [glass] falls back to its solid tint for those.
 */
val LocalEnclosingHazeSources = staticCompositionLocalOf<Set<HazeState>> { emptySet() }

/**
 * Whether the tab around the current composition is the one on screen. All tabs stay composed (MainActivity), so
 * endless animations check this to stop asking for frames while their tab is hidden.
 */
val LocalTabVisible = compositionLocalOf { true }

/** A Box whose content becomes the blur backdrop [state]; glass inside it won't try to blur it. */
@Composable
fun HazeSource(
    state: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val enclosing = LocalEnclosingHazeSources.current
    // Without real blur (LOW/MEDIUM, API < 31) nothing samples the backdrop, so don't record the content
    // into an extra backdrop layer on every frame at all.
    val recordBackdrop = glassEnabled()
    // iOS Liquid Glass refracts a recording of the content itself instead of a blurred copy (API 33+).
    val liquidRecorder = if (recordBackdrop && liquidRefractionEnabled()) Modifier.liquidBackdropSource(state) else Modifier
    Box(modifier = (if (recordBackdrop) Modifier.haze(state) else Modifier).then(liquidRecorder).then(modifier)) {
        CompositionLocalProvider(LocalEnclosingHazeSources provides if (recordBackdrop) enclosing + state else enclosing) {
            content()
        }
    }
}

/** Live graphics quality, so a tier change recomposes every glass surface immediately. */
val LocalGraphicsQuality = compositionLocalOf { AmoledGalleryApp.performanceProfile }

/** Real blur needs RenderEffect (API 31+) and is part of the HIGH/ULTRA effect budget only. */
@Composable
@ReadOnlyComposable
fun glassEnabled(): Boolean {
    val quality = LocalGraphicsQuality.current
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (quality == PerformanceProfile.HIGH || quality == PerformanceProfile.ULTRA)
}

/** ULTRA-only flourishes: continuously animated waves, refraction sheen, parallax. */
@Composable
@ReadOnlyComposable
fun ultraEffects(): Boolean = LocalGraphicsQuality.current == PerformanceProfile.ULTRA

enum class GlassRim {
    /** Flat frosted surface, no highlight. */
    None,
    /** Faint edge highlight (One UI / HyperOS frosted glass). */
    Subtle,
    /** Bright specular rim and top sheen (iOS Liquid Glass). */
    Liquid
}

data class GlassSpec(
    /** Translucent tint laid over the blurred backdrop. */
    val tint: Color,
    /** Opaque-ish color used when blur is unavailable (LOW/MEDIUM, API < 31). */
    val fallback: Color,
    val blur: Dp,
    val rim: GlassRim,
    val noise: Float = 0f
)

/** The glass used for floating bars (bottom navigation, selection toolbars, top bars). */
@Composable
@ReadOnlyComposable
fun barGlass(): GlassSpec {
    val theme = LocalAppTheme.current
    val light = theme.palette.isLight
    return when (theme.language) {
        DesignLanguage.SAMSUNG_ONEUI -> GlassSpec(
            tint = theme.palette.frost(Color(0xFF1E1E1E), if (light) 0.72f else 0.66f),
            fallback = theme.palette.frost(Color(0xFF1E1E1E), 0.97f),
            blur = 30.dp,
            rim = GlassRim.Subtle
        )
        // Google Photos' floating bar: a dark frosted pill without a rim.
        DesignLanguage.GOOGLE_EXPRESSIVE -> GlassSpec(
            tint = theme.palette.surface.copy(alpha = if (light) 0.84f else 0.86f),
            fallback = theme.palette.surface.copy(alpha = 0.98f),
            blur = 28.dp,
            rim = GlassRim.None
        )
        DesignLanguage.IOS_LIQUID_GLASS -> if (light) {
            GlassSpec(Color.White.copy(alpha = 0.46f), Color.White.copy(alpha = 0.96f), 12.dp, GlassRim.Liquid)
        } else {
            GlassSpec(Color(0xFF242428).copy(alpha = 0.42f), Color(0xFF242428).copy(alpha = 0.96f), 12.dp, GlassRim.Liquid)
        }
        DesignLanguage.XIAOMI_HYPEROS -> if (light) {
            GlassSpec(Color.White.copy(alpha = 0.74f), Color.White.copy(alpha = 0.98f), 26.dp, GlassRim.Subtle, noise = 0.03f)
        } else {
            GlassSpec(Color(0xFF232323).copy(alpha = 0.72f), Color(0xFF232323).copy(alpha = 0.98f), 26.dp, GlassRim.Subtle, noise = 0.03f)
        }
    }
}

/**
 * Draws a glass surface in [shape]: the blurred [state] backdrop (or [GlassSpec.fallback] when blur is
 * off), the tint, and the language's rim highlight. Content placed inside draws on top.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    spec: GlassSpec = barGlass(),
    state: HazeState? = LocalContentHaze.current,
    mask: Brush? = null
): Modifier {
    val theme = LocalAppTheme.current
    // iOS controls (buttons, capsules, the tab bar) are Liquid Glass: refraction instead of frosting, and a
    // touch response. Large frosted surfaces - dialogs, sheets, menus, masked bars - keep their blur.
    if (theme.language == DesignLanguage.IOS_LIQUID_GLASS && spec.rim == GlassRim.Liquid && spec.blur <= 16.dp && mask == null) {
        return liquidGlass(shape, spec, state?.takeIf { it !in LocalEnclosingHazeSources.current })
    }
    val palette = theme.palette
    val blur = state != null && spec.blur > 0.dp && glassEnabled() &&
        state !in LocalEnclosingHazeSources.current
    var result = this.clip(shape)
    result = if (blur && state != null) {
        val style = HazeStyle(
            backgroundColor = palette.background,
            tint = HazeTint(spec.tint),
            blurRadius = spec.blur,
            noiseFactor = spec.noise,
            fallbackTint = HazeTint(spec.fallback)
        )
        if (mask != null) result.hazeChild(state, style) { this.mask = mask } else result.hazeChild(state, style)
    } else {
        result.background(spec.fallback)
    }
    if (spec.rim != GlassRim.None) {
        result = result.glassRim(shape, spec.rim, palette.isLight)
    }
    return result
}

/** Specular rim + sheen that turns a frosted rectangle into a piece of glass. Drawn behind content. */
fun Modifier.glassRim(shape: Shape, rim: GlassRim, light: Boolean): Modifier =
    if (rim == GlassRim.Liquid) liquidGlassRim(shape, light) else frostedGlassRim(shape, rim, light)

/**
 * Apple's Liquid Glass look: a thick, rounded pane. Its edge bends the light - a bright band hugging the rim,
 * lit from above and shaded below - with sharp specular highlights on the top-left and bottom-right edges, a
 * soft glow towards the middle and a fine dark outline that lifts it off the content behind.
 */
private fun Modifier.liquidGlassRim(shape: Shape, light: Boolean): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val minDim = size.minDimension
    // Strokes are centred on the (already clipped) edge, so each one is doubled to get its visible width.
    val bandWidth = (minDim * 0.16f).coerceIn(3.dp.toPx(), 9.dp.toPx())
    val band = Stroke(width = bandWidth * 2f)
    val glow = Stroke(width = bandWidth * 4f)
    val specularStroke = Stroke(width = 1.2.dp.toPx() * 2f)
    val outlineStroke = Stroke(width = 0.6.dp.toPx() * 2f)

    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (light) 0.34f else 0.10f),
        0.4f to Color.Transparent,
        1f to Color.Transparent
    )
    // Kept light: the refraction itself now shapes the edge; these only add the sheen of the glass surface.
    val glowBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (light) 0.08f else 0.025f),
        0.5f to Color.Transparent,
        1f to Color.White.copy(alpha = if (light) 0.04f else 0.012f)
    )
    val bandBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (light) 0.32f else 0.10f),
        0.35f to Color.White.copy(alpha = if (light) 0.08f else 0.025f),
        0.7f to Color.Transparent,
        1f to Color.White.copy(alpha = if (light) 0.18f else 0.05f)
    )
    val shadeBrush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.62f to Color.Transparent,
        1f to Color.Black.copy(alpha = if (light) 0.06f else 0.20f)
    )
    val specular = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (light) 0.95f else 0.80f),
        0.18f to Color.White.copy(alpha = 0.24f),
        0.42f to Color.White.copy(alpha = 0.04f),
        0.58f to Color.White.copy(alpha = 0.04f),
        0.82f to Color.White.copy(alpha = 0.20f),
        1f to Color.White.copy(alpha = if (light) 0.70f else 0.55f),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    onDrawWithContent {
        drawOutline(outline, sheen)
        drawOutline(outline, glowBrush, style = glow)
        drawOutline(outline, bandBrush, style = band)
        drawOutline(outline, shadeBrush, style = band)
        drawOutline(outline, Color.Black.copy(alpha = if (light) 0.08f else 0.28f), style = outlineStroke)
        drawOutline(outline, specular, style = specularStroke)
        drawContent()
    }
}

private fun Modifier.frostedGlassRim(shape: Shape, rim: GlassRim, light: Boolean): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val liquid = rim == GlassRim.Liquid
    // The stroke is centred on the (already clipped) edge, so double it to get the visible width.
    val rimStroke = Stroke(width = (if (liquid) 1.4.dp else 0.8.dp).toPx() * 2f)
    val hairline = Stroke(width = 0.5.dp.toPx() * 2f)
    val rimBrush = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (liquid) 0.62f else 0.20f),
        0.28f to Color.White.copy(alpha = if (liquid) 0.12f else 0.05f),
        0.72f to Color.White.copy(alpha = 0.03f),
        1f to Color.White.copy(alpha = if (liquid) 0.34f else 0.08f),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (liquid) (if (light) 0.40f else 0.13f) else 0.04f),
        0.45f to Color.Transparent,
        0.88f to Color.Transparent,
        1f to Color.White.copy(alpha = if (liquid) 0.07f else 0f)
    )
    onDrawWithContent {
        drawOutline(outline, sheen)
        drawOutline(outline, rimBrush, style = rimStroke)
        if (light) drawOutline(outline, Color.Black.copy(alpha = 0.07f), style = hairline)
        drawContent()
    }
}

/** Screen background for the active language - flat, except Material's soft tonal glow. */
@Composable
fun Modifier.screenBackground(): Modifier {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    return if (theme.language == DesignLanguage.GOOGLE_EXPRESSIVE) {
        this
            .background(palette.background)
            .drawBehind {
                // Gemini: black (or near-white) with a glow in the accent's hue rising from the bottom edge.
                val glow = if (palette.isLight) {
                    accentTone(palette.accent, 0.36f, 0.97f).copy(alpha = 0.95f)
                } else {
                    accentTone(palette.accent, 0.75f, 0.42f).copy(alpha = 0.6f)
                }
                drawRect(Brush.verticalGradient(0f to Color.Transparent, if (palette.isLight) 0.5f to Color.Transparent else 0.52f to Color.Transparent, 1f to glow))
            }
    } else {
        this.background(palette.background)
    }
}

/** Bottom content padding so the last row can scroll clear of the (floating) bottom bar. */
@Composable
fun bottomBarContentPadding(extra: Dp = 0.dp): Dp {
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return LocalAppTheme.current.shapes.bottomBarHeight + inset + extra
}

/** Springy press feedback (iOS glass, Material buttons). */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.94f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "press_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
