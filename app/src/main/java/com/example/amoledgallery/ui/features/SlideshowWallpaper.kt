package com.example.amoledgallery.ui.features

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.amoledgallery.service.SlideshowWallpaperChoice
import com.example.amoledgallery.service.SlideshowWallpaperService
import com.example.amoledgallery.ui.design.ListRow
import com.example.amoledgallery.ui.design.ThemedButton
import com.example.amoledgallery.ui.design.ThemedListSection
import com.example.amoledgallery.ui.design.ThemedSegmentedControl
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.LocalAppTheme

/** Picks the album and the interval of the slideshow wallpaper, then opens Android's wallpaper preview. */
@Composable
fun SlideshowWallpaperScreen() {
    val context = LocalContext.current
    val env = LocalFeatureEnv.current
    val albums by env.vm.albumsState.collectAsStateWithLifecycle()
    val saved = remember { SlideshowWallpaperChoice.read(context) }
    var bucket by remember { mutableStateOf(saved?.first) }
    var minutes by remember { mutableIntStateOf(saved?.second ?: 30) }
    val palette = LocalAppTheme.current.palette
    FeatureMenuPage(title = F.slideshowWallpaper) {
        Text(F.slideshowWallpaperSubtitle, color = palette.textMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Text(F.changeEvery, color = palette.onBackground, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp))
        ThemedSegmentedControl(
            options = listOf(5, 30, 60, 240),
            selected = minutes,
            label = { if (it < 60) F.minutes(it) else F.hours(it / 60) },
            onSelect = { minutes = it }
        )
        ThemedButton(
            F.apply,
            enabled = bucket != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val chosen = bucket ?: return@ThemedButton
                SlideshowWallpaperChoice.write(context, chosen, minutes)
                val component = ComponentName(context, SlideshowWallpaperService::class.java)
                val opened = runCatching {
                    context.startActivity(
                        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
                    )
                    true
                }.getOrDefault(false)
                if (!opened) runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            }
        )
        ThemedListSection(
            header = F.chooseAlbum,
            rows = albums.filter { it.count > 0 }.sortedBy { it.name.lowercase() }.map { album ->
                ListRow(
                    album.name,
                    subtitle = strings.items(album.count),
                    icon = if (album.bucketId == bucket) Icons.Rounded.CheckCircle else Icons.Rounded.Folder,
                    iconColor = palette.accent,
                    showChevron = false,
                    onClick = { bucket = album.bucketId }
                )
            }
        )
    }
}
