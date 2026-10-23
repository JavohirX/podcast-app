# Podcast

Yet another podcast player app.

A local Android podcast player. Metadata lives in a Room database on the device, and downloads live in app storage.

This one keeps the library on the phone. An RSS feed, a folder of audio files, and a YouTube video are the same kind of episode: the audio is stored on the device, and progress, favourites, and listened state work the same for all three. Playback reads those local files. There is no account and no streaming catalog.

Current version: 1.0.2 (`dev.javohir.podcast`).

## What it plays

- **RSS feeds.** Subscribe by URL, or share a feed link into the app. Episodes download before playback.
- **Folders on the device.** Pick a folder with the system file picker. The app scans it for `mp3`, `m4a`, `m4b`, `aac`, `ogg`, `opus`, `flac`, `wav`, and `wma`, then keeps the list updated.
- **YouTube.** Paste a video URL. Audio is resolved with [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), downloaded, and played like any other episode.

## Features

- New, Left off, and Favourites, with resume position and a listened state
- Bookmarks, playback speed from 0.5× to 2.0×, skip silence, and a sleep timer
- Background playback with a media notification (Media3)
- Search across the library
- OPML import and export
- Local backup and restore
- Optional deletion of listened downloads

## Stack

Kotlin, Jetpack Compose, Material 3, Media3, Room, WorkManager, Hilt, OkHttp, Coil, and DataStore. One Gradle module. Minimum SDK 26, compile and target SDK 35.

Design notes are in [`docs/`](docs/00-overview.md).

## Build

Install JDK 17 and the Android SDK. Android Studio sets both up. From this directory:

```bat
gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```sh
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

`release.ps1` adds 0.0.1 to `versionName` and 1 to `versionCode` in `app/version.properties`, then builds a minified APK into `releases/`. That release build is signed with the debug keystore, for sideloading.

## License

Copyright (C) 2026 Javohir

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

The full text is in [LICENSE](LICENSE).

YouTube support links NewPipe Extractor, which is GPL-3.0-or-later. This project uses the same license because of that dependency. AndroidX, OkHttp, Coil, and Hilt remain under the Apache License 2.0.
