package com.example.amoledgallery.ui.features

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.openWithLocation
import com.example.amoledgallery.ui.i18n.LanguageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/** A town of the bundled GeoNames list. */
data class Town(val name: String, val country: String, val latitude: Double, val longitude: Double, val population: Int) {
    /** "Berlin, Germany" in the app language. */
    val label: String
        get() {
            val countryName = Locale("", country).getDisplayCountry(LanguageState.locale).ifBlank { country }
            return "$name, $countryName"
        }
}

/**
 * Offline place names: the towns with 5000+ inhabitants from GeoNames (CC BY 4.0), bundled with the app.
 * Nothing is ever looked up on the internet.
 */
object Geo {
    private var names: Array<String> = emptyArray()
    private var asciiNames: Array<String> = emptyArray()
    private var countries: Array<String> = emptyArray()
    private var lats = DoubleArray(0)
    private var lons = DoubleArray(0)
    private var pops = IntArray(0)
    /** Town indices by 1° cell ("lat:lon"), for nearest-town lookups. */
    private var cells: Map<Long, IntArray> = emptyMap()
    private val mutex = Mutex()
    @Volatile private var loaded = false

    private fun cellKey(lat: Int, lon: Int): Long = (lat + 90).toLong() * 1000L + (lon + 180)

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                val n = ArrayList<String>(70_000)
                val a = ArrayList<String>(70_000)
                val c = ArrayList<String>(70_000)
                val la = ArrayList<Double>(70_000)
                val lo = ArrayList<Double>(70_000)
                val p = ArrayList<Int>(70_000)
                context.assets.open("geo/cities.tsv").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("#")) return@forEach
                        val f = line.split('\t')
                        if (f.size < 6) return@forEach
                        n += f[0]; a += f[1].ifBlank { f[0] }; c += f[2]
                        la += f[3].toDouble(); lo += f[4].toDouble(); p += f[5].toIntOrNull() ?: 0
                    }
                }
                names = n.toTypedArray(); asciiNames = a.toTypedArray(); countries = c.toTypedArray()
                lats = la.toDoubleArray(); lons = lo.toDoubleArray(); pops = p.toIntArray()
                val buckets = HashMap<Long, MutableList<Int>>()
                for (i in names.indices) {
                    buckets.getOrPut(cellKey(floor(lats[i]).toInt(), floor(lons[i]).toInt())) { ArrayList() }.add(i)
                }
                cells = buckets.mapValues { it.value.toIntArray() }
            }
            loaded = true
        }
    }

    private fun town(i: Int) = Town(names[i], countries[i], lats[i], lons[i], pops[i])

    /**
     * The town a photo was most likely taken in: the nearest one, where a big town counts as a little nearer
     * than a village at the same distance (so a photo in a city's outskirts is not given to a suburb).
     */
    fun nearest(latitude: Double, longitude: Double): Town? {
        if (!loaded) return null
        val cosLat = cos(Math.toRadians(latitude))
        var best = -1
        var bestScore = Double.MAX_VALUE
        val baseLat = floor(latitude).toInt()
        val baseLon = floor(longitude).toInt()
        for (dLat in -1..1) for (dLon in -1..1) {
            val lonCell = ((baseLon + dLon + 180 + 360) % 360) - 180
            val bucket = cells[cellKey(baseLat + dLat, lonCell)] ?: continue
            for (i in bucket) {
                val dy = lats[i] - latitude
                var dx = lons[i] - longitude
                if (dx > 180) dx -= 360 else if (dx < -180) dx += 360
                val km = sqrt(dy * dy + dx * dx * cosLat * cosLat) * 111.0
                val score = km / (1.0 + kotlin.math.ln(1.0 + pops[i] / 50_000.0) * 0.6)
                if (score < bestScore) { bestScore = score; best = i }
            }
        }
        return if (best >= 0 && bestScore < 60.0) town(best) else null
    }

    /** Towns whose name starts with (or, failing that, contains) [query], biggest first. */
    fun search(query: String, limit: Int = 30): List<Town> {
        if (!loaded) return emptyList()
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return emptyList()
        val starts = ArrayList<Int>()
        val contains = ArrayList<Int>()
        for (i in names.indices) {
            val name = names[i].lowercase(Locale.ROOT)
            val ascii = asciiNames[i].lowercase(Locale.ROOT)
            when {
                name.startsWith(q) || ascii.startsWith(q) -> starts += i
                contains.size < limit && (name.contains(q) || ascii.contains(q)) -> contains += i
            }
            if (starts.size >= limit) break
        }
        // The list is ordered by population already.
        return (starts + contains).take(limit).map { town(it) }
    }

    /** Parses ISO 6709 ("+52.5163+013.3777/") as MediaMetadataRetriever reports a video's location. */
    fun parseIso6709(value: String?): Pair<Double, Double>? {
        if (value.isNullOrBlank()) return null
        val m = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(value) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        return if (lat == 0.0 && lon == 0.0) null else lat to lon
    }
}

/**
 * Where each photo and video was taken, read from its EXIF / container data once and remembered in the cache
 * (by id and modification time), so the Places page and the search only read new files.
 */
object LocationIndex {
    private val mutex = Mutex()
    private var cache: MutableMap<String, Pair<Double, Double>?>? = null

    // v2: v1 could hold "no location" for photos read while the app was not yet allowed to see locations.
    private fun file(context: Context) = File(context.cacheDir, "locations_v2.txt")
    private fun key(item: MediaItem) = "${item.id}:${item.dateModified}"

    private fun load(context: Context): MutableMap<String, Pair<Double, Double>?> {
        cache?.let { return it }
        val map = HashMap<String, Pair<Double, Double>?>()
        runCatching {
            file(context).takeIf { it.exists() }?.forEachLine { line ->
                val f = line.split('\t')
                if (f.size >= 3) {
                    val lat = f[1].toDoubleOrNull()
                    val lon = f[2].toDoubleOrNull()
                    map[f[0]] = if (lat != null && lon != null) lat to lon else null
                }
            }
        }
        cache = map
        return map
    }

    private fun save(context: Context, map: Map<String, Pair<Double, Double>?>) {
        runCatching {
            file(context).bufferedWriter().use { out ->
                map.forEach { (k, v) ->
                    out.write(k); out.write("\t"); out.write(v?.first?.toString() ?: ""); out.write("\t")
                    out.write(v?.second?.toString() ?: ""); out.newLine()
                }
            }
        }
    }

    /** Reads one file's location (null when it has none or cannot be read). */
    fun read(context: Context, item: MediaItem): Pair<Double, Double>? = runCatching {
        if (item.isVideo) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, item.uri)
                Geo.parseIso6709(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_LOCATION))
            } finally {
                runCatching { retriever.release() }
            }
        } else {
            openWithLocation(context, item.uri)?.use { stream ->
                ExifInterface(stream).latLong?.let { (lat, lon) -> if (lat == 0.0 && lon == 0.0) null else lat to lon }
            }
        }
    }.getOrNull()

    /** Locations of [items] (only those that have one). [onProgress] reports files read so far. */
    suspend fun locate(
        context: Context,
        items: List<MediaItem>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Map<Long, Pair<Double, Double>> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val map = load(context)
            val result = HashMap<Long, Pair<Double, Double>>()
            var changed = false
            // Without the permission Android hands out photos with their location blanked: "none found" then
            // says nothing about the photo and must not be remembered, or Places stays empty after it is granted.
            val mayReadLocations = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_MEDIA_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            items.forEachIndexed { index, item ->
                val k = key(item)
                val value = if (map.containsKey(k)) map[k] else read(context, item).also {
                    if (it != null || mayReadLocations || item.isVideo) {
                        map[k] = it
                        changed = true
                    }
                }
                if (value != null) result[item.id] = value
                if (index % 25 == 0) onProgress(index, items.size)
            }
            onProgress(items.size, items.size)
            if (changed) {
                val live = items.mapTo(HashSet()) { key(it) }
                save(context, map.filterKeys { it in live })
            }
            result
        }
    }

    /** Forgets [item]'s location after it was changed or removed. */
    fun invalidate(item: MediaItem) {
        cache?.keys?.removeAll { it.startsWith("${item.id}:") }
    }
}
