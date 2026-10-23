# Data Model

Single Room database, one file in app-private storage (`/data/data/<pkg>/databases/podcast.db`). Nothing leaves the device; the backup feature (see [02-features.md](02-features.md#backup--restore)) exports a JSON snapshot to a user-chosen folder.

## Entity-relationship overview

```
Feed 1 ──── * Episode * ──── 1 WatchedFolder
                │                  (sourceType decides which parent is set)
                ├── embedded: PlaybackState (position, percent, listened…)
                ├── embedded: DownloadState (RSS only)
                └── 1 ──── * Bookmark
```

## Entities

### `Feed` — an RSS subscription

| Column | Type | Notes |
|---|---|---|
| `id` | Long PK autogen | |
| `url` | String, **unique index** | Canonical feed URL after redirects |
| `title` | String | From `<title>` |
| `description` | String? | |
| `author` | String? | `<itunes:author>` |
| `imageUrl` | String? | Feed artwork URL (cached by Coil) |
| `link` | String? | Podcast homepage |
| `lastFetchAt` | Long? | epoch ms |
| `lastFetchError` | String? | shown in UI when refresh fails |
| `etag` / `lastModified` | String? | HTTP conditional-GET headers to avoid re-downloading unchanged XML |
| `addedAt` | Long | |

### `WatchedFolder` — a user-selected local folder

| Column | Type | Notes |
|---|---|---|
| `id` | Long PK autogen | |
| `treeUri` | String, **unique index** | SAF tree URI; permission persisted via `takePersistableUriPermission` |
| `displayName` | String | Folder name shown in UI |
| `includeSubfolders` | Boolean | default true |
| `lastScanAt` | Long? | |
| `addedAt` | Long | |

### `Episode` — the unified core entity (one row per RSS episode *or* local file)

Identity & source:

| Column | Type | Notes |
|---|---|---|
| `id` | Long PK autogen | Internal ID; what playback and UI use |
| `sourceType` | Enum `RSS` \| `LOCAL` | |
| `feedId` | Long? FK→Feed, `CASCADE` delete | set iff RSS |
| `folderId` | Long? FK→WatchedFolder, `CASCADE` delete | set iff LOCAL |
| `guid` | String? | RSS `<guid>` (fallback: enclosure URL). **Unique index on `(feedId, guid)`** — dedupe key on refresh |
| `relativePath` | String? | Path under the folder's tree URI. **Unique index on `(folderId, relativePath)`** — dedupe key on rescan |
| `fileSize` | Long? | LOCAL: from DocumentFile; used in moved-file heuristic |

Display metadata:

| Column | Type | Notes |
|---|---|---|
| `title` | String | RSS `<title>` / file tag title / filename fallback |
| `description` | String? | RSS show notes (HTML) / file comment tag |
| `author` | String? | |
| `publishedAt` | Long | RSS `pubDate`; LOCAL: file `lastModified`. **This drives the "New" sort** |
| `durationMs` | Long? | `itunes:duration` or extracted by retriever; corrected to exact value once ExoPlayer loads the media |
| `artworkUrl` | String? | episode/feed image URL, or `embedded://<episodeId>` sentinel for art pulled from the file |
| `addedAt` | Long | when the row was first created |

Playback state (embedded — always queried with the episode):

| Column | Type | Notes |
|---|---|---|
| `positionMs` | Long, default 0 | exact resume point |
| `percentListened` | Float, default 0 | `positionMs / durationMs`, denormalized **so Room can sort the Left-off view directly** |
| `isListened` | Boolean, default false | auto-set at completion threshold (default ≥ 99% or `STATE_ENDED`); manually togglable both ways |
| `lastPlayedAt` | Long? | drives "continue listening" ordering tie-breaks |
| `playCount` | Int, default 0 | |

Flags & file state:

| Column | Type | Notes |
|---|---|---|
| `isFavourite` | Boolean, default false | |
| `isMissing` | Boolean, default false | LOCAL file vanished on rescan — row kept (progress preserved) but hidden from lists; un-hidden if the file returns |
| `downloadStatus` | Enum `NONE`/`QUEUED`/`RUNNING`/`DONE`/`FAILED` | RSS only; LOCAL rows are implicitly playable |
| `downloadedFilePath` | String? | absolute path in app-private storage once `DONE` |
| `enclosureUrl` | String? | RSS audio URL |
| `enclosureSize` | Long? | `<enclosure length>` for download progress UI |

### `Bookmark`

| Column | Type |
|---|---|
| `id` | Long PK |
| `episodeId` | Long FK→Episode, `CASCADE` |
| `positionMs` | Long |
| `note` | String? |
| `createdAt` | Long |

### Settings (DataStore, not Room)

`defaultSpeed` (Float, 1.0), `completionThreshold` (0.99), `seekForwardMs` (30 000), `seekBackMs` (10 000), `resumeRewindMs` (5 000), `rssRefreshHours` (6), `wifiOnlyDownloads` (true), `autoDeleteListenedDownloads` (false), `skipSilence` (false), `theme`.

## Identity & dedupe rules

- **RSS:** an episode is the same episode iff `(feedId, guid)` matches. Missing `<guid>` → use enclosure URL as guid. Refresh = upsert: insert new guids, update metadata on existing ones, **never touch playback state columns** on update.
- **LOCAL:** identity is `(folderId, relativePath)`. Rescan diff: new path → insert; known path → refresh metadata if `lastModified` changed; path gone → set `isMissing = true` (don't delete; progress survives). **Moved-file heuristic:** before flagging missing, look for an inserted-this-scan row in the same folder with equal `(fileName, fileSize)` — if found, treat as a move: carry playback state over and delete the duplicate.

## Key queries (the category views)

```sql
-- NEW: everything not yet finished, newest first
SELECT * FROM episode
WHERE isListened = 0 AND isMissing = 0
  AND (sourceType = 'LOCAL' OR downloadStatus IS NOT NULL)   -- RSS rows always visible; play button gated on DONE
ORDER BY publishedAt DESC;

-- LEFT OFF: strictly in-progress, closest-to-finished first (99%, 98%, …)
SELECT * FROM episode
WHERE isListened = 0 AND isMissing = 0
  AND percentListened > 0 AND percentListened < 1.0
ORDER BY percentListened DESC, lastPlayedAt DESC;

-- FAVOURITES
SELECT * FROM episode
WHERE isFavourite = 1 AND isMissing = 0
ORDER BY publishedAt DESC;

-- LISTENED (archive of R4-tagged episodes)
SELECT * FROM episode
WHERE isListened = 1 AND isMissing = 0
ORDER BY lastPlayedAt DESC;
```

All four are Room `Flow<List<Episode>>` — the UI recomposes automatically when the playback service writes a position update.

Indexes: `(feedId, guid)` unique, `(folderId, relativePath)` unique, plus non-unique on `publishedAt`, `percentListened`, `isFavourite`, `isListened` to keep the category sorts index-backed.

## Migration policy

- Schema versioned from v1; `exportSchema = true` with schemas checked into the repo.
- Destructive migration **never** enabled — playback history is the user's most valuable data.
- Backup JSON (see features doc) doubles as a recovery path across reinstalls.
