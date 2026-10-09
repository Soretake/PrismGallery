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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
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
    if (!unlocked) {
        PinGate(onUnlocked = { unlocked = true })
    } else {
        PrivateContent()
    }
}

/** Sets the PIN on first use; afterwards asks for it (or a fingerprint). */
@Composable
private fun PinGate(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    val creating = remember { !PrivateVault.hasPin(context) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val biometrics = remember { !creating && canUseBiometrics(context) }

    fun submit() {
        if (pin.length !in 4..8) { error = F.pinHint; return }
        if (creating) {
            val f = first
            if (f == null) { first = pin; pin = ""; error = null }
            else if (f == pin) { PrivateVault.setPin(context, pin); onUnlocked() }
            else { first = null; pin = ""; error = F.pinsDiffer }
        } else if (PrivateVault.checkPin(context, pin)) {
            onUnlocked()
        } else {
            pin = ""; error = F.wrongPin
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
            Icon(Icons.Rounded.Lock, null, tint = palette.accent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                when {
                    !creating -> F.enterPin
                    first == null -> F.setPin
                    else -> F.repeatPin
                },
                color = palette.onBackground, fontSize = 22.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(error ?: F.pinHint, color = if (error != null) Color(0xFFF14438) else palette.textMuted, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Text(
                "●".repeat(pin.length).ifEmpty { " " },
                color = palette.onBackground, fontSize = 26.sp, letterSpacing = 6.sp
            )
            Spacer(Modifier.height(18.dp))
            PinPad(
                onDigit = { if (pin.length < 8) pin += it },
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

@Composable
private fun PinPad(onDigit: (String) -> Unit, onDelete: () -> Unit, onOk: () -> Unit) {
    val palette = LocalAppTheme.current.palette
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceVariant)
                            .clickable {
                                when (key) {
                                    "⌫" -> onDelete()
                                    "OK" -> onOk()
                                    else -> onDigit(key)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(key, color = if (key == "OK") palette.accent else palette.onBackground, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

private fun canUseBiometrics(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false
    val manager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java) ?: return false
    return manager.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
}

private fun authenticate(context: Context, onSuccess: () -> Unit) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
    val executor = context.mainExecutor
    val prompt = android.hardware.biometrics.BiometricPrompt.Builder(context)
        .setTitle(F.privateAlbum)
        .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .setNegativeButton(F.enterPin, executor) { _, _ -> }
        .build()
    prompt.authenticate(android.os.CancellationSignal(), executor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) = onSuccess()
    })
}

@Composable
private fun PrivateContent() {
    val context = LocalContext.current
    val env = LocalFeatureEnv.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val items = remember(version) { PrivateVault.items(context) }
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    var openIndex by remember { mutableStateOf<Int?>(null) }
    val theme = LocalAppTheme.current

    FeaturePage(
        title = F.privateAlbum,
        subtitle = if (selection != null) F.selectedCount(selection!!.size) else strings.items(items.size),
        actions = {
            if (items.isNotEmpty()) {
                com.example.amoledgallery.ui.design.ThemedIconButton(
                    Icons.Rounded.CheckCircle,
                    strings.select,
                    onClick = { selection = if (selection == null) emptySet() else null }
                )
            }
        },
        bottomBar = if (!selection.isNullOrEmpty()) {
            {
                Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    ThemedButton(F.restoreFromPrivate, modifier = Modifier.fillMaxWidth(), onClick = {
                        val chosen = items.filter { it.id in selection!! }
                        selection = null
                        scope.launch {
                            val count = withContext(Dispatchers.IO) { PrivateVault.restoreAll(context, chosen) }
                            env.vm.message(F.restoredFromPrivate(count))
                            version++
                            env.vm.refreshAfterEdit()
                        }
                    })
                }
            }
        } else null
    ) {
        if (items.isEmpty()) {
            ThemedEmptyState(Icons.Rounded.Lock, F.privateEmptyTitle, F.privateEmptyMessage)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
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
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(theme.shapes.gridSpacing))
                            .clickable {
                                val s = selection
                                if (s != null) selection = if (selected) s - item.id else s + item.id
                                else openIndex = items.indexOf(item)
                            }
                    ) {
                        AsyncImage(
                            model = File(item.dataPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (item.isVideo) {
                            Icon(Icons.Rounded.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(30.dp))
                        }
                        if (selection != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (selected) Color.Black.copy(alpha = 0.35f) else Color.Transparent)
                            )
                            Icon(
                                Icons.Rounded.CheckCircle, null,
                                tint = if (selected) theme.palette.accent else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                            )
                        }
                    }
                }
            }
        }
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
            onDone = { env.vm.message(F.movedToPrivate(copied.size)) },
            onCancelled = {
                // The originals stay: the copies would only be doubles, so they go again.
                copied.forEach { item ->
                    PrivateVault.dir(context).listFiles()?.firstOrNull { it.name == item.displayName }?.delete()
                }
            }
        )
    }
}
