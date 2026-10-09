package com.example.amoledgallery.ui.design
import com.example.amoledgallery.ui.i18n.strings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.cos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.destructive
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent
import dev.chrisbanes.haze.HazeState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Progress bar
// ---------------------------------------------------------------------------------------------

/**
 * Determinate progress in the active language: One UI's thick rounded bar, Material 3 Expressive's
 * wavy indicator (the wave travels on ULTRA), iOS's slim capsule, HyperOS's gradient bar.
 */
@Composable
fun ThemedProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 140f),
        label = "progress"
    )

    when (theme.language) {
        DesignLanguage.GOOGLE_EXPRESSIVE -> {
            val phase = if (ultraEffects()) {
                rememberInfiniteTransition(label = "wave").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
                    label = "wave_phase"
                )
            } else {
                remember { mutableFloatStateOf(0f) }
            }
            val accent = palette.accent
            Canvas(modifier = modifier.fillMaxWidth().height(16.dp)) {
                val stroke = 4.dp.toPx()
                val amplitude = 3.dp.toPx()
                val wavelength = 26.dp.toPx()
                val mid = size.height / 2f
                val width = size.width
                val end = width * animated
                val gap = 6.dp.toPx()

                val trackStart = (end + gap).coerceAtMost(width)
                if (trackStart < width - stroke) {
                    drawLine(
                        color = accent.copy(alpha = 0.26f),
                        start = Offset(trackStart, mid),
                        end = Offset(width - stroke / 2f, mid),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                // Material's stop indicator dot at the very end of the track.
                drawCircle(accent, radius = 2.dp.toPx(), center = Offset(width - 2.dp.toPx(), mid))

                if (end > stroke) {
                    val path = Path()
                    val p = phase.value
                    var x = stroke / 2f
                    path.moveTo(x, mid + amplitude * sin(2f * PI.toFloat() * (x / wavelength - p)))
                    while (x < end - stroke / 2f) {
                        x = min(x + 2f, end - stroke / 2f)
                        path.lineTo(x, mid + amplitude * sin(2f * PI.toFloat() * (x / wavelength - p)))
                    }
                    drawPath(path, accent, style = Stroke(width = stroke, cap = StrokeCap.Round))
                }
            }
        }

        DesignLanguage.SAMSUNG_ONEUI -> LinearBar(
            fraction = animated,
            height = 8.dp,
            track = palette.surfaceVariant,
            fill = Brush.horizontalGradient(listOf(palette.accent, palette.accent)),
            modifier = modifier
        )

        DesignLanguage.IOS_LIQUID_GLASS -> LinearBar(
            fraction = animated,
            height = 6.dp,
            track = palette.onBackground.copy(alpha = if (palette.isLight) 0.08f else 0.14f),
            fill = Brush.horizontalGradient(listOf(palette.accent, palette.accent)),
            modifier = modifier
        )

        DesignLanguage.XIAOMI_HYPEROS -> LinearBar(
            fraction = animated,
            height = 8.dp,
            track = palette.surfaceVariant,
            fill = Brush.horizontalGradient(listOf(lerp(palette.accent, Color.White, 0.35f), palette.accent)),
            modifier = modifier
        )
    }
}

@Composable
private fun LinearBar(
    fraction: Float,
    height: androidx.compose.ui.unit.Dp,
    track: Color,
    fill: Brush,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.001f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fill)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Segmented control
// ---------------------------------------------------------------------------------------------

/**
 * A single-choice control: One UI / iOS / HyperOS use a sliding thumb inside a track (iOS optionally as
 * a floating glass capsule), Material 3 Expressive uses a connected button group whose selected button
 * morphs into a full pill.
 */
@Composable
fun <T> ThemedSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    floatingGlass: Boolean = false
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language

    if (language == DesignLanguage.GOOGLE_EXPRESSIVE) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selected
                val inner by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 6.dp,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
                    label = "segment_corner"
                )
                val first = index == 0
                val last = index == options.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (first) 22.dp else inner,
                    bottomStart = if (first) 22.dp else inner,
                    topEnd = if (last) 22.dp else inner,
                    bottomEnd = if (last) 22.dp else inner
                )
                val container by animateColorAsState(
                    targetValue = if (isSelected) palette.accent else lerp(palette.surfaceVariant, palette.accent, 0.06f),
                    label = "segment_bg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)                        .clip(shape)
                        .background(container)
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                        Text(
                            text = label(option),
                            color = if (isSelected) palette.onAccent else palette.onBackground,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        return
    }

    val trackHeight = when (language) {
        DesignLanguage.SAMSUNG_ONEUI -> 46.dp
        DesignLanguage.IOS_LIQUID_GLASS -> if (floatingGlass) 46.dp else 38.dp
        else -> 40.dp
    }
    val trackPadding = if (language == DesignLanguage.SAMSUNG_ONEUI) 4.dp else 3.dp
    val trackShape = if (language == DesignLanguage.XIAOMI_HYPEROS) RoundedCornerShape(14.dp) else CircleShape
    val thumbShape = if (language == DesignLanguage.XIAOMI_HYPEROS) RoundedCornerShape(11.dp) else CircleShape

    val trackModifier = when {
        language == DesignLanguage.IOS_LIQUID_GLASS && floatingGlass -> Modifier.glass(CircleShape)
        language == DesignLanguage.IOS_LIQUID_GLASS -> Modifier
            .clip(trackShape)
            .background(palette.onBackground.copy(alpha = if (palette.isLight) 0.06f else 0.11f))
        else -> Modifier.clip(trackShape).background(palette.surfaceVariant)
    }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val trackPaddingPx = with(density) { trackPadding.toPx() }
    val latestOptions by rememberUpdatedState(options)
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)
    // Hold or drag on the control: the thumb follows the finger and the hovered segment is selected live.
    var dragX by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .then(trackModifier)
            // The slide gesture lives on the track itself, the parent of the segments, so it sees every
            // touch. Once it becomes a slide it consumes the moves, which cancels the segment taps.
            .pointerInput(trackPaddingPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!dragging) {
                            // A surrounding vertical scroll took the gesture: leave it alone.
                            if (change.isConsumed ||
                                abs(change.position.y - down.position.y) > viewConfiguration.touchSlop * 1.5f
                            ) break
                            if (abs(change.position.x - down.position.x) > viewConfiguration.touchSlop ||
                                System.currentTimeMillis() - downTime > 300
                            ) {
                                dragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                        if (dragging) {
                            change.consume()
                            dragX = change.position.x
                            val count = latestOptions.size.coerceAtLeast(1)
                            val segmentPx = (size.width - trackPaddingPx * 2) / count
                            val hovered = ((change.position.x - trackPaddingPx) / segmentPx).toInt().coerceIn(0, count - 1)
                            val option = latestOptions[hovered]
                            if (option != latestSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnSelect(option)
                            }
                        }
                    }
                    dragX = null
                }
            }
    ) {
        val count = options.size.coerceAtLeast(1)
        val segmentWidth = (maxWidth - trackPadding * 2) / count
        val index = options.indexOf(selected).coerceAtLeast(0)
        val segmentWidthPx = with(density) { segmentWidth.toPx() }
        val fingerOffset = dragX?.let {
            with(density) { (it - trackPaddingPx - segmentWidthPx / 2f).coerceIn(0f, segmentWidthPx * (count - 1)).toDp() }
        }
        val thumbOffset by animateDpAsState(
            targetValue = fingerOffset ?: (segmentWidth * index),
            animationSpec = when {
                fingerOffset != null -> snap()
                language == DesignLanguage.IOS_LIQUID_GLASS -> spring(dampingRatio = 0.55f, stiffness = 480f)
                language == DesignLanguage.XIAOMI_HYPEROS -> tween(160)
                else -> tween(260, easing = FastOutSlowInEasing)
            },
            label = "segment_thumb"
        )
        val thumbScale by animateFloatAsState(
            targetValue = if (dragX != null) (if (language == DesignLanguage.IOS_LIQUID_GLASS) 1.18f else 1.04f) else 1f,
            animationSpec = if (language == DesignLanguage.IOS_LIQUID_GLASS) spring(dampingRatio = 0.42f, stiffness = 500f) else spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "segment_thumb_scale"
        )

        Box(
            modifier = Modifier
                .padding(trackPadding)
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .then(
                    when (language) {
                        DesignLanguage.SAMSUNG_ONEUI -> Modifier
                            .clip(thumbShape)
                            .background(if (palette.isLight) palette.surface else Color.White.copy(alpha = 0.16f))
                        DesignLanguage.IOS_LIQUID_GLASS -> if (palette.isLight) {
                            Modifier
                                .shadow(3.dp, thumbShape)
                                .clip(thumbShape)
                                .background(Color.White)
                        } else {
                            Modifier
                                .clip(thumbShape)
                                .background(Color.White.copy(alpha = 0.20f))
                                .glassRim(thumbShape, GlassRim.Liquid, light = false)
                        }
                        // Gemini: the chosen segment darker than the track, as Prism Camera's pills.
                        DesignLanguage.GOOGLE_EXPRESSIVE -> Modifier
                            .clip(thumbShape)
                            .background(geminiSelectionFill(palette.isLight))
                            .border(0.8.dp, geminiSelectionEdge(palette.isLight), thumbShape)
                        else -> Modifier
                            .shadow(2.dp, thumbShape)
                            .clip(thumbShape)
                            .background(palette.surface)
                    }
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(trackPadding)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()                        .clip(thumbShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(option),
                        color = when {
                            isSelected -> palette.onBackground
                            language == DesignLanguage.IOS_LIQUID_GLASS -> palette.onBackground.copy(alpha = 0.8f)
                            else -> palette.textMuted
                        },
                        fontSize = if (language == DesignLanguage.IOS_LIQUID_GLASS) 13.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lists & cards
// ---------------------------------------------------------------------------------------------

data class ListRow(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    /** Brand color of this row's icon (filled square on iOS, tonal cookie on Material, flat tint elsewhere). */
    val iconColor: Color = Color.Unspecified,
    val value: String? = null,
    val showChevron: Boolean = true,
    val onClick: (() -> Unit)? = null,
    /** Overrides the title color, e.g. [com.example.amoledgallery.ui.theme.destructive] for delete rows. */
    val titleColor: Color = Color.Unspecified
)

@Composable
fun ThemedSectionHeader(text: String, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    when (theme.language) {
        DesignLanguage.SAMSUNG_ONEUI -> Text(
            text = text,
            color = palette.textMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = modifier.padding(start = 24.dp, bottom = 8.dp, top = 4.dp)
        )
        DesignLanguage.GOOGLE_EXPRESSIVE -> Text(
            text = text,
            color = palette.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier.padding(start = 16.dp, bottom = 10.dp, top = 4.dp)
        )
        DesignLanguage.IOS_LIQUID_GLASS -> Text(
            text = text.uppercase(),
            color = palette.textMuted,
            fontSize = 13.sp,
            letterSpacing = 0.2.sp,
            modifier = modifier.padding(start = 20.dp, bottom = 6.dp, top = 4.dp)
        )
        DesignLanguage.XIAOMI_HYPEROS -> Text(
            text = text,
            color = palette.textMuted,
            fontSize = 13.sp,
            modifier = modifier.padding(start = 20.dp, bottom = 8.dp, top = 4.dp)
        )
    }
}

/** The grouped container every OS uses for its settings-style lists. */
@Composable
fun ThemedListSection(
    rows: List<ListRow>,
    modifier: Modifier = Modifier,
    header: String? = null,
    /** Use one step lighter containers when the list sits on a sheet or dialog surface. */
    inSheet: Boolean = false
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language

    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            // On a page the headers follow the design's own menu; on sheets the compact form.
            when {
                inSheet -> ThemedSectionHeader(header)
                language == DesignLanguage.XIAOMI_HYPEROS ->
                    HyperOsSectionLabel(header, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp))
                language == DesignLanguage.GOOGLE_EXPRESSIVE ->
                    Text(header, color = palette.textMuted, fontSize = 17.sp, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp))
                else -> ThemedSectionHeader(header)
            }
        }

        when {
            // Page lists look like the design's menu: Gemini's drawer rows and HyperOS's rows sit on the
            // background without a card.
            !inSheet && (language == DesignLanguage.GOOGLE_EXPRESSIVE || language == DesignLanguage.XIAOMI_HYPEROS) ->
                Column { rows.forEach { ListRowContent(it, page = true) } }

            language == DesignLanguage.GOOGLE_EXPRESSIVE -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.forEachIndexed { index, row ->
                    val first = index == 0
                    val last = index == rows.lastIndex
                    val shape = RoundedCornerShape(
                        topStart = if (first) 24.dp else 6.dp,
                        topEnd = if (first) 24.dp else 6.dp,
                        bottomStart = if (last) 24.dp else 6.dp,
                        bottomEnd = if (last) 24.dp else 6.dp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                if (inSheet) lerp(palette.surfaceVariant, palette.accent, 0.04f)
                                else lerp(palette.surface, palette.accent, 0.03f)
                            )
                    ) {
                        ListRowContent(row)
                    }
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(if (!inSheet && language == DesignLanguage.SAMSUNG_ONEUI) 26.dp else theme.shapes.cardCornerRadius))
                    .background(if (inSheet) palette.surfaceVariant else palette.surface)
                    .padding(vertical = if (language == DesignLanguage.XIAOMI_HYPEROS) 6.dp else 0.dp)
            ) {
                rows.forEachIndexed { index, row ->
                    if (index > 0 && language != DesignLanguage.XIAOMI_HYPEROS) {
                        val inset = when (language) {
                            DesignLanguage.IOS_LIQUID_GLASS -> if (row.icon != null) 60.dp else 16.dp
                            else -> if (row.icon != null) (if (inSheet) 64.dp else 78.dp) else 22.dp
                        }
                        Box(
                            modifier = Modifier
                                .padding(start = inset, end = if (!inSheet && language == DesignLanguage.SAMSUNG_ONEUI) 20.dp else 0.dp)
                                .fillMaxWidth()
                                .height(if (language == DesignLanguage.IOS_LIQUID_GLASS) 0.5.dp else 1.dp)
                                .background(
                                    if (language == DesignLanguage.IOS_LIQUID_GLASS) palette.cardBorder
                                    else palette.onBackground.copy(alpha = 0.07f)
                                )
                        )
                    }
                    ListRowContent(row, page = !inSheet)
                }
            }
        }
    }
}

@Composable
private fun ListRowContent(row: ListRow, page: Boolean = false) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language
    val iconColor = if (row.iconColor == Color.Unspecified) palette.accent else row.iconColor
    if (page && language != DesignLanguage.IOS_LIQUID_GLASS) {
        PageRowContent(row, iconColor)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier)
            .heightIn(min = if (language == DesignLanguage.IOS_LIQUID_GLASS) 52.dp else 60.dp)
            .padding(
                start = if (language == DesignLanguage.IOS_LIQUID_GLASS) 16.dp else if (language == DesignLanguage.SAMSUNG_ONEUI) 22.dp else 18.dp,
                end = 16.dp,
                top = if (language == DesignLanguage.IOS_LIQUID_GLASS) 9.dp else 14.dp,
                bottom = if (language == DesignLanguage.IOS_LIQUID_GLASS) 9.dp else 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.icon != null) {
            when (language) {
                DesignLanguage.IOS_LIQUID_GLASS -> {
                    // iOS Settings with tinted icons: every square takes the accent colour. Rows with their own
                    // title colour (destructive actions) keep their icon colour.
                    val ownColor = row.titleColor != Color.Unspecified
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (ownColor) iconColor else palette.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(row.icon, null, tint = if (ownColor) Color.White else palette.onAccent, modifier = Modifier.size(19.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                }
                // Gemini lists: a plain outlined icon in the text colour.
                DesignLanguage.GOOGLE_EXPRESSIVE -> {
                    Icon(row.icon, null, tint = if (row.titleColor == Color.Unspecified) palette.onBackground else row.titleColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(20.dp))
                }
                DesignLanguage.SAMSUNG_ONEUI -> {
                    Icon(row.icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                }
                DesignLanguage.XIAOMI_HYPEROS -> {
                    Icon(row.icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                color = if (row.titleColor == Color.Unspecified) palette.onBackground else row.titleColor,
                fontSize = when (language) {
                    DesignLanguage.IOS_LIQUID_GLASS, DesignLanguage.SAMSUNG_ONEUI -> 17.sp
                    else -> 16.sp
                },
                fontWeight = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = row.subtitle,
                    color = if (language == DesignLanguage.SAMSUNG_ONEUI) palette.accent else palette.textMuted,
                    fontSize = if (language == DesignLanguage.IOS_LIQUID_GLASS) 13.sp else 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (row.value != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = row.value,
                color = palette.textMuted,
                fontSize = if (language == DesignLanguage.IOS_LIQUID_GLASS) 17.sp else 14.sp
            )
        }

        if (row.showChevron && row.onClick != null &&
            (language == DesignLanguage.IOS_LIQUID_GLASS || language == DesignLanguage.XIAOMI_HYPEROS)
        ) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.textMuted.copy(alpha = if (language == DesignLanguage.XIAOMI_HYPEROS) 0.55f else 0.75f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * A list row on a page, drawn like the rows of the design's own menu: One UI's coloured circle with the
 * subtitle in the accent colour, HyperOS's coloured rounded square with a chevron, Gemini's plain icon in a
 * drawer row.
 */
@Composable
private fun PageRowContent(row: ListRow, iconColor: Color) {
    val palette = LocalAppTheme.current.palette
    val language = LocalAppTheme.current.language
    val titleColor = if (row.titleColor == Color.Unspecified) palette.onBackground else row.titleColor
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (language == DesignLanguage.GOOGLE_EXPRESSIVE) Modifier.clip(CircleShape) else Modifier)
            .then(
                if (row.onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = ripple(color = palette.onBackground), onClick = row.onClick)
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = if (language == DesignLanguage.XIAOMI_HYPEROS) 12.dp else 20.dp,
                vertical = when (language) {
                    DesignLanguage.XIAOMI_HYPEROS -> 11.dp
                    DesignLanguage.GOOGLE_EXPRESSIVE -> if (row.subtitle != null) 12.dp else 16.dp
                    else -> 16.dp
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.icon != null) {
            when (language) {
                DesignLanguage.SAMSUNG_ONEUI -> {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(iconColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(row.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(18.dp))
                }
                DesignLanguage.XIAOMI_HYPEROS -> {
                    Box(
                        modifier = Modifier
                            .pressScale(interaction, 0.88f)
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(row.icon, null, tint = contentOn(iconColor), modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                }
                else -> {
                    Icon(row.icon, null, tint = titleColor, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(24.dp))
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.title,
                color = titleColor,
                fontSize = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 20.sp else 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.subtitle != null) {
                Text(
                    row.subtitle,
                    color = if (language == DesignLanguage.SAMSUNG_ONEUI) palette.accent else palette.textMuted,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (row.value != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(row.value, color = palette.textMuted, fontSize = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 16.sp else 15.sp, maxLines = 1)
        }
        if (row.showChevron && row.onClick != null && language == DesignLanguage.XIAOMI_HYPEROS) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.textMuted.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
        }
    }
}

/** Plain grouped container in the language's card style, for custom content. */
@Composable
fun ThemedCard(
    modifier: Modifier = Modifier,
    inSheet: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val shape = RoundedCornerShape(
        if (theme.language == DesignLanguage.GOOGLE_EXPRESSIVE) 24.dp else theme.shapes.cardCornerRadius
    )
    val base = if (inSheet) palette.surfaceVariant else palette.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (theme.language == DesignLanguage.GOOGLE_EXPRESSIVE) lerp(base, palette.accent, 0.04f) else base
            )
            .padding(
                when (theme.language) {
                    DesignLanguage.IOS_LIQUID_GLASS -> 16.dp
                    DesignLanguage.SAMSUNG_ONEUI -> 22.dp
                    else -> 20.dp
                }
            ),
        content = content
    )
}

// ---------------------------------------------------------------------------------------------
// Buttons, fields, indicators
// ---------------------------------------------------------------------------------------------

/**
 * Full-size action button. Material 3 Expressive buttons morph from a pill to a rounded square while
 * pressed; iOS capsules squish; One UI / HyperOS stay calm.
 */
@Composable
fun ThemedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: DialogButtonStyle = DialogButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language
    val interaction = remember { MutableInteractionSource() }

    val filled = style == DialogButtonStyle.Primary
    val container = when {
        filled -> palette.accent
        language == DesignLanguage.IOS_LIQUID_GLASS -> palette.onBackground.copy(alpha = if (palette.isLight) 0.07f else 0.14f)
        else -> palette.surfaceVariant
    }
    val content = when (style) {
        DialogButtonStyle.Primary -> palette.onAccent
        DialogButtonStyle.Destructive -> palette.destructive
        else -> palette.onBackground
    }

    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when (language) {
            DesignLanguage.GOOGLE_EXPRESSIVE -> if (pressed) 12.dp else 26.dp
            DesignLanguage.XIAOMI_HYPEROS -> 16.dp
            else -> 26.dp
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "button_corner"
    )
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .then(if (language == DesignLanguage.IOS_LIQUID_GLASS) Modifier.pressScale(interaction, 0.96f) else Modifier)
            .height(if (language == DesignLanguage.IOS_LIQUID_GLASS) 50.dp else 52.dp)
            .clip(shape)
            .background(if (enabled) container else container.copy(alpha = container.alpha * 0.4f))
            .clickable(
                interactionSource = interaction,
                indication = if (language == DesignLanguage.IOS_LIQUID_GLASS) null else ripple(),
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (enabled) content else content.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) content else content.copy(alpha = 0.5f),
                fontSize = if (language == DesignLanguage.IOS_LIQUID_GLASS) 17.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ThemedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    /**
     * Above 1 a long value wraps onto further lines instead of scrolling sideways, so every position can
     * be reached by tapping or dragging the cursor handle. Line breaks are never entered.
     */
    maxLines: Int = 1
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(250)
            runCatching { focusRequester.requestFocus() }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = { onValueChange(if (maxLines > 1) it.replace("\n", "") else it) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
        textStyle = TextStyle(color = palette.onBackground, fontSize = 17.sp),
        cursorBrush = SolidColor(palette.accent),
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox = { inner ->
            val placeholderText: @Composable () -> Unit = {
                if (value.isEmpty()) Text(placeholder, color = palette.textMuted, fontSize = 17.sp, maxLines = 1)
            }
            when (language) {
                DesignLanguage.SAMSUNG_ONEUI -> Column {
                    Box(modifier = Modifier.padding(vertical = 10.dp)) {
                        placeholderText()
                        inner()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (focused) 2.dp else 1.dp)
                            .background(if (focused) palette.accent else palette.textMuted.copy(alpha = 0.5f))
                    )
                }

                DesignLanguage.GOOGLE_EXPRESSIVE -> Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(lerp(palette.surfaceVariant, palette.accent, 0.05f))
                ) {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)) {
                        Text(
                            text = placeholder,
                            color = if (focused) palette.accent else palette.textMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        inner()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (focused) 2.dp else 1.dp)
                            .background(if (focused) palette.accent else palette.textMuted)
                    )
                }

                DesignLanguage.IOS_LIQUID_GLASS -> Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.onBackground.copy(alpha = if (palette.isLight) 0.06f else 0.10f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    placeholderText()
                    inner()
                }

                DesignLanguage.XIAOMI_HYPEROS -> Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceVariant)
                        .then(if (focused) Modifier.border(1.5.dp, palette.accent, RoundedCornerShape(14.dp)) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    placeholderText()
                    inner()
                }
            }
        }
    )
}

/** Selection indicator for single-choice lists. */
@Composable
fun ThemedRadioIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    when (theme.language) {
        DesignLanguage.IOS_LIQUID_GLASS -> Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        }

        DesignLanguage.XIAOMI_HYPEROS -> Box(
            modifier = modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) palette.accent else Color.Transparent)
                .then(if (!selected) Modifier.border(1.5.dp, palette.textMuted.copy(alpha = 0.5f), CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(15.dp))
        }

        else -> {
            val dot by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = if (theme.language == DesignLanguage.GOOGLE_EXPRESSIVE) {
                    spring(dampingRatio = 0.45f, stiffness = 600f)
                } else {
                    tween(180)
                },
                label = "radio_dot"
            )
            Box(
                modifier = modifier
                    .size(22.dp)
                    .border(2.dp, if (selected) palette.accent else palette.textMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .graphicsLayer {
                            scaleX = dot
                            scaleY = dot
                        }
                        .clip(CircleShape)
                        .background(palette.accent)
                )
            }
        }
    }
}

/** Toolbar icon button: plain (One UI, HyperOS), tonal circle (Material), glass circle (iOS). */
@Composable
fun ThemedIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    hazeState: HazeState? = LocalContentHaze.current
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val interaction = remember { MutableInteractionSource() }
    val resolvedTint = (if (tint == Color.Unspecified) palette.onBackground else tint)
        .let { if (enabled) it else it.copy(alpha = 0.35f) }

    // Tap pulse: a quick squeeze and springy bounce on every click, so even a fast tap is clearly
    // acknowledged (a pure press-scale only shows while the finger is held down).
    val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()
    val pulseDepth = when (theme.language) {
        DesignLanguage.GOOGLE_EXPRESSIVE -> 0.78f
        DesignLanguage.IOS_LIQUID_GLASS -> 0.86f
        else -> 0.82f
    }
    val pulsingClick: () -> Unit = {
        scope.launch {
            pulse.animateTo(pulseDepth, tween(70))
            pulse.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 600f))
        }
        onClick()
    }
    val pulseModifier = Modifier.graphicsLayer {
        scaleX = pulse.value
        scaleY = pulse.value
    }

    when (theme.language) {
        // The Liquid Glass itself swells, bounces and lights up on touch.
        DesignLanguage.IOS_LIQUID_GLASS -> Box(
            modifier = modifier
                .size(44.dp)
                .glass(CircleShape, barGlass(), hazeState)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, tint = resolvedTint, modifier = Modifier.size(21.dp))
        }

        DesignLanguage.GOOGLE_EXPRESSIVE -> Box(
            modifier = modifier
                .then(pulseModifier)
                .pressScale(interaction, 0.9f)
                .size(48.dp)
                .geminiChip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = pulsingClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, tint = resolvedTint, modifier = Modifier.size(22.dp))
        }

        else -> Box(
            modifier = modifier
                .then(pulseModifier)
                .size(if (theme.language == DesignLanguage.SAMSUNG_ONEUI) 48.dp else 44.dp)                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = pulsingClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(if (theme.language == DesignLanguage.SAMSUNG_ONEUI) 25.dp else 24.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

/**
 * Compact top bar for sub-screens and scrolled states. Its background blurs [hazeState] (if given) and
 * fades with [backgroundAlpha] so it can appear only once content scrolls underneath:
 * - One UI: black frosted bar, back arrow, start-aligned title.
 * - Material: tonal bar, tonal circle back button, larger start-aligned title.
 * - iOS: soft-edged progressive glass, glass circle chevron, centered title, glass circle actions.
 * - HyperOS: light frosted bar, plain arrow, start-aligned title.
 */
@Composable
fun ThemedTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    hazeState: HazeState? = null,
    backgroundAlpha: () -> Float = { 1f },
    /** Replaces the back arrow, e.g. with a close icon for selection mode. */
    navigationIcon: ImageVector? = null,
    /**
     * Optional second row of compact actions under the title, for screens whose actions would otherwise
     * leave too little room for the title (e.g. the trash on iOS / Material).
     */
    secondaryActions: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language

    Box(modifier = modifier.fillMaxWidth()) {
        val background = when (language) {
            DesignLanguage.SAMSUNG_ONEUI -> Modifier.glass(
                RectangleShape,
                GlassSpec(palette.background.copy(alpha = 0.72f), palette.background.copy(alpha = 0.97f), 30.dp, GlassRim.None),
                hazeState
            )
            // Fades out below the bar (see the extended background box) instead of ending in a hard edge
            // across Material's tonal background glow.
            DesignLanguage.GOOGLE_EXPRESSIVE -> Modifier.glass(
                RectangleShape,
                GlassSpec(
                    lerp(palette.background, palette.accent, 0.06f).copy(alpha = 0.70f),
                    lerp(palette.background, palette.accent, 0.06f).copy(alpha = 0.97f),
                    28.dp,
                    GlassRim.None
                ),
                hazeState,
                // Solid behind the bar itself (title and subtitle stay legible), fading only in the part
                // that extends below it.
                mask = Brush.verticalGradient(0f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent)
            )
            DesignLanguage.IOS_LIQUID_GLASS -> Modifier.glass(
                RectangleShape,
                if (palette.isLight) {
                    GlassSpec(Color.White.copy(alpha = 0.55f), palette.background.copy(alpha = 0.96f), 18.dp, GlassRim.None)
                } else {
                    GlassSpec(Color.Black.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.92f), 18.dp, GlassRim.None)
                },
                hazeState,
                mask = Brush.verticalGradient(0f to Color.Black, 0.75f to Color.Black, 1f to Color.Transparent)
            )
            DesignLanguage.XIAOMI_HYPEROS -> Modifier.glass(
                RectangleShape,
                GlassSpec(palette.background.copy(alpha = 0.96f), palette.background.copy(alpha = 0.98f), 26.dp, GlassRim.None),
                hazeState
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (language == DesignLanguage.GOOGLE_EXPRESSIVE) {
                        // Drawn taller than the bar, so its fade-out runs over the content below the bar.
                        Modifier.layout { measurable, constraints ->
                            val height = constraints.maxHeight + 28.dp.roundToPx()
                            val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                        }
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer { alpha = backgroundAlpha() }
                .then(background)
        )

        val barHeight = when (language) {
            DesignLanguage.SAMSUNG_ONEUI, DesignLanguage.GOOGLE_EXPRESSIVE -> 64.dp
            DesignLanguage.IOS_LIQUID_GLASS -> 60.dp
            DesignLanguage.XIAOMI_HYPEROS -> 56.dp
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // HyperOS centres plain titles too ("Favorites", "Detail info").
            if (language == DesignLanguage.IOS_LIQUID_GLASS ||
                (language == DesignLanguage.XIAOMI_HYPEROS && subtitle == null && navigationIcon == null)
            ) {
                // iOS centres the title on the screen, not in the space left between the controls. The title
                // gets the width that stays free when the wider side is mirrored onto the other side, so long
                // titles and wide action groups shrink it instead of colliding with it.
                Layout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(barHeight)
                        .padding(horizontal = 16.dp),
                    content = {
                        Box {
                            if (onBack != null) {
                                ThemedIconButton(
                                    icon = navigationIcon ?: if (language == DesignLanguage.IOS_LIQUID_GLASS) Icons.AutoMirrored.Rounded.ArrowBackIos else Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = strings.back,
                                    onClick = onBack,
                                    hazeState = hazeState
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                title,
                                color = palette.onBackground,
                                fontSize = if (language == DesignLanguage.XIAOMI_HYPEROS) 21.sp else 17.sp,
                                fontWeight = if (language == DesignLanguage.XIAOMI_HYPEROS) FontWeight.Medium else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle != null) {
                                Text(subtitle, color = palette.textMuted, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions
                        )
                    }
                ) { measurables, constraints ->
                    val loose = constraints.copy(minWidth = 0, minHeight = 0)
                    val start = measurables[0].measure(loose)
                    val end = measurables[2].measure(loose)
                    val gap = 10.dp.roundToPx()
                    val side = maxOf(start.width, end.width)
                    val centredWidth = (constraints.maxWidth - 2 * (side + if (side > 0) gap else 0)).coerceAtLeast(0)
                    // A title that does not fit centred (a wide action group on one side only) may also use the
                    // free space on the other side, and moves off-centre only as far as it has to.
                    val left = start.width + if (start.width > 0) gap else 0
                    val right = constraints.maxWidth - end.width - if (end.width > 0) gap else 0
                    val titleWidth = if (measurables[1].maxIntrinsicWidth(constraints.maxHeight) > centredWidth) {
                        (right - left).coerceAtLeast(0)
                    } else {
                        centredWidth
                    }
                    val titlePlaceable = measurables[1].measure(loose.copy(maxWidth = titleWidth))
                    val height = constraints.maxHeight
                    layout(constraints.maxWidth, height) {
                        start.place(0, (height - start.height) / 2)
                        end.place(constraints.maxWidth - end.width, (height - end.height) / 2)
                        val titleX = ((constraints.maxWidth - titlePlaceable.width) / 2)
                            .coerceAtMost(right - titlePlaceable.width)
                            .coerceAtLeast(left)
                        titlePlaceable.place(titleX, (height - titlePlaceable.height) / 2)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(barHeight)
                        .padding(horizontal = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 12.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        ThemedIconButton(
                            icon = navigationIcon ?: Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.back,
                            onClick = onBack,
                            hazeState = hazeState
                        )
                        Spacer(modifier = Modifier.width(if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 12.dp else 4.dp))
                    } else {
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = palette.onBackground,
                            fontSize = when (language) {
                                DesignLanguage.GOOGLE_EXPRESSIVE -> 22.sp
                                DesignLanguage.SAMSUNG_ONEUI -> 20.sp
                                else -> 18.sp
                            },
                            fontWeight = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) FontWeight.Normal else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Text(subtitle, color = palette.textMuted, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (language == DesignLanguage.GOOGLE_EXPRESSIVE) 8.dp else 0.dp),
                        content = actions
                    )
                }
            }

            if (secondaryActions != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = secondaryActions
                )
            }
        }
    }
}

/** Compact pill action for a top bar's secondary row: tonal on Material, glass capsule on iOS. */
@Composable
fun ThemedChipAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    hazeState: HazeState? = null
) {
    val theme = LocalAppTheme.current
    val interaction = remember { MutableInteractionSource() }
    val shape = CircleShape
    Box(
        modifier = Modifier
            .pressScale(interaction, 0.93f)
            .height(34.dp)
            .then(
                if (theme.language == DesignLanguage.IOS_LIQUID_GLASS) Modifier.glass(shape, barGlass(), hazeState)
                else Modifier.clip(shape).background(color.copy(alpha = 0.16f))
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Material 3 Expressive "cookie" shape: a circle with nine soft scallops. */
val ExpressiveCookieShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = min(cx, cy)
    val lobes = 9
    val depth = 0.07f
    val steps = 108
    for (i in 0..steps) {
        val t = i.toFloat() / steps * 2f * PI.toFloat()
        val r = radius * (1f - depth + depth * cos(lobes * t))
        val x = cx + r * cos(t)
        val y = cy + r * sin(t)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Centred empty state (no favorites, empty trash...). */
@Composable
fun ThemedEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (theme.language) {
            // Gemini: the page's icon filled with the Gemini gradient (a heart for favourites, a bin for the trash).
            DesignLanguage.GOOGLE_EXPRESSIVE -> GeminiIcon(
                when (icon) {
                    Icons.Rounded.FavoriteBorder -> Icons.Rounded.Favorite
                    Icons.Rounded.DeleteOutline -> Icons.Rounded.Delete
                    else -> icon
                },
                Modifier.size(84.dp)
            )
            DesignLanguage.XIAOMI_HYPEROS -> Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = palette.textMuted, modifier = Modifier.size(42.dp))
            }
            else -> Icon(icon, null, tint = palette.textMuted, modifier = Modifier.size(64.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            color = palette.onBackground,
            fontSize = if (theme.language == DesignLanguage.IOS_LIQUID_GLASS) 22.sp else 20.sp,
            fontWeight = if (theme.language == DesignLanguage.IOS_LIQUID_GLASS) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = palette.textMuted,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Text used on small badges drawn straight on top of photos. */
val PhotoBadgeTextStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 6f)
)
