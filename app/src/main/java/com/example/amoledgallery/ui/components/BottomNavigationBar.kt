package com.example.amoledgallery.ui.components
import com.example.amoledgallery.ui.i18n.strings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.amoledgallery.ui.design.GlassRim
import com.example.amoledgallery.ui.design.glass
import com.example.amoledgallery.ui.design.geminiGlass
import com.example.amoledgallery.ui.design.glassRim
import com.example.amoledgallery.ui.design.pressScale
import com.example.amoledgallery.ui.design.selectionPop
import com.example.amoledgallery.ui.design.SamsungGalleryIcons
import androidx.compose.animation.core.snap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.destructive
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent
import com.example.amoledgallery.ui.viewmodel.GalleryTab
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HOLD_TO_SLIDE_TIMEOUT_MS = 250L

private data class NavTab(
    val tab: GalleryTab,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    val label: String
        get() = when (tab) {
            GalleryTab.BILDER -> strings.tabPictures
            GalleryTab.ALBEN -> strings.tabAlbums
            GalleryTab.MENU -> strings.tabMenu
        }
}

/** One UI uses Samsung Gallery's own album and menu glyphs. */
private fun NavTab.oneUiIcon(selected: Boolean): ImageVector = when (tab) {
    GalleryTab.ALBEN -> if (selected) SamsungGalleryIcons.AlbumsSelected else SamsungGalleryIcons.Albums
    GalleryTab.MENU -> if (selected) SamsungGalleryIcons.MenuSelected else SamsungGalleryIcons.Menu
    else -> if (selected) selectedIcon else icon
}

private val NAV_TABS = listOf(
    NavTab(GalleryTab.BILDER, Icons.Rounded.Image, Icons.Outlined.Image),
    NavTab(GalleryTab.ALBEN, Icons.Rounded.Collections, Icons.Outlined.Collections),
    NavTab(GalleryTab.MENU, Icons.Rounded.Menu, Icons.Rounded.Menu)
)

/**
 * Bottom navigation whose whole *structure* changes per [DesignLanguage]:
 * - One UI (Samsung Gallery): a floating frosted pill spanning the width, icon above label, with a
 *   lighter capsule sliding behind the active tab.
 * - Material 3 Expressive: an attached tonal bar; the active indicator springs open around the icon.
 * - iOS 26 Photos: a Liquid Glass capsule for the content tabs with a selection bubble that stretches as
 *   it glides, plus a detached glass circle. The glass swells slightly while touched.
 * - HyperOS: a small frosted icon-only pill centred above the gesture bar.
 *
 * Holding a tab for [HOLD_TO_SLIDE_TIMEOUT_MS] and dragging slides the selection across tabs; the
 * highlight follows the finger and the tab under it is selected on release.
 */
@Composable
fun BottomNavigationBar(
    currentTab: GalleryTab,
    onTabSelected: (GalleryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val haptic = LocalHapticFeedback.current

    // Root-space horizontal centre of each tab, reported by the tabs themselves.
    val tabCenters = remember { mutableStateMapOf<Int, Float>() }
    var gestureRootX by remember { mutableFloatStateOf(0f) }
    var slideRootX by remember { mutableStateOf<Float?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val latestTab by rememberUpdatedState(currentTab)
    val latestOnTabSelected by rememberUpdatedState(onTabSelected)

    fun indexForRootX(rootX: Float): Int =
        tabCenters.entries.minByOrNull { abs(it.value - rootX) }?.key
            ?: NAV_TABS.indexOfFirst { it.tab == latestTab }.coerceAtLeast(0)

    val highlighted = slideRootX?.let { indexForRootX(it) }
        ?: NAV_TABS.indexOfFirst { it.tab == currentTab }.coerceAtLeast(0)

    val holdSlideModifier = Modifier
        .onGloballyPositioned { gestureRootX = it.positionInRoot().x }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                val downTime = System.currentTimeMillis()
                var slideActive = false
                var hoveredIndex = -1
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        // Let go on the menu: only now does it open.
                        if (change != null && slideActive && hoveredIndex != -1 && NAV_TABS[hoveredIndex].tab == GalleryTab.MENU) {
                            latestOnTabSelected(GalleryTab.MENU)
                        }
                        slideRootX = null
                        pressed = false
                        break
                    }
                    // Sliding starts after a short hold, or as soon as the finger clearly moves sideways.
                    if (!slideActive && (System.currentTimeMillis() - downTime >= HOLD_TO_SLIDE_TIMEOUT_MS ||
                            abs(change.position.x - down.position.x) > viewConfiguration.touchSlop)
                    ) {
                        slideActive = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (slideActive) {
                        val rootX = gestureRootX + change.position.x
                        slideRootX = rootX
                        change.consume()
                        // Live switching: a hovered content tab opens immediately, without waiting for release.
                        // The menu is a page of its own rather than a view of the library, so sliding across it
                        // only highlights it; see the release above.
                        val index = indexForRootX(rootX)
                        if (index != hoveredIndex) {
                            if (hoveredIndex != -1) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            hoveredIndex = index
                            val hovered = NAV_TABS[index].tab
                            if (hovered != GalleryTab.MENU && hovered != latestTab) latestOnTabSelected(hovered)
                        }
                    }
                }
            }
        }

    fun reportCenter(index: Int): Modifier = Modifier.onGloballyPositioned {
        val bounds = it.boundsInRoot()
        tabCenters[index] = bounds.left + bounds.width / 2f
    }

    val palette = theme.palette

    when (theme.language) {
        DesignLanguage.SAMSUNG_ONEUI -> Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
        ) {
            val barScale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "oneui_bar_press")
            val pill = RoundedCornerShape(percent = 50)
            // Proportions measured from Samsung Gallery: a slim pill whose active capsule nearly fills it.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .graphicsLayer {
                        scaleX = barScale
                        scaleY = barScale
                    }
                    .glass(pill)
                    .then(holdSlideModifier)
            ) {
                val inset = 4.dp
                val itemWidth = (maxWidth - inset * 2) / NAV_TABS.size
                // While sliding, the capsule sits exactly under the finger; afterwards it glides to the tab.
                val fingerOffset = slideRootX?.let { rootX ->
                    with(LocalDensity.current) { (rootX - gestureRootX).toDp() } - inset - itemWidth / 2
                }?.coerceIn(0.dp, itemWidth * (NAV_TABS.size - 1))
                val indicatorX by animateDpAsState(
                    targetValue = fingerOffset ?: (itemWidth * highlighted),
                    animationSpec = if (fingerOffset != null) snap() else tween(280, easing = FastOutSlowInEasing),
                    label = "oneui_indicator"
                )
                Box(
                    modifier = Modifier
                        .padding(inset)
                        .offset(x = indicatorX)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(pill)
                        .background(if (palette.isLight) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.15f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inset)
                ) {
                    NAV_TABS.forEachIndexed { index, tab ->
                        val selected = index == highlighted
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(reportCenter(index))                                .clip(pill)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = palette.onBackground)
                                ) { onTabSelected(tab.tab) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.oneUiIcon(selected),
                                contentDescription = tab.label,
                                tint = palette.onBackground,
                                modifier = Modifier
                                    .size(23.dp)
                                    .selectionPop(selected, fromScale = 0.78f, dampingRatio = 0.5f, stiffness = 650f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                color = palette.onBackground,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Google Photos: a floating dark pill with the content tabs - the active one opens into a lighter pill with
        // its label - and a detached circle for the menu.
        DesignLanguage.GOOGLE_EXPRESSIVE -> Row(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pill = RoundedCornerShape(percent = 50)
            // The open tab: darker than the glass bar, with a hairline edge (as in Prism Camera).
            val activeFill = com.example.amoledgallery.ui.design.geminiSelectionFill(palette.isLight)
            val activeEdge = com.example.amoledgallery.ui.design.geminiSelectionEdge(palette.isLight)
            val barScale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "google_bar_press")
            // How far each tab is open. One spring opens a tab while the other closes, so the bar keeps its width
            // instead of jumping, and the frames only re-lay and redraw the bar: nothing recomposes. The labels stay
            // composed, because adding one in the middle of a switch made the width step before the spring started.
            val contentTabs = 2
            val tabOpen = remember { List(contentTabs) { Animatable(if (it == highlighted) 1f else 0f) } }
            LaunchedEffect(highlighted) {
                tabOpen.forEachIndexed { index, open ->
                    launch { open.animateTo(if (index == highlighted) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 380f)) }
                }
            }
            val tabWidths = remember { FloatArray(contentTabs) }
            val tabGap = 4.dp
            val barPadding = 9.dp
            Row(
                modifier = Modifier
                    .height(70.dp)
                    .graphicsLayer {
                        scaleX = barScale
                        scaleY = barScale
                    }
                    .geminiGlass(pill, tintAlpha = com.example.amoledgallery.ui.design.GEMINI_PILL_TINT, blur = com.example.amoledgallery.ui.design.GeminiPillBlur, vivid = true)
                    .then(holdSlideModifier)
                    // One capsule glides from tab to tab, where two used to fade in and out on their own.
                    .drawBehind {
                        val open0 = tabOpen[0].value.coerceAtLeast(0f)
                        val open1 = tabOpen[1].value.coerceAtLeast(0f)
                        val visible = maxOf(open0, open1).coerceAtMost(1f)
                        if (visible <= 0.001f) return@drawBehind
                        val toSecond = open1 / (open0 + open1)
                        val first = barPadding.toPx()
                        val second = first + tabWidths[0] + tabGap.toPx()
                        val left = first + (second - first) * toSecond
                        val width = tabWidths[0] + (tabWidths[1] - tabWidths[0]) * toSecond
                        val height = 52.dp.toPx()
                        val top = (size.height - height) / 2f
                        val radius = androidx.compose.ui.geometry.CornerRadius(height / 2f)
                        drawRoundRect(activeFill, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), radius, alpha = visible)
                        drawRoundRect(
                            activeEdge,
                            Offset(left, top),
                            androidx.compose.ui.geometry.Size(width, height),
                            radius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(0.8.dp.toPx()),
                            alpha = visible
                        )
                    }
                    .padding(horizontal = barPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tabGap)
            ) {
                NAV_TABS.take(contentTabs).forEachIndexed { index, tab ->
                    val selected = index == highlighted
                    Row(
                        modifier = Modifier
                            .then(reportCenter(index))
                            .height(52.dp)
                            .clip(pill)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = palette.onBackground)
                            ) { onTabSelected(tab.tab) }
                            // Inside the clip, so a closing tab cuts its label off at its current width.
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                                val closed = (18.dp + 26.dp + 18.dp).roundToPx()
                                val open = tabOpen[index].value.coerceIn(0f, 1.1f)
                                val width = (closed + (placeable.width - closed) * open).roundToInt()
                                tabWidths[index] = width.toFloat()
                                layout(width, placeable.height) { placeable.placeRelative(0, 0) }
                            }
                            .padding(start = 18.dp, end = 22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = tab.label,
                            tint = palette.onBackground,
                            modifier = Modifier
                                .size(26.dp)
                                .selectionPop(selected, fromScale = 0.88f, dampingRatio = 0.8f, stiffness = 420f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = tab.label,
                            color = palette.onBackground,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                // Appears once the tab has opened far enough to show it, and leaves first.
                                alpha = ((tabOpen[index].value - 0.45f) / 0.55f).coerceIn(0f, 1f)
                            }
                        )
                    }
                }
            }
            val menuTab = NAV_TABS[2]
            val menuSelected = highlighted == 2
            val menuInteraction = remember { MutableInteractionSource() }
            val menuFill by animateFloatAsState(
                targetValue = if (menuSelected) 1f else 0f,
                animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
                label = "google_menu_fill"
            )
            Box(
                modifier = Modifier
                    .then(reportCenter(2))
                    .pressScale(menuInteraction, 0.92f)
                    .size(70.dp)
                    .geminiGlass(CircleShape, tintAlpha = com.example.amoledgallery.ui.design.GEMINI_PILL_TINT, blur = com.example.amoledgallery.ui.design.GeminiPillBlur, vivid = true)
                    .clickable(interactionSource = menuInteraction, indication = ripple(color = palette.onBackground)) {
                        onTabSelected(menuTab.tab)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer { alpha = menuFill }
                        .clip(CircleShape)
                        .background(activeFill)
                )
                Icon(
                    Icons.Rounded.Settings,
                    menuTab.label,
                    tint = palette.onBackground,
                    modifier = Modifier
                        .size(26.dp)
                        .selectionPop(menuSelected, fromScale = 0.88f, dampingRatio = 0.8f, stiffness = 420f)
                )
            }
        }

        DesignLanguage.IOS_LIQUID_GLASS -> Row(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pill = RoundedCornerShape(percent = 50)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    // Swelling and the fingertip glow come from the Liquid Glass itself.
                    .glass(pill)
                    .then(holdSlideModifier)
            ) {
                val inset = 4.dp
                val contentTabs = 2
                val itemWidth = (maxWidth - inset * 2) / contentTabs
                val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
                val bubbleIndex = highlighted.coerceAtMost(contentTabs - 1)
                val bubbleX = remember { Animatable(bubbleIndex * itemWidthPx) }
                // While sliding, the bubble sits exactly under the finger. A fast spring "follower" trails
                // it only to derive the liquid stretch, which relaxes as soon as the finger stops. After
                // release the bubble springs from the finger position to the selected tab.
                val slideTarget = slideRootX?.let {
                    (it - gestureRootX - itemWidthPx / 2f).coerceIn(0f, itemWidthPx * (contentTabs - 1))
                }
                val follower = remember { Animatable(bubbleIndex * itemWidthPx) }
                val lastFinger = remember { floatArrayOf(Float.NaN) }
                if (slideTarget != null) lastFinger[0] = slideTarget
                LaunchedEffect(slideTarget) {
                    if (slideTarget != null) follower.animateTo(slideTarget, spring(dampingRatio = 1f, stiffness = 700f))
                }
                LaunchedEffect(slideTarget == null, bubbleIndex, itemWidthPx) {
                    if (slideTarget == null) {
                        if (!lastFinger[0].isNaN()) {
                            bubbleX.snapTo(lastFinger[0])
                            lastFinger[0] = Float.NaN
                        }
                        bubbleX.animateTo(bubbleIndex * itemWidthPx, spring(dampingRatio = 0.55f, stiffness = 450f))
                    } else {
                        follower.snapTo(bubbleX.value)
                    }
                }
                val bubbleAlpha by animateFloatAsState(
                    targetValue = if (highlighted < contentTabs) 1f else 0f,
                    label = "ios_bubble_alpha"
                )
                Box(
                    modifier = Modifier
                        .padding(inset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val finger = slideTarget
                            translationX = finger ?: bubbleX.value
                            // Liquid stretch: elongates with speed and settles back round.
                            val stretch = if (finger != null) {
                                (abs(finger - follower.value) / itemWidthPx * 0.45f).coerceAtMost(0.2f)
                            } else {
                                (abs(bubbleX.velocity) / 4500f).coerceAtMost(0.2f)
                            }
                            scaleX = 1f + stretch
                            scaleY = 1f - stretch * 0.3f
                            alpha = bubbleAlpha
                        }
                        .clip(pill)
                        // iOS 26 Photos: the selected tab sits in a darker well of the glass (a light one on light glass).
                        .background(if (palette.isLight) Color.Black.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.42f))
                        .glassRim(pill, GlassRim.Subtle, palette.isLight)
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inset)
                ) {
                    NAV_TABS.take(contentTabs).forEachIndexed { index, tab ->
                        val selected = index == highlighted
                        val tint by animateColorAsState(
                            targetValue = if (selected) palette.accent else palette.onBackground,
                            label = "ios_tab_tint"
                        )
                        // Photos calls its tabs "Mediathek" and "Sammlungen".
                        val label = if (index == 0) strings.tabLibrary else strings.tabCollections
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(reportCenter(index))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTabSelected(tab.tab) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (index == 0) Icons.Rounded.PhotoLibrary else Icons.Rounded.AutoAwesomeMotion,
                                contentDescription = label,
                                tint = tint,
                                modifier = Modifier
                                    .size(26.dp)
                                    .selectionPop(selected, fromScale = 0.84f, dampingRatio = 0.5f, stiffness = 700f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = tint,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            val menuTab = NAV_TABS[2]
            val menuSelected = highlighted == 2
            val menuInteraction = remember { MutableInteractionSource() }
            val menuTint by animateColorAsState(
                targetValue = if (menuSelected) palette.accent else palette.onBackground,
                label = "ios_menu_tint"
            )
            Box(
                modifier = Modifier
                    .then(reportCenter(2))
                    .size(64.dp)
                    .glass(CircleShape)
                    .clickable(interactionSource = menuInteraction, indication = null) { onTabSelected(menuTab.tab) },
                contentAlignment = Alignment.Center
            ) {
                val innerAlpha by animateFloatAsState(if (menuSelected) 1f else 0f, label = "ios_menu_bubble")
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer { alpha = innerAlpha }
                        .clip(CircleShape)
                        .background(if (palette.isLight) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.16f))
                )
                // The detached circle opens the Settings-style menu, so it carries the settings gear.
                Icon(
                    Icons.Rounded.Settings,
                    menuTab.label,
                    tint = menuTint,
                    modifier = Modifier
                        .size(24.dp)
                        .selectionPop(menuSelected, fromScale = 0.8f, dampingRatio = 0.5f, stiffness = 700f)
                )
            }
        }

        // HyperOS Gallery: a solid full-width bar under a hairline, filled icons over their labels. The active
        // tab is bright and bold, the others grey.
        DesignLanguage.XIAOMI_HYPEROS -> Column(
            modifier = modifier
                .fillMaxWidth()
                .background(palette.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.onBackground.copy(alpha = if (palette.isLight) 0.08f else 0.12f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp)
                    .then(holdSlideModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NAV_TABS.forEachIndexed { index, tab ->
                    val selected = index == highlighted
                    val tint by animateColorAsState(
                        targetValue = if (selected) palette.onBackground else palette.textMuted.copy(alpha = 0.75f),
                        animationSpec = tween(150),
                        label = "hyperos_tint"
                    )
                    val tabInteraction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(reportCenter(index))
                            .clickable(
                                interactionSource = tabInteraction,
                                indication = null
                            ) { onTabSelected(tab.tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.selectedIcon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier
                                // HyperOS icons squeeze on touch and snap back quickly.
                                .pressScale(tabInteraction, 0.84f)
                                .selectionPop(selected, fromScale = 0.82f, dampingRatio = 0.6f, stiffness = 800f)
                                .size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            color = tint,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Replaces the tab bar while items are selected, in each OS's own form: One UI's frosted action pill,
 * Material's floating toolbar with a separate FAB, the iOS glass toolbar with a centred count, and
 * HyperOS's full-width frosted action bar.
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** More tools for the selection (slideshow, copy, private album...), in a menu. */
    onMore: (() -> Unit)? = null
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val enabled = selectedCount > 0

    when (theme.language) {
        // One UI 8: a compact floating frosted capsule with labelled actions.
        DesignLanguage.SAMSUNG_ONEUI -> Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 6.dp, bottom = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .height(66.dp)
                    .glass(RoundedCornerShape(percent = 50))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                LabeledAction(Icons.Outlined.Share, strings.send, enabled, palette.onBackground, Modifier.width(92.dp), onShare)
                LabeledAction(Icons.AutoMirrored.Rounded.DriveFileMove, strings.move, enabled, palette.onBackground, Modifier.width(92.dp), onMove)
                LabeledAction(Icons.Outlined.Delete, strings.delete, enabled, palette.onBackground, Modifier.width(92.dp), onDelete)
                if (onMore != null) LabeledAction(Icons.Outlined.MoreVert, strings.moreActions, enabled, palette.onBackground, Modifier.width(80.dp), onMore)
            }
        }

        // Google (Gemini): the actions in a glowing translucent glass capsule.
        DesignLanguage.GOOGLE_EXPRESSIVE -> Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .height(72.dp)
                    .geminiGlass(RoundedCornerShape(percent = 50), tintAlpha = com.example.amoledgallery.ui.design.GEMINI_PILL_TINT, blur = com.example.amoledgallery.ui.design.GeminiPillBlur, vivid = true)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                LabeledAction(Icons.Outlined.Share, strings.share, enabled, palette.onBackground, Modifier.width(88.dp), onShare)
                LabeledAction(Icons.AutoMirrored.Rounded.DriveFileMove, strings.move, enabled, palette.onBackground, Modifier.width(88.dp), onMove)
                LabeledAction(Icons.Outlined.Delete, strings.delete, enabled, palette.onBackground, Modifier.width(88.dp), onDelete)
                if (onMore != null) LabeledAction(Icons.Outlined.MoreVert, strings.moreActions, enabled, palette.onBackground, Modifier.width(76.dp), onMore)
            }
        }

        // iOS 26 Photos: share and trash as glass circles, the selection count in a glass capsule between them
        // ("Objekte auswählen" until something is selected). Moving lives in the ⋯ menu at the top.
        DesignLanguage.IOS_LIQUID_GLASS -> Row(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCircleAction(Icons.Outlined.IosShare, strings.share, enabled, palette.onBackground, onShare)
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .glass(RoundedCornerShape(percent = 50))
                    // iOS: the count capsule opens the further actions.
                    .then(if (onMore != null && enabled) Modifier.clickable(onClick = onMore) else Modifier)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (enabled) strings.showSelection(selectedCount) else strings.selectItems,
                    color = palette.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            GlassCircleAction(Icons.Outlined.Delete, strings.delete, enabled, palette.onBackground, onDelete)
        }

        DesignLanguage.XIAOMI_HYPEROS -> Column(
            modifier = modifier
                .fillMaxWidth()
                .background(palette.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.onBackground.copy(alpha = if (palette.isLight) 0.08f else 0.12f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = palette.onBackground.copy(alpha = 0.85f)
                LabeledAction(Icons.AutoMirrored.Outlined.OpenInNew, strings.send, enabled, tint, Modifier.weight(1f), onShare)
                LabeledAction(Icons.Outlined.AddBox, strings.move, enabled, tint, Modifier.weight(1f), onMove)
                LabeledAction(Icons.Outlined.Delete, strings.delete, enabled, tint, Modifier.weight(1f), onDelete)
                if (onMore != null) LabeledAction(Icons.Outlined.MoreVert, strings.moreActions, enabled, tint, Modifier.weight(1f), onMore)
            }
        }
    }
}

@Composable
private fun LabeledAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.35f)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, color = color, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            label,
            tint = if (enabled) palette.onBackground else palette.onBackground.copy(alpha = 0.35f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun GlassCircleAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(56.dp)
            .glass(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(23.dp))
    }
}

/** One action of a [ThemedActionBar]. */
data class BarAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * The selection bar of [SelectionActionBar] with other actions (restore from the private album, move to the
 * trash...), in the same form for each design. On iOS the first action sits left and the last one right of
 * the count capsule, as share and delete do there.
 */
@Composable
fun ThemedActionBar(selectedCount: Int, actions: List<BarAction>, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val enabled = selectedCount > 0
    when (theme.language) {
        DesignLanguage.SAMSUNG_ONEUI, DesignLanguage.GOOGLE_EXPRESSIVE -> Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = if (theme.language == DesignLanguage.SAMSUNG_ONEUI) 10.dp else 14.dp),
            contentAlignment = Alignment.Center
        ) {
            val shape = RoundedCornerShape(percent = 50)
            Row(
                modifier = Modifier
                    .height(if (theme.language == DesignLanguage.SAMSUNG_ONEUI) 66.dp else 72.dp)
                    .then(
                        if (theme.language == DesignLanguage.SAMSUNG_ONEUI) {
                            Modifier.glass(shape)
                        } else {
                            Modifier.geminiGlass(shape, tintAlpha = com.example.amoledgallery.ui.design.GEMINI_PILL_TINT, blur = com.example.amoledgallery.ui.design.GeminiPillBlur, vivid = true)
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                actions.forEach { action ->
                    LabeledAction(action.icon, action.label, enabled, palette.onBackground, Modifier.width(96.dp), action.onClick)
                }
            }
        }

        DesignLanguage.IOS_LIQUID_GLASS -> Row(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.firstOrNull()?.let { GlassCircleAction(it.icon, it.label, enabled, palette.onBackground, it.onClick) }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .glass(RoundedCornerShape(percent = 50))
                    .padding(horizontal = if (actions.size > 2) 8.dp else 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (actions.size > 2) {
                    actions.subList(1, actions.size - 1).forEach { ToolbarIcon(it.icon, it.label, enabled, it.onClick) }
                } else {
                    Text(
                        text = if (enabled) strings.showSelection(selectedCount) else strings.selectItems,
                        color = palette.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (actions.size > 1) actions.last().let { GlassCircleAction(it.icon, it.label, enabled, palette.onBackground, it.onClick) }
        }

        DesignLanguage.XIAOMI_HYPEROS -> Column(
            modifier = modifier
                .fillMaxWidth()
                .background(palette.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.onBackground.copy(alpha = if (palette.isLight) 0.08f else 0.12f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = palette.onBackground.copy(alpha = 0.85f)
                actions.forEach { LabeledAction(it.icon, it.label, enabled, tint, Modifier.weight(1f), it.onClick) }
            }
        }
    }
}
