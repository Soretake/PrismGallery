// The viewer's bars keep their system-bar padding while Android's bars are hidden, so nothing jumps.
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.amoledgallery.ui.screens
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import com.example.amoledgallery.ui.i18n.LanguageState
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.components.HyperOsDetailInfoPage
import com.example.amoledgallery.ui.design.geminiGlass
import com.example.amoledgallery.ui.design.geminiChip
import com.example.amoledgallery.ui.design.screenBackground
import com.example.amoledgallery.ui.design.blockTouchesBelow
import com.example.amoledgallery.ui.design.pushSpec
import com.example.amoledgallery.ui.design.pushTransition
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.AmoledVideoPlayer
import com.example.amoledgallery.ui.components.MediaInfoContent
import com.example.amoledgallery.ui.components.ZoomableBox
import com.example.amoledgallery.ui.design.DialogButton
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.ThemedDialog
import com.example.amoledgallery.ui.design.ThemedTextField
import com.example.amoledgallery.ui.design.barGlass
import com.example.amoledgallery.ui.design.glass
import com.example.amoledgallery.ui.design.pressScale
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.destructive
import com.example.amoledgallery.ui.theme.isLight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.chrisbanes.haze.HazeState
import com.example.amoledgallery.ui.design.HazeSource
import com.example.amoledgallery.ui.design.OverlayWindow
import com.example.amoledgallery.ui.editor.MediaEditorScreen
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

/** Share of the screen height the details panel takes when it is open underneath the photo. */
private const val INFO_PANEL_FRACTION = 0.52f

/**
 * Fullscreen media viewer: pager, pinch/double-tap zoom, swipe down to close, swipe up for details.
 * The details open like the OS galleries do: the photo moves up and the panel opens underneath it.
 * The chrome follows the design language:
 * - One UI: date/time title and icon-only action row on soft black gradients.
 * - Material 3 Expressive: tonal floating circles on top, a floating toolbar pill at the bottom.
 * - iOS: glass circle buttons, a glass date capsule and a glass toolbar blurring the photo.
 * - HyperOS: white chrome with labelled actions while controls are shown; tapping hides the chrome
 *   and turns the background black.
 *
 * In [trashMode] (opened from the trash) only restore, delete permanently and details are offered.
 * In [viewOnly] mode (a file another app opened with "Open with") only sharing and details are offered.
 */
@Composable
fun MediaDetailScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onTrashItem: (MediaItem) -> Unit,
    onRenameItem: (MediaItem, String) -> Unit,
    isFavorite: (Long) -> Boolean = { false },
    onToggleFavorite: (Long) -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    trashMode: Boolean = false,
    onRestoreItem: (MediaItem) -> Unit = {},
    onDeletePermanently: (MediaItem) -> Unit = {},
    viewOnly: Boolean = false,
    /** Off for files opened from another app: renaming them needs the gallery's write-access flow. */
    allowRename: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (mediaList.isEmpty()) {
        // Closing changes state, which must not happen in the middle of composition.
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { mediaList.size })

    var isZoomed by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(initialIndex) {
        val targetPage = initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    // Pull-down-to-close offset (px) and how far the details panel is open (0..1). Both follow the finger
    // directly and are settled with animate() afterwards.
    var dismissOffset by remember { mutableFloatStateOf(0f) }
    var infoProgress by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        dismissOffset = 0f
        // Report the page back so a delete acts on the item actually on screen and the index is
        // left pointing at the following one.
        onPageChanged(pagerState.currentPage)
    }

    var showControls by remember { mutableStateOf(true) }
    // A video's play button and scrubber are part of these controls, not a second set of their own:
    // both fade away together while it plays and come back together on a tap. They used to keep their
    // own visibility and ended up contradicting each other - bars without a scrubber or the other way
    // round - which showed up most clearly in landscape.
    val videoController = remember { com.example.amoledgallery.ui.components.VideoController() }
    var videoSpeedOpen by remember { mutableStateOf(false) }
    // Hiding the controls also hides Android's status and navigation bars (a swipe from the edge shows them briefly).
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(showControls) {
        val window = hostView.context.findActivity()?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, hostView)
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showControls) {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (com.example.amoledgallery.ui.features.ViewerState.rotationLocked) com.example.amoledgallery.ui.features.setRotationLock(hostView.context, false)
            hostView.context.findActivity()?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, hostView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(showControls, videoController.isPlaying, videoController.scrubbing) {
        if (showControls && videoController.isPlaying && !videoController.scrubbing) {
            kotlinx.coroutines.delay(2500)
            showControls = false
        }
    }
    // Pausing, or a video that doesn't loop reaching its end, brings the controls back.
    androidx.compose.runtime.LaunchedEffect(videoController.pausedByUser, videoController.ended) {
        if (videoController.pausedByUser || videoController.ended) showControls = true
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    /** The photo whose "set as wallpaper" sheet is open. */
    var wallpaperItem by remember { mutableStateOf<MediaItem?>(null) }
    // iOS: the ⋯ menu in the top bar.
    var showMoreMenu by remember { mutableStateOf(false) }
    var moreMenuAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // HyperOS: details open as a full "Detail info" page instead of a panel.
    var hyperInfoOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MediaItem?>(null) }
    var viewerSize by remember { mutableStateOf(IntSize.Zero) }

    val currentItem = mediaList.getOrNull(pagerState.currentPage) ?: mediaList[0]
    val isFav = isFavorite(currentItem.id)

    // Motion photos: the short video inside the photo, played on request.
    var isMotion by remember(currentItem.id) { mutableStateOf(false) }
    var motionFile by remember { mutableStateOf<java.io.File?>(null) }
    androidx.compose.runtime.LaunchedEffect(currentItem.id) {
        isMotion = !trashMode && withContext(Dispatchers.IO) { com.example.amoledgallery.ui.features.MotionPhoto.videoOffset(context, currentItem) != null }
    }
    // HDR photos (Ultra HDR / gain maps) are shown with their full brightness range where the screen can.
    androidx.compose.runtime.LaunchedEffect(currentItem.id) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hostView.context.findActivity()?.window?.colorMode =
                if (currentItem.isImage) android.content.pm.ActivityInfo.COLOR_MODE_HDR else android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                hostView.context.findActivity()?.window?.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }
    var bounceTrigger by remember { mutableStateOf(false) }
    val favScale by animateFloatAsState(
        targetValue = if (bounceTrigger) 1.3f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        finishedListener = {
            if (bounceTrigger) bounceTrigger = false
        },
        label = "fav_scale"
    )

    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language
    val detailHaze = remember { HazeState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val statusBarPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
    val topChromePx = statusBarPx + with(density) { 64.dp.toPx() }
    val isIos = language == DesignLanguage.IOS_LIQUID_GLASS
    val isHyperOs = language == DesignLanguage.XIAOMI_HYPEROS

    // iOS opens the details the way Photos does: the photo moves up together with the panel, which starts at
    // 46 % of the height. The panel can then be pulled further up over the photo ("expand") until it reaches
    // the status bar; pulling down undoes the expand before it closes the panel.
    val panelHeightPx = viewerSize.height * (if (isIos) 0.54f else INFO_PANEL_FRACTION)
    val infoExpandMaxPx = if (isIos) (viewerSize.height - panelHeightPx - statusBarPx).coerceAtLeast(0f) else 0f
    var infoExpand by remember { mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) { infoExpand = 0f }
    // The other designs' panel grows upwards too, but only as far as its details need to be seen whole
    // (what does not fit is its scroll range), and never over the top bar.
    val infoScroll = rememberScrollState()
    fun infoExpandMax(): Float = when {
        isIos -> infoExpandMaxPx
        isHyperOs -> 0f
        else -> minOf(
            infoScroll.maxValue + infoExpand,
            viewerSize.height - viewerSize.height * INFO_PANEL_FRACTION - topChromePx
        ).coerceAtLeast(0f)
    }
    // Only read inside callbacks and small child scopes: read here, crossing the halfway point would
    // recompose the whole viewer (pager and images included) in the middle of the panel animation.
    val infoMostlyOpen by remember { derivedStateOf { infoProgress > 0.5f } }
    // The details panel is built once, shortly after the viewer has opened, and then kept (invisible while
    // closed), so opening it doesn't have to compose and lay it out during its first animation frames.
    var panelComposed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(600)
        panelComposed = true
    }

    /**
     * Applies a vertical finger movement ([dy] > 0 = downwards) to the details and returns the part of it that
     * was used. iOS: upwards past the open position expands the panel over the photo, downwards first undoes
     * that expand before closing the panel.
     */
    fun applyInfoDrag(dy: Float): Float {
        val expandMax = infoExpandMax()
        if (dy < 0f && infoProgress >= 1f && infoExpand < expandMax) {
            val take = maxOf(dy, -(expandMax - infoExpand))
            infoExpand -= take
            return take
        }
        var rest = dy
        if (rest > 0f && infoExpand > 0f) {
            val take = minOf(rest, infoExpand)
            infoExpand -= take
            rest -= take
            if (rest == 0f) return dy
        }
        if (panelHeightPx <= 0f) return dy - rest
        val before = infoProgress
        infoProgress = (infoProgress - rest / panelHeightPx).coerceIn(0f, 1f)
        return dy - rest + (before - infoProgress) * panelHeightPx
    }

    fun settleInfo(target: Float) {
        panelComposed = true
        settleJob?.cancel()
        settleJob = scope.launch {
            if (target == 0f && infoExpand > 0f) {
                launch {
                    animate(infoExpand, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = 420f)) { value, _ -> infoExpand = value }
                }
            }
            animate(infoProgress, target, animationSpec = spring(dampingRatio = 1f, stiffness = 420f)) { value, _ ->
                infoProgress = value
            }
        }
    }

    fun settleDismiss() {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(dismissOffset, 0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f)) { value, _ ->
                dismissOffset = value
            }
        }
    }

    // Back closes the details first, then the viewer.
    BackHandler(onBack = onBack)
    InfoBackHandler(isOpen = { infoMostlyOpen }, onClose = { settleInfo(0f) })

    // Pull-down-to-close: the photo follows the finger and shrinks, the background fades; released past
    // the threshold it closes (zooming back into the grid), otherwise it springs back.
    val dismissShrink = when (language) {
        DesignLanguage.IOS_LIQUID_GLASS -> 0.45f
        DesignLanguage.GOOGLE_EXPRESSIVE -> 0.3f
        DesignLanguage.SAMSUNG_ONEUI -> 0.25f
        DesignLanguage.XIAOMI_HYPEROS -> 0.2f
    }
    val favoriteBurst = remember { androidx.compose.animation.core.Animatable(1f) }

    // HyperOS shows photos on white while the controls are visible, black in immersive mode.
    val lightChrome = language == DesignLanguage.XIAOMI_HYPEROS && palette.isLight
    val backgroundColor by animateColorAsState(
        targetValue = if (lightChrome && showControls) palette.background else Color.Black,
        animationSpec = tween(220),
        label = "viewer_background"
    )

    val dateMillis = currentItem.date * 1000L
    // iOS Photos names the day relatively ("Heute", "Gestern") and shows the bare time below it.
    // A file handed over by another app may have no date at all; its name is shown instead of 1 January 1970.
    val hasDate = dateMillis > 0L
    val dateTitle = remember(dateMillis, LanguageState.language, isIos, currentItem.displayName) {
        if (!hasDate) return@remember currentItem.displayName
        val longDate = SimpleDateFormat(strings.longDatePattern, LanguageState.locale).format(Date(dateMillis))
        if (isIos) relativeDayTitle(dateMillis) ?: longDate else longDate
    }
    val timeText = remember(dateMillis, LanguageState.language, isIos) {
        if (!hasDate) return@remember ""
        val time = SimpleDateFormat("HH:mm", LanguageState.locale).format(Date(dateMillis))
        if (isIos || isHyperOs) time else strings.timeLabel(time)
    }

    val actions = DetailActions(
        isFavorite = isFav,
        favoriteScale = { favScale },
        favoriteBurst = { favoriteBurst.value },
        onBack = onBack,
        onToggleFavorite = {
            bounceTrigger = true
            if (!isFav) {
                scope.launch {
                    favoriteBurst.snapTo(0f)
                    favoriteBurst.animateTo(1f, tween(520))
                }
            }
            onToggleFavorite(currentItem.id)
        },
        onShare = {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (currentItem.isVideo) "video/*" else "image/*"
                putExtra(Intent.EXTRA_STREAM, currentItem.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, strings.shareItem))
        },
        onRename = { showRenameDialog = true },
        onEdit = { editingItem = currentItem },
        onInfo = { if (isHyperOs) hyperInfoOpen = true else settleInfo(if (infoMostlyOpen) 0f else 1f) },
        onDelete = { onTrashItem(currentItem) },
        onRestore = { onRestoreItem(currentItem) },
        onDeletePermanently = { onDeletePermanently(currentItem) },
        showRename = !trashMode && !viewOnly && allowRename,
        showMore = (!trashMode && !viewOnly && allowRename) || currentItem.isVideo,
        onMore = { anchor ->
            moreMenuAnchor = anchor
            showMoreMenu = true
        },
        infoOpen = { infoMostlyOpen }
    )
    val onPhotoTap: () -> Unit = {
        if (infoMostlyOpen) settleInfo(0f) else showControls = !showControls
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewerSize = it }
            .drawBehind {
                val fade = (dismissOffset / size.height * 2.2f).coerceIn(0f, 0.9f)
                drawRect(backgroundColor.copy(alpha = backgroundColor.alpha * (1f - fade)))
            }
    ) {
        HazeSource(
            detailHaze,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val h = size.height
                    val w = size.width
                    val p = infoProgress
                    var infoScale = 1f
                    var infoShift = 0f
                    if (p > 0f && h > 0f && w > 0f) {
                        val aspect = currentItem.displayAspectRatio
                        val fittedHeight = if (aspect > 0f) minOf(h, w / aspect) else h
                        if (isIos) {
                            // iOS: the photo keeps its size and moves up with the panel to the top of the screen,
                            // then further up while the panel is pulled over it.
                            infoShift = -((h - fittedHeight) / 2f) * p - infoExpand
                        } else {
                            // Details: shrink the photo just enough to fit the space above the panel and centre it there.
                            // A panel pulled higher leaves less room, so the photo shrinks further.
                            val available = (h - panelHeightPx - infoExpand - topChromePx).coerceAtLeast(h * 0.2f)
                            val targetScale = (available / fittedHeight).coerceAtMost(1f)
                            infoScale = 1f + (targetScale - 1f) * p
                            infoShift = (topChromePx + available / 2f - h / 2f) * p
                        }
                    }
                    val offset = dismissOffset
                    val fraction = (offset / h).coerceIn(0f, 1f)
                    translationY = offset + infoShift
                    val s = (1f - fraction * dismissShrink) * infoScale
                    scaleX = s
                    scaleY = s
                    if (language == DesignLanguage.GOOGLE_EXPRESSIVE && fraction > 0f) {
                        shape = RoundedCornerShape((fraction * 120f).dp)
                        clip = true
                    }
                }
                .pointerInput(isZoomed, panelHeightPx) {
                    if (isZoomed) return@pointerInput
                    // Decides the direction of a one-finger drag before the pager sees it (Initial pass).
                    // A drag that is mostly vertical - even with some sideways drift - closes the viewer or
                    // moves the details panel and is kept away from the pager; everything else is left to it.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val velocity = VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        var total = Offset.Zero
                        var vertical = false
                        var dismissing = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            val multiTouch = event.changes.count { it.pressed } > 1
                            if (change == null || !change.pressed || multiTouch) {
                                if (vertical) {
                                    val vy = if (multiTouch) 0f else velocity.calculateVelocity().y
                                    if (dismissing) {
                                        if (dismissOffset > 170f || vy > 1800f) onBack() else settleDismiss()
                                    } else if (!(isIos && infoProgress >= 1f)) {
                                        // iOS: a panel that is fully open stays where it was pulled to.
                                        val target = when {
                                            vy < -700f -> 1f
                                            vy > 700f -> 0f
                                            else -> if (infoProgress > 0.5f) 1f else 0f
                                        }
                                        settleInfo(target)
                                    }
                                }
                                break
                            }
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val delta = change.positionChange()
                            if (!vertical) {
                                // The pager is already between pages: this drag belongs to it.
                                if (pagerState.currentPageOffsetFraction != 0f) break
                                total += delta
                                if (total.getDistance() > viewConfiguration.touchSlop) {
                                    if (abs(total.y) < abs(total.x) * 0.8f) break
                                    vertical = true
                                    settleJob?.cancel()
                                    dismissing = infoProgress <= 0.001f && total.y > 0f && dismissOffset >= 0f
                                    if (isHyperOs && !dismissing) {
                                        // HyperOS has no panel under the photo, and like Xiaomi's gallery its Detail page
                                        // opens only from More > Details: swiping up does nothing.
                                        change.consume()
                                        break
                                    }
                                    if (!dismissing) panelComposed = true
                                }
                            }
                            if (vertical) {
                                change.consume()
                                if (dismissing) {
                                    dismissOffset = (dismissOffset + delta.y).coerceAtLeast(0f)
                                } else if (panelHeightPx > 0f) {
                                    applyInfoDrag(delta.y)
                                }
                            }
                        }
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> mediaList[index].id }
            ) { page ->
                val currentMedia = mediaList[page]

                if (currentMedia.isVideo) {
                    AmoledVideoPlayer(
                        item = currentMedia,
                        isActivePage = pagerState.currentPage == page,
                        controller = videoController,
                        controlsVisible = showControls,
                        onTap = onPhotoTap,
                        onZoomChange = { zoomed ->
                            if (pagerState.currentPage == page) {
                                isZoomed = zoomed
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // The sharp full-size version is only loaded once the photo is zoomed, so swiping through
                    // photos keeps using screen-sized bitmaps.
                    var showFull by remember(currentMedia.id) { mutableStateOf(false) }
                    ZoomableBox(
                        contentAspectRatio = currentMedia.displayAspectRatio,
                        onSingleTap = onPhotoTap,
                        onZoomChange = { zoomed ->
                            if (zoomed) showFull = true
                            if (pagerState.currentPage == page) {
                                isZoomed = zoomed
                            }
                        }
                    ) {
                        AsyncImage(
                            model = currentMedia.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (showFull) {
                            val pageContext = LocalContext.current
                            // Drawn over the screen-sized image; it stays transparent until it has loaded.
                            AsyncImage(
                                model = remember(currentMedia.id, currentMedia.dateModified) { fullImageRequest(pageContext, currentMedia) },
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        if (currentItem.isVideo) {
            com.example.amoledgallery.ui.components.VideoCenterPlay(
                controller = videoController,
                language = language,
                visible = showControls,
                hazeState = detailHaze,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = (1f - (dismissOffset / 220f).coerceIn(0f, 1f)) * (1f - infoProgress * 2f).coerceIn(0f, 1f) }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    // iOS hides the top bar while the details are open, so the photo reaches up to the status bar.
                    val infoFade = if (isIos) (1f - infoProgress * 1.5f).coerceIn(0f, 1f) else 1f
                    alpha = (1f - (dismissOffset / 220f).coerceIn(0f, 1f)) * infoFade
                }
        ) {
            DetailTopBar(language, dateTitle, timeText, lightChrome, detailHaze, actions)
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showControls && isMotion,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 74.dp)
        ) {
            com.example.amoledgallery.ui.features.MotionChip {
                scope.launch {
                    motionFile = withContext(Dispatchers.IO) { com.example.amoledgallery.ui.features.MotionPhoto.extract(context, currentItem) }
                }
            }
        }

        val bottomBar: @Composable BoxScope.() -> Unit = {
            AnimatedVisibility(
                // Fades with the panel through its alpha below (no visibility switch halfway, which recomposed);
                // once the panel is up it covers the bar and takes its touches. iOS keeps its toolbar on top of
                // the details instead.
                visible = showControls,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(160)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val infoFade = if (isIos) 1f else (1f - infoProgress * 2f).coerceIn(0f, 1f)
                        alpha = (1f - (dismissOffset / 220f).coerceIn(0f, 1f)) * infoFade
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // iOS keeps its toolbar over the open details, but not the video controls.
                    if (currentItem.isVideo && !(isIos && infoMostlyOpen)) {
                        com.example.amoledgallery.ui.components.VideoControls(videoController, currentItem, language, lightChrome, detailHaze)
                    }
                    if (viewOnly) {
                        DetailViewOnlyBar(language, lightChrome, detailHaze, actions)
                    } else if (trashMode) {
                        DetailTrashBar(language, lightChrome, detailHaze, actions)
                    } else {
                        // One UI: the strip of neighbouring photos above the toolbar (not on videos, where the
                        // strip of the video's own frames sits in that place).
                        if (language == DesignLanguage.SAMSUNG_ONEUI && mediaList.size > 1 && !currentItem.isVideo) {
                            OneUiFilmstrip(mediaList, pagerState)
                        }
                        DetailBottomBar(language, lightChrome, detailHaze, actions)
                    }
                }
            }
        }
        if (!isIos) bottomBar()

        if (panelComposed && panelHeightPx > 0f && isIos) {
            IosInfoPanel(
                item = currentItem,
                viewerHeightPx = viewerSize.height.toFloat(),
                openHeightPx = panelHeightPx,
                expandMaxPx = infoExpandMaxPx,
                progress = { infoProgress },
                expand = { infoExpand },
                onDrag = { dy ->
                    settleJob?.cancel()
                    applyInfoDrag(dy)
                },
                onSettle = { vy ->
                    settleInfo(
                        when {
                            vy < -700f -> 1f
                            vy > 700f -> 0f
                            else -> if (infoProgress > 0.5f) 1f else 0f
                        }
                    )
                }
            )
        }
        if (isIos) bottomBar()

        if (panelComposed && panelHeightPx > 0f && !isIos && !isHyperOs) {
            InfoPanel(
                item = currentItem,
                onEdit = if (viewOnly) null else actions.onEdit,
                heightDp = with(density) { panelHeightPx.toDp() },
                progress = { infoProgress },
                expand = { infoExpand },
                scrollState = infoScroll,
                onDrag = { dy ->
                    settleJob?.cancel()
                    applyInfoDrag(dy)
                },
                onSettle = { vy ->
                    settleInfo(
                        when {
                            vy < -700f -> 1f
                            vy > 700f -> 0f
                            else -> if (infoProgress > 0.5f) 1f else 0f
                        }
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (isHyperOs) {
            val infoPush by animateFloatAsState(
                targetValue = if (hyperInfoOpen) 1f else 0f,
                animationSpec = pushSpec(language),
                label = "hyperos_info_push"
            )
            if (hyperInfoOpen || infoPush > 0.001f) {
                if (hyperInfoOpen) BackHandler { hyperInfoOpen = false }
                HyperOsDetailInfoPage(
                    item = currentItem,
                    onBack = { hyperInfoOpen = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .pushTransition(language, { infoPush }, isTop = true)
                        .blockTouchesBelow()
                )
            }
        }
    }

    // Speed and looping, first in every design's "more" menu; the player itself only keeps play, seek and sound.
    val videoEntries: (Boolean) -> List<com.example.amoledgallery.ui.design.MenuEntry> = { withIcons ->
        if (!currentItem.isVideo) emptyList() else listOf(
            com.example.amoledgallery.ui.design.MenuEntry(
                "${com.example.amoledgallery.ui.i18n.F.playbackSpeed}: ${speedLabel(videoController.speed)}",
                icon = if (withIcons) Icons.Outlined.Speed else null
            ) { videoSpeedOpen = true },
            com.example.amoledgallery.ui.design.MenuEntry(
                if (videoController.looping) com.example.amoledgallery.ui.i18n.F.loopOn else com.example.amoledgallery.ui.i18n.F.loopOff,
                icon = if (withIcons) Icons.Outlined.Repeat else null
            ) { videoController.toggleLoop() }
        )
    }
    com.example.amoledgallery.ui.design.ThemedPopupMenu(
        expanded = showMoreMenu,
        onDismissRequest = { showMoreMenu = false },
        anchorBounds = moreMenuAnchor,
        entries = if (language == DesignLanguage.SAMSUNG_ONEUI) {
            // One UI: the ⋮ circle at the top right.
            buildList {
                addAll(videoEntries(false))
                add(com.example.amoledgallery.ui.design.MenuEntry(strings.copyToClipboard) {
                    context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(
                        android.content.ClipData.newUri(context.contentResolver, currentItem.displayName, currentItem.uri)
                    )
                })
                // Photos become the wallpaper; videos and GIFs become a live wallpaper (see the sheet).
                add(com.example.amoledgallery.ui.design.MenuEntry(strings.setAsWallpaper) { wallpaperItem = currentItem })
                if (actions.showRename) {
                    add(com.example.amoledgallery.ui.design.MenuEntry(strings.rename) { showRenameDialog = true })
                }
                if (!trashMode && !viewOnly) addAll(com.example.amoledgallery.ui.features.viewerExtraEntries(context, currentItem, mediaList, scope, withIcons = false))
            }
        } else if (isHyperOs) {
            // HyperOS: the "More" menu above the toolbar.
            buildList {
                addAll(videoEntries(false))
                if (actions.showRename) add(com.example.amoledgallery.ui.design.MenuEntry(strings.rename) { showRenameDialog = true })
                // Photos become the wallpaper; videos and GIFs become a live wallpaper (see the sheet).
                add(com.example.amoledgallery.ui.design.MenuEntry(strings.setAsWallpaper) { wallpaperItem = currentItem })
                add(com.example.amoledgallery.ui.design.MenuEntry(strings.details) { hyperInfoOpen = true })
                if (!trashMode && !viewOnly) addAll(com.example.amoledgallery.ui.features.viewerExtraEntries(context, currentItem, mediaList, scope, withIcons = false))
            }
        } else {
            buildList {
                addAll(videoEntries(true))
                if (actions.showRename) {
                    add(com.example.amoledgallery.ui.design.MenuEntry(strings.rename, icon = Icons.Outlined.DriveFileRenameOutline) { showRenameDialog = true })
                }
                add(com.example.amoledgallery.ui.design.MenuEntry(strings.edit, icon = Icons.Outlined.Edit) { editingItem = currentItem })
                add(com.example.amoledgallery.ui.design.MenuEntry(strings.setAsWallpaper, icon = Icons.Outlined.Wallpaper) { wallpaperItem = currentItem })
                if (!trashMode && !viewOnly) addAll(com.example.amoledgallery.ui.features.viewerExtraEntries(context, currentItem, mediaList, scope, withIcons = true))
            }
        }
    )

    if (videoSpeedOpen) {
        com.example.amoledgallery.ui.design.ThemedSheet(
            onDismissRequest = { videoSpeedOpen = false },
            title = com.example.amoledgallery.ui.i18n.F.playbackSpeed
        ) {
            com.example.amoledgallery.ui.design.ThemedListSection(
                inSheet = true,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp),
                rows = listOf(0.25f, 0.5f, 1f, 1.5f, 2f).map { speed ->
                    com.example.amoledgallery.ui.design.ListRow(
                        speedLabel(speed),
                        value = if (speed == videoController.speed) "✓" else null,
                        showChevron = false,
                        onClick = {
                            videoController.changeSpeed(speed)
                            videoSpeedOpen = false
                        }
                    )
                }
            )
        }
    }

    // Set as wallpaper: which screen, in the current design's sheet; set in-app (see setWallpaper).
    wallpaperItem?.let { item ->
        com.example.amoledgallery.ui.design.ThemedSheet(
            onDismissRequest = { wallpaperItem = null },
            title = strings.setAsWallpaper
        ) {
            val palette = LocalAppTheme.current.palette
            // A GIF can be either: a still wallpaper of its first frame, or a moving one. A video can
            // only be the moving one.
            val movable = item.isVideo || item.mimeType.equals("image/gif", ignoreCase = true)
            com.example.amoledgallery.ui.design.ThemedListSection(
                inSheet = true,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp),
                rows = buildList {
                    if (movable) {
                        add(
                            com.example.amoledgallery.ui.design.ListRow(
                                strings.liveWallpaper,
                                icon = Icons.Outlined.Movie,
                                iconColor = palette.accent,
                                showChevron = false,
                                onClick = {
                                    val tooLong = item.isVideo &&
                                        item.durationMs > com.example.amoledgallery.service.LiveWallpaperChoice.MAX_VIDEO_MS
                                    if (tooLong) {
                                        android.widget.Toast.makeText(context, strings.liveWallpaperTooLong, android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        wallpaperItem = null
                                        com.example.amoledgallery.util.setLiveWallpaper(context, item.uri, item.isVideo)
                                    }
                                }
                            )
                        )
                    }
                    if (!item.isVideo) {
                        listOf(
                            com.example.amoledgallery.util.WallpaperTarget.HOME to strings.wallpaperHome,
                            com.example.amoledgallery.util.WallpaperTarget.LOCK to strings.wallpaperLock,
                            com.example.amoledgallery.util.WallpaperTarget.BOTH to strings.wallpaperBoth
                        ).forEach { (target, label) ->
                            add(
                                com.example.amoledgallery.ui.design.ListRow(
                                    label,
                                    icon = Icons.Outlined.Wallpaper,
                                    iconColor = palette.accent,
                                    showChevron = false,
                                    onClick = {
                                        wallpaperItem = null
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                com.example.amoledgallery.util.setWallpaper(context, item.uri, target)
                                            }
                                            android.widget.Toast.makeText(
                                                context,
                                                if (ok) strings.wallpaperSet else strings.wallpaperFailed,
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    motionFile?.let { file -> com.example.amoledgallery.ui.features.MotionPlayerOverlay(currentItem, file) { motionFile = null } }

    if (showRenameDialog) {
        RenameDialog(
            currentName = currentItem.displayName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onRenameItem(currentItem, newName)
            }
        )
    }

    // Crop / rotate / mirror editor, shown above everything (bars included) in the overlay host. It settles
    // in from a slight zoom while fading in, as if the photo shrinks into the editor's frame.
    editingItem?.let { item ->
        OverlayWindow { transition ->
            val progress by transition.animateFloat(
                transitionSpec = {
                    if (targetState) spring(dampingRatio = 0.9f, stiffness = 420f) else tween(180)
                },
                label = "editor_enter"
            ) { visible -> if (visible) 1f else 0f }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (progress * 1.6f).coerceIn(0f, 1f)
                        val s = 1.06f - 0.06f * progress
                        scaleX = s
                        scaleY = s
                    }
            ) {
                MediaEditorScreen(item = item, onClose = { editingItem = null })
            }
        }
    }
}

private fun speedLabel(speed: Float): String = "${speed}×".replace(".0×", "×")

/**
 * The photo decoded large enough to stay sharp when zoomed. By default it would be decoded at the screen size,
 * which leaves a very tall scroll screenshot only about a hundred pixels wide. It is capped at 12 million pixels
 * and 16384 px per side so the bitmap can still be drawn and stays affordable on low-memory phones.
 */
private fun fullImageRequest(context: android.content.Context, item: MediaItem): coil3.request.ImageRequest {
    val rotated = item.orientation % 180 != 0
    val w = if (rotated) item.height else item.width
    val h = if (rotated) item.width else item.height
    val builder = coil3.request.ImageRequest.Builder(context).data(item.uri)
    if (w > 0 && h > 0) {
        val maxPixels = 12_000_000.0
        val maxSide = 16384.0
        val scale = minOf(1.0, kotlin.math.sqrt(maxPixels / (w.toDouble() * h)), maxSide / maxOf(w, h))
        builder.size((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        builder.precision(coil3.size.Precision.INEXACT)
    }
    return builder.build()
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * One UI's filmstrip: small thumbnails of the neighbouring photos with the current one larger and outlined.
 * Tapping a thumbnail opens it; sliding the strip flips through the photos as it passes them.
 */
@Composable
private fun OneUiFilmstrip(mediaList: List<MediaItem>, pagerState: androidx.compose.foundation.pager.PagerState) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = pagerState.currentPage)
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var userScrolling by remember { mutableStateOf(false) }
    if (dragged) userScrolling = true
    val itemWidth = 38.dp
    val gap = 3.dp
    val stepPx = with(density) { (itemWidth + gap).toPx() }

    // The strip follows the pager, unless the user is sliding the strip itself.
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        if (!userScrolling) listState.animateScrollToItem(pagerState.currentPage)
    }
    // While the user slides the strip, the photo under its centre opens.
    androidx.compose.runtime.LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex + if (listState.firstVisibleItemScrollOffset > stepPx / 2f) 1 else 0
        }.collect { centre ->
            if (userScrolling && centre in mediaList.indices && centre != pagerState.currentPage) {
                pagerState.scrollToPage(centre)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) userScrolling = false
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        contentAlignment = Alignment.Center
    ) {
        val side = (maxWidth - itemWidth) / 2
        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(listState),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(mediaList.size, key = { mediaList[it].id }) { index ->
                val item = mediaList[index]
                val current = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(if (current) 54.dp else 44.dp)
                        .graphicsLayer { scaleX = if (current) 1.2f else 1f }
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2A2A2C))
                        .then(if (current) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
                        .clickable {
                            userScrolling = false
                            scope.launch {
                                pagerState.scrollToPage(index)
                                listState.animateScrollToItem(index)
                            }
                        }
                ) {
                    AsyncImage(
                        model = remember(item.id, context) { com.example.amoledgallery.ui.components.thumbnailRequest(context, item) },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** Back closes the details panel while it is mostly open. Reads the state in its own small scope. */
@Composable
private fun InfoBackHandler(isOpen: () -> Boolean, onClose: () -> Unit) {
    BackHandler(enabled = isOpen(), onBack = onClose)
}

/**
 * The details panel underneath the photo. Its content scrolls; dragging it down at the top of the list
 * (or anywhere while it is still opening) moves the panel itself.
 */
@Composable
private fun InfoPanel(
    item: MediaItem,
    onEdit: (() -> Unit)?,
    heightDp: Dp,
    progress: () -> Float,
    /** How far the open panel has been pulled up beyond [heightDp], in px (read at layout, not composition). */
    expand: () -> Float,
    scrollState: androidx.compose.foundation.ScrollState,
    /** Applies a vertical drag (px, > 0 = down) to opening / growing the panel; returns the part it used. */
    onDrag: (Float) -> Float,
    onSettle: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language
    val latestOnDrag by androidx.compose.runtime.rememberUpdatedState(onDrag)
    val latestOnSettle by androidx.compose.runtime.rememberUpdatedState(onSettle)
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Upwards: first open the panel fully, then pull it up until all details fit, then scroll.
                if (available.y < 0f && source == NestedScrollSource.UserInput) {
                    val used = latestOnDrag(available.y)
                    if (used != 0f) return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Downwards at the top of the list: lower the panel, then close it.
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    return Offset(0f, latestOnDrag(available.y))
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val p = progress()
                if (p < 1f) {
                    latestOnSettle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val corner = when (language) {
        DesignLanguage.IOS_LIQUID_GLASS -> 26.dp
        DesignLanguage.GOOGLE_EXPRESSIVE -> 28.dp
        // One UI: the details simply continue on the black page below the photo.
        DesignLanguage.SAMSUNG_ONEUI -> 0.dp
        else -> 20.dp
    }
    val isOneUi = language == DesignLanguage.SAMSUNG_ONEUI
    Column(
        modifier = modifier
            .fillMaxWidth()
            // The open height plus however far it has been pulled up; measured here, so growing the panel
            // re-lays it out without recomposing the viewer.
            .layout { measurable, constraints ->
                val height = (heightDp.roundToPx() + expand()).roundToInt().coerceAtMost(constraints.maxHeight)
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val p = progress()
                translationY = (1f - p) * size.height
                // Kept composed while closed; a zero alpha layer skips drawing it entirely.
                alpha = if (p <= 0f) 0f else 1f
            }
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
            .then(
                // Google (Gemini): a Gemini glass sheet with its colour glow along the edge, like the bars.
                if (language == DesignLanguage.GOOGLE_EXPRESSIVE) {
                    Modifier.geminiGlass(RoundedCornerShape(topStart = corner, topEnd = corner), state = null, tintAlpha = 0.96f)
                } else {
                    Modifier.background(if (palette.isLight || isOneUi) palette.background else palette.surface)
                }
            )
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
            .nestedScroll(connection)
            .verticalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(horizontal = if (isOneUi) 24.dp else 16.dp)
            .padding(bottom = 24.dp)
    ) {
        if (isOneUi) {
            Spacer(modifier = Modifier.height(28.dp))
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(palette.onBackground.copy(alpha = 0.22f))
                )
            }
            if (language == DesignLanguage.GOOGLE_EXPRESSIVE) {
                // Gemini: no title - the date heading of the details leads.
                Spacer(modifier = Modifier.height(18.dp))
            } else {
                Text(
                    text = strings.details,
                    color = palette.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 14.dp)
                )
            }
        }
        MediaInfoContent(item = item, onEdit = onEdit)
    }
}

/**
 * iOS Photos' details: the info cards open underneath the photo (which moves up with them) and can be pulled
 * further up over the photo. Scrolling upwards first opens the panel, then pulls it up, then scrolls the list;
 * scrolling downwards at the top of the list lowers it again and finally closes it. The toolbar stays on top.
 */
@Composable
private fun IosInfoPanel(
    item: MediaItem,
    viewerHeightPx: Float,
    openHeightPx: Float,
    expandMaxPx: Float,
    progress: () -> Float,
    expand: () -> Float,
    onDrag: (Float) -> Float,
    onSettle: (Float) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val density = LocalDensity.current
    val latestOnDrag by androidx.compose.runtime.rememberUpdatedState(onDrag)
    val latestOnSettle by androidx.compose.runtime.rememberUpdatedState(onSettle)
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && source == NestedScrollSource.UserInput) {
                    return Offset(0f, latestOnDrag(available.y))
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    return Offset(0f, latestOnDrag(available.y))
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (progress() < 1f) {
                    latestOnSettle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { (openHeightPx + expandMaxPx).toDp() })
            .graphicsLayer {
                val p = progress()
                translationY = viewerHeightPx - openHeightPx - expand() + (1f - p) * openHeightPx
                // Kept composed while closed; a zero alpha layer skips drawing it entirely.
                alpha = if (p <= 0f) 0f else 1f
            }
            .background(palette.background)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
            .nestedScroll(connection)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            // Room to scroll the last card above the floating toolbar.
            .padding(bottom = 100.dp)
    ) {
        MediaInfoContent(item = item)
    }
}

/** "Heute" / "Gestern" for photos of those days, null for any other day. */
private fun relativeDayTitle(millis: Long): String? {
    val day = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val today = java.util.Calendar.getInstance()
    fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay(day, today)) return strings.today
    today.add(java.util.Calendar.DAY_OF_YEAR, -1)
    if (sameDay(day, today)) return strings.yesterday
    return null
}

private class DetailActions(
    val isFavorite: Boolean,
    val favoriteScale: () -> Float,
    /** 0..1 progress of the ring that bursts around the heart when an item is favorited (1 = idle). */
    val favoriteBurst: () -> Float,
    val onBack: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onShare: () -> Unit,
    val onRename: () -> Unit,
    val onEdit: () -> Unit,
    val onInfo: () -> Unit,
    val onDelete: () -> Unit,
    val onRestore: () -> Unit,
    val onDeletePermanently: () -> Unit,
    /** False in the trash, where a deleted item cannot be renamed. */
    val showRename: Boolean,
    /** iOS: whether the ⋯ menu has anything to offer (renaming, or a video's speed and loop). */
    val showMore: Boolean,
    /** iOS: opens the ⋯ menu (anchored at the given bounds) holding the less frequent actions. */
    val onMore: (androidx.compose.ui.geometry.Rect?) -> Unit = {},
    /** Whether the details are open; read only inside the toolbar, so only the toolbar recomposes. */
    val infoOpen: () -> Boolean = { false }
)

@Composable
private fun DetailTopBar(
    language: DesignLanguage,
    dateTitle: String,
    timeText: String,
    lightChrome: Boolean,
    hazeState: HazeState,
    actions: DetailActions
) {
    val palette = LocalAppTheme.current.palette
    when (language) {
        // One UI 8 viewer: a dark circle with the back chevron on the left, a dark ⋮ circle on the right.
        DesignLanguage.SAMSUNG_ONEUI -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OneUiChromeCircle(Icons.AutoMirrored.Rounded.ArrowBackIos, strings.back, Modifier, actions.onBack)
            Spacer(modifier = Modifier.weight(1f))
            var moreBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
            OneUiChromeCircle(
                Icons.Rounded.MoreVert,
                strings.options,
                Modifier.onGloballyPositioned { moreBounds = it.boundsInRoot() }
            ) { actions.onMore(moreBounds) }
        }

        DesignLanguage.GOOGLE_EXPRESSIVE -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TonalCircle(Icons.AutoMirrored.Rounded.ArrowBack, strings.back, actions.onBack)
            // Balances the circles on the right so the date pill stays centred.
            Spacer(modifier = Modifier.width(56.dp))
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .geminiChip(CircleShape)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(dateTitle, color = palette.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.weight(1f))
            // ⋮ holds rename, edit and "set as wallpaper".
            var moreBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
            TonalCircle(
                Icons.Rounded.MoreVert,
                strings.options,
                { actions.onMore(moreBounds) },
                Modifier.onGloballyPositioned { moreBounds = it.boundsInRoot() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            TonalCircle(Icons.Outlined.Info, strings.details, actions.onInfo)
        }

        DesignLanguage.IOS_LIQUID_GLASS -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            GlassCircle(Icons.AutoMirrored.Rounded.ArrowBackIos, strings.back, palette.onBackground, hazeState, 48.dp, Modifier.align(Alignment.CenterStart), actions.onBack)
            // iOS 26 Photos: a wide glass capsule with the day ("Heute") and the time underneath.
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(min = 170.dp)
                    .glass(RoundedCornerShape(percent = 50), barGlass(), hazeState)
                    .padding(horizontal = 36.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(dateTitle, color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(timeText, color = palette.onBackground, fontSize = 13.sp, maxLines = 1)
            }
            // Less frequent actions (rename, a video's speed and loop) sit behind the ⋯ circle.
            if (actions.showMore) {
                var moreBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                GlassCircle(
                    Icons.Rounded.MoreHoriz,
                    strings.options,
                    palette.onBackground,
                    hazeState,
                    48.dp,
                    Modifier
                        .align(Alignment.CenterEnd)
                        .onGloballyPositioned { moreBounds = it.boundsInRoot() }
                ) { actions.onMore(moreBounds) }
            }
        }

        // HyperOS: a solid dark bar with the back arrow, the date and the time underneath; rename lives in "More".
        DesignLanguage.XIAOMI_HYPEROS -> {
            val chrome = if (lightChrome) palette.onBackground else Color.White
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (lightChrome) palette.background else Color(0xFF121212).copy(alpha = 0.94f))
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .height(76.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeIcon(Icons.AutoMirrored.Rounded.ArrowBack, strings.back, chrome, actions.onBack)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(dateTitle, color = chrome, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(timeText, color = chrome.copy(alpha = 0.7f), fontSize = 15.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DetailBottomBar(
    language: DesignLanguage,
    lightChrome: Boolean,
    hazeState: HazeState,
    actions: DetailActions
) {
    val palette = LocalAppTheme.current.palette
    val favoriteIcon = if (actions.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
    val burstColor = if (language == DesignLanguage.GOOGLE_EXPRESSIVE) palette.accent else Color(0xFFFF4D6D)
    val favoriteModifier = Modifier
        .drawBehind {
            val b = actions.favoriteBurst()
            if (b < 1f) {
                drawCircle(
                    color = burstColor.copy(alpha = (1f - b) * 0.85f),
                    radius = size.minDimension * (0.45f + b * 0.5f),
                    style = Stroke(width = 3.dp.toPx() * (1f - b))
                )
            }
        }
        .graphicsLayer {
            val s = actions.favoriteScale()
            scaleX = s
            scaleY = s
        }

    when (language) {
        // One UI 8 viewer: a floating dark capsule with favourite, edit, details, share and delete.
        DesignLanguage.SAMSUNG_ONEUI -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(top = 8.dp, bottom = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF232325).copy(alpha = 0.94f))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeIcon(favoriteIcon, strings.favorite, if (actions.isFavorite) Color(0xFFFF4D6D) else Color.White, actions.onToggleFavorite, favoriteModifier)
                ChromeIcon(Icons.Outlined.Edit, strings.edit, Color.White, actions.onEdit)
                ChromeIcon(Icons.Outlined.Info, strings.details, Color.White, actions.onInfo)
                ChromeIcon(Icons.Outlined.Share, strings.share, Color.White, actions.onShare)
                ChromeIcon(Icons.Outlined.Delete, strings.delete, Color.White, actions.onDelete)
            }
        }

        DesignLanguage.GOOGLE_EXPRESSIVE -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(bottom = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            val pill = RoundedCornerShape(percent = 50)
            Row(
                modifier = Modifier
                    .geminiGlass(pill, hazeState, tintAlpha = com.example.amoledgallery.ui.design.GEMINI_PILL_TINT, blur = com.example.amoledgallery.ui.design.GeminiPillBlur, vivid = true)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChromeIcon(Icons.Outlined.Share, strings.share, palette.onBackground, actions.onShare)
                ChromeIcon(Icons.Outlined.Edit, strings.edit, palette.onBackground, actions.onEdit)
                ChromeIcon(favoriteIcon, strings.favorite, if (actions.isFavorite) palette.accent else palette.onBackground, actions.onToggleFavorite, favoriteModifier)
                ChromeIcon(Icons.Outlined.Delete, strings.delete, palette.onBackground, actions.onDelete)
            }
        }

        // iOS 26 Photos: share and trash as separate glass circles at the edges, favourite / details / edit
        // (the adjust sliders) in a compact glass capsule in the middle.
        DesignLanguage.IOS_LIQUID_GLASS -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCircle(Icons.Outlined.IosShare, strings.share, palette.onBackground, hazeState, 56.dp, Modifier, actions.onShare)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .glass(RoundedCornerShape(percent = 50), barGlass(), hazeState)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeIcon(favoriteIcon, strings.favorite, if (actions.isFavorite) Color(0xFFFF375F) else palette.onBackground, actions.onToggleFavorite, favoriteModifier)
                // Filled while the details are open, like Photos' info button.
                ChromeIcon(if (actions.infoOpen()) Icons.Rounded.Info else Icons.Outlined.Info, strings.details, palette.onBackground, actions.onInfo)
                ChromeIcon(Icons.Outlined.Tune, strings.edit, palette.onBackground, actions.onEdit)
            }
            Spacer(modifier = Modifier.weight(1f))
            GlassCircle(Icons.Outlined.Delete, strings.delete, palette.onBackground, hazeState, 56.dp, Modifier, actions.onDelete)
        }

        // HyperOS: five labelled actions - send, edit, favourite, delete, more (rename, details).
        DesignLanguage.XIAOMI_HYPEROS -> {
            val chrome = if (lightChrome) palette.onBackground else Color.White.copy(alpha = 0.9f)
            var moreBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (lightChrome) palette.background else Color.Black.copy(alpha = 0.55f))
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .height(80.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabeledChromeAction(Icons.AutoMirrored.Outlined.OpenInNew, strings.send, chrome, actions.onShare, modifier = Modifier.weight(1f))
                LabeledChromeAction(Icons.Outlined.Edit, strings.edit, chrome, actions.onEdit, modifier = Modifier.weight(1f))
                LabeledChromeAction(favoriteIcon, strings.favorites, if (actions.isFavorite) Color(0xFFFF5C7A) else chrome, actions.onToggleFavorite, favoriteModifier, modifier = Modifier.weight(1f))
                LabeledChromeAction(Icons.Outlined.Delete, strings.delete, chrome, actions.onDelete, modifier = Modifier.weight(1f))
                LabeledChromeAction(
                    Icons.Outlined.MoreHoriz,
                    strings.moreActions,
                    chrome,
                    { actions.onMore(moreBounds) },
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { moreBounds = it.boundsInRoot() }
                )
            }
        }
    }
}

/** Reduced action bar for an item in the trash: restore, details, delete permanently. */
@Composable
private fun DetailTrashBar(
    language: DesignLanguage,
    lightChrome: Boolean,
    hazeState: HazeState,
    actions: DetailActions
) {
    val palette = LocalAppTheme.current.palette
    when (language) {
        DesignLanguage.GOOGLE_EXPRESSIVE, DesignLanguage.IOS_LIQUID_GLASS -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(bottom = if (language == DesignLanguage.IOS_LIQUID_GLASS) 14.dp else 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .glass(RoundedCornerShape(percent = 50), barGlass(), hazeState)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabeledChromeAction(Icons.Outlined.Restore, strings.restore, palette.accent, actions.onRestore, height = 60.dp)
                LabeledChromeAction(Icons.Outlined.Info, strings.details, palette.onBackground, actions.onInfo, height = 60.dp)
                LabeledChromeAction(Icons.Outlined.DeleteForever, strings.deletePermanently, palette.destructive, actions.onDeletePermanently, height = 60.dp)
            }
        }

        DesignLanguage.SAMSUNG_ONEUI, DesignLanguage.XIAOMI_HYPEROS -> {
            val chrome = if (lightChrome) palette.onBackground else Color.White
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (lightChrome) Modifier.background(palette.background)
                        else Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                    )
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .height(72.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabeledChromeAction(Icons.Outlined.Restore, strings.restore, chrome, actions.onRestore)
                LabeledChromeAction(Icons.Outlined.Info, strings.details, chrome, actions.onInfo)
                LabeledChromeAction(Icons.Outlined.DeleteForever, strings.deletePermanently, if (lightChrome) palette.destructive else Color(0xFFFF6961), actions.onDeletePermanently)
            }
        }
    }
}

/** Action bar for a file another app opened with "Open with": share and details only. */
@Composable
private fun DetailViewOnlyBar(
    language: DesignLanguage,
    lightChrome: Boolean,
    hazeState: HazeState,
    actions: DetailActions
) {
    val palette = LocalAppTheme.current.palette
    when (language) {
        DesignLanguage.GOOGLE_EXPRESSIVE, DesignLanguage.IOS_LIQUID_GLASS -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(bottom = if (language == DesignLanguage.IOS_LIQUID_GLASS) 14.dp else 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .glass(RoundedCornerShape(percent = 50), barGlass(), hazeState)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabeledChromeAction(Icons.Outlined.Share, strings.share, palette.onBackground, actions.onShare, height = 60.dp)
                LabeledChromeAction(Icons.Outlined.Info, strings.details, palette.onBackground, actions.onInfo, height = 60.dp)
            }
        }

        DesignLanguage.SAMSUNG_ONEUI -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(top = 8.dp, bottom = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF232325).copy(alpha = 0.94f))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeIcon(Icons.Outlined.Info, strings.details, Color.White, actions.onInfo)
                ChromeIcon(Icons.Outlined.Share, strings.share, Color.White, actions.onShare)
            }
        }

        DesignLanguage.XIAOMI_HYPEROS -> {
            val chrome = if (lightChrome) palette.onBackground else Color.White.copy(alpha = 0.9f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (lightChrome) palette.background else Color.Black.copy(alpha = 0.55f))
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .height(80.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabeledChromeAction(Icons.AutoMirrored.Outlined.OpenInNew, strings.send, chrome, actions.onShare)
                LabeledChromeAction(Icons.Outlined.Info, strings.details, chrome, actions.onInfo)
            }
        }
    }
}

@Composable
private fun ChromeIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    iconModifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = iconModifier.size(25.dp))
    }
}

@Composable
private fun OneUiChromeCircle(icon: ImageVector, description: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, 0.9f)
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0xFF232325).copy(alpha = 0.94f))
            .clickable(interactionSource = interaction, indication = ripple(color = Color.White), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun TonalCircle(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, 0.9f)
            .size(48.dp)
            .geminiChip(CircleShape)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = palette.onBackground, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun GlassCircle(
    icon: ImageVector,
    description: String,
    tint: Color,
    hazeState: HazeState,
    size: Dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .glass(CircleShape, barGlass(), hazeState)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun LabeledChromeAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    iconModifier: Modifier = Modifier,
    height: Dp? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .then(if (height != null) Modifier.height(height) else Modifier.fillMaxHeight())
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = tint, modifier = iconModifier.size(24.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, color = tint, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(currentName) }
    ThemedDialog(
        onDismissRequest = onDismiss,
        title = strings.renameFile,
        buttons = listOf(
            DialogButton(strings.cancel, DialogButtonStyle.Cancel, onClick = onDismiss),
            DialogButton(strings.rename, DialogButtonStyle.Primary, enabled = textValue.isNotBlank()) {
                if (textValue.isNotBlank()) onConfirm(textValue.trim())
            }
        )
    ) {
        // Long file names wrap instead of scrolling sideways, so the cursor can be placed anywhere.
        ThemedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            placeholder = strings.name,
            autoFocus = true,
            maxLines = 4
        )
    }
}
