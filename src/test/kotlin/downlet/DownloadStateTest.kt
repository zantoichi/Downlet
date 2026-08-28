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
    }

    @Test
    fun `state holder handles only explicit events`() {
        val holder = DownloadStateHolder()

        holder.onEvent(DownloadEvent.ShowReady(DownloadFixtures.longTitle))
        assertEquals(DownloadUiState.Ready(DownloadFixtures.longTitle), holder.state)

        holder.onEvent(DownloadEvent.Reset)
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
}
