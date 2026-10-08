package com.example.amoledgallery.ui.screens
import com.example.amoledgallery.ui.i18n.strings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.MediaGridItem
import androidx.compose.ui.draw.clipToBounds
import com.example.amoledgallery.ui.design.DialogButton
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.ThemedChipAction
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.design.ThemedDialog
import com.example.amoledgallery.ui.design.ThemedEmptyState
import com.example.amoledgallery.ui.design.ThemedTopBar
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.destructive
import dev.chrisbanes.haze.HazeState
import com.example.amoledgallery.ui.design.HazeSource

/**
 * MediaStore trash. Tapping an item opens it in the fullscreen viewer (in its reduced trash mode, with
 * restore and delete permanently); everything can be restored or deleted at once through the top bar.
 */
@Composable
fun TrashScreen(
    trashedItems: List<MediaItem>,
    onBack: () -> Unit,
    onItemClick: (MediaItem, Int) -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEmptyConfirmDialog by remember { mutableStateOf(false) }
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val compactActions = trashedItems.isNotEmpty() &&
        (theme.language == DesignLanguage.GOOGLE_EXPRESSIVE || theme.language == DesignLanguage.IOS_LIQUID_GLASS)
    val trashHaze = remember { HazeState() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        if (trashedItems.isEmpty()) {
            ThemedEmptyState(
                icon = Icons.Rounded.DeleteOutline,
                title = strings.trashEmpty,
                message = strings.trashEmptyMessage
            )
        } else {
            HazeSource(trashHaze, Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = theme.shapes.gridHorizontalPadding,
                        end = theme.shapes.gridHorizontalPadding,
                        // Material's top bar fades out over the first 28dp below it: start the content under that.
                        top = statusBarTop + when {
                            compactActions && theme.language == DesignLanguage.GOOGLE_EXPRESSIVE -> 136.dp
                            compactActions -> 112.dp
                            else -> 64.dp
                        },
                        bottom = navigationBottom + 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(theme.shapes.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(theme.shapes.gridSpacing)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = strings.trashInfo,
                            color = palette.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    // The regular media tile: the small cached thumbnail, cropped to a square, with the video
                    // badge. It records its position, so the viewer grows out of it.
                    items(trashedItems, key = { it.id }, contentType = { "media_tile" }) { item ->
                        MediaGridItem(
                            item = item,
                            onClick = { onItemClick(item, trashedItems.indexOf(item)) },
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clipToBounds()
                        )
                    }
                }
            }
        }

        ThemedTopBar(
            title = strings.trash,
            subtitle = if (trashedItems.isEmpty()) null else strings.items(trashedItems.size),
            onBack = onBack,
            hazeState = trashHaze,
            modifier = Modifier.align(Alignment.TopCenter),
            // iOS and Material center/size their title so that two text actions beside it would leave
            // no room for strings.trash; they get a compact second row instead.
            secondaryActions = if (compactActions) {
                {
                    ThemedChipAction(strings.restore, palette.accent, onRestoreAll, trashHaze)
                    ThemedChipAction(strings.emptyAction, palette.destructive, { showEmptyConfirmDialog = true }, trashHaze)
                }
            } else {
                null
            }
        ) {
            if (trashedItems.isNotEmpty() && !compactActions) {
                TopBarTextAction(strings.restore, palette.accent, onRestoreAll)
                TopBarTextAction(strings.emptyAction, palette.destructive) { showEmptyConfirmDialog = true }
            }
        }
    }

    if (showEmptyConfirmDialog) {
        ThemedDialog(
            onDismissRequest = { showEmptyConfirmDialog = false },
            title = strings.emptyTrashTitle,
            message = strings.emptyTrashMessage(trashedItems.size),
            buttons = listOf(
                DialogButton(strings.cancel, DialogButtonStyle.Cancel) { showEmptyConfirmDialog = false },
                DialogButton(strings.emptyAction, DialogButtonStyle.Destructive) {
                    showEmptyConfirmDialog = false
                    onEmptyTrash()
                }
            )
        )
    }
}

@Composable
internal fun TopBarTextAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
