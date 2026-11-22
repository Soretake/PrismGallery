package com.example.amoledgallery.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.amoledgallery.PerformanceProfile
import com.example.amoledgallery.data.MediaRepository
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.thumbnailRequest
import com.example.amoledgallery.ui.design.HyperOsDivider
import com.example.amoledgallery.ui.design.HyperOsIconRow
import com.example.amoledgallery.ui.design.HyperOsSectionLabel
import com.example.amoledgallery.ui.design.bottomBarContentPadding
import com.example.amoledgallery.ui.design.pressScale
import com.example.amoledgallery.ui.i18n.AppLanguage
import com.example.amoledgallery.ui.i18n.LanguageState
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.ThemeState
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent
import com.example.amoledgallery.ui.theme.presetsFor

/*
 * Xiaomi HyperOS Gallery and Settings: pure black (or light grey) pages with big thin titles, rounded album
 * covers, sections under muted blue-grey labels separated by hairlines, coloured rounded-square icons, choice
 * lists drawn like HyperOS's stacked popup cards, and the storage page's 3D cylinder.
 */

// ---------------------------------------------------------------------------------------------
// Albums tab
// ---------------------------------------------------------------------------------------------

/** The three large tiles at the top of HyperOS's Albums tab: all photos, videos and favourites. */
@Composable
internal fun HyperOsFeaturedAlbums(
    allMedia: List<MediaItem>,
    favoriteMedia: List<MediaItem>,
    onOpenAllPhotos: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenFavorites: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val videos = remember(allMedia) { allMedia.filter { it.isVideo } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FeaturedTile(strings.allPhotos, "${allMedia.size}", Modifier.weight(1f), onOpenAllPhotos) {
            MediaMosaic(allMedia, grid = 3)
        }
        FeaturedTile(strings.legendVideos, "${videos.size}", Modifier.weight(1f), onOpenVideos) {
            MediaMosaic(videos, grid = 1)
            if (videos.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Videocam,
                    null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
        }
        FeaturedTile(strings.favorites, "${favoriteMedia.size}", Modifier.weight(1f), onOpenFavorites) {
            if (favoriteMedia.isEmpty()) {
                Icon(
                    Icons.Rounded.Favorite,
                    null,
                    tint = palette.textMuted,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                )
            } else {
                MediaMosaic(favoriteMedia, grid = 2)
            }
        }
    }
}

@Composable
private fun FeaturedTile(
    title: String,
    count: String,
    modifier: Modifier,
    onClick: () -> Unit,
    cover: @Composable BoxScope.() -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressScale(interaction, 0.95f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(palette.surfaceVariant),
            content = cover
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(count, color = palette.textMuted, fontSize = 14.sp, maxLines = 1)
    }
}

/** A grid x grid mosaic of the first thumbnails, like HyperOS's album covers. */
@Composable
private fun MediaMosaic(items: List<MediaItem>, grid: Int) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
        for (row in 0 until grid) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                for (col in 0 until grid) {
                    val item = items.getOrNull(row * grid + col)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(palette.surfaceVariant)
                    ) {
                        if (item != null) {
                            key(item.id) {
                                AsyncImage(
                                    model = remember(context) { thumbnailRequest(context, item) },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Menu tab
// ---------------------------------------------------------------------------------------------

@Composable
internal fun HyperOsMenuContent(
    favoriteCount: Int,
    trashedCount: Int,
    trashLockEnabled: Boolean = true,
    storageValue: String?,
    appVersionName: String,
    onOpenFavorites: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenDesign: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Column(modifier = Modifier.fillMaxWidth()) {
        HyperOsSectionLabel(strings.organizePhotos, Modifier.padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 6.dp))
        HyperOsIconRow(Icons.Rounded.Favorite, Color(0xFFFF5C7A), strings.favorites, value = "$favoriteCount", onClick = onOpenFavorites)
        HyperOsIconRow(
            Icons.Rounded.Delete,
            palette.accent,
            strings.recentlyDeleted,
            value = "$trashedCount",
            trailingIcon = if (trashLockEnabled) Icons.Rounded.Lock else null,
            onClick = onOpenTrash
        )
        HyperOsIconRow(Icons.Rounded.ContentCopy, Color(0xFF00A9A5), strings.duplicates, onClick = onOpenDuplicates)
        HyperOsIconRow(Icons.Rounded.Storage, Color(0xFF8A90A6), strings.storage, value = storageValue, onClick = onOpenStorage)
        HyperOsDivider(Modifier.padding(horizontal = 28.dp, vertical = 16.dp))
        HyperOsSectionLabel("App", Modifier.padding(start = 28.dp, end = 28.dp, bottom = 6.dp))
        HyperOsIconRow(Icons.Rounded.Palette, Color(0xFFFF9F0A), strings.appearance, onClick = onOpenDesign)
        HyperOsIconRow(Icons.Rounded.Settings, Color(0xFF30B46C), strings.settings, onClick = onOpenSettings)
        HyperOsIconRow(Icons.Rounded.Info, Color(0xFF5E8BFF), strings.aboutGallery, value = appVersionName.ifBlank { null }, onClick = onOpenInfo)
    }
}

// ---------------------------------------------------------------------------------------------
// Settings pages
// ---------------------------------------------------------------------------------------------

/** A page of the HyperOS menu, laid out like HyperOS Settings: back arrow, big thin title, content below. */
@Composable
internal fun HyperOsSettingsPage(
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
    onOpenLibrary: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val title = when (section) {
        SettingsSection.DESIGN -> strings.appearance
        SettingsSection.SETTINGS -> strings.settings
        SettingsSection.STORAGE -> strings.storageSpaceTitle
        SettingsSection.INFO -> strings.aboutGallery
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = bottomBarContentPadding(24.dp))
    ) {
        Box(
            modifier = Modifier
                .padding(start = 12.dp, top = 4.dp)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings.back, tint = palette.onBackground, modifier = Modifier.size(26.dp))
        }
        Text(
            text = title,
            color = palette.onBackground,
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp,
            lineHeight = 46.sp,
            maxLines = 2,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 12.dp)
        )
        when (section) {
            SettingsSection.DESIGN -> AppearanceContent()
            SettingsSection.SETTINGS -> GeneralContent(graphicsQuality, onSetGraphicsQuality, trashLockEnabled, onToggleTrashLock)
            SettingsSection.STORAGE -> StorageContent(
                internalFreeBytes, internalTotalBytes, sdCardFreeBytes, sdCardTotalBytes,
                trashedCount, loadStorageStats, onEmptyTrash, onOpenLibrary, onOpenTrash
            )
            SettingsSection.INFO -> InfoContent(appVersionName)
        }
    }
}

@Composable
private fun AppearanceContent() {
    val context = LocalContext.current
    val currentLanguage = ThemeState.designLanguage
    val currentPalette = ThemeState.palette
    val lightMode = currentPalette.isLight
    var showCustomEditor by remember { mutableStateOf(false) }

    Label(strings.design)
    ChoiceCard(
        options = DesignLanguage.entries,
        selected = currentLanguage,
        label = { it.displayName },
        description = { strings.designDescription(it) }
    ) { language ->
        // Keep the current light/dark mode when switching design language.
        val preset = presetsFor(language, lightMode).firstOrNull() ?: presetsFor(language).first()
        ThemeState.apply(language, preset, context = context)
    }
    Footer(strings.designFooterText)

    Label(strings.appearanceModeHeader)
    ChoiceCard(
        options = listOf(true, false),
        selected = lightMode,
        label = { if (it) strings.light else strings.dark }
    ) { switchPaletteBrightness(context, it) }

    Label(strings.accentColorHeader)
    Card {
        Box(modifier = Modifier.padding(18.dp)) {
            PaletteRow(
                presets = presetsFor(currentLanguage, lightMode),
                currentPaletteId = currentPalette.id,
                onSelectPreset = { preset -> ThemeState.apply(currentLanguage, preset, context = context) },
                onOpenCustomEditor = { showCustomEditor = true }
            )
        }
    }
    Footer("${strings.paletteLabel(currentPalette)}. ${strings.accentFooterText}")

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
private fun GeneralContent(
    graphicsQuality: PerformanceProfile,
    onSetGraphicsQuality: (PerformanceProfile) -> Unit,
    trashLockEnabled: Boolean = true,
    onToggleTrashLock: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    Label(strings.language)
    ChoiceCard(
        options = AppLanguage.entries,
        selected = LanguageState.language,
        label = { it.displayName }
    ) { LanguageState.apply(it, context) }
    Footer(strings.languageFooterText)

    Label(strings.graphicsQuality)
    ChoiceCard(
        options = PerformanceProfile.entries,
        selected = graphicsQuality,
        label = { graphicsQualityLabel(it) }
    ) { onSetGraphicsQuality(it) }
    Footer(strings.qualityDescription(graphicsQuality))

    Label(strings.lockTrashSetting)
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleTrashLock(!trashLockEnabled) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.lockTrashSetting, color = palette.onBackground, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(strings.lockTrashSettingSubtitle, color = palette.textMuted, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
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
    Footer(strings.lockTrashSettingSubtitle)
}

@Composable
private fun InfoContent(appVersionName: String) {
    val context = LocalContext.current
    val appName = APP_FULL_NAME
    Spacer(modifier = Modifier.height(12.dp))
    Card {
        InfoRow(strings.appNameLabel, appName)
        InfoRow(strings.appVersion, appVersionName.ifBlank { strings.unknown })
        InfoRow(strings.design, ThemeState.designLanguage.displayName)
        InfoRow(strings.colorPaletteLabel, strings.paletteLabel(ThemeState.palette))
        InfoRow(strings.language, LanguageState.language.displayName)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = palette.onBackground, fontSize = 17.sp)
        Spacer(modifier = Modifier.width(16.dp).weight(1f))
        Text(value, color = palette.textMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Choices drawn like HyperOS's popup cards: the chosen row carries an accent wash, accent text and a check. */
@Composable
private fun <T> ChoiceCard(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: ((T) -> String)? = null,
    onSelect: (T) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Card {
        options.forEach { option ->
            val isSelected = option == selected
            val wash by animateColorAsState(
                targetValue = palette.accent.copy(alpha = if (!isSelected) 0f else if (palette.isLight) 0.10f else 0.18f),
                animationSpec = tween(180),
                label = "hyperos_choice"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(wash)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 22.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label(option),
                        color = if (isSelected) palette.accent else palette.onBackground,
                        fontSize = 17.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                    if (description != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(description(option), color = palette.textMuted, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                }
                if (isSelected) {
                    Spacer(modifier = Modifier.width(14.dp))
                    Icon(Icons.Rounded.Check, null, tint = palette.accent, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (palette.isLight) palette.surface else palette.surfaceVariant),
        content = content
    )
}

@Composable
private fun Label(text: String) {
    HyperOsSectionLabel(text, Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 10.dp))
}

@Composable
private fun Footer(text: String) {
    val palette = LocalAppTheme.current.palette
    Text(
        text = text,
        color = palette.textMuted,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 10.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// Storage
// ---------------------------------------------------------------------------------------------

private data class StorageLayer(
    val label: String,
    val bytes: Long,
    val color: Color,
    val isFreeSpace: Boolean = false,
    val onClick: (() -> Unit)? = null
)

@Composable
private fun StorageContent(
    internalFreeBytes: Long,
    internalTotalBytes: Long,
    sdCardFreeBytes: Long?,
    sdCardTotalBytes: Long?,
    trashedCount: Int,
    loadStorageStats: suspend () -> MediaRepository.MediaStorageStats?,
    onEmptyTrash: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val stats by produceState<MediaRepository.MediaStorageStats?>(initialValue = null) { value = loadStorageStats() }
    val current = stats
    val volumes = storageVolumes(current, internalFreeBytes, internalTotalBytes, sdCardFreeBytes, sdCardTotalBytes)
    // One cylinder per volume; swipe sideways to the SD card.
    StorageVolumePager(volumes = volumes, pageSpacing = 0.dp) { volume ->
        // The used space rises into the cylinder whenever this page appears.
        val growth = remember { Animatable(0f) }
        LaunchedEffect(Unit) { growth.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = (if (volumes.size > 1) "${volume.label}: " else "") +
                    strings.hyperStorageSummary(formatStorageSize(volume.used), formatStorageSize(volume.totalBytes), formatStorageSize(volume.free)),
                color = palette.textMuted,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            // Top to bottom, as stacked in the cylinder.
            val layers = listOf(
                StorageLayer(strings.storageAvailable, volume.free, Color.Transparent, isFreeSpace = true),
                StorageLayer(strings.legendPhotos, volume.photos, Color(0xFFFF9A2E), onClick = onOpenLibrary),
                StorageLayer(strings.legendVideos, volume.videos, Color(0xFFC94BF0), onClick = onOpenLibrary),
                StorageLayer(strings.recentlyDeleted, volume.trash, Color(0xFFFF5252), onClick = onOpenTrash),
                StorageLayer(strings.legendOther, volume.other, Color(0xFF5D6886))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 16.dp, top = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StorageCylinder(
                    layers = layers,
                    totalBytes = volume.total,
                    growth = { growth.value },
                    modifier = Modifier
                        .weight(0.46f)
                        .height(430.dp)
                )
                Spacer(modifier = Modifier.width(26.dp))
                Column(
                    modifier = Modifier.weight(0.54f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    layers.forEach { StorageLegendRow(it) }
                }
            }
        }
    }

    if (trashedCount > 0) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .padding(start = 28.dp, end = 28.dp, top = 34.dp)
                .fillMaxWidth()
                .pressScale(interaction, 0.97f)
                .height(58.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = if (palette.isLight) 0.12f else 0.20f))
                .clickable(interactionSource = interaction, indication = ripple(color = palette.accent), onClick = onEmptyTrash),
            contentAlignment = Alignment.Center
        ) {
            Text(strings.emptyTrashAction, color = palette.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StorageLegendRow(layer: StorageLayer) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (layer.onClick != null) Modifier.clickable(onClick = layer.onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (layer.isFreeSpace) palette.textMuted.copy(alpha = 0.45f) else layer.color)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(layer.label, color = palette.textMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = formatStorageSize(layer.bytes),
                color = palette.onBackground,
                fontSize = 22.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp)
            )
        }
        if (layer.onClick != null) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = palette.textMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * HyperOS's storage cylinder: the used categories stacked as shaded layers from the bottom, each boundary
 * curving round the front, inside a faint glass tube whose empty top part is the free space.
 */
@Composable
private fun StorageCylinder(
    layers: List<StorageLayer>,
    totalBytes: Long,
    growth: () -> Float,
    modifier: Modifier = Modifier
) {
    val light = LocalAppTheme.current.palette.isLight
    Canvas(modifier = modifier) {
        val w = size.width
        val e = w * 0.26f
        val bodyTop = e / 2f
        val bodyBottom = size.height - e / 2f
        val body = bodyBottom - bodyTop
        val minPx = 6.dp.toPx()

        // Used layers from the bottom up. Tiny categories still show as a thin ring.
        val used = layers.filter { !it.isFreeSpace }.reversed()
        val raw = used.map { it.bytes.toFloat() / totalBytes * body }
        val boosted = raw.map { if (it > 0f && it < minPx) minPx else it }
        val extra = boosted.sum() - raw.sum()
        val large = boosted.filter { it > minPx }.sum()
        val heights = boosted.map { if (it > minPx && large > 0f) (it - extra * it / large).coerceAtLeast(minPx) else it }
        val g = growth()

        drawOval(
            Color.Black.copy(alpha = if (light) 0.10f else 0.35f),
            topLeft = Offset(w * 0.06f, bodyBottom - e * 0.1f),
            size = Size(w * 0.88f, e * 0.9f)
        )
        val glass = if (light) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.07f)
        drawOval(glass, topLeft = Offset(0f, bodyBottom - e / 2f), size = Size(w, e))
        drawRect(glass, topLeft = Offset(0f, bodyTop), size = Size(w, body))

        var yBottom = bodyBottom
        var topColor: Color? = null
        used.forEachIndexed { index, layer ->
            val h = heights[index] * g
            if (h <= 0.5f) return@forEachIndexed
            val yTop = yBottom - h
            val shade = Brush.horizontalGradient(
                0f to lerp(layer.color, Color.White, 0.14f),
                0.55f to layer.color,
                1f to lerp(layer.color, Color.Black, 0.30f),
                startX = 0f,
                endX = w
            )
            // The lower half of this layer's bottom ellipse covers the layer beneath: the boundary curves forward.
            drawOval(shade, topLeft = Offset(0f, yBottom - e / 2f), size = Size(w, e))
            drawRect(shade, topLeft = Offset(0f, yTop), size = Size(w, h))
            yBottom = yTop
            topColor = layer.color
        }
        topColor?.let { drawOval(lerp(it, Color.White, 0.22f), topLeft = Offset(0f, yBottom - e / 2f), size = Size(w, e)) }

        drawOval(glass, topLeft = Offset(0f, bodyTop - e / 2f), size = Size(w, e))
        drawOval(
            if (light) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.16f),
            topLeft = Offset(0f, bodyTop - e / 2f),
            size = Size(w, e),
            style = Stroke(1.dp.toPx())
        )
    }
}
