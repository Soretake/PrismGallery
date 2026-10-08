package com.example.amoledgallery.ui.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.amoledgallery.PerformanceProfile
import com.example.amoledgallery.ui.theme.AppColorPalette
import com.example.amoledgallery.ui.theme.CUSTOM_PALETTE_ID
import com.example.amoledgallery.ui.theme.DesignLanguage
import com.example.amoledgallery.ui.viewmodel.MediaFilter
import java.util.Locale

enum class AppLanguage(val displayName: String) {
    GERMAN("Deutsch"),
    ENGLISH("English")
}

/**
 * The app's UI language. Chosen explicitly in the settings and persisted; on first start it follows the
 * device language (German devices get German, everything else English). Backed by snapshot state, so
 * switching recomposes every screen that reads [strings].
 */
object LanguageState {
    private const val PREFS_NAME = "amoled_gallery_language"
    private const val KEY_LANGUAGE = "app_language"

    var language by mutableStateOf(deviceDefault())
        private set

    val locale: Locale
        get() = if (language == AppLanguage.GERMAN) Locale.GERMAN else Locale.ENGLISH

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        language = saved?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: deviceDefault()
    }

    fun apply(newLanguage: AppLanguage, context: Context) {
        language = newLanguage
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, newLanguage.name)
            .apply()
    }

    private fun deviceDefault(): AppLanguage =
        if (Locale.getDefault().language == "de") AppLanguage.GERMAN else AppLanguage.ENGLISH
}

/** Texts of the active language. Readable from composables (recomposes on switch) and plain code alike. */
val strings: AppStrings
    get() = if (LanguageState.language == AppLanguage.ENGLISH) EnglishStrings else GermanStrings

val MediaFilter.displayLabel: String
    get() = when (this) {
        MediaFilter.ALL -> strings.filterAll
        MediaFilter.IMAGES -> strings.filterImages
        MediaFilter.VIDEOS -> strings.filterVideos
        MediaFilter.GIFS -> strings.filterGifs
    }

interface AppStrings {
    // General actions
    val back: String
    val close: String
    val cancel: String
    val create: String
    val rename: String
    val delete: String
    val hide: String
    val move: String
    val share: String
    val send: String
    val edit: String
    val save: String
    val saving: String
    val reset: String
    val rotate: String
    val flipHorizontal: String
    val flipVertical: String
    val editSaved: String
    fun editFailed(reason: String?): String
    val details: String
    val favorite: String
    val done: String
    val all: String
    val none: String
    val options: String
    val unknown: String
    val notAvailable: String
    val restore: String
    val emptyAction: String
    val apply: String
    val play: String
    val pause: String
    val name: String

    // Tabs, filters, grid
    val tabPictures: String
    val tabAlbums: String
    val tabMenu: String
    /** iOS Photos' names for the two content tabs. */
    val tabLibrary: String
    val tabCollections: String
    val select: String
    /** Item count as iOS Photos words it ("153 Objekte"). */
    fun objects(count: Int): String
    /** iOS selection bar: "Objekte auswählen" while nothing is selected, "Auswahl (15) anzeigen" after. */
    val selectItems: String
    /** iOS details: "Samstag, 18. April 2026 um 12:10". */
    val infoDatePattern: String
    val noCameraInfo: String
    /** iOS details: "Im Album" followed by the album name. */
    val inAlbumPrefix: String
    fun showSelection(count: Int): String
    val selectAllItems: String
    val deselectAllItems: String
    val filterAll: String
    val filterImages: String
    val filterVideos: String
    val filterGifs: String
    fun items(count: Int): String
    fun selectionTitle(count: Int): String
    fun itemsSelected(count: Int): String
    val endSelection: String
    val newBadge: String
    val latestShot: String
    val largerThumbnails: String
    val smallerThumbnails: String
    val largerSubAlbums: String
    val smallerSubAlbums: String
    val emptyGallery: String
    val today: String
    val yesterday: String
    val dayPattern: String
    val dayWithYearPattern: String
    val longDatePattern: String
    fun timeLabel(time: String): String

    // Albums
    fun albums(count: Int): String
    val myAlbums: String
    val allAlbums: String
    val noAlbums: String
    val subAlbums: String
    val media: String
    val albumEmpty: String
    val createSubAlbum: String
    val newAlbum: String
    val newSubAlbum: String
    val hiddenAlbums: String
    val sortAlbums: String
    val sortStandard: String
    val sortNameAsc: String
    val sortNameDesc: String
    val sortMostItems: String
    val sortCustom: String
    val moveAlbumToTop: String
    val noHiddenAlbums: String
    val unhide: String
    val moveToInternal: String
    val moveToSd: String
    val moveToUsb: String
    val usbStorage: String
    val showAlbums: String
    val showAll: String
    fun freeSpace(size: String): String
    fun selectionSize(count: Int, size: String): String
    val sdCard: String
    val internal: String
    val noSdCard: String
    val renameAlbum: String
    val hideAlbum: String
    val deleteAlbum: String
    val hideAlbumTitle: String
    fun hideAlbumMessage(name: String): String
    val searchAlbums: String
    val searchAlbumsPlaceholder: String
    fun noAlbumsFound(query: String): String
    fun deleteAlbumTitle(name: String): String
    fun deleteAlbumMessage(count: Int): String
    fun deleteEmptyAlbumMessage(name: String): String
    val changeLocationTitle: String
    fun migrateMessage(name: String, count: Int, toSdCard: Boolean): String
    fun moveSizeInfo(size: String, target: String, free: String): String
    fun subAlbumSuffix(count: Int): String
    fun inAlbum(name: String, location: String): String
    val subAlbumName: String
    val albumName: String
    val newAlbumName: String
    val internalStorage: String
    val sdCardUnavailable: String
    val externalStorage: String

    // Move sheet
    val moveToAlbum: String
    val createNewAlbum: String
    val createNewAlbumSubtitle: String
    fun albumTargetSubtitle(isSdCard: Boolean, count: Int): String

    // Favorites & trash
    val favorites: String
    val noFavorites: String
    val noFavoritesMessage: String
    val trash: String
    val recentlyDeleted: String
    val trashEmpty: String
    val trashEmptyMessage: String
    val trashInfo: String
    // Duplicates: files that are byte for byte the same.
    val duplicates: String
    val duplicatesSubtitle: String
    val duplicatesScanning: String
    fun duplicatesScanProgress(checked: Int, total: Int): String
    val noDuplicates: String
    val noDuplicatesMessage: String
    val rescan: String
    fun duplicateSets(count: Int): String
    fun duplicatesSummary(sets: Int, size: String): String
    fun duplicateCopies(count: Int): String
    fun duplicateWastes(size: String): String
    val duplicateKept: String
    fun deleteCopies(count: Int): String
    val deleteCopiesTitle: String
    fun deleteCopiesMessage(count: Int, size: String): String
    fun deleteCopiesMessageAll(count: Int, sets: Int): String
    fun duplicatesFrees(count: Int, size: String): String
    val deletedItem: String
    val itemFallback: String
    val deletePermanently: String
    val emptyTrashTitle: String
    fun emptyTrashMessage(count: Int): String

    // Viewer & details
    val shareItem: String
    val shareMedia: String
    val renameFile: String
    val loadingMetadata: String
    val dateTime: String
    val fileName: String
    val resolution: String
    val fileSize: String
    val videoDuration: String
    val bitrate: String
    val filePath: String
    val cameraModel: String
    val captureSettings: String
    val infoAperture: String
    val infoShutter: String
    val infoFocalLength: String
    val infoExposure: String
    val captureLocation: String
    val map: String
    val gallery: String
    val metadataDatePattern: String
    fun durationText(hours: Long, minutes: Long, seconds: Long): String

    // Menu & settings
    fun freeOf(free: String, total: String): String
    fun free(amount: String): String
    fun percentUsed(percent: Int): String
    fun version(versionName: String): String
    val design: String
    val designAndColors: String
    val appearance: String
    val settings: String
    val graphicsQuality: String
    val storage: String
    val info: String
    val aboutGallery: String
    val deviceStorage: String
    val library: String
    val mediaLibrary: String
    val general: String
    val colors: String
    val light: String
    val dark: String
    val appVersion: String
    val debugBuild: String
    val prereleaseBuild: String
    val customColor: String
    val customAccentTitle: String
    fun customAccentMessage(lightMode: Boolean, designName: String): String
    val customPalette: String
    val language: String
    fun qualityDescription(tier: PerformanceProfile): String
    fun designDescription(language: DesignLanguage): String
    fun paletteLabel(palette: AppColorPalette): String

    // iOS settings pages
    fun storageUsed(used: String, total: String): String
    val legendPhotos: String
    val legendVideos: String
    val legendOther: String
    val storageRecommendations: String
    val recentlyDeletedAlbum: String
    fun freeUpTrash(size: String): String
    val emptyShort: String
    val reviewMedia: String
    fun reviewMediaMessage(size: String): String
    val sortBySize: String
    val storageCalculating: String
    val designFooterText: String
    val appearanceModeHeader: String
    val accentColorHeader: String
    val fromWallpaper: String
    val accentFooterText: String
    val languageFooterText: String
    val appNameLabel: String
    val colorPaletteLabel: String

    // HyperOS pages
    val allPhotos: String
    val organizePhotos: String
    val storageSpaceTitle: String
    val storageAvailable: String
    val emptyTrashAction: String
    fun hyperStorageSummary(used: String, total: String, free: String): String
    val detailInfo: String
    val takenOn: String
    val fileInfo: String
    val exifData: String
    val localPath: String
    val flashFired: String
    val moreActions: String

    // One UI
    val copyToClipboard: String
    val setAsWallpaper: String
    val wallpaperHome: String
    val wallpaperLock: String
    val wallpaperBoth: String
    val wallpaperSet: String
    val liveWallpaper: String
    val liveWallpaperTooLong: String
    val wallpaperFailed: String
    val imageInfo: String
    val recent: String
    val manageStorage: String
    val usedWord: String
    val suggestions: String
    fun imagesAndVideos(images: Int, videos: Int): String

    // Permissions
    val permissionTitle: String
    val permissionDescription: String
    val permissionGrant: String
    val permissionWarning: String
    val permissionMissingMedia: String
    val permissionOpenSettings: String

    // Messages (toasts)
    val favoriteAdded: String
    val favoriteRemoved: String
    fun movedToTrash(count: Int): String
    fun albumCreated(name: String, onSdCard: Boolean): String
    fun createError(message: String?): String
    val noItemsSelected: String
    val noItemsToMove: String
    fun moveStarted(albumName: String): String
    fun movedItems(count: Int, albumName: String): String
    fun albumHidden(name: String): String
    fun albumsHidden(count: Int): String
    fun migratingAlbums(count: Int, toSdCard: Boolean): String
    fun migratedAlbums(count: Int, toSdCard: Boolean): String
    fun migratedAlbumsPartly(moved: Int, failed: Int): String
    val selectAlbums: String
    val hideSelected: String
    val moveSelected: String
    val moveAlbumsTitle: String
    val albumUnhidden: String
    val itemTrashed: String
    val restored: String
    val allRestored: String
    val deletedPermanently: String
    val trashEmptied: String
    fun fileRenamed(name: String): String
    fun error(message: String?): String
    val actionSucceeded: String
    fun subAlbumCreated(name: String, parentName: String, onSdCard: Boolean): String
    fun albumRenamed(name: String): String
    fun renameError(message: String?): String
    fun albumDeleted(name: String): String
    fun deleteError(message: String?): String
    fun migrating(name: String, toSdCard: Boolean): String
    fun migrated(name: String, toSdCard: Boolean): String
    fun migrationError(message: String?): String
    fun migratedPartly(name: String, toSdCard: Boolean, moved: Int, failed: Int): String
    fun notEnoughSpace(neededBytes: Long, freeBytes: Long): String
    val movingMediaTitle: String
    fun progressOf(current: Int, total: Int): String

    // Repository errors
    val sdCardNotAvailable: String
    val nameEmpty: String
    val renameFailed: String
    val permissionDenied: String
    val renameFailedGeneric: String
    val sdCardNotWritable: String
    val folderCreateFailed: String
    val subFolderCreateFailed: String
}


object GermanStrings : AppStrings {
    override val back = "Zurück"
    override val close = "Schließen"
    override val cancel = "Abbrechen"
    override val create = "Erstellen"
    override val rename = "Umbenennen"
    override val delete = "Löschen"
    override val hide = "Ausblenden"
    override val move = "Verschieben"
    override val share = "Teilen"
    override val send = "Senden"
    override val edit = "Bearbeiten"
    override val save = "Speichern"
    override val saving = "Wird gespeichert …"
    override val reset = "Zurücksetzen"
    override val rotate = "Drehen"
    override val flipHorizontal = "Spiegeln"
    override val flipVertical = "Kippen"
    override val editSaved = "Als Kopie gespeichert"
    override fun editFailed(reason: String?) = if (reason.isNullOrBlank()) "Speichern fehlgeschlagen" else "Speichern fehlgeschlagen: $reason"
    override val details = "Details"
    override val favorite = "Favorit"
    override val done = "Fertig"
    override val all = "Alle"
    override val none = "Keine"
    override val options = "Optionen"
    override val unknown = "Unbekannt"
    override val notAvailable = "Nicht vorhanden"
    override val restore = "Wiederherstellen"
    override val emptyAction = "Leeren"
    override val apply = "Übernehmen"
    override val play = "Abspielen"
    override val pause = "Pause"
    override val name = "Name"

    override val tabPictures = "Bilder"
    override val tabAlbums = "Alben"
    override val tabMenu = "Menü"
    override val tabLibrary = "Mediathek"
    override val tabCollections = "Sammlungen"
    override val select = "Auswählen"
    override fun objects(count: Int) = if (count == 1) "1 Objekt" else "$count Objekte"
    override val selectItems = "Objekte auswählen"
    override val infoDatePattern = "EEEE, d. MMMM yyyy 'um' HH:mm"
    override val noCameraInfo = "Keine Kamerainformationen"
    override val inAlbumPrefix = "Im Album"
    override fun showSelection(count: Int) = "Auswahl ($count) anzeigen"
    override val selectAllItems = "Alle auswählen"
    override val deselectAllItems = "Alle abwählen"
    override val filterAll = "Alle"
    override val filterImages = "Bilder"
    override val filterVideos = "Videos"
    override val filterGifs = "GIFs"
    override fun items(count: Int) = if (count == 1) "1 Element" else "$count Elemente"
    override fun selectionTitle(count: Int) = if (count == 0) "Elemente auswählen" else "$count ausgewählt"
    override fun itemsSelected(count: Int) = if (count == 1) "1 Objekt ausgewählt" else "$count Objekte ausgewählt"
    override val endSelection = "Auswahl beenden"
    override val newBadge = "Neu"
    override val latestShot = "Neueste Aufnahme"
    override val largerThumbnails = "Größere Vorschaubilder"
    override val smallerThumbnails = "Kleinere Vorschaubilder"
    override val largerSubAlbums = "Größere Unteralben"
    override val smallerSubAlbums = "Kleinere Unteralben"
    override val emptyGallery = "Keine Medien gefunden"
    override val today = "Heute"
    override val yesterday = "Gestern"
    override val dayPattern = "d. MMMM"
    override val dayWithYearPattern = "d. MMMM yyyy"
    override val longDatePattern = "d. MMMM yyyy"
    override fun timeLabel(time: String) = "$time Uhr"

    override fun albums(count: Int) = if (count == 1) "1 Album" else "$count Alben"
    override val myAlbums = "Meine Alben"
    override val allAlbums = "Alle Alben"
    override val noAlbums = "Noch keine Alben"
    override val subAlbums = "Unteralben"
    override val media = "Medien"
    override val albumEmpty = "Dieses Album ist leer"
    override val createSubAlbum = "Unteralbum anlegen"
    override val newAlbum = "Neues Album"
    override val newSubAlbum = "Neues Unteralbum"
    override val hiddenAlbums = "Ausgeblendete Alben"
    override val sortAlbums = "Alben sortieren"
    override val sortStandard = "Standard"
    override val sortNameAsc = "Name (A–Z)"
    override val sortNameDesc = "Name (Z–A)"
    override val sortMostItems = "Meiste Elemente"
    override val sortCustom = "Eigene Reihenfolge"
    override val moveAlbumToTop = "An den Anfang"
    override val noHiddenAlbums = "Keine ausgeblendeten Alben vorhanden"
    override val unhide = "Wieder einblenden"
    override val moveToInternal = "Auf internen Speicher verschieben"
    override val moveToSd = "Auf SD-Karte verschieben"
    override val moveToUsb = "Auf USB-Speicher verschieben"
    override val usbStorage = "USB-Speicher"
    override val showAlbums = "Alben anzeigen"
    override val showAll = "Alle"
    override fun freeSpace(size: String) = "$size frei"
    override fun selectionSize(count: Int, size: String) = "${counted(count, "Element", "Elemente")} · $size"
    override val sdCard = "SD-Karte"
    override val internal = "Intern"
    override val noSdCard = "Keine SD-Karte eingelegt"
    override val renameAlbum = "Album umbenennen"
    override val hideAlbum = "Album ausblenden"
    override val deleteAlbum = "Album löschen"
    override val hideAlbumTitle = "Album ausblenden?"
    override fun hideAlbumMessage(name: String) =
        "„$name“ und seine Inhalte werden in der Galerie und in den Alben ausgeblendet. Über „Ausgeblendete Alben“ kannst du es jederzeit wieder einblenden."
    override val searchAlbums = "Alben durchsuchen"
    override val searchAlbumsPlaceholder = "Albumname"
    override fun noAlbumsFound(query: String) = "Keine Alben für „$query“ gefunden"
    override fun deleteAlbumTitle(name: String) = "„$name“ löschen?"
    override fun deleteAlbumMessage(count: Int) =
        "Alle $count Medien in diesem Album werden in den Papierkorb verschoben und der Ordner wird entfernt."
    override fun deleteEmptyAlbumMessage(name: String) = "Der leere Ordner „$name“ wird dauerhaft gelöscht."
    override val changeLocationTitle = "Speicherort ändern?"
    override fun moveSizeInfo(size: String, target: String, free: String) = "Größe: $size · Frei auf $target: $free"
    override fun migrateMessage(name: String, count: Int, toSdCard: Boolean) =
        "„$name“ mit ${counted(count, "Element", "Elementen")} wird auf ${if (toSdCard) "die SD-Karte" else "den internen Speicher"} verschoben. Die Dateien werden physisch verschoben und der Medienindex sofort aktualisiert."
    override fun subAlbumSuffix(count: Int) = " · $count Unteralben"
    override fun inAlbum(name: String, location: String) = "In „$name“ · $location"
    override val subAlbumName = "Name des Unteralbums"
    override val albumName = "Albumname"
    override val newAlbumName = "Neuer Albumname"
    override val internalStorage = "Interner Speicher"
    override val sdCardUnavailable = "SD-Karte (keine eingelegt)"
    override val externalStorage = "Externer Speicher"

    override val moveToAlbum = "In Album verschieben"
    override val createNewAlbum = "Neues Album erstellen"
    override val createNewAlbumSubtitle = "Zielordner anlegen und verschieben"
    override fun albumTargetSubtitle(isSdCard: Boolean, count: Int) =
        "${if (isSdCard) sdCard else internal} · ${items(count)}"

    override val favorites = "Favoriten"
    override val noFavorites = "Keine Favoriten"
    override val noFavoritesMessage = "Tippe im Vollbild auf das Herz, um Fotos und Videos hier zu sammeln."
    override val trash = "Papierkorb"
    override val recentlyDeleted = "Zuletzt gelöscht"
    override val trashEmpty = "Papierkorb ist leer"
    override val trashEmptyMessage = "Gelöschte Medien werden hier 30 Tage aufbewahrt."
    override val trashInfo = "Elemente im Papierkorb werden nach 30 Tagen endgültig gelöscht."
    override val duplicates = "Doppelte Dateien"
    override val duplicatesSubtitle = "Dateien finden, die es mehrfach gibt"
    override val duplicatesScanning = "Dateien werden verglichen …"
    override fun duplicatesScanProgress(checked: Int, total: Int) = "$checked von $total geprüft"
    override val noDuplicates = "Keine Doppelten"
    override val noDuplicatesMessage = "Keine Datei liegt mehrfach auf diesem Gerät."
    override val rescan = "Neu suchen"
    override fun duplicateSets(count: Int) = if (count == 1) "1 Gruppe" else "$count Gruppen"
    override fun duplicatesSummary(sets: Int, size: String) =
        "${if (sets == 1) "Eine Datei liegt" else "$sets Dateien liegen"} mehrfach vor. Die zusätzlichen Kopien belegen $size."
    override fun duplicateCopies(count: Int) = "$count× dieselbe Datei"
    override fun duplicateWastes(size: String) = "$size doppelt"
    override val duplicateKept = "Bleibt"
    override fun deleteCopies(count: Int) = "${counted(count, "Kopie", "Kopien")} löschen"
    override val deleteCopiesTitle = "Kopien löschen?"
    override fun deleteCopiesMessage(count: Int, size: String) =
        "${counted(count, "Kopie", "Kopien")} wandern in den Papierkorb und geben $size frei. Von jeder Datei bleibt mindestens eine erhalten."
    override fun deleteCopiesMessageAll(count: Int, sets: Int) =
        "${counted(count, "Kopie", "Kopien")} wandern in den Papierkorb. Achtung: bei ${counted(sets, "Gruppe", "Gruppen")} ist jede Kopie ausgewählt – dort bleibt nichts übrig."
    override fun duplicatesFrees(count: Int, size: String) = "$count ausgewählt · gibt $size frei"
    override val deletedItem = "Gelöschtes Element"
    override val itemFallback = "Medium"
    override val deletePermanently = "Endgültig löschen"
    override val emptyTrashTitle = "Papierkorb leeren?"
    override fun emptyTrashMessage(count: Int) = "Alle $count Elemente im Papierkorb werden dauerhaft gelöscht."

    override val shareItem = "Medium teilen"
    override val shareMedia = "Medien teilen"
    override val renameFile = "Datei umbenennen"
    override val loadingMetadata = "Metadaten werden geladen…"
    override val dateTime = "Datum & Uhrzeit"
    override val fileName = "Dateiname"
    override val resolution = "Auflösung"
    override val fileSize = "Dateigröße"
    override val videoDuration = "Videodauer"
    override val bitrate = "Bitrate"
    override val filePath = "Dateipfad"
    override val cameraModel = "Kameramodell"
    override val captureSettings = "Aufnahmeparameter"
    override val infoAperture = "Blende"
    override val infoShutter = "Belichtung"
    override val infoFocalLength = "Brennweite"
    override val infoExposure = "Korrektur"
    override val captureLocation = "Aufnahmeort"
    override val map = "Karte"
    override val gallery = "Galerie"
    override val metadataDatePattern = "d. MMMM yyyy, HH:mm 'Uhr'"
    override fun durationText(hours: Long, minutes: Long, seconds: Long) =
        if (hours > 0) "$hours Std. $minutes Min. $seconds Sek." else "$minutes Min. $seconds Sek."

    override fun freeOf(free: String, total: String) = "$free frei von $total"
    override fun free(amount: String) = "$amount frei"
    override fun percentUsed(percent: Int) = "$percent % belegt"
    override fun version(versionName: String) = "Version ${versionName.ifBlank { "unbekannt" }}"
    override val design = "Design"
    override val designAndColors = "Design & Farben"
    override val appearance = "Darstellung"
    override val settings = "Einstellungen"
    override fun storageUsed(used: String, total: String) = "$used von $total verwendet"
    override val legendPhotos = "Fotos"
    override val legendVideos = "Videos"
    override val legendOther = "Andere"
    override val storageRecommendations = "Empfehlungen"
    override val recentlyDeletedAlbum = "Album „Zuletzt gelöscht“"
    override fun freeUpTrash(size: String) = "Gib $size Speicherplatz frei. Alle Fotos und Videos werden im Album „Zuletzt gelöscht“ endgültig gelöscht."
    override val emptyShort = "Leer"
    override val reviewMedia = "Fotos und Videos überprüfen"
    override fun reviewMediaMessage(size: String) = "Fotos und Videos belegen $size. Sieh sie dir in der Mediathek an und lösche sie gegebenenfalls."
    override val sortBySize = "Größe"
    override val storageCalculating = "Wird berechnet …"
    override val designFooterText = "Das Design bestimmt Leisten, Dialoge, Listen und Animationen der gesamten App."
    override val appearanceModeHeader = "Erscheinungsbild"
    override val accentColorHeader = "Akzentfarbe"
    override val fromWallpaper = "Vom Hintergrundbild"
    override val accentFooterText = "Die Akzentfarbe wird für Symbole, Auswahlen und hervorgehobene Texte verwendet."
    override val languageFooterText = "Die Sprache der App, unabhängig von der Systemsprache."
    override val appNameLabel = "Name"
    override val colorPaletteLabel = "Farbpalette"
    override val allPhotos = "Alle Fotos"
    override val organizePhotos = "Fotos organisieren"
    override val storageSpaceTitle = "Speicherplatz"
    override val storageAvailable = "Verfügbar"
    override val emptyTrashAction = "Papierkorb leeren"
    override fun hyperStorageSummary(used: String, total: String, free: String) = "$used/$total belegt | $free verfügbar"
    override val detailInfo = "Detailinfo"
    override val takenOn = "Aufgenommen:"
    override val fileInfo = "Datei:"
    override val exifData = "EXIF-Daten:"
    override val localPath = "Speicherort:"
    override val flashFired = "Blitz ausgelöst"
    override val moreActions = "Mehr"
    override val copyToClipboard = "In Ablage kopieren"
    override val setAsWallpaper = "Als Hintergrund festlegen"
    override val wallpaperHome = "Startbildschirm"
    override val wallpaperLock = "Sperrbildschirm"
    override val wallpaperBoth = "Start- und Sperrbildschirm"
    override val wallpaperSet = "Hintergrund festgelegt"
    override val liveWallpaper = "Als Live-Hintergrund festlegen"
    override val liveWallpaperTooLong = "Nur Videos bis 15 Sekunden können Live-Hintergrund werden."
    override val wallpaperFailed = "Hintergrund konnte nicht festgelegt werden"
    override val imageInfo = "Bild-Info"
    override val recent = "Aktuell"
    override val manageStorage = "Speicher verwalten"
    override val usedWord = "verwendet"
    override val suggestions = "Vorschläge"
    override fun imagesAndVideos(images: Int, videos: Int) = listOfNotNull(
        if (images > 0 || videos == 0) (if (images == 1) "1 Bild" else "$images Bilder") else null,
        if (videos > 0) (if (videos == 1) "1 Video" else "$videos Videos") else null
    ).joinToString(" ")
    override val graphicsQuality = "Grafikqualität"
    override val storage = "Speicher"
    override val info = "Info"
    override val aboutGallery = "Über Prism Gallery"
    override val deviceStorage = "Gerätespeicher"
    override val library = "Bibliothek"
    override val mediaLibrary = "Mediathek"
    override val general = "Allgemein"
    override val colors = "Farben"
    override val light = "Hell"
    override val dark = "Dunkel"
    override val appVersion = "App-Version"
    override val debugBuild = "Debug"
    override val prereleaseBuild = "Vorabversion"
    override val customColor = "Eigene Farbe"
    override val customAccentTitle = "Eigene Akzentfarbe"
    override fun customAccentMessage(lightMode: Boolean, designName: String) =
        "Die ${if (lightMode) "hellen" else "dunklen"} Grundfarben von $designName bleiben erhalten - nur der Akzent ändert sich."
    override val customPalette = "Eigene Palette"
    override val language = "Sprache"
    override fun qualityDescription(tier: PerformanceProfile) = when (tier) {
        PerformanceProfile.LOW -> "Nur das Nötigste: keine Animationen, kein Glas, kleinere Vorschaubilder, geringster Speicher- & Akkuverbrauch."
        PerformanceProfile.MEDIUM -> "Ausgewogen: reduzierte Animationen, undurchsichtige statt gläserne Leisten, gute Bildqualität."
        PerformanceProfile.HIGH -> "Echtes Glas mit Hintergrundunschärfe, flüssige Animationen, hohe Bildqualität."
        PerformanceProfile.ULTRA -> "Die volle Effektpalette: Glas, bewegte Wellen, längste Animationen, höchste Bildqualität und größte Caches."
    }
    override fun designDescription(language: DesignLanguage) = when (language) {
        DesignLanguage.SAMSUNG_ONEUI -> "AMOLED-Schwarz, riesiger zentrierter Titel und schwebende Milchglas-Leiste wie in der Samsung Galerie"
        DesignLanguage.GOOGLE_EXPRESSIVE -> "Google Fotos im Gemini-Look: tiefschwarz mit farbigem Schimmer, schwebende Pillen-Leiste und leuchtende Glaskarten"
        DesignLanguage.IOS_LIQUID_GLASS -> "Klares Flüssigglas über randlosen Fotos, schwebende Glas-Bedienelemente und zentrierte Dialoge"
        DesignLanguage.XIAOMI_HYPEROS -> "Große dünne Titel, runde Album-Cover, gestapelte Menükarten, volle Tab-Leiste und Dialoge am unteren Rand"
    }
    override fun paletteLabel(palette: AppColorPalette) =
        if (palette.id == CUSTOM_PALETTE_ID) customPalette else palette.label

    override val permissionTitle = "Zugriff auf Fotos und Videos"
    override val permissionDescription = "Erlaube den Zugriff auf deine Medienbibliothek, um deine Fotos und Videos anzuzeigen."
    override val permissionGrant = "Berechtigungen erteilen"
    override val permissionWarning = "Die App funktioniert nicht richtig, solange diese Berechtigungen fehlen:"
    override val permissionMissingMedia = "Zugriff auf Fotos und Videos — ohne diesen kann die Galerie nichts anzeigen"
    override val permissionOpenSettings = "In den Einstellungen öffnen"

    override val favoriteAdded = "Zu Favoriten hinzugefügt"
    override val favoriteRemoved = "Aus Favoriten entfernt"
    override fun movedToTrash(count: Int) = "${counted(count, "Element", "Elemente")} in Papierkorb verschoben"
    override fun albumCreated(name: String, onSdCard: Boolean) =
        "Album '$name' ${if (onSdCard) "auf SD-Karte" else "im internen Speicher"} erstellt"
    override fun createError(message: String?) = "Fehler beim Erstellen: $message"
    override val noItemsSelected = "Keine Elemente ausgewählt"
    override val noItemsToMove = "Keine Elemente zum Verschieben gefunden"
    override fun moveStarted(albumName: String) = "Verschieben nach '$albumName' gestartet..."
    override fun movedItems(count: Int, albumName: String) = "${counted(count, "Element", "Elemente")} erfolgreich nach '$albumName' verschoben"
    override fun albumHidden(name: String) = "Album '$name' ausgeblendet"
    override fun albumsHidden(count: Int) = "$count Alben ausgeblendet"
    override fun migratingAlbums(count: Int, toSdCard: Boolean) =
        "$count Alben werden auf ${if (toSdCard) "die SD-Karte" else "den internen Speicher"} verschoben …"
    override fun migratedAlbums(count: Int, toSdCard: Boolean) =
        "$count Alben auf ${if (toSdCard) "die SD-Karte" else "den internen Speicher"} verschoben"
    override fun migratedAlbumsPartly(moved: Int, failed: Int) =
        "$moved Dateien verschoben, $failed konnten nicht verschoben werden"
    override val selectAlbums = "Auswählen"
    override val hideSelected = "Ausblenden"
    override val moveSelected = "Verschieben"
    override val moveAlbumsTitle = "Wohin verschieben?"
    override val albumUnhidden = "Album wieder eingeblendet"
    override val itemTrashed = "In Papierkorb verschoben"
    override val restored = "Wiederhergestellt"
    override val allRestored = "Alle Elemente wiederhergestellt"
    override val deletedPermanently = "Endgültig gelöscht"
    override val trashEmptied = "Papierkorb geleert"
    override fun fileRenamed(name: String) = "Datei umbenannt in $name"
    override fun error(message: String?) = "Fehler: $message"
    override val actionSucceeded = "Aktion erfolgreich ausgeführt"
    override fun subAlbumCreated(name: String, parentName: String, onSdCard: Boolean) =
        "Unteralbum '$name' in '$parentName' ${if (onSdCard) "auf SD-Karte" else "im internen Speicher"} erstellt"
    override fun albumRenamed(name: String) = "Album in '$name' umbenannt"
    override fun renameError(message: String?) = "Fehler beim Umbenennen: $message"
    override fun albumDeleted(name: String) = "Album '$name' gelöscht"
    override fun deleteError(message: String?) = "Fehler beim Löschen: $message"
    override fun migrating(name: String, toSdCard: Boolean) =
        "Verschiebe '$name' auf ${if (toSdCard) "SD-Karte" else "internen Speicher"}..."
    override fun migrated(name: String, toSdCard: Boolean) =
        "Album '$name' erfolgreich auf ${if (toSdCard) "SD-Karte" else "internen Speicher"} verschoben"
    override fun migrationError(message: String?) = "Fehler bei Migration: $message"
    override fun migratedPartly(name: String, toSdCard: Boolean, moved: Int, failed: Int) =
        "${counted(moved, "Element", "Elemente")} von '$name' auf ${if (toSdCard) "SD-Karte" else "internen Speicher"} verschoben, $failed konnten nicht verschoben werden und sind noch am alten Ort"
    override fun notEnoughSpace(neededBytes: Long, freeBytes: Long) =
        "Nicht genug Platz: ${gigabytes(neededBytes)} nötig, ${gigabytes(freeBytes)} frei"
    override val movingMediaTitle = "Verschiebe Medien…"
    override fun progressOf(current: Int, total: Int) = "$current von $total"

    override val sdCardNotAvailable = "SD-Karte nicht verfügbar"
    override val nameEmpty = "Name darf nicht leer sein."
    override val renameFailed = "Umbenennen fehlgeschlagen."
    override val permissionDenied = "Berechtigung verweigert."
    override val renameFailedGeneric = "Fehler beim Umbenennen."
    override val sdCardNotWritable = "SD-Karte nicht gefunden oder nicht beschreibbar."
    override val folderCreateFailed = "Ordner konnte nicht erstellt werden."
    override val subFolderCreateFailed = "Unterordner konnte nicht erstellt werden."
}

object EnglishStrings : AppStrings {
    override val back = "Back"
    override val close = "Close"
    override val cancel = "Cancel"
    override val create = "Create"
    override val rename = "Rename"
    override val delete = "Delete"
    override val hide = "Hide"
    override val move = "Move"
    override val share = "Share"
    override val send = "Send"
    override val edit = "Edit"
    override val save = "Save"
    override val saving = "Saving …"
    override val reset = "Reset"
    override val rotate = "Rotate"
    override val flipHorizontal = "Mirror"
    override val flipVertical = "Flip"
    override val editSaved = "Saved as a copy"
    override fun editFailed(reason: String?) = if (reason.isNullOrBlank()) "Saving failed" else "Saving failed: $reason"
    override val details = "Details"
    override val favorite = "Favorite"
    override val done = "Done"
    override val all = "All"
    override val none = "None"
    override val options = "Options"
    override val unknown = "Unknown"
    override val notAvailable = "Not available"
    override val restore = "Restore"
    override val emptyAction = "Empty"
    override val apply = "Apply"
    override val play = "Play"
    override val pause = "Pause"
    override val name = "Name"

    override val tabPictures = "Photos"
    override val tabAlbums = "Albums"
    override val tabMenu = "Menu"
    override val tabLibrary = "Library"
    override val tabCollections = "Collections"
    override val select = "Select"
    override fun objects(count: Int) = if (count == 1) "1 Item" else "$count Items"
    override val selectItems = "Select Items"
    override val infoDatePattern = "EEEE, MMMM d, yyyy 'at' HH:mm"
    override val noCameraInfo = "No camera information"
    override val inAlbumPrefix = "In album"
    override fun showSelection(count: Int) = "Show Selection ($count)"
    override val selectAllItems = "Select All"
    override val deselectAllItems = "Deselect All"
    override val filterAll = "All"
    override val filterImages = "Images"
    override val filterVideos = "Videos"
    override val filterGifs = "GIFs"
    override fun items(count: Int) = if (count == 1) "1 item" else "$count items"
    override fun selectionTitle(count: Int) = if (count == 0) "Select items" else "$count selected"
    override fun itemsSelected(count: Int) = if (count == 1) "1 item selected" else "$count items selected"
    override val endSelection = "End selection"
    override val newBadge = "New"
    override val latestShot = "Latest capture"
    override val largerThumbnails = "Larger thumbnails"
    override val smallerThumbnails = "Smaller thumbnails"
    override val largerSubAlbums = "Larger sub-albums"
    override val smallerSubAlbums = "Smaller sub-albums"
    override val emptyGallery = "No media found"
    override val today = "Today"
    override val yesterday = "Yesterday"
    override val dayPattern = "MMMM d"
    override val dayWithYearPattern = "MMMM d, yyyy"
    override val longDatePattern = "MMMM d, yyyy"
    override fun timeLabel(time: String) = time

    override fun albums(count: Int) = if (count == 1) "1 album" else "$count albums"
    override val myAlbums = "My albums"
    override val allAlbums = "All albums"
    override val noAlbums = "No albums yet"
    override val subAlbums = "Sub-albums"
    override val media = "Media"
    override val albumEmpty = "This album is empty"
    override val createSubAlbum = "Create sub-album"
    override val newAlbum = "New album"
    override val newSubAlbum = "New sub-album"
    override val hiddenAlbums = "Hidden albums"
    override val sortAlbums = "Sort albums"
    override val sortStandard = "Standard"
    override val sortNameAsc = "Name (A–Z)"
    override val sortNameDesc = "Name (Z–A)"
    override val sortMostItems = "Most items"
    override val sortCustom = "Custom order"
    override val moveAlbumToTop = "Move to top"
    override val noHiddenAlbums = "No hidden albums"
    override val unhide = "Show again"
    override val moveToInternal = "Move to internal storage"
    override val moveToSd = "Move to SD card"
    override val moveToUsb = "Move to USB storage"
    override val usbStorage = "USB storage"
    override val showAlbums = "Show albums"
    override val showAll = "All"
    override fun freeSpace(size: String) = "$size free"
    override fun selectionSize(count: Int, size: String) = "${counted(count, "item", "items")} · $size"
    override val sdCard = "SD card"
    override val internal = "Internal"
    override val noSdCard = "No SD card inserted"
    override val renameAlbum = "Rename album"
    override val hideAlbum = "Hide album"
    override val deleteAlbum = "Delete album"
    override val hideAlbumTitle = "Hide album?"
    override fun hideAlbumMessage(name: String) =
        "“$name” and its contents will be hidden from the gallery and your albums. You can show it again at any time under “Hidden albums”."
    override val searchAlbums = "Search albums"
    override val searchAlbumsPlaceholder = "Album name"
    override fun noAlbumsFound(query: String) = "No albums found for “$query”"
    override fun deleteAlbumTitle(name: String) = "Delete “$name”?"
    override fun deleteAlbumMessage(count: Int) =
        "All $count items in this album will be moved to the trash and the folder will be removed."
    override fun deleteEmptyAlbumMessage(name: String) = "The empty folder “$name” will be deleted permanently."
    override val changeLocationTitle = "Change storage location?"
    override fun moveSizeInfo(size: String, target: String, free: String) = "Size: $size · Free on $target: $free"
    override fun migrateMessage(name: String, count: Int, toSdCard: Boolean) =
        "“$name” with ${counted(count, "item", "items")} will be moved to ${if (toSdCard) "the SD card" else "internal storage"}. The files are physically moved and the media index is updated right away."
    override fun subAlbumSuffix(count: Int) = " · $count sub-albums"
    override fun inAlbum(name: String, location: String) = "In “$name” · $location"
    override val subAlbumName = "Sub-album name"
    override val albumName = "Album name"
    override val newAlbumName = "New album name"
    override val internalStorage = "Internal storage"
    override val sdCardUnavailable = "SD card (none inserted)"
    override val externalStorage = "External storage"

    override val moveToAlbum = "Move to album"
    override val createNewAlbum = "Create new album"
    override val createNewAlbumSubtitle = "Create a folder and move there"
    override fun albumTargetSubtitle(isSdCard: Boolean, count: Int) =
        "${if (isSdCard) sdCard else internal} · ${items(count)}"

    override val favorites = "Favorites"
    override val noFavorites = "No favorites"
    override val noFavoritesMessage = "Tap the heart in the full-screen view to collect photos and videos here."
    override val trash = "Trash"
    override val recentlyDeleted = "Recently deleted"
    override val trashEmpty = "Trash is empty"
    override val trashEmptyMessage = "Deleted media is kept here for 30 days."
    override val trashInfo = "Items in the trash are deleted permanently after 30 days."
    override val duplicates = "Duplicate files"
    override val duplicatesSubtitle = "Find files you have more than once"
    override val duplicatesScanning = "Comparing files …"
    override fun duplicatesScanProgress(checked: Int, total: Int) = "$checked of $total checked"
    override val noDuplicates = "No duplicates"
    override val noDuplicatesMessage = "No file on this device exists more than once."
    override val rescan = "Scan again"
    override fun duplicateSets(count: Int) = if (count == 1) "1 set" else "$count sets"
    override fun duplicatesSummary(sets: Int, size: String) =
        "${if (sets == 1) "One file exists" else "$sets files exist"} more than once. The extra copies take up $size."
    override fun duplicateCopies(count: Int) = "$count× the same file"
    override fun duplicateWastes(size: String) = "$size doubled"
    override val duplicateKept = "Kept"
    override fun deleteCopies(count: Int) = "Delete ${counted(count, "copy", "copies")}"
    override val deleteCopiesTitle = "Delete copies?"
    override fun deleteCopiesMessage(count: Int, size: String) =
        "${counted(count, "copy", "copies")} go to the trash and free $size. At least one of every file is kept."
    override fun deleteCopiesMessageAll(count: Int, sets: Int) =
        "${counted(count, "copy", "copies")} go to the trash. Careful: in ${counted(sets, "set", "sets")} every copy is ticked, so nothing of those is left."
    override fun duplicatesFrees(count: Int, size: String) = "$count selected · frees $size"
    override val deletedItem = "Deleted item"
    override val itemFallback = "Item"
    override val deletePermanently = "Delete permanently"
    override val emptyTrashTitle = "Empty trash?"
    override fun emptyTrashMessage(count: Int) = "All $count items in the trash will be deleted permanently."

    override val shareItem = "Share item"
    override val shareMedia = "Share media"
    override val renameFile = "Rename file"
    override val loadingMetadata = "Loading metadata…"
    override val dateTime = "Date & time"
    override val fileName = "File name"
    override val resolution = "Resolution"
    override val fileSize = "File size"
    override val videoDuration = "Video length"
    override val bitrate = "Bitrate"
    override val filePath = "File path"
    override val cameraModel = "Camera model"
    override val captureSettings = "Capture settings"
    override val infoAperture = "Aperture"
    override val infoShutter = "Shutter"
    override val infoFocalLength = "Focal length"
    override val infoExposure = "Exposure"
    override val captureLocation = "Location"
    override val map = "Map"
    override val gallery = "Gallery"
    override val metadataDatePattern = "MMMM d, yyyy, HH:mm"
    override fun durationText(hours: Long, minutes: Long, seconds: Long) =
        if (hours > 0) "$hours h $minutes min $seconds s" else "$minutes min $seconds s"

    override fun freeOf(free: String, total: String) = "$free free of $total"
    override fun free(amount: String) = "$amount free"
    override fun percentUsed(percent: Int) = "$percent% used"
    override fun version(versionName: String) = "Version ${versionName.ifBlank { "unknown" }}"
    override val design = "Design"
    override val designAndColors = "Design & colors"
    override val appearance = "Appearance"
    override val settings = "Settings"
    override fun storageUsed(used: String, total: String) = "$used of $total used"
    override val legendPhotos = "Photos"
    override val legendVideos = "Videos"
    override val legendOther = "Other"
    override val storageRecommendations = "Recommendations"
    override val recentlyDeletedAlbum = "“Recently Deleted” Album"
    override fun freeUpTrash(size: String) = "Free up $size. All photos and videos in “Recently Deleted” will be permanently deleted."
    override val emptyShort = "Empty"
    override val reviewMedia = "Review Photos and Videos"
    override fun reviewMediaMessage(size: String) = "Photos and videos use $size. Review them in the library and delete the ones you no longer need."
    override val sortBySize = "Size"
    override val storageCalculating = "Calculating…"
    override val designFooterText = "The design shapes the bars, dialogs, lists and animations of the whole app."
    override val appearanceModeHeader = "Appearance"
    override val accentColorHeader = "Accent Color"
    override val fromWallpaper = "From wallpaper"
    override val accentFooterText = "The accent color is used for icons, selections and highlighted text."
    override val languageFooterText = "The app's language, independent of the system language."
    override val appNameLabel = "Name"
    override val colorPaletteLabel = "Color Palette"
    override val allPhotos = "All photos"
    override val organizePhotos = "Organize photos"
    override val storageSpaceTitle = "Storage space"
    override val storageAvailable = "Available"
    override val emptyTrashAction = "Empty trash"
    override fun hyperStorageSummary(used: String, total: String, free: String) = "$used/$total occupied | $free available"
    override val detailInfo = "Detail info"
    override val takenOn = "Taken on:"
    override val fileInfo = "File info:"
    override val exifData = "EXIF data:"
    override val localPath = "Local path:"
    override val flashFired = "Flash used"
    override val moreActions = "More"
    override val copyToClipboard = "Copy to clipboard"
    override val setAsWallpaper = "Set as wallpaper"
    override val wallpaperHome = "Home screen"
    override val wallpaperLock = "Lock screen"
    override val wallpaperBoth = "Home and lock screen"
    override val wallpaperSet = "Wallpaper set"
    override val liveWallpaper = "Set as live wallpaper"
    override val liveWallpaperTooLong = "Only videos up to 15 seconds can be a live wallpaper."
    override val wallpaperFailed = "Could not set the wallpaper"
    override val imageInfo = "Image info"
    override val recent = "Recent"
    override val manageStorage = "Manage storage"
    override val usedWord = "used"
    override val suggestions = "Suggestions"
    override fun imagesAndVideos(images: Int, videos: Int) = listOfNotNull(
        if (images > 0 || videos == 0) (if (images == 1) "1 image" else "$images images") else null,
        if (videos > 0) (if (videos == 1) "1 video" else "$videos videos") else null
    ).joinToString(" ")
    override val graphicsQuality = "Graphics quality"
    override val storage = "Storage"
    override val info = "Info"
    override val aboutGallery = "About Prism Gallery"
    override val deviceStorage = "Device storage"
    override val library = "Library"
    override val mediaLibrary = "Library"
    override val general = "General"
    override val colors = "Colors"
    override val light = "Light"
    override val dark = "Dark"
    override val appVersion = "App version"
    override val debugBuild = "Debug"
    override val prereleaseBuild = "Pre-release"
    override val customColor = "Custom color"
    override val customAccentTitle = "Custom accent color"
    override fun customAccentMessage(lightMode: Boolean, designName: String) =
        "The ${if (lightMode) "light" else "dark"} base colors of $designName stay the same - only the accent changes."
    override val customPalette = "Custom palette"
    override val language = "Language"
    override fun qualityDescription(tier: PerformanceProfile) = when (tier) {
        PerformanceProfile.LOW -> "Just the essentials: no animations, no glass, smaller thumbnails, lowest memory and battery use."
        PerformanceProfile.MEDIUM -> "Balanced: reduced animations, solid instead of glass bars, good image quality."
        PerformanceProfile.HIGH -> "Real glass with background blur, smooth animations, high image quality."
        PerformanceProfile.ULTRA -> "The full effect palette: glass, moving waves, the longest animations, the highest image quality and the largest caches."
    }
    override fun designDescription(language: DesignLanguage) = when (language) {
        DesignLanguage.SAMSUNG_ONEUI -> "AMOLED black, a huge centered title and a floating frosted bar like the Samsung Gallery"
        DesignLanguage.GOOGLE_EXPRESSIVE -> "Google Photos with a Gemini feel: deep black with a coloured glow, a floating pill bar and glowing glass cards"
        DesignLanguage.IOS_LIQUID_GLASS -> "Clear liquid glass over edge-to-edge photos, floating glass controls and centered dialogs"
        DesignLanguage.XIAOMI_HYPEROS -> "Big thin titles, rounded album covers, stacked menu cards, a full tab bar and dialogs at the bottom"
    }
    override fun paletteLabel(palette: AppColorPalette) = when (palette.id) {
        CUSTOM_PALETTE_ID -> customPalette
        "oneui_blue" -> "OneUI Blue"
        "oneui_lavender" -> "OneUI Lavender"
        "oneui_green" -> "OneUI Green"
        "oneui_light_blue" -> "OneUI Light Blue"
        "oneui_light_lavender" -> "OneUI Light Lavender"
        "oneui_light_green" -> "OneUI Light Green"
        "expressive_mint" -> "Gemini Blue"
        "expressive_violet" -> "Gemini Violet"
        "expressive_amber" -> "Gemini Green"
        "expressive_light_mint" -> "Gemini Light Blue"
        "expressive_light_violet" -> "Gemini Light Violet"
        "expressive_light_amber" -> "Gemini Light Green"
        "glass_blue" -> "Glass Blue"
        "glass_purple" -> "Glass Purple"
        "glass_mint" -> "Glass Mint"
        "glass_light" -> "Glass Light Blue"
        "glass_light_purple" -> "Glass Light Purple"
        "glass_light_mint" -> "Glass Light Mint"
        "hyperos_light" -> "HyperOS Light Blue"
        "hyperos_orange" -> "HyperOS Light Orange"
        "hyperos_green" -> "HyperOS Light Green"
        "hyperos_dark" -> "HyperOS Dark Blue"
        "hyperos_dark_orange" -> "HyperOS Dark Orange"
        "hyperos_dark_green" -> "HyperOS Dark Green"
        else -> palette.label
    }

    override val permissionTitle = "Access to photos and videos"
    override val permissionDescription = "Allow access to your media library to show your photos and videos."
    override val permissionGrant = "Grant permissions"
    override val permissionWarning = "The app won't work properly while these permissions are missing:"
    override val permissionMissingMedia = "Access to photos and videos — without it the gallery can't show anything"
    override val permissionOpenSettings = "Open in settings"

    override val favoriteAdded = "Added to favorites"
    override val favoriteRemoved = "Removed from favorites"
    override fun movedToTrash(count: Int) = "Moved ${counted(count, "item", "items")} to the trash"
    override fun albumCreated(name: String, onSdCard: Boolean) =
        "Album '$name' created ${if (onSdCard) "on the SD card" else "in internal storage"}"
    override fun createError(message: String?) = "Could not create: $message"
    override val noItemsSelected = "No items selected"
    override val noItemsToMove = "No items found to move"
    override fun moveStarted(albumName: String) = "Moving to '$albumName'..."
    override fun movedItems(count: Int, albumName: String) = "Moved ${counted(count, "item", "items")} to '$albumName'"
    override fun albumHidden(name: String) = "Album '$name' hidden"
    override fun albumsHidden(count: Int) = "$count albums hidden"
    override fun migratingAlbums(count: Int, toSdCard: Boolean) =
        "Moving $count albums to ${if (toSdCard) "the SD card" else "internal storage"} …"
    override fun migratedAlbums(count: Int, toSdCard: Boolean) =
        "Moved $count albums to ${if (toSdCard) "the SD card" else "internal storage"}"
    override fun migratedAlbumsPartly(moved: Int, failed: Int) =
        "Moved $moved files, $failed could not be moved"
    override val selectAlbums = "Select"
    override val hideSelected = "Hide"
    override val moveSelected = "Move"
    override val moveAlbumsTitle = "Move where?"
    override val albumUnhidden = "Album shown again"
    override val itemTrashed = "Moved to trash"
    override val restored = "Restored"
    override val allRestored = "All items restored"
    override val deletedPermanently = "Deleted permanently"
    override val trashEmptied = "Trash emptied"
    override fun fileRenamed(name: String) = "File renamed to $name"
    override fun error(message: String?) = "Error: $message"
    override val actionSucceeded = "Done"
    override fun subAlbumCreated(name: String, parentName: String, onSdCard: Boolean) =
        "Sub-album '$name' created in '$parentName' ${if (onSdCard) "on the SD card" else "in internal storage"}"
    override fun albumRenamed(name: String) = "Album renamed to '$name'"
    override fun renameError(message: String?) = "Could not rename: $message"
    override fun albumDeleted(name: String) = "Album '$name' deleted"
    override fun deleteError(message: String?) = "Could not delete: $message"
    override fun migrating(name: String, toSdCard: Boolean) =
        "Moving '$name' to ${if (toSdCard) "the SD card" else "internal storage"}..."
    override fun migrated(name: String, toSdCard: Boolean) =
        "Album '$name' moved to ${if (toSdCard) "the SD card" else "internal storage"}"
    override fun migrationError(message: String?) = "Migration failed: $message"
    override fun migratedPartly(name: String, toSdCard: Boolean, moved: Int, failed: Int) =
        "Moved ${counted(moved, "item", "items")} of '$name' to ${if (toSdCard) "the SD card" else "internal storage"}; $failed could not be moved and are still where they were"
    override fun notEnoughSpace(neededBytes: Long, freeBytes: Long) =
        "Not enough space: ${gigabytes(neededBytes)} needed, ${gigabytes(freeBytes)} free"
    override val movingMediaTitle = "Moving media…"
    override fun progressOf(current: Int, total: Int) = "$current of $total"

    override val sdCardNotAvailable = "SD card not available"
    override val nameEmpty = "Name must not be empty."
    override val renameFailed = "Rename failed."
    override val permissionDenied = "Permission denied."
    override val renameFailedGeneric = "Error while renaming."
    override val sdCardNotWritable = "SD card not found or not writable."
    override val folderCreateFailed = "The folder could not be created."
    override val subFolderCreateFailed = "The sub-folder could not be created."
}

/** "1 copy" / "3 copies": the count with the matching singular or plural noun. */
private fun counted(count: Int, one: String, other: String) = "$count ${if (count == 1) one else other}"

/** A size as "1.4 GB" (one decimal), for storage messages. */
internal fun gigabytes(bytes: Long): String =
    String.format(java.util.Locale.getDefault(), "%.1f GB", bytes / 1_000_000_000.0)

/** "Internal storage", "SD card" or "USB storage". */
internal fun storageLabel(kind: com.example.amoledgallery.util.StorageKind): String = when (kind) {
    com.example.amoledgallery.util.StorageKind.INTERNAL -> strings.internalStorage
    com.example.amoledgallery.util.StorageKind.SD_CARD -> strings.sdCard
    com.example.amoledgallery.util.StorageKind.USB -> strings.usbStorage
}

/** A size in bytes as people read it: "850 MB", "1,4 GB". */
internal fun readableSize(bytes: Long): String {
    val locale = LanguageState.locale
    return when {
        bytes >= 1_000_000_000L -> String.format(locale, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(locale, "%.0f MB", bytes / 1_000_000.0)
        else -> String.format(locale, "%.0f KB", bytes / 1_000.0)
    }
}
