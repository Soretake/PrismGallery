package com.example.amoledgallery.ui.features

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.AmoledVideoPlayer
import com.example.amoledgallery.ui.design.OverlayWindow
import com.example.amoledgallery.ui.i18n.F
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Motion photos (Google's "Motion"/"MicroVideo" and Samsung's "Motion photo"): a JPEG with a short MP4 appended
 * after the picture. The video part is found by its MP4 header and copied into the cache to be played.
 */
object MotionPhoto {
    /** Offset of the embedded video by media id, or -1 for "none". */
    private val offsets = ConcurrentHashMap<Long, Long>()

    private fun cacheFile(context: Context, item: MediaItem) = File(context.cacheDir, "motion/${item.id}_${item.dateModified}.mp4")

    /** True once [videoOffset] found a video in this photo (without reading the file). */
    fun isKnownMotion(id: Long): Boolean = (offsets[id] ?: -1L) >= 0L

    /** Where the embedded MP4 starts, or null. Reads the file once; blocking. */
    fun videoOffset(context: Context, item: MediaItem): Long? {
        if (item.isVideo || item.isGif) return null
        val mime = item.mimeType.lowercase()
        if (!mime.contains("jpeg") && !mime.contains("jpg") && !mime.contains("heic") && !mime.contains("heif")) return null
        offsets[item.id]?.let { return it.takeIf { o -> o >= 0 } }
        val found = runCatching { findOffset(context, item.uri) }.getOrNull()
        offsets[item.id] = found ?: -1L
        return found
    }

    private fun findOffset(context: Context, uri: Uri): Long? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        // Only files that say they are motion photos: XMP markers, or Samsung's trailer.
        val head = String(bytes, 0, minOf(bytes.size, 256 * 1024), Charsets.ISO_8859_1)
        val tail = String(bytes, maxOf(0, bytes.size - 64 * 1024), minOf(bytes.size, 64 * 1024), Charsets.ISO_8859_1)
        val marked = head.contains("MotionPhoto") || head.contains("MicroVideo") || tail.contains("MotionPhoto_Data")
        if (!marked) return null
        // The MP4 starts with a box "....ftyp" whose size field is small; search after the first kilobytes.
        var i = 1024
        while (i < bytes.size - 12) {
            if (bytes[i] == 'f'.code.toByte() && bytes[i + 1] == 't'.code.toByte() && bytes[i + 2] == 'y'.code.toByte() && bytes[i + 3] == 'p'.code.toByte()) {
                val start = i - 4
                val size = ((bytes[start].toInt() and 0xff) shl 24) or ((bytes[start + 1].toInt() and 0xff) shl 16) or
                    ((bytes[start + 2].toInt() and 0xff) shl 8) or (bytes[start + 3].toInt() and 0xff)
                if (size in 8..256) return start.toLong()
            }
            i++
        }
        return null
    }

    /** The embedded video as a file in the cache (made on first use), or null. Blocking. */
    fun extract(context: Context, item: MediaItem): File? {
        val offset = videoOffset(context, item) ?: return null
        val file = cacheFile(context, item)
        if (file.exists() && file.length() > 0) return file
        file.parentFile?.mkdirs()
        // Old extractions are only needed while their photo is looked at.
        file.parentFile?.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3_600_000L }?.forEach { it.delete() }
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            input.skip(offset)
            file.outputStream().use { input.copyTo(it) }
        }
        return file.takeIf { it.length() > 0 }
    }

    /** Saves the embedded video to Movies/Motion photos. */
    fun saveVideo(context: Context, item: MediaItem): Uri? {
        val file = extract(context, item) ?: return null
        val base = item.displayName.substringBeforeLast('.').ifBlank { "motion_${item.id}" }
        return MediaOps.saveNew(context, "$base.mp4", "video/mp4", "${Environment.DIRECTORY_MOVIES}/Motion photos") { out ->
            file.inputStream().use { it.copyTo(out) }
        }
    }
}

/** The motion part of a motion photo, played over the viewer until closed. */
@Composable
fun MotionPlayerOverlay(item: MediaItem, file: File, onClose: () -> Unit) {
    OverlayWindow { _ ->
        BackHandler(onBack = onClose)
        var controls by remember { mutableStateOf(true) }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AmoledVideoPlayer(
                item = item.copy(uri = Uri.fromFile(file), isVideo = true, mimeType = "video/mp4"),
                isActivePage = true,
                controlsVisible = controls,
                onTap = { controls = !controls },
                onShowControls = { controls = true },
                onPlayingChange = {},
                onScrubbingChange = {},
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
        }
    }
}

/** The "motion photo" chip the viewer shows on such photos; a tap plays the motion. */
@Composable
fun MotionChip(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.MotionPhotosOn, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(F.playMotion, color = Color.White, fontSize = 13.sp)
    }
}
