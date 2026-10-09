# Prism Gallery

A fast, private photo and video gallery for Android, with four complete design styles and smooth animations.
It works entirely offline: no internet permission, no ads, no tracking, no account.

Prism Gallery is made for:

- custom ROMs that ship without a usable gallery app,
- phones whose only gallery is a cloud photo app,
- anyone who wants a smooth, good-looking gallery that keeps their photos private.

## Features

- **Four design styles** with their own layouts and animations, inspired by iOS (Liquid Glass), Samsung One UI,
  Xiaomi HyperOS and Google Gemini
- Photos, videos and GIFs from internal storage and the SD card
- Albums: create, rename, hide, sub-albums, move media between albums, move whole albums between internal storage
  and SD card
- Albums your way: groups, "Other albums" for rarely used ones, grid or list view, home-screen shortcuts
- Favorites, trash, a private album (PIN, fingerprint or screen lock; stored inside the app)
- Timeline by day, month or year; camera photos only, if you like
- Search by name, album, place, date or type; Places and "On this day" - all on the device
- Deep zoom, readable even on huge photos and very long scroll screenshots
- Viewer: slideshow, motion photos, HDR photos, video speed / loop / mute, save a video frame, rotation lock
- Editor (always saved as a copy): crop, rotate, mirror, brightness, contrast, saturation, warmth, vignette,
  filters, drawing, text and a protective watermark; copy edits onto other photos. Videos: trim, make smaller,
  brightness, contrast, saturation
- Change a photo's date, time and location, or remove the location; share without location, as JPEG or smaller
- Create collages, animated GIFs and PDFs; print
- Clean up: duplicates, similar photos and bursts, old screenshots, large videos, RAW files
- Slideshow wallpaper, video/GIF live wallpaper and a photo widget
- Storage overview for internal storage and SD card
- Open photos and videos from other apps ("Open with")
- English and German
- Adjustable graphics quality for older or low-memory devices

## Privacy

Prism Gallery has no internet permission, so it cannot send anything anywhere. It only asks for access to your
photos and videos. Settings stay on your device. Place names are looked up in a list of towns bundled with the app,
never online. The private album and its PIN are excluded from backups.

## Requirements

Android 10 (API 29) or newer.

## Download

- **GitHub Releases:** https://github.com/Soretake/PrismGallery/releases
- **F-Droid:** coming soon

### Verifying the APK

APKs from GitHub Releases are signed with this certificate (SHA-256):

```
A5:66:D9:9F:38:36:C3:6E:77:BE:C4:EA:44:62:AC:8F:E6:FF:30:9F:67:F1:64:97:F0:87:F8:D0:3D:9E:EF:9F
```

You can check it with `apksigner verify --print-certs app-release.apk`, or let
[AppVerifier](https://github.com/soupslurpr/AppVerifier) or [Obtainium](https://github.com/ImranR98/Obtainium) do it.
The build is reproducible: F-Droid builds the app from source and publishes the APK signed with this same key, so
installs from F-Droid and from GitHub can update each other.

## Building

Requirements: JDK 17 and the Android SDK (compile SDK 34).

```sh
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. Without a signing key the release APK is unsigned.
To sign it, create a `keystore.properties` file in the project root (it is git-ignored and must never be committed):

```properties
storeFile=/path/to/your-release-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Disclaimer

Prism Gallery is an independent project and is not affiliated with, endorsed by or connected to Apple, Samsung,
Xiaomi or Google. Their names are only used to describe the visual style each design is inspired by.

## Credits

Place names: [GeoNames](https://www.geonames.org/) (towns with 5000+ inhabitants), licensed under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

## License

Copyright (C) 2026 Soretake

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [GNU General Public License](LICENSE) for
more details.
