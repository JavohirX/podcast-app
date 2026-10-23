package dev.javohir.podcast.data.scanner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.db.dao.FolderDao
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.db.entities.WatchedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac", "wav", "wma"
)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/** Preferred folder-image names, in priority order. */
private val COVER_NAMES = listOf("cover", "folder", "front", "albumart", "album")

private data class DirEntry(
    val docId: String,
    val name: String,
    val mime: String,
    val lastModified: Long,
    val size: Long
)

private const val BATCH_SIZE = 50

@Singleton
class FolderScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val folderDao: FolderDao,
    private val metadataReader: AudioMetadataReader
) {

    suspend fun scanFolder(folder: WatchedFolder) = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folder.treeUri)
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) { return@withContext }

        val existingEpisodes = episodeDao.getByFolder(folder.id).associateBy { it.relativePath }.toMutableMap()
        val foundPaths = mutableSetOf<String>()
        val toInsert = mutableListOf<Episode>()

        traverseDirectory(
            context.contentResolver,
            treeUri,
            rootDocId,
            folder,
            existingEpisodes,
            foundPaths,
            toInsert,
            ""
        )

        // Flush remaining inserts
        flushBatch(toInsert, folder, existingEpisodes)

        // Mark missing: paths that existed before but weren't found now
        val missingPaths = existingEpisodes.keys.filterNotNull() - foundPaths
        for (path in missingPaths) {
            val ep = existingEpisodes[path] ?: continue
            if (!ep.isMissing) {
                // Apply moved-file heuristic before marking missing
                val fileName = path.substringAfterLast("/")
                val size = ep.fileSize
                if (size != null) {
                    val moved = episodeDao.findMovedFile(folder.id, fileName, size)
                    if (moved != null) {
                        // Carry playback state to the new row
                        episodeDao.update(
                            moved.copy(
                                positionMs = ep.positionMs,
                                percentListened = ep.percentListened,
                                isListened = ep.isListened,
                                lastPlayedAt = ep.lastPlayedAt,
                                playCount = ep.playCount,
                                isFavourite = ep.isFavourite
                            )
                        )
                        episodeDao.delete(ep.id)
                        continue
                    }
                }
                episodeDao.setMissing(ep.id, true)
            }
        }

        folderDao.updateLastScanAt(folder.id, System.currentTimeMillis())
    }

    private suspend fun traverseDirectory(
        resolver: ContentResolver,
        treeUri: Uri,
        dirDocId: String,
        folder: WatchedFolder,
        existingEpisodes: MutableMap<String?, Episode>,
        foundPaths: MutableSet<String>,
        toInsert: MutableList<Episode>,
        pathPrefix: String
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val cursor = resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null, null, null
        ) ?: return

        val dirs = mutableListOf<DirEntry>()
        val audioFiles = mutableListOf<DirEntry>()
        val images = mutableListOf<DirEntry>()

        cursor.use {
            while (it.moveToNext()) {
                val docId = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                val mime = it.getString(2) ?: ""
                val entry = DirEntry(docId, name, mime, it.getLong(3), it.getLong(4))
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> dirs.add(entry)
                    isAudioFile(name, mime) -> audioFiles.add(entry)
                    isImageFile(name, mime) -> images.add(entry)
                }
            }
        }

        // Folder image (cover.jpg etc.) — artwork fallback for files without embedded art
        val dirArtwork = pickDirectoryImage(images)?.let {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, it.docId).toString()
        }

        for (entry in audioFiles) {
            val relativePath = "$pathPrefix${entry.name}"
            foundPaths.add(relativePath)

            val existing = existingEpisodes[relativePath]
            if (existing != null) {
                // Mark as not-missing if it was
                if (existing.isMissing) episodeDao.setMissing(existing.id, false)
                // Backfill rows scanned before the playable URI was stored
                if (existing.downloadedFilePath == null) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.docId)
                    val meta = metadataReader.read(uri, artworkKey(folder.id, relativePath))
                    episodeDao.update(
                        existing.copy(
                            downloadedFilePath = uri.toString(),
                            artworkUrl = meta.artworkCachePath ?: dirArtwork ?: existing.artworkUrl,
                            durationMs = existing.durationMs ?: meta.durationMs
                        )
                    )
                } else if (existing.artworkUrl == null && dirArtwork != null) {
                    episodeDao.update(existing.copy(artworkUrl = dirArtwork))
                }
            } else {
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.docId)
                val meta = metadataReader.read(uri, artworkKey(folder.id, relativePath))
                val episode = Episode(
                    sourceType = SourceType.LOCAL,
                    folderId = folder.id,
                    relativePath = relativePath,
                    fileSize = entry.size,
                    title = meta.title?.takeIf { it.isNotBlank() }
                        ?: entry.name.substringBeforeLast("."),
                    author = meta.artist,
                    publishedAt = entry.lastModified,
                    durationMs = meta.durationMs,
                    artworkUrl = meta.artworkCachePath ?: dirArtwork,
                    downloadStatus = DownloadStatus.DONE,
                    downloadedFilePath = uri.toString()
                )
                toInsert.add(episode)

                if (toInsert.size >= BATCH_SIZE) {
                    flushBatch(toInsert, folder, existingEpisodes)
                }
            }
        }

        if (folder.includeSubfolders) {
            for (dir in dirs) {
                traverseDirectory(
                    resolver, treeUri, dir.docId, folder,
                    existingEpisodes, foundPaths, toInsert,
                    "$pathPrefix${dir.name}/"
                )
            }
        }
    }

    private suspend fun flushBatch(
        batch: MutableList<Episode>,
        folder: WatchedFolder,
        existingMap: MutableMap<String?, Episode>
    ) {
        for (ep in batch) {
            val id = episodeDao.insert(ep)
            existingMap[ep.relativePath] = ep.copy(id = id)
        }
        batch.clear()
    }

    /** Stable per-file key so artwork survives rescans without knowing the episode id. */
    private fun artworkKey(folderId: Long, relativePath: String): String =
        "f${folderId}_${relativePath.hashCode().toUInt().toString(16)}"

    private fun isAudioFile(name: String, mime: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    private fun isImageFile(name: String, mime: String): Boolean {
        if (mime.startsWith("image/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    private fun pickDirectoryImage(images: List<DirEntry>): DirEntry? {
        if (images.isEmpty()) return null
        for (preferred in COVER_NAMES) {
            images.firstOrNull {
                it.name.substringBeforeLast('.').lowercase() == preferred
            }?.let { return it }
        }
        return images.minByOrNull { it.name.lowercase() }
    }
}
