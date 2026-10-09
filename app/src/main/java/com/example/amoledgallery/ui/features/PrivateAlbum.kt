package com.example.amoledgallery.ui.features

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import com.example.amoledgallery.ui.components.MediaGridItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.ui.components.openWithLocation
import com.example.amoledgallery.ui.design.DialogButtonStyle
import com.example.amoledgallery.ui.design.ThemedButton
import com.example.amoledgallery.ui.design.ThemedEmptyState
import com.example.amoledgallery.ui.design.ThemedTextField
import com.example.amoledgallery.ui.i18n.F
import com.example.amoledgallery.ui.i18n.strings
import com.example.amoledgallery.ui.screens.MediaDetailScreen
import com.example.amoledgallery.ui.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The private album: files moved into the app's own storage (no other app, and no media scanner, can see
 * them there), shown only after the PIN or a fingerprint. Excluded from backups (see backup_rules.xml).
 */
object PrivateVault {
    private const val PREFS = "prism_private_album"

    fun dir(context: Context): File = File(context.filesDir, "private").apply { mkdirs() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPin(context: Context) = prefs(context).contains("pin_hash")

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString("pin_salt", salt).putString("pin_hash", hash(salt, pin)).apply()
    }

    fun clearPin(context: Context) {
        prefs(context).edit().remove("pin_hash").remove("pin_salt").apply()
    }

    fun checkPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString("pin_salt", null) ?: return false
        return p.getString("pin_hash", null) == hash(salt, pin)
    }

    /** The private files as gallery items (content uris of this app's FileProvider), newest first. */
    fun items(context: Context): List<MediaItem> =
        dir(context).listFiles().orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") }
            .sortedByDescending { it.lastModified() }
            .mapIndexed { index, file ->
                val mime = mimeOf(file.name)
                MediaItem(
                    // Negative ids never collide with MediaStore's.
                    id = -1L - index,
                    uri = MediaOps.providerUri(context, file),
                    isVideo = mime.startsWith("video/"),
                    dateAdded = file.lastModified() / 1000,
                    dateModified = file.lastModified() / 1000,
                    displayName = file.name,
                    mimeType = mime,
                    dataPath = file.absolutePath,
                    size = file.length()
                )
            }

    private fun mimeOf(name: String): String =
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    /** Copies [items] in (keeping their location data and date); returns the ones that were copied. */
    fun importAll(context: Context, items: List<MediaItem>): List<MediaItem> {
        val target = dir(context)
        return items.filter { item ->
            runCatching {
                var file = File(target, item.displayName.ifBlank { "media_${item.id}" })
                var n = 1
                while (file.exists()) {
                    file = File(target, item.displayName.substringBeforeLast('.') + " ($n)." + item.displayName.substringAfterLast('.', "jpg"))
                    n++
                }
                (openWithLocation(context, item.uri) ?: error("no input")).use { input -> file.outputStream().use { input.copyTo(it) } }
                file.setLastModified(item.date * 1000L)
                true
            }.getOrDefault(false)
        }
    }

    /** Moves [items] back into Pictures/Restored (videos: Movies/Restored) and deletes them here. */
    fun restoreAll(context: Context, items: List<MediaItem>): Int = items.count { item ->
        val file = File(item.dataPath)
        val folder = if (item.isVideo) "${Environment.DIRECTORY_MOVIES}/Restored" else "${Environment.DIRECTORY_PICTURES}/Restored"
        val uri = MediaOps.saveNew(context, file.name, item.mimeType, folder) { out -> file.inputStream().use { it.copyTo(out) } }
        if (uri != null) file.delete()
        uri != null
    }
}

@Composable
fun PrivateAlbumScreen() {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    // Changing the PIN: the old one stays valid until a new one has been entered twice.
    var changingPin by remember { mutableStateOf(false) }
    if (!unlocked || changingPin) {
        androidx.compose.runtime.key(changingPin) {
            PinGate(newPin = changingPin, onUnlocked = { unlocked = true; changingPin = false })
        }
        if (changingPin) androidx.activity.compose.BackHandler { changingPin = false }
    } else {
        PrivateContent(onChangePin = { changingPin = true })
    }
}

/** Sets the PIN on first use; afterwards asks for it (or a fingerprint). */
@Composable
private fun PinGate(newPin: Boolean = false, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    val creating = remember { newPin || !PrivateVault.hasPin(context) }
    // The phone's screen lock or fingerprint also opens it (and is how a forgotten PIN is replaced).
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorCount by remember { mutableIntStateOf(0) }
    val biometrics = remember { !creating && canUseBiometrics(context) }

    fun submit() {
        if (pin.length !in 4..8) { error = F.pinHint; return }
        if (creating) {
            val f = first
            if (f == null) { first = pin; pin = ""; error = null }
            else if (f == pin) { PrivateVault.setPin(context, pin); onUnlocked() }
            else { first = null; pin = ""; error = F.pinsDiffer; errorCount++ }
        } else if (PrivateVault.checkPin(context, pin)) {
            onUnlocked()
        } else {
            pin = ""; error = F.wrongPin; errorCount++
        }
    }
    LaunchedEffect(Unit) { if (biometrics) authenticate(context, onUnlocked) }

    FeaturePage(title = F.privateAlbum) {
        // Scrolls, so the keypad also fits in landscape.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 40.dp)
                .padding(top = featureTopPadding(), bottom = featureBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PinScreen(
                title = when {
                    !creating -> F.enterPin
                    first == null -> F.setPin
                    else -> F.repeatPin
                },
                message = error ?: F.pinHint,
                isError = error != null,
                errorCount = errorCount,
                length = pin.length,
                onDigit = { if (pin.length < 8) { pin += it; error = null } },
                onDelete = { pin = pin.dropLast(1) },
                onOk = { submit() }
            )
            if (biometrics) {
                Spacer(Modifier.height(12.dp))
                ThemedButton(F.unlockWithBiometrics, onClick = { authenticate(context, onUnlocked) }, style = DialogButtonStyle.Default, icon = Icons.Rounded.Fingerprint)
            }
            if (creating) {
                Spacer(Modifier.height(18.dp))
                Text(F.privateWarning, color = palette.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Fingerprint/face, or the phone's own screen lock: either may open the album instead of the PIN. */
private const val DEVICE_AUTH = android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL

private fun canUseBiometrics(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false
    val manager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java) ?: return false
    return manager.canAuthenticate(DEVICE_AUTH) == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
}

private fun authenticate(context: Context, onSuccess: () -> Unit) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
    val executor = context.mainExecutor
    val prompt = android.hardware.biometrics.BiometricPrompt.Builder(context)
        .setTitle(F.privateAlbum)
        // The screen lock counts too, so a forgotten album PIN never locks the photos away for good.
        .setAllowedAuthenticators(DEVICE_AUTH)
        .build()
    prompt.authenticate(android.os.CancellationSignal(), executor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) = onSuccess()
    })
}

@Composable
private fun PrivateContent(onChangePin: () -> Unit) {
    val context = LocalContext.current
    val env = LocalFeatureEnv.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val items = remember(version) { PrivateVault.items(context) }
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    var openIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf<List<MediaItem>?>(null) }
    val theme = LocalAppTheme.current
    if (selection != null) androidx.activity.compose.BackHandler { selection = null }

    FeaturePage(
        title = F.privateAlbum,
        subtitle = if (selection != null) F.selectedCount(selection!!.size) else strings.items(items.size),
        actions = {
            com.example.amoledgallery.ui.design.ThemedIconButton(Icons.Rounded.Password, F.changePin, onClick = onChangePin)
            if (items.isNotEmpty()) {
                com.example.amoledgallery.ui.design.ThemedIconButton(
                    Icons.Rounded.CheckCircle,
                    strings.select,
                    onClick = { selection = if (selection == null) emptySet() else null }
                )
            }
        },
        bottomBar = if (selection != null) {
            {
                val chosen = items.filter { it.id in selection.orEmpty() }
                com.example.amoledgallery.ui.components.ThemedActionBar(
                    selectedCount = chosen.size,
                    actions = listOf(
                        com.example.amoledgallery.ui.components.BarAction(Icons.Outlined.Share, strings.share) {
                            sharePrivate(context, chosen)
                        },
                        com.example.amoledgallery.ui.components.BarAction(Icons.Outlined.Unarchive, F.restoreShort) {
                            selection = null
                            scope.launch {
                                val count = withContext(Dispatchers.IO) { PrivateVault.restoreAll(context, chosen) }
                                env.vm.message(F.restoredFromPrivate(count))
                                version++
                                env.vm.refreshAfterEdit()
                            }
                        },
                        com.example.amoledgallery.ui.components.BarAction(Icons.Outlined.Delete, strings.delete) {
                            confirmDelete = chosen
                        }
                    )
                )
            }
        } else null
    ) {
        if (items.isEmpty()) {
            ThemedEmptyState(Icons.Rounded.Lock, F.privateEmptyTitle, F.privateEmptyMessage)
        } else {
            LazyVerticalGrid(
                // More, smaller tiles on wide screens, as on the other tool pages.
                columns = GridCells.Fixed(maxOf(4, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 110)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = theme.shapes.gridHorizontalPadding,
                    end = theme.shapes.gridHorizontalPadding,
                    top = featureTopPadding(),
                    bottom = featureBottomPadding(96.dp)
                ),
                horizontalArrangement = Arrangement.spacedBy(theme.shapes.gridSpacing),
                verticalArrangement = Arrangement.spacedBy(theme.shapes.gridSpacing)
            ) {
                items(items, key = { it.dataPath }) { item ->
                    val selected = selection?.contains(item.id) == true
                    MediaGridItem(
                        // Id 0: the thumbnail is decoded from the file each time and never written to the
                        // thumbnail cache, which other photos' ids could later reuse.
                        item = item.copy(id = 0L),
                        isSelectionMode = selection != null,
                        isSelected = selected,
                        onClick = {
                            val s = selection
                            if (s != null) selection = if (selected) s - item.id else s + item.id
                            else openIndex = items.indexOf(item)
                        },
                        modifier = Modifier.aspectRatio(1f).clipToBounds()
                    )
                }
            }
        }
    }

    confirmDelete?.let { doomed ->
        com.example.amoledgallery.ui.design.ThemedDialog(
            onDismissRequest = { confirmDelete = null },
            title = F.deletePrivateTitle(doomed.size),
            message = F.deletePrivateMessage,
            buttons = listOf(
                com.example.amoledgallery.ui.design.DialogButton(strings.cancel, com.example.amoledgallery.ui.design.DialogButtonStyle.Cancel) { confirmDelete = null },
                com.example.amoledgallery.ui.design.DialogButton(strings.delete, com.example.amoledgallery.ui.design.DialogButtonStyle.Destructive) {
                    confirmDelete = null
                    selection = null
                    doomed.forEach { File(it.dataPath).delete() }
                    version++
                }
            )
        )
    }

    val holder = remember { IntArray(1) }
    openIndex?.let { holder[0] = it }
    if (openIndex != null && items.isNotEmpty()) {
        androidx.activity.compose.BackHandler { openIndex = null }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            MediaDetailScreen(
                mediaList = items,
                initialIndex = holder[0].coerceIn(0, items.lastIndex),
                onBack = { openIndex = null },
                onTrashItem = {},
                onRenameItem = { _, _ -> },
                viewOnly = true,
                allowRename = false
            )
        }
    }
}

/** Moves [items] into the private album: copied in first, the originals deleted once Android allows it. */
fun moveToPrivate(context: Context, env: FeatureEnv, items: List<MediaItem>, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        val copied = withContext(Dispatchers.IO) { PrivateVault.importAll(context, items) }
        if (copied.isEmpty()) {
            env.vm.message(F.failed)
            return@launch
        }
        env.vm.deleteItemsForGood(
            copied,
            onDone = {
                // The moved photos are gone from the gallery, so the selection that held them ends too.
                env.vm.clearSelection()
                env.vm.message(F.movedToPrivate(copied.size))
            },
            onCancelled = {
                // The originals stay: the copies would only be doubles, so they go again.
                copied.forEach { item ->
                    PrivateVault.dir(context).listFiles()?.firstOrNull { it.name == item.displayName }?.delete()
                }
            }
        )
    }
}

/** Shares private photos through the app's own file provider, readable only by the app that receives them. */
private fun sharePrivate(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val uris = ArrayList(items.map { it.uri })
    val mime = items.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
    val intent = if (uris.size == 1) {
        android.content.Intent(android.content.Intent.ACTION_SEND).putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
    } else {
        android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
    }
        .setType(mime)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = android.content.ClipData.newUri(context.contentResolver, "", uris[0]).apply {
        uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, null)) }
}
