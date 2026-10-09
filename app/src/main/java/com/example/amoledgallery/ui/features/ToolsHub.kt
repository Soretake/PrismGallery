package com.example.amoledgallery.ui.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.amoledgallery.ui.design.ListRow
import com.example.amoledgallery.ui.design.ThemedListSection
import com.example.amoledgallery.ui.i18n.F

/** Everything 1.2 added, in one place: reached from the menu of every design. */
@Composable
fun ToolsHubScreen() {
    FeaturePage(title = F.tools) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = featureTopPadding(), bottom = featureBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ThemedListSection(
                header = F.findSection,
                rows = listOf(
                    ListRow(F.search, icon = Icons.Rounded.Search, iconColor = Color(0xFF3E7BFA), onClick = { Features.open(FeatureScreen.Search) }),
                    ListRow(F.places, icon = Icons.Rounded.Map, iconColor = Color(0xFF34A853), onClick = { Features.open(FeatureScreen.Places) }),
                    ListRow(F.onThisDay, icon = Icons.Rounded.History, iconColor = Color(0xFFF4B400), onClick = { Features.open(FeatureScreen.OnThisDay) }),
                    ListRow(F.privateAlbum, subtitle = F.privateSubtitle, icon = Icons.Rounded.Lock, iconColor = Color(0xFF7B61F0), onClick = { Features.open(FeatureScreen.PrivateAlbum) }),
                    ListRow(F.otherAlbums, icon = Icons.Rounded.Collections, iconColor = Color(0xFF8E8E93), onClick = { Features.open(FeatureScreen.OtherAlbums) })
                )
            )
            ThemedListSection(
                header = F.cleanSection,
                rows = listOf(
                    ListRow(F.similarPhotos, subtitle = F.similarSubtitle, icon = Icons.Rounded.BurstMode, iconColor = Color(0xFF00A9A5), onClick = { Features.open(FeatureScreen.Similar) }),
                    ListRow(F.cleanUp, subtitle = F.cleanUpSubtitle, icon = Icons.Rounded.CleaningServices, iconColor = Color(0xFFF4731F), onClick = { Features.open(FeatureScreen.CleanUp) })
                )
            )
            ThemedListSection(
                header = F.createSection,
                rows = listOf(
                    ListRow(F.collage, subtitle = F.collageSubtitle, icon = Icons.Rounded.AutoAwesomeMosaic, iconColor = Color(0xFFE8546A), onClick = { Features.open(FeatureScreen.Create(CreateKind.COLLAGE)) }),
                    ListRow(F.gif, subtitle = F.gifSubtitle, icon = Icons.Rounded.Gif, iconColor = Color(0xFF9C27B0), onClick = { Features.open(FeatureScreen.Create(CreateKind.GIF)) }),
                    ListRow(F.pdf, subtitle = F.pdfSubtitle, icon = Icons.Rounded.PictureAsPdf, iconColor = Color(0xFFF14438), onClick = { Features.open(FeatureScreen.Create(CreateKind.PDF)) })
                )
            )
            ThemedListSection(
                header = F.personalizeSection,
                rows = listOf(
                    ListRow(F.slideshowWallpaper, subtitle = F.slideshowWallpaperSubtitle, icon = Icons.Rounded.Slideshow, iconColor = Color(0xFF4CC34F), onClick = { Features.open(FeatureScreen.SlideshowWallpaper) }),
                    ListRow(F.photoWidget, subtitle = F.photoWidgetSubtitle, icon = Icons.Rounded.Widgets, iconColor = Color(0xFF5E8BFF), onClick = { Features.show(FeatureDialog.PhotoWidgetHelp) })
                )
            )
        }
    }
}
