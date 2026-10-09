package com.example.amoledgallery.ui.features

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.amoledgallery.model.AlbumItem
import com.example.amoledgallery.ui.design.DialogButton
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.ListRow
import com.example.amoledgallery.ui.design.ThemedDialog
import com.example.amoledgallery.ui.design.ThemedEmptyState
import com.example.amoledgallery.ui.design.ThemedListSection
import com.example.amoledgallery.ui.design.ThemedSheet
import com.example.amoledgallery.ui.design.ThemedTextField
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.theme.LocalAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Album organisation beyond folders: albums tucked away under "Other albums" (HyperOS), album groups
 * (One UI) and the list layout of the album tab. Kept by bucket id in the app's preferences.
 */
object AlbumExtras {
    private const val PREFS = "prism_album_extras"
    private var prefs: android.content.SharedPreferences? = null

    private val _otherIds = MutableStateFlow<Set<String>>(emptySet())
    val otherIds: StateFlow<Set<String>> = _otherIds.asStateFlow()

    /** Group name to the bucket ids in it, in the order they were added. */
    private val _groups = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val groups: StateFlow<Map<String, List<String>>> = _groups.asStateFlow()

    var listView by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _otherIds.value = p.getStringSet("other", emptySet()).orEmpty().toSet()
        _groups.value = p.getString("groups", null)?.lines()?.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.isEmpty() || parts[0].isBlank()) null else parts[0] to parts.drop(1).filter { it.isNotBlank() }
        }?.toMap().orEmpty()
        listView = p.getBoolean("list_view", false)
    }

    private fun saveGroups() {
        prefs?.edit()?.putString("groups", _groups.value.entries.joinToString("\n") { (name, ids) -> (listOf(name) + ids).joinToString("\t") })?.apply()
    }

    fun setOther(album: AlbumItem, other: Boolean) {
        _otherIds.value = if (other) _otherIds.value + album.bucketId else _otherIds.value - album.bucketId
        prefs?.edit()?.putStringSet("other", _otherIds.value)?.apply()
    }

    fun addToGroup(group: String, album: AlbumItem) {
        val name = group.trim().replace('\t', ' ').replace('\n', ' ')
        if (name.isBlank()) return
        // An album belongs to one group at a time.
        val cleaned = _groups.value.mapValues { (_, ids) -> ids - album.bucketId }
        _groups.value = cleaned + (name to ((cleaned[name] ?: emptyList()) + album.bucketId))
        saveGroups()
    }

    fun removeFromGroup(album: AlbumItem) {
        _groups.value = _groups.value.mapValues { (_, ids) -> ids - album.bucketId }.filterValues { it.isNotEmpty() }
        saveGroups()
    }

    fun deleteGroup(group: String) {
        _groups.value = _groups.value - group
        saveGroups()
    }

    fun toggleListView() {
        listView = !listView
        prefs?.edit()?.putBoolean("list_view", listView)?.apply()
    }

    /** Bucket ids that are not shown among the main albums (tucked away or in a group). */
    fun hiddenFromMain(other: Set<String>, groups: Map<String, List<String>>): Set<String> =
        other + groups.values.flatten()

    /** A tile standing for a group in the album list. */
    fun groupTile(name: String, albums: List<AlbumItem>): AlbumItem = AlbumItem(
        bucketId = "group:$name",
        name = name,
        count = albums.sumOf { it.count },
        coverUri = albums.firstOrNull()?.coverUri,
        subAlbumCount = albums.size
    )

    fun otherTile(albums: List<AlbumItem>): AlbumItem = AlbumItem(
        bucketId = "other_albums",
        name = F.otherAlbums,
        count = albums.sumOf { it.count },
        coverUri = albums.firstOrNull()?.coverUri,
        subAlbumCount = albums.size
    )

    const val ACTION_OPEN_ALBUM = "com.prismgallery.app.OPEN_ALBUM"
    const val EXTRA_BUCKET = "bucket_id"

    /** Asks the launcher to pin a shortcut that opens [album]. */
    fun pinShortcut(context: Context, album: AlbumItem): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        val intent = Intent(ACTION_OPEN_ALBUM)
            .setClassName(context, "com.example.amoledgallery.MainActivity")
            .putExtra(EXTRA_BUCKET, album.bucketId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val icon = runCatching {
            album.coverUri?.let { uri ->
                val bitmap = context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null)
                Icon.createWithAdaptiveBitmap(centerSquare(bitmap))
            }
        }.getOrNull() ?: Icon.createWithResource(context, com.example.amoledgallery.R.mipmap.ic_launcher)
        val info = ShortcutInfo.Builder(context, "album_${album.bucketId}")
            .setShortLabel(album.name.take(24))
            .setIcon(icon)
            .setIntent(intent)
            .build()
        return manager.requestPinShortcut(info, null)
    }

    private fun centerSquare(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val side = minOf(src.width, src.height)
        return android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    }
}

/** The albums tucked away under "Other albums". */
@Composable
fun OtherAlbumsScreen() {
    val env = LocalFeatureEnv.current
    val all by env.vm.albumsState.collectAsStateWithLifecycle()
    val otherIds by AlbumExtras.otherIds.collectAsStateWithLifecycle()
    val albums = all.filter { it.bucketId in otherIds }.sortedBy { it.name.lowercase() }
    AlbumListPage(F.otherAlbums, albums, emptyText = F.otherAlbumsEmpty, removeLabel = F.removeFromOtherAlbums) { AlbumExtras.setOther(it, false) }
}

/** The albums of one group. */
@Composable
fun AlbumGroupScreen(name: String) {
    val env = LocalFeatureEnv.current
    val all by env.vm.albumsState.collectAsStateWithLifecycle()
    val groups by AlbumExtras.groups.collectAsStateWithLifecycle()
    val ids = groups[name].orEmpty()
    val albums = ids.mapNotNull { id -> all.firstOrNull { it.bucketId == id } }
    var confirmDelete by remember { mutableStateOf(false) }
    AlbumListPage(
        name, albums,
        emptyText = F.albumsCount(0),
        removeLabel = F.removeFromGroup,
        extraRows = listOf(
            ListRow(F.deleteGroup, icon = Icons.Rounded.Delete, iconColor = Color(0xFFF14438), showChevron = false, onClick = { confirmDelete = true })
        )
    ) { AlbumExtras.removeFromGroup(it) }
    if (confirmDelete) {
        ThemedDialog(
            onDismissRequest = { confirmDelete = false },
            title = F.deleteGroup,
            message = name,
            buttons = listOf(
                DialogButton(strings.cancel, DialogButtonStyle.Cancel) { confirmDelete = false },
                DialogButton(strings.delete, DialogButtonStyle.Destructive) {
                    confirmDelete = false
                    AlbumExtras.deleteGroup(name)
                    Features.back()
                }
            )
        )
    }
}

@Composable
private fun AlbumListPage(
    title: String,
    albums: List<AlbumItem>,
    emptyText: String,
    removeLabel: String,
    extraRows: List<ListRow> = emptyList(),
    onRemove: (AlbumItem) -> Unit
) {
    val env = LocalFeatureEnv.current
    var actionsFor by remember { mutableStateOf<AlbumItem?>(null) }
    val accent = LocalAppTheme.current.palette.accent
    FeaturePage(title = title, subtitle = F.albumsCount(albums.size)) {
        if (albums.isEmpty() && extraRows.isEmpty()) {
            ThemedEmptyState(Icons.Rounded.Collections, title, emptyText)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = featureTopPadding(), bottom = featureBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (albums.isNotEmpty()) {
                    ThemedListSection(
                        rows = albums.map { album ->
                            ListRow(
                                album.name,
                                subtitle = strings.items(album.count),
                                icon = Icons.Rounded.Folder,
                                iconColor = accent,
                                onClick = {
                                    // Opens the album in the album tab, where everything else works as usual.
                                    Features.screens.clear()
                                    env.vm.selectTab(com.example.amoledgallery.ui.viewmodel.GalleryTab.ALBEN)
                                    env.vm.jumpToAlbum(album)
                                }
                            )
                        }
                    )
                    ThemedListSection(
                        header = removeLabel,
                        rows = albums.map { album ->
                            ListRow(album.name, icon = Icons.Rounded.RemoveCircleOutline, iconColor = Color(0xFF8E8E93), showChevron = false, onClick = { onRemove(album) })
                        }
                    )
                }
                if (extraRows.isNotEmpty()) ThemedListSection(rows = extraRows)
            }
        }
    }
}

/** Picks (or names) the group an album is added to. */
@Composable
fun AddToGroupSheet(album: AlbumItem, onDismiss: () -> Unit) {
    val groups by AlbumExtras.groups.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    val accent = LocalAppTheme.current.palette.accent
    ThemedSheet(onDismissRequest = onDismiss, title = F.addToGroup) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (groups.isNotEmpty()) {
                ThemedListSection(
                    inSheet = true,
                    rows = groups.keys.sorted().map { name ->
                        ListRow(name, subtitle = F.albumsCount(groups[name].orEmpty().size), icon = Icons.Rounded.Collections, iconColor = accent, showChevron = false, onClick = {
                            AlbumExtras.addToGroup(name, album)
                            onDismiss()
                        })
                    }
                )
            }
            ThemedTextField(value = newName, onValueChange = { newName = it }, placeholder = F.groupName)
            com.example.amoledgallery.ui.design.ThemedButton(F.newGroup, onClick = {
                if (newName.isNotBlank()) {
                    AlbumExtras.addToGroup(newName, album)
                    onDismiss()
                }
            }, enabled = newName.isNotBlank())
        }
    }
}
