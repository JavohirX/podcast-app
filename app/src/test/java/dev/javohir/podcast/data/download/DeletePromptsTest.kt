package dev.javohir.podcast.data.download

import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletePromptsTest {

    @Test
    fun deleteDownloadKeepsTheEpisode() {
        val prompt = deletePrompt(DeleteTarget.DOWNLOAD, SourceType.RSS, DownloadStatus.DONE, "content://audio")
        assertEquals("Delete download?", prompt.title)
        assertEquals("Delete download", prompt.confirmLabel)
        assertTrue(prompt.body.contains("episode stays"))
        assertFalse(prompt.body.contains("deleted too"))
    }

    @Test
    fun deleteDownloadedEpisodeAlsoRemovesTheFile() {
        val done = deletePrompt(DeleteTarget.EPISODE, SourceType.RSS, DownloadStatus.DONE, "content://audio")
        val withPath = deletePrompt(DeleteTarget.EPISODE, SourceType.YOUTUBE, DownloadStatus.NONE, "content://audio")
        assertEquals("Delete episode and file", done.confirmLabel)
        assertTrue(done.body.contains("downloaded audio file will be deleted too"))
        assertTrue(withPath.body.contains("downloaded audio file will be deleted too"))
    }

    @Test
    fun deleteEpisodeWithoutADownloadLeavesAudioOutOfIt() {
        val prompt = deletePrompt(DeleteTarget.EPISODE, SourceType.RSS, DownloadStatus.NONE, null)
        assertEquals("Delete episode", prompt.confirmLabel)
        assertFalse(prompt.body.contains("deleted too"))
        assertTrue(prompt.body.contains("listening progress"))
    }

    @Test
    fun deleteEpisodeStopsAnInProgressDownload() {
        val prompt = deletePrompt(DeleteTarget.EPISODE, SourceType.YOUTUBE, DownloadStatus.RUNNING, null)
        assertEquals("Delete episode and file", prompt.confirmLabel)
        assertTrue(prompt.body.contains("download will stop"))
        assertTrue(prompt.body.contains("audio already saved will be deleted"))
    }

    @Test
    fun deleteLocalEpisodeLeavesTheFolderFile() {
        val prompt = deletePrompt(DeleteTarget.EPISODE, SourceType.LOCAL, DownloadStatus.NONE, "/storage/music.mp3")
        assertEquals("Delete episode", prompt.confirmLabel)
        assertTrue(prompt.body.contains("audio file in your folder stays"))
        assertFalse(prompt.body.contains("deleted too"))
    }
}
