package com.example.amoledgallery.ui.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.ThemedButton
import com.example.amoledgallery.ui.design.ThemedSegmentedControl
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

enum class CreateKind(val min: Int, val max: Int) { COLLAGE(2, 6), GIF(2, 40), PDF(1, 100) }

private fun title(kind: CreateKind) = when (kind) {
    CreateKind.COLLAGE -> F.collage
    CreateKind.GIF -> F.gif
    CreateKind.PDF -> F.pdf
}

/** Pick the photos, then the options; the result is saved and opened in the gallery's list. */
@Composable
fun CreateScreen(kind: CreateKind) {
    val env = LocalFeatureEnv.current
    val media by env.vm.rawMedia.collectAsStateWithLifecycle()
    val photos = remember(media) { media.filter { it.isImage } }
    var order by remember { mutableStateOf<List<Long>>(emptyList()) }
    var step by remember { mutableIntStateOf(0) }
    if (step == 1) BackHandler { step = 0 }
    val chosen = order.mapNotNull { id -> photos.firstOrNull { it.id == id } }

    if (step == 0) {
        val ok = chosen.size in kind.min..kind.max
        FeaturePage(
            title = title(kind),
            subtitle = if (order.isEmpty()) F.selectPhotos(kind.min, kind.max) else F.selectedCount(order.size),
            bottomBar = if (ok) {
                {
                    Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        ThemedButton(F.next, onClick = { step = 1 }, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else null
        ) {
            MediaSectionGrid(
                sections = listOf(MediaSection(null, photos)),
                selection = order.toSet(),
                onToggle = { item ->
                    order = if (item.id in order) order - item.id else if (order.size < kind.max) order + item.id else order
                },
                bottomPadding = featureBottomPadding(96.dp),
                // The tapping order is the order in the result.
                sectionBadge = { _, item -> order.indexOf(item.id).takeIf { it >= 0 }?.let { "${it + 1}" } }
            )
        }
    } else {
        when (kind) {
            CreateKind.COLLAGE -> CollageOptions(chosen, onBack = { step = 0 })
            CreateKind.GIF -> GifOptions(chosen, onBack = { step = 0 })
            CreateKind.PDF -> PdfOptions(chosen, onBack = { step = 0 })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Collage
// ---------------------------------------------------------------------------------------------

/** Layouts per photo count, as cells in 0..1 coordinates of the square. */
private fun collageLayouts(count: Int): List<List<RectF>> = when (count) {
    2 -> listOf(
        listOf(RectF(0f, 0f, .5f, 1f), RectF(.5f, 0f, 1f, 1f)),
        listOf(RectF(0f, 0f, 1f, .5f), RectF(0f, .5f, 1f, 1f))
    )
    3 -> listOf(
        listOf(RectF(0f, 0f, .5f, 1f), RectF(.5f, 0f, 1f, .5f), RectF(.5f, .5f, 1f, 1f)),
        listOf(RectF(0f, 0f, 1f, .5f), RectF(0f, .5f, .5f, 1f), RectF(.5f, .5f, 1f, 1f)),
        listOf(RectF(0f, 0f, 1f / 3, 1f), RectF(1f / 3, 0f, 2f / 3, 1f), RectF(2f / 3, 0f, 1f, 1f))
    )
    4 -> listOf(
        listOf(RectF(0f, 0f, .5f, .5f), RectF(.5f, 0f, 1f, .5f), RectF(0f, .5f, .5f, 1f), RectF(.5f, .5f, 1f, 1f)),
        listOf(RectF(0f, 0f, 1f, .6f), RectF(0f, .6f, 1f / 3, 1f), RectF(1f / 3, .6f, 2f / 3, 1f), RectF(2f / 3, .6f, 1f, 1f))
    )
    5 -> listOf(
        listOf(RectF(0f, 0f, .5f, .5f), RectF(.5f, 0f, 1f, .5f), RectF(0f, .5f, 1f / 3, 1f), RectF(1f / 3, .5f, 2f / 3, 1f), RectF(2f / 3, .5f, 1f, 1f)),
        listOf(RectF(0f, 0f, .6f, .6f), RectF(.6f, 0f, 1f, .3f), RectF(.6f, .3f, 1f, .6f), RectF(0f, .6f, .5f, 1f), RectF(.5f, .6f, 1f, 1f))
    )
    else -> listOf(
        List(6) { i -> RectF((i % 3) / 3f, (i / 3) / 2f, (i % 3 + 1) / 3f, (i / 3 + 1) / 2f) },
        List(6) { i -> RectF((i % 2) / 2f, (i / 2) / 3f, (i % 2 + 1) / 2f, (i / 2 + 1) / 3f) }
    )
}

private fun drawCropped(canvas: Canvas, bitmap: Bitmap, dst: RectF, paint: Paint) {
    val srcAspect = bitmap.width.toFloat() / bitmap.height
    val dstAspect = dst.width() / dst.height()
    val src = if (srcAspect > dstAspect) {
        val w = (bitmap.height * dstAspect).roundToInt()
        Rect((bitmap.width - w) / 2, 0, (bitmap.width + w) / 2, bitmap.height)
    } else {
        val h = (bitmap.width / dstAspect).roundToInt()
        Rect(0, (bitmap.height - h) / 2, bitmap.width, (bitmap.height + h) / 2)
    }
    canvas.drawBitmap(bitmap, src, dst, paint)
}

private fun renderCollage(bitmaps: List<Bitmap>, layout: List<RectF>, size: Int, gap: Float, white: Boolean): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(if (white) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val g = gap * size
    layout.forEachIndexed { i, cell ->
        val bitmap = bitmaps.getOrNull(i) ?: return@forEachIndexed
        val dst = RectF(cell.left * size + g / 2, cell.top * size + g / 2, cell.right * size - g / 2, cell.bottom * size - g / 2)
        // The outer edge gets the full gap too.
        if (cell.left == 0f) dst.left += g / 2
        if (cell.top == 0f) dst.top += g / 2
        if (cell.right == 1f) dst.right -= g / 2
        if (cell.bottom == 1f) dst.bottom -= g / 2
        drawCropped(canvas, bitmap, dst, paint)
    }
    return out
}

@Composable
private fun CollageOptions(items: List<MediaItem>, onBack: () -> Unit) {
    val context = LocalContext.current
    val env = LocalFeatureEnv.current
    val scope = rememberCoroutineScope()
    var gap by remember { mutableIntStateOf(1) }
    var white by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val sources by androidx.compose.runtime.produceState<List<Bitmap>?>(null, items) {
        value = withContext(Dispatchers.IO) { items.mapNotNull { runCatching { MediaOps.decode(context, it.uri, 1400) }.getOrNull() } }
    }
    // Laid out for the photos that could be read: a damaged file used to leave its cell empty.
    val cellCount = (sources?.size ?: items.size).coerceIn(2, 6)
    val layouts = remember(cellCount) { collageLayouts(cellCount) }
    var layoutIndex by remember(cellCount) { mutableIntStateOf(0) }
    val gapFraction = listOf(0f, 0.012f, 0.03f)[gap]
    val preview by androidx.compose.runtime.produceState<Bitmap?>(null, sources, layoutIndex, gap, white) {
        val src = sources ?: return@produceState
        value = withContext(Dispatchers.Default) { renderCollage(src, layouts[layoutIndex], 900, gapFraction, white) }
    }
    val palette = LocalAppTheme.current.palette
    FeaturePage(title = F.collage, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = featureTopPadding(), bottom = featureBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Never taller than about half a landscape screen, so the options stay in view.
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(max = 360.dp).aspectRatio(1f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) {
                preview?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize()) }
            }
            Text(F.layout, color = palette.textMuted, fontSize = 14.sp)
            ThemedSegmentedControl(layouts.indices.toList(), layoutIndex, { "${it + 1}" }, { layoutIndex = it })
            Text(F.spacing, color = palette.textMuted, fontSize = 14.sp)
            ThemedSegmentedControl(listOf(0, 1, 2), gap, { listOf("0", "S", "L")[it] }, { gap = it })
            Text(F.frameColor, color = palette.textMuted, fontSize = 14.sp)
            ThemedSegmentedControl(listOf(true, false), white, { if (it) F.white else F.black }, { white = it })
            ThemedButton(if (saving) F.creating else com.example.amoledgallery.ui.i18n.strings.save, enabled = !saving && sources != null, modifier = Modifier.fillMaxWidth(), onClick = {
                val src = sources ?: return@ThemedButton
                saving = true
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        val full = renderCollage(src, layouts[layoutIndex], 2400, gapFraction, white)
                        MediaOps.saveNew(context, MediaOps.timestampName("Collage") + ".jpg", "image/jpeg", "${Environment.DIRECTORY_PICTURES}/Collages") {
                            full.compress(Bitmap.CompressFormat.JPEG, 93, it)
                        }.also { full.recycle() }
                    }
                    saving = false
                    env.vm.message(if (uri != null) F.savedTo("Pictures/Collages") else F.failed)
                    if (uri != null) {
                        env.vm.refreshAfterEdit()
                        Features.back()
                    }
                }
            })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// GIF
// ---------------------------------------------------------------------------------------------

@Composable
private fun GifOptions(items: List<MediaItem>, onBack: () -> Unit) {
    val context = LocalContext.current
    val env = LocalFeatureEnv.current
    val scope = rememberCoroutineScope()
    var delay by remember { mutableIntStateOf(500) }
    var size by remember { mutableIntStateOf(480) }
    var saving by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    val thumbs by androidx.compose.runtime.produceState<List<Bitmap>?>(null, items) {
        value = withContext(Dispatchers.IO) { items.mapNotNull { runCatching { MediaOps.decode(context, it.uri, 720) }.getOrNull() } }
    }
    LaunchedEffect(delay, thumbs) {
        while (true) {
            kotlinx.coroutines.delay(delay.toLong())
            frame = (frame + 1) % (thumbs?.size?.coerceAtLeast(1) ?: 1)
        }
    }
    val palette = LocalAppTheme.current.palette
    val aspect = thumbs?.firstOrNull()?.let { it.width.toFloat() / it.height } ?: 1f
    FeaturePage(title = F.gif, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = featureTopPadding(), bottom = featureBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(max = 360.dp).aspectRatio(aspect.coerceIn(0.5f, 2f), matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) {
                thumbs?.getOrNull(frame)?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            }
            Text(F.frameDelay, color = palette.textMuted, fontSize = 14.sp)
            ThemedSegmentedControl(listOf(200, 500, 1000, 2000), delay, { "%.1f s".format(it / 1000f) }, { delay = it })
            Text(F.size, color = palette.textMuted, fontSize = 14.sp)
            ThemedSegmentedControl(listOf(320, 480, 720), size, { "$it px" }, { size = it })
            ThemedButton(if (saving) F.creating else com.example.amoledgallery.ui.i18n.strings.save, enabled = !saving && thumbs != null, modifier = Modifier.fillMaxWidth(), onClick = {
                saving = true
                scope.launch {
                    val uri = withContext(Dispatchers.Default) {
                        // Every frame gets the first photo's aspect ratio, filled (cropped) like the preview.
                        val w = if (aspect >= 1f) size else (size * aspect).roundToInt()
                        val h = if (aspect >= 1f) (size / aspect).roundToInt() else size
                        MediaOps.saveNew(context, MediaOps.timestampName("Animation") + ".gif", "image/gif", "${Environment.DIRECTORY_PICTURES}/GIFs") { out ->
                            val encoder = GifEncoder(java.io.BufferedOutputStream(out), w, h, delay)
                            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                            items.forEach { item ->
                                val src = runCatching { MediaOps.decode(context, item.uri, max(w, h) * 2) }.getOrNull() ?: return@forEach
                                val frameBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                drawCropped(Canvas(frameBitmap), src, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
                                encoder.addFrame(frameBitmap)
                                frameBitmap.recycle()
                                src.recycle()
                            }
                            encoder.finish()
                        }
                    }
                    saving = false
                    env.vm.message(if (uri != null) F.savedTo("Pictures/GIFs") else F.failed)
                    if (uri != null) {
                        env.vm.refreshAfterEdit()
                        Features.back()
                    }
                }
            })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// PDF
// ---------------------------------------------------------------------------------------------

@Composable
private fun PdfOptions(items: List<MediaItem>, onBack: () -> Unit) {
    val context = LocalContext.current
    val env = LocalFeatureEnv.current
    val scope = rememberCoroutineScope()
    var a4 by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val palette = LocalAppTheme.current.palette
    FeaturePage(title = F.pdf, subtitle = com.example.amoledgallery.ui.i18n.strings.items(items.size), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = featureTopPadding(), bottom = featureBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(F.pageSize, color = palette.textMuted, fontSize = 14.sp)
            ThemedSegmentedControl(listOf(true, false), a4, { if (it) "A4" else F.fitToPhoto }, { a4 = it })
            ThemedButton(if (saving) F.creating else com.example.amoledgallery.ui.i18n.strings.save, enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
                saving = true
                scope.launch {
                    val uri = withContext(Dispatchers.IO) { writePdf(context, items, a4) }
                    saving = false
                    env.vm.message(if (uri != null) F.savedTo("Documents/Prism Gallery") else F.failed)
                    if (uri != null) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    .setDataAndType(uri, "application/pdf")
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        Features.back()
                    }
                }
            })
        }
    }
}

/** One photo per page, fitted with a margin (A4) or filling a page of the photo's own proportions. */
private fun writePdf(context: Context, items: List<MediaItem>, a4: Boolean): android.net.Uri? {
    val document = PdfDocument()
    return try {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        items.forEachIndexed { index, item ->
            val bitmap = runCatching { MediaOps.decode(context, item.uri, 2400) }.getOrNull() ?: return@forEachIndexed
            val landscape = bitmap.width > bitmap.height
            // PDF points (1/72 inch). Photo-sized pages are 8 inches on their long side.
            val (pw, ph) = if (a4) (if (landscape) 842 to 595 else 595 to 842)
            else if (landscape) 576 to (576f * bitmap.height / bitmap.width).roundToInt() else (576f * bitmap.width / bitmap.height).roundToInt() to 576
            val page = document.startPage(PdfDocument.PageInfo.Builder(pw, ph, index + 1).create())
            val margin = if (a4) 28f else 0f
            val scale = minOf((pw - 2 * margin) / bitmap.width, (ph - 2 * margin) / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            page.canvas.drawBitmap(bitmap, null, RectF((pw - w) / 2, (ph - h) / 2, (pw + w) / 2, (ph + h) / 2), paint)
            document.finishPage(page)
            bitmap.recycle()
        }
        MediaOps.saveNew(context, MediaOps.timestampName("Photos") + ".pdf", "application/pdf", "${Environment.DIRECTORY_DOCUMENTS}/Prism Gallery") {
            document.writeTo(it)
        }
    } catch (e: Exception) {
        null
    } finally {
        document.close()
    }
}
