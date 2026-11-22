package com.example.amoledgallery.ui.screens

import com.example.amoledgallery.ui.i18n.strings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.MediaGridItem
import com.example.amoledgallery.ui.design.DialogButton
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.GeminiIcon
import com.example.amoledgallery.ui.design.HazeSource
import com.example.amoledgallery.ui.design.ThemedChipAction
import com.example.amoledgallery.ui.design.ThemedDialog
import com.example.amoledgallery.ui.design.ThemedEmptyState
import com.example.amoledgallery.ui.design.ThemedIconButton
import com.example.amoledgallery.ui.design.ThemedTopBar
import com.example.amoledgallery.ui.design.pressScale
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.destructive
import com.example.amoledgallery.ui.theme.onAccent
import com.example.amoledgallery.util.BiometricAuthHelper
import dev.chrisbanes.haze.HazeState

/**
 * MediaStore trash. Tapping an item opens it in the fullscreen viewer (in its reduced trash mode, with
 * restore and delete permanently); everything can be restored or deleted at once through the top bar.
 * When locked, requires the system default lock (PIN/pattern/biometric) to view.
 */
@Composable
fun TrashScreen(
    trashedItems: List<MediaItem>,
    isLocked: Boolean = false,
    onUnlockRequest: () -> Unit = {},
    onLockTrash: () -> Unit = {},
    onBack: () -> Unit,
    onItemClick: (MediaItem, Int) -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEmptyConfirmDialog by remember { mutableStateOf(false) }
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val compactActions = !isLocked && trashedItems.isNotEmpty() &&
        (theme.language == DesignLanguage.GOOGLE_EXPRESSIVE || theme.language == DesignLanguage.IOS_LIQUID_GLASS)
    val trashHaze = remember { HazeState() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Prompt for authentication automatically only once when entering Trash in locked state
    var hasPromptedOnEntry by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (isLocked && !hasPromptedOnEntry) {
            hasPromptedOnEntry = true
            onUnlockRequest()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLocked) {
            val context = LocalContext.current
            val isSecure = remember(context) { BiometricAuthHelper.isDeviceSecure(context) }
            TrashLockedState(
                onUnlockRequest = onUnlockRequest,
                onOpenSecuritySettings = if (!isSecure) { { BiometricAuthHelper.openSecuritySettings(context) } } else null
            )
        } else if (trashedItems.isEmpty()) {
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
                        top = statusBarTop + if (compactActions) 112.dp else 64.dp,
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
            subtitle = when {
                isLocked -> strings.trashLocked
                trashedItems.isEmpty() -> null
                else -> strings.items(trashedItems.size)
            },
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
            if (!isLocked) {
                if (trashedItems.isNotEmpty() && !compactActions) {
                    TopBarTextAction(strings.restore, palette.accent, onRestoreAll)
                    TopBarTextAction(strings.emptyAction, palette.destructive) { showEmptyConfirmDialog = true }
                }
                ThemedIconButton(
                    icon = Icons.Rounded.Lock,
                    contentDescription = strings.lockTrashAction,
                    onClick = onLockTrash,
                    hazeState = trashHaze
                )
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

/**
 * Screen state displayed when Trash is locked. Shows a prominent lock icon, description,
 * and an Unlock button to trigger the system default biometric / screen lock prompt.
 */
@Composable
private fun TrashLockedState(
    onUnlockRequest: () -> Unit,
    onOpenSecuritySettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (theme.language) {
            DesignLanguage.GOOGLE_EXPRESSIVE -> GeminiIcon(
                Icons.Rounded.Lock,
                Modifier.size(84.dp)
            )
            DesignLanguage.XIAOMI_HYPEROS -> Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = palette.accent, modifier = Modifier.size(44.dp))
            }
            DesignLanguage.IOS_LIQUID_GLASS -> Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = palette.accent, modifier = Modifier.size(42.dp))
            }
            else -> Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = palette.accent, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = strings.trashLockedTitle,
            color = palette.onBackground,
            fontSize = if (theme.language == DesignLanguage.IOS_LIQUID_GLASS) 22.sp else 20.sp,
            fontWeight = if (theme.language == DesignLanguage.IOS_LIQUID_GLASS) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = strings.trashLockedDescription,
            color = palette.textMuted,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        // Unlock action button with smooth interactive feedback
        val shape = CircleShape
        Box(
            modifier = Modifier
                .pressScale(interaction, 0.94f)
                .height(46.dp)
                .clip(shape)
                .background(palette.accent)
                .clickable(interactionSource = interaction, indication = null, onClick = onUnlockRequest)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = palette.onAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = strings.unlockTrashAction,
                    color = palette.onAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (onOpenSecuritySettings != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = strings.deviceNotSecured,
                color = palette.textMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemedChipAction(
                text = strings.openSecuritySettings,
                color = palette.accent,
                onClick = onOpenSecuritySettings
            )
        }
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
