package com.example.amoledgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.amoledgallery.PerformanceProfile
import com.example.amoledgallery.data.MediaRepository
import com.example.amoledgallery.ui.design.HazeSource
import com.example.amoledgallery.ui.design.ThemedTopBar
import com.example.amoledgallery.ui.design.bottomBarContentPadding
import com.example.amoledgallery.ui.i18n.AppLanguage
import com.example.amoledgallery.ui.i18n.LanguageState
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.ThemeState
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent
import com.example.amoledgallery.ui.theme.presetsFor
import dev.chrisbanes.haze.HazeState

/**
 * A page of the iOS menu, built like a page of Apple's Settings app: a glass back button with the centred
 * title, inset grouped lists with checkmarks, grey section headers and explanatory footers. Storage follows
 * the iPhone storage page with its segmented usage bar, recommendations and the albums by size.
 */
@Composable
internal fun IosSettingsPage(
    section: SettingsSection,
    onBack: () -> Unit,
    internalFreeBytes: Long,
    internalTotalBytes: Long,
    sdCardFreeBytes: Long?,
    sdCardTotalBytes: Long?,
    appVersionName: String,
    trashedCount: Int,
    trashLockEnabled: Boolean = true,
    graphicsQuality: PerformanceProfile,
    onSetGraphicsQuality: (PerformanceProfile) -> Unit,
    onToggleTrashLock: (Boolean) -> Unit = {},
    loadStorageStats: suspend () -> MediaRepository.MediaStorageStats?,
    onEmptyTrash: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val pageHaze = remember { HazeState() }
    val title = when (section) {
        SettingsSection.DESIGN -> strings.appearance
        SettingsSection.SETTINGS -> strings.settings
        SettingsSection.STORAGE -> strings.deviceStorage
        SettingsSection.INFO -> strings.info
    }
    Box(modifier = Modifier.fillMaxSize()) {
        HazeSource(pageHaze, Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 84.dp, bottom = bottomBarContentPadding(24.dp))
            ) {
                when (section) {
                    SettingsSection.DESIGN -> AppearanceContent()
                    SettingsSection.SETTINGS -> GeneralContent(graphicsQuality, onSetGraphicsQuality, trashLockEnabled, onToggleTrashLock)
                    SettingsSection.STORAGE -> StorageContent(
                        internalFreeBytes, internalTotalBytes, sdCardFreeBytes, sdCardTotalBytes,
                        trashedCount, loadStorageStats, onEmptyTrash, onOpenLibrary
                    )
                    SettingsSection.INFO -> InfoContent(appVersionName)
                }
            }
        }
        ThemedTopBar(
            title = title,
            onBack = onBack,
            hazeState = pageHaze,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun ColumnScope.AppearanceContent() {
    val context = LocalContext.current
    val currentLanguage = ThemeState.designLanguage
    val currentPalette = ThemeState.palette
    val lightMode = currentPalette.isLight
    var showCustomEditor by remember { mutableStateOf(false) }

    IosHeader(strings.design, first = true)
    IosGroup {
        DesignLanguage.entries.forEachIndexed { index, language ->
            if (index > 0) IosDivider()
            IosCheckRow(language.displayName, checked = language == currentLanguage) {
                // Keep the current light/dark mode when switching design language.
                val preset = presetsFor(language, lightMode).firstOrNull() ?: presetsFor(language).first()
                ThemeState.apply(language, preset, context = context)
            }
        }
    }
    IosFooter(strings.designFooterText)

    IosHeader(strings.appearanceModeHeader)
    IosGroup {
        IosCheckRow(strings.light, checked = lightMode) { switchPaletteBrightness(context, true) }
        IosDivider()
        IosCheckRow(strings.dark, checked = !lightMode) { switchPaletteBrightness(context, false) }
    }

    IosHeader(strings.accentColorHeader)
    IosGroup {
        Box(modifier = Modifier.padding(16.dp)) {
            PaletteRow(
                presets = presetsFor(currentLanguage, lightMode),
                currentPaletteId = currentPalette.id,
                onSelectPreset = { preset -> ThemeState.apply(currentLanguage, preset, context = context) },
                onOpenCustomEditor = { showCustomEditor = true }
            )
        }
    }
    IosFooter("${strings.paletteLabel(currentPalette)}. ${strings.accentFooterText}")

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
private fun ColumnScope.GeneralContent(
    graphicsQuality: PerformanceProfile,
    onSetGraphicsQuality: (PerformanceProfile) -> Unit,
    trashLockEnabled: Boolean = true,
    onToggleTrashLock: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    IosHeader(strings.language, first = true)
    IosGroup {
        AppLanguage.entries.forEachIndexed { index, appLanguage ->
            if (index > 0) IosDivider()
            IosCheckRow(appLanguage.displayName, checked = appLanguage == LanguageState.language) {
                LanguageState.apply(appLanguage, context)
            }
        }
    }
    IosFooter(strings.languageFooterText)

    IosHeader(strings.graphicsQuality)
    IosGroup {
        PerformanceProfile.entries.forEachIndexed { index, tier ->
            if (index > 0) IosDivider()
            IosCheckRow(graphicsQualityLabel(tier), checked = tier == graphicsQuality) { onSetGraphicsQuality(tier) }
        }
    }
    IosFooter(strings.qualityDescription(graphicsQuality))

    IosHeader(strings.lockTrashSetting)
    IosGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleTrashLock(!trashLockEnabled) }
                .heightIn(min = 54.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.lockTrashSetting, color = palette.onBackground, fontSize = 17.sp)
            }
            androidx.compose.material3.Switch(
                checked = trashLockEnabled,
                onCheckedChange = onToggleTrashLock,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = palette.onAccent,
                    checkedTrackColor = palette.accent
                )
            )
        }
    }
    IosFooter(strings.lockTrashSettingSubtitle)
}

@Composable
private fun ColumnScope.InfoContent(appVersionName: String) {
    val context = LocalContext.current
    val appName = APP_FULL_NAME
    IosGroup {
        IosValueRow(strings.appNameLabel, appName)
        IosDivider()
        IosValueRow(strings.appVersion, appVersionName.ifBlank { strings.unknown })
        IosDivider()
        IosValueRow(strings.design, ThemeState.designLanguage.displayName)
        IosDivider()
        IosValueRow(strings.colorPaletteLabel, strings.paletteLabel(ThemeState.palette))
        IosDivider()
        IosValueRow(strings.language, LanguageState.language.displayName)
    }
}

private val PhotoColor = Color(0xFFFF9F0A)
private val VideoColor = Color(0xFFFF453A)
private val OtherColor = Color(0xFF8E8E93)

@Composable
private fun ColumnScope.StorageContent(
    internalFreeBytes: Long,
    internalTotalBytes: Long,
    sdCardFreeBytes: Long?,
    sdCardTotalBytes: Long?,
    trashedCount: Int,
    loadStorageStats: suspend () -> MediaRepository.MediaStorageStats?,
    onEmptyTrash: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val stats by produceState<MediaRepository.MediaStorageStats?>(initialValue = null) { value = loadStorageStats() }
    val current = stats

    // Short label like the iPhone's "iPhone", so the used-of-total text next to it fits.
    StorageCard(strings.internal, internalFreeBytes, internalTotalBytes, current, current?.photoBytes, current?.videoBytes)
    // Each SD card and USB drive gets its own card, with its own photo and video sizes, once counted.
    val externals = current?.externals.orEmpty().filter { it.totalBytes > 0L }
    if (externals.isNotEmpty()) {
        externals.forEach { ext ->
            Spacer(modifier = Modifier.height(16.dp))
            StorageCard(
                com.example.amoledgallery.ui.i18n.storageLabel(ext.kind), ext.freeBytes, ext.totalBytes,
                current, ext.photoBytes, ext.videoBytes
            )
        }
    } else if (sdCardFreeBytes != null && sdCardTotalBytes != null && sdCardTotalBytes > 0) {
        Spacer(modifier = Modifier.height(16.dp))
        StorageCard(strings.sdCard, sdCardFreeBytes, sdCardTotalBytes, current, current?.sdPhotoBytes, current?.sdVideoBytes)
    }

    IosHeader(strings.storageRecommendations)
    if (trashedCount > 0) {
        IosGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosIconSquare(Icons.Rounded.Delete)
                Spacer(modifier = Modifier.width(14.dp))
                Text(strings.recentlyDeletedAlbum, color = palette.onBackground, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    text = strings.emptyShort,
                    color = palette.accent,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onEmptyTrash)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            IosDivider(inset = 66.dp)
            Text(
                text = if (current == null) strings.storageCalculating else strings.freeUpTrash(formatStorageSize(current.trashBytes)),
                color = palette.textMuted,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(start = 66.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    IosGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenLibrary)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosIconSquare(Icons.Rounded.PhotoLibrary)
            Spacer(modifier = Modifier.width(14.dp))
            Text(strings.reviewMedia, color = palette.onBackground, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.textMuted, modifier = Modifier.size(22.dp))
        }
        IosDivider(inset = 66.dp)
        Text(
            text = if (current == null) strings.storageCalculating else strings.reviewMediaMessage(formatStorageSize(current.photoBytes + current.videoBytes)),
            color = palette.textMuted,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(start = 66.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
        )
    }

    val albums = current?.albums.orEmpty()
    if (albums.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.tabAlbums, color = palette.textMuted, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(strings.sortBySize, color = palette.accent, fontSize = 17.sp)
        }
        IosGroup {
            albums.take(30).forEachIndexed { index, album ->
                if (index > 0) IosDivider(inset = 74.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceVariant)
                    ) {
                        if (album.coverUri != null) {
                            AsyncImage(
                                model = album.coverUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(album.name, color = palette.onBackground, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(strings.objects(album.count), color = palette.textMuted, fontSize = 14.sp, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(formatStorageSize(album.bytes), color = palette.textMuted, fontSize = 17.sp, maxLines = 1)
                }
            }
        }
    }
}

/** The iPhone storage card: used of total, a segmented bar (photos, videos, other, free) and its legend. */
@Composable
private fun StorageCard(
    label: String,
    freeBytes: Long,
    totalBytes: Long,
    stats: MediaRepository.MediaStorageStats?,
    photoBytes: Long?,
    videoBytes: Long?
) {
    val palette = LocalAppTheme.current.palette
    val total = totalBytes.coerceAtLeast(1L)
    val used = (totalBytes - freeBytes).coerceIn(0L, total)
    val photos = photoBytes?.coerceIn(0L, used) ?: 0L
    val videos = videoBytes?.coerceIn(0L, used - photos) ?: 0L
    val other = (used - photos - videos).coerceAtLeast(0L)
    val free = (total - used).coerceAtLeast(0L)
    // The used part fills the bar from 0 when the page opens.
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        grow.animateTo(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val g = grow.value

    IosGroup {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = palette.onBackground, fontSize = 17.sp, maxLines = 1)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = strings.storageUsed(formatStorageSize(used), formatStorageSize(totalBytes)),
                    color = palette.textMuted,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.background)
            ) {
                val segments = listOf(photos to PhotoColor, videos to VideoColor, other to OtherColor).filter { it.first > 0L }
                segments.forEachIndexed { index, (bytes, color) ->
                    if (index > 0) Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(palette.surface))
                    Box(
                        modifier = Modifier
                            .weight((bytes.toFloat() / total * g).coerceAtLeast(0.0001f))
                            .fillMaxHeight()
                            .background(color)
                    )
                }
                val rest = (free.toFloat() + used.toFloat() * (1f - g)) / total
                if (rest > 0.0001f) {
                    Box(
                        modifier = Modifier
                            .weight(rest)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (free.toFloat() / total > 0.2f) {
                            Text(formatStorageSize(free), color = palette.onBackground, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (stats == null && label == strings.internal) {
                Text(strings.storageCalculating, color = palette.textMuted, fontSize = 15.sp)
            } else if (stats != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendEntry(PhotoColor, strings.legendPhotos)
                    LegendEntry(VideoColor, strings.legendVideos)
                    LegendEntry(OtherColor, strings.legendOther)
                }
            }
        }
    }
}

@Composable
private fun LegendEntry(color: Color, text: String) {
    val palette = LocalAppTheme.current.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, color = palette.onBackground, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun IosGroup(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(palette.surface),
        content = content
    )
}

@Composable
private fun IosHeader(text: String, first: Boolean = false) {
    val palette = LocalAppTheme.current.palette
    Text(
        text = text,
        color = palette.textMuted,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (first) 4.dp else 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun IosFooter(text: String) {
    val palette = LocalAppTheme.current.palette
    Text(
        text = text,
        color = palette.textMuted,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
    )
}

@Composable
private fun IosDivider(inset: androidx.compose.ui.unit.Dp = 16.dp) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = Modifier
            .padding(start = inset, end = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.onBackground.copy(alpha = 0.10f))
    )
}

@Composable
private fun IosCheckRow(title: String, checked: Boolean, onClick: () -> Unit) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = palette.onBackground, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (checked) {
            Icon(Icons.Rounded.Check, null, tint = palette.accent, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun IosValueRow(title: String, value: String) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = palette.onBackground, fontSize = 17.sp)
        Spacer(modifier = Modifier.width(16.dp).weight(1f))
        Text(value, color = palette.textMuted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IosIconSquare(icon: ImageVector) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(palette.accent),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = palette.onAccent, modifier = Modifier.size(21.dp))
    }
}

/** "843,5 MB" / "62,42 GB" in the app language. */
internal fun formatStorageSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val locale = LanguageState.locale
    return when {
        gb >= 1.0 -> String.format(locale, "%.2f GB", gb)
        mb >= 1.0 -> String.format(locale, "%.1f MB", mb)
        else -> String.format(locale, "%.0f KB", kb)
    }
}
