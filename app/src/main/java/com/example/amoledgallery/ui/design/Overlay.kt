package com.example.amoledgallery.ui.design
import com.example.amoledgallery.ui.i18n.strings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.ui.theme.AppColorPalette
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.destructive
import com.example.amoledgallery.ui.theme.frost
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * Dialogs, sheets and popup menus are rendered INSIDE the activity window by an [OverlayHost] rather
 * than as separate platform windows. A separate window can only get a blurred backdrop through
 * FLAG_BLUR_BEHIND, which many devices (including the Galaxy A15) do not support - rendered in-window
 * they can blur the real app content with Haze on every API 31+ device.
 */

@Stable
class OverlayHostState {
    internal val entries = mutableStateListOf<OverlayEntry>()
}

internal class OverlayEntry(content: @Composable (Transition<Boolean>) -> Unit) {
    val id: Long = nextOverlayId++
    var content by mutableStateOf(content)
    val visibility = MutableTransitionState(false).apply { targetState = true }
    var removed by mutableStateOf(false)
}

private var nextOverlayId = 0L

val LocalOverlayHost = staticCompositionLocalOf<OverlayHostState?> { null }

/** Renders every open overlay. Place it last inside a full-screen Box so it draws above the app. */
@Composable
fun OverlayHost(state: OverlayHostState) {
    state.entries.toList().forEach { entry ->
        key(entry.id) {
            val transition = rememberTransition(entry.visibility, label = "overlay")
            entry.content(transition)
            LaunchedEffect(entry.removed) {
                if (entry.removed) {
                    // Keep the entry composed until its exit animation has fully played.
                    snapshotFlow { entry.visibility.isIdle && !entry.visibility.currentState }.first { it }
                    state.entries.remove(entry)
                }
            }
        }
    }
}

/**
 * Shows [content] in the overlay host for as long as this call stays in composition, then plays the
 * exit transition. [content] receives the visibility transition to drive its own animation.
 */
@Composable
fun OverlayWindow(content: @Composable (Transition<Boolean>) -> Unit) {
    val host = LocalOverlayHost.current ?: error("OverlayWindow needs an OverlayHost higher up")
    val entry = remember { OverlayEntry(content) }
    SideEffect { entry.content = content }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose {
            entry.removed = true
            entry.visibility.targetState = false
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Scrim
// ---------------------------------------------------------------------------------------------

private fun scrimColor(language: DesignLanguage, palette: AppColorPalette): Color = when (language) {
    DesignLanguage.SAMSUNG_ONEUI -> Color.Black.copy(alpha = 0.45f)
    DesignLanguage.GOOGLE_EXPRESSIVE -> Color.Black.copy(alpha = 0.52f)
    DesignLanguage.IOS_LIQUID_GLASS -> Color.Black.copy(alpha = if (palette.isLight) 0.18f else 0.38f)
    DesignLanguage.XIAOMI_HYPEROS -> Color.Black.copy(alpha = if (palette.isLight) 0.30f else 0.45f)
}

@Composable
private fun OverlayScrim(
    transition: Transition<Boolean>,
    onTap: (() -> Unit)?,
    blur: Boolean
) {
    val theme = LocalAppTheme.current
    val alpha by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 240 else 180) },
        label = "scrim_alpha"
    ) { if (it) 1f else 0f }
    val rootHaze = LocalRootHaze.current
    val dim = scrimColor(theme.language, theme.palette)
    val useBlur = blur && glassEnabled()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (useBlur && rootHaze != null) {
                    Modifier.hazeChild(
                        rootHaze,
                        HazeStyle(
                            backgroundColor = theme.palette.background,
                            tint = HazeTint(dim),
                            blurRadius = 18.dp,
                            fallbackTint = HazeTint(dim)
                        )
                    )
                } else {
                    Modifier.background(dim)
                }
            )
            .then(
                // Block touches to the app underneath while open; stop blocking during the exit.
                if (transition.targetState) {
                    Modifier.pointerInput(onTap) { detectTapGestures { onTap?.invoke() } }
                } else {
                    Modifier
                }
            )
    )
}

// ---------------------------------------------------------------------------------------------
// Dialog
// ---------------------------------------------------------------------------------------------

enum class DialogButtonStyle { Default, Primary, Destructive, Cancel }

data class DialogButton(
    val text: String,
    val style: DialogButtonStyle = DialogButtonStyle.Default,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * A dialog drawn the way each OS draws it:
 * - One UI: frosted dark card, start-aligned bold title, split text buttons at the bottom.
 * - Material 3 Expressive: tonal card, large headline, end-aligned buttons with a filled pill, springy pop.
 * - iOS: centered Liquid Glass alert that settles in from a slight zoom, capsule buttons.
 * - HyperOS: sheet-like card anchored to the bottom of the screen, two wide pill buttons.
 *
 * Pass [buttons] in visual order, dismiss action first and confirm action last.
 */
@Composable
fun ThemedDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    message: String? = null,
    buttons: List<DialogButton> = emptyList(),
    dismissible: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    OverlayWindow { transition ->
        DialogFrame(transition, onDismissRequest, title, message, buttons, dismissible, content)
    }
}

@Composable
private fun DialogFrame(
    transition: Transition<Boolean>,
    onDismissRequest: () -> Unit,
    title: String?,
    message: String?,
    buttons: List<DialogButton>,
    dismissible: Boolean,
    content: (@Composable ColumnScope.() -> Unit)?
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language

    BackHandler(enabled = transition.targetState && dismissible) { onDismissRequest() }

    val progress by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                when (language) {
                    DesignLanguage.GOOGLE_EXPRESSIVE -> spring(dampingRatio = 0.68f, stiffness = 420f)
                    DesignLanguage.XIAOMI_HYPEROS -> spring(dampingRatio = 0.9f, stiffness = 600f)
                    else -> spring(dampingRatio = 0.86f, stiffness = 520f)
                }
            } else {
                tween(160)
            }
        },
        label = "dialog_progress"
    ) { if (it) 1f else 0f }

    Box(modifier = Modifier.fillMaxSize()) {
        OverlayScrim(
            transition = transition,
            onTap = if (dismissible) onDismissRequest else null,
            blur = true
        )

        val bottomAnchored = language == DesignLanguage.XIAOMI_HYPEROS
        val centeredText = language == DesignLanguage.IOS_LIQUID_GLASS || language == DesignLanguage.XIAOMI_HYPEROS
        val sidePadding = when (language) {
            DesignLanguage.IOS_LIQUID_GLASS -> 36.dp
            DesignLanguage.XIAOMI_HYPEROS -> 12.dp
            else -> 24.dp
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = sidePadding, vertical = if (bottomAnchored) 10.dp else 24.dp),
            contentAlignment = if (bottomAnchored) Alignment.BottomCenter else Alignment.Center
        ) {
            val shape = RoundedCornerShape(theme.shapes.dialogCornerRadius)
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = progress
                        alpha = p.coerceIn(0f, 1f)
                        when (language) {
                            DesignLanguage.XIAOMI_HYPEROS -> translationY = (1f - p) * 180.dp.toPx()
                            DesignLanguage.IOS_LIQUID_GLASS -> {
                                val s = 1.14f - 0.14f * p
                                scaleX = s
                                scaleY = s
                            }
                            DesignLanguage.GOOGLE_EXPRESSIVE -> {
                                val s = 0.82f + 0.18f * p
                                scaleX = s
                                scaleY = s
                            }
                            DesignLanguage.SAMSUNG_ONEUI -> {
                                val s = 0.92f + 0.08f * p
                                scaleX = s
                                scaleY = s
                            }
                        }
                    }
                    .dialogSurface(shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(
                        when (language) {
                            DesignLanguage.SAMSUNG_ONEUI -> PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
                            DesignLanguage.GOOGLE_EXPRESSIVE -> PaddingValues(24.dp)
                            DesignLanguage.IOS_LIQUID_GLASS -> PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp)
                            DesignLanguage.XIAOMI_HYPEROS -> PaddingValues(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 20.dp)
                        }
                    ),
                horizontalAlignment = if (centeredText) Alignment.CenterHorizontally else Alignment.Start
            ) {
                val textAlign = if (centeredText) TextAlign.Center else TextAlign.Start
                if (title != null) {
                    Text(
                        text = title,
                        color = palette.onBackground,
                        fontSize = when (language) {
                            DesignLanguage.SAMSUNG_ONEUI -> 20.sp
                            DesignLanguage.GOOGLE_EXPRESSIVE -> 24.sp
                            DesignLanguage.IOS_LIQUID_GLASS -> 17.sp
                            DesignLanguage.XIAOMI_HYPEROS -> 21.sp
                        },
                        fontWeight = when (language) {
                            DesignLanguage.GOOGLE_EXPRESSIVE -> FontWeight.Normal
                            DesignLanguage.SAMSUNG_ONEUI -> FontWeight.Bold
                            else -> FontWeight.SemiBold
                        },
                        lineHeight = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 32.sp else 24.sp,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (message != null) {
                    Spacer(modifier = Modifier.height(if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 14.dp else 8.dp))
                    Text(
                        text = message,
                        color = when (language) {
                            DesignLanguage.GOOGLE_EXPRESSIVE, DesignLanguage.XIAOMI_HYPEROS -> palette.textMuted
                            else -> palette.onBackground.copy(alpha = 0.86f)
                        },
                        fontSize = when (language) {
                            DesignLanguage.IOS_LIQUID_GLASS -> 14.sp
                            DesignLanguage.XIAOMI_HYPEROS -> 17.sp
                            else -> 15.sp
                        },
                        lineHeight = if (language == DesignLanguage.XIAOMI_HYPEROS) 24.sp else 21.sp,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (content != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    content()
                }
                if (buttons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(if (language == DesignLanguage.SAMSUNG_ONEUI) 14.dp else 22.dp))
                    DialogButtons(buttons)
                }
            }
        }
    }
}

@Composable
private fun Modifier.dialogSurface(shape: Shape): Modifier {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val rootHaze = LocalRootHaze.current
    return when (theme.language) {
        DesignLanguage.SAMSUNG_ONEUI -> this.glass(
            shape,
            GlassSpec(palette.frost(Color(0xFF282828), 0.80f), palette.frost(Color(0xFF282828), 1f), 44.dp, GlassRim.Subtle),
            rootHaze
        )
        // Gemini's glowing translucent box.
        DesignLanguage.GOOGLE_EXPRESSIVE -> this.geminiGlass(shape, rootHaze, tintAlpha = 0.74f)
        DesignLanguage.IOS_LIQUID_GLASS -> this.glass(
            shape,
            if (palette.isLight) {
                GlassSpec(Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.98f), 40.dp, GlassRim.Liquid)
            } else {
                GlassSpec(Color(0xFF2A2A2D).copy(alpha = 0.58f), Color(0xFF2A2A2D).copy(alpha = 0.98f), 40.dp, GlassRim.Liquid)
            },
            rootHaze
        )
        DesignLanguage.XIAOMI_HYPEROS -> this
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(shape)
            .background(if (palette.isLight) palette.surface else palette.surfaceVariant)
    }
}

private fun dialogButtonTextColor(button: DialogButton, palette: AppColorPalette): Color = when (button.style) {
    DialogButtonStyle.Primary -> palette.accent
    DialogButtonStyle.Destructive -> palette.destructive
    DialogButtonStyle.Default, DialogButtonStyle.Cancel -> palette.onBackground
}

@Composable
private fun DialogButtons(buttons: List<DialogButton>) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    when (theme.language) {
        DesignLanguage.SAMSUNG_ONEUI -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            buttons.forEachIndexed { index, button ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(palette.onBackground.copy(alpha = 0.16f))
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(enabled = button.enabled, onClick = button.onClick)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val color = dialogButtonTextColor(button, palette)
                    Text(
                        text = button.text,
                        color = if (button.enabled) color else color.copy(alpha = 0.38f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        DesignLanguage.GOOGLE_EXPRESSIVE -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            buttons.forEach { button ->
                val filled = button.style == DialogButtonStyle.Primary || button.style == DialogButtonStyle.Destructive
                val container = if (button.style == DialogButtonStyle.Destructive) palette.destructive else palette.accent
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .pressScale(interaction, 0.92f)
                        .clip(CircleShape)
                        .then(
                            if (filled) {
                                Modifier.background(if (button.enabled) container else container.copy(alpha = 0.3f))
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = androidx.compose.material3.ripple(),
                            enabled = button.enabled,
                            onClick = button.onClick
                        )
                        .padding(horizontal = if (filled) 22.dp else 14.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = button.text,
                        color = when {
                            filled && button.style == DialogButtonStyle.Destructive -> Color.White
                            filled -> palette.onAccent
                            else -> palette.accent
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        DesignLanguage.IOS_LIQUID_GLASS -> {
            if (buttons.size > 2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { IosCapsuleButton(it, Modifier.fillMaxWidth()) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    buttons.forEach { IosCapsuleButton(it, Modifier.weight(1f)) }
                }
            }
        }

        DesignLanguage.XIAOMI_HYPEROS -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // HyperOS: wide pills on a lighter grey; cancel in soft white, delete in red.
            buttons.forEach { button ->
                val primary = button.style == DialogButtonStyle.Primary
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(interaction, 0.96f)
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                primary && button.enabled -> palette.accent
                                primary -> palette.accent.copy(alpha = 0.35f)
                                else -> lerp(if (palette.isLight) palette.surfaceVariant else palette.surfaceVariant, palette.onBackground, if (palette.isLight) 0.02f else 0.10f)
                            }
                        )
                        .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = button.enabled, onClick = button.onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = button.text,
                        color = when (button.style) {
                            DialogButtonStyle.Primary -> palette.onAccent
                            DialogButtonStyle.Destructive -> palette.destructive
                            else -> palette.onBackground.copy(alpha = 0.8f)
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun IosCapsuleButton(button: DialogButton, modifier: Modifier) {
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    val primary = button.style == DialogButtonStyle.Primary
    Box(
        modifier = modifier
            .pressScale(interaction, 0.95f)
            .height(48.dp)
            .clip(CircleShape)
            .background(
                when {
                    primary -> palette.accent.copy(alpha = if (button.enabled) 1f else 0.4f)
                    palette.isLight -> Color.Black.copy(alpha = 0.07f)
                    else -> Color.White.copy(alpha = 0.14f)
                }
            )
            .clickable(interactionSource = interaction, indication = null, enabled = button.enabled, onClick = button.onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = button.text,
            color = when (button.style) {
                DialogButtonStyle.Primary -> palette.onAccent
                DialogButtonStyle.Destructive -> palette.destructive
                else -> palette.onBackground
            }.let { if (button.enabled) it else it.copy(alpha = 0.5f) },
            fontSize = 17.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Sheet
// ---------------------------------------------------------------------------------------------

/**
 * A bottom sheet in the active language: One UI frosted dark sheet, Material tonal sheet with a spring,
 * iOS floating Liquid Glass card inset from the screen edges, HyperOS frosted light sheet. Dragging the
 * header down dismisses it. Scrollable content should use `Modifier.weight(1f, fill = false)`.
 */
@Composable
fun ThemedSheet(
    onDismissRequest: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    OverlayWindow { transition ->
        SheetFrame(transition, onDismissRequest, title, content)
    }
}

@Composable
private fun SheetFrame(
    transition: Transition<Boolean>,
    onDismissRequest: () -> Unit,
    title: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalAppTheme.current
    val language = theme.language

    BackHandler(enabled = transition.targetState) { onDismissRequest() }

    val progress by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 0.78f else 1f, stiffness = 360f)
            } else {
                tween(210)
            }
        },
        label = "sheet_progress"
    ) { if (it) 1f else 0f }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    val dragState = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) }
    val dragScope = rememberCoroutineScope()

    fun settleDrag(velocity: Float) {
        if (dragOffset > dismissThresholdPx || velocity > 2000f) {
            onDismissRequest()
        } else {
            dragScope.launch {
                animate(dragOffset, 0f, animationSpec = spring(stiffness = 500f)) { value, _ -> dragOffset = value }
            }
        }
    }

    // The sheet can be pulled down from anywhere: non-scrolling content drags it directly (draggable on
    // the whole panel), scrolling content hands over the rest of a downward drag once it is at its top.
    val sheetNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && dragOffset > 0f) {
                    val consumed = maxOf(available.y, -dragOffset)
                    dragOffset += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dragOffset += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffset > 0f) {
                    settleDrag(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OverlayScrim(transition, onDismissRequest, blur = true)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // A sheet with a text field: the room above the keyboard is what it may fill. Without this the
                // window was shifted up as a whole and the sheet's title slid under the status bar.
                .imePadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val floating = language == DesignLanguage.IOS_LIQUID_GLASS
            val corner = theme.shapes.dialogCornerRadius
            val shape = if (floating) RoundedCornerShape(corner) else RoundedCornerShape(topStart = corner, topEnd = corner)
            Column(
                modifier = Modifier
                    .then(
                        if (floating) Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp) else Modifier
                    )
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f)
                    .graphicsLayer { translationY = (1f - progress) * size.height + dragOffset }
                    .sheetSurface(shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .nestedScroll(sheetNestedScroll)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settleDrag(velocity) }
                    )
                    .then(if (floating) Modifier else Modifier.navigationBarsPadding())
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SheetHeader(title, onDismissRequest)
                }
                content()
            }
        }
    }
}

@Composable
private fun Modifier.sheetSurface(shape: Shape): Modifier {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val rootHaze = LocalRootHaze.current
    return when (theme.language) {
        DesignLanguage.SAMSUNG_ONEUI -> this.glass(
            shape,
            GlassSpec(palette.frost(Color(0xFF1C1C1C), 0.84f), palette.frost(Color(0xFF1C1C1C), 1f), 44.dp, GlassRim.Subtle),
            rootHaze
        )
        DesignLanguage.GOOGLE_EXPRESSIVE -> this.glass(
            shape,
            GlassSpec(palette.surface.copy(alpha = 0.86f), palette.surface, 44.dp, GlassRim.Subtle),
            rootHaze
        )
        DesignLanguage.IOS_LIQUID_GLASS -> this.glass(
            shape,
            if (palette.isLight) {
                GlassSpec(Color(0xFFF7F7FA).copy(alpha = 0.78f), Color(0xFFF7F7FA), 44.dp, GlassRim.Liquid)
            } else {
                GlassSpec(Color(0xFF1C1C1E).copy(alpha = 0.66f), Color(0xFF1C1C1E), 44.dp, GlassRim.Liquid)
            },
            rootHaze
        )
        DesignLanguage.XIAOMI_HYPEROS -> this.glass(
            shape,
            GlassSpec(palette.surface.copy(alpha = 0.90f), palette.surface, 44.dp, GlassRim.None),
            rootHaze
        )
    }
}

@Composable
private fun SheetHeader(title: String?, onDismissRequest: () -> Unit) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 32.dp else 36.dp,
                    height = if (language == DesignLanguage.IOS_LIQUID_GLASS) 5.dp else 4.dp
                )
                .clip(CircleShape)
                .background(palette.onBackground.copy(alpha = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 0.4f else 0.22f))
        )
    }

    if (title == null) {
        Spacer(modifier = Modifier.height(12.dp))
        return
    }

    when (language) {
        DesignLanguage.IOS_LIQUID_GLASS -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .pressScale(interaction)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (palette.isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f))
                    .clickable(interactionSource = interaction, indication = null, onClick = onDismissRequest),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, strings.close, tint = palette.onBackground, modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                color = palette.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        DesignLanguage.XIAOMI_HYPEROS -> Text(
            text = title,
            color = palette.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )

        DesignLanguage.GOOGLE_EXPRESSIVE -> Text(
            text = title,
            color = palette.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp)
        )

        DesignLanguage.SAMSUNG_ONEUI -> Text(
            text = title,
            color = palette.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Popup menu
// ---------------------------------------------------------------------------------------------

data class MenuEntry(
    val text: String,
    val icon: ImageVector? = null,
    val checked: Boolean = false,
    val destructive: Boolean = false,
    /** HyperOS shows each group of entries as its own stacked card (e.g. actions, then the view choice). */
    val group: Int = 0,
    val onClick: () -> Unit
)

/**
 * A popup menu anchored below [anchorBounds] (root coordinates, right-aligned to the anchor).
 * One UI frosted rounded popup, Material tonal menu with a selected-item container, iOS Liquid Glass
 * context menu with trailing icons and separators, HyperOS white floating card.
 */
@Composable
fun ThemedPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect?,
    entries: List<MenuEntry>
) {
    if (!expanded) return
    OverlayWindow { transition ->
        PopupMenuFrame(transition, onDismissRequest, anchorBounds, entries)
    }
}

@Composable
private fun PopupMenuFrame(
    transition: Transition<Boolean>,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect?,
    entries: List<MenuEntry>
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language

    BackHandler(enabled = transition.targetState) { onDismissRequest() }
    // The menu stays between the status bar and the navigation bar. A long menu (an album's, opened low on the
    // screen) used to be pushed up to the very top, where its first entries sat under the status bar.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topInset = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
    val bottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)

    val progress by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                if (language == DesignLanguage.GOOGLE_EXPRESSIVE) spring(dampingRatio = 0.7f, stiffness = 600f)
                else spring(dampingRatio = 0.85f, stiffness = 800f)
            } else {
                tween(130)
            }
        },
        label = "menu_progress"
    ) { if (it) 1f else 0f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (transition.targetState) Modifier.pointerInput(Unit) { detectTapGestures { onDismissRequest() } } else Modifier
            )
    ) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                val shape = RoundedCornerShape(
                    when (language) {
                        DesignLanguage.SAMSUNG_ONEUI -> 26.dp
                        DesignLanguage.GOOGLE_EXPRESSIVE -> 30.dp
                        DesignLanguage.IOS_LIQUID_GLASS -> 26.dp
                        DesignLanguage.XIAOMI_HYPEROS -> 24.dp
                    }
                )
                val popIn = Modifier.graphicsLayer {
                    val p = progress
                    alpha = p.coerceIn(0f, 1f)
                    val s = 0.82f + 0.18f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                if (language == DesignLanguage.XIAOMI_HYPEROS) {
                    Column(
                        modifier = popIn
                            .widthIn(min = 236.dp, max = 300.dp)
                            .width(IntrinsicSize.Max)
                            // Long menus (and landscape) scroll instead of running off the screen.
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                    ) {
                        entries.groupBy { it.group }.toSortedMap().values.forEach { group ->
                            val anyChecked = group.any { it.checked }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuSurface(shape)
                                    .padding(vertical = if (anyChecked) 0.dp else 6.dp)
                            ) {
                                group.forEach { entry ->
                                    MenuRow(entry, anyChecked) {
                                        onDismissRequest()
                                        entry.onClick()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = popIn
                            .widthIn(min = 196.dp, max = 290.dp)
                            .width(IntrinsicSize.Max)
                            .menuSurface(shape)
                            // Long menus (and landscape) scroll instead of running off the screen.
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 10.dp else 6.dp)
                    ) {
                        val anyChecked = entries.any { it.checked }
                        entries.forEachIndexed { index, entry ->
                            if (index > 0 && language == DesignLanguage.IOS_LIQUID_GLASS) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(palette.onBackground.copy(alpha = 0.12f))
                                )
                            }
                            MenuRow(entry, anyChecked) {
                                onDismissRequest()
                                entry.onClick()
                            }
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val margin = 12.dp.roundToPx()
            val gap = 6.dp.roundToPx()
            val top = topInset + margin
            val bottom = constraints.maxHeight - bottomInset - margin
            val placeable = measurables.first().measure(
                constraints.copy(minWidth = 0, minHeight = 0, maxHeight = (bottom - top).coerceAtLeast(0))
            )
            val anchor = anchorBounds ?: Rect(
                constraints.maxWidth.toFloat() - margin, 0f, constraints.maxWidth.toFloat() - margin, 0f
            )
            val x = (anchor.right.roundToInt() - placeable.width)
                .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            var y = anchor.bottom.roundToInt() + gap
            if (y + placeable.height > bottom) {
                y = (anchor.top.roundToInt() - placeable.height - gap).coerceAtLeast(top)
            }
            // Fits neither below nor above the anchor: as much of the screen as needed.
            y = y.coerceAtMost((bottom - placeable.height).coerceAtLeast(top))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(x, y)
            }
        }
    }
}

@Composable
private fun Modifier.menuSurface(shape: Shape): Modifier {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val rootHaze = LocalRootHaze.current
    return when (theme.language) {
        // One UI 8 popups are a solid grey card.
        DesignLanguage.SAMSUNG_ONEUI -> this
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(shape)
            .background(if (palette.isLight) Color.White else Color(0xFF3A3A3C))
        // Gemini menus: a black card with a hairline edge; the accent marks the chosen entry.
        DesignLanguage.GOOGLE_EXPRESSIVE -> this
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(shape)
            .background(if (palette.isLight) Color.White else Color(0xFF0C0D0E))
            .border(1.dp, palette.onBackground.copy(alpha = if (palette.isLight) 0.08f else 0.10f), shape)
        DesignLanguage.IOS_LIQUID_GLASS -> this.glass(
            shape,
            if (palette.isLight) {
                GlassSpec(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.98f), 30.dp, GlassRim.Liquid)
            } else {
                GlassSpec(Color(0xFF2C2C2E).copy(alpha = 0.55f), Color(0xFF2C2C2E).copy(alpha = 0.98f), 30.dp, GlassRim.Liquid)
            },
            rootHaze
        )
        DesignLanguage.XIAOMI_HYPEROS -> this
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(shape)
            .background(if (palette.isLight) palette.surface else palette.surfaceVariant)
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, anyChecked: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val textColor = when {
        entry.destructive -> palette.destructive
        entry.checked && theme.language != DesignLanguage.IOS_LIQUID_GLASS -> palette.accent
        else -> palette.onBackground
    }
    when (theme.language) {
        DesignLanguage.GOOGLE_EXPRESSIVE -> {
            // A neutral (white/grey/black) accent on its own tinted pill is unreadable: use the text colour instead.
            val neutral = isNeutralColor(palette.accent)
            val rowText = if (entry.checked && neutral) palette.onBackground else textColor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        when {
                            !entry.checked -> Color.Transparent
                            neutral -> palette.onBackground.copy(alpha = if (palette.isLight) 0.08f else 0.12f)
                            else -> vividAccent(palette.accent).copy(alpha = if (palette.isLight) 0.12f else 0.22f)
                        }
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.icon != null) {
                    Icon(entry.icon, null, tint = rowText, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(entry.text, color = rowText, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (entry.checked) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Rounded.Check, null, tint = if (neutral) palette.onBackground else palette.accent, modifier = Modifier.size(18.dp))
                }
            }
        }

        DesignLanguage.IOS_LIQUID_GLASS -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (anyChecked) {
                Box(modifier = Modifier.width(26.dp)) {
                    if (entry.checked) {
                        Icon(Icons.Rounded.Check, null, tint = palette.onBackground, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(entry.text, color = textColor, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (entry.icon != null) {
                Spacer(modifier = Modifier.width(16.dp))
                Icon(entry.icon, null, tint = textColor, modifier = Modifier.size(20.dp))
            }
        }

        DesignLanguage.SAMSUNG_ONEUI -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 30.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                entry.text,
                color = textColor,
                fontSize = 19.sp,
                fontWeight = if (entry.checked) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
        }

        // HyperOS: the chosen entry fills its row with an accent wash, with accent icon, text and check.
        DesignLanguage.XIAOMI_HYPEROS -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (entry.checked) palette.accent.copy(alpha = if (palette.isLight) 0.10f else 0.18f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (anyChecked && entry.icon != null) {
                Icon(entry.icon, null, tint = if (entry.checked) palette.accent else palette.onBackground, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(18.dp))
            }
            Text(entry.text, color = textColor, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (entry.checked) {
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Rounded.Check, null, tint = palette.accent, modifier = Modifier.size(24.dp))
            }
        }
    }
}
