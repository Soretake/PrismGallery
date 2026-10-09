package com.example.amoledgallery.ui.features

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ScreenLockRotation
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.design.MenuEntry
import com.example.amoledgallery.ui.i18n.F
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared state of the open viewer that its tools need. */
object ViewerState {
    /** Where the video on screen is, so its frame can be saved. Set by the video player. */
    @Volatile var videoPositionMs: Long = 0L

    /** The viewer keeps the screen in its current orientation. */
    var rotationLocked by mutableStateOf(false)
}

internal tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

fun setRotationLock(context: Context, locked: Boolean) {
    ViewerState.rotationLocked = locked
    context.activity()?.requestedOrientation =
        if (locked) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

/**
 * The tools the fullscreen viewer offers for [item] in its "more" menu, after the design's own entries:
 * slideshow, share options, date and location, copy, private album, "use as", print, rotation lock, and for
 * videos saving the frame on screen.
 */
fun viewerExtraEntries(
    context: Context,
    item: MediaItem,
    all: List<MediaItem>,
    scope: CoroutineScope,
    withIcons: Boolean
): List<MenuEntry> {
    fun icon(vector: androidx.compose.ui.graphics.vector.ImageVector) = if (withIcons) vector else null
    val env = Features.env
    return buildList {
        add(MenuEntry(F.slideshow, icon = icon(Icons.Outlined.Slideshow), group = 1) {
            Features.show(FeatureDialog.Slideshow(all, all.indexOf(item).coerceAtLeast(0)))
        })
        add(MenuEntry(F.shareOptions, icon = icon(Icons.Outlined.Share), group = 1) { Features.show(FeatureDialog.Share(listOf(item))) })
        if (item.isVideo) {
            add(MenuEntry(F.saveFrame, icon = icon(Icons.Outlined.PhotoCamera), group = 1) {
                val position = ViewerState.videoPositionMs
                scope.launch {
                    val uri = withContext(Dispatchers.IO) { MediaOps.saveVideoFrame(context, item, position) }
                    env?.vm?.message(if (uri != null) F.frameSaved else F.failed)
                    env?.vm?.refreshAfterEdit()
                }
            })
        }
        if (MotionPhoto.isKnownMotion(item.id)) {
            add(MenuEntry(F.exportMotionVideo, icon = icon(Icons.Outlined.MotionPhotosOn), group = 1) {
                scope.launch {
                    val uri = withContext(Dispatchers.IO) { MotionPhoto.saveVideo(context, item) }
                    env?.vm?.message(if (uri != null) F.videoSaved else F.failed)
                    env?.vm?.refreshAfterEdit()
                }
            })
        }
        if (MediaOps.canWriteExif(item)) {
            add(MenuEntry(F.editDateTime, icon = icon(Icons.Outlined.CalendarMonth), group = 2) { Features.show(FeatureDialog.EditDate(item)) })
            add(MenuEntry(F.editLocation, icon = icon(Icons.Outlined.LocationOn), group = 2) { Features.show(FeatureDialog.EditLocation(item)) })
        }
        add(MenuEntry(F.copyToAlbum, icon = icon(Icons.Outlined.ContentCopy), group = 2) { Features.show(FeatureDialog.CopyToAlbum(listOf(item))) })
        if (env != null) {
            add(MenuEntry(F.moveToPrivate, icon = icon(Icons.Outlined.Lock), group = 2) { moveToPrivate(context, env, listOf(item), scope) })
        }
        if (!item.isVideo) {
            add(MenuEntry(F.setAs, icon = icon(Icons.Outlined.AccountBox), group = 3) {
                val intent = Intent(Intent.ACTION_ATTACH_DATA)
                    .setDataAndType(item.uri, item.mimeType.ifBlank { "image/*" })
                    .putExtra("mimeType", item.mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(intent, F.setAs)) }
            })
            add(MenuEntry(F.print, icon = icon(Icons.Outlined.Print), group = 3) {
                runCatching {
                    val helper = androidx.print.PrintHelper(context)
                    helper.scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT
                    helper.printBitmap(item.displayName.ifBlank { "Photo" }, item.uri)
                }.onFailure { env?.vm?.message(F.failed) }
            })
        }
        val locked = ViewerState.rotationLocked
        add(MenuEntry(
            if (locked) F.unlockRotation else F.lockRotation,
            icon = icon(if (locked) Icons.Outlined.ScreenRotation else Icons.Outlined.ScreenLockRotation),
            group = 3
        ) { setRotationLock(context, !locked) })
    }
}

/** Selection-bar tools for several items at once. */
fun selectionExtraEntries(context: Context, items: List<MediaItem>, scope: CoroutineScope): List<MenuEntry> {
    val env = Features.env
    return buildList {
        add(MenuEntry(F.slideshow, icon = Icons.Outlined.Slideshow) { Features.show(FeatureDialog.Slideshow(items, 0)) })
        add(MenuEntry(F.shareOptions, icon = Icons.Outlined.Share) { Features.show(FeatureDialog.Share(items)) })
        add(MenuEntry(F.copyToAlbum, icon = Icons.Outlined.ContentCopy) { Features.show(FeatureDialog.CopyToAlbum(items)) })
        if (env != null) add(MenuEntry(F.moveToPrivate, icon = Icons.Outlined.Lock) { moveToPrivate(context, env, items, scope) })
        if (EditClipboard.spec != null && items.any { it.isImage }) {
            add(MenuEntry(F.pasteEdits, icon = Icons.Outlined.AddPhotoAlternate) { EditClipboard.pasteOnto(context, items.filter { it.isImage }, scope) })
        }
    }
}
