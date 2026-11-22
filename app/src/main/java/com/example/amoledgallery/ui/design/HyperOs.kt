package com.example.amoledgallery.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent

/** HyperOS section headers ("More albums", "Organize photos") are a muted grey with a hint of blue. */
@Composable
@ReadOnlyComposable
fun hyperOsSectionColor(): Color {
    val palette = LocalAppTheme.current.palette
    return lerp(palette.textMuted, palette.accent, if (palette.isLight) 0.25f else 0.3f)
}

@Composable
fun HyperOsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = hyperOsSectionColor(),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun HyperOsDivider(modifier: Modifier = Modifier) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.onBackground.copy(alpha = if (palette.isLight) 0.08f else 0.10f))
    )
}

/** A HyperOS list row: a coloured rounded-square icon, the title, an optional value and a chevron. */
@Composable
fun HyperOsIconRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    trailingIcon: ImageVector? = null,
    horizontalPadding: Dp = 28.dp,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = ripple(color = palette.onBackground), onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = horizontalPadding, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .pressScale(interaction, 0.88f)
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            // A white (or very light) accent would swallow a white icon.
            Icon(icon, null, tint = contentOn(iconColor), modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            color = palette.onBackground,
            fontSize = 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(trailingIcon, null, tint = palette.textMuted, modifier = Modifier.size(18.dp))
        }
        if (value != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(value, color = palette.textMuted, fontSize = 16.sp, maxLines = 1)
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = palette.textMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** HyperOS Gallery's round accent "+" button floating above the tab bar. */
@Composable
fun HyperOsFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, 0.9f)
            .shadow(12.dp, CircleShape, ambientColor = palette.accent.copy(alpha = 0.5f), spotColor = palette.accent.copy(alpha = 0.5f))
            .size(64.dp)
            .clip(CircleShape)
            .background(palette.accent)
            .clickable(interactionSource = interaction, indication = ripple(color = palette.onAccent), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = palette.onAccent, modifier = Modifier.size(34.dp))
    }
}

/** Lets a full-width header inside a padded list reach the screen edges again. */
fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val width = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}
