package com.example.amoledgallery.ui.viewmodel
import com.example.amoledgallery.ui.i18n.LanguageState
import com.example.amoledgallery.ui.i18n.strings

import android.app.Application
import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.amoledgallery.data.AlbumRenameOutcome
import com.example.amoledgallery.data.MediaRepository
import kotlinx.coroutines.Job
import com.example.amoledgallery.data.MoveResult
import com.example.amoledgallery.data.RenameResult
import com.example.amoledgallery.data.ThumbnailManager
import com.example.amoledgallery.model.AlbumItem
import com.example.amoledgallery.model.AlbumSort
import com.example.amoledgallery.model.sortAlbums
import com.example.amoledgallery.model.MediaItem
import com.example.amoledgallery.service.MoveMediaService
import com.example.amoledgallery.service.MoveProgressHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GalleryTab {
    BILDER,
    ALBEN,
    MENU
}

enum class DetailSource {
    ALL,
    ALBUM,
    TRASH,
    FAVORITES
}

enum class MediaFilter(val label: String) {
    ALL("Alle"),
    IMAGES("Bilder"),
    VIDEOS("Videos"),
    GIFS("GIFs")
}

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data object PermissionRequired : GalleryUiState
    data object Empty : GalleryUiState
    data class Success(val items: List<MediaItem>) : GalleryUiState
}

data class PendingRenameData(
    val uri: Uri,
    val oldName: String,
    val targetName: String
)

data class PendingMoveData(
    val itemsToMove: List<MediaItem>,
    val targetAlbum: AlbumItem,
    /** Already copied to the target; only the originals still have to be deleted once access is granted. */
    val originalsToDelete: List<MediaItem> = emptyList()
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    // Current Navigation Tab (Bilder vs Alben)
    private val _selectedTab = MutableStateFlow(GalleryTab.BILDER)
    val selectedTab: StateFlow<GalleryTab> = _selectedTab.asStateFlow()

    // Hierarchical Album Breadcrumb Stack (empty = root Alben)
    private val _breadcrumbStack = MutableStateFlow<List<AlbumItem>>(emptyList())
    val breadcrumbStack: StateFlow<List<AlbumItem>> = _breadcrumbStack.asStateFlow()

    // Active Album (null when viewing root tabs)
    private val _selectedAlbum = MutableStateFlow<AlbumItem?>(null)
    val selectedAlbum: StateFlow<AlbumItem?> = _selectedAlbum.asStateFlow()

    // Bucket id of the album whose media and sub-albums are currently loaded (they arrive after navigation)
    private val _loadedAlbumKey = MutableStateFlow<String?>(null)
    val loadedAlbumKey: StateFlow<String?> = _loadedAlbumKey.asStateFlow()

    // Sub-albums of currently opened album
    private val _currentSubAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val currentSubAlbums: StateFlow<List<AlbumItem>> = _currentSubAlbums.asStateFlow()

    // Media Filter State (Alle, Bilder, Videos, GIFs)
    private val _selectedFilter = MutableStateFlow(MediaFilter.ALL)
    val selectedFilter: StateFlow<MediaFilter> = _selectedFilter.asStateFlow()

    // Trash View state
    private val _isTrashOpen = MutableStateFlow(false)
    val isTrashOpen: StateFlow<Boolean> = _isTrashOpen.asStateFlow()

    // ---- Duplicates: files that are byte for byte the same -----------------------------------------
    private val duplicateFinder = com.example.amoledgallery.data.DuplicateFinder(application)
    private val _isDuplicatesOpen = MutableStateFlow(false)
    val isDuplicatesOpen: StateFlow<Boolean> = _isDuplicatesOpen.asStateFlow()
    private val _duplicateGroups = MutableStateFlow<List<com.example.amoledgallery.data.DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<com.example.amoledgallery.data.DuplicateGroup>> = _duplicateGroups.asStateFlow()
    /** How far the scan has got; null when none is running. */
    private val _duplicateProgress = MutableStateFlow<com.example.amoledgallery.data.DuplicateProgress?>(null)
    val duplicateProgress: StateFlow<com.example.amoledgallery.data.DuplicateProgress?> = _duplicateProgress.asStateFlow()
    /** True once a scan has finished, so "nothing found" can be told apart from "not looked yet". */
    private val _duplicatesScanned = MutableStateFlow(false)
    val duplicatesScanned: StateFlow<Boolean> = _duplicatesScanned.asStateFlow()
    /** The copies ticked for deletion, by media id. */
    private val _duplicateSelection = MutableStateFlow<Set<Long>>(emptySet())
    val duplicateSelection: StateFlow<Set<Long>> = _duplicateSelection.asStateFlow()
    private var duplicateJob: Job? = null

    // Raw unfiltered media items
    private val _rawMedia = MutableStateFlow<List<MediaItem>>(emptyList())

    // Main media state
    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // Filtered media for currently selected album
    private val _albumMediaState = MutableStateFlow<List<MediaItem>>(emptyList())
    val albumMediaState: StateFlow<List<MediaItem>> = _albumMediaState.asStateFlow()

    // All available albums/folders
    private val _albumsState = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albumsState: StateFlow<List<AlbumItem>> = _albumsState.asStateFlow()

    // How the album list is ordered, and the user's own order once they have moved albums by hand.
    private val albumOrderPrefs = application.getSharedPreferences("amoled_gallery_album_order", Context.MODE_PRIVATE)
    private val _albumSort = MutableStateFlow(
        albumOrderPrefs.getString("sort", null)?.let { runCatching { AlbumSort.valueOf(it) }.getOrNull() } ?: AlbumSort.STANDARD
    )
    val albumSort: StateFlow<AlbumSort> = _albumSort.asStateFlow()
    /** Bucket ids in the user's order (for [AlbumSort.CUSTOM]); albums not in it follow in the standard order. */
    private val _albumOrder = MutableStateFlow(
        albumOrderPrefs.getString("order", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    )

    // Root-level albums (not contained within another album folder), in the chosen order
    // Albums tucked away under "Other albums" or put into a group are not listed among the main albums.
    val rootAlbums: StateFlow<List<AlbumItem>> = combine(
        _albumsState, _albumSort, _albumOrder,
        com.example.amoledgallery.ui.features.AlbumExtras.otherIds,
        com.example.amoledgallery.ui.features.AlbumExtras.groups
    ) { albums, sort, order, other, groups ->
        val tucked = com.example.amoledgallery.ui.features.AlbumExtras.hiddenFromMain(other, groups)
        sortAlbums(repository.getRootAlbums(albums), sort, order).filterNot { it.bucketId in tucked }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setAlbumSort(sort: AlbumSort) {
        _albumSort.value = sort
        albumOrderPrefs.edit().putString("sort", sort.name).apply()
    }

    /**
     * Moves [album] in the album list: [steps] places later (negative: earlier), or to the very top
     * with [toTop]. Switches to the user's own order, starting from the order shown right now.
     */
    fun moveAlbum(album: AlbumItem, steps: Int, toTop: Boolean = false) {
        val shown = rootAlbums.value.map { it.bucketId }.toMutableList()
        val from = shown.indexOf(album.bucketId)
        if (from < 0) return
        shown.removeAt(from)
        val to = if (toTop) 0 else (from + steps).coerceIn(0, shown.size)
        shown.add(to, album.bucketId)
        _albumOrder.value = shown
        albumOrderPrefs.edit().putString("order", shown.joinToString("\n")).apply()
        setAlbumSort(AlbumSort.CUSTOM)
    }

    /** The album list arranged by dragging: [order] is every album's bucket id, in its new place. */
    fun reorderAlbums(order: List<String>) {
        // Dragged while the list showed only one storage: the albums that were filtered out keep their
        // places, and the dragged ones are slotted back into the places they occupied.
        val shown = rootAlbums.value.map { it.bucketId }
        val merged = if (order.size < shown.size) {
            val slots = shown.withIndex().filter { it.value in order }.map { it.index }
            shown.toMutableList().apply { slots.forEachIndexed { i, slot -> this[slot] = order[i] } }
        } else {
            order
        }
        _albumOrder.value = merged
        albumOrderPrefs.edit().putString("order", merged.joinToString("\n")).apply()
        setAlbumSort(AlbumSort.CUSTOM)
    }

    /** The album list shows only this storage's albums; null shows all. Not persisted - it is a quick look. */
    private val _albumStorageFilter = MutableStateFlow<com.example.amoledgallery.util.StorageKind?>(null)
    val albumStorageFilter: StateFlow<com.example.amoledgallery.util.StorageKind?> = _albumStorageFilter.asStateFlow()

    fun setAlbumStorageFilter(kind: com.example.amoledgallery.util.StorageKind?) {
        _albumStorageFilter.value = kind
    }

    /** SD card or USB storage, whichever "move to..." would write to (SD card when both are plugged in). */
    val externalTargetKind: com.example.amoledgallery.util.StorageKind
        get() = repository.externalTarget()?.kind ?: com.example.amoledgallery.util.StorageKind.SD_CARD

    /** Free space on internal storage and on every mounted SD card / USB drive, for the move screens. */
    fun freeByStorage(): Map<com.example.amoledgallery.util.StorageKind, Long> = buildMap {
        put(com.example.amoledgallery.util.StorageKind.INTERNAL, repository.getInternalStorageSpace().freeBytes)
        repository.externalVolumes().forEach { volume ->
            // Two cards of the same kind would be rare; the one with less room decides.
            val free = volume.freeBytes()
            put(volume.kind, minOf(get(volume.kind) ?: Long.MAX_VALUE, free))
        }
    }

    /** What an album holds altogether, in bytes (read when a move dialog opens). */
    suspend fun albumBytes(album: AlbumItem): Long = withContext(Dispatchers.IO) {
        repository.getAllMedia(album.bucketId, includeHidden = true).sumOf { it.size }
    }

    // Trashed media items
    private val _trashedMediaState = MutableStateFlow<List<MediaItem>>(emptyList())
    val trashedMediaState: StateFlow<List<MediaItem>> = _trashedMediaState.asStateFlow()

    // Persistent Favorites system
    private val favPrefs = application.getSharedPreferences("amoled_gallery_fav_prefs", Context.MODE_PRIVATE)
    private val KEY_FAVORITES = "favorite_media_ids"

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    // Favorited media items stream derived from loaded media and favorite IDs
    val favoriteMediaState: StateFlow<List<MediaItem>> = combine(_rawMedia, _favoriteIds) { media, favIds ->
        media.filter { it.id in favIds }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Grid column count: 2 to 5 (OneUI style pinch-to-zoom, fixed 5 columns as default)
    private val _columnCount = MutableStateFlow(
        when (com.example.amoledgallery.AmoledGalleryApp.performanceProfile) {
            com.example.amoledgallery.PerformanceProfile.LOW,
            com.example.amoledgallery.PerformanceProfile.MEDIUM -> 4
            com.example.amoledgallery.PerformanceProfile.HIGH,
            com.example.amoledgallery.PerformanceProfile.ULTRA -> 5
        }
    )
    val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    // Fullscreen media detail state
    private val _selectedItemIndex = MutableStateFlow<Int?>(null)
    val selectedItemIndex: StateFlow<Int?> = _selectedItemIndex.asStateFlow()

    private val _detailSource = MutableStateFlow(DetailSource.ALL)
    val detailSource: StateFlow<DetailSource> = _detailSource.asStateFlow()

    // Multi-Selection State
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /** What the selected items take altogether, in bytes. */
    val selectionBytes: StateFlow<Long> = combine(_selectedIds, _rawMedia) { ids, media ->
        media.filter { it.id in ids }.sumOf { it.size }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)


    // Move to album bottom sheet state
    private val _isMoveSheetOpen = MutableStateFlow(false)
    val isMoveSheetOpen: StateFlow<Boolean> = _isMoveSheetOpen.asStateFlow()

    // Hidden albums state
    private val _hiddenAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val hiddenAlbums: StateFlow<List<AlbumItem>> = _hiddenAlbums.asStateFlow()

    // Trigger IntentSender for user authorization dialogs (Trash, Delete, Write)
    private val _pendingIntentSender = MutableSharedFlow<PendingIntent>()
    val pendingIntentSender: SharedFlow<PendingIntent> = _pendingIntentSender.asSharedFlow()

    // Feedback messages (Snackbar/Toast)
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // Temporary storage for pending rename action awaiting write permission
    private var pendingRename: PendingRenameData? = null

    // Temporary storage for pending move action awaiting write permission
    private var pendingMove: PendingMoveData? = null

    // Album rename whose items of other apps await write permission
    private var pendingAlbumRename: AlbumRenameOutcome? = null

    // Album deletion whose items await the confirmation of the trash request
    private var pendingAlbumDelete: AlbumItem? = null

    // The running media reload; a newer reload request replaces it instead of scanning in parallel.
    private var loadJob: Job? = null

    /**
     * Fires whenever MediaStore changes, so a photo or video added while the app is open shows up by
     * itself - a download that finishes in the background used to stay invisible until the app was
     * reopened. Emissions are collapsed: a move that touches hundreds of files causes one reload.
     */
    private val mediaChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    private val mediaObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            mediaChanges.tryEmit(Unit)
        }
    }

    // Move Progress state
    data class MoveProgress(val isMoving: Boolean = false, val current: Int = 0, val total: Int = 0)
    val moveProgress = MutableStateFlow(MoveProgress())

    // MicroSD Card detection
    val isSdCardAvailable: Boolean get() = repository.isSdCardAvailable()

    // Storage free/total space, queried fresh each time the settings dialog reads them
    fun getInternalStorageSpace() = repository.getInternalStorageSpace()
    fun getSdCardStorageSpace() = repository.getSdCardStorageSpace()

    /** Space used by photos, videos, the trash and each album (iOS storage page). */
    suspend fun loadStorageStats(): MediaRepository.MediaStorageStats = repository.getMediaStorageStats()

    // Manual Graphics Quality tier (Low/Medium/High/Ultra), like a game's video settings
    private val _graphicsQuality = MutableStateFlow(com.example.amoledgallery.AmoledGalleryApp.performanceProfile)
    val graphicsQuality: StateFlow<com.example.amoledgallery.PerformanceProfile> = _graphicsQuality.asStateFlow()

    fun setGraphicsQuality(quality: com.example.amoledgallery.PerformanceProfile) {
        _graphicsQuality.value = quality
        ThumbnailManager.getInstance(getApplication()).quality = quality
        com.example.amoledgallery.AmoledGalleryApp.applyGraphicsQuality(getApplication(), quality)
    }

    init {
        com.example.amoledgallery.ui.features.AlbumExtras.init(application)
        loadFavorites()
        ThumbnailManager.getInstance(application).quality = _graphicsQuality.value

        // Everything under content://media, so changes on the SD card and on USB storage count too.
        application.contentResolver.registerContentObserver(
            android.provider.MediaStore.AUTHORITY_URI, true, mediaObserver
        )
        viewModelScope.launch {
            mediaChanges.collect {
                // Let a burst of changes settle, and stay out of the way of a running move, which
                // reloads by itself once it is through.
                kotlinx.coroutines.delay(1000)
                if (!moveProgress.value.isMoving) {
                    // A file another app writes straight to disk (a download, a file manager) becomes a
                    // "pending" row in MediaStore, which no other app is allowed to see - that is why a
                    // downloaded video could sit there for hours while the file manager showed it all
                    // along. Scanning finalises the row; the scan's own notification then brings it in.
                    withContext(Dispatchers.IO) { repository.rescanMediaFolders(minIntervalMs = 20_000L) }
                    loadMedia()
                    _selectedAlbum.value?.let { loadAlbumContents(it) }
                }
            }
        }

        // Listen for MediaStore Scoped Storage write permission requests and progress from MoveMediaService
        viewModelScope.launch {
            MoveProgressHolder.progress.collect { progress ->
                if (progress.isRunning) {
                    moveProgress.value = MoveProgress(isMoving = true, current = progress.current, total = progress.total)
                } else if (progress.isCompleted || progress.error != null) {
                    moveProgress.value = MoveProgress(isMoving = false)
                }

                val intent = progress.permissionRequiredIntent
                val targetAlbum = progress.pendingTargetAlbum
                if (intent != null && targetAlbum != null) {
                    pendingMove = PendingMoveData(progress.pendingItemsToMove, targetAlbum, progress.pendingOriginalsToDelete)
                    _pendingIntentSender.emit(intent)
                }
            }
        }
    }

    private fun loadFavorites() {
        val stringSet = favPrefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        _favoriteIds.value = stringSet.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggleFavorite(id: Long) {
        val current = _favoriteIds.value
        val isNowFav = id !in current
        val newSet = if (isNowFav) current + id else current - id
        _favoriteIds.value = newSet
        favPrefs.edit().putStringSet(KEY_FAVORITES, newSet.map { it.toString() }.toSet()).apply()
        viewModelScope.launch {
            _userMessage.emit(if (isNowFav) strings.favoriteAdded else strings.favoriteRemoved)
        }
    }

    fun updatePermissionState(hasPermission: Boolean) {
        if (hasPermission) {
            loadMedia()
        } else {
            _uiState.value = GalleryUiState.PermissionRequired
        }
    }

    fun selectTab(tab: GalleryTab) {
        _selectedTab.value = tab
        closeAlbum()
        clearSelection()
        _isTrashOpen.value = false
    }

    /**
     * Updates the media filter and re-computes the visible list.
     */
    fun selectFilter(filter: MediaFilter) {
        _selectedFilter.value = filter
        applyFilter()
    }

    private fun applyFilter() {
        val raw = _rawMedia.value.let { media ->
            if (cameraOnly.value) {
                media.filter { it.relativePath.startsWith("DCIM/", ignoreCase = true) && !it.relativePath.contains("screenshot", ignoreCase = true) }
            } else {
                media
            }
        }

        val filtered = filterMediaList(raw, _selectedFilter.value)
        _uiState.value = if (filtered.isEmpty()) {
            GalleryUiState.Empty
        } else {
            GalleryUiState.Success(filtered)
        }
    }

    private fun filterMediaList(items: List<MediaItem>, filter: MediaFilter): List<MediaItem> {
        return when (filter) {
            MediaFilter.ALL -> items
            MediaFilter.IMAGES -> items.filter { it.isImage }
            MediaFilter.VIDEOS -> items.filter { it.isVideo }
            MediaFilter.GIFS -> items.filter { it.isGif }
        }
    }

    // --- Multi-Selection Mode ---

    fun startSelection(initialId: Long? = null) {
        _isSelectionMode.value = true
        _selectedIds.value = if (initialId != null) setOf(initialId) else emptySet()
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (current.contains(id)) {
            current - id
        } else {
            current + id
        }
    }

    fun selectAll(items: List<MediaItem>) {
        _selectedIds.value = items.map { it.id }.toSet()
    }

    fun deselectAll() {
        _selectedIds.value = emptySet()
    }

    fun setItemSelection(id: Long, selected: Boolean) {
        val current = _selectedIds.value
        if (selected && !current.contains(id)) {
            _selectedIds.value = current + id
        } else if (!selected && current.contains(id)) {
            _selectedIds.value = current - id
        }
    }

    fun setItemSelections(ids: Collection<Long>, selected: Boolean) {
        if (ids.isEmpty()) return
        val current = _selectedIds.value
        _selectedIds.value = if (selected) {
            current + ids
        } else {
            current - ids.toSet()
        }
    }

    fun setSelectedIds(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    /**
     * Batch trashes all currently selected media items using MediaStore.createTrashRequest.
     */
    fun trashSelectedMedia() {
        viewModelScope.launch {
            val selected = _selectedIds.value
            if (selected.isEmpty()) return@launch

            val allItems = if (_selectedAlbum.value != null) {
                _albumMediaState.value
            } else {
                _rawMedia.value
            }

            val itemsToTrash = allItems.filter { it.id in selected }
            if (itemsToTrash.isEmpty()) return@launch

            val uris = itemsToTrash.map { it.uri }
            val pendingIntent = withContext(Dispatchers.IO) { repository.createTrashRequest(uris, trash = true) }
            if (pendingIntent != null) {
                _pendingIntentSender.emit(pendingIntent)
            } else {
                _userMessage.emit(strings.movedToTrash(itemsToTrash.size))
                clearSelection()
                loadMedia()
                loadTrash()
                _selectedAlbum.value?.let { loadAlbumContents(it) }
            }
        }
    }

    fun clearSelection() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
        _isMoveSheetOpen.value = false
    }

    // --- Move to Album ---

    fun openMoveSheet() {
        if (_selectedIds.value.isNotEmpty()) {
            _isMoveSheetOpen.value = true
        }
    }

    fun closeMoveSheet() {
        _isMoveSheetOpen.value = false
    }

    fun createAlbumAndMove(albumName: String, onSdCard: Boolean) {
        viewModelScope.launch {
            closeMoveSheet()
            val result = withContext(Dispatchers.IO) { repository.createAlbumFolder(albumName, onSdCard) }
            result.onSuccess { dir ->
                val targetLocation = onSdCard
                _userMessage.emit(strings.albumCreated(dir.name, targetLocation))
                loadAlbums()
                val newAlbum = AlbumItem(
                    bucketId = "",
                    name = dir.name,
                    count = 0,
                    isSdCard = onSdCard,
                    directoryPath = dir.absolutePath,
                    relativePath = "Pictures/${dir.name}/"
                )
                moveSelectedItems(newAlbum)
            }.onFailure { err ->
                _userMessage.emit(strings.createError(err.localizedMessage))
            }
        }
    }

    fun moveSelectedItems(targetAlbum: AlbumItem) {
        viewModelScope.launch(Dispatchers.IO) {
            closeMoveSheet()
            val selected = _selectedIds.value
            if (selected.isEmpty()) {
                _userMessage.emit(strings.noItemsSelected)
                return@launch
            }

            val allItems = (_rawMedia.value + _albumMediaState.value).distinctBy { it.id }
            val itemsToMove = allItems.filter { it.id in selected }
            if (itemsToMove.isEmpty()) {
                _userMessage.emit(strings.noItemsToMove)
                return@launch
            }

            clearSelection()
            
            _userMessage.emit(strings.moveStarted(targetAlbum.name))
            
            moveProgress.value = MoveProgress(isMoving = true, current = 0, total = itemsToMove.size)

            var lastUpdate = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                repository.moveMediaItems(itemsToMove, targetAlbum) { current, totalCount ->
                    val now = System.currentTimeMillis()
                    if (current == totalCount || now - lastUpdate >= 200 || current % maxOf(1, totalCount / 20) == 0) {
                        // Local moveProgress only - deliberately NOT mirrored into the global
                        // MoveProgressHolder (that singleton is for the actual MoveMediaService
                        // foreground-service path only). Writing to both created a race where the
                        // init{} collector below could clobber the final isMoving=false with a
                        // stale isRunning=true from a delayed emission, freezing the progress dialog.
                        moveProgress.value = moveProgress.value.copy(current = current, total = totalCount)
                        lastUpdate = now
                    }
                }
            }

            moveProgress.value = moveProgress.value.copy(isMoving = false)

            when (result) {
                is MoveResult.Success -> _userMessage.emit(strings.movedItems(result.count, targetAlbum.name))
                // Items of other apps: ask once for write access, onIntentSenderSuccess finishes the move.
                is MoveResult.PermissionRequired -> {
                    pendingMove = PendingMoveData(result.itemsToMove, targetAlbum, result.originalsToDelete)
                    _pendingIntentSender.emit(result.pendingIntent)
                }
                is MoveResult.Error -> _userMessage.emit(strings.error(result.message))
            }

            loadMedia()
            _selectedAlbum.value?.let { loadAlbumContents(it) }
        }
    }

    // --- Hidden Albums ---

    fun hideAlbum(album: AlbumItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.setAlbumHidden(album.bucketId, true, album.directoryPath) }
            _userMessage.emit(strings.albumHidden(album.name))
            // loadMedia also rebuilds the visible and the hidden album lists.
            loadMedia()
        }
    }

    fun unhideAlbum(bucketId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.setAlbumHidden(bucketId, false) }
            _userMessage.emit(strings.albumUnhidden)
            loadMedia()
        }
    }

    // --- Album Navigation ---

    /**
     * Opens an album and adds it to the breadcrumb stack.
     */
    fun openAlbum(album: AlbumItem) {
        clearSelection()
        val currentStack = _breadcrumbStack.value
        _breadcrumbStack.value = currentStack + album
        _selectedAlbum.value = album
        loadAlbumContents(album)
    }

    /**
     * Navigates back to a specific breadcrumb level (0-based index).
     * Index -1 resets back to root ("Alben").
     */
    fun navigateToBreadcrumb(index: Int) {
        clearSelection()
        if (index < 0 || index >= _breadcrumbStack.value.size) {
            closeAlbum()
            return
        }
        val newStack = _breadcrumbStack.value.take(index + 1)
        _breadcrumbStack.value = newStack
        val targetAlbum = newStack.last()
        _selectedAlbum.value = targetAlbum
        loadAlbumContents(targetAlbum)
    }

    /**
     * Pops one level from the breadcrumb stack.
     * Returns true if popped, false if already at root.
     */
    fun popAlbumBreadcrumb(): Boolean {
        clearSelection()
        val stack = _breadcrumbStack.value
        return if (stack.isNotEmpty()) {
            if (stack.size == 1) {
                closeAlbum()
            } else {
                navigateToBreadcrumb(stack.size - 2)
            }
            true
        } else {
            false
        }
    }

    /** An edited copy was saved: refresh the grid, the albums and the open album's contents. */
    fun refreshAfterEdit() {
        loadMedia()
        _selectedAlbum.value?.let { loadAlbumContents(it) }
    }

    private fun loadAlbumContents(album: AlbumItem) {
        viewModelScope.launch {
            val media = withContext(Dispatchers.IO) { repository.getAllMedia(album.bucketId) }
            val subAlbums = withContext(Dispatchers.IO) { repository.getSubAlbums(album, _albumsState.value) }
            // Sub-albums first, together with the media: arriving after the media, their rows were inserted
            // above the photos and the grid stayed anchored on the first photo, hiding the sub-albums.
            _currentSubAlbums.value = subAlbums
            _albumMediaState.value = media
            _loadedAlbumKey.value = album.bucketId
        }
    }

    fun closeAlbum() {
        clearSelection()
        _breadcrumbStack.value = emptyList()
        _selectedAlbum.value = null
        _albumMediaState.value = emptyList()
        _currentSubAlbums.value = emptyList()
    }

    fun openTrash() {
        clearSelection()
        _isTrashOpen.value = true
        loadTrash()
    }

    fun closeTrash() {
        _isTrashOpen.value = false
    }

    // ---- Duplicates --------------------------------------------------------------------------------

    fun openDuplicates() {
        clearSelection()
        _isDuplicatesOpen.value = true
        if (_duplicateGroups.value.isEmpty() && !_duplicatesScanned.value) scanDuplicates()
    }

    fun closeDuplicates() {
        _isDuplicatesOpen.value = false
        cancelDuplicateScan()
    }

    /**
     * Looks through every photo and video for files that are byte for byte the same.
     *
     * Hidden albums are searched too: a copy there is still a copy, and leaving them out would report
     * space as free that is not.
     */
    fun scanDuplicates() {
        duplicateJob?.cancel()
        _duplicateSelection.value = emptySet()
        _duplicateGroups.value = emptyList()
        _duplicatesScanned.value = false
        duplicateJob = viewModelScope.launch {
            try {
                val media = withContext(Dispatchers.IO) { repository.getAllMedia(includeHidden = true) }
                _duplicateProgress.value = com.example.amoledgallery.data.DuplicateProgress(0, media.size)
                val found = duplicateFinder.find(media) { progress -> _duplicateProgress.value = progress }
                _duplicateGroups.value = found
                // Everything but the newest copy of each set is ticked, which is what is wanted nearly
                // always; the ticks can be changed before anything is deleted.
                _duplicateSelection.value = found.flatMap { group -> group.items.drop(1).map { it.id } }.toSet()
                _duplicatesScanned.value = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                // Leaving the screen stops the scan; nothing is reported, and it starts afresh next time.
                throw cancelled
            } catch (e: Exception) {
                _duplicatesScanned.value = true
            } finally {
                _duplicateProgress.value = null
            }
        }
    }

    fun cancelDuplicateScan() {
        duplicateJob?.cancel()
        duplicateJob = null
        _duplicateProgress.value = null
    }

    fun toggleDuplicateSelection(id: Long) {
        val current = _duplicateSelection.value
        _duplicateSelection.value = if (id in current) current - id else current + id
    }

    /** How much deleting the ticked copies would free. */
    fun duplicateSelectionBytes(): Long {
        val selected = _duplicateSelection.value
        return _duplicateGroups.value
            .flatMap { it.items }
            .filter { it.id in selected }
            .sumOf { it.size }
    }

    /** Moves the ticked copies to the trash; the originals they duplicate stay where they are. */
    fun trashDuplicateSelection() {
        viewModelScope.launch {
            val selected = _duplicateSelection.value
            if (selected.isEmpty()) return@launch
            val items = _duplicateGroups.value.flatMap { it.items }.filter { it.id in selected }
            if (items.isEmpty()) return@launch
            pendingDuplicateTrash = items.map { it.id }.toSet()
            val pendingIntent = withContext(Dispatchers.IO) {
                repository.createTrashRequest(items.map { it.uri }, trash = true)
            }
            if (pendingIntent != null) {
                _pendingIntentSender.emit(pendingIntent)
            } else {
                pendingDuplicateTrash = null
                _userMessage.emit(strings.movedToTrash(items.size))
                dropTrashedDuplicates(items.map { it.id }.toSet())
                loadMedia()
                loadTrash()
            }
        }
    }

    /** Ids awaiting the user's confirmation of the trash request, so the list can be pruned after it. */
    private var pendingDuplicateTrash: Set<Long>? = null

    /** Takes the deleted copies out of the groups, dropping any set that no longer has a double. */
    private fun dropTrashedDuplicates(ids: Set<Long>) {
        _duplicateGroups.value = _duplicateGroups.value.mapNotNull { group ->
            val left = group.items.filterNot { it.id in ids }
            if (left.size < 2) null else group.copy(items = left, wastedBytes = left.first().size * (left.size - 1))
        }
        _duplicateSelection.value = _duplicateSelection.value - ids
    }

    // Tiles per row of the album list and of the sub-albums inside an album; null = the design's default.
    private val gridPrefs = application.getSharedPreferences("amoled_gallery_grid_prefs", Context.MODE_PRIVATE)
    private val _albumColumns = MutableStateFlow(gridPrefs.getInt("album_columns", 0).takeIf { it > 0 })
    val albumColumns: StateFlow<Int?> = _albumColumns.asStateFlow()
    private val _subAlbumColumns = MutableStateFlow(gridPrefs.getInt("sub_album_columns", 0).takeIf { it > 0 })
    val subAlbumColumns: StateFlow<Int?> = _subAlbumColumns.asStateFlow()

    fun setAlbumColumns(columns: Int) {
        _albumColumns.value = columns.coerceIn(2, 5)
        gridPrefs.edit().putInt("album_columns", columns.coerceIn(2, 5)).apply()
    }

    fun setSubAlbumColumns(columns: Int) {
        _subAlbumColumns.value = columns.coerceIn(2, 5)
        gridPrefs.edit().putInt("sub_album_columns", columns.coerceIn(2, 5)).apply()
    }

    /** Switches straight to another top-level album (One UI's album drawer), replacing the breadcrumbs. */
    fun openRootAlbum(album: AlbumItem) {
        clearSelection()
        _breadcrumbStack.value = listOf(album)
        _selectedAlbum.value = album
        loadAlbumContents(album)
    }

    /**
     * Opens [album] directly, however deep it sits - a search result found by name, not by tapping
     * through its parents. The breadcrumb trail is rebuilt from its actual ancestors, so the back
     * button and the breadcrumbs above the album still walk out through them normally.
     */
    fun jumpToAlbum(album: AlbumItem) {
        clearSelection()
        val ancestors = repository.ancestorsOf(album, _albumsState.value)
        _breadcrumbStack.value = ancestors + album
        _selectedAlbum.value = album
        loadAlbumContents(album)
    }

    fun zoomIn() {
        // Pinching out -> fewer columns (larger tiles)
        if (_columnCount.value > 2) {
            _columnCount.value -= 1
        }
    }

    fun zoomOut() {
        // Pinching in -> more columns (smaller tiles); 6 and 7 columns are the year view.
        if (_columnCount.value < 7) {
            _columnCount.value += 1
        }
    }

    /** Jumps straight to a zoom level: 3 = days, 5 = months, 7 = years. */
    fun setColumnCount(columns: Int) {
        _columnCount.value = columns.coerceIn(2, 7)
    }

    /** Photos tab shows only what the camera took (DCIM, without screenshots), as Xiaomi's gallery can. */
    private val viewPrefs = application.getSharedPreferences("amoled_gallery_view_prefs", Context.MODE_PRIVATE)
    val cameraOnly = MutableStateFlow(viewPrefs.getBoolean("camera_only", false))

    fun setCameraOnly(on: Boolean) {
        cameraOnly.value = on
        viewPrefs.edit().putBoolean("camera_only", on).apply()
        applyFilter()
    }

    fun loadMedia() {
        // A full MediaStore scan takes a while on large libraries. Callers often ask for several
        // refreshes at once (media + albums after a move), so a new request replaces a running one.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Only blank the screen out for the very first load. Every later refresh (after a
            // delete/move, or simply on app resume) keeps the current list on screen until the new
            // one is ready - otherwise the list momentarily becomes empty, which flashes the grid
            // and made the fullscreen viewer close itself mid-delete because it treats an empty
            // list as "nothing left to show".
            if (_uiState.value !is GalleryUiState.Success) {
                _uiState.value = GalleryUiState.Loading
            }
            withContext(Dispatchers.IO) {
                // Single MediaStore scan (images + videos) reused to derive the main feed,
                // the visible albums and the hidden albums - avoids 3-4x redundant re-queries.
                val allMediaIncludingHidden = repository.getAllMedia(includeHidden = true)
                val hiddenBuckets = repository.getHiddenBucketIds()
                val visibleMedia = if (hiddenBuckets.isEmpty()) {
                    allMediaIncludingHidden
                } else {
                    allMediaIncludingHidden.filter { it.bucketId !in hiddenBuckets }
                }

                val hiddenAlbums = if (hiddenBuckets.isEmpty()) {
                    emptyList()
                } else {
                    repository.groupIntoAlbums(allMediaIncludingHidden, includeHidden = true)
                        .filter { it.bucketId in hiddenBuckets }
                }
                // Before the visible list is built, so an album hidden before its folder was recorded
                // does not come back as an empty copy of itself even on this very first pass.
                repository.rememberHiddenAlbumDirs(hiddenAlbums)

                val visibleAlbums = repository.groupIntoAlbums(visibleMedia, includeHidden = false)

                _rawMedia.value = visibleMedia
                _albumsState.value = visibleAlbums
                _hiddenAlbums.value = hiddenAlbums
                ThumbnailManager.getInstance(getApplication()).startPreindexing(visibleMedia)
            }
            applyFilter()
            openPendingAlbum()
            _selectedAlbum.value?.let { active ->
                _currentSubAlbums.value = withContext(Dispatchers.IO) { repository.getSubAlbums(active, _albumsState.value) }
            }
            loadTrash()
            loadFavorites()
        }
    }

    /** Albums are derived from the same scan as the media feed, so this is a full [loadMedia]. */
    fun loadAlbums() = loadMedia()

    fun loadTrash() {
        viewModelScope.launch {
            val trashed = withContext(Dispatchers.IO) { repository.getTrashedMedia() }
            _trashedMediaState.value = trashed
        }
    }

    fun openDetail(index: Int, source: DetailSource = DetailSource.ALL) {
        if (_isSelectionMode.value) return
        _detailSource.value = source
        _selectedItemIndex.value = index
    }

    /**
     * Keeps the stored detail index in step with the page the user actually swiped to, so that
     * deleting from the fullscreen viewer removes the visible item and leaves the index pointing
     * at what is now the next one.
     */
    fun updateDetailIndex(index: Int) {
        if (_selectedItemIndex.value != null && _selectedItemIndex.value != index) {
            _selectedItemIndex.value = index
        }
    }

    fun closeDetail() {
        _selectedItemIndex.value = null
    }

    /**
     * Moves media to MediaStore Trash.
     */
    fun trashMedia(item: MediaItem) {
        viewModelScope.launch {
            val pendingIntent = withContext(Dispatchers.IO) { repository.createTrashRequest(listOf(item.uri), trash = true) }
            if (pendingIntent != null) {
                _pendingIntentSender.emit(pendingIntent)
            } else {
                _userMessage.emit(strings.itemTrashed)
                loadMedia()
            }
        }
    }

    /**
     * Restores media from MediaStore Trash.
     */
    fun restoreMedia(item: MediaItem) {
        viewModelScope.launch {
            val pendingIntent = withContext(Dispatchers.IO) { repository.createTrashRequest(listOf(item.uri), trash = false) }
            if (pendingIntent != null) {
                _pendingIntentSender.emit(pendingIntent)
            } else {
                _userMessage.emit(strings.restored)
                loadTrash()
                loadMedia()
            }
        }
    }

    /**
     * Restores all media items from the trash.
     */
    fun restoreAllTrash() {
        viewModelScope.launch {
            val uris = _trashedMediaState.value.map { it.uri }
            if (uris.isEmpty()) return@launch
            val pendingIntent = withContext(Dispatchers.IO) { repository.createTrashRequest(uris, trash = false) }
            if (pendingIntent != null) {
                _pendingIntentSender.emit(pendingIntent)
            } else {
                _userMessage.emit(strings.allRestored)
                loadTrash()
                loadMedia()
            }
        }
    }

    /**
     * Permanently deletes a media item from MediaStore.
     */
    fun deletePermanently(item: MediaItem) {
        viewModelScope.launch {
            val pendingIntent = withContext(Dispatchers.IO) { repository.createDeleteRequest(listOf(item.uri)) }
            if (pendingIntent != null) {
                _pendingIntentSender.emit(pendingIntent)
            } else {
                _userMessage.emit(strings.deletedPermanently)
                loadTrash()
                loadMedia()
            }
        }
    }

    /**
     * Permanently deletes all items in trash.
     */
    fun emptyTrash() {
        viewModelScope.launch {
            val uris = _trashedMediaState.value.map { it.uri }
            if (uris.isEmpty()) return@launch
            val pendingIntent = withContext(Dispatchers.IO) { repository.createDeleteRequest(uris) }
            if (pendingIntent != null) {
                _pendingIntentSender.emit(pendingIntent)
            } else {
                _userMessage.emit(strings.trashEmptied)
                loadTrash()
                loadMedia()
            }
        }
    }

    /**
     * Renames media item by updating MediaStore.MediaColumns.DISPLAY_NAME.
     */
    fun renameMedia(item: MediaItem, newName: String) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.renameMedia(item.uri, item.displayName, newName) }) {
                is RenameResult.Success -> {
                    _userMessage.emit(strings.fileRenamed(result.newName))
                    loadMedia()
                    // Reload in place: openAlbum would push the album onto the breadcrumbs a second time.
                    _selectedAlbum.value?.let { loadAlbumContents(it) }
                }
                is RenameResult.PermissionRequired -> {
                    pendingRename = PendingRenameData(item.uri, item.displayName, result.targetName)
                    _pendingIntentSender.emit(result.pendingIntent)
                }
                is RenameResult.Error -> {
                    _userMessage.emit(strings.error(result.message))
                }
            }
        }
    }

    /**
     * Called when the system ActivityResultLauncher completes with RESULT_OK.
     */
    /** The user declined a write, trash or delete request: nothing of it may carry over to the next one. */
    fun onIntentSenderCancelled() {
        pendingWriteAction = null
        pendingCancelAction?.let { cancel -> viewModelScope.launch(Dispatchers.IO) { cancel() } }
        pendingCancelAction = null
        pendingRename = null
        pendingMove = null
        pendingAlbumRename = null
        pendingAlbumDelete = null
        pendingDuplicateTrash = null
    }

    fun onIntentSenderSuccess() {
        viewModelScope.launch {
            pendingWriteAction?.let { action ->
                pendingWriteAction = null
                pendingCancelAction = null
                try {
                    withContext(Dispatchers.IO) { action() }
                } catch (e: Exception) {
                    _userMessage.emit(com.example.amoledgallery.ui.i18n.F.failed)
                }
                kotlinx.coroutines.delay(300)
                refreshAfterEdit()
                loadTrash()
                return@launch
            }
            val rename = pendingRename
            val move = pendingMove
            val albumRename = pendingAlbumRename
            val albumDelete = pendingAlbumDelete

            if (albumDelete != null) {
                pendingAlbumDelete = null
                // MediaStore commits the trashed rows shortly after RESULT_OK.
                kotlinx.coroutines.delay(300)
                withContext(Dispatchers.IO) { repository.finishAlbumDeletion(albumDelete) }
                _userMessage.emit(strings.albumDeleted(albumDelete.name))
                if (_selectedAlbum.value?.bucketId == albumDelete.bucketId) {
                    closeAlbum()
                }
                loadTrash()
            } else if (albumRename != null) {
                pendingAlbumRename = null
                val stillDenied = repository.finishAlbumRename(albumRename)
                if (stillDenied.isNotEmpty()) {
                    _userMessage.emit(strings.error(strings.permissionDenied))
                } else {
                    _userMessage.emit(strings.albumRenamed(albumRename.relativePath.trim('/').substringAfterLast('/')))
                }
                clearSelection()
                loadMedia()
                followRenamedAlbum(albumRename)
                return@launch
            } else if (rename != null) {
                pendingRename = null
                when (val result = withContext(Dispatchers.IO) { repository.renameMedia(rename.uri, rename.oldName, rename.targetName) }) {
                    is RenameResult.Success -> {
                        _userMessage.emit(strings.fileRenamed(result.newName))
                    }
                    is RenameResult.Error -> {
                        _userMessage.emit(strings.error(result.message))
                    }
                    is RenameResult.PermissionRequired -> Unit
                }
            } else if (move != null) {
                pendingMove = null
                // Originals that were already copied to the other volume only need to be deleted now.
                val deletedOriginals = if (move.originalsToDelete.isNotEmpty()) {
                    repository.deleteMedia(move.originalsToDelete)
                } else {
                    0
                }
                if (move.itemsToMove.isNotEmpty()) {
                    MoveMediaService.start(
                        context = getApplication(),
                        items = move.itemsToMove,
                        targetAlbum = move.targetAlbum,
                        onCompletion = {
                            loadMedia()
                            _selectedAlbum.value?.let { loadAlbumContents(it) }
                            viewModelScope.launch {
                                _userMessage.emit(strings.movedItems(move.itemsToMove.size + deletedOriginals, move.targetAlbum.name))
                            }
                        }
                    )
                } else {
                    _userMessage.emit(strings.movedItems(deletedOriginals, move.targetAlbum.name))
                }
                clearSelection()
            } else {
                _userMessage.emit(strings.actionSucceeded)
                // Covers Trash/Delete/Album-migration write requests: MediaStore commits the
                // underlying row changes shortly after RESULT_OK, so a brief delay avoids
                // refreshing on stale data (e.g. a just-migrated album still showing its old location).
                kotlinx.coroutines.delay(300)
                // Copies the user had ticked in the duplicates list are gone now: take them out of it
                // rather than leaving sets behind that have nothing left to compare.
                pendingDuplicateTrash?.let { trashed ->
                    pendingDuplicateTrash = null
                    dropTrashedDuplicates(trashed)
                    loadTrash()
                }
            }

            clearSelection()
            loadMedia()
            // Reload in place: openAlbum would push the album onto the breadcrumbs a second time.
            _selectedAlbum.value?.let { loadAlbumContents(it) }
        }
    }

    /**
     * Creates a new album folder.
     */
    fun createAlbum(albumName: String, onSdCard: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.createAlbumFolder(albumName, onSdCard) }
            result.onSuccess { dir ->
                val targetLocation = onSdCard
                _userMessage.emit(strings.albumCreated(dir.name, targetLocation))
                loadAlbums()
            }.onFailure { err ->
                _userMessage.emit(strings.createError(err.localizedMessage))
            }
        }
    }

    /**
     * Creates a new sub-album folder inside [parentAlbum].
     */
    fun createSubAlbum(parentAlbum: AlbumItem, subAlbumName: String) {
        viewModelScope.launch {
            val result = repository.createSubAlbumFolder(parentAlbum, subAlbumName)
            result.onSuccess { dir ->
                val location = parentAlbum.isSdCard
                _userMessage.emit(strings.subAlbumCreated(dir.name, parentAlbum.name, location))
                loadAlbums()
                loadAlbumContents(parentAlbum)
            }.onFailure { err ->
                _userMessage.emit(strings.createError(err.localizedMessage))
            }
        }
    }

    /**
     * Renames an album folder physically and updates MediaStore.
     */
    fun renameAlbum(album: AlbumItem, newName: String) {
        viewModelScope.launch {
            val result = repository.renameAlbum(album, newName)
            result.onSuccess { outcome ->
                val pendingIntent = outcome.pendingIntent
                if (pendingIntent != null) {
                    // Photos of other apps can only be moved to the new folder with the user's consent.
                    pendingAlbumRename = outcome
                    _pendingIntentSender.emit(pendingIntent)
                    return@onSuccess
                }
                _userMessage.emit(strings.albumRenamed(newName))
                loadMedia()
                followRenamedAlbum(outcome)
            }.onFailure { err ->
                _userMessage.emit(strings.renameError(err.localizedMessage))
            }
        }
    }

    /**
     * A renamed album has a new folder and with it a new id. Where it is open - or one of its sub-albums is - the
     * trail is rebuilt from the reloaded albums; it used to keep the old id, which showed an empty album.
     */
    private suspend fun followRenamedAlbum(outcome: AlbumRenameOutcome) {
        val open = _selectedAlbum.value ?: return
        val old = outcome.oldDirectoryPath.trimEnd('/')
        val openDir = open.directoryPath.trimEnd('/')
        if (old.isBlank() || !(openDir.equals(old, ignoreCase = true) || openDir.startsWith("$old/", ignoreCase = true))) {
            loadAlbumContents(open)
            return
        }
        loadJob?.join()
        val newRel = outcome.relativePath.trim('/') + openDir.substring(old.length)
        val updated = _albumsState.value.firstOrNull { it.relativePath.trim('/').equals(newRel, ignoreCase = true) }
        if (updated == null) {
            closeAlbum()
        } else {
            _breadcrumbStack.value = repository.ancestorsOf(updated, _albumsState.value) + updated
            _selectedAlbum.value = updated
            loadAlbumContents(updated)
        }
    }

    /**
     * Deletes an album by moving its media to Trash and removing the folder.
     */
    fun deleteAlbum(album: AlbumItem) {
        viewModelScope.launch {
            val result = repository.deleteAlbum(album)
            result.onSuccess { pendingIntent ->
                if (pendingIntent != null) {
                    // onIntentSenderSuccess finishes it: the folder is only forgotten and removed once
                    // the user has confirmed, otherwise the album comes back as an empty one.
                    pendingAlbumDelete = album
                    _pendingIntentSender.emit(pendingIntent)
                } else {
                    _userMessage.emit(strings.albumDeleted(album.name))
                    if (_selectedAlbum.value?.bucketId == album.bucketId) {
                        closeAlbum()
                    }
                    loadAlbums()
                    loadMedia()
                    loadTrash()
                }
            }.onFailure { err ->
                _userMessage.emit(strings.deleteError(err.localizedMessage))
            }
        }
    }

    /**
     * Migrates an album between Internal storage and MicroSD card.
     */
    /** Hides several albums at once, from the album list's selection mode. */
    fun hideAlbums(albums: List<AlbumItem>) {
        if (albums.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                albums.forEach { repository.setAlbumHidden(it.bucketId, true, it.directoryPath) }
            }
            _userMessage.emit(
                if (albums.size == 1) strings.albumHidden(albums.first().name) else strings.albumsHidden(albums.size)
            )
            loadMedia()
        }
    }

    /**
     * Moves several albums to the other storage, one after another, under a single progress bar.
     *
     * Albums already on the target are skipped rather than copied onto themselves.
     */
    fun migrateAlbums(albums: List<AlbumItem>, toSdCard: Boolean) {
        val todo = albums.filter { it.isSdCard != toSdCard }
        if (todo.isEmpty()) return
        viewModelScope.launch {
            _userMessage.emit(strings.migratingAlbums(todo.size, toSdCard))
            val total = todo.sumOf { it.count }
            moveProgress.value = MoveProgress(isMoving = true, current = 0, total = total)
            var done = 0
            var moved = 0
            var failed = 0
            val pendingDeletes = ArrayList<Uri>()

            // NonCancellable for the same reason one album is: stopping halfway would leave an album
            // with part of itself on each storage.
            withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                for (album in todo) {
                    val before = done
                    val result = repository.migrateAlbumStorage(album, toSdCard) { current, _ ->
                        moveProgress.value = moveProgress.value.copy(current = before + current, total = total)
                    }
                    done = before + album.count
                    result.onSuccess { outcome ->
                        moved += outcome.moved
                        failed += outcome.failed
                        pendingDeletes.addAll(outcome.pendingDeleteUris)
                    }.onFailure { failed += album.count }
                }
            }

            moveProgress.value = moveProgress.value.copy(isMoving = false)
            _userMessage.emit(
                if (failed > 0) strings.migratedAlbumsPartly(moved, failed) else strings.migratedAlbums(todo.size, toSdCard)
            )
            // Originals owned by other apps still need the user's go-ahead: one request for all albums. Only the
            // first album's used to be asked for, which left the others' originals behind as doubles.
            if (pendingDeletes.isNotEmpty()) {
                withContext(Dispatchers.IO) { repository.createDeleteRequest(pendingDeletes) }?.let { _pendingIntentSender.emit(it) }
            }
            loadAlbums()
            loadMedia()
            if (todo.any { it.bucketId == _selectedAlbum.value?.bucketId }) closeAlbum()
        }
    }

    fun migrateAlbum(album: AlbumItem, toSdCard: Boolean) {
        viewModelScope.launch {
            val destName = toSdCard
            _userMessage.emit(strings.migrating(album.name, destName))

            moveProgress.value = MoveProgress(isMoving = true, current = 0, total = album.count)

            var lastUpdate = System.currentTimeMillis()
            // NonCancellable: a big album takes a while, and leaving the screen (which clears this
            // ViewModel) must not stop it halfway, with part of the album on each volume.
            val result = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                repository.migrateAlbumStorage(album, toSdCard) { current, totalCount ->
                    val now = System.currentTimeMillis()
                    if (current == totalCount || now - lastUpdate >= 200 || current % maxOf(1, totalCount / 20) == 0) {
                        moveProgress.value = moveProgress.value.copy(current = current, total = totalCount)
                        lastUpdate = now
                    }
                }
            }

            moveProgress.value = moveProgress.value.copy(isMoving = false)

            result.onSuccess { outcome ->
                when {
                    outcome.failed > 0 -> _userMessage.emit(strings.migratedPartly(album.name, destName, outcome.moved, outcome.failed))
                    outcome.deleteRequest == null -> _userMessage.emit(strings.migrated(album.name, destName))
                }
                // Originals owned by other apps still need the user's go-ahead to be removed.
                outcome.deleteRequest?.let { _pendingIntentSender.emit(it) }
                loadAlbums()
                loadMedia()
                if (outcome.failed == 0 && _selectedAlbum.value?.bucketId == album.bucketId) {
                    closeAlbum()
                }
            }.onFailure { err ->
                _userMessage.emit(strings.migrationError(err.localizedMessage))
            }
        }
    }

    /**
     * Has Android index files that were written without telling MediaStore. Called when the app comes
     * to the front; the scan itself runs in MediaStore's own process and reports back through
     * [mediaObserver], which then refreshes the library.
     */
    fun rescanMediaFolders() {
        viewModelScope.launch(Dispatchers.IO) { repository.rescanMediaFolders() }
    }

    // ---- 1.2 features: hooks for the tools in ui/features ---------------------------------------------

    /** Every visible photo and video, unfiltered (the tools search and group these). */
    val rawMedia: StateFlow<List<MediaItem>> = _rawMedia.asStateFlow()

    /** Shows [text] the way every other confirmation is shown. */
    fun message(text: String) {
        viewModelScope.launch { _userMessage.emit(text) }
    }

    /** A write that failed for lack of access, retried once the user has allowed it. */
    private var pendingWriteAction: (suspend () -> Unit)? = null

    /** Undoes the preparation of [pendingWriteAction] when the user declines it. */
    private var pendingCancelAction: (() -> Unit)? = null

    /**
     * Runs [action], which changes the files of [items] (their EXIF date or location, say). Files of other apps
     * may only be written after the user's consent: the first attempt then fails with a SecurityException,
     * Android's "Allow ... to modify" dialog is shown and the action runs again once it was allowed.
     */
    fun runWithWriteAccess(items: List<MediaItem>, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                refreshAfterEdit()
            } catch (e: SecurityException) {
                val request = when {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
                        android.provider.MediaStore.createWriteRequest(
                            getApplication<Application>().contentResolver, items.map { it.uri }
                        )
                    e is android.app.RecoverableSecurityException -> e.userAction.actionIntent
                    else -> null
                }
                if (request != null) {
                    pendingWriteAction = action
                    _pendingIntentSender.emit(request)
                } else {
                    _userMessage.emit(com.example.amoledgallery.ui.i18n.F.failed)
                }
            } catch (e: Exception) {
                _userMessage.emit(com.example.amoledgallery.ui.i18n.F.failed)
            }
        }
    }

    /** Moves [items] to the trash (asking first where Android wants that). */
    fun trashItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val pendingIntent = withContext(Dispatchers.IO) { repository.createTrashRequest(items.map { it.uri }, trash = true) }
            if (pendingIntent != null) {
                _pendingIntentSender.emit(pendingIntent)
            } else {
                _userMessage.emit(strings.movedToTrash(items.size))
                loadMedia()
                loadTrash()
            }
        }
    }

    /** Asks Android to delete [items] for good (used once a copy of them exists elsewhere). */
    fun deleteItemsForGood(items: List<MediaItem>, onDone: () -> Unit = {}, onCancelled: () -> Unit = {}) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val pendingIntent = withContext(Dispatchers.IO) { repository.createDeleteRequest(items.map { it.uri }) }
            if (pendingIntent != null) {
                pendingWriteAction = { onDone() }
                pendingCancelAction = onCancelled
                _pendingIntentSender.emit(pendingIntent)
            } else {
                withContext(Dispatchers.IO) { repository.deleteMedia(items) }
                onDone()
                loadMedia()
            }
        }
    }

    /** An album a home-screen shortcut asked for; opened as soon as the albums are known. */
    private var albumToOpen: String? = null

    fun openAlbumWhenLoaded(bucketId: String) {
        albumToOpen = bucketId
        openPendingAlbum()
    }

    private fun openPendingAlbum() {
        val bucket = albumToOpen ?: return
        val album = _albumsState.value.firstOrNull { it.bucketId == bucket } ?: return
        albumToOpen = null
        com.example.amoledgallery.ui.features.Features.screens.clear()
        _selectedItemIndex.value = null
        selectTab(GalleryTab.ALBEN)
        jumpToAlbum(album)
    }

    /** MediaStore volume of the SD card or USB drive "move to" would use, or null. */
    val externalVolumeName: String? get() = repository.externalTarget()?.mediaStoreName

    /** Copies [items] into [target]; the originals stay where they are. */
    fun copyItems(items: List<MediaItem>, target: AlbumItem) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            moveProgress.value = MoveProgress(isMoving = true, current = 0, total = items.size)
            val volume = if (target.isSdCard) externalVolumeName else null
            var copied = 0
            withContext(Dispatchers.IO) {
                items.forEachIndexed { index, item ->
                    if (com.example.amoledgallery.ui.features.MediaOps.copyTo(getApplication(), item, target.relativePath, volume) != null) copied++
                    moveProgress.value = moveProgress.value.copy(current = index + 1)
                }
            }
            moveProgress.value = MoveProgress()
            _userMessage.emit(com.example.amoledgallery.ui.i18n.F.copiedItems(copied, target.name))
            clearSelection()
            refreshAfterEdit()
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<android.app.Application>().contentResolver.unregisterContentObserver(mediaObserver)
        ThumbnailManager.getInstance(getApplication()).stopPreindexing()
    }
}
