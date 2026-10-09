package com.example.amoledgallery.ui.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.design.GlassRim
import com.example.amoledgallery.ui.design.GlassSpec
import com.example.amoledgallery.ui.design.ThemedProgressBar
import com.example.amoledgallery.ui.design.ThemedSegmentedControl
import com.example.amoledgallery.ui.design.ThemedTextField
import com.example.amoledgallery.ui.design.blockTouchesBelow
import com.example.amoledgallery.ui.design.glass
import com.example.amoledgallery.ui.design.pressScale
import com.example.amoledgallery.ui.features.EditClipboard
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.onAccent
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Called after an edited copy was saved, so the gallery can reload. Provided by MainActivity. */
val LocalOnMediaEdited = staticCompositionLocalOf<() -> Unit> { {} }

private enum class CropHandle { NONE, MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private const val MIN_CROP = 0.1f

/** The editor's tools; photos and videos offer different ones. */
private enum class EditorMode { CROP, ADJUST, FILTER, DRAW, TEXT, WATERMARK, TRIM, SIZE }

private fun EditorMode.label(): String = when (this) {
    EditorMode.CROP -> strings.edit
    EditorMode.ADJUST -> F.adjust
    EditorMode.FILTER -> F.filters
    EditorMode.DRAW -> F.draw
    EditorMode.TEXT -> F.text
    EditorMode.WATERMARK -> F.watermark
    EditorMode.TRIM -> F.trim
    EditorMode.SIZE -> F.compressVideo
}

private fun PhotoFilter.label(): String = when (this) {
    PhotoFilter.NONE -> F.original
    PhotoFilter.VIVID -> F.vivid
    PhotoFilter.MONO -> F.mono
    PhotoFilter.SEPIA -> F.sepia
    PhotoFilter.COOL -> F.cool
    PhotoFilter.WARM -> F.warm
    PhotoFilter.FADE -> F.fade
    PhotoFilter.NOIR -> F.noir
}

private val markColors = listOf(
    Color.White, Color.Black, Color(0xFFF14438), Color(0xFFF4B400), Color(0xFF34A853), Color(0xFF3E7BFA), Color(0xFFE8546A)
)

/**
 * Editor for images and videos. Photos: crop, rotate and mirror, colour adjustments, filters, drawing, text and
 * a protective watermark. Videos: crop, rotate, mirror, brightness/contrast/saturation, trimming and a smaller
 * size. The result is always saved as a new copy next to the original, which is never overwritten.
 */
@Composable
fun MediaEditorScreen(item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val onEdited = LocalOnMediaEdited.current
    val scope = rememberCoroutineScope()

    var source by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        source = MediaEditor.loadPreview(context, item)
        loadFailed = source == null
    }

    var mode by remember { mutableStateOf(EditorMode.CROP) }
    var rotation by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf(NormalizedRect.Full) }
    var looks by remember { mutableStateOf(Looks()) }
    val marks = remember { mutableStateListOf<Mark>() }
    var watermark by remember { mutableStateOf("") }
    var trim by remember { mutableStateOf(0f..1f) }
    var maxHeight by remember { mutableIntStateOf(0) }
    var drawColor by remember { mutableStateOf(Color(0xFFF14438)) }
    var drawWidth by remember { mutableFloatStateOf(0.008f) }
    var textInput by remember { mutableStateOf("") }
    var textSize by remember { mutableFloatStateOf(0.06f) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val rotateAnimation = remember { Animatable(0f) }
    val flipXAnimation = remember { Animatable(1f) }
    val flipYAnimation = remember { Animatable(1f) }

    BackHandler { if (!saving) onClose() }

    val preview: ImageBitmap? = remember(source, rotation, flipHorizontal, flipVertical) {
        source?.let { MediaEditor.transform(it, rotation, flipHorizontal, flipVertical).asImageBitmap() }
    }
    val duration = item.durationMs.coerceAtLeast(0L)
    fun currentSpec() = EditSpec(
        rotation, flipHorizontal, flipVertical, crop,
        looks = looks,
        marks = if (item.isVideo) emptyList() else marks.toList(),
        watermark = if (item.isVideo) null else watermark.takeIf { it.isNotBlank() },
        trimStartMs = if (item.isVideo && duration > 0) (trim.start * duration).toLong() else 0L,
        trimEndMs = if (item.isVideo && duration > 0 && trim.endInclusive < 0.999f) (trim.endInclusive * duration).toLong() else 0L,
        maxVideoHeight = if (item.isVideo) maxHeight else 0
    )
    val changed = currentSpec().hasChanges
    val modes = if (item.isVideo) {
        listOf(EditorMode.CROP, EditorMode.ADJUST, EditorMode.TRIM, EditorMode.SIZE)
    } else {
        listOf(EditorMode.CROP, EditorMode.ADJUST, EditorMode.FILTER, EditorMode.DRAW, EditorMode.TEXT, EditorMode.WATERMARK)
    }

    fun rotate() {
        rotation = (rotation + 90) % 360
        crop = crop.rotateClockwise()
        val turned = marks.map { it.rotateClockwise() }
        marks.clear(); marks.addAll(turned)
        scope.launch {
            rotateAnimation.snapTo(-90f)
            rotateAnimation.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 380f))
        }
    }

    // Mirroring always follows the screen axes: after a 90°/270° rotation the source's other axis flips.
    fun mirrorHorizontally() {
        if (rotation % 180 == 0) flipHorizontal = !flipHorizontal else flipVertical = !flipVertical
        crop = crop.flipHorizontal()
        val turned = marks.map { it.flipHorizontal() }
        marks.clear(); marks.addAll(turned)
        scope.launch {
            flipXAnimation.snapTo(-1f)
            flipXAnimation.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 350f))
        }
    }

    fun mirrorVertically() {
        if (rotation % 180 == 0) flipVertical = !flipVertical else flipHorizontal = !flipHorizontal
        crop = crop.flipVertical()
        val turned = marks.map { it.flipVertical() }
        marks.clear(); marks.addAll(turned)
        scope.launch {
            flipYAnimation.snapTo(-1f)
            flipYAnimation.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 350f))
        }
    }

    fun reset() {
        rotation = 0
        flipHorizontal = false
        flipVertical = false
        crop = NormalizedRect.Full
        looks = Looks()
        marks.clear()
        watermark = ""
        trim = 0f..1f
        maxHeight = 0
    }

    fun save() {
        if (saving || !changed) return
        saving = true
        progress = 0f
        val spec = currentSpec()
        scope.launch {
            val result = runCatching {
                if (item.isVideo) {
                    MediaEditor.saveVideo(context, item, spec) { progress = it }
                } else {
                    MediaEditor.saveImage(context, item, spec)
                }
            }
            saving = false
            result.onSuccess {
                Toast.makeText(context, strings.editSaved, Toast.LENGTH_SHORT).show()
                onEdited()
                onClose()
            }.onFailure {
                Toast.makeText(context, strings.editFailed(it.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .blockTouchesBelow()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: close, title, save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !saving, onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, strings.close, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Text(
                    text = strings.edit,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                val saveInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .pressScale(saveInteraction, 0.93f)
                        .clip(CircleShape)
                        .background(if (changed) palette.accent else palette.accent.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = saveInteraction,
                            indication = ripple(),
                            enabled = changed && !saving && preview != null,
                            onClick = ::save
                        )
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text(strings.save, color = palette.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Landscape: the tools beside the photo instead of below it, so the photo keeps its size.
            val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val previewArea: @Composable (Modifier) -> Unit = { areaModifier ->
            // Preview with crop frame, drawings and texts
            BoxWithConstraints(
                modifier = areaModifier.padding(horizontal = 28.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val image = preview
                if (image == null) {
                    if (loadFailed) {
                        Text(strings.unknown, color = Color.White.copy(alpha = 0.7f))
                    } else {
                        CircularProgressIndicator(color = palette.accent)
                    }
                    return@BoxWithConstraints
                }
                val areaWidth = constraints.maxWidth.toFloat()
                val areaHeight = constraints.maxHeight.toFloat()
                val scale = min(areaWidth / image.width, areaHeight / image.height)
                val drawWidth = image.width * scale
                val drawHeight = image.height * scale
                val imageRect = Rect(
                    Offset((areaWidth - drawWidth) / 2f, (areaHeight - drawHeight) / 2f),
                    Size(drawWidth, drawHeight)
                )
                var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
                val colorFilter = remember(looks) {
                    if (looks.isNeutral) null else ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(looks.matrix().array))
                }
                fun normalized(position: Offset) =
                    ((position.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f) to
                        ((position.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotateAnimation.value
                            scaleX = flipXAnimation.value
                            scaleY = flipYAnimation.value
                        }
                        .pointerInput(imageRect, mode) {
                            val touchSlop = 28.dp.toPx()
                            when (mode) {
                                EditorMode.DRAW -> detectDragGestures(
                                    onDragStart = { position ->
                                        marks.add(Mark.Stroke(listOf(normalized(position)), drawColor.toArgb(), drawWidth))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val last = marks.lastOrNull() as? Mark.Stroke ?: return@detectDragGestures
                                        marks[marks.lastIndex] = last.copy(points = last.points + normalized(change.position))
                                    }
                                )
                                EditorMode.TEXT -> detectDragGestures { change, _ ->
                                    // Moves the most recent text with the finger.
                                    change.consume()
                                    val index = marks.indexOfLast { it is Mark.Label }
                                    if (index >= 0) {
                                        val label = marks[index] as Mark.Label
                                        val (x, y) = normalized(change.position)
                                        marks[index] = label.copy(x = x, y = y)
                                    }
                                }
                                EditorMode.CROP -> detectDragGestures(
                                    onDragStart = { position ->
                                        activeHandle = hitTest(position, cropRectIn(imageRect, crop), touchSlop)
                                    },
                                    onDrag = { change, drag ->
                                        if (activeHandle != CropHandle.NONE) {
                                            change.consume()
                                            crop = applyDrag(crop, activeHandle, drag.x / imageRect.width, drag.y / imageRect.height)
                                        }
                                    },
                                    onDragEnd = { activeHandle = CropHandle.NONE },
                                    onDragCancel = { activeHandle = CropHandle.NONE }
                                )
                                else -> Unit
                            }
                        }
                ) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                        dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt()),
                        colorFilter = colorFilter
                    )
                    // Vignette, drawings, texts and watermark, exactly as the export paints them.
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        native.translate(imageRect.left, imageRect.top)
                        native.clipRect(0f, 0f, imageRect.width, imageRect.height)
                        val w = imageRect.width.roundToInt()
                        val h = imageRect.height.roundToInt()
                        EditRenderer.drawVignette(native, w, h, looks.vignette)
                        EditRenderer.drawMarks(native, w, h, marks)
                        if (watermark.isNotBlank()) EditRenderer.drawWatermark(native, w, h, watermark)
                        native.restore()
                    }
                    val cropRect = cropRectIn(imageRect, crop)
                    val shade = Color.Black.copy(alpha = if (mode == EditorMode.CROP) 0.55f else 0.8f)
                    // Dim everything outside the crop.
                    drawRect(shade, Offset(imageRect.left, imageRect.top), Size(imageRect.width, cropRect.top - imageRect.top))
                    drawRect(shade, Offset(imageRect.left, cropRect.bottom), Size(imageRect.width, imageRect.bottom - cropRect.bottom))
                    drawRect(shade, Offset(imageRect.left, cropRect.top), Size(cropRect.left - imageRect.left, cropRect.height))
                    drawRect(shade, Offset(cropRect.right, cropRect.top), Size(imageRect.right - cropRect.right, cropRect.height))
                    if (mode == EditorMode.CROP) {
                        // Rule-of-thirds grid while adjusting.
                        if (activeHandle != CropHandle.NONE) {
                            for (i in 1..2) {
                                val gx = cropRect.left + cropRect.width * i / 3f
                                val gy = cropRect.top + cropRect.height * i / 3f
                                drawLine(Color.White.copy(alpha = 0.4f), Offset(gx, cropRect.top), Offset(gx, cropRect.bottom), 1.dp.toPx())
                                drawLine(Color.White.copy(alpha = 0.4f), Offset(cropRect.left, gy), Offset(cropRect.right, gy), 1.dp.toPx())
                            }
                        }
                        drawRect(Color.White, cropRect.topLeft, cropRect.size, style = Stroke(1.5.dp.toPx()))
                        // Corner handles
                        val handleLength = 20.dp.toPx()
                        val handleStroke = 3.5.dp.toPx()
                        val handleColor = if (theme.language == DesignLanguage.GOOGLE_EXPRESSIVE) palette.accent else Color.White
                        listOf(
                            Triple(cropRect.topLeft, 1f, 1f),
                            Triple(Offset(cropRect.right, cropRect.top), -1f, 1f),
                            Triple(Offset(cropRect.left, cropRect.bottom), 1f, -1f),
                            Triple(cropRect.bottomRight, -1f, -1f)
                        ).forEach { (corner, dx, dy) ->
                            drawLine(handleColor, corner, Offset(corner.x + handleLength * dx, corner.y), handleStroke)
                            drawLine(handleColor, corner, Offset(corner.x, corner.y + handleLength * dy), handleStroke)
                        }
                    }
                }
            }

            }
            val toolPanel: @Composable () -> Unit = {
            // The panel of the chosen tool
            val enabled = !saving && preview != null
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                when (mode) {
                    EditorMode.CROP -> EditorToolbar(
                        enabled = enabled,
                        onRotate = ::rotate,
                        onMirrorHorizontally = ::mirrorHorizontally,
                        onMirrorVertically = ::mirrorVertically,
                        onReset = ::reset
                    )
                    EditorMode.ADJUST -> Column(
                        modifier = Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState())
                    ) {
                        AdjustSlider(F.brightness, looks.brightness) { looks = looks.copy(brightness = it) }
                        AdjustSlider(F.contrast, looks.contrast) { looks = looks.copy(contrast = it) }
                        AdjustSlider(F.saturation, looks.saturation) { looks = looks.copy(saturation = it) }
                        if (!item.isVideo) {
                            AdjustSlider(F.warmth, looks.warmth) { looks = looks.copy(warmth = it) }
                            AdjustSlider(F.vignette, looks.vignette, range = 0f..1f) { looks = looks.copy(vignette = it) }
                            CopyEditsButton { EditClipboard.copy(looks, watermark) }
                        }
                    }
                    EditorMode.FILTER -> Column {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val thumb = remember(preview) { preview }
                            PhotoFilter.entries.forEach { filter ->
                                val selected = looks.filter == filter
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { looks = looks.copy(filter = filter) }
                                        .padding(4.dp)
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb,
                                            contentDescription = filter.label(),
                                            contentScale = ContentScale.Crop,
                                            colorFilter = Looks(filter = filter).let { l ->
                                                if (l.isNeutral) null else ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(l.matrix().array))
                                            },
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(2.dp, if (selected) palette.accent else Color.Transparent, RoundedCornerShape(10.dp))
                                        )
                                    }
                                    Text(filter.label(), color = if (selected) palette.accent else Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        CopyEditsButton { EditClipboard.copy(looks, watermark) }
                    }
                    EditorMode.DRAW -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ColorChips(drawColor) { drawColor = it }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                ThemedSegmentedControl(listOf(0.004f, 0.008f, 0.016f), drawWidth, { listOf("S", "M", "L")[listOf(0.004f, 0.008f, 0.016f).indexOf(it)] }, { drawWidth = it })
                            }
                            UndoButton(enabled = marks.any { it is Mark.Stroke }) {
                                val index = marks.indexOfLast { it is Mark.Stroke }
                                if (index >= 0) marks.removeAt(index)
                            }
                        }
                    }
                    EditorMode.TEXT -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                ThemedTextField(value = textInput, onValueChange = { textInput = it }, placeholder = F.enterText)
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(palette.accent)
                                    .clickable(enabled = textInput.isNotBlank()) {
                                        marks.add(Mark.Label(textInput.trim(), 0.5f, 0.5f, textSize, drawColor.toArgb()))
                                        textInput = ""
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) { Text("+", color = palette.onAccent, fontSize = 18.sp) }
                            UndoButton(enabled = marks.any { it is Mark.Label }) {
                                val index = marks.indexOfLast { it is Mark.Label }
                                if (index >= 0) marks.removeAt(index)
                            }
                        }
                        ColorChips(drawColor) { color ->
                            drawColor = color
                            val index = marks.indexOfLast { it is Mark.Label }
                            if (index >= 0) marks[index] = (marks[index] as Mark.Label).copy(color = color.toArgb())
                        }
                        ThemedSegmentedControl(listOf(0.04f, 0.06f, 0.09f), textSize, { listOf("S", "M", "L")[listOf(0.04f, 0.06f, 0.09f).indexOf(it)] }, onSelect = { size ->
                            textSize = size
                            val index = marks.indexOfLast { it is Mark.Label }
                            if (index >= 0) marks[index] = (marks[index] as Mark.Label).copy(size = size)
                        })
                    }
                    EditorMode.WATERMARK -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemedTextField(value = watermark, onValueChange = { watermark = it.take(60) }, placeholder = F.watermarkHint)
                        CopyEditsButton { EditClipboard.copy(looks, watermark) }
                    }
                    EditorMode.TRIM -> Column {
                        RangeSlider(
                            value = trim,
                            onValueChange = { if (it.endInclusive - it.start > 0.02f) trim = it },
                            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(formatMs((trim.start * duration).toLong()), color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(formatMs((trim.endInclusive * duration).toLong()), color = Color.White, fontSize = 13.sp)
                        }
                    }
                    EditorMode.SIZE -> ThemedSegmentedControl(
                        listOf(0, 1080, 720, 480), maxHeight,
                        { if (it == 0) F.keepResolution else F.compressTo(it) },
                        { maxHeight = it }
                    )
                }
            }

            }
            val toolTabs: @Composable () -> Unit = {
            // Tool tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modes.forEach { m ->
                    val selected = m == mode
                    Text(
                        m.label(),
                        color = if (selected) palette.onAccent else Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) palette.accent else Color.White.copy(alpha = 0.08f))
                            .clickable { mode = m }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
            }
            if (landscape) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    previewArea(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        modifier = Modifier.width(420.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        toolPanel()
                        toolTabs()
                    }
                }
            } else {
                previewArea(Modifier.weight(1f).fillMaxWidth())
                toolPanel()
                toolTabs()
            }
        }

        if (saving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .blockTouchesBelow(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (item.isVideo) {
                        ThemedProgressBar(progress = progress, modifier = Modifier.width(220.dp))
                    } else {
                        CircularProgressIndicator(color = palette.accent)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(strings.saving, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100)
}

@Composable
private fun AdjustSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float> = -1f..1f, onChange: (Float) -> Unit) {
    val palette = LocalAppTheme.current.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(92.dp))
        Slider(
            value = value,
            onValueChange = { v -> onChange(if (abs(v) < 0.03f) 0f else v) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
            modifier = Modifier.weight(1f)
        )
        Text("${(value * 100).roundToInt()}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun ColorChips(selected: Color, onSelect: (Color) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        markColors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (color == selected) 3.dp else 1.dp, if (color == selected) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable { onSelect(color) }
            )
        }
    }
}

@Composable
private fun UndoButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Rounded.Undo, F.undo, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f))
    }
}

@Composable
private fun CopyEditsButton(onClick: () -> Unit) {
    val context = LocalContext.current
    Text(
        F.copyEdits,
        color = LocalAppTheme.current.palette.accent,
        fontSize = 14.sp,
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(CircleShape)
            .clickable {
                onClick()
                Toast.makeText(context, F.editsCopied, Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private fun cropRectIn(imageRect: Rect, crop: NormalizedRect) = Rect(
    imageRect.left + crop.left * imageRect.width,
    imageRect.top + crop.top * imageRect.height,
    imageRect.left + crop.right * imageRect.width,
    imageRect.top + crop.bottom * imageRect.height
)

private fun hitTest(position: Offset, rect: Rect, slop: Float): CropHandle {
    val nearLeft = abs(position.x - rect.left) < slop
    val nearRight = abs(position.x - rect.right) < slop
    val nearTop = abs(position.y - rect.top) < slop
    val nearBottom = abs(position.y - rect.bottom) < slop
    val withinX = position.x > rect.left - slop && position.x < rect.right + slop
    val withinY = position.y > rect.top - slop && position.y < rect.bottom + slop
    return when {
        nearLeft && nearTop -> CropHandle.TOP_LEFT
        nearRight && nearTop -> CropHandle.TOP_RIGHT
        nearLeft && nearBottom -> CropHandle.BOTTOM_LEFT
        nearRight && nearBottom -> CropHandle.BOTTOM_RIGHT
        nearLeft && withinY -> CropHandle.LEFT
        nearRight && withinY -> CropHandle.RIGHT
        nearTop && withinX -> CropHandle.TOP
        nearBottom && withinX -> CropHandle.BOTTOM
        rect.contains(position) -> CropHandle.MOVE
        else -> CropHandle.NONE
    }
}

private fun applyDrag(crop: NormalizedRect, handle: CropHandle, dx: Float, dy: Float): NormalizedRect {
    var left = crop.left
    var top = crop.top
    var right = crop.right
    var bottom = crop.bottom
    if (handle == CropHandle.MOVE) {
        val width = right - left
        val height = bottom - top
        left = (left + dx).coerceIn(0f, 1f - width)
        top = (top + dy).coerceIn(0f, 1f - height)
        return NormalizedRect(left, top, left + width, top + height)
    }
    if (handle == CropHandle.LEFT || handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT) {
        left = (left + dx).coerceIn(0f, right - MIN_CROP)
    }
    if (handle == CropHandle.RIGHT || handle == CropHandle.TOP_RIGHT || handle == CropHandle.BOTTOM_RIGHT) {
        right = (right + dx).coerceIn(left + MIN_CROP, 1f)
    }
    if (handle == CropHandle.TOP || handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT) {
        top = (top + dy).coerceIn(0f, bottom - MIN_CROP)
    }
    if (handle == CropHandle.BOTTOM || handle == CropHandle.BOTTOM_LEFT || handle == CropHandle.BOTTOM_RIGHT) {
        bottom = (bottom + dy).coerceIn(top + MIN_CROP, 1f)
    }
    return NormalizedRect(left, top, right, bottom)
}

/** Rotate / mirror / reset tools in the design language's toolbar style. */
@Composable
private fun EditorToolbar(
    enabled: Boolean,
    onRotate: () -> Unit,
    onMirrorHorizontally: () -> Unit,
    onMirrorVertically: () -> Unit,
    onReset: () -> Unit
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val tools: @Composable (Color) -> Unit = { tint ->
        EditorTool(Icons.Rounded.Rotate90DegreesCw, strings.rotate, tint, enabled, onRotate)
        EditorTool(Icons.Rounded.Flip, strings.flipHorizontal, tint, enabled, onMirrorHorizontally)
        EditorTool(Icons.Rounded.Flip, strings.flipVertical, tint, enabled, onMirrorVertically, iconRotation = 90f)
        EditorTool(Icons.Rounded.Restore, strings.reset, tint, enabled, onReset)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        when (theme.language) {
            DesignLanguage.GOOGLE_EXPRESSIVE -> Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(lerp(palette.surfaceVariant, palette.accent, 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) { tools(palette.onBackground) }

            DesignLanguage.IOS_LIQUID_GLASS -> Row(
                modifier = Modifier
                    .glass(
                        RoundedCornerShape(percent = 50),
                        GlassSpec(Color(0xFF2C2C2E).copy(alpha = 0.9f), Color(0xFF2C2C2E), 0.dp, GlassRim.Liquid),
                        state = null
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) { tools(Color.White) }

            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) { tools(Color.White) }
        }
    }
}

@Composable
private fun EditorTool(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRotation: Float = 0f
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .pressScale(interaction, 0.88f)
            .clip(RoundedCornerShape(16.dp))            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { rotationZ = iconRotation }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = if (enabled) tint else tint.copy(alpha = 0.4f), fontSize = 11.sp, maxLines = 1)
    }
}
