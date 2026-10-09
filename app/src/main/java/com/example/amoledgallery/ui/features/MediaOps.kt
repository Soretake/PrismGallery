package com.example.amoledgallery.ui.features

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.openWithLocation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** File work behind the 1.2 tools: copies, EXIF changes, prepared shares and newly created pictures. */
object MediaOps {

    /** Types whose EXIF ExifInterface can rewrite in place. */
    fun canWriteExif(item: MediaItem): Boolean {
        val mime = item.mimeType.lowercase(Locale.ROOT)
        return !item.isVideo && (mime == "image/jpeg" || mime == "image/png" || mime == "image/webp")
    }

    /**
     * Copies [item] into [relativePath] ("Pictures/Holiday/") on [volume] (null: internal storage). MediaStore
     * adds " (1)" itself when the name is taken. Returns the copy's uri, or null when it failed.
     */
    fun copyTo(context: Context, item: MediaItem, relativePath: String, volume: String?): Uri? {
        val resolver = context.contentResolver
        val volumeName = volume ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
        val collection = if (item.isVideo) MediaStore.Video.Media.getContentUri(volumeName) else MediaStore.Images.Media.getContentUri(volumeName)
        val path = relativePath.trim('/').ifBlank { if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES } + "/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName.ifBlank { "media_${item.id}" })
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType.ifBlank { if (item.isVideo) "video/mp4" else "image/jpeg" })
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            if (item.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken * 1000L)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            (openWithLocation(context, item.uri) ?: error("no input")).use { input ->
                resolver.openOutputStream(target)?.use { output -> input.copyTo(output) } ?: error("no output")
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            target
        } catch (e: Exception) {
            runCatching { resolver.delete(target, null, null) }
            null
        }
    }

    /** Opens [item]'s EXIF for writing. Throws SecurityException when the app may not write the file yet. */
    private inline fun editExif(context: Context, item: MediaItem, block: (ExifInterface) -> Unit) {
        val pfd = context.contentResolver.openFileDescriptor(item.uri, "rw") ?: error("no file")
        pfd.use {
            val exif = ExifInterface(it.fileDescriptor)
            block(exif)
            exif.saveAttributes()
        }
        rescan(context, item)
    }

    /** Has MediaStore read the changed file again, so the gallery sorts and shows it with its new data. */
    private fun rescan(context: Context, item: MediaItem) {
        if (item.dataPath.isNotBlank()) {
            android.media.MediaScannerConnection.scanFile(context, arrayOf(item.dataPath), null, null)
        }
    }

    /** Writes a new capture time (local time of this device) into the photo. */
    fun setDateTaken(context: Context, item: MediaItem, millis: Long) {
        val local = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(millis))
        val offsetMinutes = TimeZone.getDefault().getOffset(millis) / 60_000
        val offset = String.format(Locale.US, "%s%02d:%02d", if (offsetMinutes < 0) "-" else "+", abs(offsetMinutes) / 60, abs(offsetMinutes) % 60)
        editExif(context, item) { exif ->
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, local)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, local)
            exif.setAttribute(ExifInterface.TAG_DATETIME, local)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        }
        // Keeps the file's own time in step too, for apps that sort by it.
        if (item.dataPath.isNotBlank()) runCatching { File(item.dataPath).setLastModified(millis) }
    }

    private val GPS_TAGS = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR, ExifInterface.TAG_GPS_MAP_DATUM, ExifInterface.TAG_GPS_VERSION_ID
    )

    fun removeLocation(context: Context, item: MediaItem) {
        editExif(context, item) { exif -> GPS_TAGS.forEach { exif.setAttribute(it, null) } }
    }

    fun setLocation(context: Context, item: MediaItem, latitude: Double, longitude: Double) {
        editExif(context, item) { exif -> exif.setLatLong(latitude, longitude) }
    }

    /** A temporary copy in the cache that only this app's FileProvider hands out (shares, prints). */
    private fun shareFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Old shares are only needed while the receiving app reads them.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 6 * 3_600_000L }?.forEach { it.delete() }
        return File(dir, name)
    }

    fun providerUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    enum class ShareMode { WITHOUT_LOCATION, AS_JPEG, SMALLER }

    /**
     * Prepares [item] for sharing: a copy without GPS data, converted to JPEG, or scaled down to 2048 px.
     * Videos are only ever shared as they are (there is no location to strip in a copy without re-encoding).
     */
    fun prepareShare(context: Context, item: MediaItem, mode: ShareMode): Uri? = runCatching {
        if (item.isVideo || item.isGif) return item.uri
        val base = item.displayName.substringBeforeLast('.').ifBlank { "photo_${item.id}" }
        when (mode) {
            ShareMode.WITHOUT_LOCATION -> {
                val ext = item.displayName.substringAfterLast('.', "jpg")
                val file = shareFile(context, "$base.$ext")
                context.contentResolver.openInputStream(item.uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                if (canWriteExif(item)) {
                    val exif = ExifInterface(file)
                    GPS_TAGS.forEach { exif.setAttribute(it, null) }
                    exif.saveAttributes()
                    providerUri(context, file)
                } else {
                    // A format ExifInterface cannot rewrite (HEIF): a JPEG without location instead.
                    file.delete()
                    jpegCopy(context, item, base, maxSide = 0, keepLocation = false)
                }
            }
            ShareMode.AS_JPEG -> jpegCopy(context, item, base, maxSide = 0, keepLocation = true)
            ShareMode.SMALLER -> jpegCopy(context, item, base, maxSide = 2048, keepLocation = true)
        }
    }.getOrNull()

    private fun jpegCopy(context: Context, item: MediaItem, base: String, maxSide: Int, keepLocation: Boolean): Uri {
        val bitmap = decode(context, item.uri, if (maxSide > 0) maxSide else 8192)
        val file = shareFile(context, "$base.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        // The capture details travel along; the location only when it was meant to.
        runCatching {
            val source = (if (keepLocation) openWithLocation(context, item.uri) else context.contentResolver.openInputStream(item.uri))
                ?.use { ExifInterface(it) }
            if (source != null) {
                val target = ExifInterface(file)
                val tags = buildList {
                    addAll(listOf(
                        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL
                    ))
                    if (keepLocation) addAll(GPS_TAGS)
                }
                tags.forEach { tag -> source.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
                target.saveAttributes()
            }
        }
        return providerUri(context, file)
    }

    /** Decodes [uri] upright, its longest side at most [maxSide]. */
    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }
    }

    /**
     * Saves a newly made picture or file (collage, GIF, video frame, PDF) into [relativePath] and returns its
     * uri. [write] fills the output stream.
     */
    fun saveNew(
        context: Context,
        displayName: String,
        mimeType: String,
        relativePath: String,
        write: (java.io.OutputStream) -> Unit
    ): Uri? {
        val resolver = context.contentResolver
        val collection = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mimeType.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath.trim('/') + "/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            resolver.openOutputStream(uri)?.use(write) ?: error("no output")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** "Prism_20261009_221530" */
    fun timestampName(prefix: String): String =
        prefix + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Saves the video frame at [positionMs] of [item] as a JPEG in Pictures/Video captures. */
    fun saveVideoFrame(context: Context, item: MediaItem, positionMs: Long): Uri? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.uri)
            val frame = retriever.getFrameAtTime(positionMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return null
            val base = item.displayName.substringBeforeLast('.').ifBlank { "video" }
            saveNew(context, "${base}_${positionMs / 1000}s.jpg", "image/jpeg", "${Environment.DIRECTORY_PICTURES}/Video captures") {
                frame.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
