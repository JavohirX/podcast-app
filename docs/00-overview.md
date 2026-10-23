# Podcast App — Architecture Overview

## Vision

A **fully local** Android podcast player. Two content sources only:

1. **RSS feeds** — subscribed by URL, episodes are *downloaded* before playback (download-only, no streaming).
2. **Local audio files** — the user picks folders on the device; the app scans them for audio files (mp3, wav, m4a, …) and treats each file as a podcast episode.

No accounts, no cloud sync, no analytics, no third-party APIs. The only network traffic is fetching RSS XML, downloading episode enclosures, and fetching feed artwork.

## Core requirements (from product owner)

| # | Requirement |
|---|-------------|
| R1 | Subscribe to podcasts via RSS URL |
| R2 | Pick local folders; detect audio files (mp3, wav, etc.) and add them to the collection |
| R3 | Per-episode progress tracking: how much listened, exact resume position |
| R4 | Auto-tag episodes that have been fully listened |
| R5 | Category views: **New** (sorted newest first), **Left off** (in-progress, sorted by percent listened descending — 99% at top), **Favourites** |
| R6 | Playback speed 0.5×–2.0× in 0.1 steps |
| R7 | Everything on-device: local SQLite file for metadata, app storage for downloads |

Additional features chosen as essential (detailed in [02-features.md](02-features.md)): background playback with media notification, sleep timer, skip-silence, OPML import/export, local backup/restore, search, auto-delete of listened downloads, bookmarks.

## Tech stack (decided)

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin | Android standard |
| UI | Jetpack Compose + Material 3 | Modern declarative UI, dynamic color |
| Architecture | MVVM + Repository, single Gradle module | Solo-dev friendly; layering by package, not module |
| Playback | Media3 (ExoPlayer + `MediaSessionService`) | Background playback, notification/lockscreen/Bluetooth controls, `PlaybackParameters` for speed, built-in skip-silence |
| Persistence | Room (SQLite) | Single on-device DB file, fast sorted queries for the category views |
| Background work | WorkManager | RSS refresh, episode downloads, folder rescans — survives process death |
| HTTP | OkHttp | RSS fetch + enclosure downloads with resume support |
| RSS parsing | `XmlPullParser` (Android built-in) | Zero extra dependency; RSS 2.0 + iTunes namespace is simple enough |
| Folder access | Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) | Only sanctioned way to get persistent access to arbitrary user folders on modern Android |
| Audio metadata | `MediaMetadataRetriever` | Duration, title, artist, embedded artwork from local files |
| Images | Coil | Feed/episode artwork loading + disk cache |
| DI | Hilt | Standard, low ceremony |
| Settings | DataStore (Preferences) | Speed default, thresholds, refresh interval, etc. |
| Min SDK | 26 (Android 8.0) | Covers ~98% of devices; required for notification channels; SAF mature |
| Target SDK | Latest stable | Play-store style hygiene even for sideloaded builds |

## Layering

```
┌─────────────────────────────────────────────────────┐
│ UI layer (Compose)                                  │
│  screens, navigation, ViewModels, MiniPlayer        │
└──────────────────────┬──────────────────────────────┘
                       │ StateFlow / events
┌──────────────────────▼──────────────────────────────┐
│ Domain layer                                        │
│  use cases: ScanFolders, RefreshFeed, DownloadEp,   │
│  UpdateProgress, BuildCategoryLists, Import/Export  │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│ Data layer                                          │
│  EpisodeRepository, FeedRepository, FolderRepository│
│  ├ Room DAOs (single source of truth)               │
│  ├ RssClient (OkHttp + XmlPullParser)               │
│  ├ FolderScanner (SAF/DocumentFile)                 │
│  ├ DownloadManager (WorkManager + OkHttp)           │
│  └ SettingsStore (DataStore)                        │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│ Playback (parallel vertical)                        │
│  PlaybackService : MediaSessionService              │
│  ├ ExoPlayer (speed, skip-silence, audio focus)     │
│  ├ ProgressPersister (position → Room every 5 s)    │
│  └ SleepTimer                                       │
└─────────────────────────────────────────────────────┘
```

**Single source of truth = Room.** The UI never reads the network or the filesystem directly; scanners/fetchers write into Room, and all screens observe Room `Flow`s. This is what makes the category views (New / Left off / Favourites) trivial: each is just a Room query (see [01-data-model.md](01-data-model.md)).

**Unified episode model.** Local files and RSS episodes converge into one `Episode` entity with a `sourceType` discriminator. Progress tracking, categories, favourites, and playback are written once and work identically for both sources.

## Document map

| Doc | Contents |
|---|---|
| [01-data-model.md](01-data-model.md) | Room entities, identity rules, key queries, migrations |
| [02-features.md](02-features.md) | Feature-by-feature specs: scanning, RSS, downloads, playback, progress, categories, speed, extras |
| [03-ui-navigation.md](03-ui-navigation.md) | Screen inventory, navigation graph, key components, state handling |
| [04-roadmap.md](04-roadmap.md) | Build milestones, project skeleton, testing strategy, risks & edge cases |
