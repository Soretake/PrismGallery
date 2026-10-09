package com.example.amoledgallery.service

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.UserManager
import android.provider.MediaStore
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.roundToInt

/** What the slideshow wallpaper shows: one album (bucket id) and how often it changes. */
object SlideshowWallpaperChoice {
    private const val PREFS = "prism_slideshow_wallpaper"

    fun write(context: Context, bucketId: String, intervalMinutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("bucket", bucketId)
            .putInt("minutes", intervalMinutes)
            .apply()
    }

    fun read(context: Context): Pair<String, Int>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bucket = prefs.getString("bucket", null) ?: return null
        return bucket to prefs.getInt("minutes", 30)
    }
}

/**
 * A wallpaper that shows the photos of one album in random order, changing every few minutes (and when the
 * screen is turned on after the interval has passed) with a soft cross-fade.
 *
 * Direct-boot aware like [LiveWallpaperService]: before the first unlock the photos and the settings are not
 * readable, so it stays black until the user has unlocked the phone, instead of crashing.
 */
class SlideshowWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = SlideshowEngine()

    private inner class SlideshowEngine : Engine() {
        private val thread = HandlerThread("slideshow-wallpaper").apply { start() }
        private val handler = Handler(thread.looper)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private var current: Bitmap? = null
        private var previous: Bitmap? = null
        private var fadeStart = 0L
        private var lastChange = 0L
        private var width = 0
        private var height = 0
        private var visible = false
        private var queue = ArrayDeque<Long>()
        private var unlockReceiver: BroadcastReceiver? = null

        private val unlocked: Boolean
            get() = getSystemService(UserManager::class.java)?.isUserUnlocked != false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            if (!unlocked) {
                unlockReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        runCatching { unregisterReceiver(this) }
                        unlockReceiver = null
                        handler.post { next() }
                    }
                }
                registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), RECEIVER_NOT_EXPORTED)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            handler.post { if (current == null) next() else draw() }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(tick)
            if (visible) handler.post(tick)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacksAndMessages(null)
        }

        override fun onDestroy() {
            super.onDestroy()
            unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
        }

        /** Changes the photo when its time is up; while fading, redraws every frame. */
        private val tick: Runnable = object : Runnable {
            override fun run() {
                if (!visible) return
                val minutes = if (unlocked) SlideshowWallpaperChoice.read(this@SlideshowWallpaperService)?.second ?: 30 else 30
                if (current == null || System.currentTimeMillis() - lastChange >= minutes * 60_000L) next()
                // Also the very first photo fades in (from black), so redraw until any fade is done.
                val fading = System.currentTimeMillis() - fadeStart < FADE_MS + 50
                draw()
                handler.postDelayed(this, if (fading) 16L else 30_000L)
            }
        }

        private fun next() {
            if (!unlocked || width <= 0 || height <= 0) { draw(); return }
            val choice = SlideshowWallpaperChoice.read(this@SlideshowWallpaperService)
            if (choice == null) { draw(); return }
            if (queue.isEmpty()) queue = ArrayDeque(photoIds(choice.first).shuffled())
            val id = queue.removeFirstOrNull() ?: run { draw(); return }
            val bitmap = runCatching { load(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)) }.getOrNull()
            if (bitmap != null) {
                previous?.recycle()
                previous = current
                current = bitmap
                fadeStart = System.currentTimeMillis()
                lastChange = fadeStart
            }
            draw()
            if (visible) {
                handler.removeCallbacks(tick)
                handler.post(tick)
            }
        }

        private fun photoIds(bucket: String): List<Long> {
            val ids = ArrayList<Long>()
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.BUCKET_ID} = ?",
                arrayOf(bucket),
                null
            )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
            return ids
        }

        private fun load(uri: android.net.Uri): Bitmap {
            val source = ImageDecoder.createSource(contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // Large enough to fill the screen when cropped, no larger.
                val scale = max(width.toFloat() / info.size.width, height.toFloat() / info.size.height).coerceAtMost(1f)
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            }
        }

        private fun drawCover(canvas: Canvas, bitmap: Bitmap, alpha: Int) {
            val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            paint.alpha = alpha
            canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), paint)
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                canvas.drawColor(Color.BLACK)
                val fade = ((System.currentTimeMillis() - fadeStart).toFloat() / FADE_MS).coerceIn(0f, 1f)
                previous?.takeIf { fade < 1f }?.let { drawCover(canvas, it, 255) }
                current?.let { drawCover(canvas, it, (fade * 255).roundToInt()) }
                if (fade >= 1f) {
                    previous?.recycle()
                    previous = null
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }

    private companion object {
        const val FADE_MS = 1200L
    }
}
