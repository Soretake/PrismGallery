package com.example.amoledgallery.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.MediaGridItem
import com.example.amoledgallery.ui.design.ThemedEmptyState
import com.example.amoledgallery.ui.design.ThemedProgressBar
import com.example.amoledgallery.ui.design.ThemedTextField
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.i18n.LanguageState
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/** Town label of every located photo, built in the background for the search and the Places page. */
private class PlaceLookup {
    var labels by mutableStateOf<Map<Long, String>>(emptyMap())
    var progress by mutableStateOf<Float?>(0f)
}

@Composable
private fun rememberPlaceLookup(media: List<MediaItem>): PlaceLookup {
    val context = LocalContext.current
    val lookup = remember { PlaceLookup() }
    LaunchedEffect(media) {
        if (media.isEmpty()) {
            lookup.progress = null
            return@LaunchedEffect
        }
        Geo.ensureLoaded(context)
        val located = LocationIndex.locate(context, media) { done, total ->
            lookup.progress = if (total > 0) done.toFloat() / total else null
        }
        lookup.labels = withContext(Dispatchers.Default) {
            located.mapNotNull { (id, latLon) -> Geo.nearest(latLon.first, latLon.second)?.let { id to it.label } }.toMap()
        }
        lookup.progress = null
    }
    return lookup
}

// ---------------------------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------------------------

private val typeWords = mapOf(
    "video" to { m: MediaItem -> m.isVideo }, "videos" to { m: MediaItem -> m.isVideo },
    "gif" to { m: MediaItem -> m.isGif }, "gifs" to { m: MediaItem -> m.isGif },
    "photo" to { m: MediaItem -> m.isImage }, "photos" to { m: MediaItem -> m.isImage },
    "foto" to { m: MediaItem -> m.isImage }, "fotos" to { m: MediaItem -> m.isImage },
    "bild" to { m: MediaItem -> m.isImage }, "bilder" to { m: MediaItem -> m.isImage },
    "screenshot" to { m: MediaItem -> isScreenshot(m) }, "screenshots" to { m: MediaItem -> isScreenshot(m) },
    "bildschirmfoto" to { m: MediaItem -> isScreenshot(m) },
    "raw" to { m: MediaItem -> isRaw(m) }, "dng" to { m: MediaItem -> isRaw(m) }
)

internal fun isScreenshot(m: MediaItem) =
    m.relativePath.contains("screenshot", ignoreCase = true) || m.displayName.startsWith("Screenshot", ignoreCase = true)

internal fun isRaw(m: MediaItem) =
    m.mimeType.contains("dng", ignoreCase = true) || m.mimeType.contains("raw", ignoreCase = true) ||
        m.displayName.endsWith(".dng", ignoreCase = true)

/** Month numbers (0-11) for month names and abbreviations in English and German. */
private val monthWords: Map<String, Int> by lazy {
    buildMap {
        listOf(Locale.ENGLISH, Locale.GERMAN).forEach { locale ->
            val symbols = DateFormatSymbols(locale)
            symbols.months.forEachIndexed { i, name -> if (name.isNotBlank()) put(name.lowercase(locale), i) }
            symbols.shortMonths.forEachIndexed { i, name -> if (name.isNotBlank()) put(name.lowercase(locale).trimEnd('.'), i) }
        }
    }
}

/** Does [item] match one search word? */
private fun matches(item: MediaItem, word: String, place: String?, calendar: Calendar): Boolean {
    typeWords[word]?.let { if (it(item)) return true }
    if (item.displayName.contains(word, ignoreCase = true)) return true
    if (item.bucketDisplayName.contains(word, ignoreCase = true)) return true
    if (place != null && place.contains(word, ignoreCase = true)) return true
    calendar.timeInMillis = item.date * 1000L
    if (word.length == 4 && word.all { it.isDigit() } && calendar.get(Calendar.YEAR) == word.toInt()) return true
    monthWords[word]?.let { if (calendar.get(Calendar.MONTH) == it) return true }
    return false
}

@Composable
fun SearchScreen() {
    val env = LocalFeatureEnv.current
    val media by env.vm.rawMedia.collectAsStateWithLifecycle()
    val lookup = rememberPlaceLookup(media)
    var query by remember { mutableStateOf("") }
    val palette = LocalAppTheme.current.palette
    val results = remember(query, media, lookup.labels) {
        val words = query.trim().lowercase(LanguageState.locale).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) emptyList() else {
            val calendar = Calendar.getInstance()
            media.filter { item -> words.all { matches(item, it, lookup.labels[item.id], calendar) } }
        }
    }
    FeaturePage(title = F.search, subtitle = if (query.isNotBlank()) F.results(results.size) else null) {
        val top = featureTopPadding()
        // Above the keyboard, so the last rows of the results can be scrolled into view while typing.
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            Box(modifier = Modifier.padding(top = top - 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                ThemedTextField(value = query, onValueChange = { query = it }, placeholder = F.searchHint, autoFocus = true)
            }
            lookup.progress?.let { p ->
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Text(F.placesScanning, color = palette.textMuted, fontSize = 12.sp)
                    ThemedProgressBar(p, Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            when {
                query.isBlank() -> ThemedEmptyState(Icons.Rounded.Search, F.search, F.searchStartMessage)
                results.isEmpty() -> ThemedEmptyState(Icons.Rounded.Search, F.searchEmptyTitle, F.searchEmptyMessage)
                else -> MediaSectionGrid(listOf(MediaSection(null, results)), topPadding = 8.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Places
// ---------------------------------------------------------------------------------------------

@Composable
fun PlacesScreen() {
    val env = LocalFeatureEnv.current
    val media by env.vm.rawMedia.collectAsStateWithLifecycle()
    val lookup = rememberPlaceLookup(media)
    val palette = LocalAppTheme.current.palette
    val places = remember(lookup.labels, media) {
        val byId = media.associateBy { it.id }
        lookup.labels.entries
            .groupBy({ it.value }, { byId[it.key] })
            .mapValues { (_, items) -> items.filterNotNull().sortedByDescending { it.date } }
            .filterValues { it.isNotEmpty() }
            .entries.sortedByDescending { it.value.size }
    }
    FeaturePage(title = F.places, subtitle = if (places.isNotEmpty()) "${places.size}" else null) {
        when {
            places.isEmpty() && lookup.progress != null -> Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(F.placesScanning, color = palette.textMuted, fontSize = 15.sp)
                ThemedProgressBar(lookup.progress ?: 0f, Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            places.isEmpty() -> ThemedEmptyState(Icons.Rounded.Map, F.placesEmptyTitle, F.placesEmptyMessage)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = featureTopPadding(), bottom = featureBottomPadding(), start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(places, key = { it.key }) { (label, items) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { Features.open(FeatureScreen.Place(label, items)) }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) {
                            MediaGridItem(item = items.first(), onClick = { Features.open(FeatureScreen.Place(label, items)) }, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = palette.onBackground, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(strings.items(items.size), color = palette.textMuted, fontSize = 13.sp)
                        }
                    }
                }
                item {
                    Text(F.geoNamesCredit, color = palette.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, start = 6.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// On this day
// ---------------------------------------------------------------------------------------------

@Composable
fun OnThisDayScreen() {
    val env = LocalFeatureEnv.current
    val media by env.vm.rawMedia.collectAsStateWithLifecycle()
    val sections = remember(media) { onThisDaySections(media) }
    FeaturePage(title = F.onThisDay) {
        if (sections.isEmpty()) {
            ThemedEmptyState(Icons.Rounded.History, F.onThisDayEmptyTitle, F.onThisDayEmptyMessage)
        } else {
            MediaSectionGrid(sections)
        }
    }
}

/** Photos taken on today's day and month in earlier years, a section per year (most recent first). */
internal fun onThisDaySections(media: List<MediaItem>): List<MediaSection> {
    val now = Calendar.getInstance()
    val day = now.get(Calendar.DAY_OF_MONTH)
    val month = now.get(Calendar.MONTH)
    val year = now.get(Calendar.YEAR)
    val c = Calendar.getInstance()
    return media.filter { item ->
        if (item.date <= 0) return@filter false
        c.timeInMillis = item.date * 1000L
        c.get(Calendar.DAY_OF_MONTH) == day && c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) < year
    }.groupBy {
        c.timeInMillis = it.date * 1000L
        c.get(Calendar.YEAR)
    }.toSortedMap(compareByDescending { it }).map { (y, items) ->
        MediaSection("${F.yearsAgo(year - y)} · $y", items.sortedBy { it.date })
    }
}
