package com.example.amoledgallery.ui.features

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.ListRow
import com.example.amoledgallery.ui.design.ThemedButton
import com.example.amoledgallery.ui.design.ThemedEmptyState
import com.example.amoledgallery.ui.design.ThemedListSection
import com.example.amoledgallery.ui.design.ThemedProgressBar
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.i18n.readableSize
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------------------------------------
// Similar photos
// ---------------------------------------------------------------------------------------------

/**
 * Finds series of near-identical photos (bursts, the same scene shot several times): photos taken within a
 * few minutes of each other whose 64-bit difference hashes differ in only a few bits.
 */
object SimilarFinder {
    private val mutex = Mutex()
    private var cache: MutableMap<String, Long>? = null
    private fun file(context: Context) = File(context.cacheDir, "dhash_v1.txt")
    private fun key(item: MediaItem) = "${item.id}:${item.dateModified}"

    /** Difference hash: a 9×8 grey thumbnail, one bit per neighbouring pixel pair (brighter or not). */
    private fun dHash(context: Context, item: MediaItem): Long? = runCatching {
        val thumb = context.contentResolver.loadThumbnail(item.uri, Size(96, 96), null)
        val small = Bitmap.createScaledBitmap(thumb, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (luma(small.getPixel(x, y)) > luma(small.getPixel(x + 1, y))) hash = hash or (1L shl bit)
                bit++
            }
        }
        if (small !== thumb) small.recycle()
        thumb.recycle()
        hash
    }.getOrNull()

    private fun luma(c: Int) = ((c shr 16) and 0xff) * 299 + ((c shr 8) and 0xff) * 587 + (c and 0xff) * 114

    suspend fun find(context: Context, media: List<MediaItem>, onProgress: (Float) -> Unit): List<List<MediaItem>> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val map = cache ?: HashMap<String, Long>().also { m ->
                runCatching {
                    file(context).takeIf { it.exists() }?.forEachLine { line ->
                        val tab = line.indexOf('\t')
                        if (tab > 0) line.substring(tab + 1).toLongOrNull()?.let { m[line.substring(0, tab)] = it }
                    }
                }
                cache = m
            }
            val photos = media.filter { it.isImage && it.date > 0 }.sortedBy { it.date }
            val hashes = HashMap<Long, Long>()
            var changed = false
            photos.forEachIndexed { i, item ->
                val k = key(item)
                val h = map[k] ?: dHash(context, item)?.also { map[k] = it; changed = true }
                if (h != null) hashes[item.id] = h
                if (i % 20 == 0) onProgress(i.toFloat() / photos.size.coerceAtLeast(1))
            }
            if (changed) runCatching {
                val live = photos.mapTo(HashSet()) { key(it) }
                file(context).bufferedWriter().use { out -> map.filterKeys { it in live }.forEach { (k, v) -> out.write("$k\t$v\n") } }
            }
            onProgress(1f)
            // Neighbours in time that look alike form a series.
            val groups = ArrayList<List<MediaItem>>()
            var current = ArrayList<MediaItem>()
            for (item in photos) {
                val h = hashes[item.id] ?: continue
                val last = current.lastOrNull()
                val joins = last != null && item.date - last.date <= 180 &&
                    java.lang.Long.bitCount(h xor (hashes[last.id] ?: 0L)) <= 10
                if (joins) current.add(item) else {
                    if (current.size > 1) groups.add(current)
                    current = arrayListOf(item)
                }
            }
            if (current.size > 1) groups.add(current)
            groups.sortedByDescending { group -> group.first().date }
        }
    }

    /** The photo offered to be kept: the largest one (most pixels, then most bytes - usually the sharpest). */
    fun best(group: List<MediaItem>): MediaItem =
        group.maxWith(compareBy<MediaItem> { it.width.toLong() * it.height }.thenBy { it.size })
}

@Composable
fun SimilarScreen() {
    val context = LocalContext.current
    val env = LocalFeatureEnv.current
    val media by env.vm.rawMedia.collectAsStateWithLifecycle()
    var progress by remember { mutableStateOf<Float?>(0f) }
    var groups by remember { mutableStateOf<List<List<MediaItem>>>(emptyList()) }
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val palette = LocalAppTheme.current.palette
    LaunchedEffect(media) {
        if (media.isEmpty()) { progress = null; return@LaunchedEffect }
        val found = SimilarFinder.find(context, media) { progress = it }
        groups = found
        selection = found.flatMap { g -> val keep = SimilarFinder.best(g); g.filter { it.id != keep.id }.map { it.id } }.toSet()
        progress = null
    }
    val all = groups.flatten()
    val selectedItems = all.filter { it.id in selection }
    FeaturePage(
        title = F.similarPhotos,
        subtitle = if (selectedItems.isNotEmpty()) F.selectedFrees(selectedItems.size, readableSize(selectedItems.sumOf { it.size }))
            else if (groups.isNotEmpty()) "${groups.size}" else null,
        bottomBar = if (selectedItems.isNotEmpty()) {
            { TrashBar(selectedItems) { env.vm.trashItems(selectedItems); selection = emptySet() } }
        } else null
    ) {
        when {
            progress != null -> ScanProgress(F.similarScanning, progress ?: 0f)
            groups.isEmpty() -> ThemedEmptyState(Icons.Rounded.BurstMode, F.similarEmptyTitle, F.similarEmptyMessage)
            else -> MediaSectionGrid(
                sections = groups.map { MediaSection(F.similarSet(it.size), it) },
                selection = selection,
                onToggle = { item -> selection = if (item.id in selection) selection - item.id else selection + item.id },
                bottomPadding = featureBottomPadding(110.dp),
                sectionBadge = { section, item -> if (SimilarFinder.best(section.items).id == item.id) F.keepBest else null }
            )
        }
    }
}

@Composable
internal fun ScanProgress(text: String, progress: Float) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = palette.textMuted, fontSize = 15.sp)
        ThemedProgressBar(progress, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

/** The floating "move to trash" bar under a selection, with what it frees. */
@Composable
internal fun TrashBar(items: List<MediaItem>, onTrash: () -> Unit) {
    // The size this frees is shown in the page's subtitle; the bar itself is the design's selection bar.
    com.example.amoledgallery.ui.components.ThemedActionBar(
        selectedCount = items.size,
        actions = listOf(com.example.amoledgallery.ui.components.BarAction(androidx.compose.material.icons.Icons.Outlined.Delete, strings.delete, onTrash))
    )
}

// ---------------------------------------------------------------------------------------------
// Clean-up suggestions
// ---------------------------------------------------------------------------------------------

enum class CleanKind { OLD_SCREENSHOTS, LARGE_VIDEOS, RAW }

internal fun cleanItems(kind: CleanKind, media: List<MediaItem>): List<MediaItem> {
    val now = System.currentTimeMillis() / 1000
    return when (kind) {
        CleanKind.OLD_SCREENSHOTS -> media.filter { isScreenshot(it) && now - it.date > 30L * 24 * 3600 }
        CleanKind.LARGE_VIDEOS -> media.filter { it.isVideo && it.size > 100L * 1000 * 1000 }.sortedByDescending { it.size }
        CleanKind.RAW -> media.filter { isRaw(it) }
    }.let { if (kind == CleanKind.LARGE_VIDEOS) it else it.sortedBy { item -> item.date } }
}

internal fun cleanTitle(kind: CleanKind) = when (kind) {
    CleanKind.OLD_SCREENSHOTS -> F.oldScreenshots
    CleanKind.LARGE_VIDEOS -> F.largeVideos
    CleanKind.RAW -> F.rawFiles
}

@Composable
fun CleanUpScreen() {
    val env = LocalFeatureEnv.current
    val media by env.vm.rawMedia.collectAsStateWithLifecycle()
    val rows = CleanKind.entries.map { kind ->
        val items = cleanItems(kind, media)
        val icon = when (kind) {
            CleanKind.OLD_SCREENSHOTS -> Icons.Rounded.Screenshot
            CleanKind.LARGE_VIDEOS -> Icons.Rounded.VideoFile
            CleanKind.RAW -> Icons.Rounded.PhotoCamera
        }
        ListRow(
            cleanTitle(kind),
            subtitle = if (items.isEmpty()) F.nothingToClean else "${strings.items(items.size)} · ${readableSize(items.sumOf { it.size })}",
            icon = icon,
            iconColor = Color(0xFFF4731F),
            onClick = if (items.isEmpty()) null else ({ Features.open(FeatureScreen.CleanCategory(kind)) })
        )
    }
    FeatureMenuPage(title = F.cleanUp) {
        ThemedListSection(rows = rows)
        ThemedListSection(
            rows = listOf(
                ListRow(F.similarPhotos, subtitle = F.similarSubtitle, icon = Icons.Rounded.BurstMode, iconColor = Color(0xFF00A9A5), onClick = { Features.open(FeatureScreen.Similar) }),
                ListRow(strings.duplicates, subtitle = strings.duplicatesSubtitle, icon = Icons.Rounded.CleaningServices, iconColor = Color(0xFF00A9A5), onClick = {
                    Features.screens.clear()
                    env.vm.openDuplicates()
                })
            )
        )
    }
}

@Composable
fun CleanCategoryScreen(kind: CleanKind) {
    val env = LocalFeatureEnv.current
    val media by env.vm.rawMedia.collectAsStateWithLifecycle()
    val items = remember(media) { cleanItems(kind, media) }
    var selection by remember(items) { mutableStateOf(items.map { it.id }.toSet()) }
    val selectedItems = items.filter { it.id in selection }
    FeaturePage(
        title = cleanTitle(kind),
        subtitle = if (selectedItems.isNotEmpty()) F.selectedFrees(selectedItems.size, readableSize(selectedItems.sumOf { it.size }))
            else strings.items(items.size),
        bottomBar = if (selectedItems.isNotEmpty()) {
            { TrashBar(selectedItems) { env.vm.trashItems(selectedItems); selection = emptySet() } }
        } else null
    ) {
        if (items.isEmpty()) {
            ThemedEmptyState(Icons.Rounded.CleaningServices, cleanTitle(kind), F.nothingToClean)
        } else {
            MediaSectionGrid(
                sections = listOf(MediaSection(null, items)),
                selection = selection,
                onToggle = { item -> selection = if (item.id in selection) selection - item.id else selection + item.id },
                bottomPadding = featureBottomPadding(110.dp),
                sectionBadge = if (kind == CleanKind.LARGE_VIDEOS) { _, item -> readableSize(item.size) } else null
            )
        }
    }
}
