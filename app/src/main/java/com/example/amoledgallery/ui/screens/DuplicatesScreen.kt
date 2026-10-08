package com.example.amoledgallery.ui.screens
import com.example.amoledgallery.ui.i18n.strings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.amoledgallery.data.DuplicateGroup
import com.example.amoledgallery.data.DuplicateProgress
import com.example.amoledgallery.ui.components.thumbnailRequest
import com.example.amoledgallery.ui.design.DialogButton
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.HazeSource
import com.example.amoledgallery.ui.design.ThemedButton
import com.example.amoledgallery.ui.design.ThemedDialog
import com.example.amoledgallery.ui.design.ThemedEmptyState
import com.example.amoledgallery.ui.design.ThemedProgressBar
import com.example.amoledgallery.ui.design.ThemedTopBar
import com.example.amoledgallery.ui.design.bottomBarContentPadding
import com.example.amoledgallery.ui.i18n.readableSize
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.destructive
import com.example.amoledgallery.ui.theme.onAccent
import dev.chrisbanes.haze.HazeState

/**
 * The duplicates: files that are byte for byte the same, not merely alike.
 *
 * Each set is a row of its copies, the newest first and already unticked - that is the one kept. The
 * others are ticked and go to the trash together, where they can still be fetched back. A tap changes
 * which copies of a set are ticked; at least nothing stops every copy being ticked, but the bar then
 * says so, since deleting a whole set keeps nothing.
 */
@Composable
fun DuplicatesScreen(
    groups: List<DuplicateGroup>,
    selection: Set<Long>,
    progress: DuplicateProgress?,
    scanned: Boolean,
    onBack: () -> Unit,
    onToggle: (Long) -> Unit,
    onRescan: () -> Unit,
    onDelete: () -> Unit,
    selectionBytes: Long,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val haze = remember { HazeState() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var confirmDelete by remember { mutableStateOf(false) }
    val selectedCount = selection.size
    val totalWasted = groups.sumOf { it.wastedBytes }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            progress != null -> ScanningState(progress)

            groups.isEmpty() && scanned -> ThemedEmptyState(
                icon = Icons.Rounded.ContentCopy,
                title = strings.noDuplicates,
                message = strings.noDuplicatesMessage
            )

            groups.isEmpty() -> Unit

            else -> HazeSource(haze, Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        // Material's top bar fades out over the first 28dp below it: start the content under that.
                        top = statusBarTop + if (theme.language == com.example.amoledgallery.ui.theme.DesignLanguage.GOOGLE_EXPRESSIVE) 96.dp else 72.dp,
                        bottom = navigationBottom + if (selectedCount > 0) 132.dp else 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item(key = "summary") {
                        Text(
                            text = strings.duplicatesSummary(groups.size, readableSize(totalWasted)),
                            color = palette.textMuted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                    items(groups.size, key = { groups[it].items.first().id }) { index ->
                        DuplicateGroupRow(
                            group = groups[index],
                            selection = selection,
                            onToggle = onToggle
                        )
                    }
                }
            }
        }

        ThemedTopBar(
            title = strings.duplicates,
            subtitle = if (groups.isEmpty()) null else strings.duplicateSets(groups.size),
            onBack = onBack,
            hazeState = haze,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            if (progress == null) {
                TopBarTextAction(strings.rescan, palette.accent, onRescan)
            }
        }

        // What the ticked copies would free, and the button that does it.
        if (selectedCount > 0 && progress == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = bottomBarContentPadding(20.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.duplicatesFrees(selectedCount, readableSize(selectionBytes)),
                    color = palette.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                ThemedButton(
                    text = strings.deleteCopies(selectedCount),
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (confirmDelete) {
        // Whole sets ticked would leave nothing behind, so the message says as much.
        val losingAll = groups.count { group -> group.items.all { it.id in selection } }
        ThemedDialog(
            onDismissRequest = { confirmDelete = false },
            title = strings.deleteCopiesTitle,
            message = if (losingAll > 0) {
                strings.deleteCopiesMessageAll(selectedCount, losingAll)
            } else {
                strings.deleteCopiesMessage(selectedCount, readableSize(selectionBytes))
            },
            buttons = listOf(
                DialogButton(strings.cancel, DialogButtonStyle.Cancel) { confirmDelete = false },
                DialogButton(strings.delete, DialogButtonStyle.Destructive) {
                    confirmDelete = false
                    onDelete()
                }
            )
        )
    }
}

@Composable
private fun ScanningState(progress: DuplicateProgress) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(strings.duplicatesScanning, color = palette.onBackground, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        ThemedProgressBar(
            progress = if (progress.total > 0) progress.checked.toFloat() / progress.total else 0f,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = strings.duplicatesScanProgress(progress.checked, progress.total),
            color = palette.textMuted,
            fontSize = 14.sp
        )
    }
}

/** One set: its size and copy count, then the copies themselves as tickable thumbnails. */
@Composable
private fun DuplicateGroupRow(
    group: DuplicateGroup,
    selection: Set<Long>,
    onToggle: (Long) -> Unit
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val first = group.items.first()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.duplicateCopies(group.items.size),
                color = palette.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${readableSize(first.size)} · ${strings.duplicateWastes(readableSize(group.wastedBytes))}",
                color = palette.textMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            group.items.take(4).forEach { item ->
                DuplicateCopy(
                    item = item,
                    ticked = item.id in selection,
                    onClick = { onToggle(item.id) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Only four fit across; a set with more says how many did not.
            if (group.items.size > 4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(theme.shapes.cardCornerRadius))
                        .background(palette.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+${group.items.size - 4}", color = palette.textMuted, fontSize = 15.sp)
                }
            }
            repeat((4 - group.items.size).coerceAtLeast(0)) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = first.relativePath.trimEnd('/').ifBlank { first.displayName },
            color = palette.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DuplicateCopy(
    item: com.example.amoledgallery.model.MediaItem,
    ticked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = RoundedCornerShape(theme.shapes.cardCornerRadius)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(palette.surface)
            .clickable(onClick = onClick)
            .clipToBounds()
    ) {
        AsyncImage(
            model = remember(item.id) { thumbnailRequest(context, item) },
            contentDescription = item.displayName,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // A ticked copy is the one that goes; it is dimmed so the kept one stands out.
        if (ticked) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (ticked) palette.destructive else Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (ticked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = palette.onAccent,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        if (!ticked) {
            Text(
                text = strings.duplicateKept,
                color = palette.onBackground,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
