package com.example.amoledgallery.ui.components
import com.example.amoledgallery.ui.i18n.LanguageState
import com.example.amoledgallery.ui.i18n.strings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import com.example.amoledgallery.ui.theme.isLight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.design.ThemedCard
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Extracted detailed file metadata.
 */
data class MediaMetadata(
    val formattedDate: String = "",
    val fileName: String = "",
    val fileSizeFormatted: String = "",
    val resolutionFormatted: String = "",
    val megapixelsFormatted: String = "",
    val storagePath: String = "",
    val albumName: String = "",
    val mimeType: String = "",
    // Camera EXIF
    val cameraDevice: String? = null,
    val cameraSettings: String? = null,
    val focalLength: String? = null,
    val iso: String? = null,
    val flashUsed: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** "Berlin, Germany": the nearest town from the bundled offline list, looked up after loading. */
    val placeName: String? = null,
    // Video
    val durationFormatted: String? = null,
    val bitrateFormatted: String? = null,
    // Individual capture values as iOS Photos lists them ("ƒ1,6", "1/43 s", "0 ev", "26 mm")
    val fNumberText: String? = null,
    val exposureTimeText: String? = null,
    val exposureBiasText: String? = null,
    val focal35Text: String? = null,
    /** File format as a short label ("JPEG", "HEIC", "MP4"). */
    val formatLabel: String = "",
    /** "Samstag, 18. April 2026 um 12:10" */
    val fullDate: String = "",
    /** "Ultra HDR" when Android finds a gain map in the photo (Android 14+), else null. */
    val hdrLabel: String? = null
)

/**
 * Whether Android decodes [item] with an HDR gain map (Ultra HDR / ISO 21496-1). Only files that name a gain
 * map in their metadata are decoded, and then only as a small preview.
 */
private fun hasGainmap(context: Context, item: MediaItem): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE || item.isVideo) return false
    return runCatching {
        val head = context.contentResolver.openInputStream(item.uri)?.use { input ->
            val buffer = ByteArray(128 * 1024)
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n <= 0) break
                read += n
            }
            String(buffer, 0, read, Charsets.ISO_8859_1)
        } ?: return false
        if (!head.contains("hdrgm") && !head.contains("21496")) return false
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, item.uri)
        val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / 256))
        }
        bitmap.hasGainmap().also { bitmap.recycle() }
    }.getOrDefault(false)
}

/** The town (when known) above the coordinates, which few people can place by themselves. */
internal fun MediaMetadata.locationText(): String {
    val coordinates = "${String.format(Locale.US, "%.5f", latitude)}, ${String.format(Locale.US, "%.5f", longitude)}"
    return placeName?.let { "$it\n$coordinates" } ?: coordinates
}

/** Loads [item]'s metadata off the main thread once a page swipe has settled. */
@Composable
private fun rememberMediaMetadata(item: MediaItem): MediaMetadata? {
    val context = LocalContext.current
    var metadata by remember(item.id) { mutableStateOf<MediaMetadata?>(null) }
    // displayName and dateModified too: a rename or a new date / location keeps the id and uri, and the open
    // viewer must show the change.
    LaunchedEffect(item.id, item.uri, item.displayName, item.dateModified, LanguageState.language) {
        // Let a page swipe settle first; the result is then applied on the main thread in one go.
        kotlinx.coroutines.delay(200)
        val loaded = withContext(Dispatchers.IO) { extractMediaMetadata(context, item) }
        metadata = loaded
        // The place name follows once the offline town list is loaded (the first time takes a moment).
        if (loaded.latitude != null && loaded.longitude != null) {
            com.example.amoledgallery.ui.features.Geo.ensureLoaded(context)
            com.example.amoledgallery.ui.features.Geo.nearest(loaded.latitude, loaded.longitude)?.let { town ->
                metadata = loaded.copy(placeName = town.label)
            }
        }
    }
    return metadata
}

/**
 * Details & metadata of [item] as the language's cards. The fullscreen viewer shows it in the panel
 * that opens underneath the photo; the caller provides scrolling. iOS lays them out like Photos' info view.
 */
@Composable
fun MediaInfoContent(
    item: MediaItem,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null
) {
    val theme = LocalAppTheme.current
    if (theme.language == DesignLanguage.IOS_LIQUID_GLASS) {
        IosMediaInfoContent(item, modifier)
        return
    }
    if (theme.language == DesignLanguage.SAMSUNG_ONEUI) {
        OneUiMediaInfoContent(item, onEdit, modifier)
        return
    }
    if (theme.language == DesignLanguage.GOOGLE_EXPRESSIVE) {
        GoogleMediaInfoContent(item, modifier)
        return
    }
    val context = LocalContext.current
    val metadata = rememberMediaMetadata(item)
    val palette = theme.palette

    Column(modifier = modifier) {
        val meta = metadata
        if (meta == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = strings.loadingMetadata, color = palette.textMuted, fontSize = 14.sp)
            }
        } else {
            MetadataCard {
                MetadataRow(Icons.Rounded.CalendarToday, strings.dateTime, meta.formattedDate)
                MetadataRow(Icons.Rounded.Description, strings.fileName, meta.fileName)
            }

            Spacer(modifier = Modifier.height(12.dp))

            MetadataCard {
                if (meta.resolutionFormatted.isNotBlank()) {
                    val resSubtitle = listOfNotNull(meta.resolutionFormatted, meta.megapixelsFormatted.ifBlank { null }, meta.hdrLabel)
                        .joinToString(" • ")
                    MetadataRow(Icons.Rounded.AspectRatio, strings.resolution, resSubtitle)
                }
                if (meta.fileSizeFormatted.isNotBlank()) {
                    MetadataRow(Icons.Rounded.Storage, strings.fileSize, meta.fileSizeFormatted)
                }
                if (meta.durationFormatted != null) {
                    MetadataRow(Icons.Rounded.Movie, strings.videoDuration, meta.durationFormatted)
                }
                if (meta.bitrateFormatted != null) {
                    MetadataRow(Icons.Rounded.Tune, strings.bitrate, meta.bitrateFormatted)
                }
                MetadataRow(
                    Icons.Rounded.Folder,
                    strings.filePath,
                    meta.storagePath.ifBlank { "Pictures/${meta.albumName}/${meta.fileName}" }
                )
            }

            if (meta.cameraDevice != null || meta.cameraSettings != null) {
                Spacer(modifier = Modifier.height(12.dp))
                MetadataCard {
                    if (meta.cameraDevice != null) {
                        MetadataRow(Icons.Rounded.CameraAlt, strings.cameraModel, meta.cameraDevice)
                    }
                    if (meta.cameraSettings != null) {
                        MetadataRow(Icons.Rounded.Tune, strings.captureSettings, meta.cameraSettings)
                    }
                }
            }

            if (meta.latitude != null && meta.longitude != null) {
                Spacer(modifier = Modifier.height(12.dp))
                MetadataCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetadataRow(
                            icon = Icons.Rounded.LocationOn,
                            title = strings.captureLocation,
                            value = meta.locationText(),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = strings.map,
                            color = palette.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { openMap(context, meta) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * HyperOS Gallery's "Detail info" page: a back arrow and centred title, then label/value groups - the capture
 * date and weekday in blue, the file name in blue with its size and resolution, the camera with its capture
 * values, and the storage path in blue.
 */
@Composable
fun HyperOsDetailInfoPage(item: MediaItem, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalAppTheme.current.palette
    val meta = rememberMediaMetadata(item)
    val dateMillis = item.date * 1000L
    val locale = LanguageState.locale
    val date = remember(dateMillis, locale) { SimpleDateFormat(strings.longDatePattern, locale).format(Date(dateMillis)) }
    val weekday = remember(dateMillis, locale) { SimpleDateFormat("EEEE", locale).format(Date(dateMillis)) }
    val time = remember(dateMillis, locale) { SimpleDateFormat("HH:mm", locale).format(Date(dateMillis)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings.back, tint = palette.onBackground, modifier = Modifier.size(26.dp))
            }
            Text(
                text = strings.detailInfo,
                color = palette.onBackground,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))

        HyperOsInfoGroup(strings.takenOn) {
            Text(date, color = palette.accent, fontSize = 16.sp)
            Row {
                Text(weekday, color = palette.accent, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Text(time, color = palette.accent, fontSize = 16.sp)
            }
        }
        if (meta != null) {
            HyperOsInfoGroup(strings.fileInfo) {
                Text(meta.fileName, color = palette.accent, fontSize = 16.sp)
                val sizeLine = listOfNotNull(meta.fileSizeFormatted, meta.resolutionFormatted, meta.hdrLabel).filter { it.isNotBlank() }
                if (sizeLine.isNotEmpty()) Text(sizeLine.joinToString("    "), color = palette.textMuted, fontSize = 16.sp)
                val videoLine = listOfNotNull(meta.durationFormatted, meta.bitrateFormatted)
                if (videoLine.isNotEmpty()) Text(videoLine.joinToString("    "), color = palette.textMuted, fontSize = 16.sp)
            }
            val exposure = listOfNotNull(
                meta.fNumberText,
                meta.exposureTimeText,
                meta.iso?.let { if (it.startsWith("ISO", ignoreCase = true)) it else "ISO$it" }
            )
            val lens = listOfNotNull(meta.focalLength, if (meta.flashUsed == true) strings.flashFired else null)
            if (meta.cameraDevice != null || exposure.isNotEmpty() || lens.isNotEmpty()) {
                HyperOsInfoGroup(strings.exifData) {
                    if (meta.cameraDevice != null) Text(meta.cameraDevice, color = palette.onBackground, fontSize = 16.sp)
                    if (exposure.isNotEmpty()) Text(exposure.joinToString("    "), color = palette.textMuted, fontSize = 16.sp)
                    if (lens.isNotEmpty()) Text(lens.joinToString("    "), color = palette.textMuted, fontSize = 16.sp)
                }
            }
            HyperOsInfoGroup(strings.localPath) {
                Text(
                    meta.storagePath.ifBlank { "Pictures/${meta.albumName}/${meta.fileName}" },
                    color = palette.accent,
                    fontSize = 16.sp
                )
            }
        } else {
            Text(strings.loadingMetadata, color = palette.textMuted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 30.dp))
        }
    }
}

/**
 * Samsung Gallery's details under the photo: the weekday, date and time in bold with "Bearbeiten" on the right,
 * the file name and folder in grey, then "Bild-Info" with size, resolution and megapixels split by hairlines,
 * and the camera with its capture values.
 */
@Composable
private fun OneUiMediaInfoContent(item: MediaItem, onEdit: (() -> Unit)?, modifier: Modifier) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    val meta = rememberMediaMetadata(item)
    val dateMillis = item.date * 1000L
    val locale = LanguageState.locale
    val dateLine = remember(dateMillis, locale) {
        SimpleDateFormat("EEEE, ${strings.longDatePattern} · HH:mm", locale).format(Date(dateMillis))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dateLine, color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (onEdit != null) {
                Text(
                    text = strings.edit,
                    color = palette.accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        if (meta == null) {
            Text(strings.loadingMetadata, color = palette.textMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            return@Column
        }
        Text(meta.fileName, color = palette.textMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        Text(meta.storagePath.ifBlank { "Pictures/${meta.albumName}" }, color = palette.textMuted, fontSize = 15.sp)

        Spacer(modifier = Modifier.height(26.dp))
        Text(strings.imageInfo, color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        OneUiValueLine(
            listOfNotNull(
                meta.fileSizeFormatted.ifBlank { null },
                meta.resolutionFormatted.ifBlank { null }?.replace(" ", "")?.replace("×", "x"),
                meta.megapixelsFormatted.ifBlank { null }?.replace(" ", ""),
                meta.hdrLabel,
                meta.durationFormatted,
                meta.bitrateFormatted
            )
        )

        val capture = listOfNotNull(
            meta.fNumberText,
            meta.exposureTimeText,
            meta.iso?.let { if (it.startsWith("ISO", ignoreCase = true)) it else "ISO $it" },
            meta.focalLength
        )
        if (meta.cameraDevice != null || capture.isNotEmpty()) {
            Spacer(modifier = Modifier.height(22.dp))
            Text(meta.cameraDevice ?: strings.cameraModel, color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            OneUiValueLine(capture)
        }

        if (meta.latitude != null && meta.longitude != null) {
            Spacer(modifier = Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.captureLocation, color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        meta.locationText(),
                        color = palette.textMuted,
                        fontSize = 15.sp
                    )
                }
                Text(
                    text = strings.map,
                    color = palette.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { openMap(context, meta) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Gemini details, in the look of the Gemini screens: the day and date as a large heading with the
 * time and the photo's size under it, the capture values as a grid of small tiles (big value, small
 * label) in the dark Gemini chips with a hairline edge, and the file, camera and place as rows with
 * a tinted icon. It sits on the glowing glass panel (see InfoPanel).
 */
@Composable
private fun GoogleMediaInfoContent(item: MediaItem, modifier: Modifier) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    val meta = rememberMediaMetadata(item)
    val dateMillis = item.date * 1000L
    val locale = LanguageState.locale
    val weekday = remember(dateMillis, locale) { SimpleDateFormat("EEEE", locale).format(Date(dateMillis)) }
    val day = remember(dateMillis, locale) { SimpleDateFormat(strings.longDatePattern, locale).format(Date(dateMillis)) }
    val time = remember(dateMillis, locale) { SimpleDateFormat("HH:mm", locale).format(Date(dateMillis)) }
    val accentText = if (palette.isLight) palette.accent else com.example.amoledgallery.ui.design.vividAccent(palette.accent)

    Column(modifier = modifier.fillMaxWidth()) {
        // Heading: the weekday small in the accent, the date large, then time, megapixels and size.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            com.example.amoledgallery.ui.design.GeminiSparkle(modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(weekday, color = accentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            day,
            color = palette.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp)
        )
        val summary = listOfNotNull(
            time,
            meta?.megapixelsFormatted?.takeIf { it.isNotBlank() },
            meta?.hdrLabel,
            meta?.fileSizeFormatted?.takeIf { it.isNotBlank() },
            meta?.durationFormatted
        ).joinToString("  ·  ")
        Text(summary, color = palette.textMuted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))

        if (meta == null) {
            Text(strings.loadingMetadata, color = palette.textMuted, fontSize = 15.sp, modifier = Modifier.padding(6.dp, 16.dp))
            return@Column
        }

        // Capture values as tiles, three to a row.
        val tiles = buildList {
            meta.fNumberText?.let { add(it to strings.infoAperture) }
            meta.exposureTimeText?.let { add(it to strings.infoShutter) }
            meta.iso?.removePrefix("ISO")?.trim()?.takeIf { it.isNotBlank() }?.let { add(it to "ISO") }
            (meta.focal35Text ?: meta.focalLength)?.let { add(it to strings.infoFocalLength) }
            meta.exposureBiasText?.let { add(it to strings.infoExposure) }
            meta.resolutionFormatted.takeIf { it.isNotBlank() }?.let { add(it to strings.resolution) }
            meta.bitrateFormatted?.let { add(it to strings.bitrate) }
        }
        if (tiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (value, label) -> GeminiStatTile(value, label, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        GeminiInfoGroup {
            GeminiInfoRow(
                Icons.Rounded.Description,
                meta.fileName,
                meta.storagePath.ifBlank { "Pictures/${meta.albumName}/" }.substringBeforeLast('/')
            )
            if (meta.cameraDevice != null) {
                GeminiInfoDivider()
                GeminiInfoRow(
                    Icons.Rounded.CameraAlt,
                    meta.cameraDevice,
                    listOfNotNull(meta.formatLabel.takeIf { it.isNotBlank() }, meta.focalLength).joinToString("  ·  ").ifBlank { null }
                )
            }
            if (meta.latitude != null && meta.longitude != null) {
                GeminiInfoDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        GeminiInfoRow(
                            Icons.Rounded.LocationOn,
                            strings.captureLocation,
                            meta.locationText()
                        )
                    }
                    Text(
                        text = strings.map,
                        color = accentText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .geminiPill()
                            .clickable { openMap(context, meta) }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

/** A Gemini chip: the dark grey of the Gemini pills with a hairline edge (a soft grey in light mode). */
@Composable
private fun Modifier.geminiPill(shape: androidx.compose.ui.graphics.Shape = CircleShape): Modifier {
    val palette = LocalAppTheme.current.palette
    return this
        .clip(shape)
        .background(com.example.amoledgallery.ui.design.geminiSelectionOnBackground(palette.isLight))
        .border(1.dp, com.example.amoledgallery.ui.design.geminiSelectionEdge(palette.isLight), shape)
}

@Composable
private fun GeminiStatTile(value: String, label: String, modifier: Modifier) {
    val palette = LocalAppTheme.current.palette
    // A tile is a third of the row, and a full resolution ("8160 × 6120") does not fit there at full
    // size: it used to be cut off mid-number. The value shrinks until it fits instead.
    var valueSize by remember(value) { mutableStateOf(17.sp) }
    Column(
        modifier = modifier
            .geminiPill(RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = value,
            color = palette.onBackground,
            fontSize = valueSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { layout ->
                if (layout.hasVisualOverflow && valueSize.value > 11f) {
                    valueSize = (valueSize.value - 0.8f).sp
                }
            }
        )
        Text(label, color = palette.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** File, camera and place in one Gemini card, separated by hairlines. */
@Composable
private fun GeminiInfoGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .geminiPill(RoundedCornerShape(26.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        content = content
    )
}

@Composable
private fun GeminiInfoDivider() {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 54.dp)
            .height(0.5.dp)
            .background(palette.onBackground.copy(alpha = 0.08f))
    )
}

@Composable
private fun GeminiInfoRow(icon: ImageVector, title: String, subtitle: String?) {
    val palette = LocalAppTheme.current.palette
    val tint = if (palette.isLight) palette.accent else com.example.amoledgallery.ui.design.vividAccent(palette.accent)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (palette.isLight) 0.12f else 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, color = palette.onBackground, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = palette.textMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Values in one grey line, separated by short vertical hairlines ("2,15 MB | 2268x3096 | 7MP"). */
@Composable
private fun OneUiValueLine(values: List<String>) {
    if (values.isEmpty()) return
    val palette = LocalAppTheme.current.palette
    Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { index, value ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .height(16.dp)
                        .background(palette.onBackground.copy(alpha = 0.2f))
                )
            }
            Text(value, color = palette.textMuted, fontSize = 15.sp, maxLines = 1)
        }
    }
}

@Composable
private fun HyperOsInfoGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalAppTheme.current.palette
    Row(modifier = Modifier.padding(start = 30.dp, end = 30.dp, bottom = 40.dp)) {
        Text(label, color = palette.textMuted, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/**
 * iOS Photos' info view: one card with the capture date and file name, the camera with a format chip, the
 * lens and size summary and a row of capture values; then the location (when known) and the album.
 */
@Composable
private fun IosMediaInfoContent(item: MediaItem, modifier: Modifier) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    val meta = rememberMediaMetadata(item)
    val divider = palette.onBackground.copy(alpha = 0.10f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IosInfoCard {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    text = meta?.fullDate ?: strings.loadingMetadata,
                    color = palette.onBackground,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.displayName.ifBlank { strings.itemFallback },
                    color = palette.textMuted,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (meta != null) {
                IosDivider(divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = meta.cameraDevice ?: strings.noCameraInfo,
                        color = if (meta.cameraDevice != null) palette.onBackground else palette.textMuted,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (meta.hdrLabel != null) {
                        Text(
                            text = "HDR",
                            color = palette.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(CircleShape)
                                .background(palette.onBackground.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (meta.formatLabel.isNotBlank()) {
                        Text(
                            text = meta.formatLabel,
                            color = palette.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(palette.onBackground.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                val lensLine = listOfNotNull(meta.focal35Text, meta.fNumberText).joinToString(" ")
                val sizeLine = listOfNotNull(
                    meta.megapixelsFormatted.ifBlank { null },
                    meta.resolutionFormatted.ifBlank { null },
                    meta.fileSizeFormatted.ifBlank { null },
                    meta.durationFormatted
                ).joinToString(" • ")
                if (lensLine.isNotBlank() || sizeLine.isNotBlank()) {
                    IosDivider(divider)
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (lensLine.isNotBlank()) Text(lensLine, color = palette.textMuted, fontSize = 17.sp)
                        if (sizeLine.isNotBlank()) Text(sizeLine, color = palette.textMuted, fontSize = 17.sp)
                    }
                }

                val cells = listOfNotNull(
                    meta.iso?.let { "ISO $it" },
                    meta.focalLength,
                    meta.exposureBiasText,
                    meta.fNumberText,
                    meta.exposureTimeText
                )
                if (cells.isNotEmpty()) {
                    IosDivider(divider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cells.forEachIndexed { index, cell ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(divider)
                                )
                            }
                            Text(
                                text = cell,
                                color = palette.textMuted,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (meta?.latitude != null && meta.longitude != null) {
            IosInfoCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.captureLocation, color = palette.onBackground, fontSize = 17.sp)
                        Text(
                            text = meta.locationText(),
                            color = palette.textMuted,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = strings.map,
                        color = palette.accent,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { openMap(context, meta) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        IosInfoCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = palette.onAccent, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = palette.textMuted)) { append(strings.inAlbumPrefix + " ") }
                        withStyle(SpanStyle(color = palette.onBackground)) { append(item.bucketDisplayName.ifBlank { strings.gallery }) }
                    },
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun IosInfoCard(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(palette.surface),
        content = content
    )
}

@Composable
private fun IosDivider(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

private fun openMap(context: Context, meta: MediaMetadata) {
    val geoUri = Uri.parse("geo:${meta.latitude},${meta.longitude}?q=${meta.latitude},${meta.longitude}(${Uri.encode(meta.fileName)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
    } catch (_: Exception) {
    }
}

@Composable
private fun MetadataCard(content: @Composable ColumnScope.() -> Unit) {
    // One step lighter than the details panel, so the cards stay visible on it.
    ThemedCard(inSheet = true, content = content)
}

@Composable
private fun MetadataRow(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = palette.textMuted, fontSize = 12.sp)
            Text(
                text = value,
                color = palette.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Opens a photo with its location tags intact. Since Android 10 the system strips GPS data from files the app did not
 * create, unless the app holds ACCESS_MEDIA_LOCATION and asks MediaStore for the original. Without the permission, or
 * for files that are not in the media library ("Open with" from another app), the normal stream is used.
 */
internal fun openWithLocation(context: Context, uri: Uri): java.io.InputStream? {
    if (uri.authority == MediaStore.AUTHORITY &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) {
        try {
            context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))?.let { return it }
        } catch (_: Exception) {
            // Not available for this file: fall back to the normal stream below.
        }
    }
    return context.contentResolver.openInputStream(uri)
}

/**
 * Extracts comprehensive metadata from file, ContentResolver and ExifInterface.
 */
private fun extractMediaMetadata(context: Context, item: MediaItem): MediaMetadata {
    val locale = LanguageState.locale
    // Decimal separator of the app language: "ƒ1,6" / "ƒ1.6".
    val decimal = DecimalFormat("#.#", DecimalFormatSymbols(locale))

    // 1. Date formatting
    val millis = item.date * 1000L
    val dateStr = if (millis > 0) {
        val sdf = SimpleDateFormat(strings.metadataDatePattern, locale)
        sdf.format(Date(millis))
    } else ""
    val fullDate = if (millis > 0) SimpleDateFormat(strings.infoDatePattern, locale).format(Date(millis)) else ""

    // 2. File size
    var sizeBytes = 0L
    if (item.dataPath.isNotBlank()) {
        try {
            val f = File(item.dataPath)
            if (f.exists()) sizeBytes = f.length()
        } catch (_: Exception) {}
    }
    if (sizeBytes <= 0L) {
        try {
            context.contentResolver.openFileDescriptor(item.uri, "r")?.use {
                sizeBytes = it.statSize
            }
        } catch (_: Exception) {}
    }
    val sizeStr = formatFileSize(sizeBytes, decimal)

    // 3. Media info (Dimensions, Camera EXIF or Video Info)
    var resStr = ""
    var mpStr = ""
    var camDevice: String? = null
    var camSettings: String? = null
    var focal: String? = null
    var isoStr: String? = null
    var flashUsed: Boolean? = null
    var lat: Double? = null
    var lon: Double? = null
    var videoDuration: String? = null
    var videoBitrate: String? = null
    var fNumberText: String? = null
    var exposureTimeText: String? = null
    var exposureBiasText: String? = null
    var focal35Text: String? = null

    if (item.isVideo) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (w > 0 && h > 0) {
                resStr = "$w × $h"
                val mp = (w.toDouble() * h) / 1_000_000.0
                mpStr = "${decimal.format(mp)} MP"
            }
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: item.durationMs
            if (durMs > 0) {
                val totalSec = durMs / 1000
                val m = (totalSec % 3600) / 60
                val s = totalSec % 60
                val hrs = totalSec / 3600
                videoDuration = strings.durationText(hrs, m, s)
            }
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            if (bitrate > 0) {
                val mbps = bitrate.toDouble() / 1_000_000.0
                videoBitrate = "${decimal.format(mbps)} Mbps"
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    } else {
        // Image EXIF
        try {
            openWithLocation(context, item.uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                if (w > 0 && h > 0) {
                    resStr = "$w × $h"
                    val mp = (w.toDouble() * h) / 1_000_000.0
                    mpStr = "${decimal.format(mp)} MP"
                }

                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                if (!model.isNullOrBlank()) {
                    camDevice = if (!make.isNullOrBlank() && !model.startsWith(make, ignoreCase = true)) {
                        "$make $model"
                    } else {
                        model
                    }
                }

                val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                val focalLen = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)

                val settingsParts = mutableListOf<String>()
                if (!fNumber.isNullOrBlank()) {
                    settingsParts.add("f/$fNumber")
                    exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0.0 }?.let {
                        fNumberText = "ƒ${decimal.format(it)}"
                    }
                }
                if (!exposure.isNullOrBlank()) {
                    val expDouble = exposure.toDoubleOrNull()
                    if (expDouble != null && expDouble < 1.0 && expDouble > 0.0) {
                        val fraction = (1.0 / expDouble).roundToInt()
                        settingsParts.add("1/${fraction}s")
                        exposureTimeText = "1/$fraction s"
                    } else {
                        settingsParts.add("${exposure}s")
                        exposureTimeText = expDouble?.let { "${decimal.format(it)} s" }
                    }
                }
                if (!iso.isNullOrBlank()) {
                    settingsParts.add("ISO $iso")
                    isoStr = iso
                }
                if (!focalLen.isNullOrBlank()) {
                    val fVal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    if (fVal > 0.0) {
                        settingsParts.add("${decimal.format(fVal)}mm")
                        focal = "${decimal.format(fVal)} mm"
                    }
                }
                if (exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE) != null) {
                    exposureBiasText = "${decimal.format(exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0))} ev"
                }
                exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0).takeIf { it > 0 }?.let {
                    focal35Text = "$it mm"
                }

                if (settingsParts.isNotEmpty()) {
                    camSettings = settingsParts.joinToString(" • ")
                }

                val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
                if (flash != -1) {
                    flashUsed = (flash and 1) != 0
                }

                val latLong = exif.latLong
                if (latLong != null && latLong.size >= 2) {
                    lat = latLong[0]
                    lon = latLong[1]
                }
            }
        } catch (_: Exception) {}
    }

    // Files without size EXIF (PNG, screenshots) still have MediaStore's dimensions.
    if (resStr.isBlank() && item.width > 0 && item.height > 0) {
        val rotated = item.orientation % 180 != 0
        val w = if (rotated) item.height else item.width
        val h = if (rotated) item.width else item.height
        resStr = "$w × $h"
        mpStr = "${decimal.format((w.toDouble() * h) / 1_000_000.0)} MP"
    }

    return MediaMetadata(
        formattedDate = dateStr,
        fileName = item.displayName.ifBlank { "media_${item.id}" },
        fileSizeFormatted = sizeStr,
        resolutionFormatted = resStr,
        megapixelsFormatted = mpStr,
        storagePath = item.dataPath.ifBlank { item.relativePath },
        albumName = item.bucketDisplayName.ifBlank { strings.gallery },
        mimeType = item.mimeType,
        cameraDevice = camDevice,
        cameraSettings = camSettings,
        focalLength = focal,
        iso = isoStr,
        flashUsed = flashUsed,
        latitude = lat,
        longitude = lon,
        durationFormatted = videoDuration,
        bitrateFormatted = videoBitrate,
        fNumberText = fNumberText,
        exposureTimeText = exposureTimeText,
        exposureBiasText = exposureBiasText,
        focal35Text = focal35Text,
        formatLabel = item.mimeType.substringAfter('/', "").substringAfterLast('.').uppercase(Locale.ROOT)
            .removePrefix("X-").ifBlank { item.displayName.substringAfterLast('.', "").uppercase(Locale.ROOT) },
        fullDate = fullDate,
        hdrLabel = if (hasGainmap(context, item)) {
            // Android says whether this screen can light photo highlights above white at all; when it cannot,
            // HDR photos look like normal photos in every app (HDR video uses a separate path).
            val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
                ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            if (display != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !display.isHdrSdrRatioAvailable
            ) "Ultra HDR (${com.example.amoledgallery.ui.i18n.F.hdrScreenUnsupported})" else "Ultra HDR"
        } else null
    )
}

private fun formatFileSize(bytes: Long, decimal: DecimalFormat): String {
    if (bytes <= 0L) return ""
    // Decimal units, as Android's own storage settings and the rest of the app count.
    val kb = bytes / 1000.0
    val mb = kb / 1000.0
    val gb = mb / 1000.0
    return when {
        gb >= 1.0 -> "${decimal.format(gb)} GB"
        mb >= 1.0 -> "${decimal.format(mb)} MB"
        else -> "${decimal.format(kb)} KB"
    }
}
