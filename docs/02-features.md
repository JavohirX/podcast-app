# Feature Specifications

Each section: behavior, components involved, and edge cases. Entities referenced are defined in [01-data-model.md](01-data-model.md).

---

## 1. Local folder library (R2)

**Adding a folder**
1. User taps "Add folder" → `ACTION_OPEN_DOCUMENT_TREE` (SAF picker).
2. App calls `takePersistableUriPermission` (read) and inserts a `WatchedFolder` row.
3. Immediate first scan is enqueued.

**Scanning** (`FolderScanner`, runs via WorkManager)
- Traverse with `DocumentFile.fromTreeUri`, recursing if `includeSubfolders`.
- Accept extensions: `mp3, m4a, m4b, aac, ogg, opus, flac, wav, wma` (case-insensitive). Also accept by MIME `audio/*` when the provider reports one.
- For each new file, extract via `MediaMetadataRetriever`: title, artist, duration, embedded artwork (artwork bytes cached to app-private `artwork/` dir, referenced by `embedded://` URI). Fallback title = filename without extension.
- Apply the dedupe/move/missing rules from the data-model doc.
- **Scan triggers:** app cold start (throttled to ≥15 min since `lastScanAt`), manual pull-to-refresh on the folder screen, after adding a folder, periodic WorkManager job (every 12 h, idle constraints).

**Edge cases**
- Persisted URI permission revoked (folder deleted, SD card removed): mark folder rows `isMissing`, surface a banner "Folder unavailable — re-pick to restore" with a re-pick action that re-links by `displayName`.
- Huge folders: scan streams results in batches of 50 inserts per transaction; UI shows incremental progress.
- `MediaMetadataRetriever` failures (corrupt/unsupported file): keep the row with filename-only metadata; mark duration unknown until first playback resolves it.

---

## 2. RSS subscriptions (R1)

**Subscribing**
1. User pastes a feed URL (also accept URLs shared to the app via `ACTION_SEND`).
2. `RssClient` fetches (OkHttp, follows redirects, stores final URL), parses with `XmlPullParser`.
3. Supported dialects: RSS 2.0 with iTunes namespace (primary), Atom (best-effort). Per item, read: `title`, `guid`, `pubDate`, `description`/`content:encoded`, `enclosure url/length/type`, `itunes:duration`, `itunes:image`, `itunes:author`.
4. Items without an audio `enclosure` are skipped.
5. Insert `Feed` + upsert `Episode` rows (`downloadStatus = NONE`).

**Refreshing**
- Manual: pull-to-refresh on feed screen / "refresh all" on library screen.
- Automatic: periodic WorkManager job (default every 6 h, network-connected constraint, configurable; "wifi-only" honored).
- Conditional GET with `ETag`/`Last-Modified` to skip unchanged feeds.
- Upserts never overwrite playback state (rule in data-model doc).

**Edge cases**
- Malformed XML / HTML error pages: keep old data, set `lastFetchError`, show inline error chip on the feed.
- Duplicate guids within one document: first wins.
- Feed permanently moved (301): rewrite `Feed.url`.
- Date parsing: RFC-822 with lenient fallbacks; unparseable → `addedAt` as `publishedAt`.

---

## 3. Episode downloads (download-only playback for RSS)

RSS episodes **must be downloaded before they can play** (decided). Local files play in place.

**Flow**
1. Tap download on an episode → `downloadStatus = QUEUED`, enqueue a unique WorkManager `DownloadWorker` keyed by episode id.
2. Worker streams enclosure → `filesDir/episodes/<feedId>/<episodeId>.<ext>` (app-private: no storage permission, removed on uninstall). Supports HTTP `Range` resume after interruption; honors wifi-only setting via Work constraints.
3. Progress reported through WorkManager progress + a low-importance notification; UI shows per-episode progress ring.
4. On success: `downloadStatus = DONE`, `downloadedFilePath` set. On failure: `FAILED` with retry affordance (Work backoff for transient errors).

**Storage management**
- Settings → Storage screen: total usage per feed, "delete all listened downloads" action.
- Optional auto-delete: when an episode becomes `isListened`, delete the file and reset status to `NONE` (re-downloadable anytime). Default off.
- Deleting a download never deletes the Episode row or its progress.

---

## 4. Playback engine (R3, R4, R6)

`PlaybackService : MediaSessionService` hosting a single ExoPlayer. UI binds via `MediaController` — so the notification, lockscreen, Bluetooth buttons, and the app all share one state.

**Core behaviors**
- Foreground service with media notification (play/pause, seek ±, speed shown).
- Audio focus handled by ExoPlayer (`setAudioAttributes(..., handleAudioFocus = true)`): pause on loss, duck on transient.
- `becomingNoisy` handling: pause when headphones unplug.
- **Resume:** starting an episode seeks to `positionMs` minus `resumeRewindMs` (5 s context rewind, floor 0).
- **Queue/continuous play:** playing from a list loads that list as the queue (e.g. playing from "New" auto-advances to the next new episode). Toggle "stop after current" available.

**Progress persistence (R3)**
- `ProgressPersister` coroutine: while playing, write `positionMs`, `percentListened`, `lastPlayedAt` to Room every **5 s**, plus immediately on pause / seek / episode transition / service destroy. Survives process death by construction (DB is the truth).
- Exact `durationMs` corrected from ExoPlayer on first ready state (RSS durations are often wrong).

**Completion tagging (R4)**
- On `STATE_ENDED`, or whenever `percentListened ≥ completionThreshold` (default 0.99): set `isListened = true`, increment `playCount`. Episode drops out of New/Left-off automatically (queries filter it).
- Manual long-press actions: "mark listened" / "mark unlistened" (resets percent to 0 or keeps position — keeps position, resets `isListened` only).

**Speed control (R6)**
- `player.playbackParameters = PlaybackParameters(speed)` with speed ∈ {0.5, 0.6, …, 2.0} — **0.1 granularity, clamped to [0.5, 2.0]**, value always rounded to one decimal to avoid float drift.
- UI: tap the speed chip on the player → bottom sheet with − / + steppers, a snapping slider, and preset chips (1.0 / 1.2 / 1.5 / 1.8 / 2.0).
- Persistence: last-used speed stored in DataStore and applied to every new playback (global default). *(Per-feed speed override is a listed phase-2 nicety, column reserved.)*
- Pitch kept at 1.0 (timestretch, not chipmunk).

**Sleep timer**
- Options: 10/20/30/45/60 min, end of episode. Fades volume over the last 5 s, then pauses (position saved as usual). Survives in the service, not the UI.

**Skip silence**
- ExoPlayer `skipSilenceEnabled` toggle in the player overflow + settings default.

---

## 5. Category views (R5)

Backed 1:1 by the queries in the data-model doc. Home screen tabs:

| Tab | Contents | Sort |
|---|---|---|
| **New** | unfinished episodes, both sources | `publishedAt` DESC |
| **Left off** | `0 < percent < 1`, not listened | `percentListened` DESC (99 % first), tie-break `lastPlayedAt` DESC |
| **Favourites** | hearted episodes | `publishedAt` DESC |
| **Listened** | the R4 archive | `lastPlayedAt` DESC |

- Favourite toggle: heart icon on rows, episode detail, and player.
- Every row shows a thin progress bar + percent label so Left-off ordering is visually self-explanatory.
- Local-source rows show a folder badge; RSS rows show download state.

---

## 6. Search

Local search-as-you-type over `Episode.title`, `Episode.author`, `Feed.title` (Room `LIKE` query; FTS4 upgrade only if it ever feels slow). Scoped filter chips: All / RSS / Local / by feed.

---

## 7. OPML import & export

- **Import:** SAF file picker → parse OPML `<outline xmlUrl>` entries → subscribe to each (batched, progress shown).
- **Export:** generate OPML of all feeds → SAF "create document". Standard escape hatch to/from other podcast apps.

## 8. Backup & restore (fully local)

- **Export:** single JSON file (schema-versioned) with feeds, watched-folder URIs, episodes incl. playback state, bookmarks, settings → user-chosen location via SAF.
- **Import:** restores subscriptions and merges playback state by `(feedUrl, guid)` / `(treeUri, relativePath)` — merge keeps the higher `percentListened`. Folder permissions can't be restored automatically (Android limitation): restore flow lists folders needing a re-pick.

## 9. Bookmarks

"Bookmark" button in the player stamps current position (+ optional note). Episode detail lists bookmarks; tap to jump. Useful for long lectures/audiobooks in the local library.

## 10. Smaller essentials

- Seek buttons: back 10 s / forward 30 s (configurable), also in the notification.
- Mini-player docked above the bottom nav on all screens; swipe up → full player; swipe away → stop.
- "Continue listening" shortcut: app open resumes UI on the most recent `lastPlayedAt` episode in the mini-player (paused, never autoplays).
- Themes: light/dark/system + Material You dynamic color.
- Per-episode overflow: mark (un)listened, favourite, download/delete download, share title+link (RSS), view file location (local).

## Deliberately out of scope (v1)

Streaming, chapters (ID3/PSC), Android Auto, widgets, video podcasts, cloud anything. Chapters + per-feed speed are the first phase-2 candidates.
