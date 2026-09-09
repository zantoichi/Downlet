package downlet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class YtDlpProgressTest {
    @Test
    fun `public video bot challenges do not claim an age restriction`() {
        listOf("you're", "you’re").forEach { pronoun ->
            val reason =
                classifyDownloadFailure(
                    listOf(
                        "ERROR: DIHm1Jzu7vI: Sign in to confirm $pronoun not a bot. " +
                            "Use --cookies-from-browser or --cookies for the authentication.",
                    ),
                )
            assertEquals(DownloadFailureReason.BotChallenge, reason)
            assertEquals(true, reason.needsBrowserSession)
            val copy = ProductCopy.downloadFailure(DownloadErrorKind.Resolution, reason)
            assertEquals("YouTube wants to verify this session.", copy.title)
        }
    }

    @Test
    fun `parser accepts controlled values and treats unavailable numbers as missing`() {
        assertEquals(
            YtDlpProgressEvent.Plan(
                overall = PlannedSize(300, false),
                streams = listOf(PlannedSize(200, false), PlannedSize(100, true)),
            ),
            parseYtDlpProgressEvent("DOWNLET_PLAN=300|310|200|NA|NA|100"),
        )
        assertEquals(
            YtDlpProgressEvent.Transfer(
                status = TransferStatus.Downloading,
                downloadedBytes = 42,
                exactTotalBytes = null,
                estimatedTotalBytes = null,
                speedBytesPerSecond = null,
                eta = null,
            ),
            parseYtDlpProgressEvent("DOWNLET_TRANSFER=downloading|42|-1|Infinity|NaN|NA"),
        )
        assertNull(parseYtDlpProgressEvent("DOWNLET_TRANSFER=downloading|oops|100|NA|10|5"))
        assertNull(parseYtDlpProgressEvent("DOWNLET_TRANSFER=waiting|1|100|NA|10|5"))
        assertNull(parseYtDlpProgressEvent("ordinary yt-dlp output"))
    }

    @Test
    fun `two streams aggregate in completion order without resetting visible progress`() {
        val tracker = DownloadProgressTracker()
        tracker.accept(requireEvent("DOWNLET_PLAN=200|NA|100|NA|100|NA"))

        val video =
            assertIs<DownloadProgress.Transferring>(
                tracker.accept(requireEvent("DOWNLET_TRANSFER=downloading|80|100|NA|20|6")),
            )
        val videoFinished =
            assertIs<DownloadProgress.Transferring>(
                tracker.accept(requireEvent("DOWNLET_TRANSFER=finished|100|100|NA|20|0")),
            )
        val audio =
            assertIs<DownloadProgress.Transferring>(
                tracker.accept(requireEvent("DOWNLET_TRANSFER=downloading|10|100|NA|20|4")),
            )

        assertEquals(0.4f, video.fraction)
        assertEquals(100, videoFinished.downloadedBytes)
        assertEquals(110, audio.downloadedBytes)
        assertEquals(0.55f, audio.fraction)
    }

    @Test
    fun `stronger runtime totals update telemetry without backward rail motion`() {
        val tracker = DownloadProgressTracker()
        tracker.accept(requireEvent("DOWNLET_PLAN=NA|100|NA|100|NA|NA"))
        val estimated =
            assertIs<DownloadProgress.Transferring>(
                tracker.accept(requireEvent("DOWNLET_TRANSFER=downloading|50|NA|100|10|5")),
            )
        val exact =
            assertIs<DownloadProgress.Transferring>(
                tracker.accept(requireEvent("DOWNLET_TRANSFER=downloading|60|200|NA|10|14")),
            )

        assertTrue(estimated.totalIsEstimated)
        assertEquals(200, exact.totalBytes)
        assertEquals(false, exact.totalIsEstimated)
        assertEquals(0.5f, exact.fraction)
        assertEquals(14.seconds, exact.eta)
    }

    @Test
    fun `unknown totals stay indeterminate and retain transfer telemetry`() {
        val tracker = DownloadProgressTracker()
        tracker.accept(requireEvent("DOWNLET_PLAN=NA|NA|NA|NA|NA|NA"))
        val progress =
            assertIs<DownloadProgress.Transferring>(
                tracker.accept(requireEvent("DOWNLET_TRANSFER=downloading|72|NA|NA|12.5|9")),
            )

        assertEquals(72, progress.downloadedBytes)
        assertNull(progress.totalBytes)
        assertNull(progress.fraction)
        assertEquals(12.5, progress.speedBytesPerSecond)
        assertEquals(9.seconds, progress.eta)
    }

    @Test
    fun `aggregate eta uses remaining bytes and current speed`() {
        val tracker = DownloadProgressTracker()
        tracker.accept(requireEvent("DOWNLET_PLAN=200|NA|200|NA|NA|NA"))
        val progress =
            assertIs<DownloadProgress.Transferring>(
                tracker.accept(requireEvent("DOWNLET_TRANSFER=downloading|100|200|NA|25|99")),
            )

        assertEquals(4.seconds, progress.eta)
    }

    @Test
    fun `unexpected extra streams cannot move the rail backward`() {
        val tracker = DownloadProgressTracker()
        tracker.accept(requireEvent("DOWNLET_PLAN=100|NA|100|NA|NA|NA"))
        tracker.accept(requireEvent("DOWNLET_TRANSFER=finished|100|100|NA|10|0"))
        val extra =
            assertIs<DownloadProgress.Transferring>(
                tracker.accept(requireEvent("DOWNLET_TRANSFER=downloading|20|100|NA|10|8")),
            )

        assertEquals(120, extra.downloadedBytes)
        assertEquals(200, extra.totalBytes)
        assertEquals(1f, extra.fraction)
    }

    @Test
    fun `processing hooks map to finalizing stages`() {
        val tracker = DownloadProgressTracker()

        assertEquals(
            DownloadProgress.Processing(DownloadProcessingStage.Merging),
            tracker.accept(requireEvent("DOWNLET_PROCESSING=started|FFmpegMerger")),
        )
        assertEquals(
            DownloadProgress.Processing(DownloadProcessingStage.Converting),
            tracker.accept(requireEvent("DOWNLET_PROCESSING=processing|FFmpegExtractAudio")),
        )
        assertEquals(
            DownloadProgress.Processing(DownloadProcessingStage.Finalizing),
            tracker.accept(requireEvent("DOWNLET_PROCESSING=started|Metadata")),
        )
        assertNull(tracker.accept(requireEvent("DOWNLET_PROCESSING=finished|Metadata")))
    }

    @Test
    fun `failure classifier follows category priority`() {
        assertEquals(
            DownloadFailureReason.Storage,
            classifyDownloadFailure(listOf("permission denied", "HTTP Error 403")),
        )
        assertEquals(
            DownloadFailureReason.Tool,
            classifyDownloadFailure(listOf("ffmpeg not found", "conversion failed")),
        )
        assertEquals(DownloadFailureReason.Processing, classifyDownloadFailure(listOf("Postprocessing error")))
        assertEquals(
            DownloadFailureReason.Authentication,
            classifyDownloadFailure(
                listOf(
                    "Sign in to confirm your age. Use --cookies-from-browser or --cookies for the authentication.",
                ),
            ),
        )
        assertEquals(
            DownloadFailureReason.Authentication,
            classifyDownloadFailure(listOf("ERROR: could not find chrome cookies database")),
        )
        assertEquals(
            DownloadFailureReason.Authentication,
            classifyDownloadFailure(listOf("ERROR: Failed to extract cookies from Firefox")),
        )
        assertEquals(DownloadFailureReason.Availability, classifyDownloadFailure(listOf("Private video")))
        assertEquals(DownloadFailureReason.Network, classifyDownloadFailure(listOf("HTTP Error 503")))
        listOf(
            DownloadFailureReason.Storage to "OSError: [WinError 206] The filename or extension is too long",
            DownloadFailureReason.Availability to "HTTP Error 404: Not Found",
            DownloadFailureReason.Availability to "This video is DRM protected",
            DownloadFailureReason.Availability to "This video is no longer available",
            DownloadFailureReason.Network to "Temporary failure in name resolution",
            DownloadFailureReason.Network to "socket.gaierror: getaddrinfo failed",
            DownloadFailureReason.Network to "Network is unreachable",
            DownloadFailureReason.Network to "Remote end closed connection without response",
            DownloadFailureReason.Network to "Broken pipe",
            DownloadFailureReason.Network to "HTTP Error 408: Request Timeout",
        ).forEach { (reason, diagnostic) ->
            assertEquals(reason, classifyDownloadFailure(listOf(diagnostic)), diagnostic)
        }
        assertEquals(
            DownloadFailureReason.Unknown,
            classifyDownloadFailure(listOf("Requested format is not available")),
        )
        assertEquals(DownloadFailureReason.Unknown, classifyDownloadFailure(listOf("unclassified failure")))
    }

    private fun requireEvent(line: String): YtDlpProgressEvent = requireNotNull(parseYtDlpProgressEvent(line))
}
