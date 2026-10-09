package com.example.amoledgallery.ui.screens

import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.PerformanceProfile
import com.example.amoledgallery.data.MediaRepository
import com.example.amoledgallery.ui.design.GeminiStorageRing
import com.example.amoledgallery.ui.design.geminiGlowColors
import com.example.amoledgallery.ui.design.GeminiSparkle
import com.example.amoledgallery.ui.design.ThemedProgressBar
import com.example.amoledgallery.ui.design.bottomBarContentPadding
import com.example.amoledgallery.ui.design.geminiGlass
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
import kotlin.math.roundToInt

/*
 * Google (Gemini): the menu and settings pages in Gemini's style - a glowing translucent storage box, drawer-like
 * lists with plain outlined icons and large text, choices that fill into a soft pill with a check, and a title
 * on the left with a close button on the right.
 */

@Composable
internal fun GoogleMenuContent(
    internalFreeBytes: Long,
    internalTotalBytes: Long,
    sdCardFreeBytes: Long?,
    sdCardTotalBytes: Long?,
    loadStorageStats: suspend () -> MediaRepository.MediaStorageStats?,
    favoriteCount: Int,
    trashedCount: Int,
    storageText: String,
    usedFraction: Float,
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
        val shape = RoundedCornerShape(32.dp)
        val menuStats by produceState<MediaRepository.MediaStorageStats?>(initialValue = null) { value = loadStorageStats() }
        // Swipe from internal storage to the SD card when one is mounted.
        StorageVolumePager(
            volumes = storageVolumes(menuStats, internalFreeBytes, internalTotalBytes, sdCardFreeBytes, sdCardTotalBytes)
        ) { volume ->
            val interaction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScale(interaction, 0.98f)
                    .geminiGlass(shape, state = null, tintAlpha = 0.5f)
                    .clickable(interactionSource = interaction, indication = null, onClick = onOpenStorage)
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GeminiSparkle(Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                if (volume.isSdCard) volume.label else strings.deviceStorage,
                                color = palette.onBackground,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            strings.freeOf(formatStorageSize(volume.free), formatStorageSize(volume.totalBytes)),
                            color = palette.textMuted,
                            fontSize = 15.sp
                        )
                    }
                    GeminiStorageRing(
                        usedFraction = volume.usedFraction,
                        thickness = 6.dp,
                        modifier = Modifier.size(96.dp)
                    ) {
                        Text("${(volume.usedFraction * 100).roundToInt()}%", color = palette.onBackground, fontSize = 17.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel(strings.library)
        DrawerRow(Icons.Outlined.FavoriteBorder, strings.favorites, "$favoriteCount", onOpenFavorites)
        DrawerRow(Icons.Outlined.Delete, strings.trash, "$trashedCount", onOpenTrash)
        DrawerRow(Icons.Outlined.ContentCopy, strings.duplicates, null, onOpenDuplicates)
        DrawerRow(Icons.Outlined.Build, com.example.amoledgallery.ui.i18n.F.tools, null) { com.example.amoledgallery.ui.features.Features.open(com.example.amoledgallery.ui.features.FeatureScreen.Tools) }
        DrawerRow(Icons.Outlined.Lock, com.example.amoledgallery.ui.i18n.F.privateAlbum, null) { com.example.amoledgallery.ui.features.Features.open(com.example.amoledgallery.ui.features.FeatureScreen.PrivateAlbum) }
        Spacer(modifier = Modifier.height(14.dp))
        SectionLabel("App")
        DrawerRow(Icons.Outlined.Palette, strings.appearance, null, onOpenDesign)
        DrawerRow(Icons.Outlined.Settings, strings.settings, null, onOpenSettings)
        DrawerRow(Icons.Outlined.Info, strings.info, appVersionName.ifBlank { null }, onOpenInfo)
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = palette.onBackground, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.width(24.dp))
        Text(title, color = palette.onBackground, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = palette.textMuted, fontSize = 16.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val palette = LocalAppTheme.current.palette
    Text(text, color = palette.textMuted, fontSize = 17.sp, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp))
}

@Composable
private fun Footer(text: String) {
    val palette = LocalAppTheme.current.palette
    Text(text, color = palette.textMuted, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
}

@Composable
internal fun GoogleSettingsPage(
    section: SettingsSection,
    onBack: () -> Unit,
    internalFreeBytes: Long,
    internalTotalBytes: Long,
    sdCardFreeBytes: Long?,
    sdCardTotalBytes: Long?,
    appVersionName: String,
    trashedCount: Int,
    graphicsQuality: PerformanceProfile,
    onSetGraphicsQuality: (PerformanceProfile) -> Unit,
    loadStorageStats: suspend () -> MediaRepository.MediaStorageStats?,
    onEmptyTrash: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val title = when (section) {
        SettingsSection.DESIGN -> strings.appearance
        SettingsSection.SETTINGS -> strings.settings
        SettingsSection.STORAGE -> strings.deviceStorage
        SettingsSection.INFO -> strings.info
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = bottomBarContentPadding(24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(start = 28.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = palette.onBackground, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, strings.back, tint = palette.onBackground, modifier = Modifier.size(28.dp))
            }
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            when (section) {
                SettingsSection.DESIGN -> AppearanceContent()
                SettingsSection.SETTINGS -> GeneralContent(graphicsQuality, onSetGraphicsQuality)
                SettingsSection.STORAGE -> StorageContent(
                    internalFreeBytes, internalTotalBytes, sdCardFreeBytes, sdCardTotalBytes,
                    trashedCount, loadStorageStats, onEmptyTrash, onOpenLibrary, onOpenTrash
                )
                SettingsSection.INFO -> InfoContent(appVersionName)
            }
        }
    }
}

/** Gemini choices: the chosen row fills into a soft pill with a check. */
@Composable
private fun <T> ChoiceList(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: ((T) -> String)? = null,
    onSelect: (T) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    options.forEach { option ->
        val isSelected = option == selected
        val fill by animateColorAsState(
            targetValue = if (isSelected) palette.surface else palette.surface.copy(alpha = 0f),
            label = "gemini_choice"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(fill)
                .clickable { onSelect(option) }
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label(option), color = palette.onBackground, fontSize = 19.sp)
                if (description != null) {
                    Text(description(option), color = palette.textMuted, fontSize = 14.sp, lineHeight = 18.sp)
                }
            }
            if (isSelected) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Rounded.Check, null, tint = palette.accent, modifier = Modifier.size(24.dp))
            }
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

    SectionLabel(strings.design)
    ChoiceList(DesignLanguage.entries, currentLanguage, { it.displayName }, { strings.designDescription(it) }) { language ->
        val preset = presetsFor(language, lightMode).firstOrNull() ?: presetsFor(language).first()
        ThemeState.apply(language, preset, context = context)
    }
    Footer(strings.designFooterText)
    SectionLabel(strings.appearanceModeHeader)
    ChoiceList(listOf(true, false), lightMode, { if (it) strings.light else strings.dark }) { switchPaletteBrightness(context, it) }
    SectionLabel(strings.accentColorHeader)
    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        PaletteRow(
            presets = presetsFor(currentLanguage, lightMode),
            currentPaletteId = currentPalette.id,
            onSelectPreset = { preset -> ThemeState.apply(currentLanguage, preset, context = context) },
            onOpenCustomEditor = { showCustomEditor = true }
        )
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
private fun GeneralContent(graphicsQuality: PerformanceProfile, onSetGraphicsQuality: (PerformanceProfile) -> Unit) {
    val context = LocalContext.current
    SectionLabel(strings.language)
    ChoiceList(AppLanguage.entries, LanguageState.language, { it.displayName }) { LanguageState.apply(it, context) }
    Footer(strings.languageFooterText)
    SectionLabel(strings.graphicsQuality)
    ChoiceList(PerformanceProfile.entries, graphicsQuality, { graphicsQualityLabel(it) }) { onSetGraphicsQuality(it) }
    Footer(strings.qualityDescription(graphicsQuality))
}

@Composable
private fun InfoContent(appVersionName: String) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    val appName = APP_FULL_NAME
    listOf(
        strings.appNameLabel to appName,
        strings.appVersion to appVersionName.ifBlank { strings.unknown },
        strings.design to ThemeState.designLanguage.displayName,
        strings.colorPaletteLabel to strings.paletteLabel(ThemeState.palette),
        strings.language to LanguageState.language.displayName
    ).forEach { (title, value) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = palette.onBackground, fontSize = 19.sp, modifier = Modifier.weight(1f))
            Text(value, color = palette.textMuted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

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
    val internal = volumes.first()
    val legendColors = geminiGlowColors()

    // One glowing card per volume; swipe sideways to the SD card.
    StorageVolumePager(volumes = volumes, modifier = Modifier.padding(top = 6.dp)) { volume ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .geminiGlass(RoundedCornerShape(32.dp), state = null, tintAlpha = 0.5f)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            if (volumes.size > 1) {
                Text(volume.label, color = palette.onBackground, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            // The glowing ring with the used space in its middle and the free space underneath.
            GeminiStorageRing(
                usedFraction = volume.usedFraction,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(270.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatStorageSize(volume.used), color = palette.onBackground, fontSize = 34.sp)
                    Text(strings.usedWord, color = palette.textMuted, fontSize = 16.sp)
                    Text("${(volume.usedFraction * 100).roundToInt()}%", color = palette.textMuted, fontSize = 14.sp)
                }
            }
            Text(
                "${formatStorageSize(volume.free)} ${strings.storageAvailable} · ${formatStorageSize(volume.totalBytes)}",
                color = palette.textMuted,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(18.dp))
            LegendRow(legendColors[0], strings.legendPhotos, volume.photos, onOpenLibrary)
            LegendRow(legendColors[1], strings.legendVideos, volume.videos, onOpenLibrary)
            LegendRow(legendColors[2], strings.recentlyDeleted, volume.trash, onOpenTrash)
            LegendRow(legendColors[3], strings.legendOther, volume.other, null)
        }
    }
    val photos = volumes.sumOf { it.photos }
    val videos = volumes.sumOf { it.videos }
    val trash = volumes.sumOf { it.trash }

    Spacer(modifier = Modifier.height(14.dp))
    SectionLabel(strings.storageRecommendations)
    if (trashedCount > 0) {
        Suggestion(
            text = if (current == null) strings.storageCalculating else strings.freeUpTrash(formatStorageSize(trash)),
            button = strings.emptyTrashAction,
            onClick = onEmptyTrash
        )
    }
    Suggestion(
        text = if (current == null) strings.storageCalculating else strings.reviewMediaMessage(formatStorageSize(photos + videos)),
        button = strings.reviewMedia,
        onClick = onOpenLibrary
    )
}

@Composable
private fun LegendRow(color: Color, label: String, bytes: Long, onClick: (() -> Unit)?) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = palette.onBackground, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(formatStorageSize(bytes), color = palette.textMuted, fontSize = 16.sp)
    }
}

@Composable
private fun Suggestion(text: String, button: String, onClick: () -> Unit) {
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(text, color = palette.onBackground, fontSize = 16.sp, lineHeight = 22.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .pressScale(interaction, 0.95f)
                .clip(CircleShape)
                .background(palette.surfaceVariant)
                .clickable(interactionSource = interaction, indication = ripple(color = palette.onBackground), onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text(button, color = palette.onBackground, fontSize = 16.sp, maxLines = 1)
        }
    }
}
