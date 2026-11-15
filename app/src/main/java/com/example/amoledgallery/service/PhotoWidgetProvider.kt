package com.example.amoledgallery.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.MediaStore
import android.util.Size
import android.widget.RemoteViews
import com.example.amoledgallery.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The home-screen photo widget: a random photo from the favorites (or, without favorites, from the 60 newest
 * photos), changed by Android about every hour. A tap opens the gallery.
 */
class PhotoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked == false) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Never lets a failed lookup take the app's process down: the widget then just keeps its photo.
                ids.forEach { id -> runCatching { update(context, manager, id) } }
            } finally {
                pending.finish()
            }
        }
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_photo)
        val photo = pickPhoto(context)
        if (photo != null) {
            runCatching {
                // Small enough for the widget IPC limit, large enough for a 4×4 widget.
                val bitmap = context.contentResolver.loadThumbnail(photo, Size(720, 720), null)
                views.setImageViewBitmap(R.id.widget_photo, bitmap)
            }
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setClassName(context, "com.example.amoledgallery.MainActivity")
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
        manager.updateAppWidget(id, views)
    }

    private fun pickPhoto(context: Context): android.net.Uri? {
        val favorites = context.getSharedPreferences("amoled_gallery_fav_prefs", Context.MODE_PRIVATE)
            .getStringSet("favorite_media_ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        val imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // Albums hidden in the gallery stay off the home screen too: the widget used to pick from every
        // photo on the phone, hidden albums included.
        val hidden = context.getSharedPreferences("amoled_gallery_hidden_prefs", Context.MODE_PRIVATE)
            .getStringSet("hidden_bucket_ids", emptySet()).orEmpty()
        val columns = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_ID)
        val existingFavorites = if (favorites.isEmpty()) emptyList() else {
            val found = ArrayList<Long>()
            context.contentResolver.query(
                imagesUri, columns,
                "${MediaStore.Images.Media._ID} IN (${favorites.joinToString(",")})", null, null
            )?.use { c -> while (c.moveToNext()) if (c.getString(1) !in hidden) found += c.getLong(0) }
            found
        }
        val pool = existingFavorites.ifEmpty {
            val recent = ArrayList<Long>()
            context.contentResolver.query(
                imagesUri, columns, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c -> while (c.moveToNext() && recent.size < 60) if (c.getString(1) !in hidden) recent += c.getLong(0) }
            recent
        }
        return pool.randomOrNull()?.let { ContentUris.withAppendedId(imagesUri, it) }
    }
}
