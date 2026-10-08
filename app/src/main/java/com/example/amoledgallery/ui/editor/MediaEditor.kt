@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.example.amoledgallery.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.openWithLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem as ExoMediaItem

/** Crop rectangle in 0..1 coordinates of the rotated/flipped frame (y grows downwards). */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val isFull: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    /** The same region after the frame is rotated 90° clockwise. */
    fun rotateClockwise() = NormalizedRect(1f - bottom, left, 1f - top, right)

    /** The same region after the frame is mirrored left-right. */
    fun flipHorizontal() = NormalizedRect(1f - right, top, 1f - left, bottom)

    /** The same region after the frame is mirrored top-bottom. */
    fun flipVertical() = NormalizedRect(left, 1f - bottom, right, 1f - top)

    companion object {
        val Full = NormalizedRect(0f, 0f, 1f, 1f)
    }
}

/**
 * An edit: mirror first ([flipHorizontal]/[flipVertical] on the source frame), then rotate clockwise by
 * [rotation] degrees, then crop to [crop] (expressed in the resulting frame).
 */
data class EditSpec(
    val rotation: Int,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val crop: NormalizedRect
)

/** Loading previews and exporting edited copies of images and videos. */
object MediaEditor {

    /** Largest side of a saved image; keeps full-resolution decodes of 50 MP photos within memory. */
    private const val MAX_EXPORT_SIDE = 4096

    suspend fun loadPreview(context: Context, item: MediaItem, maxSize: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (item.isVideo) loadVideoFrame(context, item.uri, maxSize) else decodeImage(context, item.uri, maxSize)
        }.getOrNull()
    }

    /** Applies mirroring and then a clockwise rotation (the preview and export share this order). */
    fun transform(source: Bitmap, rotation: Int, flipHorizontal: Boolean, flipVertical: Boolean): Bitmap {
        if (rotation % 360 == 0 && !flipHorizontal && !flipVertical) return source
        val matrix = Matrix().apply {
            postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f, source.width / 2f, source.height / 2f)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun decodeImage(context: Context, uri: Uri, maxSize: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder already applies the EXIF orientation.
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSize) {
                val scale = maxSize.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }
    }

    private fun loadVideoFrame(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val frame = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize)
                ?: return null
            // Some retrievers return frames without the container rotation applied; detect that by
            // comparing the frame's orientation with the displayed orientation and rotate if needed.
            if (rotation % 180 != 0 && videoWidth > 0 && videoHeight > 0) {
                val displayedLandscape = videoHeight >= videoWidth
                val frameLandscape = frame.width >= frame.height
                if (displayedLandscape != frameLandscape) return transform(frame, rotation, false, false)
            }
            return frame
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Saves an edited copy of an image next to the original and returns its uri. */
    suspend fun saveImage(context: Context, item: MediaItem, spec: EditSpec): Uri = withContext(Dispatchers.IO) {
        val decoded = decodeImage(context, item.uri, MAX_EXPORT_SIDE)
        val transformed = transform(decoded, spec.rotation, spec.flipHorizontal, spec.flipVertical)
        val crop = spec.crop
        val x = (crop.left * transformed.width).roundToInt().coerceIn(0, transformed.width - 1)
        val y = (crop.top * transformed.height).roundToInt().coerceIn(0, transformed.height - 1)
        val width = ((crop.right - crop.left) * transformed.width).roundToInt().coerceIn(1, transformed.width - x)
        val height = ((crop.bottom - crop.top) * transformed.height).roundToInt().coerceIn(1, transformed.height - y)
        val result = if (crop.isFull) transformed else Bitmap.createBitmap(transformed, x, y, width, height)

        val mime = item.mimeType.lowercase()
        val (format, extension, outMime) = when {
            mime.contains("png") -> Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
            mime.contains("webp") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Triple(Bitmap.CompressFormat.WEBP_LOSSY, "webp", "image/webp")
            else -> Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
        }

        val volume = volumeNameOf(context, item)
        val collection = MediaStore.Images.Media.getContentUri(volume)
        val uri = insertPending(
            context, collection, editedName(item, extension), outMime, item.relativePath,
            allowedDirectories = listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES),
            fallbackDirectory = Environment.DIRECTORY_PICTURES
        )
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                if (!result.compress(format, 95, stream)) error("compress failed")
            } ?: error("no output stream")
            copyExif(context, item, uri, result.width, result.height)
            publish(context, uri)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
        uri
    }

    /**
     * Exports an edited copy of a video with Media3 Transformer (rotation/mirroring and crop as GPU
     * effects), then stores it next to the original. [onProgress] reports 0..1.
     */
    suspend fun saveVideo(context: Context, item: MediaItem, spec: EditSpec, onProgress: (Float) -> Unit): Uri {
        val output = File(context.cacheDir, "edit_${System.currentTimeMillis()}.mp4")
        try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val effects = mutableListOf<Effect>()
                    if (spec.rotation % 360 != 0 || spec.flipHorizontal || spec.flipVertical) {
                        effects += ScaleAndRotateTransformation.Builder()
                            .setScale(if (spec.flipHorizontal) -1f else 1f, if (spec.flipVertical) -1f else 1f)
                            // Media3 rotates counter-clockwise; the editor rotates clockwise.
                            .setRotationDegrees(((360 - spec.rotation % 360) % 360).toFloat())
                            .build()
                    }
                    if (!spec.crop.isFull) {
                        val c = spec.crop
                        // Normalized device coordinates: x and y in -1..1, y pointing up.
                        effects += Crop(2f * c.left - 1f, 2f * c.right - 1f, 1f - 2f * c.bottom, 1f - 2f * c.top)
                    }
                    val edited = EditedMediaItem.Builder(ExoMediaItem.fromUri(item.uri))
                        .setEffects(Effects(emptyList(), effects))
                        .build()

                    val handler = Handler(Looper.getMainLooper())
                    val transformer = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (continuation.isActive) continuation.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformer.start(edited, output.absolutePath)

                    val holder = ProgressHolder()
                    val poll = object : Runnable {
                        override fun run() {
                            if (!continuation.isActive) return
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress / 100f)
                            }
                            handler.postDelayed(this, 150)
                        }
                    }
                    handler.post(poll)
                    continuation.invokeOnCancellation {
                        handler.removeCallbacks(poll)
                        transformer.cancel()
                    }
                }
            }
            onProgress(1f)

            return withContext(Dispatchers.IO) {
                val volume = volumeNameOf(context, item)
                val collection = MediaStore.Video.Media.getContentUri(volume)
                val uri = insertPending(
                    context, collection, editedName(item, "mp4"), "video/mp4", item.relativePath,
                    allowedDirectories = listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES),
                    fallbackDirectory = Environment.DIRECTORY_MOVIES
                )
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        output.inputStream().use { it.copyTo(out) }
                    } ?: error("no output stream")
                    publish(context, uri)
                } catch (e: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw e
                }
                uri
            }
        } finally {
            output.delete()
        }
    }

    /**
     * The storage volume the original lives on, so the copy lands next to it. Gallery uris use the
     * merged "external" volume, which inserts into internal storage - SD card originals then got their
     * copy in a same-named album on internal storage.
     */
    private fun volumeNameOf(context: Context, item: MediaItem): String {
        val fromRow = runCatching {
            context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.VOLUME_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromRow?.takeIf { it.isNotBlank() && it != MediaStore.VOLUME_EXTERNAL }
            ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
    }

    /** Original details the edited copy keeps, so it stays next to the original in the timeline and on the map. */
    private val COPIED_EXIF_TAGS = arrayOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_GPS_VERSION_ID, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH, ExifInterface.TAG_WHITE_BALANCE
    )

    /**
     * Writes the original's date, location and camera details into the edited copy. The pixels are already
     * upright, so the orientation is reset. Best effort: a copy without them is still a valid edit.
     */
    private fun copyExif(context: Context, item: MediaItem, target: Uri, width: Int, height: Int) {
        runCatching {
            val source = openWithLocation(context, item.uri)?.use { ExifInterface(it) } ?: return
            context.contentResolver.openFileDescriptor(target, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                for (tag in COPIED_EXIF_TAGS) source.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
                exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
                exif.saveAttributes()
            }
        }
    }

    private fun editedName(item: MediaItem, extension: String): String {
        val base = item.displayName.substringBeforeLast('.').ifBlank { "media" }
        return "${base}_edited_${System.currentTimeMillis() / 1000}.$extension"
    }

    private fun insertPending(
        context: Context,
        collection: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String,
        allowedDirectories: List<String>,
        fallbackDirectory: String
    ): Uri {
        // MediaStore only accepts certain top-level folders per media type (images: DCIM/Pictures,
        // videos: DCIM/Movies/Pictures). Originals elsewhere - the storage root, Download, app
        // folders - get their copy in the default folder instead.
        val cleanPath = relativePath.trim('/')
        val topDirectory = cleanPath.substringBefore('/')
        val targetPath = if (cleanPath.isNotEmpty() && allowedDirectories.any { it.equals(topDirectory, ignoreCase = true) }) {
            "$cleanPath/"
        } else {
            "$fallbackDirectory/"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collection, values) ?: error("MediaStore insert failed")
    }

    private fun publish(context: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }
}
