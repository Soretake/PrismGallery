package com.example.amoledgallery.ui.i18n

/**
 * Texts of the 1.2 features (tools, viewer extras, editor additions), kept apart from [AppStrings] so the
 * long interface does not have to grow by a hundred entries. English first, then German.
 */
object F {
    private val en: Boolean get() = LanguageState.language == AppLanguage.ENGLISH
    private fun t(english: String, german: String) = if (en) english else german
    private fun n(count: Int, oneEn: String, otherEn: String, oneDe: String, otherDe: String) =
        "$count " + if (en) (if (count == 1) oneEn else otherEn) else (if (count == 1) oneDe else otherDe)

    // ---- Tools hub ----------------------------------------------------------------------------------
    val tools get() = t("Tools", "Werkzeuge")
    val toolsSubtitle get() = t("Search, places, clean-up and more", "Suche, Orte, Aufräumen und mehr")
    val findSection get() = t("Find", "Finden")
    val cleanSection get() = t("Clean up", "Aufräumen")
    val createSection get() = t("Create", "Erstellen")
    val personalizeSection get() = t("Personalize", "Personalisieren")

    // ---- Search ------------------------------------------------------------------------------------
    val search get() = t("Search", "Suche")
    val searchHint get() = t("Name, place, date, album, video…", "Name, Ort, Datum, Album, Video…")
    val searchEmptyTitle get() = t("Nothing found", "Nichts gefunden")
    val searchEmptyMessage get() = t("Try a place, a month, a year or a file name.", "Versuche einen Ort, einen Monat, ein Jahr oder einen Dateinamen.")
    val searchStartMessage get() = t(
        "Search runs on this device only - by file name, album, place, date and type.",
        "Die Suche läuft nur auf diesem Gerät - nach Dateiname, Album, Ort, Datum und Art."
    )
    fun results(count: Int) = n(count, "result", "results", "Treffer", "Treffer")

    // ---- Places --------------------------------------------------------------------------------------
    val places get() = t("Places", "Orte")
    val placesEmptyTitle get() = t("No places yet", "Noch keine Orte")
    val placesEmptyMessage get() = t(
        "Photos with a location appear here, grouped by town. Place names are looked up offline.",
        "Fotos mit Standort erscheinen hier, nach Ort sortiert. Ortsnamen werden offline ermittelt."
    )
    val placesScanning get() = t("Reading locations…", "Standorte werden gelesen…")
    val geoNamesCredit get() = t("Place names: GeoNames (CC BY 4.0)", "Ortsnamen: GeoNames (CC BY 4.0)")
    val near get() = t("near", "bei")

    // ---- On this day ---------------------------------------------------------------------------------
    val onThisDay get() = t("On this day", "An diesem Tag")
    val onThisDayEmptyTitle get() = t("No memories today", "Heute keine Erinnerungen")
    val onThisDayEmptyMessage get() = t(
        "Photos taken on this date in earlier years show up here.",
        "Fotos, die an diesem Datum in früheren Jahren entstanden sind, erscheinen hier."
    )
    fun yearsAgo(years: Int) = if (years == 1) t("1 year ago", "Vor 1 Jahr") else t("$years years ago", "Vor $years Jahren")

    // ---- Slideshow -----------------------------------------------------------------------------------
    val slideshow get() = t("Slideshow", "Diashow")
    val slideInterval get() = t("Show each photo for", "Jedes Foto zeigen für")
    fun seconds(s: Int) = t("$s s", "$s s")
    val loop get() = t("Loop", "Wiederholen")
    val off get() = t("off", "aus")
    val start get() = t("Start", "Starten")
    val shuffle get() = t("Shuffle", "Zufällig")

    // ---- Viewer extras -------------------------------------------------------------------------------
    val editDateTime get() = t("Edit date & time", "Datum & Uhrzeit ändern")
    val editLocation get() = t("Edit location", "Standort bearbeiten")
    val removeLocation get() = t("Remove location", "Standort entfernen")
    val setLocation get() = t("Set location", "Standort festlegen")
    val searchTown get() = t("Search a town", "Ort suchen")
    val locationRemoved get() = t("Location removed", "Standort entfernt")
    val locationSaved get() = t("Location saved", "Standort gespeichert")
    val dateSaved get() = t("Date & time saved", "Datum & Uhrzeit gespeichert")
    val onlyPhotos get() = t("This only works for photos (JPEG, PNG, WebP).", "Das geht nur bei Fotos (JPEG, PNG, WebP).")
    val shareOptions get() = t("Share options", "Teilen-Optionen")
    val shareOriginal get() = t("Share original", "Original teilen")
    val shareWithoutLocation get() = t("Share without location", "Ohne Standort teilen")
    val shareAsJpeg get() = t("Share as JPEG", "Als JPEG teilen")
    val shareSmaller get() = t("Share smaller (max. 2048 px)", "Kleiner teilen (max. 2048 px)")
    val copyToAlbum get() = t("Copy to album", "In Album kopieren")
    fun copiedItems(count: Int, album: String) = t("Copied ${n(count, "item", "items", "", "")} to '$album'", "${n(count, "Element", "Elemente", "Element", "Elemente")} nach '$album' kopiert")
    val setAs get() = t("Use as… (contact photo, etc.)", "Verwenden als… (Kontaktbild usw.)")
    val print get() = t("Print", "Drucken")
    val lockRotation get() = t("Lock rotation", "Drehung sperren")
    val unlockRotation get() = t("Unlock rotation", "Drehung entsperren")
    val saveFrame get() = t("Save current frame", "Aktuelles Bild speichern")
    val frameSaved get() = t("Frame saved to Pictures/Video captures", "Bild in Pictures/Video captures gespeichert")
    val playbackSpeed get() = t("Playback speed", "Wiedergabegeschwindigkeit")
    val mute get() = t("Mute", "Stumm")
    val unmute get() = t("Sound on", "Ton an")
    val motionPhoto get() = t("Motion photo", "Bewegtes Foto")
    val playMotion get() = t("Play motion", "Bewegung abspielen")
    val exportMotionVideo get() = t("Save motion as video", "Bewegung als Video speichern")
    val videoSaved get() = t("Video saved", "Video gespeichert")
    val hdrDisplay get() = t("HDR photos", "HDR-Fotos")
    val hdrDisplaySubtitle get() = t("Show HDR photos with their full brightness", "HDR-Fotos mit voller Helligkeit zeigen")
    val failed get() = t("That didn't work", "Das hat nicht geklappt")

    // ---- Private album -------------------------------------------------------------------------------
    val privateAlbum get() = t("Private album", "Privates Album")
    val moveToPrivate get() = t("Move to private album", "Ins private Album verschieben")
    val privateSubtitle get() = t("Locked with a PIN, invisible to other apps", "Mit PIN gesperrt, für andere Apps unsichtbar")
    val setPin get() = t("Set a PIN", "PIN festlegen")
    val enterPin get() = t("Enter PIN", "PIN eingeben")
    val repeatPin get() = t("Repeat PIN", "PIN wiederholen")
    val wrongPin get() = t("Wrong PIN", "Falsche PIN")
    val pinsDiffer get() = t("The PINs don't match", "Die PINs stimmen nicht überein")
    val pinHint get() = t("4 to 8 digits", "4 bis 8 Ziffern")
    val unlockWithBiometrics get() = t("Use fingerprint", "Fingerabdruck verwenden")
    val privateEmptyTitle get() = t("Nothing private yet", "Noch nichts Privates")
    val privateEmptyMessage get() = t(
        "Select photos and choose \"Move to private album\". They are moved into the app's own storage, where no other app can see them.",
        "Wähle Fotos aus und tippe auf \"Ins private Album verschieben\". Sie werden in den eigenen Speicher der App verschoben, wo keine andere App sie sieht."
    )
    val restoreFromPrivate get() = t("Move back to gallery", "Zurück in die Galerie")
    val privateWarning get() = t(
        "Private photos are stored inside the app. Uninstalling the app or clearing its data deletes them.",
        "Private Fotos liegen in der App. Wird die App deinstalliert oder ihr Speicher gelöscht, sind sie weg."
    )
    fun movedToPrivate(count: Int) = t("${n(count, "item", "items", "", "")} moved to the private album", "${n(count, "Element", "Elemente", "Element", "Elemente")} ins private Album verschoben")
    fun restoredFromPrivate(count: Int) = t("${n(count, "item", "items", "", "")} moved back to Pictures/Restored", "${n(count, "Element", "Elemente", "Element", "Elemente")} nach Pictures/Restored zurückverschoben")
    val pullForPrivate get() = t("Pull down to open the private album", "Herunterziehen für das private Album")
    val releaseForPrivate get() = t("Release to open the private album", "Loslassen für das private Album")

    // ---- Other albums / album extras -----------------------------------------------------------------
    val otherAlbums get() = t("Other albums", "Weitere Alben")
    val moveToOtherAlbums get() = t("Move to Other albums", "Zu Weitere Alben")
    val removeFromOtherAlbums get() = t("Back to main albums", "Zurück zu den Hauptalben")
    val otherAlbumsEmpty get() = t("Albums you rarely open can be tucked away here from their menu.", "Selten genutzte Alben kannst du über ihr Menü hierher auslagern.")
    val addToHomeScreen get() = t("Add to home screen", "Zum Startbildschirm")
    val listView get() = t("List view", "Listenansicht")
    val gridView get() = t("Grid view", "Rasteransicht")
    val cameraOnly get() = t("Only camera photos", "Nur Kamerafotos")
    val allItems get() = t("All items", "Alle Objekte")
    val viewDays get() = t("Days", "Tage")
    val viewMonths get() = t("Months", "Monate")
    val viewYears get() = t("Years", "Jahre")
    val albumGroups get() = t("Album groups", "Albumgruppen")
    val newGroup get() = t("New group", "Neue Gruppe")
    val groupName get() = t("Group name", "Gruppenname")
    val addToGroup get() = t("Add to group", "Zu Gruppe hinzufügen")
    val removeFromGroup get() = t("Remove from group", "Aus Gruppe entfernen")
    val deleteGroup get() = t("Delete group", "Gruppe löschen")
    fun albumsCount(count: Int) = n(count, "album", "albums", "Album", "Alben")

    // ---- Clean up ------------------------------------------------------------------------------------
    val similarPhotos get() = t("Similar photos", "Ähnliche Fotos")
    val similarSubtitle get() = t("Bursts and near-identical shots", "Serienbilder und fast gleiche Aufnahmen")
    val similarScanning get() = t("Comparing photos…", "Fotos werden verglichen…")
    val similarEmptyTitle get() = t("No similar photos", "Keine ähnlichen Fotos")
    val similarEmptyMessage get() = t("Series of near-identical shots appear here so you can keep the best one.", "Reihen fast gleicher Aufnahmen erscheinen hier, damit du die beste behalten kannst.")
    fun similarSet(count: Int) = t("$count similar photos", "$count ähnliche Fotos")
    val keepBest get() = t("Best", "Beste")
    val cleanUp get() = t("Clean-up suggestions", "Aufräum-Vorschläge")
    val cleanUpSubtitle get() = t("Old screenshots, large videos, RAW files", "Alte Screenshots, große Videos, RAW-Dateien")
    val oldScreenshots get() = t("Screenshots older than 30 days", "Screenshots älter als 30 Tage")
    val largeVideos get() = t("Large videos (over 100 MB)", "Große Videos (über 100 MB)")
    val rawFiles get() = t("RAW files", "RAW-Dateien")
    val blurryOrDark get() = t("Very dark photos", "Sehr dunkle Fotos")
    val nothingToClean get() = t("Nothing to clean up here.", "Hier gibt es nichts aufzuräumen.")
    fun selectedFrees(count: Int, size: String) = t("${n(count, "item", "items", "", "")} selected · frees $size", "${n(count, "Element", "Elemente", "Element", "Elemente")} ausgewählt · gibt $size frei")
    val moveToTrash get() = t("Move to trash", "In den Papierkorb")

    // ---- Create --------------------------------------------------------------------------------------
    val collage get() = t("Collage", "Collage")
    val collageSubtitle get() = t("2 to 6 photos in one picture", "2 bis 6 Fotos in einem Bild")
    val gif get() = t("GIF", "GIF")
    val gifSubtitle get() = t("Animated GIF from several photos", "Animiertes GIF aus mehreren Fotos")
    val pdf get() = t("PDF", "PDF")
    val pdfSubtitle get() = t("A PDF document with one photo per page", "Ein PDF mit einem Foto pro Seite")
    fun selectPhotos(min: Int, max: Int) = t("Select $min to $max photos", "Wähle $min bis $max Fotos")
    fun selectedCount(count: Int) = t("$count selected", "$count ausgewählt")
    val next get() = t("Next", "Weiter")
    val layout get() = t("Layout", "Anordnung")
    val spacing get() = t("Spacing", "Abstand")
    val frameDelay get() = t("Time per frame", "Zeit pro Bild")
    val creating get() = t("Creating…", "Wird erstellt…")
    fun savedTo(path: String) = t("Saved to $path", "Gespeichert in $path")
    val pageSize get() = t("Page size", "Seitenformat")
    val fitToPhoto get() = t("Photo size", "Fotogröße")

    // ---- Personalize ---------------------------------------------------------------------------------
    val slideshowWallpaper get() = t("Slideshow wallpaper", "Diashow-Hintergrund")
    val slideshowWallpaperSubtitle get() = t("Photos of an album change on your home screen", "Fotos eines Albums wechseln auf dem Startbildschirm")
    val chooseAlbum get() = t("Album", "Album")
    val changeEvery get() = t("Change every", "Wechseln alle")
    fun minutes(m: Int) = t("$m min", "$m Min.")
    fun hours(h: Int) = t("$h h", "$h Std.")
    val apply get() = t("Set as wallpaper", "Als Hintergrund festlegen")
    val photoWidget get() = t("Photo widget", "Foto-Widget")
    val photoWidgetSubtitle get() = t("Long-press your home screen > Widgets > Prism Gallery", "Startbildschirm lange drücken > Widgets > Prism Gallery")
    val photoWidgetHelp get() = t(
        "The widget shows a photo from your favorites (or recent photos) and changes it every hour. Tap it to open the gallery.",
        "Das Widget zeigt ein Foto aus deinen Favoriten (oder neuen Fotos) und wechselt es stündlich. Tippe darauf, um die Galerie zu öffnen."
    )
    val favoritesOrRecent get() = t("Favorites (or recent photos)", "Favoriten (oder neue Fotos)")

    // ---- Editor additions ----------------------------------------------------------------------------
    val adjust get() = t("Adjust", "Anpassen")
    val filters get() = t("Filters", "Filter")
    val draw get() = t("Draw", "Zeichnen")
    val text get() = t("Text", "Text")
    val watermark get() = t("Watermark", "Wasserzeichen")
    val watermarkHint get() = t("e.g. ONLY FOR APARTMENT APPLICATION", "z. B. NUR FÜR WOHNUNGSBEWERBUNG")
    val brightness get() = t("Brightness", "Helligkeit")
    val contrast get() = t("Contrast", "Kontrast")
    val saturation get() = t("Saturation", "Sättigung")
    val warmth get() = t("Warmth", "Wärme")
    val vignette get() = t("Vignette", "Vignette")
    val original get() = t("Original", "Original")
    val vivid get() = t("Vivid", "Lebhaft")
    val mono get() = t("Mono", "Schwarzweiß")
    val sepia get() = t("Sepia", "Sepia")
    val cool get() = t("Cool", "Kühl")
    val warm get() = t("Warm", "Warm")
    val fade get() = t("Fade", "Verblasst")
    val noir get() = t("Noir", "Noir")
    val copyEdits get() = t("Copy edits", "Bearbeitungen kopieren")
    val pasteEdits get() = t("Paste edits", "Bearbeitungen einfügen")
    val editsCopied get() = t("Edits copied - select photos and paste them", "Bearbeitungen kopiert - wähle Fotos und füge sie ein")
    fun editsPasted(count: Int) = t("Edited copies of ${n(count, "photo", "photos", "", "")} saved", "Bearbeitete Kopien von ${n(count, "Foto", "Fotos", "Foto", "Fotos")} gespeichert")
    val undo get() = t("Undo", "Rückgängig")
    val enterText get() = t("Enter text", "Text eingeben")
    val size get() = t("Size", "Größe")
    val color get() = t("Color", "Farbe")
    val trim get() = t("Trim", "Kürzen")
    val compressVideo get() = t("Make smaller", "Verkleinern")
    val compressVideoTitle get() = t("Video size", "Videogröße")
    fun compressTo(p: Int) = t("${p}p", "${p}p")
    val keepResolution get() = t("Keep resolution", "Auflösung behalten")
}
