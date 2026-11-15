package com.example.amoledgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.Size
import com.example.amoledgallery.PerformanceProfile
import com.example.amoledgallery.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream

/**
 * Permanent internal thumbnail cache service.
 * Eliminates MicroSD I/O bus latency by caching downsampled thumbnails (250x250 px, WebP, 75% quality)
 * permanently on internal high-speed UFS storage (context.cacheDir.resolve("thumbnails")).
 *
 * Cache naming format: "${mediaItem.id}_${mediaItem.dateModified}.webp"
 * Includes a quiet background pre-indexer that step-by-step pre-caches missing thumbnails.
 */
class ThumbnailManager private constructor(private val context: Context) {

    val cacheDir: File = context.cacheDir.resolve("thumbnails").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    private val preindexScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    private var preindexJob: Job? = null

    /**
     * Current manual graphics quality tier. Drives thumbnail pixel size and JPEG quality for
     * newly-written cache entries; applies instantly since it only affects future writes.
     */
    @Volatile
    var quality: PerformanceProfile = PerformanceProfile.MEDIUM

    /**
     * Width of a grid tile at the default four columns on this screen. Fixed sizes ignored the screen: on a
     * 1080p phone LOW's 120px thumbnails were stretched over 2x into ~265px tiles (and at 120px the system may
     * hand out the camera's tiny embedded EXIF preview), so photos could hardly be told apart.
     */
    private val tilePx: Int = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) } / 4

    val thumbnailPx: Int
        get() = when (quality) {
            // A fraction of the tile on the lower tiers keeps decoding and memory cheap; never below the old
            // fixed sizes, so small screens keep what they had.
            PerformanceProfile.LOW -> maxOf(160, tilePx * 6 / 10)
            PerformanceProfile.MEDIUM -> maxOf(160, tilePx * 3 / 4)
            // HIGH/ULTRA target stronger devices, which can afford visibly sharper grid tiles -
            // especially once pinch-zoom shows fewer, larger columns (bigger tiles = more upscaling).
            PerformanceProfile.HIGH -> maxOf(220, tilePx)
            PerformanceProfile.ULTRA -> maxOf(320, tilePx * 5 / 4)
        }

    private val jpegQuality: Int
        get() = when (quality) {
            PerformanceProfile.LOW -> 60
            PerformanceProfile.MEDIUM -> 70
            PerformanceProfile.HIGH -> 80
            PerformanceProfile.ULTRA -> 85
        }

    @Volatile
    private var isPreindexingPaused: Boolean = false

    /**
     * Items whose thumbnail could not be decoded (broken or unsupported files). Remembered for the
     * process lifetime so neither scrolling nor the pre-indexer keeps retrying an expensive
     * MediaMetadataRetriever / decoder setup for them every time they come into view.
     */
    private val failedThumbnails = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun failureKey(id: Long, dateModified: Long) = "${id}_$dateModified"

    fun hasThumbnailFailed(id: Long, dateModified: Long): Boolean = failureKey(id, dateModified) in failedThumbnails

    fun markThumbnailFailed(id: Long, dateModified: Long) {
        failedThumbnails.add(failureKey(id, dateModified))
    }

    /**
     * Resolves the internal cache file location for a given [MediaItem].
     */
    fun getThumbnailFile(item: MediaItem): File {
        return getThumbnailFile(item.id, item.dateModified)
    }

    /**
     * Resolves the internal cache file location for a given [id] and [dateModified].
     * The current [thumbnailPx] tier is baked into the filename so raising the graphics
     * quality (and therefore the target resolution) naturally misses the old, lower-res
     * cache entry instead of silently reusing it - [cleanupOutdatedThumbnails] then sweeps
     * the stale file away once the new one is written.
     */
    fun getThumbnailFile(id: Long, dateModified: Long): File {
        val jpg = cacheDir.resolve("${id}_${dateModified}_${thumbnailPx}.jpg")
        if (jpg.exists()) return jpg
        val webp = cacheDir.resolve("${id}_${dateModified}_${thumbnailPx}.webp")
        if (webp.exists()) return webp
        return jpg
    }

    /**
     * Returns true if a non-empty cache file already exists on internal storage.
     */
    fun isCached(item: MediaItem): Boolean {
        return isCached(item.id, item.dateModified)
    }

    /**
     * Returns true if a non-empty cache file already exists on internal storage for [id] and [dateModified].
     */
    fun isCached(id: Long, dateModified: Long): Boolean {
        val file = getThumbnailFile(id, dateModified)
        return file.exists() && file.length() > 0L
    }

    /**
     * Retrieves existing cached thumbnail File, or decodes via ContentResolver.loadThumbnail(),
     * compresses to 160x160 JPEG with 75% quality, saves to internal storage, and returns the File.
     */
    suspend fun getOrCreateThumbnailFile(item: MediaItem): File? = withContext(Dispatchers.IO) {
        getOrCreateThumbnailFile(item.uri, item.isVideo, item.id, item.dateModified)
    }

    /**
     * Retrieves existing cached thumbnail File, or decodes via ContentResolver.loadThumbnail(),
     * compresses to 160x160 JPEG with 75% quality, saves to internal storage, and returns the File.
     */
    suspend fun getOrCreateThumbnailFile(
        uri: Uri,
        isVideo: Boolean,
        id: Long,
        dateModified: Long
    ): File? = withContext(Dispatchers.IO) {
        val targetFile = getThumbnailFile(id, dateModified)
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@withContext targetFile
        }
        if (hasThumbnailFailed(id, dateModified)) return@withContext null

        val bitmap = loadThumbnailBitmap(uri, isVideo, thumbnailPx, thumbnailPx)
        if (bitmap == null) {
            markThumbnailFailed(id, dateModified)
            return@withContext null
        }
        if (saveBitmapToCache(bitmap, targetFile)) {
            cleanupOutdatedThumbnails(id, targetFile.name)
            targetFile
        } else {
            null
        }
    }

    /**
     * Decodes a downsampled thumbnail bitmap using ContentResolver.loadThumbnail (API 29+)
     * with fallbacks for older devices or decoding failures.
     */
    fun loadThumbnailBitmap(
        uri: Uri,
        isVideo: Boolean,
        targetWidth: Int = thumbnailPx,
        targetHeight: Int = thumbnailPx
    ): Bitmap? {
        try {
            return context.contentResolver.loadThumbnail(uri, Size(targetWidth, targetHeight), null)
        } catch (_: Throwable) {
            // Fallback below
        }

        return if (isVideo) {
            loadVideoThumbnailFallback(uri)
        } else {
            loadImageThumbnailFallback(uri, targetWidth, targetHeight)
        }
    }

    private fun loadImageThumbnailFallback(
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadVideoThumbnailFallback(uri: Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.frameAtTime
            retriever.release()
            if (frame != null && frame.config != Bitmap.Config.RGB_565) {
                val converted = frame.copy(Bitmap.Config.RGB_565, false)
                converted ?: frame
            } else {
                frame
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Compresses bitmap to JPEG format at 75% quality using fast SIMD libjpeg-turbo.
     */
    fun saveBitmapToCache(bitmap: Bitmap, targetFile: File): Boolean {
        return try {
            val tempFile = File.createTempFile("thumb_tmp_", ".tmp", cacheDir)
            val compressBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
            } else {
                bitmap
            }

            FileOutputStream(tempFile).use { out ->
                compressBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                out.flush()
            }

            if (tempFile.length() > 0L) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            Log.w("ThumbnailManager", "Failed to write cached thumbnail ${targetFile.name}", e)
            false
        }
    }

    /**
     * Asynchronously writes a decoded bitmap to internal UFS cache without blocking the caller.
     * Subsequent accesses will load the cached file directly at hardware speed.
     */
    fun saveBitmapAsync(bitmap: Bitmap, targetFile: File, id: Long) {
        if (targetFile.exists() && targetFile.length() > 0L) return
        preindexScope.launch {
            if (!targetFile.exists() || targetFile.length() == 0L) {
                if (saveBitmapToCache(bitmap, targetFile)) {
                    cleanupOutdatedThumbnails(id, targetFile.name)
                }
            }
        }
    }

    /**
     * Deletes older thumbnails for the same item ID when the media item was updated/modified.
     */
    private fun cleanupOutdatedThumbnails(id: Long, currentFileName: String) {
        try {
            val prefix = "${id}_"
            cacheDir.listFiles { _, name ->
                name.startsWith(prefix) && (name.endsWith(".jpg") || name.endsWith(".webp")) && name != currentFileName
            }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    /**
     * Quietly walks [items] in the background and pre-decodes any thumbnail that isn't cached
     * yet, so scrolling into previously-unseen content hits the disk cache instead of paying a
     * full decode on the frame it first becomes visible. Disabled entirely on LOW (weakest
     * devices skip the extra background work), throttled more gently as quality increases.
     * A short delay between items - rather than firing everything at once - is what keeps this
     * from overheating/draining battery on older SoCs while still catching up over time.
     */
    fun startPreindexing(items: List<MediaItem>) {
        if (quality == PerformanceProfile.LOW) return
        val batchDelayMs = when (quality) {
            PerformanceProfile.LOW -> return
            PerformanceProfile.MEDIUM -> 30L
            PerformanceProfile.HIGH -> 12L
            PerformanceProfile.ULTRA -> 4L
        }

        preindexJob?.cancel()
        isPreindexingPaused = false
        preindexJob = preindexScope.launch {
            // One directory listing instead of two stat calls per item: with thousands of items that are
            // almost all cached already, the old walk spent tens of seconds just delaying between hits.
            val px = thumbnailPx
            val cachedNames = cacheDir.list()?.toHashSet() ?: hashSetOf()
            for (item in items) {
                if (!isActive) return@launch
                while (isPreindexingPaused) {
                    if (!isActive) return@launch
                    delay(200)
                }
                val base = "${item.id}_${item.dateModified}_$px"
                if ("$base.jpg" in cachedNames || "$base.webp" in cachedNames) continue
                getOrCreateThumbnailFile(item)
                delay(batchDelayMs)
            }
            completedItems = items
        }
    }

    /** The list the last pre-indexing pass fully completed for, so an idle grid doesn't restart it. */
    @Volatile
    private var completedItems: List<MediaItem>? = null

    /** Pauses the background pre-indexer without losing its place - e.g. while the user is actively flinging the grid. */
    fun pausePreindexing() {
        isPreindexingPaused = true
    }

    /** Resumes a paused pre-indexer, or starts a fresh one if none is running. */
    fun resumePreindexing(items: List<MediaItem>) {
        isPreindexingPaused = false
        if (preindexJob?.isActive != true && completedItems !== items) {
            startPreindexing(items)
        }
    }

    fun stopPreindexing() {
        preindexJob?.cancel()
        preindexJob = null
        isPreindexingPaused = false
    }

    companion object {
        @Volatile
        private var instance: ThumbnailManager? = null

        fun getInstance(context: Context): ThumbnailManager {
            return instance ?: synchronized(this) {
                instance ?: ThumbnailManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
