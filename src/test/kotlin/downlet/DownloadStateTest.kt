package downlet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadStateTest {
    @Test
    fun `all six states are explicit`() {
        val normal = DownloadFixtures.normal
        val failure = DownloadFixtures.failure

        val labels =
            listOf(
                DownloadUiState.Empty,
                DownloadUiState.Resolving(normal),
                DownloadUiState.Ready(normal),
                DownloadUiState.Downloading(normal, progressPercent = 43),
                DownloadUiState.Completed(normal),
                DownloadUiState.Error(failure),
            ).map(DownloadUiState::label)

        assertEquals(listOf("Empty", "Resolving", "Ready", "Downloading", "Completed", "Error"), labels)
    }

    @Test
    fun `invalid outcome combinations are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DownloadUiState.Downloading(DownloadFixtures.failure, progressPercent = 87)
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadUiState.Completed(DownloadFixtures.failure)
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadUiState.Error(DownloadFixtures.normal)
        }
    }

    @Test
    fun `fixtures remain deterministic and distinct`() {
        assertTrue(DownloadFixtures.longTitle.title.length > DownloadFixtures.normal.title.length)
        assertNull(DownloadFixtures.missingThumbnail.thumbnailResource)
        assertTrue(DownloadFixtures.longDestination.destination.length > DownloadFixtures.normal.destination.length)
        assertEquals(FakeDownloadOutcome.Failure(68), DownloadFixtures.failure.outcome)
        assertFalse(DownloadFixtures.disabledAction.canDownload)
    }

    @Test
    fun `media semantics announce missing preview only when unavailable`() {
        val available = mediaContentDescription(DownloadFixtures.normal, thumbnailAvailable = true)
        val unavailable = mediaContentDescription(DownloadFixtures.missingThumbnail, thumbnailAvailable = false)

        assertFalse(available.contains("Preview unavailable"))
        assertTrue(unavailable.endsWith("Preview unavailable."))
    }

    @Test
    fun `state holder handles only explicit events`() {
        val holder = DownloadStateHolder()

        holder.onEvent(DownloadEvent.ShowReady(DownloadFixtures.longTitle))
        assertEquals(DownloadUiState.Ready(DownloadFixtures.longTitle), holder.state)

        holder.onEvent(DownloadEvent.ShowEmpty)
        assertEquals(DownloadUiState.Empty, holder.state)
    }

    @Test
    @Suppress("HttpUrlsUsage")
    fun `youtube url validation accepts only supported hosts and schemes`() {
        listOf(
            "https://youtube.com/watch?v=one",
            "https://www.youtube.com/watch?v=two",
            "http://m.youtube.com/watch?v=three",
            "https://youtu.be/four",
        ).forEach { assertTrue(isValidYouTubeUrl(it), it) }

        listOf(
            "",
            "not a url",
            "ftp://youtube.com/video",
            "https://youtube.example/video",
            "https://youtube.com.evil.example/video",
            "https:///missing-host",
        ).forEach { assertFalse(isValidYouTubeUrl(it), it) }
    }

    @Test
    fun `paste resolves immediately while typing waits 350 milliseconds`() {
        val url = "https://youtu.be/quiet-transfer"

        assertEquals(LinkSubmission.ResolveImmediately, linkSubmissionFor(url, pasteIntent = true))
        assertEquals(LinkSubmission.ResolveAfter(350L), linkSubmissionFor(url, pasteIntent = false))
        assertEquals(LinkSubmission.None, linkSubmissionFor("invalid", pasteIntent = true))
    }

    @Test
    fun `paste validation and fake resolution remain deterministic`() {
        val holder = DownloadStateHolder()
        val validUrl = "https://youtu.be/quiet-transfer"

        holder.pasteLink(validUrl)
        val resolving = holder.state as DownloadUiState.Resolving
        assertEquals(validUrl, holder.linkFieldState.text.toString())
        assertEquals(validUrl, resolving.fixture.sourceUrl)
        assertNull(holder.validationMessage)

        holder.completeResolution(resolving.fixture)
        assertEquals(DownloadUiState.Ready(resolving.fixture), holder.state)

        holder.pasteLink("not a url")
        assertEquals(DownloadUiState.Empty, holder.state)
        assertEquals(InvalidLinkMessage, holder.validationMessage)
    }

    @Test
    fun `editing clears stale resolved state and forced resolving bypasses completion`() {
        val holder = DownloadStateHolder()
        holder.onEvent(DownloadEvent.ShowReady())

        assertTrue(holder.observeLinkEdit("https://youtube.com/watch?v=new"))
        assertEquals(DownloadUiState.Empty, holder.state)

        holder.onEvent(DownloadEvent.ShowResolving())
        val forced = holder.state as DownloadUiState.Resolving
        holder.completeResolution(forced.fixture)
        assertEquals(forced, holder.state)
    }

    @Test
    fun `fresh ready defaults to video and resolved best quality`() {
        val holder = DownloadStateHolder()

        holder.onEvent(DownloadEvent.ShowReady())

        assertEquals(DownloadMode.Video, holder.selectedMode)
        assertEquals(VideoQualityOptions, holder.qualityOptions)
        assertEquals("Best available — 2160p", holder.selectedQualityLabel)
        assertEquals("Downloads", holder.destination)
        assertNull(holder.readyFeedback)
    }

    @Test
    fun `mode and quality changes stay mutually exclusive and reset best quality`() {
        val holder = DownloadStateHolder()
        holder.onEvent(DownloadEvent.ShowReady())

        holder.selectQuality(2)
        assertEquals("1080p", holder.selectedQualityLabel)

        holder.selectMode(DownloadMode.Audio)
        assertEquals(DownloadMode.Audio, holder.selectedMode)
        assertEquals(AudioQualityOptions, holder.qualityOptions)
        assertEquals("Best available — 251 kbps audio", holder.selectedQualityLabel)

        holder.selectQuality(1)
        assertEquals("160 kbps audio", holder.selectedQualityLabel)

        holder.selectMode(DownloadMode.Video)
        assertEquals("Best available — 2160p", holder.selectedQualityLabel)
    }

    @Test
    fun `destination cycles deterministic fixtures and acknowledges the visible value`() {
        val holder = DownloadStateHolder()
        holder.onEvent(DownloadEvent.ShowReady())

        holder.changeDestination()

        assertEquals(DownloadFixtures.longDestination.destination, holder.destination)
        assertEquals("Save location changed to ${holder.destination}.", holder.readyFeedback)
    }

    @Test
    fun `download acknowledgement stays ready and disabled fixture ignores activation`() {
        val holder = DownloadStateHolder()
        holder.onEvent(DownloadEvent.ShowReady())

        holder.download()
        assertEquals(DownloadAcknowledgement, holder.readyFeedback)
        assertEquals(DownloadUiState.Ready(DownloadFixtures.normal), holder.state)

        holder.onEvent(DownloadEvent.ShowReady(DownloadFixtures.disabledAction))
        assertFalse(holder.downloadEnabled)
        holder.download()
        assertNull(holder.readyFeedback)
    }

    @Test
    fun `source resolution fixture and reset clear stale ready choices and feedback`() {
        val holder = DownloadStateHolder()
        holder.onEvent(DownloadEvent.ShowReady())
        holder.selectMode(DownloadMode.Audio)
        holder.selectQuality(1)
        holder.changeDestination()
        holder.download()

        holder.observeLinkEdit("https://youtube.com/watch?v=new")
        assertEquals(DownloadUiState.Empty, holder.state)
        assertEquals(DownloadMode.Video, holder.selectedMode)
        assertEquals(0, holder.selectedQualityIndex)
        assertEquals("", holder.destination)
        assertNull(holder.readyFeedback)

        holder.beginResolution("https://youtube.com/watch?v=new")
        val resolving = holder.state as DownloadUiState.Resolving
        holder.completeResolution(resolving.fixture)
        assertEquals("Downloads", holder.destination)
        assertEquals("Best available — 2160p", holder.selectedQualityLabel)

        holder.onEvent(DownloadEvent.ShowReady(DownloadFixtures.longDestination))
        assertEquals(DownloadFixtures.longDestination.destination, holder.destination)
        assertNull(holder.readyFeedback)

        holder.onEvent(DownloadEvent.Reset)
        assertEquals(DownloadUiState.Empty, holder.state)
        assertEquals(DownloadMode.Video, holder.selectedMode)
        assertEquals(0, holder.selectedQualityIndex)
        assertEquals("", holder.destination)
        assertNull(holder.readyFeedback)

        holder.onEvent(DownloadEvent.ShowReady())
        assertEquals(DownloadUiState.Ready(DownloadFixtures.normal), holder.state)
        assertEquals("Best available — 2160p", holder.selectedQualityLabel)
        assertEquals("Downloads", holder.destination)
    }
}
