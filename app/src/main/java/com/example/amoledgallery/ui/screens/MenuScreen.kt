package com.example.amoledgallery.ui.screens
import com.example.amoledgallery.ui.i18n.AppLanguage
import com.example.amoledgallery.ui.i18n.LanguageState
import com.example.amoledgallery.ui.i18n.strings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import com.example.amoledgallery.util.WallpaperAccents
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.PerformanceProfile
import com.example.amoledgallery.ui.components.HsvColorPicker
import com.example.amoledgallery.ui.components.LargeHeader
import com.example.amoledgallery.ui.design.DialogButton
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.ExpressiveCookieShape
import com.example.amoledgallery.ui.design.HazeSource
import com.example.amoledgallery.ui.design.ListRow
import com.example.amoledgallery.ui.design.ThemedCard
import com.example.amoledgallery.ui.design.ThemedDialog
import com.example.amoledgallery.ui.design.ThemedIconButton
import com.example.amoledgallery.ui.design.ThemedListSection
import com.example.amoledgallery.ui.design.ThemedProgressBar
import com.example.amoledgallery.ui.design.ThemedRadioIndicator
import com.example.amoledgallery.ui.design.ThemedSectionHeader
import com.example.amoledgallery.ui.design.ThemedSegmentedControl
import com.example.amoledgallery.ui.design.ThemedSheet
import com.example.amoledgallery.ui.design.ThemedTopBar
import com.example.amoledgallery.ui.design.bottomBarContentPadding
import com.example.amoledgallery.ui.design.pressScale
import com.example.amoledgallery.ui.design.selectionPop
import com.example.amoledgallery.ui.design.pushSpec
import com.example.amoledgallery.ui.design.pushTransition
import com.example.amoledgallery.ui.design.screenBackground
import com.example.amoledgallery.ui.design.blockTouchesBelow
import com.example.amoledgallery.ui.theme.AppColorPalette
import com.example.amoledgallery.ui.theme.CUSTOM_PALETTE_ID
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.ThemeState
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent
import com.example.amoledgallery.ui.theme.presetsFor
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** The app's full name, shown on the info pages; the launcher label (app_name) is the same. */
internal const val APP_FULL_NAME = "Prism Gallery"

/** Sections of the settings sheet; every menu entry opens the sheet scrolled to its own section. */
enum class SettingsSection { DESIGN, SETTINGS, STORAGE, INFO }

/**
 * The "Menü" tab, built the way each OS would build it:
 * - One UI: huge centred title, two big shortcut tiles, a list card with blue value summaries.
 * - Material 3 Expressive: a tonal storage card with the wavy progress indicator, segmented lists with
 *   cookie-shaped icons.
 * - iOS: the Settings app - a pinned centred "Einstellungen" title over the glass, header-less inset grouped
 *   lists with icon squares in the accent colour and trailing values.
 * - HyperOS: light cards - shortcut cards with pastel icon circles, a storage card, a chevron list.
 *
 * Design, Einstellungen, Speicher and Info all live in one settings sheet, and each entry opens it
 * scrolled to that section.
 */
@Composable
fun MenuScreen(
    favoriteCount: Int,
    trashedCount: Int,
    internalFreeBytes: Long,
    internalTotalBytes: Long,
    sdCardFreeBytes: Long?,
    sdCardTotalBytes: Long?,
    appVersionName: String = "",
    graphicsQuality: PerformanceProfile = PerformanceProfile.MEDIUM,
    onSetGraphicsQuality: (PerformanceProfile) -> Unit = {},
    onOpenFavorites: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit = {},
    modifier: Modifier = Modifier,
    // iOS settings pages
    loadStorageStats: suspend () -> com.example.amoledgallery.data.MediaRepository.MediaStorageStats? = { null },
    onEmptyTrash: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    /** One UI: its settings are a pushed page without the tab bar, so they need their own way back. */
    onBack: () -> Unit = {},
    /** Reports whether a settings page is pushed over the menu (iOS hides the tab bar then). */
    onSettingsPageOpenChanged: (Boolean) -> Unit = {}
) {
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language
    val isIos = language == DesignLanguage.IOS_LIQUID_GLASS

    val favoritesText = strings.items(favoriteCount)
    val trashText = strings.items(trashedCount)
    val storageText = if (internalTotalBytes > 0) {
        strings.freeOf(formatBytes(internalFreeBytes), formatBytes(internalTotalBytes))
    } else {
        strings.unknown
    }
    val usedFraction = if (internalTotalBytes > 0) {
        (1f - internalFreeBytes.toFloat() / internalTotalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    // iOS and HyperOS push each entry as its own page, like their Settings apps; the others open the shared sheet.
    val isHyperOs = language == DesignLanguage.XIAOMI_HYPEROS
    val isOneUi = language == DesignLanguage.SAMSUNG_ONEUI
    val isGoogle = language == DesignLanguage.GOOGLE_EXPRESSIVE
    val usesPages = isIos || isHyperOs || isOneUi || isGoogle
    var page by remember { mutableStateOf<SettingsSection?>(null) }
    val openSection: (SettingsSection) -> Unit = { if (usesPages) page = it else settingsSection = it }
    val openDesign = { openSection(SettingsSection.DESIGN) }
    val openSettings = { openSection(SettingsSection.SETTINGS) }
    val openStorage = { openSection(SettingsSection.STORAGE) }
    val openInfo = { openSection(SettingsSection.INFO) }
    // The last page stays shown while it slides back out.
    val lastPage = remember { arrayOf(SettingsSection.DESIGN) }
    page?.let { lastPage[0] = it }
    val pageProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (page != null) 1f else 0f,
        animationSpec = pushSpec(language),
        label = "settings_page_push"
    )
    val pageShown by remember { androidx.compose.runtime.derivedStateOf { pageProgress.value > 0.001f } }
    // Composed only while a page is open: a handler added later takes priority over the tab's own back handler,
    // which merely enabling an always-present one would not.
    if (page != null) {
        androidx.activity.compose.BackHandler { page = null }
    }
    LaunchedEffect(page != null) {
        onSettingsPageOpenChanged(page != null)
        com.example.amoledgallery.ui.design.GeminiVisits.bump()
    }
    // Picking a design whose settings take the other form carries the open section over to it, so the settings
    // switch to the new design at once instead of staying in the old form until the menu is reopened.
    LaunchedEffect(usesPages) {
        if (usesPages && settingsSection != null) {
            page = settingsSection
            settingsSection = null
        } else if (!usesPages && page != null) {
            settingsSection = page
            page = null
        }
    }

    val menuScroll = rememberScrollState()
    // One UI's big title fades out while the page scrolls instead of sliding under the status bar.
    val headerCollapsePx = with(androidx.compose.ui.platform.LocalDensity.current) { 220.dp.toPx() }
    // Backdrop for iOS's pinned title bar, which blurs the list scrolling underneath it.
    val menuHaze = remember { HazeState() }

    Box(modifier = modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (usesPages) Modifier.pushTransition(language, { pageProgress.value }, isTop = false) else Modifier)
    ) {
        HazeSource(menuHaze, Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(menuScroll)
            ) {
                if (isIos) {
                    Spacer(modifier = Modifier.statusBarsPadding().height(72.dp))
                } else {
                    LargeHeader(
                        title = if (isOneUi) strings.settings else strings.tabMenu,
                        collapseFraction = { (menuScroll.value / headerCollapsePx).coerceIn(0f, 1f) },
                        actions = {
                            if (isOneUi || isHyperOs) {
                                ThemedIconButton(
                                    icon = if (isOneUi) Icons.AutoMirrored.Rounded.ArrowBackIos else Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = strings.back,
                                    onClick = onBack
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = when (language) {
                                DesignLanguage.GOOGLE_EXPRESSIVE -> 12.dp
                                DesignLanguage.XIAOMI_HYPEROS -> 0.dp
                                else -> 16.dp
                            }
                        )
                        .padding(bottom = bottomBarContentPadding(24.dp)),
                    verticalArrangement = Arrangement.spacedBy(if (isIos) 32.dp else 16.dp)
                ) {
                    when (language) {
                        // One UI 8: Samsung Settings' grouped cards with coloured circle icons.
                        DesignLanguage.SAMSUNG_ONEUI -> OneUiMenuContent(
                            favoriteCount = favoriteCount,
                            trashedCount = trashedCount,
                            storageValue = storageText,
                            appVersionName = appVersionName,
                            onOpenFavorites = onOpenFavorites,
                            onOpenTrash = onOpenTrash,
                            onOpenDuplicates = onOpenDuplicates,
                            onOpenDesign = openDesign,
                            onOpenSettings = openSettings,
                            onOpenStorage = openStorage,
                            onOpenInfo = openInfo
                        )

                        // Google (Gemini): the glowing storage box, then Gemini's drawer-style lists.
                        DesignLanguage.GOOGLE_EXPRESSIVE -> GoogleMenuContent(
                            internalFreeBytes = internalFreeBytes,
                            internalTotalBytes = internalTotalBytes,
                            sdCardFreeBytes = sdCardFreeBytes,
                            sdCardTotalBytes = sdCardTotalBytes,
                            loadStorageStats = loadStorageStats,
                            favoriteCount = favoriteCount,
                            trashedCount = trashedCount,
                            storageText = storageText,
                            usedFraction = usedFraction,
                            appVersionName = appVersionName,
                            onOpenFavorites = onOpenFavorites,
                            onOpenTrash = onOpenTrash,
                            onOpenDuplicates = onOpenDuplicates,
                            onOpenDesign = openDesign,
                            onOpenSettings = openSettings,
                            onOpenStorage = openStorage,
                            onOpenInfo = openInfo
                        )

                        // iOS Settings groups have no headers; the icon squares take the accent colour (see ListRow).
                        DesignLanguage.IOS_LIQUID_GLASS -> {
                            ThemedListSection(
                                rows = listOf(
                                    ListRow(strings.favorites, icon = Icons.Rounded.Favorite, value = "$favoriteCount", onClick = onOpenFavorites),
                                    ListRow(strings.recentlyDeleted, icon = Icons.Rounded.Delete, value = "$trashedCount", onClick = onOpenTrash),
                                    ListRow(strings.duplicates, icon = Icons.Rounded.ContentCopy, onClick = onOpenDuplicates)
                                )
                            )
                            ThemedListSection(
                                rows = listOf(
                                    ListRow(strings.appearance, icon = Icons.Rounded.Palette, onClick = openDesign),
                                    ListRow(strings.settings, icon = Icons.Rounded.Settings, onClick = openSettings),
                                    ListRow(strings.storage, icon = Icons.Rounded.Storage, value = if (internalTotalBytes > 0) strings.free(formatBytes(internalFreeBytes)) else null, onClick = openStorage),
                                    ListRow(strings.info, icon = Icons.Rounded.Info, value = appVersionName.ifBlank { null }, onClick = openInfo)
                                )
                            )
                        }

                        DesignLanguage.XIAOMI_HYPEROS -> HyperOsMenuContent(
                            favoriteCount = favoriteCount,
                            trashedCount = trashedCount,
                            storageValue = if (internalTotalBytes > 0) strings.free(formatBytes(internalFreeBytes)) else null,
                            appVersionName = appVersionName,
                            onOpenFavorites = onOpenFavorites,
                            onOpenTrash = onOpenTrash,
                            onOpenDuplicates = onOpenDuplicates,
                            onOpenDesign = openDesign,
                            onOpenSettings = openSettings,
                            onOpenStorage = openStorage,
                            onOpenInfo = openInfo
                        )
                    }
                }
            }
        }

        if (isIos) {
            ThemedTopBar(
                title = strings.settings,
                hazeState = menuHaze,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

        if (usesPages && (page != null || pageShown)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pushTransition(language, { pageProgress.value }, isTop = true)
                    .screenBackground()
                    .blockTouchesBelow()
            ) {
                if (isIos) {
                    IosSettingsPage(
                        section = lastPage[0],
                        onBack = { page = null },
                        internalFreeBytes = internalFreeBytes,
                        internalTotalBytes = internalTotalBytes,
                        sdCardFreeBytes = sdCardFreeBytes,
                        sdCardTotalBytes = sdCardTotalBytes,
                        appVersionName = appVersionName,
                        trashedCount = trashedCount,
                        graphicsQuality = graphicsQuality,
                        onSetGraphicsQuality = onSetGraphicsQuality,
                        loadStorageStats = loadStorageStats,
                        onEmptyTrash = onEmptyTrash,
                        onOpenLibrary = onOpenLibrary
                    )
                } else if (isOneUi) {
                    OneUiSettingsPage(
                        section = lastPage[0],
                        onBack = { page = null },
                        internalFreeBytes = internalFreeBytes,
                        internalTotalBytes = internalTotalBytes,
                        sdCardFreeBytes = sdCardFreeBytes,
                        sdCardTotalBytes = sdCardTotalBytes,
                        appVersionName = appVersionName,
                        trashedCount = trashedCount,
                        graphicsQuality = graphicsQuality,
                        onSetGraphicsQuality = onSetGraphicsQuality,
                        loadStorageStats = loadStorageStats,
                        onEmptyTrash = onEmptyTrash,
                        onOpenLibrary = onOpenLibrary,
                        onOpenTrash = onOpenTrash
                    )
                } else if (isGoogle) {
                    GoogleSettingsPage(
                        section = lastPage[0],
                        onBack = { page = null },
                        internalFreeBytes = internalFreeBytes,
                        internalTotalBytes = internalTotalBytes,
                        sdCardFreeBytes = sdCardFreeBytes,
                        sdCardTotalBytes = sdCardTotalBytes,
                        appVersionName = appVersionName,
                        trashedCount = trashedCount,
                        graphicsQuality = graphicsQuality,
                        onSetGraphicsQuality = onSetGraphicsQuality,
                        loadStorageStats = loadStorageStats,
                        onEmptyTrash = onEmptyTrash,
                        onOpenLibrary = onOpenLibrary,
                        onOpenTrash = onOpenTrash
                    )
                } else {
                    HyperOsSettingsPage(
                        section = lastPage[0],
                        onBack = { page = null },
                        internalFreeBytes = internalFreeBytes,
                        internalTotalBytes = internalTotalBytes,
                        sdCardFreeBytes = sdCardFreeBytes,
                        sdCardTotalBytes = sdCardTotalBytes,
                        appVersionName = appVersionName,
                        trashedCount = trashedCount,
                        graphicsQuality = graphicsQuality,
                        onSetGraphicsQuality = onSetGraphicsQuality,
                        loadStorageStats = loadStorageStats,
                        onEmptyTrash = onEmptyTrash,
                        onOpenLibrary = onOpenLibrary,
                        onOpenTrash = onOpenTrash
                    )
                }
            }
        }
    }

    settingsSection?.let { section ->
        SettingsSheet(
            initialSection = section,
            internalFreeBytes = internalFreeBytes,
            internalTotalBytes = internalTotalBytes,
            sdCardFreeBytes = sdCardFreeBytes,
            sdCardTotalBytes = sdCardTotalBytes,
            appVersionName = appVersionName,
            graphicsQuality = graphicsQuality,
            onSetGraphicsQuality = onSetGraphicsQuality,
            onDismiss = { settingsSection = null }
        )
    }
}

@Composable
private fun OneUiShortcutTile(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(palette.surface)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(30.dp))
        Spacer(modifier = Modifier.height(26.dp))
        Text(title, color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(subtitle, color = palette.accent, fontSize = 14.sp)
    }
}

@Composable
private fun SettingsSheet(
    initialSection: SettingsSection,
    internalFreeBytes: Long,
    internalTotalBytes: Long,
    sdCardFreeBytes: Long?,
    sdCardTotalBytes: Long?,
    appVersionName: String,
    graphicsQuality: PerformanceProfile,
    onSetGraphicsQuality: (PerformanceProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showCustomEditor by remember { mutableStateOf(false) }
    val currentLanguage = ThemeState.designLanguage
    val currentPalette = ThemeState.palette
    val lightMode = currentPalette.isLight
    val palette = LocalAppTheme.current.palette

    val scrollState = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<SettingsSection, Int>() }
    fun Modifier.sectionAnchor(section: SettingsSection) = onGloballyPositioned {
        sectionOffsets[section] = it.positionInParent().y.roundToInt()
    }

    fun switchBrightness(light: Boolean) = switchPaletteBrightness(context, light)

    ThemedSheet(onDismissRequest = onDismiss, title = strings.settings) {
        // Jump to the section on the first laid-out frame, while the sheet is still below the screen, so
        // it slides up already showing that section instead of visibly racing down the whole list.
        LaunchedEffect(initialSection) {
            if (initialSection != SettingsSection.DESIGN) {
                val offset = snapshotFlow { sectionOffsets[initialSection] }.filterNotNull().first()
                scrollState.scrollTo(offset)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Column(modifier = Modifier.sectionAnchor(SettingsSection.DESIGN)) {
                ThemedSectionHeader(strings.design)
                ThemedCard(inSheet = true) {
                    DesignLanguage.entries.forEachIndexed { index, language ->
                        if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    // Keep the current light/dark mode when switching design language.
                                    val preset = presetsFor(language, lightMode).firstOrNull() ?: presetsFor(language).first()
                                    ThemeState.apply(language, preset, context = context)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(language.displayName, color = palette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(strings.designDescription(language), color = palette.textMuted, fontSize = 13.sp, lineHeight = 17.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            ThemedRadioIndicator(selected = language == currentLanguage)
                        }
                    }
                }
            }

            Column {
                ThemedSectionHeader(strings.colors)
                ThemedCard(inSheet = true) {
                    ThemedSegmentedControl(
                        options = listOf(true, false),
                        selected = lightMode,
                        label = { light -> if (light) strings.light else strings.dark },
                        onSelect = { light -> switchBrightness(light) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PaletteRow(
                        presets = presetsFor(currentLanguage, lightMode),
                        currentPaletteId = currentPalette.id,
                        onSelectPreset = { preset -> ThemeState.apply(currentLanguage, preset, context = context) },
                        onOpenCustomEditor = { showCustomEditor = true }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(strings.paletteLabel(currentPalette), color = palette.textMuted, fontSize = 13.sp)
                }
            }

            Column(modifier = Modifier.sectionAnchor(SettingsSection.SETTINGS)) {
                ThemedSectionHeader(strings.language)
                ThemedSegmentedControl(
                    options = AppLanguage.entries,
                    selected = LanguageState.language,
                    label = { it.displayName },
                    onSelect = { LanguageState.apply(it, context) }
                )
                Spacer(modifier = Modifier.height(22.dp))
                ThemedSectionHeader(strings.graphicsQuality)
                ThemedSegmentedControl(
                    options = PerformanceProfile.entries,
                    selected = graphicsQuality,
                    label = { graphicsQualityLabel(it) },
                    onSelect = onSetGraphicsQuality
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = graphicsQualityDescription(graphicsQuality),
                    color = palette.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Column(modifier = Modifier.sectionAnchor(SettingsSection.STORAGE)) {
                ThemedSectionHeader(strings.storage)
                ThemedCard(inSheet = true) {
                    StorageBlock(strings.internalStorage, internalFreeBytes, internalTotalBytes, null)
                    Spacer(modifier = Modifier.height(18.dp))
                    StorageBlock(strings.sdCard, sdCardFreeBytes, sdCardTotalBytes, strings.notAvailable)
                }
            }

            ThemedListSection(
                header = strings.info,
                inSheet = true,
                modifier = Modifier.sectionAnchor(SettingsSection.INFO),
                rows = listOf(
                    ListRow(strings.appVersion, icon = Icons.Rounded.Info, iconColor = Color(0xFF8E8E93), value = appVersionName.ifBlank { strings.unknown }, showChevron = false)
                )
            )
        }
    }

    if (showCustomEditor) {
        CustomAccentDialog(
            language = currentLanguage,
            lightMode = lightMode,
            initialAccent = currentPalette.accent,
            onConfirm = { accent ->
                applyCustomAccent(context, currentLanguage, lightMode, accent)
                showCustomEditor = false
            },
            onDismiss = { showCustomEditor = false }
        )
    }
}

@Composable
private fun StorageBlock(label: String, freeBytes: Long?, totalBytes: Long?, notAvailableText: String?) {
    val palette = LocalAppTheme.current.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(
            text = if (freeBytes != null && totalBytes != null && totalBytes > 0) {
                strings.freeOf(formatBytes(freeBytes), formatBytes(totalBytes))
            } else {
                notAvailableText ?: strings.unknown
            },
            color = palette.textMuted,
            fontSize = 13.sp
        )
    }
    if (freeBytes != null && totalBytes != null && totalBytes > 0) {
        Spacer(modifier = Modifier.height(10.dp))
        ThemedProgressBar(progress = (1f - freeBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
    }
}

/** Accent swatches for the active mode, plus a trailing "custom" swatch. */
@Composable
internal fun PaletteRow(
    presets: List<AppColorPalette>,
    currentPaletteId: String,
    onSelectPreset: (AppColorPalette) -> Unit,
    onOpenCustomEditor: () -> Unit
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val swatchShape = CircleShape
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        presets.forEach { preset ->
            val isSelected = preset.id == currentPaletteId
            // Each swatch shows the preset's own background with its accent inside.
            Box(
                modifier = Modifier
                    .selectionPop(isSelected, fromScale = 0.72f, dampingRatio = 0.4f, stiffness = 600f)
                    .size(44.dp)
                    .clip(swatchShape)
                    .background(preset.background)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) palette.accent else palette.onBackground.copy(alpha = 0.15f),
                        shape = swatchShape
                    )
                    .clickable { onSelectPreset(preset) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(preset.accent)
                )
            }
        }
        val context = LocalContext.current
        // A wallpaper colour is stored as a custom accent too; then its swatch below is the selected one.
        val isCustomSelected = currentPaletteId == CUSTOM_PALETTE_ID && pickedWallpaperAccent(context) == null
        Box(
            modifier = Modifier
                .selectionPop(isCustomSelected, fromScale = 0.72f, dampingRatio = 0.4f, stiffness = 600f)
                .size(44.dp)
                .clip(swatchShape)
                .background(if (isCustomSelected) ThemeState.accent else palette.surface)
                .border(
                    width = if (isCustomSelected) 2.5.dp else 1.dp,
                    color = if (isCustomSelected) palette.onBackground else palette.textMuted,
                    shape = swatchShape
                )
                .clickable { onOpenCustomEditor() },
            contentAlignment = Alignment.Center
        ) {
            if (!isCustomSelected) {
                Icon(Icons.Rounded.Add, strings.customColor, tint = palette.textMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
    WallpaperAccentRow(currentPaletteId)
    }
}

@Composable
internal fun CustomAccentDialog(
    language: DesignLanguage,
    lightMode: Boolean,
    initialAccent: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var pickedColor by remember { mutableStateOf(initialAccent) }
    ThemedDialog(
        onDismissRequest = onDismiss,
        title = strings.customAccentTitle,
        message = strings.customAccentMessage(lightMode, language.displayName),
        buttons = listOf(
            DialogButton(strings.cancel, DialogButtonStyle.Cancel, onClick = onDismiss),
            DialogButton(strings.apply, DialogButtonStyle.Primary) { onConfirm(pickedColor) }
        )
    ) {
        HsvColorPicker(
            initialColor = initialAccent,
            onColorChanged = { pickedColor = it }
        )
    }
}

/** Switches the active palette between light and dark, keeping the accent slot (blue stays blue) or custom accent. */
internal fun switchPaletteBrightness(context: android.content.Context, light: Boolean) {
    val currentLanguage = ThemeState.designLanguage
    val currentPalette = ThemeState.palette
    if (currentPalette.isLight == light) return
    val target = presetsFor(currentLanguage, light)
    val next = if (currentPalette.id == CUSTOM_PALETTE_ID) {
        target.first().copy(id = CUSTOM_PALETTE_ID, label = "Eigene Palette", accent = currentPalette.accent)
    } else {
        val slot = presetsFor(currentLanguage, currentPalette.isLight).indexOfFirst { it.id == currentPalette.id }
        target.getOrElse(slot) { target.first() }
    }
    ThemeState.apply(currentLanguage, next, context = context)
}

/** Applies a custom accent on the active mode's base colors - light stays light. */
internal fun applyCustomAccent(context: android.content.Context, language: DesignLanguage, lightMode: Boolean, accent: Color) {
    val base = presetsFor(language, lightMode).first()
    val custom = base.copy(id = CUSTOM_PALETTE_ID, label = "Eigene Palette", accent = accent)
    ThemeState.apply(language, custom, context = context)
    wallpaperAccentPrefs(context).edit().remove(KEY_WALLPAPER_ACCENT).apply()
}

private const val KEY_WALLPAPER_ACCENT = "wallpaper_accent"

private fun wallpaperAccentPrefs(context: android.content.Context) =
    context.getSharedPreferences("prism_wallpaper_accent", android.content.Context.MODE_PRIVATE)

/** The wallpaper colour the current custom accent was taken from, or null if it was picked by hand. */
internal fun pickedWallpaperAccent(context: android.content.Context): Color? =
    wallpaperAccentPrefs(context).takeIf { it.contains(KEY_WALLPAPER_ACCENT) }
        ?.let { Color(it.getInt(KEY_WALLPAPER_ACCENT, 0)) }

/** Takes one of the wallpaper's colours as the accent (stored as a custom accent, remembered as from the wallpaper). */
internal fun applyWallpaperAccent(context: android.content.Context, language: DesignLanguage, lightMode: Boolean, color: Color) {
    applyCustomAccent(context, language, lightMode, color)
    wallpaperAccentPrefs(context).edit().putInt(KEY_WALLPAPER_ACCENT, color.toArgb()).apply()
}

/**
 * The colours Android took from the home-screen wallpaper, as accent swatches (as in Prism Camera).
 * Left out entirely when the phone reports none.
 */
@Composable
private fun WallpaperAccentRow(currentPaletteId: String) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    val lightMode = palette.isLight
    val colors = remember(lightMode) { WallpaperAccents.read(context, lightMode) }
    if (colors.isEmpty()) return
    val picked = if (currentPaletteId == CUSTOM_PALETTE_ID) pickedWallpaperAccent(context) else null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(strings.fromWallpaper, color = palette.textMuted, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            colors.forEach { color ->
                val isSelected = picked == color
                Box(
                    modifier = Modifier
                        .selectionPop(isSelected, fromScale = 0.72f, dampingRatio = 0.4f, stiffness = 600f)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.background)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) palette.accent else palette.onBackground.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .clickable { applyWallpaperAccent(context, ThemeState.designLanguage, lightMode, color) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

internal fun graphicsQualityLabel(tier: PerformanceProfile): String = when (tier) {
    PerformanceProfile.LOW -> "Low"
    PerformanceProfile.MEDIUM -> "Medium"
    PerformanceProfile.HIGH -> "High"
    PerformanceProfile.ULTRA -> "Ultra"
}

private fun graphicsQualityDescription(tier: PerformanceProfile): String = strings.qualityDescription(tier)

private fun formatBytes(bytes: Long): String {
    // Decimal units, as Android's own storage settings and the rest of the app count.
    val gb = bytes / 1_000_000_000.0
    // App language decides the decimal separator (1,5 GB / 1.5 GB), like every other text.
    return String.format(LanguageState.locale, "%.1f GB", gb)
}
