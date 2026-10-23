package dev.javohir.podcast.playback

import android.net.Uri
import android.provider.DocumentsContract
import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.db.dao.FolderDao
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.download.DownloadFileStore
import dev.javohir.podcast.ui.UserMessages
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackUriResolver @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val folderDao: FolderDao,
    private val fileStore: DownloadFileStore,
    private val coordinator: DownloadCoordinator,
    private val userMessages: UserMessages
) {
    /**
     * Returns a URI that can be played. A downloaded file that is no longer on disk is cleared
     * first. RSS then falls back to the enclosure. YouTube has nothing to fall back to.
     */
    suspend fun resolve(episode: Episode): String? {
        if (episode.sourceType == SourceType.LOCAL) {
            val saved = episode.downloadedFilePath
            if (saved != null && fileStore.exists(saved)) return saved
            return resolveLocal(episode.folderId, episode.relativePath)
        }
        val saved = episode.downloadedFilePath
        if (episode.downloadStatus == DownloadStatus.DONE && saved != null) {
            if (fileStore.exists(saved)) return saved
            coordinator.noteMissingFile(episode.id)
            if (episode.sourceType == SourceType.YOUTUBE) {
                userMessages.show("File was deleted. Download it again.")
                return null
            }
            userMessages.show("Saved file is gone. Streaming instead.")
            return episode.enclosureUrl
        }
        return episode.enclosureUrl
    }

    suspend fun resolve(episodeId: Long): String? {
        val episode = episodeDao.getById(episodeId) ?: return null
        return resolve(episode)
    }

    private suspend fun resolveLocal(folderId: Long?, relativePath: String?): String? {
        if (folderId == null || relativePath == null) return null
        val folder = folderDao.getById(folderId) ?: return null
        return try {
            val treeUri = Uri.parse(folder.treeUri)
            val docId = DocumentsContract.getTreeDocumentId(treeUri) + "/" + relativePath
            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
        } catch (_: Exception) {
            null
        }
    }
}
