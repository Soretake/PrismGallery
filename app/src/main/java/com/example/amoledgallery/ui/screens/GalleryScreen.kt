package com.example.amoledgallery.ui.screens
import com.example.amoledgallery.ui.i18n.LanguageState
import com.example.amoledgallery.ui.i18n.displayLabel
import com.example.amoledgallery.ui.i18n.strings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.foundation.layout.size
import com.example.amoledgallery.ui.design.barGlass
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.data.ThumbnailManager
import com.example.amoledgallery.model.MediaDateSection
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.DateHeader
import com.example.amoledgallery.ui.components.ExpressiveHeroCard
import com.example.amoledgallery.ui.components.FastScroller
import com.example.amoledgallery.ui.components.FilterChips
import com.example.amoledgallery.ui.components.LargeHeader
import com.example.amoledgallery.ui.components.MediaGridItem
import com.example.amoledgallery.ui.components.detectPinchToZoomGrid
import com.example.amoledgallery.ui.components.SelectionTopBar
import com.example.amoledgallery.ui.components.OneUiSelectAll
import com.example.amoledgallery.ui.components.detectDragSelect
import com.example.amoledgallery.ui.design.GlassRim
import com.example.amoledgallery.ui.design.GlassSpec
import com.example.amoledgallery.ui.design.MenuEntry
import com.example.amoledgallery.ui.design.ThemedIconButton
import com.example.amoledgallery.ui.design.ThemedPopupMenu
import com.example.amoledgallery.ui.design.ThemedTopBar
import com.example.amoledgallery.ui.design.bottomBarContentPadding
import com.example.amoledgallery.ui.design.glass
import com.example.amoledgallery.ui.design.pressScale
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent
import com.example.amoledgallery.ui.viewmodel.MediaFilter
import com.example.amoledgallery.util.DateUtils
import dev.chrisbanes.haze.HazeState
import com.example.amoledgallery.ui.design.HazeSource
import com.example.amoledgallery.ui.design.MediaZoomTransition
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


/**
 * The "Bilder" media grid, laid out per design language:
 * - One UI: Samsung Gallery's expanded app bar (big centred title in the upper third), a frosted
 *   pinned bar once scrolled, filters in the ⋮ popup, and One UI 9's floating frosted selection card.
 * - Material 3 Expressive: headline, a hero card with the newest photo, filter chips, rounded tiles.
 * - iOS Photos ("Mediathek"): oldest photos at the top and the newest at the bottom, opening at the
 *   bottom; no date headers; photos run edge-to-edge under a progressive glass title with a filter
 *   button and "Auswählen".
 * - HyperOS: light layout, text-tab filters, compact frosted bars.
 *
 * Grid behaviour (pinch zoom, long-press drag selection, fast scroller, detail overlay that keeps the
 * LazyGridState intact) is shared by all of them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    items: List<MediaItem>,
    isLoading: Boolean,
    columnCount: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onItemClick: (MediaItem, Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = strings.tabPictures,
    selectedFilter: MediaFilter = MediaFilter.ALL,
    onFilterSelected: ((MediaFilter) -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    // Overlay detail viewer state
    selectedDetailIndex: Int? = null,
    onCloseDetail: () -> Unit = {},
    onDetailIndexChanged: (Int) -> Unit = {},
    onTrashItem: (MediaItem) -> Unit = {},
    onRenameItem: (MediaItem, String) -> Unit = { _, _ -> },
    isFavorite: (Long) -> Boolean = { false },
    onToggleFavorite: (Long) -> Unit = {},
    // Multi-select state
    isSelectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onStartSelection: (Long) -> Unit = {},
    /** Enters selection mode with nothing selected yet (iOS "Auswählen"). */
    onEnterSelection: () -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onSetSelection: (Long, Boolean) -> Unit = { _, _ -> },
    onSetSelections: (Collection<Long>, Boolean) -> Unit = { _, _ -> },
    onUpdateSelectedIds: (Set<Long>) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    // Move / delete for a selection live in the SelectionActionBar that replaces the tab bar.
    onMoveClick: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    graphicsQuality: com.example.amoledgallery.PerformanceProfile = com.example.amoledgallery.PerformanceProfile.MEDIUM
) {
    // Intercept back button if in selection mode and detail overlay is not open
    if (isSelectionMode && selectedDetailIndex == null) {
        BackHandler {
            onClearSelection()
        }
    }

    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language
    val isIos = language == DesignLanguage.IOS_LIQUID_GLASS
    // iOS Photos shows its library in chronological order: oldest first, the newest photo last. The grid,
    // drag selection and the fullscreen viewer all work on this displayed order.
    val displayItems = remember(items, isIos) { if (isIos) items.asReversed() else items }
    val screenTitle = if (isIos && onBackClick == null) strings.tabLibrary else title
    val countText = if (isIos) strings.objects(items.size) else strings.items(items.size)
    // While the fullscreen viewer covers everything, the grid below it is not drawn at all.
    var gridCoveredByViewer by remember { mutableStateOf(false) }

    // Changing the filter lets the new tiles rise and fade in, so the switch is visibly acknowledged
    // instead of the grid being swapped out in a single frame. Drawn in graphicsLayer: no recomposition.
    val filterAnim = remember { androidx.compose.animation.core.Animatable(1f) }
    val lastFilter = remember { arrayOf(selectedFilter) }
    LaunchedEffect(selectedFilter) {
        if (lastFilter[0] != selectedFilter) {
            lastFilter[0] = selectedFilter
            filterAnim.snapTo(0f)
            filterAnim.animateTo(
                1f,
                androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }
    }
    val filterSwitchModifier = Modifier.graphicsLayer {
        val p = filterAnim.value
        alpha = 0.2f + 0.8f * p
        translationY = (1f - p) * 18.dp.toPx()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = palette.accent)
            }
        } else if (items.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (onBackClick != null) {
                    ThemedTopBar(title = screenTitle, onBack = onBackClick)
                } else {
                    LargeHeader(title = screenTitle)
                }

                if (onFilterSelected != null) {
                    FilterChips(
                        selectedFilter = selectedFilter,
                        onFilterSelected = onFilterSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isIos) 20.dp else 0.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.emptyGallery,
                        color = palette.textMuted,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            // Group items into date sections (Day for columns 2-3, Month for 4-5). iOS shows one continuous
            // grid without headers.
            val dateSections = remember(displayItems, columnCount, LanguageState.language, isIos) {
                if (isIos) listOf(MediaDateSection("", displayItems)) else DateUtils.groupMediaByDate(displayItems, columnCount)
            }

            val gridSpacing = theme.shapes.gridSpacing
            val gridPadding = theme.shapes.gridHorizontalPadding

            // Preserved in memory permanently because GalleryScreen stays composed! iOS opens at the newest
            // photo, i.e. at the very bottom (an index past the end is clamped to the last page).
            val gridState = remember(isIos) {
                LazyGridState(firstVisibleItemIndex = if (isIos) displayItems.size else 0)
            }
            val coroutineScope = rememberCoroutineScope()
            val density = LocalDensity.current

            // iOS: a different filter starts again at the newest photos at the bottom.
            if (isIos) {
                LaunchedEffect(selectedFilter) {
                    gridState.scrollToItem(displayItems.size)
                }
            }

            // 0f = expanded large header, 1f = scrolled far enough that the compact bar takes over.
            val collapseDistancePx = with(density) {
                (if (language == DesignLanguage.SAMSUNG_ONEUI) 220.dp else 110.dp).toPx()
            }
            val headerCollapseFraction by remember(collapseDistancePx) {
                derivedStateOf {
                    if (gridState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        (gridState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
                    }
                }
            }
            val showPinnedBar by remember { derivedStateOf { headerCollapseFraction > 0.25f } }

            val haptic = LocalHapticFeedback.current
            val context = LocalContext.current

            // Backdrop for this screen's own top bars (they sit inside the tab content, so they can't blur
            // the tab-level backdrop they are part of).
            val gridHaze = remember { HazeState() }
            var viewMenuOpen by remember { mutableStateOf(false) }
            var viewMenuAnchor by remember { mutableStateOf<Rect?>(null) }

            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val iosHeaderHeight = statusBarTop + 84.dp
            val bottomPadding = bottomBarContentPadding(extra = 12.dp)

            // Compose's low-level long-press-drag detector (used for selection) and each tile's
            // plain `clickable` both observe the same raw touch stream. A press-then-release with
            // no movement is recognized by BOTH: the long-press already selects the item, and then
            // the tile's own clickable ALSO fires as a completed tap and immediately toggles it
            // back off - a genuine race, not something tied to any particular tile/position. This
            // records which item a long-press just (de)selected so the trailing click for that same
            // item can be ignored as the tail end of the same gesture rather than a fresh tap.
            var lastLongPressId by remember { mutableStateOf<Long?>(null) }
            var lastLongPressTime by remember { mutableLongStateOf(0L) }
            var isLongPressGestureActive by remember { mutableStateOf(false) }

            // Pause background thumbnail pre-indexing while actively flinging so it never competes
            // with visible-tile decode threads, resume it as soon as the grid settles.
            // Observed outside composition, so starting and stopping a fling doesn't recompose the screen.
            LaunchedEffect(gridState, items) {
                val thumbnailManager = ThumbnailManager.getInstance(context)
                androidx.compose.runtime.snapshotFlow { gridState.isScrollInProgress }.collect { scrolling ->
                    if (scrolling) {
                        thumbnailManager.pausePreindexing()
                    } else {
                        thumbnailManager.resumePreindexing(items)
                    }
                }
            }

            val openViewMenu: (Rect?) -> Unit = { anchor ->
                viewMenuAnchor = anchor
                viewMenuOpen = true
            }

            HazeSource(
                gridHaze,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (gridCoveredByViewer) 0f else 1f }
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .detectPinchToZoomGrid(
                            onZoomIn = onZoomIn,
                            onZoomOut = onZoomOut
                        )
                        .detectDragSelect(
                            items = displayItems,
                            gridState = gridState,
                            isSelectionMode = { isSelectionMode },
                            onStartSelection = onStartSelection,
                            onSetSelection = onSetSelection,
                            onUpdateSelectedIds = onUpdateSelectedIds,
                            selectedIds = { selectedIds },
                            haptic = haptic,
                            coroutineScope = coroutineScope,
                            onLongPressRecognized = { id ->
                                lastLongPressId = id
                                isLongPressGestureActive = true
                            },
                            onLongPressEnded = {
                                lastLongPressTime = System.currentTimeMillis()
                                isLongPressGestureActive = false
                            }
                        ),
                    contentPadding = PaddingValues(
                        start = gridPadding,
                        end = gridPadding,
                        top = when {
                            onBackClick != null -> statusBarTop + 64.dp
                            isIos -> iosHeaderHeight
                            else -> 0.dp
                        },
                        bottom = bottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
                    // Index 0 is always this header item - getGridItemIndexForMedia relies on it.
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "grid_top_header",
                        contentType = "header"
                    ) {
                        if (onBackClick == null) {
                            GalleryHeader(
                                title = screenTitle,
                                countText = countText,
                                items = items,
                                language = language,
                                collapseFraction = { headerCollapseFraction },
                                selectedFilter = selectedFilter,
                                onFilterSelected = onFilterSelected,
                                onOpenViewMenu = openViewMenu,
                                onHeroClick = { onItemClick(items.first(), displayItems.indexOf(items.first())) },
                                hazeState = gridHaze,
                                isSelectionMode = isSelectionMode,
                                selectedCount = selectedIds.size,
                                allSelected = items.isNotEmpty() && selectedIds.size == items.size,
                                onToggleSelectAll = { if (items.isNotEmpty() && selectedIds.size == items.size) onDeselectAll() else onSelectAll() },
                                onCancelSelection = onClearSelection
                            )
                        } else {
                            Spacer(modifier = Modifier.height(0.dp))
                        }
                    }

                    // Date sections with full-span headers and optimized media tiles
                    dateSections.forEach { section ->
                        if (section.title.isNotEmpty()) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "header_${section.title}_$columnCount",
                                contentType = "date_header"
                            ) {
                                // HyperOS: each day can be selected or deselected as a whole while selecting.
                                val sectionSelectable = isSelectionMode &&
                                    (language == DesignLanguage.XIAOMI_HYPEROS || language == DesignLanguage.SAMSUNG_ONEUI)
                                val sectionAllSelected = sectionSelectable && section.items.all { selectedIds.contains(it.id) }
                                DateHeader(
                                    title = section.title,
                                    modifier = filterSwitchModifier,
                                    actionText = if (!sectionSelectable) null else if (sectionAllSelected) strings.deselectAllItems else strings.selectAllItems,
                                    onAction = { onSetSelections(section.items.map { it.id }, !sectionAllSelected) },
                                    actionChecked = sectionAllSelected
                                )
                            }
                        }

                        // Keyed items with fixed dimensions, contentType="media_tile", and clipToBounds for zero-lag recycling
                        items(
                            items = section.items,
                            key = { it.id },
                            contentType = { "media_tile" }
                        ) { item ->
                            MediaGridItem(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedIds.contains(item.id),
                                isFavorite = isFavorite(item.id),
                                onClick = {
                                    // Suppressed for the entire duration of an active long-press-drag on
                                    // this same item (covers holds of any length), plus a short grace
                                    // period after it ends as a backstop in case the child's tap and the
                                    // parent's onDragEnd/onDragCancel resolve in the opposite order.
                                    val isTrailingClickFromLongPress = item.id == lastLongPressId &&
                                        (isLongPressGestureActive || (System.currentTimeMillis() - lastLongPressTime) < 600)
                                    if (isTrailingClickFromLongPress) {
                                        // Same press-release the long-press gesture already handled -
                                        // ignore it instead of letting the tile's own tap detector
                                        // immediately toggle the selection it just made back off.
                                    } else if (isSelectionMode) {
                                        onToggleSelection(item.id)
                                    } else {
                                        onItemClick(item, displayItems.indexOf(item))
                                    }
                                },
                                modifier = Modifier
                                    .clipToBounds()
                                    .then(filterSwitchModifier)
                            )
                        }
                    }
                }
            }

            val allSelected = items.isNotEmpty() && selectedIds.size == items.size
            val toggleSelectAll = { if (allSelected) onDeselectAll() else onSelectAll() }
            var selectionMenuOpen by remember { mutableStateOf(false) }
            var selectionMenuAnchor by remember { mutableStateOf<Rect?>(null) }

            if (isIos && onBackClick == null) {
                // iOS keeps the Mediathek title while selecting; only its buttons change.
                IosTitleOverlay(
                    title = screenTitle,
                    subtitle = countText,
                    height = iosHeaderHeight,
                    hazeState = gridHaze,
                    selectionMode = isSelectionMode,
                    onOpenFilter = openViewMenu,
                    onSelect = onEnterSelection,
                    onOpenSelectionMenu = { anchor ->
                        selectionMenuAnchor = anchor
                        selectionMenuOpen = true
                    },
                    onEndSelection = onClearSelection,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            } else if (!isSelectionMode) {
                when {
                    onBackClick != null -> ThemedTopBar(
                        title = screenTitle,
                        subtitle = countText,
                        onBack = onBackClick,
                        hazeState = gridHaze,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    showPinnedBar -> ThemedTopBar(
                        title = screenTitle,
                        hazeState = gridHaze,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer { alpha = ((headerCollapseFraction - 0.25f) / 0.75f).coerceIn(0f, 1f) }
                    ) {
                        var anchor by remember { mutableStateOf<Rect?>(null) }
                        ThemedIconButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = strings.options,
                            onClick = { openViewMenu(anchor) },
                            hazeState = gridHaze,
                            modifier = Modifier.onGloballyPositioned { anchor = it.boundsInRoot() }
                        )
                    }
                }
            } else if (language != DesignLanguage.SAMSUNG_ONEUI || (showPinnedBar && onBackClick == null)) {
                // One UI shows the selection in the big header; this compact bar only takes over once scrolled.
                SelectionTopBar(
                    selectedCount = selectedIds.size,
                    totalCount = items.size,
                    onToggleSelectAll = toggleSelectAll,
                    onClose = onClearSelection,
                    hazeState = gridHaze,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            if (language == DesignLanguage.SAMSUNG_ONEUI) alpha = ((headerCollapseFraction - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        }
                )
            }

            // iOS selection: the ⋯ in the top capsule.
            ThemedPopupMenu(
                expanded = selectionMenuOpen,
                onDismissRequest = { selectionMenuOpen = false },
                anchorBounds = selectionMenuAnchor,
                entries = buildList {
                    add(MenuEntry(if (allSelected) strings.deselectAllItems else strings.selectAllItems, icon = Icons.Rounded.CheckCircle) { toggleSelectAll() })
                    if (selectedIds.isNotEmpty()) {
                        add(MenuEntry(strings.move, icon = Icons.AutoMirrored.Rounded.DriveFileMove) { onMoveClick() })
                    }
                }
            )

            ThemedPopupMenu(
                expanded = viewMenuOpen,
                onDismissRequest = { viewMenuOpen = false },
                anchorBounds = viewMenuAnchor,
                entries = buildList {
                    if (language == DesignLanguage.SAMSUNG_ONEUI) {
                        add(MenuEntry(strings.select) { onEnterSelection() })
                    }
                    // One UI keeps its filters in the ⋮ popup, iOS in the filter button's menu.
                    if (onFilterSelected != null && (language == DesignLanguage.SAMSUNG_ONEUI || isIos || language == DesignLanguage.GOOGLE_EXPRESSIVE)) {
                        MediaFilter.entries.forEach { filter ->
                            add(MenuEntry(filter.displayLabel, checked = filter == selectedFilter) { onFilterSelected(filter) })
                        }
                    }
                    add(MenuEntry(strings.largerThumbnails, icon = Icons.Rounded.ZoomIn) { onZoomIn() })
                    add(MenuEntry(strings.smallerThumbnails, icon = Icons.Rounded.ZoomOut) { onZoomOut() })
                    // Days, months or years at a glance (pinching also moves between them).
                    if (!isIos) {
                        add(MenuEntry(com.example.amoledgallery.ui.i18n.F.viewDays, checked = columnCount <= 3, group = 2) { com.example.amoledgallery.ui.features.Features.env?.vm?.setColumnCount(3) })
                        add(MenuEntry(com.example.amoledgallery.ui.i18n.F.viewMonths, checked = columnCount in 4..5, group = 2) { com.example.amoledgallery.ui.features.Features.env?.vm?.setColumnCount(5) })
                        add(MenuEntry(com.example.amoledgallery.ui.i18n.F.viewYears, checked = columnCount >= 6, group = 2) { com.example.amoledgallery.ui.features.Features.env?.vm?.setColumnCount(7) })
                    }
                    val cameraOnly = com.example.amoledgallery.ui.features.Features.env?.vm?.cameraOnly?.value == true
                    add(MenuEntry(com.example.amoledgallery.ui.i18n.F.cameraOnly, icon = Icons.Outlined.PhotoCamera, checked = cameraOnly, group = 3) { com.example.amoledgallery.ui.features.Features.env?.vm?.setCameraOnly(!cameraOnly) })
                    add(MenuEntry(com.example.amoledgallery.ui.i18n.F.search, icon = Icons.Outlined.Search, group = 3) {
                        com.example.amoledgallery.ui.features.Features.open(com.example.amoledgallery.ui.features.FeatureScreen.Search)
                    })
                    if (onFilterSelected != null && language == DesignLanguage.XIAOMI_HYPEROS) {
                        MediaFilter.entries.forEach { filter ->
                            val icon = when (filter) {
                                MediaFilter.ALL -> Icons.Outlined.PhotoLibrary
                                MediaFilter.IMAGES -> Icons.Outlined.Image
                                MediaFilter.VIDEOS -> Icons.Outlined.Videocam
                                MediaFilter.GIFS -> Icons.Outlined.Gif
                            }
                            add(MenuEntry(filter.displayLabel, icon = icon, checked = filter == selectedFilter, group = 1) { onFilterSelected(filter) })
                        }
                    }
                }
            )

            // Draggable Fast-Scroll Scrubber with Month & Year pill
            if (items.size > 20 && !isSelectionMode) {
                // Grid index -> media shown there (date headers map to their section's first item), for the
                // scrubber's month pill.
                val gridMedia = remember(dateSections) {
                    buildList<MediaItem?> {
                        add(null)
                        dateSections.forEach { section ->
                            if (section.title.isNotEmpty()) add(section.items.firstOrNull())
                            addAll(section.items)
                        }
                    }
                }
                FastScroller(
                    gridState = gridState,
                    labelForGridIndex = { index ->
                        (gridMedia.getOrNull(index) ?: gridMedia.getOrNull(index + 1))
                            ?.let { DateUtils.formatMonthYear(it.date) } ?: ""
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = statusBarTop + 80.dp, bottom = bottomPadding)
                )
            }
        }

        // FULLSCREEN OVERLAY: Media Detail Viewer
        // Overlaid directly onto the LazyVerticalGrid without recreating GalleryScreen.
        // Preserves LazyGridState scroll position down to the exact pixel when dismissed!
        val detailIndexHolder = remember { IntArray(1) }
        selectedDetailIndex?.let { detailIndexHolder[0] = it }
        MediaZoomTransition(
            visible = selectedDetailIndex != null,
            onCoveringChange = { gridCoveredByViewer = it }
        ) {
                MediaDetailScreen(
                    mediaList = displayItems,
                    initialIndex = detailIndexHolder[0],
                    onBack = onCloseDetail,
                    onTrashItem = onTrashItem,
                    onRenameItem = onRenameItem,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onPageChanged = onDetailIndexChanged
                )
        }
    }
}

/** The scrolling header at the top of the grid (index 0), per design language. */
@Composable
private fun GalleryHeader(
    title: String,
    countText: String,
    items: List<MediaItem>,
    language: DesignLanguage,
    collapseFraction: () -> Float,
    selectedFilter: MediaFilter,
    onFilterSelected: ((MediaFilter) -> Unit)?,
    onOpenViewMenu: (Rect?) -> Unit,
    onHeroClick: () -> Unit,
    hazeState: HazeState,
    isSelectionMode: Boolean = false,
    selectedCount: Int = 0,
    allSelected: Boolean = false,
    onToggleSelectAll: () -> Unit = {},
    onCancelSelection: () -> Unit = {}
) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val menuButton: @Composable RowScope.() -> Unit = {
        ThemedIconButton(
            icon = Icons.Rounded.MoreVert,
            contentDescription = strings.options,
            onClick = { onOpenViewMenu(anchor) },
            hazeState = hazeState,
            modifier = Modifier.onGloballyPositioned { anchor = it.boundsInRoot() }
        )
    }

    when (language) {
        // One UI 8 selection: the big title becomes "14 ausgewählt", with "Alle" and "Abbrechen" below it.
        DesignLanguage.SAMSUNG_ONEUI -> if (isSelectionMode) {
            val palette = LocalAppTheme.current.palette
            LargeHeader(
                title = strings.selectionTitle(selectedCount),
                collapseFraction = collapseFraction,
                tall = true,
                actions = {
                    OneUiSelectAll(checked = allSelected, onClick = onToggleSelectAll, modifier = Modifier.padding(start = 12.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = strings.cancel,
                        color = palette.onBackground,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onCancelSelection)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            )
        } else {
            LargeHeader(
                title = title,
                collapseFraction = collapseFraction,
                tall = true,
                actions = menuButton
            )
        }

        // Google Photos without the highlights: the title chip, then straight into the photos. Filters are in ⋮.
        DesignLanguage.GOOGLE_EXPRESSIVE -> LargeHeader(title = title, subtitle = countText, actions = menuButton)

        // HyperOS keeps its view choice (all / photos / videos / GIFs) in the ⋮ menu's second card.
        DesignLanguage.XIAOMI_HYPEROS -> LargeHeader(title = title, collapseFraction = collapseFraction, actions = menuButton)

        // iOS draws its title as a fixed glass overlay instead (IosTitleOverlay).
        DesignLanguage.IOS_LIQUID_GLASS -> Spacer(modifier = Modifier.height(0.dp))
    }
}

/**
 * iOS 26 Photos "Mediathek" title: pinned over the photos, which scroll underneath a progressive glass
 * blur that is fully frosted at the top and fades out towards the grid, plus a legibility gradient. The
 * large title and the item count sit on the left, a glass filter button and a glass "Auswählen" capsule
 * on the right.
 */
@Composable
private fun IosTitleOverlay(
    title: String,
    subtitle: String,
    height: Dp,
    hazeState: HazeState,
    /** Selection mode: the filter and ⋯ share a glass capsule next to a close circle, as in iOS Photos. */
    selectionMode: Boolean,
    onOpenFilter: (Rect?) -> Unit,
    onSelect: () -> Unit,
    onOpenSelectionMenu: (Rect?) -> Unit,
    onEndSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppTheme.current.palette
    val light = palette.isLight
    val shade = if (light) Color.White else Color.Black
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height + 36.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glass(
                    RectangleShape,
                    GlassSpec(shade.copy(alpha = 0.22f), Color.Transparent, 14.dp, GlassRim.None),
                    hazeState,
                    mask = Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent)
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to shade.copy(alpha = if (light) 0.82f else 0.66f),
                        0.6f to shade.copy(alpha = if (light) 0.36f else 0.28f),
                        1f to Color.Transparent
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = palette.onBackground,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = palette.onBackground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            var anchor by remember { mutableStateOf<Rect?>(null) }
            if (selectionMode) {
                var moreAnchor by remember { mutableStateOf<Rect?>(null) }
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .glass(CircleShape, barGlass(), hazeState)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CapsuleIcon(
                        icon = Icons.Rounded.FilterList,
                        description = strings.options,
                        modifier = Modifier.onGloballyPositioned { anchor = it.boundsInRoot() }
                    ) { onOpenFilter(anchor) }
                    CapsuleIcon(
                        icon = Icons.Rounded.MoreHoriz,
                        description = strings.options,
                        modifier = Modifier.onGloballyPositioned { moreAnchor = it.boundsInRoot() }
                    ) { onOpenSelectionMenu(moreAnchor) }
                }
                Spacer(modifier = Modifier.width(10.dp))
                ThemedIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = strings.endSelection,
                    onClick = onEndSelection,
                    hazeState = hazeState
                )
            } else {
                ThemedIconButton(
                    icon = Icons.Rounded.FilterList,
                    contentDescription = strings.options,
                    onClick = { onOpenFilter(anchor) },
                    hazeState = hazeState,
                    modifier = Modifier.onGloballyPositioned { anchor = it.boundsInRoot() }
                )
                Spacer(modifier = Modifier.width(10.dp))
                GlassTextButton(strings.select, hazeState, prominent = false, onClick = onSelect)
            }
        }
    }
}

@Composable
private fun GlassTextButton(text: String, hazeState: HazeState, prominent: Boolean, onClick: () -> Unit) {
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    val shape = CircleShape
    Box(
        modifier = Modifier
            .height(44.dp)
            .then(
                if (prominent) Modifier.clip(shape).background(palette.accent) else Modifier.glass(shape, state = hazeState)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (prominent) palette.onAccent else palette.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** An icon button inside a glass capsule (the capsule itself provides the glass). */
@Composable
private fun CapsuleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(icon, description, tint = palette.onBackground, modifier = Modifier.size(22.dp))
    }
}

