package com.example.amoledgallery.data
import com.example.amoledgallery.ui.i18n.strings

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.amoledgallery.model.AlbumItem
import com.example.amoledgallery.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface RenameResult {
    data class Success(val newName: String) : RenameResult
    data class PermissionRequired(val pendingIntent: PendingIntent, val targetName: String) : RenameResult
    data class Error(val message: String) : RenameResult
}

/**
 * Result of renaming an album. When some items belong to other apps, [pendingIntent] asks for write access
 * to [deniedItems], which then still have to be moved to [relativePath].
 */
data class AlbumRenameOutcome(
    val pendingIntent: PendingIntent?,
    val deniedItems: List<MediaItem>,
    val relativePath: String,
    /** The album's folder before the rename, removed once it has been emptied. */
    val oldDirectoryPath: String = "",
    /** Where each of [deniedItems] belongs, by media id: photos of sub-albums keep their folder below the album. */
    val deniedTargets: Map<Long, String> = emptyMap()
)

sealed interface MoveResult {
    data class Success(val count: Int) : MoveResult
    /**
     * Some items belong to other apps (e.g. the camera). [pendingIntent] asks for write access to all of
     * them at once; afterwards [itemsToMove] still have to be moved and [originalsToDelete] (already
     * copied to the target volume) deleted.
     */
    data class PermissionRequired(
        val pendingIntent: PendingIntent,
        val targetAlbum: AlbumItem,
        val itemsToMove: List<MediaItem>,
        val originalsToDelete: List<MediaItem> = emptyList()
    ) : MoveResult
    data class Error(val message: String) : MoveResult
}

/**
 * Repository responsible for querying images, videos, albums and trashed media using ContentResolver.
 * Utilizes MediaStore.VOLUME_EXTERNAL to seamlessly query both internal
 * storage and inserted MicroSD card storage.
 *
 * Supports:
 * - Persistent hidden albums filtering via SharedPreferences
 * - Scoped Storage and filesystem media move operations across albums
 * - MediaStore Trash and Delete API
 */
class MediaRepository(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    /**
     * Root paths of the storage volumes that are mounted right now, or null where that can't be told (API 29).
     * MediaStore can keep returning rows of a removed SD card; those would show up as blank, unopenable tiles.
     */
    private fun mountedStorageRoots(): List<String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val storage = context.getSystemService(StorageManager::class.java) ?: return null
        return runCatching {
            storage.storageVolumes
                .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
                .mapNotNull { it.directory?.path }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** True for a file on a storage volume that isn't mounted (see [mountedStorageRoots]). */
    private fun isOnMissingVolume(path: String?, mountedRoots: List<String>?): Boolean =
        mountedRoots != null && path != null && path.startsWith("/storage/") &&
            mountedRoots.none { path.startsWith("$it/", ignoreCase = true) }

    /**
     * Joins [selection] with the test build's folder restriction ([com.example.amoledgallery.BuildConfig.TEST_MEDIA_PATH]):
     * the performance build runs on test phones whose own photos it must never show. Empty in real builds.
     */
    private fun restrictToTestMedia(selection: String?, args: Array<String>?): Pair<String?, Array<String>?> {
        val path = com.example.amoledgallery.BuildConfig.TEST_MEDIA_PATH
        if (path.isEmpty()) return selection to args
        val clause = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        return (if (selection == null) clause else "($selection) AND $clause") to ((args ?: emptyArray()) + "$path%")
    }

    /** [restrictToTestMedia] for queries that take their arguments in a Bundle. */
    private fun Bundle.restrictToTestMedia(): Bundle = apply {
        val (selection, args) = restrictToTestMedia(null, null)
        if (selection != null) {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
        }
    }

    private val hiddenPrefs = context.getSharedPreferences("amoled_gallery_hidden_prefs", Context.MODE_PRIVATE)
    private val KEY_HIDDEN_BUCKETS = "hidden_bucket_ids"
    private val KEY_CREATED_SUB_ALBUM_DIRS = "created_sub_album_dirs"
    /** Exact paths of top-level album folders created in this app, so they show while still empty. */
    private val KEY_CREATED_ALBUM_DIRS = "created_album_dirs"
    /** "<bucket id>|<folder>" of the hidden albums, so a hidden one created here does not come back empty. */
    private val KEY_HIDDEN_ALBUM_DIRS = "hidden_album_dirs"

    /**
     * Gets set of persistently hidden album BUCKET_IDs.
     */
    fun getHiddenBucketIds(): Set<String> {
        return hiddenPrefs.getStringSet(KEY_HIDDEN_BUCKETS, emptySet()) ?: emptySet()
    }

    /** The folders of the albums that are hidden right now. */
    private fun hiddenAlbumDirs(): Set<String> =
        (hiddenPrefs.getStringSet(KEY_HIDDEN_ALBUM_DIRS, emptySet()) ?: emptySet())
            .mapNotNull { it.substringAfter('|', "").takeIf(String::isNotBlank) }
            .toSet()

    /**
     * Toggles an album's hidden state and persists to SharedPreferences.
     *
     * [directoryPath] is kept with it: an album created in this app is remembered by its folder and is
     * listed even while empty, so hiding one without this left an empty copy of it behind in the list.
     */
    fun setAlbumHidden(bucketId: String, hidden: Boolean, directoryPath: String = "") {
        val current = getHiddenBucketIds().toMutableSet()
        val dirs = (hiddenPrefs.getStringSet(KEY_HIDDEN_ALBUM_DIRS, emptySet()) ?: emptySet())
            .filterNot { it.substringBefore('|') == bucketId }
            .toMutableSet()
        if (hidden) {
            current.add(bucketId)
            if (directoryPath.isNotBlank()) dirs.add("$bucketId|${normalizeDirPath(directoryPath)}")
        } else {
            current.remove(bucketId)
        }
        hiddenPrefs.edit()
            .putStringSet(KEY_HIDDEN_BUCKETS, current)
            .putStringSet(KEY_HIDDEN_ALBUM_DIRS, dirs)
            .apply()
    }

    /**
     * Notes the folders of albums hidden before this was recorded (or hidden from somewhere that did not
     * know the folder), so they stop reappearing as empty albums. Only ever adds.
     */
    fun rememberHiddenAlbumDirs(hiddenAlbums: List<AlbumItem>) {
        val stored = (hiddenPrefs.getStringSet(KEY_HIDDEN_ALBUM_DIRS, emptySet()) ?: emptySet()).toMutableSet()
        val known = stored.map { it.substringBefore('|') }.toSet()
        val missing = hiddenAlbums
            .filter { it.bucketId !in known && it.directoryPath.isNotBlank() }
            .map { "${it.bucketId}|${normalizeDirPath(it.directoryPath)}" }
        if (missing.isEmpty()) return
        stored.addAll(missing)
        hiddenPrefs.edit().putStringSet(KEY_HIDDEN_ALBUM_DIRS, stored).apply()
    }

    /**
     * Queries all images and videos across all external volumes (Internal + MicroSD).
     * Optionally filtered by [bucketId] for album view.
     * When [includeHidden] is false and [bucketId] is null (main feed), hidden albums are filtered out.
     */
    suspend fun getAllMedia(
        bucketId: String? = null,
        includeHidden: Boolean = false
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = ArrayList<MediaItem>()

        val imagesUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATA,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.SIZE
        )

        val (selection, selectionArgs) = restrictToTestMedia(
            if (bucketId != null) "${MediaStore.Images.Media.BUCKET_ID} = ?" else null,
            if (bucketId != null) arrayOf(bucketId) else null
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                imagesUri,
                imageProjection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                parseCursor(cursor, imagesUri, isVideo = false, mediaList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val videosUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.SIZE
        )

        val (videoSelection, videoSelectionArgs) = restrictToTestMedia(
            if (bucketId != null) "${MediaStore.Video.Media.BUCKET_ID} = ?" else null,
            if (bucketId != null) arrayOf(bucketId) else null
        )

        try {
            contentResolver.query(
                videosUri,
                videoProjection,
                videoSelection,
                videoSelectionArgs,
                sortOrder
            )?.use { cursor ->
                parseCursor(cursor, videosUri, isVideo = true, mediaList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Filter out hidden bucket IDs if querying main feed ("Bilder")
        if (!includeHidden && bucketId == null) {
            val hidden = getHiddenBucketIds()
            if (hidden.isNotEmpty()) {
                mediaList.removeAll { it.bucketId in hidden }
            }
        }

        mediaList.sortByDescending { it.date }
        mediaList
    }

    /**
     * Parses cursor entries into [MediaItem] instances.
     */
    private fun parseCursor(
        cursor: Cursor,
        baseUri: Uri,
        isVideo: Boolean,
        outList: MutableList<MediaItem>,
        isTrashed: Boolean = false
    ) {
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val dateAddedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val bucketIdColumn = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        val bucketNameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val relativePathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        val dateModifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val mimeTypeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val durationColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
        val dateTakenColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val orientationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
        val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val mountedRoots = mountedStorageRoots()

        while (cursor.moveToNext()) {
            if (dataColumn != -1 && isOnMissingVolume(cursor.getString(dataColumn), mountedRoots)) continue
            val id = cursor.getLong(idColumn)
            val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0L
            val displayName = if (nameColumn != -1) cursor.getString(nameColumn) ?: "" else ""
            val bucketId = if (bucketIdColumn != -1) cursor.getString(bucketIdColumn) ?: "" else ""
            val bucketDisplayName = if (bucketNameColumn != -1) cursor.getString(bucketNameColumn) ?: "" else ""
            val relativePath = if (relativePathColumn != -1) cursor.getString(relativePathColumn) ?: "" else ""
            val dateModified = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L
            val mimeType = if (mimeTypeColumn != -1) cursor.getString(mimeTypeColumn) ?: "" else ""
            val dataPath = if (dataColumn != -1) cursor.getString(dataColumn) ?: "" else ""
            val durationMs = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
            // DATE_TAKEN is in milliseconds, every other date column in seconds.
            val dateTaken = if (dateTakenColumn != -1 && !cursor.isNull(dateTakenColumn)) cursor.getLong(dateTakenColumn) / 1000L else 0L
            val contentUri = ContentUris.withAppendedId(baseUri, id)

            outList.add(
                MediaItem(
                    id = id,
                    uri = contentUri,
                    isVideo = isVideo,
                    dateAdded = dateAdded,
                    durationMs = durationMs,
                    displayName = displayName,
                    bucketId = bucketId,
                    bucketDisplayName = bucketDisplayName,
                    relativePath = relativePath,
                    dateModified = dateModified,
                    isTrashed = isTrashed,
                    mimeType = mimeType,
                    dataPath = dataPath,
                    dateTaken = dateTaken,
                    width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0,
                    height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0,
                    orientation = if (orientationColumn != -1) cursor.getInt(orientationColumn) else 0,
                    size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                )
            )
        }
    }

    /**
     * Queries all folders/albums (BUCKET_DISPLAY_NAME) containing media.
     * When [includeHidden] is false, albums in [getHiddenBucketIds] are filtered out.
     */
    suspend fun getAlbums(includeHidden: Boolean = false): List<AlbumItem> = withContext(Dispatchers.IO) {
        val allMedia = getAllMedia(includeHidden = includeHidden)
        groupIntoAlbums(allMedia, includeHidden)
    }

    /**
     * Groups an already-fetched media list into [AlbumItem]s without issuing any additional
     * MediaStore queries. Callers that already hold a fresh [getAllMedia] snapshot should use
     * this directly instead of [getAlbums] to avoid redundant ContentResolver round-trips.
     */
    fun groupIntoAlbums(allMedia: List<MediaItem>, includeHidden: Boolean = false): List<AlbumItem> {
        if (allMedia.isEmpty()) return emptyCreatedAlbums(emptyList())

        val hiddenBuckets = if (!includeHidden) getHiddenBucketIds() else emptySet()
        // Which removable volumes are SD cards and which USB storage, to tell the albums apart.
        val externals = com.example.amoledgallery.util.ExternalStorage.volumes(context)

        // Group media by BUCKET_ID
        val bucketGroups = allMedia.groupBy { it.bucketId }
        val rawAlbums = ArrayList<AlbumItem>()

        for ((bucketId, items) in bucketGroups) {
            if (bucketId.isBlank() || items.isEmpty()) continue
            if (!includeHidden && bucketId in hiddenBuckets) continue

            val firstItem = items.first()
            val folderName = firstItem.bucketDisplayName.ifBlank { strings.unknown }
            val dataPath = items.firstOrNull { it.dataPath.isNotBlank() }?.dataPath ?: ""
            val dirFile = if (dataPath.isNotBlank()) File(dataPath).parentFile else null
            val directoryPath = dirFile?.absolutePath ?: ""
            val parentDirectoryPath = dirFile?.parentFile?.absolutePath ?: ""
            val isSdCard = isPathOnSdCard(dataPath) || isPathOnSdCard(firstItem.relativePath)

            rawAlbums.add(
                AlbumItem(
                    bucketId = bucketId,
                    name = folderName,
                    count = items.size,
                    coverUri = firstItem.uri,
                    isSdCard = isSdCard,
                    storage = if (isSdCard) {
                        com.example.amoledgallery.util.ExternalStorage.volumeOfPath(externals, dataPath)?.kind
                            ?: com.example.amoledgallery.util.StorageKind.SD_CARD
                    } else {
                        com.example.amoledgallery.util.StorageKind.INTERNAL
                    },
                    relativePath = firstItem.relativePath,
                    directoryPath = directoryPath,
                    parentDirectoryPath = parentDirectoryPath
                )
            )
        }

        // Calculate subAlbumCount for each album; a media container (Pictures and the like) counts none,
        // since the albums inside it stand on their own in the list.
        val containerDirs = mediaContainerDirs()
        val albumsWithSubCount = rawAlbums.map { album ->
            val subCount = if (album.directoryPath.isNotBlank() && normalizeDirPath(album.directoryPath) !in containerDirs) {
                rawAlbums.count { other ->
                    other.directoryPath != album.directoryPath &&
                    normalizeDirPath(other.parentDirectoryPath) == normalizeDirPath(album.directoryPath)
                }
            } else 0
            album.copy(subAlbumCount = subCount)
        } + emptyCreatedAlbums(rawAlbums)

        // Sort: Camera / DCIM / Screenshots first, then alphabetically
        return albumsWithSubCount.sortedWith(
            compareBy<AlbumItem> {
                val lower = it.name.lowercase()
                when {
                    lower == "camera" || lower == "kamera" -> 0
                    lower.contains("dcim") -> 1
                    lower.contains("screenshot") -> 2
                    else -> 3
                }
            }.thenBy { it.name.lowercase() }
        )
    }

    /**
     * Returns list of all currently hidden albums.
     */
    suspend fun getHiddenAlbums(): List<AlbumItem> = withContext(Dispatchers.IO) {
        val hiddenIds = getHiddenBucketIds()
        if (hiddenIds.isEmpty()) return@withContext emptyList()
        val allAlbums = getAlbums(includeHidden = true)
        allAlbums.filter { it.bucketId in hiddenIds }
    }

    /**
     * The folders Android hands out for media - DCIM, Pictures, Movies, Download - on every volume,
     * plus the volumes' own roots.
     *
     * They are where albums live, not albums that hold other albums. A phone that keeps loose files in
     * one of them (a browser saving downloads straight into Pictures, say) makes it an album in its own
     * right, and every real album beneath it would otherwise disappear into it - including albums this
     * app had just created, which is where this came up.
     */
    private fun mediaContainerDirs(): Set<String> {
        val roots = ArrayList<File>()
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { roots.add(it) }
        runCatching { com.example.amoledgallery.util.ExternalStorage.volumes(context) }
            .getOrDefault(emptyList())
            .forEach { roots.add(it.root) }
        val folders = listOf(
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS
        )
        return roots.flatMapTo(HashSet()) { root ->
            folders.map { normalizeDirPath(File(root, it).absolutePath) } + normalizeDirPath(root.absolutePath)
        }
    }

    /**
     * Filters top-level root albums (albums that do not have a parent album in the gallery).
     *
     * An album sitting directly in one of [mediaContainerDirs] is always one of these, whether or not
     * that folder holds loose files of its own.
     */
    fun getRootAlbums(allAlbums: List<AlbumItem>): List<AlbumItem> {
        val containers = mediaContainerDirs()
        val allDirPaths = allAlbums.mapNotNull { it.directoryPath.takeIf { p -> p.isNotBlank() } }
            .map { normalizeDirPath(it) }
            .toSet()

        return allAlbums.filter { album ->
            val parent = normalizeDirPath(album.parentDirectoryPath)
            parent.isBlank() || parent in containers || !allDirPaths.contains(parent)
        }
    }

    /**
     * The chain of parent albums above [album], root first - what jumping straight into a search
     * result (however deeply nested) needs for the breadcrumb trail. Walks [AlbumItem.parentDirectoryPath]
     * up through [allAlbums] the same way [getRootAlbums] and [getSubAlbums] already do; stops once a
     * level has no matching parent (it is a root album) or after a generous depth, in case two folders
     * ever pointed at each other.
     */
    fun ancestorsOf(album: AlbumItem, allAlbums: List<AlbumItem>): List<AlbumItem> {
        val byPath = allAlbums.associateBy { normalizeDirPath(it.directoryPath) }
        val containers = mediaContainerDirs()
        val chain = ArrayList<AlbumItem>()
        var current = album
        repeat(32) {
            val parentPath = normalizeDirPath(current.parentDirectoryPath)
            // A media container is where the trail ends: what sits in it is a top-level album.
            if (parentPath.isBlank() || parentPath in containers) return chain.asReversed()
            val parent = byPath[parentPath] ?: return chain.asReversed()
            chain += parent
            current = parent
        }
        return chain.asReversed()
    }

    /**
     * Gets direct sub-albums for a given [parentAlbum], combining MediaStore albums
     * and any newly created empty directories on disk.
     */
    fun getSubAlbums(parentAlbum: AlbumItem, allAlbums: List<AlbumItem>): List<AlbumItem> {
        val parentNormalized = normalizeDirPath(parentAlbum.directoryPath)
        // DCIM, Pictures and the like hold the albums themselves: what sits in them is listed in the
        // album list, so nothing hangs underneath them here (it would only be shown twice).
        if (parentNormalized in mediaContainerDirs()) return emptyList()
        val subAlbums = allAlbums.filter { other ->
            other.directoryPath != parentAlbum.directoryPath &&
            normalizeDirPath(other.parentDirectoryPath) == parentNormalized
        }.toMutableList()

        // Also list empty sub-folders on disk - but only those created in this app. Other apps leave
        // empty helper folders around (e.g. a camera's "Raw"), which would otherwise show up as albums
        // nobody created. Folders that contain media are real albums and come from MediaStore above.
        if (parentAlbum.directoryPath.isNotBlank()) {
            val parentDir = File(parentAlbum.directoryPath)
            val createdDirs = getCreatedSubAlbumDirs()
            if (parentDir.exists() && parentDir.isDirectory) {
                val diskDirs = parentDir.listFiles { file -> file.isDirectory && !file.isHidden }
                diskDirs?.forEach { subDir ->
                    val normalizedSub = normalizeDirPath(subDir.absolutePath)
                    if (normalizedSub in createdDirs && subAlbums.none { normalizeDirPath(it.directoryPath) == normalizedSub }) {
                        subAlbums.add(
                            AlbumItem(
                                bucketId = "disk_${subDir.absolutePath.hashCode()}",
                                name = subDir.name,
                                count = 0,
                                coverUri = null,
                                isSdCard = parentAlbum.isSdCard,
                                relativePath = "${parentAlbum.relativePath.trimEnd('/')}/${subDir.name}/",
                                directoryPath = subDir.absolutePath,
                                parentDirectoryPath = parentAlbum.directoryPath,
                                subAlbumCount = 0
                            )
                        )
                    }
                }
            }
        }

        return subAlbums.sortedBy { it.name.lowercase() }
    }

    /**
     * Finishes deleting an album once the user has confirmed the trash request.
     *
     * Its media are in the trash by then, but the folder is not gone: the trashed files still lie in
     * it under their .trashed- names until the trash is emptied. An album created in this app is
     * remembered by its folder, so without forgetting it here it came straight back into the list as
     * an empty album - the toast said it was deleted and it stayed. The folder itself is removed as
     * soon as nothing but empty folders is left in it.
     */
    fun finishAlbumDeletion(album: AlbumItem) {
        if (album.directoryPath.isBlank()) return
        val normalized = normalizeDirPath(album.directoryPath)
        val saved = hiddenPrefs.getStringSet(KEY_CREATED_ALBUM_DIRS, emptySet()) ?: emptySet()
        val keep = saved.filterNot { normalizeDirPath(it) == normalized }.toSet()
        if (keep.size != saved.size) {
            hiddenPrefs.edit().putStringSet(KEY_CREATED_ALBUM_DIRS, keep).apply()
        }
        // Sub-albums created inside it go with it.
        val savedSubs = getCreatedSubAlbumDirs()
        val keepSubs = savedSubs.filterNot {
            val sub = normalizeDirPath(it)
            sub == normalized || sub.startsWith("$normalized/")
        }.toSet()
        if (keepSubs.size != savedSubs.size) {
            hiddenPrefs.edit().putStringSet(KEY_CREATED_SUB_ALBUM_DIRS, keepSubs).apply()
        }
        removeDirectoryIfEmpty(album.directoryPath)
    }

    /**
     * Whether [name] can be a folder's name. A slash made "a/b" two nested folders while the album was looked for
     * as "b"; the other characters are refused by SD cards (FAT, exFAT).
     */
    private fun isValidFolderName(name: String): Boolean =
        name != "." && name != ".." && name.none { it in "/\\:*?\"<>|" || it.code < 32 }

    private fun normalizeDirPath(path: String): String {

        return path.trimEnd('/', '\\').lowercase()
    }

    /**
     * Moves media items physically into [targetAlbum] directory using Scoped Storage / MediaStore operations.
     * Updates MediaStore immediately and invalidates local thumbnail caches.
     */
    suspend fun moveMediaItems(
        items: List<MediaItem>,
        targetAlbum: AlbumItem,
        /**
         * Whether items owned by other apps may be answered with [MoveResult.PermissionRequired]. The
         * second pass after the user granted access passes false, so a still-failing item can never
         * loop back into another permission dialog.
         */
        requestPermission: Boolean = true,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): MoveResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext MoveResult.Success(0)

        val targetDir = if (targetAlbum.directoryPath.isNotBlank()) {
            File(targetAlbum.directoryPath)
        } else {
            if (targetAlbum.isSdCard) {
                val sdDir = getSdCardPicturesDirectory()
                    ?: return@withContext MoveResult.Error(strings.sdCardNotAvailable)
                File(sdDir, targetAlbum.name)
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), targetAlbum.name)
            }
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetRelPath = if (targetAlbum.relativePath.isNotBlank()) {
            val trimmed = targetAlbum.relativePath.trim('/')
            "$trimmed/"
        } else {
            "Pictures/${targetAlbum.name}/"
        }

        var movedCount = 0
        val canRequestPermission = requestPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        // Items of other apps that could not be moved in place: moved after the user grants access.
        val needsWriteAccess = mutableListOf<MediaItem>()
        // Items of other apps already copied to the other volume whose original could not be deleted.
        val originalsToDelete = mutableListOf<MediaItem>()
        val thumbnailManager = ThumbnailManager.getInstance(context)

        for ((index, item) in items.withIndex()) {
            val isItemOnSd = isPathOnSdCard(item.dataPath) || isPathOnSdCard(item.relativePath)
            val isSameVolume = (isItemOnSd == targetAlbum.isSdCard)
            var itemMoved = false

            // Fast path: On same storage volume with Android 10+ Scoped Storage,
            // updating RELATIVE_PATH physically moves the file on disk via MediaStore
            if (isSameVolume) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelPath)
                }
                try {
                    val updated = contentResolver.update(item.uri, values, null, null)
                    if (updated > 0) {
                        movedCount++
                        // Invalidate thumbnail cache since modification timestamp changed
                        thumbnailManager.getThumbnailFile(item).delete()
                        onProgress?.invoke(index + 1, items.size)
                        itemMoved = true
                    }
                } catch (e: SecurityException) {
                    // Owned by another app. Copying it instead would leave the original behind (it can't
                    // be deleted either), so ask for write access and move it in place afterwards.
                    if (canRequestPermission) {
                        needsWriteAccess.add(item)
                        onProgress?.invoke(index + 1, items.size)
                        continue
                    }
                } catch (_: Exception) {
                    // Fall back to physical file copy below
                }
            }

            if (!itemMoved) {
                // Cross-volume (Internal <-> MicroSD) or filesystem copy fallback
                try {
                    val fileName = item.displayName.ifBlank { "media_${item.id}.jpg" }
                    var copied = false
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelPath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    // Resolve the actual target volume so cross-volume copies land on the SD card
                    // itself instead of silently falling back to the primary (internal) volume,
                    // which previously created a duplicate same-named album on internal storage.
                    val targetVolumeName = if (targetAlbum.isSdCard) getSdCardVolumeName() else MediaStore.VOLUME_EXTERNAL_PRIMARY
                    val collection = when {
                        targetVolumeName != null && item.isVideo -> MediaStore.Video.Media.getContentUri(targetVolumeName)
                        targetVolumeName != null -> MediaStore.Images.Media.getContentUri(targetVolumeName)
                        item.isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val destUri = contentResolver.insert(collection, values)

                    if (destUri != null) {
                        try {
                            contentResolver.openInputStream(item.uri)?.use { input ->
                                contentResolver.openOutputStream(destUri)?.use { output ->
                                    input.copyTo(output)
                                    output.flush()
                                    copied = true
                                }
                            }
                        } finally {
                            if (copied) {
                                values.clear()
                                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                contentResolver.update(destUri, values, null, null)
                            } else {
                                // Unreadable original, or the target ran full: publishing the half-written copy
                                // left an empty or cut-off file in the album.
                                runCatching { contentResolver.delete(destUri, null, null) }
                            }
                        }
                        if (copied) preserveModifiedTime(destUri, item)
                    }

                    if (copied) {
                        // Delete original file from MediaStore
                        try {
                            val deleted = contentResolver.delete(item.uri, null, null)
                            if (deleted == 0) {
                                originalsToDelete.add(item)
                            }
                        } catch (_: SecurityException) {
                            originalsToDelete.add(item)
                        } catch (_: Exception) {}

                        thumbnailManager.getThumbnailFile(item).delete()
                        movedCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                onProgress?.invoke(index + 1, items.size)
            }
        }

        if (canRequestPermission && (needsWriteAccess.isNotEmpty() || originalsToDelete.isNotEmpty())) {
            val pendingIntent = MediaStore.createWriteRequest(
                contentResolver,
                (needsWriteAccess + originalsToDelete).map { it.uri }
            )
            return@withContext MoveResult.PermissionRequired(pendingIntent, targetAlbum, needsWriteAccess, originalsToDelete)
        }

        MoveResult.Success(movedCount)
    }

    /**
     * Gives a copied file the original's modification time (a plain copy is "modified" right now) and
     * rescans it so MediaStore's DATE_MODIFIED follows. Keeps the gallery order of copies that have no
     * capture date (screenshots, downloads).
     */
    private fun preserveModifiedTime(destUri: Uri, original: MediaItem) {
        if (original.dateModified <= 0L) return
        try {
            val path = contentResolver.query(destUri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: return
            val file = File(path)
            if (file.setLastModified(original.dateModified * 1000L)) {
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
        } catch (_: Exception) {}
    }

    /** Deletes [path] if it is a directory without any files left in it (empty sub-folders included). */
    /** When the folders were last handed to the scanner; scanning them again right away is pointless. */
    private var lastFolderScan = 0L

    /**
     * Has Android scan the folders new files land in (DCIM, Pictures, Movies, Download) on every volume.
     *
     * An app that writes a file without telling MediaStore - plenty of downloaders and file managers do -
     * leaves it invisible to every gallery until MediaStore notices by itself, which can take until its
     * nightly housekeeping: a video downloaded in the evening turned up the next day, while the file
     * manager had been showing it all along. The scanner skips files it already knows unchanged, so this
     * only costs something when there is really something new; [minIntervalMs] keeps it from running on
     * every single resume.
     */
    fun rescanMediaFolders(minIntervalMs: Long = 60_000L) {
        val now = System.currentTimeMillis()
        if (now - lastFolderScan < minIntervalMs) return
        lastFolderScan = now
        try {
            val roots = ArrayList<File>()
            Environment.getExternalStorageDirectory()?.let { roots.add(it) }
            com.example.amoledgallery.util.ExternalStorage.volumes(context).forEach { roots.add(it.root) }
            val folders = listOf(
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_DOWNLOADS
            )
            val paths = roots
                .flatMap { root -> folders.map { File(root, it) } }
                .filter { it.isDirectory }
                .map { it.absolutePath }
            if (paths.isEmpty()) return
            android.media.MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
        } catch (_: Exception) {}
    }

    fun removeDirectoryIfEmpty(path: String) {
        if (path.isBlank()) return
        val dir = File(path)
        try {
            if (dir.isDirectory && dir.walkBottomUp().all { it.isDirectory }) {
                dir.walkBottomUp().forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    /** Deletes media rows (and files) the app has write access to. Returns how many were deleted. */
    suspend fun deleteMedia(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        val thumbnailManager = ThumbnailManager.getInstance(context)
        items.count { item ->
            val deleted = try {
                contentResolver.delete(item.uri, null, null) > 0
            } catch (_: Exception) {
                false
            }
            if (deleted) thumbnailManager.getThumbnailFile(item).delete()
            deleted
        }
    }

    /**
     * Moves media to [relativePath] in place. Returns the items that could not be moved because they
     * belong to another app (callers can request write access for those and call this again).
     */
    suspend fun moveToRelativePath(items: List<MediaItem>, relativePath: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val thumbnailManager = ThumbnailManager.getInstance(context)
        val denied = mutableListOf<MediaItem>()
        for (item in items) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
            try {
                contentResolver.update(item.uri, values, null, null)
            } catch (_: SecurityException) {
                denied.add(item)
            } catch (_: Exception) {}
            thumbnailManager.getThumbnailFile(item).delete()
        }
        denied
    }

    /**
     * Queries trashed items using MediaStore.QUERY_ARG_MATCH_TRASHED (Android 11+).
     */
    suspend fun getTrashedMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext emptyList()
        }

        val trashedList = ArrayList<MediaItem>()
        val imagesUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videosUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_MODIFIED,
            // Without these a photo in the trash showed the day it was last changed as its date, and its
            // details had no type or size.
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.SIZE
        )

        val bundle = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )
        }.restrictToTestMedia()

        try {
            contentResolver.query(imagesUri, projection, bundle, null)?.use { cursor ->
                parseCursor(cursor, imagesUri, isVideo = false, trashedList, isTrashed = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.SIZE
        )

        try {
            contentResolver.query(videosUri, videoProjection, bundle, null)?.use { cursor ->
                parseCursor(cursor, videosUri, isVideo = true, trashedList, isTrashed = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        trashedList.sortByDescending { it.dateModified }
        trashedList
    }

    /**
     * Trashes or restores media items using MediaStore.createTrashRequest.
     * Returns a [PendingIntent] for user confirmation dialog on Android 11+.
     */
    fun createTrashRequest(uris: List<Uri>, trash: Boolean): PendingIntent? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createTrashRequest(contentResolver, uris, trash)
        } else {
            uris.forEach { contentResolver.delete(it, null, null) }
            null
        }
    }

    /**
     * Permanently deletes items from MediaStore.
     * Returns a [PendingIntent] on Android 11+.
     */
    fun createDeleteRequest(uris: List<Uri>): PendingIntent? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris)
        } else {
            uris.forEach { contentResolver.delete(it, null, null) }
            null
        }
    }

    /**
     * Renames a media file by updating MediaStore.MediaColumns.DISPLAY_NAME.
     */
    suspend fun renameMedia(
        uri: Uri,
        oldDisplayName: String,
        newDisplayName: String
    ): RenameResult = withContext(Dispatchers.IO) {
        val trimmedNewName = newDisplayName.trim()
        if (trimmedNewName.isBlank()) {
            return@withContext RenameResult.Error(strings.nameEmpty)
        }

        val finalName = if (!trimmedNewName.contains(".") && oldDisplayName.contains(".")) {
            val extension = oldDisplayName.substringAfterLast(".")
            "$trimmedNewName.$extension"
        } else {
            trimmedNewName
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
        }

        try {
            val rows = contentResolver.update(uri, values, null, null)
            if (rows > 0) {
                RenameResult.Success(finalName)
            } else {
                RenameResult.Error(strings.renameFailed)
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createWriteRequest(contentResolver, listOf(uri))
                RenameResult.PermissionRequired(pendingIntent, finalName)
            } else {
                RenameResult.Error(e.localizedMessage ?: strings.permissionDenied)
            }
        } catch (e: Exception) {
            RenameResult.Error(e.localizedMessage ?: strings.renameFailedGeneric)
        }
    }

    /**
     * Creates a new album folder either on internal storage or SD card.
     */
    suspend fun createAlbumFolder(albumName: String, onSdCard: Boolean): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cleanName = albumName.trim()
            if (cleanName.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException(strings.nameEmpty))
            }
            if (!isValidFolderName(cleanName)) {
                return@withContext Result.failure(IllegalArgumentException(com.example.amoledgallery.ui.i18n.F.invalidFolderName))
            }

            val targetDir: File = if (onSdCard) {
                val sdDir = getSdCardPicturesDirectory()
                    ?: return@withContext Result.failure(IllegalStateException(strings.sdCardNotWritable))
                File(sdDir, cleanName)
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                File(picturesDir, cleanName)
            }

            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                if (!created && !targetDir.exists()) {
                    return@withContext Result.failure(IllegalStateException(strings.folderCreateFailed))
                }
            }
            rememberCreatedAlbumDir(targetDir)

            Result.success(targetDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Normalized paths of the sub-album folders created in this app (shown even while still empty). */
    private fun getCreatedSubAlbumDirs(): Set<String> =
        hiddenPrefs.getStringSet(KEY_CREATED_SUB_ALBUM_DIRS, emptySet()) ?: emptySet()

    private fun rememberCreatedAlbumDir(dir: File) {
        val current = hiddenPrefs.getStringSet(KEY_CREATED_ALBUM_DIRS, emptySet()) ?: emptySet()
        hiddenPrefs.edit().putStringSet(KEY_CREATED_ALBUM_DIRS, current + dir.absolutePath).apply()
    }

    /**
     * Albums created in this app that hold nothing yet. MediaStore only knows folders with media in
     * them, so a freshly created album would otherwise not appear until something is moved into it.
     * Folders deleted since, or that have media now (then they come from [withMedia]), are left out;
     * deleted ones are also forgotten.
     */
    private fun emptyCreatedAlbums(withMedia: List<AlbumItem>): List<AlbumItem> {
        val saved = hiddenPrefs.getStringSet(KEY_CREATED_ALBUM_DIRS, emptySet()) ?: emptySet()
        // Sub-albums created here belong in this list too: without them an empty one existed only while
        // its parent album was open - it could not be searched for, and nothing could be moved into it.
        val savedSubs = getCreatedSubAlbumDirs()
        if (saved.isEmpty() && savedSubs.isEmpty()) return emptyList()
        // A hidden album stays hidden even though its folder is remembered here.
        val hiddenDirs = hiddenAlbumDirs()
        val known = withMedia.map { normalizeDirPath(it.directoryPath) }.toSet() + hiddenDirs
        val gone = saved.filter { resolveExistingDir(it) == null }.toSet()
        if (gone.isNotEmpty()) {
            hiddenPrefs.edit().putStringSet(KEY_CREATED_ALBUM_DIRS, saved - gone).apply()
        }
        return (saved + savedSubs - gone)
            .mapNotNull { resolveExistingDir(it)?.absolutePath }
            .distinctBy { normalizeDirPath(it) }
            .filter { normalizeDirPath(it) !in known }
            .map { path ->
                val dir = File(path)
                val onSd = isPathOnSdCard(path)
                val root = if (onSd) getSdCardRootDirectory() else Environment.getExternalStorageDirectory()
                val relative = root?.absolutePath?.let { rootPath ->
                    path.removePrefix(rootPath).trimStart('/').trimEnd('/') + "/"
                } ?: "${Environment.DIRECTORY_PICTURES}/${dir.name}/"
                AlbumItem(
                    bucketId = "disk_${path.hashCode()}",
                    name = dir.name,
                    count = 0,
                    coverUri = null,
                    isSdCard = onSd,
                    storage = com.example.amoledgallery.util.ExternalStorage.kindOfPath(
                        com.example.amoledgallery.util.ExternalStorage.volumes(context), path
                    ),
                    relativePath = relative,
                    directoryPath = path,
                    parentDirectoryPath = dir.parent ?: "",
                    subAlbumCount = 0
                )
            }
    }

    private fun rememberCreatedSubAlbumDir(dir: File) {
        // The path as it really is on disk. It used to be stored lower-cased, which no longer finds the
        // folder on a card (/storage/A28E-16F9 is a real name, and that part of the path is case
        // sensitive); [resolveExistingDir] still finds those older entries.
        val updated = getCreatedSubAlbumDirs() + dir.absolutePath
        hiddenPrefs.edit().putStringSet(KEY_CREATED_SUB_ALBUM_DIRS, updated).apply()
    }

    /**
     * The folder at [path] as it is actually spelled on disk, or null when there is none.
     *
     * Paths remembered earlier were lower-cased, which the card's own mount point does not answer to;
     * each step of the path is matched without regard to case so those still resolve.
     */
    private fun resolveExistingDir(path: String): File? {
        if (path.isBlank()) return null
        if (!File(path).isDirectory && !File(path).parentFile.let { it != null && it.isDirectory }) {
            // Not even the parent is there under this spelling: walking it would list nothing.
            if (!path.startsWith("/storage/") && !path.startsWith("/sdcard")) return null
        }
        val parts = path.trim('/').split('/').filter { it.isNotBlank() }
        var current = File("/")
        for (part in parts) {
            // The exact spelling first, then the same name in any case - which is what an entry
            // remembered in lower case needs, and what gives the folder's real name back.
            val exact = File(current, part)
            val match = if (exact.isDirectory && current.list()?.contains(part) != false) {
                exact
            } else {
                current.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(part, ignoreCase = true) }
                    ?: exact.takeIf { it.isDirectory }
            }
            current = match ?: return null
        }
        return current.takeIf { it.isDirectory }
    }

    /**
     * Creates a new sub-album folder inside [parentAlbum].
     */
    suspend fun createSubAlbumFolder(parentAlbum: AlbumItem, subAlbumName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cleanName = subAlbumName.trim()
            if (cleanName.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException(strings.nameEmpty))
            }
            if (!isValidFolderName(cleanName)) {
                return@withContext Result.failure(IllegalArgumentException(com.example.amoledgallery.ui.i18n.F.invalidFolderName))
            }

            val parentDir: File = if (parentAlbum.directoryPath.isNotBlank()) {
                File(parentAlbum.directoryPath)
            } else {
                if (parentAlbum.isSdCard) {
                    val sdDir = getSdCardPicturesDirectory()
                        ?: return@withContext Result.failure(IllegalStateException(strings.sdCardNotWritable))
                    File(sdDir, parentAlbum.relativePath)
                } else {
                    val root = Environment.getExternalStorageDirectory()
                    File(root, parentAlbum.relativePath)
                }
            }

            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }

            val targetDir = File(parentDir, cleanName)
            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                if (!created && !targetDir.exists()) {
                    return@withContext Result.failure(IllegalStateException(strings.subFolderCreateFailed))
                }
            }
            rememberCreatedSubAlbumDir(targetDir)

            Result.success(targetDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if SD card storage is available.
     */
    fun isSdCardAvailable(): Boolean {
        return getSdCardPicturesDirectory() != null
    }

    /**
     * The removable volume that "move to SD card" (or USB storage) writes to: the SD card when there is
     * one, otherwise a USB drive. Only a writable (mounted read-write) volume counts.
     */
    fun externalTarget(): com.example.amoledgallery.util.ExternalVolume? =
        com.example.amoledgallery.util.ExternalStorage.volumes(context)
            .filter { !it.readOnly }
            .sortedBy { if (it.kind == com.example.amoledgallery.util.StorageKind.SD_CARD) 0 else 1 }
            .firstOrNull()

    /** Every mounted removable volume, SD cards and USB storage alike (for the storage pages). */
    fun externalVolumes(): List<com.example.amoledgallery.util.ExternalVolume> =
        com.example.amoledgallery.util.ExternalStorage.volumes(context)

    /**
     * The MediaStore volume name of [externalTarget], for [MediaStore.Images.Media.getContentUri] /
     * [MediaStore.Video.Media.getContentUri], so inserts physically land on that volume instead of silently
     * defaulting to the primary one.
     */
    private fun getSdCardVolumeName(): String? = externalTarget()?.mediaStoreName

    private fun getSdCardPicturesDirectory(): File? {
        val root = getSdCardRootDirectory() ?: return null
        val pictures = File(root, "Pictures")
        if (!pictures.exists()) pictures.mkdirs()
        return pictures
    }

    /** The root directory of [externalTarget], for its Pictures folder and free-space queries. */
    private fun getSdCardRootDirectory(): File? = externalTarget()?.root

    private fun isPathOnSdCard(path: String): Boolean {
        return path.contains("/storage/") && !path.contains("/emulated/")
    }

    /**
     * Free/total space for a storage volume, in bytes.
     */
    data class AlbumStorage(val bucketId: String, val name: String, val count: Int, val bytes: Long, val coverUri: Uri?)

    /** How much space the photos, videos and the trash take, and each album's share, for the storage page. */
    data class MediaStorageStats(
        /** Photos, videos and trash on internal storage. */
        val photoBytes: Long,
        val videoBytes: Long,
        val trashBytes: Long,
        /** The same on the SD card (0 without one). */
        val sdPhotoBytes: Long = 0L,
        val sdVideoBytes: Long = 0L,
        val sdTrashBytes: Long = 0L,
        /** Largest first. */
        val albums: List<AlbumStorage>,
        /** Each mounted SD card and USB drive on its own (the sd* fields above add them all up). */
        val externals: List<ExternalUsage> = emptyList()
    )

    /** One removable volume's space and what the photos, videos and trash take of it. */
    data class ExternalUsage(
        val kind: com.example.amoledgallery.util.StorageKind,
        val freeBytes: Long,
        val totalBytes: Long,
        val photoBytes: Long,
        val videoBytes: Long,
        val trashBytes: Long
    )

    suspend fun getMediaStorageStats(): MediaStorageStats = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.MediaColumns.DATA
        )
        class AlbumTotal(val name: String, var count: Int = 0, var bytes: Long = 0L, var cover: Uri? = null)
        val albums = HashMap<String, AlbumTotal>()
        val mountedRoots = mountedStorageRoots()

        /** Sums of [uri] per MediaStore volume name (null for internal storage). */
        fun sumOf(uri: Uri): Map<String?, Long> {
            val sums = HashMap<String?, Long>()
            try {
                val (selection, args) = restrictToTestMedia(null, null)
                contentResolver.query(uri, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val volumeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
                    val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    while (cursor.moveToNext()) {
                        if (dataColumn >= 0 && isOnMissingVolume(cursor.getString(dataColumn), mountedRoots)) continue
                        val size = cursor.getLong(sizeColumn)
                        val volume = if (volumeColumn >= 0) cursor.getString(volumeColumn) else null
                        val key = volume?.takeIf { it != MediaStore.VOLUME_EXTERNAL_PRIMARY }
                        sums[key] = (sums[key] ?: 0L) + size
                        val bucket = cursor.getString(bucketColumn) ?: continue
                        val album = albums.getOrPut(bucket) { AlbumTotal(cursor.getString(nameColumn) ?: strings.unknown) }
                        album.count++
                        album.bytes += size
                        if (album.cover == null) album.cover = ContentUris.withAppendedId(uri, cursor.getLong(idColumn))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return sums
        }

        val imagesUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videosUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val photoSums = sumOf(imagesUri)
        val videoSums = sumOf(videosUri)
        val trashSums = HashMap<String?, Long>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bundle = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY) }.restrictToTestMedia()
            listOf(imagesUri, videosUri).forEach { uri ->
                try {
                    contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.VOLUME_NAME, MediaStore.MediaColumns.DATA), bundle, null)?.use { cursor ->
                        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                        val volumeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
                        val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        while (cursor.moveToNext()) {
                            if (dataColumn >= 0 && isOnMissingVolume(cursor.getString(dataColumn), mountedRoots)) continue
                            val volume = if (volumeColumn >= 0) cursor.getString(volumeColumn) else null
                            val key = volume?.takeIf { it != MediaStore.VOLUME_EXTERNAL_PRIMARY }
                            trashSums[key] = (trashSums[key] ?: 0L) + cursor.getLong(sizeColumn)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun externalSum(sums: Map<String?, Long>) = sums.filterKeys { it != null }.values.sum()
        val externals = externalVolumes().map { volume ->
            val name = volume.mediaStoreName
            ExternalUsage(
                kind = volume.kind,
                freeBytes = volume.freeBytes(),
                totalBytes = volume.totalBytes(),
                photoBytes = photoSums[name] ?: 0L,
                videoBytes = videoSums[name] ?: 0L,
                trashBytes = trashSums[name] ?: 0L
            )
        }
        MediaStorageStats(
            photoBytes = photoSums[null] ?: 0L,
            videoBytes = videoSums[null] ?: 0L,
            trashBytes = trashSums[null] ?: 0L,
            sdPhotoBytes = externalSum(photoSums),
            sdVideoBytes = externalSum(videoSums),
            sdTrashBytes = externalSum(trashSums),
            externals = externals,
            albums = albums.map { (bucketId, album) -> AlbumStorage(bucketId, album.name, album.count, album.bytes, album.cover) }
                .sortedByDescending { it.bytes }
        )
    }

    data class StorageSpaceInfo(val freeBytes: Long, val totalBytes: Long)

    /**
     * Free/total space on internal storage.
     */
    fun getInternalStorageSpace(): StorageSpaceInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageSpaceInfo(stat.availableBytes, stat.totalBytes)
    }

    /**
     * Free/total space on the mounted SD card, or null if none is available.
     */
    fun getSdCardStorageSpace(): StorageSpaceInfo? {
        val root = getSdCardRootDirectory() ?: return null
        return try {
            val stat = StatFs(root.path)
            StorageSpaceInfo(stat.availableBytes, stat.totalBytes)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renames an album folder physically on disk and updates RELATIVE_PATH in MediaStore.
     */
    suspend fun renameAlbum(album: AlbumItem, newName: String): Result<AlbumRenameOutcome> = withContext(Dispatchers.IO) {
        try {
            val cleanName = newName.trim()
            if (cleanName.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException(strings.nameEmpty))
            }
            if (!isValidFolderName(cleanName)) {
                return@withContext Result.failure(IllegalArgumentException(com.example.amoledgallery.ui.i18n.F.invalidFolderName))
            }

            var targetDir: File? = null
            if (album.directoryPath.isNotBlank()) {
                val currentDir = File(album.directoryPath)
                if (currentDir.exists() && currentDir.isDirectory) {
                    val parent = currentDir.parentFile ?: currentDir
                    val dest = File(parent, cleanName)
                    if (currentDir.renameTo(dest)) {
                        targetDir = dest
                    }
                }
            }

            // Calculate new relative path
            val oldRel = album.relativePath.trim('/')
            val newRelPath = if (oldRel.contains('/')) {
                val prefix = oldRel.substringBeforeLast('/')
                "$prefix/$cleanName/"
            } else {
                "Pictures/$cleanName/"
            }

            // Update all media items belonging to this album in MediaStore. Items of other apps (camera
            // photos) refuse the update; they are handed back so the user can grant write access.
            val items = getAllMedia(album.bucketId, includeHidden = true)
            val denied = moveToRelativePath(items, newRelPath).toMutableList()
            val deniedTargets = HashMap<Long, String>()
            denied.forEach { deniedTargets[it.id] = newRelPath }

            // The folder could not be renamed as a whole (it holds files of other apps, which is the usual case):
            // its sub-albums have to follow one by one. They used to stay in the old folder, which then no longer
            // was an album - so they turned up in the main album list. A media container (DCIM, Pictures) is no
            // album folder; what lies in it is never moved along.
            if (targetDir == null && oldRel.contains('/') && normalizeDirPath(album.directoryPath) !in mediaContainerDirs()) {
                val oldPrefix = "$oldRel/"
                getAllMedia(includeHidden = true)
                    .filter { it.relativePath.length > oldPrefix.length && it.relativePath.startsWith(oldPrefix, ignoreCase = true) }
                    .groupBy { it.relativePath }
                    .forEach { (path, group) ->
                        val target = newRelPath + path.substring(oldPrefix.length)
                        moveToRelativePath(group, target).forEach {
                            denied.add(it)
                            deniedTargets[it.id] = target
                        }
                    }
            }
            // Renamed as a whole: the folders remembered as created here (shown while empty) moved with it.
            targetDir?.let { renameRememberedDirs(album.directoryPath, it.absolutePath) }

            // Trigger rescan of the renamed folder
            targetDir?.let { dir ->
                val files = dir.listFiles { f -> f.isFile }?.map { it.absolutePath }?.toTypedArray()
                if (!files.isNullOrEmpty()) {
                    MediaScannerConnection.scanFile(context, files, null) { _, _ -> }
                }
            }

            if (denied.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createWriteRequest(contentResolver, denied.map { it.uri })
                return@withContext Result.success(AlbumRenameOutcome(pendingIntent, denied, newRelPath, album.directoryPath, deniedTargets))
            }
            removeDirectoryIfEmpty(album.directoryPath)
            Result.success(AlbumRenameOutcome(null, emptyList(), newRelPath, album.directoryPath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Moves the items a rename had to leave behind, now that the user has allowed it, each to its own folder.
     * Returns those that still could not be moved.
     */
    suspend fun finishAlbumRename(outcome: AlbumRenameOutcome): List<MediaItem> = withContext(Dispatchers.IO) {
        val stillDenied = ArrayList<MediaItem>()
        outcome.deniedItems
            .groupBy { outcome.deniedTargets[it.id] ?: outcome.relativePath }
            .forEach { (target, group) -> stillDenied += moveToRelativePath(group, target) }
        if (stillDenied.isEmpty()) removeDirectoryIfEmpty(outcome.oldDirectoryPath)
        stillDenied
    }

    /** Folders remembered as created in this app follow their album when its folder is renamed. */
    private fun renameRememberedDirs(oldPath: String, newPath: String) {
        if (oldPath.isBlank() || newPath.isBlank()) return
        val old = normalizeDirPath(oldPath)
        fun moved(saved: Set<String>): Set<String>? {
            var changed = false
            val result = saved.mapTo(HashSet()) { entry ->
                val normalized = normalizeDirPath(entry)
                when {
                    normalized == old -> {
                        changed = true
                        newPath
                    }
                    normalized.startsWith("$old/") -> {
                        changed = true
                        newPath.trimEnd('/') + entry.trimEnd('/').substring(old.length)
                    }
                    else -> entry
                }
            }
            return if (changed) result else null
        }
        val editor = hiddenPrefs.edit()
        moved(hiddenPrefs.getStringSet(KEY_CREATED_ALBUM_DIRS, emptySet()) ?: emptySet())?.let { editor.putStringSet(KEY_CREATED_ALBUM_DIRS, it) }
        moved(getCreatedSubAlbumDirs())?.let { editor.putStringSet(KEY_CREATED_SUB_ALBUM_DIRS, it) }
        editor.apply()
    }

    /**
     * Deletes an album by moving all contained media items to the MediaStore Trash
     * and deleting the directory on disk.
     */
    suspend fun deleteAlbum(album: AlbumItem): Result<PendingIntent?> = withContext(Dispatchers.IO) {
        try {
            val items = getAllMedia(album.bucketId, includeHidden = true)
            val thumbnailManager = ThumbnailManager.getInstance(context)

            if (items.isNotEmpty()) {
                val uris = items.map { it.uri }
                val pendingIntent = createTrashRequest(uris, trash = true)
                items.forEach { thumbnailManager.getThumbnailFile(it).delete() }

                if (pendingIntent != null) {
                    return@withContext Result.success(pendingIntent)
                }
            }

            // Nothing had to be trashed (an album that only held empty folders, or one created here and
            // never filled): forget it and take its folder with it. Only folders are removed, never files
            // MediaStore doesn't index - a recursive delete would silently destroy those.
            finishAlbumDeletion(album)

            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** How an album migration went: [moved] files are on the other volume, [failed] stayed where they were. */
    data class MigrationResult(
        /** The user has to confirm deleting originals owned by other apps; null when nothing is left. */
        val deleteRequest: PendingIntent?,
        val moved: Int,
        val failed: Int,
        /** The originals [deleteRequest] asks about, so several albums can be asked for in one go. */
        val pendingDeleteUris: List<Uri> = emptyList()
    )

    /**
     * Physically migrates all files of an album between Internal Storage and MicroSD card.
     *
     * Every file is copied on its own, through MediaStore onto the target volume (as a cross-volume
     * move does), and only then is the original removed. A file that cannot be copied - unreadable,
     * a name the storage refuses, the card filling up - is left in place and counted in
     * [MigrationResult.failed] instead of aborting the rest, which used to leave big albums half
     * moved with only an error to show for it. Space is checked before anything is copied.
     */
    suspend fun migrateAlbumStorage(
        album: AlbumItem,
        toSdCard: Boolean,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<MigrationResult> = withContext(Dispatchers.IO) {
        try {
            val targetVolume = if (toSdCard) {
                getSdCardVolumeName() ?: return@withContext Result.failure(IllegalStateException(strings.sdCardNotAvailable))
            } else {
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            }
            val targetRelPath = "${Environment.DIRECTORY_PICTURES}/${album.name}/"

            val items = getAllMedia(album.bucketId, includeHidden = true)
            val total = items.size

            // Refuse up front rather than fill the card and stop halfway; 50 MB kept as headroom.
            val needed = items.sumOf { item -> runCatching { File(item.dataPath).length() }.getOrDefault(0L) }
            val free = if (toSdCard) getSdCardStorageSpace()?.freeBytes else getInternalStorageSpace().freeBytes
            if (free != null && needed > 0L && needed + 50L * 1024 * 1024 > free) {
                return@withContext Result.failure(IllegalStateException(strings.notEnoughSpace(needed, free)))
            }

            val thumbnailManager = ThumbnailManager.getInstance(context)
            val securityExceptionUris = mutableListOf<Uri>()
            var moved = 0
            var failed = 0

            for ((index, item) in items.withIndex()) {
                // Already on the target volume (an album spread over both): nothing to do.
                if (isPathOnSdCard(item.dataPath) == toSdCard) {
                    onProgress?.invoke(index + 1, total)
                    continue
                }
                val copy = copyToVolume(item, targetVolume, targetRelPath)
                if (copy != null) {
                    moved++
                    try {
                        if (contentResolver.delete(item.uri, null, null) == 0) securityExceptionUris.add(item.uri)
                    } catch (_: SecurityException) {
                        securityExceptionUris.add(item.uri)
                    } catch (_: Exception) {}
                    thumbnailManager.getThumbnailFile(item).delete()
                } else {
                    failed++
                }
                onProgress?.invoke(index + 1, total)
            }

            // Remove the old folder once nothing is left in it.
            if (album.directoryPath.isNotBlank()) removeDirectoryIfEmpty(album.directoryPath)

            val deleteRequest = if (securityExceptionUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                createDeleteRequest(securityExceptionUris)
            } else {
                null
            }
            Result.success(MigrationResult(deleteRequest, moved, failed, securityExceptionUris))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copies [item] into [relativePath] on [volume] through MediaStore (which picks a free name if one
     * is taken) and keeps its modification time. Returns the copy, or null - with any half-written
     * copy removed - when it could not be made.
     */
    private fun copyToVolume(item: MediaItem, volume: String, relativePath: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName.ifBlank { "media_${item.id}" })
            if (item.mimeType.isNotBlank()) put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (item.isVideo) {
            MediaStore.Video.Media.getContentUri(volume)
        } else {
            MediaStore.Images.Media.getContentUri(volume)
        }
        val dest = try {
            contentResolver.insert(collection, values)
        } catch (_: Exception) {
            null
        } ?: return null
        val copied = try {
            contentResolver.openInputStream(item.uri)?.use { input ->
                contentResolver.openOutputStream(dest)?.use { output -> input.copyTo(output, 256 * 1024) }
            } != null
        } catch (_: Exception) {
            false
        }
        if (!copied) {
            runCatching { contentResolver.delete(dest, null, null) }
            return null
        }
        runCatching {
            contentResolver.update(dest, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        preserveModifiedTime(dest, item)
        return dest
    }
}
