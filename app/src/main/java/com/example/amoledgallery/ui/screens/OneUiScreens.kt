package com.example.amoledgallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.PerformanceProfile
import com.example.amoledgallery.data.MediaRepository
import com.example.amoledgallery.ui.design.OverlayWindow
import com.example.amoledgallery.ui.design.ThemedRadioIndicator
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
 * Samsung One UI 8: the Gallery's "Menü" popup with round icons, and pages built like Samsung Settings -
 * a big centred title in the upper third, a back chevron under it, grouped rounded cards with coloured
 * circle icons, radio choices, and the "Speicher verwalten" storage card.
 */

// ---------------------------------------------------------------------------------------------
// Menü popup
// ---------------------------------------------------------------------------------------------

private data class PopupAction(val icon: ImageVector, val label: String, val onClick: () -> Unit, val isLocked: Boolean = false)

/** Samsung Gallery's "Menü": a card at the bottom of the screen with round icon buttons. */
@Composable
fun OneUiMenuPopup(
    onDismiss: () -> Unit,
    onVideos: () -> Unit,
    onFavorites: () -> Unit,
    onRecent: () -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    trashLockEnabled: Boolean = true
) {
    OverlayWindow { transition ->
        val palette = LocalAppTheme.current.palette
        val progress by transition.animateFloat(
            transitionSpec = { if (targetState) tween(280, easing = FastOutSlowInEasing) else tween(180) },
            label = "oneui_menu_popup"
        ) { if (it) 1f else 0f }
        BackHandler(enabled = transition.targetState) { onDismiss() }

        val actions = listOf(
            PopupAction(Icons.Outlined.SmartDisplay, strings.legendVideos, onVideos),
            PopupAction(Icons.Outlined.FavoriteBorder, strings.favorites, onFavorites),
            PopupAction(Icons.Outlined.Schedule, strings.recent, onRecent),
            PopupAction(Icons.Outlined.Delete, strings.trash, onTrash, isLocked = trashLockEnabled),
            PopupAction(Icons.Outlined.Settings, strings.settings, onSettings)
        )
        val light = palette.isLight

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
                    .background(Color.Black.copy(alpha = 0.5f))
                    .then(
                        if (transition.targetState) Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } } else Modifier
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 60.dp.toPx()
                        val s = 0.96f + 0.04f * progress
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (light) Color.White else Color(0xFF232325))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 8.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                actions.chunked(4).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { action ->
                            val interaction = remember { MutableInteractionSource() }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(interactionSource = interaction, indication = null) {
                                        onDismiss()
                                        action.onClick()
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .pressScale(interaction, 0.9f)
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(if (light) Color(0xFFEDEDED) else Color(0xFF3A3A3C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(action.icon, null, tint = palette.onBackground, modifier = Modifier.size(28.dp))
                                    if (action.isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(3.dp)
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(palette.accent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Lock, null, tint = palette.onAccent, modifier = Modifier.size(11.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                // One line that shrinks to fit: long words like "Einstellungen" would otherwise break mid-word.
                                var labelSize by remember(action.label) { mutableStateOf(16f) }
                                Text(
                                    text = action.label,
                                    color = palette.onBackground,
                                    fontSize = labelSize.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    onTextLayout = { if (it.didOverflowWidth && labelSize > 11f) labelSize -= 0.5f },
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings root
// ---------------------------------------------------------------------------------------------

@Composable
internal fun OneUiMenuContent(
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card {
            IconRow(Icons.Rounded.Favorite, Color(0xFFF2586E), strings.favorites, strings.items(favoriteCount), onOpenFavorites)
            Divider(start = 78.dp)
            IconRow(
                Icons.Rounded.Delete,
                Color(0xFFF4731F),
                strings.trash,
                strings.items(trashedCount),
                onOpenTrash,
                trailingIcon = if (trashLockEnabled) Icons.Rounded.Lock else null
            )
            Divider(start = 78.dp)
            IconRow(Icons.Rounded.ContentCopy, Color(0xFF00A9A5), strings.duplicates, strings.duplicatesSubtitle, onOpenDuplicates)
        }
        Card {
            IconRow(Icons.Rounded.Palette, Color(0xFF7B61F0), strings.appearance, ThemeState.designLanguage.displayName, onOpenDesign)
            Divider(start = 78.dp)
            IconRow(Icons.Rounded.Settings, Color(0xFF3E7BFA), strings.general, strings.graphicsQuality, onOpenSettings)
        }
        Card {
            IconRow(Icons.Rounded.Storage, Color(0xFF4CC34F), strings.storage, storageValue, onOpenStorage)
            Divider(start = 78.dp)
            IconRow(Icons.Rounded.Info, Color(0xFF8E8E93), strings.info, appVersionName.ifBlank { null }, onOpenInfo)
        }
    }
}

@Composable
private fun IconRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null
) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.onBackground, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = palette.accent, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(trailingIcon, null, tint = palette.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings pages
// ---------------------------------------------------------------------------------------------

@Composable
internal fun OneUiSettingsPage(
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
    val scroll = rememberScrollState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val collapsePx = with(LocalDensity.current) { 180.dp.toPx() }
    val title = when (section) {
        SettingsSection.DESIGN -> strings.appearance
        SettingsSection.SETTINGS -> strings.general
        SettingsSection.STORAGE -> strings.manageStorage
        SettingsSection.INFO -> strings.info
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(bottom = bottomBarContentPadding(24.dp))
    ) {
        if (section == SettingsSection.STORAGE) {
            // "Speicher verwalten" uses a compact bar: chevron and title side by side.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(76.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackChevron(onBack)
                Text(title, color = palette.onBackground, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((screenHeight * 0.30f).coerceAtLeast(200.dp))
                    .graphicsLayer {
                        val f = (scroll.value / collapsePx).coerceIn(0f, 1f)
                        alpha = (1f - f * 1.4f).coerceIn(0f, 1f)
                        translationY = f * 60.dp.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = palette.onBackground,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp)
                )
            }
            Box(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) { BackChevron(onBack) }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
}

@Composable
private fun BackChevron(onBack: () -> Unit) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, strings.back, tint = palette.onBackground, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AppearanceContent() {
    val context = LocalContext.current
    val currentLanguage = ThemeState.designLanguage
    val currentPalette = ThemeState.palette
    val lightMode = currentPalette.isLight
    var showCustomEditor by remember { mutableStateOf(false) }

    Header(strings.design)
    ChoiceCard(DesignLanguage.entries, currentLanguage, { it.displayName }, { strings.designDescription(it) }) { language ->
        val preset = presetsFor(language, lightMode).firstOrNull() ?: presetsFor(language).first()
        ThemeState.apply(language, preset, context = context)
    }
    Header(strings.appearanceModeHeader)
    ChoiceCard(listOf(true, false), lightMode, { if (it) strings.light else strings.dark }) { switchPaletteBrightness(context, it) }
    Header(strings.accentColorHeader)
    Card {
        Box(modifier = Modifier.padding(20.dp)) {
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
    Header(strings.language)
    ChoiceCard(AppLanguage.entries, LanguageState.language, { it.displayName }) { LanguageState.apply(it, context) }
    Footer(strings.languageFooterText)
    Header(strings.graphicsQuality)
    ChoiceCard(PerformanceProfile.entries, graphicsQuality, { graphicsQualityLabel(it) }) { onSetGraphicsQuality(it) }
    Footer(strings.qualityDescription(graphicsQuality))
    Header(strings.lockTrashSetting)
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
    val rows = listOf(
        strings.appNameLabel to appName,
        strings.appVersion to appVersionName.ifBlank { strings.unknown },
        strings.design to ThemeState.designLanguage.displayName,
        strings.colorPaletteLabel to strings.paletteLabel(ThemeState.palette),
        strings.language to LanguageState.language.displayName
    )
    Card {
        rows.forEachIndexed { index, (title, value) ->
            if (index > 0) Divider(start = 22.dp)
            val palette = LocalAppTheme.current.palette
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 15.dp)) {
                Text(title, color = palette.onBackground, fontSize = 19.sp)
                Text(value, color = palette.accent, fontSize = 15.sp)
            }
        }
    }
}

/** Samsung choice list: title (and grey description) with a radio button on the right. */
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
        options.forEachIndexed { index, option ->
            if (index > 0) Divider(start = 22.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) }
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label(option), color = palette.onBackground, fontSize = 19.sp)
                    if (description != null) {
                        Text(description(option), color = palette.textMuted, fontSize = 14.sp, lineHeight = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                ThemedRadioIndicator(selected = option == selected)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
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
private fun Divider(start: androidx.compose.ui.unit.Dp) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = Modifier
            .padding(start = start, end = 20.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.onBackground.copy(alpha = 0.08f))
    )
}

@Composable
private fun Header(text: String) {
    val palette = LocalAppTheme.current.palette
    Text(
        text = text,
        color = palette.textMuted,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 22.dp, top = 8.dp)
    )
}

@Composable
private fun Footer(text: String) {
    val palette = LocalAppTheme.current.palette
    Text(text, color = palette.textMuted, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 22.dp))
}

// ---------------------------------------------------------------------------------------------
// Storage
// ---------------------------------------------------------------------------------------------

private val OneUiPhotoColor = Color(0xFFF0506E)
private val OneUiVideoColor = Color(0xFF8E54E9)
private val OneUiTrashColor = Color(0xFFB4DC1E)
private val OneUiOtherColor = Color(0xFF34B6D6)

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
    val light = palette.isLight
    val stats by produceState<MediaRepository.MediaStorageStats?>(initialValue = null) { value = loadStorageStats() }
    val current = stats
    val volumes = storageVolumes(current, internalFreeBytes, internalTotalBytes, sdCardFreeBytes, sdCardTotalBytes)
    val photos = volumes.sumOf { it.photos }
    val videos = volumes.sumOf { it.videos }
    val trash = volumes.sumOf { it.trash }

    // One storage card per volume with its soft purple glow; swipe sideways to the SD card.
    StorageVolumePager(volumes = volumes) { volume ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (light) {
                        Brush.verticalGradient(listOf(Color(0xFFF3ECF6), Color(0xFFFFFFFF)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFF3B3240), Color(0xFF2A2A2E)))
                    }
                )
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            val total = volume.total
            // The bar and the percentage fill up from 0 when the card appears.
            val grow = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                grow.animateTo(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
            val g = grow.value
            Text(volume.label, color = palette.onBackground, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${(volume.used * 100f / total * g).toInt()}%", color = palette.onBackground, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(" ${strings.usedWord}", color = palette.onBackground, fontSize = 17.sp, modifier = Modifier.padding(bottom = 7.dp))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${formatStorageSize(volume.used)} / ${formatStorageSize(volume.totalBytes)}",
                    color = palette.onBackground,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(CircleShape)
                    .background(if (light) Color(0xFFD9D9DD) else Color(0xFF58585C))
            ) {
                listOf(volume.photos to OneUiPhotoColor, volume.videos to OneUiVideoColor, volume.trash to OneUiTrashColor, volume.other to OneUiOtherColor)
                    .filter { it.first > 0L }
                    .forEach { (bytes, color) ->
                        Box(
                            modifier = Modifier
                                .weight((bytes.toFloat() / total).coerceAtLeast(0.004f) * g + 0.00001f)
                                .fillMaxHeight()
                                .background(color)
                        )
                    }
                val rest = (volume.free.toFloat() + volume.used.toFloat() * (1f - g)) / total
                if (rest > 0f) Spacer(modifier = Modifier.weight(rest))
            }
            Spacer(modifier = Modifier.height(12.dp))
            LegendRow(OneUiPhotoColor, strings.legendPhotos, volume.photos, onOpenLibrary)
            LegendRow(OneUiVideoColor, strings.legendVideos, volume.videos, onOpenLibrary)
            LegendRow(OneUiTrashColor, strings.recentlyDeleted, volume.trash, onOpenTrash)
            LegendRow(OneUiOtherColor, strings.legendOther, volume.other, null)
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.onBackground.copy(alpha = 0.12f)))
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SplitStat(strings.storageAvailable, formatStorageSize(volume.free), Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(44.dp).background(palette.onBackground.copy(alpha = 0.15f)))
                SplitStat(strings.trash, formatStorageSize(volume.trash), Modifier.weight(1f))
            }
        }
    }

    // "Vorschläge": explanatory text with a pill button underneath on the right.
    Text(
        strings.suggestions,
        color = palette.onBackground,
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 22.dp)
    )
    if (trashedCount > 0) {
        Suggestion(
            text = if (current == null) strings.storageCalculating else strings.freeUpTrash(formatStorageSize(trash)),
            button = strings.emptyTrashAction,
            onClick = onEmptyTrash
        )
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(palette.onBackground.copy(alpha = 0.10f)))
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
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = palette.onBackground, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(formatStorageSize(bytes), color = palette.onBackground.copy(alpha = 0.85f), fontSize = 16.sp)
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            tint = palette.onBackground.copy(alpha = if (onClick != null) 0.7f else 0f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SplitStat(label: String, value: String, modifier: Modifier) {
    val palette = LocalAppTheme.current.palette
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = palette.textMuted, fontSize = 16.sp, maxLines = 1)
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, color = palette.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun Suggestion(text: String, button: String, onClick: () -> Unit) {
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = palette.onBackground, fontSize = 17.sp, lineHeight = 23.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .pressScale(interaction, 0.96f)
                .clip(CircleShape)
                .background(palette.onBackground.copy(alpha = if (palette.isLight) 0.08f else 0.16f))
                .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text(button, color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
