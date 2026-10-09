package com.example.amoledgallery.ui.features

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.theme.LocalAppTheme

/**
 * HyperOS: pulling the album list down past its top reveals the private album - the list follows the finger,
 * a hint appears in the space above it, and released far enough the private album opens.
 */
@Composable
fun Modifier.pullToOpenPrivateAlbum(enabled: Boolean): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current
    val threshold = with(density) { 120.dp.toPx() }
    val textSize = with(density) { 14.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val shown by animateFloatAsState(pull, label = "private_pull")
    val color = LocalAppTheme.current.palette.textMuted.toArgb()
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scrolling back up first takes back the pull.
                if (available.y < 0f && pull > 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Only a finger dragging past the top pulls; a fling running into it does not.
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(threshold * 1.6f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull >= threshold) Features.open(FeatureScreen.PrivateAlbum)
                pull = 0f
                return Velocity.Zero
            }
        }
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }
    return this
        .nestedScroll(connection)
        .graphicsLayer { translationY = shown }
        .drawWithContent {
            drawContent()
            if (shown > 4f) {
                paint.color = color
                paint.textSize = textSize
                paint.alpha = ((shown / threshold).coerceIn(0f, 1f) * 255).toInt()
                drawIntoCanvas {
                    it.nativeCanvas.drawText(
                        if (pull >= threshold) F.releaseForPrivate else F.pullForPrivate,
                        size.width / 2f, -shown / 2f + textSize / 2f, paint
                    )
                }
            }
        }
}
