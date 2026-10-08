package com.example.amoledgallery.data

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.amoledgallery.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Files that are byte for byte the same, the one offered to be kept first. */
@Immutable
data class DuplicateGroup(
    val items: List<MediaItem>,
    /** What the extra copies take up: everything beyond the one that would be kept. */
    val wastedBytes: Long
)

/** How far a scan has got, for the progress line. */
data class DuplicateProgress(val checked: Int, val total: Int)

/**
 * Finds files that are exactly the same, not merely alike.
 *
 * MediaStore knows nothing about a file's content, so the files have to be read. Reading all of them
 * would be far too slow on a full phone, so the work is narrowed down in three steps, each only on what
 * survived the one before:
 *
 *  1. Group by size. Two files of different length cannot be the same, and this alone throws almost
 *     everything out without touching the disk - the size comes from MediaStore.
 *  2. Hash the first 256 KB of the ones that are left. Pictures differ in their first few blocks far
 *     more often than not, so this separates nearly all same-sized but different files cheaply.
 *  3. Hash whole files only where even that matched. That is what makes the answer exact rather than
 *     likely.
 *
 * Full hashes are remembered between scans in the cache directory, keyed by the file's id, size and
 * modification time, so a second scan only reads what has changed since.
 */
class DuplicateFinder(private val context: Context) {

    private val cacheFile: File get() = File(context.cacheDir, "duplicate_hashes.txt")

    /** key ("id:size:modified") to full-file hash, as last written to disk. */
    private var cached: MutableMap<String, String>? = null

    private fun cache(): MutableMap<String, String> {
        cached?.let { return it }
        val map = HashMap<String, String>()
        runCatching {
            if (cacheFile.exists()) {
                cacheFile.forEachLine { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1)
                }
            }
        }
        cached = map
        return map
    }

    private fun saveCache(map: Map<String, String>) {
        runCatching {
            cacheFile.bufferedWriter().use { out ->
                map.forEach { (key, hash) -> out.write(key); out.write("\t"); out.write(hash); out.newLine() }
            }
        }
    }

    private fun keyOf(item: MediaItem) = "${item.id}:${item.size}:${item.dateModified}"

    /**
     * Every set of identical files in [media], biggest waste first. Groups of one are not returned.
     *
     * Call off the main thread; [onProgress] is called as files are read, and cancelling the coroutine
     * stops the scan at the next file.
     */
    suspend fun find(
        media: List<MediaItem>,
        onProgress: (DuplicateProgress) -> Unit = {}
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        // Step 1: same size. Anything alone at its size is already ruled out.
        val bySize = media
            .filter { it.size > 0L && it.dataPath.isNotBlank() }
            .groupBy { it.size }
            .filterValues { it.size > 1 }
        val candidates = bySize.values.flatten()
        if (candidates.isEmpty()) return@withContext emptyList()

        val cache = cache()
        var checked = 0
        val total = candidates.size
        onProgress(DuplicateProgress(0, total))

        val groups = ArrayList<DuplicateGroup>()
        var cacheChanged = false

        for ((_, sameSize) in bySize) {
            currentCoroutineContext().ensureActive()

            // Step 2: the first blocks, which already tells most same-sized files apart.
            val byHead = HashMap<String, MutableList<MediaItem>>()
            for (item in sameSize) {
                currentCoroutineContext().ensureActive()
                val head = hashOf(item, HEAD_BYTES)
                checked++
                onProgress(DuplicateProgress(checked, total))
                if (head != null) byHead.getOrPut(head) { ArrayList() }.add(item)
            }

            // Step 3: whole files, for the few that still look alike. A file no bigger than the head was
            // read whole already, so its head hash is its full hash and there is nothing left to read.
            for (sameHead in byHead.values) {
                if (sameHead.size < 2) continue
                if (sameHead.first().size <= HEAD_BYTES) {
                    val ordered = keeperFirst(sameHead)
                    groups += DuplicateGroup(ordered, ordered.first().size * (ordered.size - 1))
                    continue
                }
                val byFull = HashMap<String, MutableList<MediaItem>>()
                for (item in sameHead) {
                    currentCoroutineContext().ensureActive()
                    val key = keyOf(item)
                    val full = cache[key] ?: hashOf(item, Long.MAX_VALUE)?.also {
                        cache[key] = it
                        cacheChanged = true
                    }
                    if (full != null) byFull.getOrPut(full) { ArrayList() }.add(item)
                }
                for (identical in byFull.values) {
                    if (identical.size < 2) continue
                    val ordered = keeperFirst(identical)
                    groups += DuplicateGroup(ordered, ordered.first().size * (ordered.size - 1))
                }
            }
        }

        if (cacheChanged) {
            // Only what is still around, so the file does not grow for ever.
            val live = media.mapTo(HashSet()) { keyOf(it) }
            saveCache(cache.filterKeys { it in live })
        }
        groups.sortedByDescending { it.wastedBytes }
    }

    /**
     * The order a set is shown in; the first one is offered to be kept. Names that look like a copy
     * ("photo_copy", "photo (1)", "Kopie von ...") go last, then the newest wins, then the shorter name.
     */
    private fun keeperFirst(items: List<MediaItem>): List<MediaItem> =
        items.sortedWith(
            compareBy<MediaItem> { COPY_NAME.containsMatchIn(it.displayName.substringBeforeLast('.')) }
                .thenByDescending { it.date }
                .thenBy { it.displayName.length }
        )

    /** SHA-256 of at most [limit] bytes of the file, or null when it cannot be read. */
    private fun hashOf(item: MediaItem, limit: Long): String? = runCatching {
        val file = File(item.dataPath)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            var read = 0L
            while (read < limit) {
                val wanted = minOf(buffer.size.toLong(), limit - read).toInt()
                val got = stream.read(buffer, 0, wanted)
                if (got <= 0) break
                digest.update(buffer, 0, got)
                read += got
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private companion object {
        const val HEAD_BYTES = 256L * 1024

        /** File names (without extension) that mark a duplicate made by copying, in English and German. */
        val COPY_NAME = Regex("""(?i)(copy|kopie)|\(\d+\)\s*$""")
    }
}
