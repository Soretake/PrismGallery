package com.example.amoledgallery.ui.features

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.ui.design.GeminiIcon
import com.example.amoledgallery.ui.design.pressScale
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme
import com.example.amoledgallery.ui.theme.isLight
import com.example.amoledgallery.ui.theme.onAccent

private val ERROR_RED = Color(0xFFF14438)

/** The letters printed under the digits on phone keypads (One UI and iOS show them). */
private fun lettersOf(digit: String): String = when (digit) {
    "2" -> "ABC"; "3" -> "DEF"; "4" -> "GHI"; "5" -> "JKL"; "6" -> "MNO"
    "7" -> "PQRS"; "8" -> "TUV"; "9" -> "WXYZ"; "0" -> "+"; else -> ""
}

/**
 * The PIN entry of the private album, drawn like the lock screen of the chosen design: One UI's keypad with
 * plain digits over an underlined field, iOS's passcode screen with round glass keys and hollow dots, HyperOS's
 * thin digits in grey circles, and the Pixel keypad of Gemini with tonal pill keys and a filled enter key.
 * The dots shake when [errorCount] goes up (a wrong PIN).
 */
@Composable
fun PinScreen(
    title: String,
    message: String,
    isError: Boolean,
    errorCount: Int,
    length: Int,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorCount) {
        if (errorCount > 0) {
            for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(45))
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (language) {
            DesignLanguage.GOOGLE_EXPRESSIVE -> GeminiIcon(Icons.Rounded.Lock, Modifier.size(44.dp))
            DesignLanguage.IOS_LIQUID_GLASS -> Icon(Icons.Rounded.Lock, null, tint = palette.onBackground, modifier = Modifier.size(26.dp))
            else -> Icon(Icons.Rounded.Lock, null, tint = palette.accent, modifier = Modifier.size(if (language == DesignLanguage.SAMSUNG_ONEUI) 40.dp else 34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            color = palette.onBackground,
            fontSize = when (language) {
                DesignLanguage.XIAOMI_HYPEROS -> 26.sp
                DesignLanguage.IOS_LIQUID_GLASS -> 20.sp
                else -> 22.sp
            },
            fontWeight = if (language == DesignLanguage.XIAOMI_HYPEROS) FontWeight.Light else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(message, color = if (isError) ERROR_RED else palette.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Box(modifier = Modifier.graphicsLayer { translationX = shake.value.dp.toPx() }) {
            PinDots(length, isError)
        }
        Spacer(Modifier.height(if (language == DesignLanguage.SAMSUNG_ONEUI) 26.dp else 30.dp))
        PinKeypad(onDigit, onDelete, onOk)
    }
}

/** How many digits have been typed, in the design's way. */
@Composable
private fun PinDots(length: Int, isError: Boolean) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    when (theme.language) {
        // One UI: the dots in a field with an accent underline.
        DesignLanguage.SAMSUNG_ONEUI -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "●".repeat(length).ifEmpty { " " },
                color = palette.onBackground,
                fontSize = 18.sp,
                letterSpacing = 8.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(2.dp)
                    .background(if (isError) ERROR_RED else palette.accent)
            )
        }
        // Gemini: large accent dots that pop in.
        DesignLanguage.GOOGLE_EXPRESSIVE -> Row(
            modifier = Modifier.height(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(length) {
                val pop = remember { Animatable(0f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                val scale = pop.value
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(if (isError) ERROR_RED else palette.accent)
                )
            }
        }
        // iOS and HyperOS: a row of hollow circles that fill up (at least four).
        else -> Row(
            modifier = Modifier.height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (theme.language == DesignLanguage.IOS_LIQUID_GLASS) 22.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val color = if (isError) ERROR_RED else palette.onBackground
            repeat(maxOf(4, length)) { i ->
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .then(if (i < length) Modifier.background(color) else Modifier.border(1.5.dp, color, CircleShape))
                )
            }
        }
    }
}

@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onDelete: () -> Unit, onOk: () -> Unit) {
    val theme = LocalAppTheme.current
    val language = theme.language
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    val gap = when (language) {
        DesignLanguage.SAMSUNG_ONEUI -> 0.dp
        DesignLanguage.GOOGLE_EXPRESSIVE -> 10.dp
        DesignLanguage.IOS_LIQUID_GLASS -> 16.dp
        DesignLanguage.XIAOMI_HYPEROS -> 14.dp
    }
    val hGap = when (language) {
        DesignLanguage.SAMSUNG_ONEUI -> 0.dp
        DesignLanguage.GOOGLE_EXPRESSIVE -> 12.dp
        else -> 26.dp
    }
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(hGap)) {
                row.forEach { DigitKey(it) { onDigit(it) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(hGap)) {
            when (language) {
                // iOS: "OK" and "Löschen" as plain text beside the 0.
                DesignLanguage.IOS_LIQUID_GLASS -> {
                    TextKey("OK", onOk)
                    DigitKey("0") { onDigit("0") }
                    TextKey(F.deleteKey, onDelete)
                }
                DesignLanguage.SAMSUNG_ONEUI -> {
                    IconKey(onDelete, filled = false) { tint -> Icon(Icons.AutoMirrored.Outlined.Backspace, F.deleteKey, tint = tint, modifier = Modifier.size(26.dp)) }
                    DigitKey("0") { onDigit("0") }
                    TextKey("OK", onOk, accent = true)
                }
                else -> {
                    IconKey(onDelete, filled = language == DesignLanguage.GOOGLE_EXPRESSIVE) { tint ->
                        Icon(Icons.AutoMirrored.Outlined.Backspace, F.deleteKey, tint = tint, modifier = Modifier.size(26.dp))
                    }
                    DigitKey("0") { onDigit("0") }
                    IconKey(onOk, filled = true, accent = true) { tint ->
                        Icon(
                            if (language == DesignLanguage.GOOGLE_EXPRESSIVE) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Check,
                            "OK", tint = tint, modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Size and shape of one key in the design. */
private data class KeySpec(val width: Dp, val height: Dp, val shape: androidx.compose.ui.graphics.Shape)

@Composable
private fun keySpec(): KeySpec = when (LocalAppTheme.current.language) {
    DesignLanguage.SAMSUNG_ONEUI -> KeySpec(104.dp, 76.dp, RoundedCornerShape(20.dp))
    DesignLanguage.GOOGLE_EXPRESSIVE -> KeySpec(92.dp, 68.dp, RoundedCornerShape(34.dp))
    DesignLanguage.IOS_LIQUID_GLASS -> KeySpec(78.dp, 78.dp, CircleShape)
    DesignLanguage.XIAOMI_HYPEROS -> KeySpec(74.dp, 74.dp, CircleShape)
}

/** The key's fill: none on One UI, glass on iOS, grey on HyperOS, tonal on Gemini. */
@Composable
private fun keyFill(): Color {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val light = palette.isLight
    return when (theme.language) {
        DesignLanguage.SAMSUNG_ONEUI -> Color.Transparent
        DesignLanguage.IOS_LIQUID_GLASS -> if (light) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.15f)
        DesignLanguage.XIAOMI_HYPEROS -> palette.surfaceVariant
        DesignLanguage.GOOGLE_EXPRESSIVE -> lerp(palette.surfaceVariant, palette.accent, if (light) 0.10f else 0.08f)
    }
}

@Composable
private fun KeyBox(onClick: () -> Unit, fill: Color, content: @Composable () -> Unit) {
    val spec = keySpec()
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(spec.width, spec.height)
            .pressScale(interaction, 0.92f)
            .clip(spec.shape)
            .background(fill)
            .clickable(interactionSource = interaction, indication = ripple(color = palette.onBackground), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun DigitKey(digit: String, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    val language = theme.language
    KeyBox(onClick, keyFill()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                digit,
                color = palette.onBackground,
                fontSize = when (language) {
                    DesignLanguage.IOS_LIQUID_GLASS -> 34.sp
                    DesignLanguage.SAMSUNG_ONEUI -> 30.sp
                    else -> 28.sp
                },
                fontWeight = if (language == DesignLanguage.XIAOMI_HYPEROS || language == DesignLanguage.IOS_LIQUID_GLASS) FontWeight.Light else FontWeight.Normal,
                lineHeight = 34.sp
            )
            // One UI and iOS print the letters under the digit.
            if (language == DesignLanguage.SAMSUNG_ONEUI || language == DesignLanguage.IOS_LIQUID_GLASS) {
                Text(
                    lettersOf(digit).ifEmpty { " " },
                    color = if (language == DesignLanguage.IOS_LIQUID_GLASS) palette.onBackground else palette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
private fun TextKey(text: String, onClick: () -> Unit, accent: Boolean = false) {
    val palette = LocalAppTheme.current.palette
    KeyBox(onClick, Color.Transparent) {
        Text(
            text,
            color = if (accent) palette.accent else palette.onBackground,
            fontSize = if (accent) 19.sp else 17.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun IconKey(onClick: () -> Unit, filled: Boolean, accent: Boolean = false, icon: @Composable (Color) -> Unit) {
    val palette = LocalAppTheme.current.palette
    val fill = when {
        accent && filled -> palette.accent
        filled -> keyFill()
        else -> Color.Transparent
    }
    KeyBox(onClick, fill) { icon(if (accent && filled) palette.onAccent else palette.onBackground) }
}
