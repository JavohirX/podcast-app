# UI & Navigation

Single-activity Compose app. Navigation via `androidx.navigation.compose`. Bottom navigation bar + a persistent mini-player docked above it.

## Navigation graph

```
MainActivity
└── NavHost
    ├── home            (bottom-nav tab 1: category pager)
    │   ├── tab: New
    │   ├── tab: Left off
    │   ├── tab: Favourites
    │   └── tab: Listened
    ├── library         (bottom-nav tab 2: sources)
    │   ├── feedDetail/{feedId}
    │   ├── folderDetail/{folderId}
    │   ├── addFeed         (dialog/sheet: paste URL, preview, confirm)
    │   └── (add folder = SAF intent, no destination)
    ├── search          (bottom-nav tab 3)
    ├── settings        (bottom-nav tab 4)
    │   ├── storage
    │   └── backupRestore
    ├── episodeDetail/{episodeId}      (from any list row)
    └── player                          (full-screen, slides over everything)
```

Deep entry points: `ACTION_SEND`/`ACTION_VIEW` with a feed URL → `addFeed` prefilled; media notification tap → `player`.

## Screens

### Home — category tabs (R5)
- `HorizontalPager` + `TabRow`: **New · Left off · Favourites · Listened**.
- Rows (`EpisodeListItem`): artwork, title, feed/folder badge, date, duration, **progress bar + percent**, download-state ring (RSS), heart. Tap = play (or episode detail if not downloaded yet); long-press = action sheet.
- Left-off tab is the headline feature: percent label is prominent on the trailing edge, list ordered 99 % → 1 %.
- Empty states per tab with a one-line explanation and a CTA (e.g. "Add a feed or folder" on New).

### Library
- Two sections: **Feeds** (artwork grid/list, unplayed-count badge, fetch-error chip) and **Folders** (name, file count, last scan, missing-warning).
- FAB → "Add RSS feed" / "Add folder".
- Pull-to-refresh = refresh all feeds + rescan all folders.

### Feed / Folder detail
- Header (artwork, title, author/path, refresh/rescan, unsubscribe/remove with confirm — warns that progress for those episodes is removed too).
- Episode list, filter chips: All / Unplayed / In progress / Downloaded.

### Episode detail
- Full show notes (rendered HTML for RSS), metadata, bookmarks list, big play/download button, favourite + listened toggles.

### Player (full screen)
- Artwork, title, scrubber with elapsed/remaining (remaining adjusts for speed), −10 s / play-pause / +30 s.
- **Speed chip ("1.4×") → speed bottom sheet: − / + steppers (0.1), snapping slider 0.5–2.0, presets.** (R6)
- Secondary row: sleep timer, bookmark, skip-silence, queue, favourite.
- Mini-player (all screens): artwork, title, thin progress line, play/pause; swipe up expands, swipe down dismisses (stops playback, position saved).

### Settings
- Playback: default speed, seek increments, resume rewind, completion threshold, skip silence.
- Feeds: refresh interval, wifi-only downloads, auto-delete listened downloads.
- Storage: usage per feed, bulk delete.
- Backup/restore, OPML import/export, theme, about.

## State & ViewModel conventions

- One ViewModel per screen; exposes a single `StateFlow<UiState>` (sealed: Loading / Content / Empty / Error) + an event channel for one-shots (snackbars, nav).
- Episode lists come straight from Room flows → live progress updates while audio plays (the 5-second persister ticks the percent bars in real time).
- `PlayerViewModel` wraps a `MediaController`; playback position in the UI is polled at 1 Hz from the controller (not the DB) for smooth scrubbing.
- Configuration change / process death: nav state via `rememberSaveable`; everything else rehydrates from Room — no in-memory caches to lose.

## Visual language

- Material 3, dynamic color (Material You) with a static fallback palette.
- List-first, dense but readable; progress visualization is the identity of the app (every episode row answers "how far in am I?" at a glance).
- All actions reachable one-handed: primary actions bottom-anchored (FAB, mini-player, sheets).
