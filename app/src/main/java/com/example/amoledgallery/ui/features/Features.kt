package com.example.amoledgallery.ui.features

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.MediaGridItem
import com.example.amoledgallery.ui.design.BottomEdgeFade
import com.example.amoledgallery.ui.design.HazeSource
import com.example.amoledgallery.ui.design.MediaZoomTransition
import com.example.amoledgallery.ui.design.ThemedTopBar
import com.example.amoledgallery.ui.design.blockTouchesBelow
import com.example.amoledgallery.ui.design.pushTransition
import com.example.amoledgallery.ui.design.screenBackground
import com.example.amoledgallery.ui.screens.MediaDetailScreen
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.viewmodel.GalleryViewModel
import dev.chrisbanes.haze.HazeState

/** The pages the 1.2 tools open over the gallery. */
sealed interface FeatureScreen {
    data object Tools : FeatureScreen
    data object Search : FeatureScreen
    data object Places : FeatureScreen
    data class Place(val name: String, val items: List<MediaItem>) : FeatureScreen
    data object OnThisDay : FeatureScreen
    data object PrivateAlbum : FeatureScreen
    data object OtherAlbums : FeatureScreen
    data class AlbumGroup(val name: String) : FeatureScreen
    data object Similar : FeatureScreen
    data object CleanUp : FeatureScreen
    data class CleanCategory(val kind: CleanKind) : FeatureScreen
    data class Create(val kind: CreateKind) : FeatureScreen
    data object SlideshowWallpaper : FeatureScreen
}

/** Dialogs and full-screen overlays the tools show on top of whatever is open. */
sealed interface FeatureDialog {
    data class Slideshow(val items: List<MediaItem>, val start: Int) : FeatureDialog
    data class EditDate(val item: MediaItem) : FeatureDialog
    data class EditLocation(val item: MediaItem) : FeatureDialog
    data class Share(val items: List<MediaItem>) : FeatureDialog
    data class CopyToAlbum(val items: List<MediaItem>) : FeatureDialog
    data object PhotoWidgetHelp : FeatureDialog
}

/**
 * Opens tool pages and dialogs from anywhere (menus, the viewer, the selection bar) without passing callbacks
 * through every design's screens. [FeatureHost] shows them.
 */
object Features {
    val screens = mutableStateListOf<FeatureScreen>()

    /** The gallery's hooks, for tools started outside the host (the viewer's and the selection's menus). */
    var env: FeatureEnv? = null
        internal set
    var dialog by mutableStateOf<FeatureDialog?>(null)

    fun open(screen: FeatureScreen) {
        screens.add(screen)
    }

    fun show(dialog: FeatureDialog) {
        this.dialog = dialog
    }

    fun back() {
        if (screens.isNotEmpty()) screens.removeAt(screens.lastIndex)
    }

    fun closeDialog() {
        dialog = null
    }
}

/** What the tool pages need from the gallery. */
class FeatureEnv(val vm: GalleryViewModel)

val LocalFeatureEnv = staticCompositionLocalOf<FeatureEnv> { Features.env ?: error("FeatureHost missing") }

/** Shows the open tool pages (stacked, newest on top) and the open dialog. */
@Composable
fun FeatureHost(viewModel: GalleryViewModel) {
    val env = remember(viewModel) { FeatureEnv(viewModel).also { Features.env = it } }
    CompositionLocalProvider(LocalFeatureEnv provides env) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (Features.screens.isNotEmpty()) BackHandler { Features.back() }
            Features.screens.forEachIndexed { index, screen ->
                // Keyed by position, so a page keeps its state while another one is pushed over it.
                androidx.compose.runtime.key(index, screen) {
                    PushedPage { FeatureScreenContent(screen) }
                }
            }
            Features.dialog?.let { FeatureDialogContent(it) }
        }
    }
}

/** A page sliding in the design's way and covering what is below it. */
@Composable
private fun PushedPage(content: @Composable () -> Unit) {
    val language = LocalAppTheme.current.language
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(320)) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pushTransition(language, { progress.value }, isTop = true)
            .screenBackground()
            .blockTouchesBelow()
    ) {
        content()
    }
}

@Composable
private fun FeatureScreenContent(screen: FeatureScreen) {
    when (screen) {
        FeatureScreen.Tools -> ToolsHubScreen()
        FeatureScreen.Search -> SearchScreen()
        FeatureScreen.Places -> PlacesScreen()
        is FeatureScreen.Place -> MediaListPage(screen.name, screen.items)
        FeatureScreen.OnThisDay -> OnThisDayScreen()
        FeatureScreen.PrivateAlbum -> PrivateAlbumScreen()
        FeatureScreen.OtherAlbums -> OtherAlbumsScreen()
        is FeatureScreen.AlbumGroup -> AlbumGroupScreen(screen.name)
        FeatureScreen.Similar -> SimilarScreen()
        FeatureScreen.CleanUp -> CleanUpScreen()
        is FeatureScreen.CleanCategory -> CleanCategoryScreen(screen.kind)
        is FeatureScreen.Create -> CreateScreen(screen.kind)
        FeatureScreen.SlideshowWallpaper -> SlideshowWallpaperScreen()
    }
}

@Composable
private fun FeatureDialogContent(dialog: FeatureDialog) {
    when (dialog) {
        is FeatureDialog.Slideshow -> SlideshowOverlay(dialog.items, dialog.start)
        is FeatureDialog.EditDate -> EditDateDialog(dialog.item)
        is FeatureDialog.EditLocation -> EditLocationDialog(dialog.item)
        is FeatureDialog.Share -> ShareOptionsSheet(dialog.items)
        is FeatureDialog.CopyToAlbum -> CopyToAlbumSheet(dialog.items)
        FeatureDialog.PhotoWidgetHelp -> PhotoWidgetHelpDialog()
    }
}

// ---------------------------------------------------------------------------------------------
// Shared page parts
// ---------------------------------------------------------------------------------------------

/** Space above the first row: status bar plus the top bar (and Gemini's fade below it). */
@Composable
fun featureTopPadding(extra: Dp = 0.dp): Dp {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bar = if (LocalAppTheme.current.language == DesignLanguage.GOOGLE_EXPRESSIVE) 96.dp else 72.dp
    return statusBarTop + bar + extra
}

@Composable
fun featureBottomPadding(extra: Dp = 24.dp): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + extra

/**
 * A tool page: [content] scrolls under the design's top bar (which blurs it), back closes the page.
 * [bottomBar] floats over the content at the bottom (e.g. a "delete selected" button).
 */
@Composable
fun FeaturePage(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit = { Features.back() },
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (haze: HazeState) -> Unit
) {
    val haze = remember { HazeState() }
    Box(modifier = Modifier.fillMaxSize()) {
        HazeSource(haze, Modifier.fillMaxSize()) { content(haze) }
        ThemedTopBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            hazeState = haze,
            modifier = Modifier.align(Alignment.TopCenter),
            actions = actions
        )
        BottomEdgeFade(Modifier.align(Alignment.BottomCenter))
        if (bottomBar != null) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        }
    }
}

/** A titled group of photos in a [MediaSectionGrid]. */
data class MediaSection(val title: String?, val items: List<MediaItem>)

/**
 * Photos in sections, four to a row. A tap opens the viewer over the page, or - with [selection] - ticks the
 * photo instead.
 */
@Composable
fun MediaSectionGrid(
    sections: List<MediaSection>,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    topPadding: Dp = featureTopPadding(),
    bottomPadding: Dp = featureBottomPadding(),
    selection: Set<Long>? = null,
    onToggle: (MediaItem) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    sectionBadge: ((MediaSection, MediaItem) -> String?)? = null
) {
    val theme = LocalAppTheme.current
    val env = LocalFeatureEnv.current
    val favoriteIds by env.vm.favoriteIds.collectAsStateWithLifecycle()
    val all = remember(sections) { sections.flatMap { it.items } }
    var openIndex by remember { mutableStateOf<Int?>(null) }
    var covered by remember { mutableStateOf(false) }

    // More, smaller tiles on wide screens (landscape), about as large as in portrait.
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    LazyVerticalGrid(
        columns = GridCells.Fixed(maxOf(columns, screenWidth / 110)),
        modifier = modifier.fillMaxSize().graphicsLayer { alpha = if (covered) 0f else 1f },
        contentPadding = PaddingValues(
            start = theme.shapes.gridHorizontalPadding,
            end = theme.shapes.gridHorizontalPadding,
            top = topPadding,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(theme.shapes.gridSpacing),
        verticalArrangement = Arrangement.spacedBy(theme.shapes.gridSpacing)
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        var offset = 0
        sections.forEach { section ->
            val start = offset
            if (section.title != null) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    Text(
                        section.title,
                        color = theme.palette.onBackground,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 8.dp)
                    )
                }
            }
            val sectionItems = section.items
            items(sectionItems.size, key = { "${section.title}_${sectionItems[it].id}" }, contentType = { "media_tile" }) { i ->
                val item = sectionItems[i]
                Box {
                    MediaGridItem(
                        item = item,
                        isSelectionMode = selection != null,
                        isSelected = selection?.contains(item.id) == true,
                        isFavorite = item.id in favoriteIds,
                        onClick = { if (selection != null) onToggle(item) else openIndex = start + i },
                        modifier = Modifier.aspectRatio(1f).clipToBounds()
                    )
                    sectionBadge?.invoke(section, item)?.let { badge ->
                        FeatureBadge(badge, Modifier.align(Alignment.BottomStart).padding(6.dp))
                    }
                }
            }
            offset += sectionItems.size
        }
    }

    FeatureViewer(all, openIndex, onClose = { openIndex = null }, onCoveringChange = { covered = it })
}

/**
 * The fullscreen viewer for photos shown on a tool page. It opens as an overlay over the whole window, so it
 * covers the page whatever layout the grid sits in (the search field above it, for instance).
 */
@Composable
fun FeatureViewer(items: List<MediaItem>, index: Int?, onClose: () -> Unit, onCoveringChange: (Boolean) -> Unit = {}) {
    val env = LocalFeatureEnv.current
    val favoriteIds by env.vm.favoriteIds.collectAsStateWithLifecycle()
    val holder = remember { IntArray(1) }
    index?.let { holder[0] = it }
    LaunchedEffect(index) { onCoveringChange(index != null) }
    if (index == null || items.isEmpty()) return
    com.example.amoledgallery.ui.design.OverlayWindow { transition ->
        val alpha by transition.animateFloat(label = "feature_viewer") { if (it) 1f else 0f }
        BackHandler { onClose() }
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
            MediaDetailScreen(
                mediaList = items,
                initialIndex = holder[0].coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                onBack = onClose,
                onTrashItem = { env.vm.trashMedia(it) },
                onRenameItem = { item, name -> env.vm.renameMedia(item, name) },
                isFavorite = { it in favoriteIds },
                onToggleFavorite = { env.vm.toggleFavorite(it) }
            )
        }
    }
}

@Composable
fun FeatureBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp)
    }
}

/** A simple page of photos (a place, a search result set). */
@Composable
fun MediaListPage(title: String, items: List<MediaItem>) {
    // The list was made when the page opened. Seen through the library as it is now, a photo deleted or renamed
    // from here leaves the page (or shows its new name) instead of staying behind as an empty tile.
    val media by LocalFeatureEnv.current.vm.rawMedia.collectAsStateWithLifecycle()
    val live = remember(items, media) {
        val byId = media.associateBy { it.id }
        items.mapNotNull { byId[it.id] }
    }
    FeaturePage(title = title, subtitle = com.example.amoledgallery.ui.i18n.strings.items(live.size)) {
        MediaSectionGrid(listOf(MediaSection(null, live)))
    }
}
