# Roadmap, Project Skeleton, Testing & Risks

## Project skeleton (single Gradle module)

```
app/src/main/java/dev/javohir/podcast/
├── PodcastApp.kt                  // @HiltAndroidApp
├── MainActivity.kt
├── data/
│   ├── db/                        // AppDatabase, DAOs, entities, converters
│   ├── rss/                       // RssClient, RssParser (XmlPullParser), OpmlParser
│   ├── scanner/                   // FolderScanner, AudioMetadataReader, diff logic
│   ├── download/                  // DownloadWorker, DownloadRequester
│   ├── settings/                  // SettingsStore (DataStore)
│   └── repository/                // EpisodeRepository, FeedRepository, FolderRepository, BackupRepository
├── domain/                        // use cases: RefreshFeeds, ScanFolders, UpdateProgress,
│                                  // ToggleListened, BuildQueue, ExportBackup, ImportOpml…
├── playback/                      // PlaybackService, PlayerHolder, ProgressPersister, SleepTimer
├── work/                          // periodic Work scheduling (refresh, rescan), WorkInitializer
├── ui/
│   ├── theme/
│   ├── navigation/
│   ├── components/                // EpisodeListItem, MiniPlayer, SpeedSheet, ProgressBarSmall…
│   ├── home/  library/  feed/  folder/  episode/  player/  search/  settings/
└── di/                            // Hilt modules
```

Key Gradle dependencies (versions via libs.versions.toml): Compose BOM + M3, navigation-compose, media3-exoplayer / media3-session, room-runtime/ktx/compiler (KSP), hilt + hilt-navigation-compose, work-runtime-ktx + hilt-work, okhttp, coil-compose, datastore-preferences, kotlinx-coroutines.

Manifest: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (runtime ask on 13+), `WAKE_LOCK`; service declared with `mediaPlayback` type; **no** storage permissions (SAF + app-private storage cover everything).

## Milestones

Each milestone ends in an installable, usable APK.

**M1 — Walking skeleton: local files play (1 week-ish)**
Project setup (Hilt, Room, Compose, nav, theme) · WatchedFolder + SAF picker · FolderScanner v1 (no move-heuristic yet) · Episode list (flat, newest first) · PlaybackService + ExoPlayer, tap-to-play, notification, pause/resume/seek.
*Exit: pick a folder of mp3s, see them, play one in the background.*

**M2 — Progress & categories: the product thesis (the core differentiator)**
ProgressPersister (5 s writes, pause/seek/transition flushes) · resume-with-rewind · completion tagging + manual toggle · Home tabs New / Left off / Favourites / Listened with live progress bars · favourite toggle · speed control end-to-end (chip, sheet, 0.1 steps, persisted default).
*Exit: R3–R6 fully demonstrable on a local library.*

**M3 — RSS & downloads**
Add-feed flow + parser · feed/folder detail screens · manual + periodic refresh (conditional GET) · DownloadWorker with progress, resume, wifi-only · storage screen + delete/auto-delete · share-URL intent entry.
*Exit: subscribe, refresh, download, listen, progress tracked — both sources unified in the tabs.*

**M4 — Resilience & convenience**
Rescan diffing with missing/moved heuristics · folder-permission-lost recovery · search · queue/continuous play + stop-after-current · sleep timer · skip silence · bookmarks · mini-player polish, empty states, error chips.

**M5 — Data portability & release hygiene**
OPML import/export · JSON backup/restore with merge · settings completeness · app icon, about screen · R8/proguard pass · signed release APK.

## Testing strategy

| Layer | Approach |
|---|---|
| RSS/OPML parsing | Pure-JVM unit tests against a corpus of real-world fixture feeds (well-formed, iTunes-heavy, missing guids, bad dates, HTML-in-description, Atom) |
| Scanner diff logic | Unit tests on the diff function with fake file listings: add / remove / move / rename / size-collision cases |
| Progress & completion | Unit tests: threshold edges (98.9 % vs 99 %), `STATE_ENDED`, manual toggles, never-regress-on-feed-refresh |
| Speed clamp/rounding | Unit test the 0.5–2.0 / 0.1-step quantizer |
| DAO queries | Room instrumentation tests (in-memory DB) for the four category sorts — esp. Left-off ordering & tie-breaks |
| Download worker | WorkManager `TestDriver` + OkHttp `MockWebServer` (success, mid-stream failure + Range resume, 404) |
| Playback service | Manual test checklist: audio focus (incoming call), noisy-disconnect, notification controls, process-death resume, Bluetooth buttons |
| UI | A handful of Compose tests on `EpisodeListItem` states + speed sheet; manual passes otherwise |

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| SAF tree traversal is slow on huge folders (`DocumentFile` does an IPC per file) | Use `DocumentsContract` query-based traversal instead of naive `DocumentFile.listFiles()` for the scan path; batch DB writes |
| SAF permission silently revoked (SD card swap, OEM cleaners) | Detect on scan, non-destructive `isMissing` flagging, guided re-pick flow |
| RSS dialect chaos (broken XML, weird dates, missing guids) | Lenient parser + fixture-driven tests; never let one bad item kill a refresh; per-feed error surfacing |
| Wrong durations from feeds break percent math | Treat feed duration as provisional; overwrite with ExoPlayer's measured duration on first play |
| Progress lost on crash/forced-stop | 5 s write cadence bounds loss; DB is source of truth, no in-memory-only state |
| Background restrictions (Doze, OEM battery killers) kill periodic refresh | Refresh is best-effort + always refresh-on-open; downloads are user-initiated foreground-ish Work |
| `wav`/`wma` edge formats | ExoPlayer covers wav/flac/ogg/opus natively; wma is *not* supported — detect codec failure and show "unsupported format" row state rather than crashing |
| Float drift in 0.1 speed steps | Store speed as `Int` tenths internally (5–20), render `/10f` |

## Definition of "architecture done" → first code step

Everything above is decided; M1 starts by generating the project scaffold (Gradle, Hilt, theme, nav) and the Room schema from [01-data-model.md](01-data-model.md) verbatim.
