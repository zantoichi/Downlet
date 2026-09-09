package downlet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProgressPresentationTest {
    @Test
    fun `byte speed and eta formatting stays compact`() {
        assertEquals("999 B", formatDecimalBytes(999))
        assertEquals("1 KB", formatDecimalBytes(1_000))
        assertEquals("72.4 MB", formatDecimalBytes(72_400_000))
        assertEquals("1.5 GB", formatDecimalBytes(1_500_000_000))
        assertEquals("About 13 sec", formatEta(13.seconds))
        assertEquals("About 2 min", formatEta(90.seconds))
        assertEquals("About 2 hr 5 min", formatEta(2.hours + 5.minutes))
        assertEquals("12 sec elapsed", formatElapsed(12.seconds))
        assertEquals("2 min 5 sec elapsed", formatElapsed(2.minutes + 5.seconds))
        assertEquals("1 hr 2 min elapsed", formatElapsed(1.hours + 2.minutes + 3.seconds))
    }

    @Test
    fun `elapsed time appears only while converting audio`() {
        val elapsed = 12.seconds
        assertEquals(
            "12 sec elapsed",
            downloadProgressPresentation(
                DownloadProgress.Processing(DownloadProcessingStage.Converting),
                elapsed,
            ).rightText,
        )
        assertNull(
            downloadProgressPresentation(
                DownloadProgress.Processing(DownloadProcessingStage.Merging),
                elapsed,
            ).rightText,
        )
        assertNull(
            downloadProgressPresentation(
                DownloadProgress.Processing(DownloadProcessingStage.Finalizing),
                elapsed,
            ).rightText,
        )
    }

    @Test
    fun `transfer presentation omits unavailable telemetry`() {
        assertEquals(
            DownloadProgressPresentation(
                heading = "Downloading",
                leftText = "72.4 MB of ~138 MB · 5.2 MB/s",
                rightText = "About 13 sec",
            ),
            downloadProgressPresentation(
                DownloadProgress.Transferring(
                    downloadedBytes = 72_400_000,
                    totalBytes = 138_000_000,
                    totalIsEstimated = true,
                    speedBytesPerSecond = 5_200_000.0,
                    eta = 13.seconds,
                    fraction = 0.52f,
                ),
            ),
        )
        assertEquals(
            DownloadProgressPresentation("Downloading", "72.4 MB downloaded"),
            downloadProgressPresentation(DownloadProgress.Transferring(downloadedBytes = 72_400_000)),
        )
    }

    @Test
    fun `announcements change only at phases and ten percent milestones`() {
        assertEquals("Preparing download.", downloadProgressAnnouncement(DownloadProgress.Preparing))
        assertEquals("Download started.", downloadProgressAnnouncement(transfer(0)))
        assertEquals("Download started.", downloadProgressAnnouncement(transfer(9)))
        assertEquals("Download 10 percent.", downloadProgressAnnouncement(transfer(10)))
        assertEquals("Download 10 percent.", downloadProgressAnnouncement(transfer(19)))
        assertEquals("Download 90 percent.", downloadProgressAnnouncement(transfer(99)))
        assertNull(downloadProgressAnnouncement(transfer(100)))
        assertEquals(
            "Finalizing. Merging video and audio.",
            downloadProgressAnnouncement(DownloadProgress.Processing(DownloadProcessingStage.Merging)),
        )
    }

    @Test
    fun `taskbar mapping mirrors visible operation state`() {
        assertEquals(TaskbarProgress(TaskbarProgressState.Off), taskbarProgress(DownloadUiState.Empty))
        assertEquals(
            TaskbarProgress(TaskbarProgressState.Indeterminate),
            taskbarProgress(DownloadUiState.Downloading(DownloadFixtures.normal, DownloadProgress.Preparing)),
        )
        assertEquals(
            TaskbarProgress(TaskbarProgressState.Normal, 43),
            taskbarProgress(DownloadUiState.Downloading(DownloadFixtures.normal, transfer(43))),
        )
        assertEquals(
            TaskbarProgress(TaskbarProgressState.Indeterminate),
            taskbarProgress(
                DownloadUiState.Downloading(
                    DownloadFixtures.normal,
                    DownloadProgress.Transferring(downloadedBytes = 10),
                ),
            ),
        )
        assertEquals(
            TaskbarProgress(TaskbarProgressState.Indeterminate),
            taskbarProgress(
                DownloadUiState.Downloading(
                    DownloadFixtures.normal,
                    DownloadProgress.Processing(DownloadProcessingStage.Converting),
                ),
            ),
        )
        assertEquals(
            TaskbarProgress(TaskbarProgressState.Error, 100),
            taskbarProgress(DownloadUiState.Error(DownloadFixtures.failure)),
        )
        assertEquals(
            TaskbarProgress(TaskbarProgressState.Off),
            taskbarProgress(DownloadUiState.Error(DownloadFixtures.failure, DownloadErrorKind.Resolution)),
        )
        assertEquals(
            TaskbarProgress(TaskbarProgressState.Off),
            taskbarProgress(DownloadUiState.Completed(DownloadFixtures.normal, DownloadFixtures.completedFile())),
        )
    }

    @Test
    fun `failure copy preserves resolution fallback and storage recovery guidance`() {
        assertEquals(
            "Couldn’t read this YouTube link.",
            ProductCopy.downloadFailure(DownloadErrorKind.Resolution, DownloadFailureReason.Unknown).title,
        )
        assertEquals(
            "Downlet couldn’t save this file.",
            ProductCopy.downloadFailure(DownloadErrorKind.Download, DownloadFailureReason.Storage).title,
        )
    }

    private fun transfer(percent: Int): DownloadProgress.Transferring =
        DownloadProgress.Transferring(
            downloadedBytes = percent.toLong(),
            totalBytes = 100,
            fraction = percent / 100f,
        )
}
