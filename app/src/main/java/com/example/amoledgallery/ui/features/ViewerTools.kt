package com.example.amoledgallery.ui.features

import com.example.amoledgallery.ui.theme.isLight
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.design.DialogButton
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.ListRow
import com.example.amoledgallery.ui.design.OverlayWindow
import com.example.amoledgallery.ui.design.ThemedDialog
import com.example.amoledgallery.ui.design.ThemedListSection
import com.example.amoledgallery.ui.design.ThemedSegmentedControl
import com.example.amoledgallery.ui.design.ThemedSheet
import com.example.amoledgallery.ui.design.ThemedTextField
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// ---------------------------------------------------------------------------------------------
// Slideshow
// ---------------------------------------------------------------------------------------------

/**
 * Full-screen slideshow of the photos (videos are left out) starting at [start]. A tap shows the controls:
 * time per photo, loop, shuffle and close. The screen stays on while it runs.
 */
@Composable
fun SlideshowOverlay(items: List<MediaItem>, start: Int) {
    val photos = remember(items) { items.filterNot { it.isVideo } }
    val startItem = items.getOrNull(start)
    val prefs = LocalContext.current.getSharedPreferences("prism_slideshow", android.content.Context.MODE_PRIVATE)
    var seconds by remember { mutableIntStateOf(prefs.getInt("seconds", 4)) }
    var loop by remember { mutableStateOf(prefs.getBoolean("loop", true)) }
    var shuffle by remember { mutableStateOf(false) }
    var order by remember(photos, shuffle) {
        mutableStateOf(if (shuffle) photos.shuffled() else photos)
    }
    var index by remember(order) {
        mutableIntStateOf(order.indexOfFirst { it.id == startItem?.id }.coerceAtLeast(0))
    }
    var controls by remember { mutableStateOf(false) }
    val close = { Features.closeDialog() }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(seconds, loop) {
        prefs.edit().putInt("seconds", seconds).putBoolean("loop", loop).apply()
    }
    LaunchedEffect(index, seconds, order, loop) {
        delay(seconds * 1000L)
        if (index < order.lastIndex) index++ else if (loop) index = 0 else close()
    }

    OverlayWindow { _ ->
        BackHandler { close() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls }
        ) {
            if (order.isEmpty()) {
                LaunchedEffect(Unit) { close() }
            } else {
                Crossfade(targetState = order[index.coerceIn(0, order.lastIndex)], animationSpec = tween(900), label = "slide") { item ->
                    AsyncImage(model = item.uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            AnimatedVisibility(
                visible = controls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val palette = LocalAppTheme.current.palette
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.surface.copy(alpha = 0.92f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(F.slideInterval, color = palette.onBackground, fontSize = 15.sp)
                    ThemedSegmentedControl(
                        options = listOf(2, 4, 7, 12),
                        selected = seconds,
                        label = { F.seconds(it) },
                        onSelect = { seconds = it }
                    )
                    ThemedSegmentedControl(
                        options = listOf(false, true),
                        selected = loop,
                        label = { if (it) F.loop else "1×" },
                        onSelect = { loop = it }
                    )
                    ThemedSegmentedControl(
                        options = listOf(false, true),
                        selected = shuffle,
                        label = { if (it) F.shuffle else strings.sortStandard },
                        onSelect = { shuffle = it }
                    )
                }
            }
            AnimatedVisibility(
                visible = controls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
            ) {
                com.example.amoledgallery.ui.design.ThemedIconButton(Icons.Rounded.Close, strings.close, onClick = close, hazeState = null)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Date & time
// ---------------------------------------------------------------------------------------------

/** Android's date picker, then its time picker; the result is written into the photo's EXIF. */
@Composable
fun EditDateDialog(item: MediaItem) {
    val context = LocalContext.current
    val env = LocalFeatureEnv.current
    val dark = !LocalAppTheme.current.palette.isLight
    LaunchedEffect(item.id) {
        if (!MediaOps.canWriteExif(item)) {
            env.vm.message(F.onlyPhotos)
            Features.closeDialog()
            return@LaunchedEffect
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = if (item.date > 0) item.date * 1000L else System.currentTimeMillis() }
        val theme = if (dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        val dateDialog = android.app.DatePickerDialog(context, theme, { _, y, m, d ->
            calendar.set(y, m, d)
            val timeDialog = android.app.TimePickerDialog(context, theme, { _, h, min ->
                calendar.set(Calendar.HOUR_OF_DAY, h)
                calendar.set(Calendar.MINUTE, min)
                calendar.set(Calendar.SECOND, 0)
                val millis = calendar.timeInMillis
                env.vm.runWithWriteAccess(listOf(item)) {
                    MediaOps.setDateTaken(context, item, millis)
                    env.vm.message(F.dateSaved)
                }
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
            timeDialog.setTitle(F.editDateTime)
            timeDialog.show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        dateDialog.setTitle(F.editDateTime)
        dateDialog.show()
        Features.closeDialog()
    }
}

// ---------------------------------------------------------------------------------------------
// Location
// ---------------------------------------------------------------------------------------------

/** Remove the location, or set it to a town picked from the offline list. */
@Composable
fun EditLocationDialog(item: MediaItem) {
    val context = LocalContext.current
    val env = LocalFeatureEnv.current
    if (!MediaOps.canWriteExif(item)) {
        LaunchedEffect(Unit) {
            env.vm.message(F.onlyPhotos)
            Features.closeDialog()
        }
        return
    }
    var query by remember { mutableStateOf("") }
    val ready by produceState(false) { Geo.ensureLoaded(context); value = true }
    val towns = remember(query, ready) { if (ready) Geo.search(query, 8) else emptyList() }
    val close = { Features.closeDialog() }
    ThemedSheet(onDismissRequest = close, title = F.editLocation) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemedListSection(
                inSheet = true,
                rows = listOf(
                    ListRow(F.removeLocation, icon = Icons.Outlined.LocationOff, iconColor = Color(0xFFF14438), showChevron = false, onClick = {
                        close()
                        env.vm.runWithWriteAccess(listOf(item)) {
                            MediaOps.removeLocation(context, item)
                            LocationIndex.invalidate(item)
                            env.vm.message(F.locationRemoved)
                        }
                    })
                )
            )
            ThemedTextField(value = query, onValueChange = { query = it }, placeholder = F.searchTown)
            if (towns.isNotEmpty()) {
                Column {
                    ThemedListSection(
                        inSheet = true,
                        rows = towns.map { town ->
                            ListRow(town.label, icon = Icons.Rounded.LocationOn, iconColor = Color(0xFF34A853), showChevron = false, onClick = {
                                close()
                                env.vm.runWithWriteAccess(listOf(item)) {
                                    MediaOps.setLocation(context, item, town.latitude, town.longitude)
                                    LocationIndex.invalidate(item)
                                    env.vm.message(F.locationSaved)
                                }
                            })
                        }
                    )
                }
            }
            Text(F.geoNamesCredit, color = LocalAppTheme.current.palette.textMuted, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Share options
// ---------------------------------------------------------------------------------------------

/** Share the original, or a prepared copy: without location, as JPEG or smaller. */
@Composable
fun ShareOptionsSheet(items: List<MediaItem>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val close = { Features.closeDialog() }
    fun share(mode: MediaOps.ShareMode?) {
        close()
        scope.launch {
            val uris: List<Uri> = withContext(Dispatchers.IO) {
                items.mapNotNull { item -> if (mode == null) item.uri else MediaOps.prepareShare(context, item, mode) }
            }
            if (uris.isEmpty()) return@launch
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
            }.apply {
                type = when {
                    items.all { it.isVideo } -> "video/*"
                    items.none { it.isVideo } -> "image/*"
                    else -> "*/*"
                }
                clipData = android.content.ClipData.newRawUri(null, uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, strings.shareMedia))
        }
    }
    ThemedSheet(onDismissRequest = close, title = F.shareOptions) {
        val accent = LocalAppTheme.current.palette.accent
        ThemedListSection(
            inSheet = true,
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
            rows = listOf(
                ListRow(F.shareOriginal, icon = Icons.Outlined.Share, iconColor = accent, showChevron = false, onClick = { share(null) }),
                ListRow(F.shareWithoutLocation, icon = Icons.Outlined.LocationOff, iconColor = accent, showChevron = false, onClick = { share(MediaOps.ShareMode.WITHOUT_LOCATION) }),
                ListRow(F.shareAsJpeg, icon = Icons.Outlined.Image, iconColor = accent, showChevron = false, onClick = { share(MediaOps.ShareMode.AS_JPEG) }),
                ListRow(F.shareSmaller, icon = Icons.Outlined.PhotoSizeSelectLarge, iconColor = accent, showChevron = false, onClick = { share(MediaOps.ShareMode.SMALLER) })
            )
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Copy to album
// ---------------------------------------------------------------------------------------------

@Composable
fun CopyToAlbumSheet(items: List<MediaItem>) {
    val env = LocalFeatureEnv.current
    val albums by env.vm.albumsState.collectAsStateWithLifecycle()
    val close = { Features.closeDialog() }
    ThemedSheet(onDismissRequest = close, title = F.copyToAlbum) {
        val accent = LocalAppTheme.current.palette.accent
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            ThemedListSection(
                inSheet = true,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp),
                rows = albums.sortedBy { it.name.lowercase() }.map { album ->
                    ListRow(
                        album.name,
                        subtitle = album.relativePath.trimEnd('/'),
                        icon = Icons.Rounded.Folder,
                        iconColor = accent,
                        showChevron = false,
                        onClick = {
                            close()
                            env.vm.copyItems(items, album)
                        }
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Photo widget
// ---------------------------------------------------------------------------------------------

@Composable
fun PhotoWidgetHelpDialog() {
    ThemedDialog(
        onDismissRequest = { Features.closeDialog() },
        title = F.photoWidget,
        message = F.photoWidgetHelp + "\n\n" + F.photoWidgetSubtitle,
        buttons = listOf(DialogButton(strings.done, DialogButtonStyle.Primary) { Features.closeDialog() })
    )
}

/** Spacer helper for rows of buttons. */
@Composable
internal fun VSpace(height: Int) = Spacer(modifier = Modifier.height(height.dp))
