package dev.javohir.podcast.data.download

import dev.javohir.podcast.data.db.entities.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRulesTest {

    @Test
    fun doneWithMissingFileClearsTheRecord() {
        val decision = reconcileDownload(DownloadStatus.DONE, fileExists = false, WorkLiveness.ABSENT)
        assertEquals(DownloadStatus.NONE, decision?.status)
        assertTrue(decision?.clearPath == true)
    }

    @Test
    fun doneWithFilePresentStaysDone() {
        assertNull(reconcileDownload(DownloadStatus.DONE, fileExists = true, WorkLiveness.ABSENT))
    }

    @Test
    fun runningWithNoWorkerIsInterrupted() {
        val decision = reconcileDownload(DownloadStatus.RUNNING, fileExists = false, WorkLiveness.ABSENT)
        assertEquals(DownloadStatus.FAILED, decision?.status)
        assertEquals(DOWNLOAD_INTERRUPTED, decision?.error)
    }

    @Test
    fun queuedWorkThatIsStillAliveIsLeftAlone() {
        assertNull(reconcileDownload(DownloadStatus.QUEUED, fileExists = false, WorkLiveness.ALIVE))
        assertNull(reconcileDownload(DownloadStatus.RUNNING, fileExists = false, WorkLiveness.ALIVE))
    }

    @Test
    fun missingFileCountsAsASuccessfulDelete() {
        assertEquals(FileDeleteResult.AlreadyGone, interpretFileDelete(existed = false, deleteSucceeded = false))
        assertTrue(isSuccessfulDelete(FileDeleteResult.AlreadyGone))
        assertTrue(isSuccessfulDelete(FileDeleteResult.Deleted))
        assertFalse(isSuccessfulDelete(FileDeleteResult.Failed))
    }

    @Test
    fun cancelDoesNotWriteANewStatus() {
        assertNull(
            statusWriteForOutcome(
                cancelled = true,
                httpCode = 500,
                message = "cancelled",
                attempt = 0
            )
        )
        assertFalse(canCommitDownload(DownloadStatus.NONE))
        assertFalse(canCommitDownload(null))
        assertTrue(canCommitDownload(DownloadStatus.RUNNING))
        assertTrue(canCommitDownload(DownloadStatus.QUEUED))
    }

    @Test
    fun notFoundAndHtmlDoNotRetry() {
        val missing = classifyDownloadFailure(404, "HTTP 404", attempt = 0)
        assertEquals(FailureKind.FAIL, missing.kind)
        val html = classifyDownloadFailure(200, DOWNLOAD_HTML_BODY, attempt = 0)
        assertEquals(FailureKind.FAIL, html.kind)
        assertEquals(DOWNLOAD_HTML_BODY, html.message)
        val disk = classifyDownloadFailure(null, "ENOSPC", attempt = 0)
        assertEquals(FailureKind.FAIL, disk.kind)
        assertEquals(DOWNLOAD_DISK_FULL, disk.message)
    }

    @Test
    fun serverErrorRetriesUntilTheAttemptLimit() {
        val retry = classifyDownloadFailure(503, "HTTP 503", attempt = 1)
        assertEquals(FailureKind.RETRY, retry.kind)
        val giveUp = classifyDownloadFailure(503, "HTTP 503", attempt = 3)
        assertEquals(FailureKind.FAIL, giveUp.kind)
    }

    @Test
    fun parallelFailureIsNotBlamedWhenOneConnectionFailsToo() {
        assertEquals(TransferFault.NETWORK, classifyTransferFault(controlReachedServer = false, controlHttpCode = null))
        assertEquals(TransferFault.NETWORK, classifyTransferFault(controlReachedServer = true, controlHttpCode = 503))
        assertEquals(TransferFault.NETWORK, classifyTransferFault(controlReachedServer = true, controlHttpCode = 429))
        assertEquals(TransferFault.FATAL, classifyTransferFault(controlReachedServer = true, controlHttpCode = 403))
        assertEquals(TransferFault.FATAL, classifyTransferFault(controlReachedServer = true, controlHttpCode = 404))
    }

    @Test
    fun parallelFailureCountsOnlyWhenOneConnectionIsServed() {
        assertEquals(TransferFault.PARALLEL, classifyTransferFault(controlReachedServer = true, controlHttpCode = 206))
        assertEquals(TransferFault.PARALLEL, classifyTransferFault(controlReachedServer = true, controlHttpCode = 200))
    }

    @Test
    fun episodeIdRoundTripsThroughTheWorkTag() {
        assertEquals(42L, episodeIdFromTags(setOf("download", episodeWorkTag(42))))
        assertNull(episodeIdFromTags(setOf("download")))
    }
}
