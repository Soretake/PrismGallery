package com.example.amoledgallery.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amoledgallery.ui.design.BottomEdgeFade
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.theme.LocalAppTheme

/**
 * A tool page that is a menu (the tools overview, the clean-up categories...), built like the design's own
 * settings pages: One UI's huge centred title that scrolls away with the back chevron below it, HyperOS's back
 * arrow over a big thin title, Gemini's title with a close button, and iOS's glass bar with the centred title.
 */
@Composable
fun FeatureMenuPage(
    title: String,
    onBack: () -> Unit = { Features.back() },
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalAppTheme.current
    val palette = theme.palette
    if (theme.language == DesignLanguage.IOS_LIQUID_GLASS) {
        FeaturePage(title = title, onBack = onBack) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = featureTopPadding(), bottom = featureBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = content
            )
        }
        return
    }

    val scroll = rememberScrollState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val collapsePx = with(LocalDensity.current) { 180.dp.toPx() }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(bottom = featureBottomPadding())
        ) {
            when (theme.language) {
                DesignLanguage.SAMSUNG_ONEUI -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((screenHeight * 0.30f).coerceAtLeast(200.dp))
                            .graphicsLayer {
                                val f = (scroll.value / collapsePx).coerceIn(0f, 1f)
                                alpha = (1f - f * 1.4f).coerceIn(0f, 1f)
                                translationY = f * 60.dp.toPx()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = palette.onBackground,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.statusBarsPadding().padding(horizontal = 24.dp)
                        )
                    }
                    Box(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                        HeaderButton(onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, strings.back, tint = palette.onBackground, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                DesignLanguage.XIAOMI_HYPEROS -> Column(modifier = Modifier.statusBarsPadding()) {
                    Box(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                        HeaderButton(onBack, size = 48) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings.back, tint = palette.onBackground, modifier = Modifier.size(26.dp))
                        }
                    }
                    Text(
                        text = title,
                        color = palette.onBackground,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 46.sp,
                        maxLines = 2,
                        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 12.dp)
                    )
                }
                else -> Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .height(76.dp)
                        .padding(start = 28.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = palette.onBackground, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    HeaderButton(onBack) {
                        Icon(Icons.Rounded.Close, strings.back, tint = palette.onBackground, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (theme.language == DesignLanguage.SAMSUNG_ONEUI) 14.dp else 18.dp),
                content = content
            )
        }
        BottomEdgeFade(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun HeaderButton(onClick: () -> Unit, size: Int = 52, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
