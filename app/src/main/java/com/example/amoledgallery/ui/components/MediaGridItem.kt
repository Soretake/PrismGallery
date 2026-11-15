package com.example.amoledgallery.ui.components
import com.example.amoledgallery.ui.i18n.strings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.target
import coil3.toBitmap
import kotlin.math.roundToInt
import coil3.request.crossfade
import coil3.size.Precision
import com.example.amoledgallery.AmoledGalleryApp
import com.example.amoledgallery.data.ThumbnailManager
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.design.LocalMediaOpenOrigin
import com.example.amoledgallery.ui.design.PhotoBadgeTextStyle
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import com.example.amoledgallery.ui.theme.AppTheme
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent

/**
 * Media grid tile. Thumbnails are downscaled hardware bitmaps sized by the graphics quality tier; the
 * placeholder matches the palette brightness so nothing flashes while decoding. Selection, video and
 * favorite badges follow the active design language:
 * - One UI: large white ring top-left that fills white with a dark check; the tile lightens.
 * - Material 3 Expressive: the selected photo shrinks into a more rounded shape on a tonal backing.
 * - iOS: blue check badge bottom-right, nothing on unselected tiles.
 * - HyperOS: accent check circle top-right, a light dim on the tile.
 */
private val DarkPlaceholder = ColorPainter(Color(0xFF141414))
private val LightPlaceholder = ColorPainter(Color(0xFFE4E4E4))

/**
 * The grid tile's thumbnail request. Shared, so screens can load thumbnails into Coil's memory cache
 * ahead of time (e.g. before an album page animates in) under exactly the key the tiles look up.
 */
fun thumbnailRequest(context: android.content.Context, item: MediaItem): ImageRequest {
    val px = ThumbnailManager.getInstance(context).thumbnailPx
    return ImageRequest.Builder(context)
        .data(item.thumbnail)
        .size(coil3.size.Size(px, px))
        .precision(Precision.INEXACT)
        .crossfade(false)
        .build()
}

@Composable
fun MediaGridItem(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isFavorite: Boolean = false
) {
    val context = LocalContext.current

    // Zero Main-Thread I/O: item.thumbnail is resolved directly by Coil's MediaThumbnailFetcher on
    // Dispatchers.IO. Note: bitmapConfig() is NOT set here - MediaThumbnailFetcher decodes the
    // bitmap itself (bypassing Coil's own decoder) and picks the config by quality tier, so a
    // request-level override here would have no effect anyway.
    //
    // Precision must stay INEXACT: ContentResolver.loadThumbnail() returns aspect-ratio-preserving
    // bitmaps (a 4:3 photo asked for at 120x120 comes back ~160x120), and Coil validates a memory
    // cache entry by comparing its dimensions against the request. Under Precision.EXACT every
    // single lookup was therefore rejected as "wrong size", so the memory cache never served a hit
    // and every tile re-decoded its JPEG from disk on each scroll-in - the main cause of scroll jank.
    val quality = AmoledGalleryApp.performanceProfile
    val imageRequest = remember(item.id, item.dateModified, quality, context) { thumbnailRequest(context, item) }

    val theme = LocalAppTheme.current
    val placeholder = if (theme.palette.isLight) LightPlaceholder else DarkPlaceholder
    val selected = isSelectionMode && isSelected

    // Remember where the tile sits so the fullscreen viewer can zoom out of it. Kept in a plain holder
    // (not state) and only resolved to bounds on click, so scrolling costs nothing extra.
    val openOrigin = LocalMediaOpenOrigin.current
    val coordinatesHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val openAwareClick: () -> Unit = {
        coordinatesHolder[0]?.takeIf { it.isAttached }?.let { openOrigin.record(it.boundsInRoot()) }
        onClick()
    }

    if (theme.language == DesignLanguage.GOOGLE_EXPRESSIVE) {
        ExpressiveTile(
            item, imageRequest, placeholder, theme, isSelectionMode, selected, isFavorite, openAwareClick,
            modifier.onPlaced { coordinatesHolder[0] = it }
        )
        return
    }

    Box(
        modifier = modifier
            .onPlaced { coordinatesHolder[0] = it }
            .fillMaxSize()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(theme.shapes.gridItemCornerRadius))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .thumbnail(imageRequest, placeholder)
                .clickable(onClick = openAwareClick)
        )

        if (selected) {
            val scrim = when (theme.language) {
                DesignLanguage.SAMSUNG_ONEUI -> Color.Transparent
                DesignLanguage.XIAOMI_HYPEROS -> Color.Transparent
                // iOS Photos marks a selected photo only with the blue check; the photo itself stays untouched.
                DesignLanguage.IOS_LIQUID_GLASS -> Color.Transparent
                else -> Color.White.copy(alpha = 0.12f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrim)
            )
        }

        if (item.isVideo) VideoBadge(item, theme.language)
        if (isFavorite) FavoriteBadge(theme.language)

        if (isSelectionMode) {
            // The check pops in on a spring when a tile gets selected.
            val checkScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
                label = "check_pop"
            )
            when (theme.language) {
                DesignLanguage.SAMSUNG_ONEUI -> Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.12f))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (checkScale > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = checkScale
                                    scaleY = checkScale
                                }
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(17.dp))
                        }
                    }
                }

                DesignLanguage.IOS_LIQUID_GLASS -> if (checkScale > 0.01f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                            }
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(1.5.dp)
                            .clip(CircleShape)
                            .background(theme.palette.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = com.example.amoledgallery.ui.design.contentOn(theme.palette.accent), modifier = Modifier.size(15.dp))
                    }
                }

                // HyperOS: a large ring in the lower right corner that fills blue with a white check.
                DesignLanguage.XIAOMI_HYPEROS -> Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) theme.palette.accent else Color.Black.copy(alpha = 0.12f))
                        .then(if (!isSelected) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (checkScale > 0.01f) {
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = checkScale
                                    scaleY = checkScale
                                }
                        )
                    }
                }

                else -> Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) theme.palette.accent else Color.Black.copy(alpha = 0.22f))
                        .then(if (!isSelected) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (checkScale > 0.01f) {
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(15.dp)
                                .graphicsLayer {
                                    scaleX = checkScale
                                    scaleY = checkScale
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveTile(
    item: MediaItem,
    imageRequest: ImageRequest,
    placeholder: ColorPainter,
    theme: AppTheme,
    isSelectionMode: Boolean,
    selected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val palette = theme.palette
    val baseRadius = theme.shapes.gridItemCornerRadius
    // Selected: the photo shrinks a little into a rounded frame with a thin accent ring, on the plain background.
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 480f),
        label = "expressive_tile_scale"
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 18.dp else baseRadius,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 480f),
        label = "expressive_tile_corner"
    )
    val ring by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(stiffness = 600f),
        label = "expressive_tile_ring"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(baseRadius))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // The spring overshoots below the square resting corner; a negative corner size crashes.
                    shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
                    clip = true
                }
                .thumbnail(imageRequest, placeholder)
                .then(
                    if (ring > 0.01f) {
                        Modifier.border(
                            (3f * ring).dp,
                            com.example.amoledgallery.ui.design.vividAccent(palette.accent).copy(alpha = ring),
                            RoundedCornerShape(corner.coerceAtLeast(0.dp))
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
        )

        if (item.isVideo) VideoBadge(item, theme.language)
        if (isFavorite) FavoriteBadge(theme.language)

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) palette.accent else Color.Black.copy(alpha = 0.25f))
                    .then(if (!selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.VideoBadge(item: MediaItem, language: DesignLanguage) {
    when (language) {
        // iOS Photos: just the duration, large and white in the corner, lifted off the photo by a soft shadow.
        DesignLanguage.IOS_LIQUID_GLASS -> Text(
            text = item.formattedDuration().removePrefix("0"),
            style = PhotoBadgeTextStyle.copy(fontSize = PhotoBadgeTextStyle.fontSize * 1.3f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 3.dp)
        )

        DesignLanguage.XIAOMI_HYPEROS -> Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(item.formattedDuration(), style = PhotoBadgeTextStyle.copy(fontSize = PhotoBadgeTextStyle.fontSize * 0.85f))
        }

        DesignLanguage.GOOGLE_EXPRESSIVE -> Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(item.formattedDuration(), style = PhotoBadgeTextStyle)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }

        DesignLanguage.SAMSUNG_ONEUI -> Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(item.formattedDuration(), style = PhotoBadgeTextStyle)
        }
    }
}

@Composable
private fun BoxScope.FavoriteBadge(language: DesignLanguage) {
    val alignment = if (language == DesignLanguage.GOOGLE_EXPRESSIVE || language == DesignLanguage.IOS_LIQUID_GLASS) {
        Alignment.BottomStart
    } else {
        Alignment.TopEnd
    }
    // A plain white heart disappears on bright photos, so it sits on a small dark disc in a saturated pink.
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(5.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = strings.favorite,
            tint = Color(0xFFFF4D6D),
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Draws the tile's thumbnail centre-cropped, with [placeholder] until it has loaded. Used instead of Coil's
 * AsyncImage, which gives every tile a painter with its own coroutine scope and state flows: setting that up and
 * tearing it down as tiles scrolled in and out cost about 1 ms per tile on the main thread (measured on an
 * Exynos 9810), so a fast fling that brought in two rows at once dropped frames. This enqueues the same request
 * on the same loader - a memory-cache hit still arrives synchronously, before the first draw - and cancels it
 * when the tile leaves.
 */
private fun Modifier.thumbnail(request: ImageRequest, placeholder: ColorPainter): Modifier =
    this then ThumbnailElement(request, placeholder.color)

private data class ThumbnailElement(val request: ImageRequest, val placeholder: Color) : ModifierNodeElement<ThumbnailNode>() {
    override fun create() = ThumbnailNode(request, placeholder)
    override fun update(node: ThumbnailNode) = node.update(request, placeholder)
}

private class ThumbnailNode(private var request: ImageRequest, private var placeholder: Color) :
    Modifier.Node(), DrawModifierNode {

    private var image: ImageBitmap? = null
    private var loading: Disposable? = null

    override fun onAttach() = load()

    override fun onDetach() {
        loading?.dispose()
        loading = null
        image = null
    }

    fun update(newRequest: ImageRequest, newPlaceholder: Color) {
        placeholder = newPlaceholder
        if (newRequest != request) {
            request = newRequest
            loading?.dispose()
            image = null
            if (isAttached) load()
        }
        invalidateDraw()
    }

    private fun load() {
        val target = request.newBuilder()
            .target(onSuccess = { result ->
                image = ((result as? BitmapImage)?.bitmap ?: result.toBitmap()).asImageBitmap()
                invalidateDraw()
            })
            .build()
        loading = SingletonImageLoader.get(request.context).enqueue(target)
    }

    override fun ContentDrawScope.draw() {
        val bitmap = image
        if (bitmap == null || bitmap.width == 0 || bitmap.height == 0 || size.minDimension <= 0f) {
            drawRect(placeholder)
        } else {
            // ContentScale.Crop around the centre, as AsyncImage drew it.
            val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
            val srcWidth = (size.width / scale).roundToInt().coerceIn(1, bitmap.width)
            val srcHeight = (size.height / scale).roundToInt().coerceIn(1, bitmap.height)
            drawImage(
                bitmap,
                srcOffset = IntOffset((bitmap.width - srcWidth) / 2, (bitmap.height - srcHeight) / 2),
                srcSize = IntSize(srcWidth, srcHeight),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )
        }
        drawContent()
    }
}
