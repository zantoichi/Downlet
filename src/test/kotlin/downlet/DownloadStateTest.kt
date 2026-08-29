package downlet

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadStateTest {
    private fun TestScope.testHolder() =
        UnconfinedTestDispatcher(testScheduler).let { dispatcher ->
            DownloadStateHolder(
                CoroutineScope(backgroundScope.coroutineContext + dispatcher),
                machineDispatcher = dispatcher,
            )
        }

    private fun immediateHolder() = DownloadStateHolder(machineDispatcher = Dispatchers.Unconfined)

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
        assertFalse(DownloadFixtures.missingThumbnail.thumbnailAvailable)
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
        val holder = immediateHolder()

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
    fun `valid paste resolves immediately and paste intent is consumed once`() =
        runTest {
            val holder = testHolder()
            var pasteIntent = true
            backgroundScope.launch {
                collectLinkEdits(
                    edits = snapshotFlow { holder.linkFieldState.text.toString() },
                    stateHolder = holder,
                    consumePasteIntent = { pasteIntent.also { pasteIntent = false } },
                )
            }
            runCurrent()

            val pastedUrl = "https://youtu.be/pasted"
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(pastedUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(pastedUrl, (holder.state as DownloadUiState.Resolving).fixture.sourceUrl)

            val typedUrl = "https://youtu.be/typed"
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(typedUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(349.milliseconds)
            assertEquals(DownloadUiState.Empty, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(typedUrl, (holder.state as DownloadUiState.Resolving).fixture.sourceUrl)
        }

    @Test
    fun `stale paste intent expires before a later typed edit`() =
        runTest {
            val holder = testHolder()
            var pasteIntent = true
            backgroundScope.launch {
                collectLinkEdits(
                    edits = snapshotFlow { holder.linkFieldState.text.toString() },
                    stateHolder = holder,
                    consumePasteIntent = { pasteIntent.also { pasteIntent = false } },
                )
            }
            backgroundScope.launch { expirePasteIntent { pasteIntent = false } }
            runCurrent()

            advanceTimeBy(PASTE_INTENT_LIFETIME_MILLIS.milliseconds)
            val typedUrl = "https://youtu.be/after-stale-paste"
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(typedUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()

            advanceTimeBy(349.milliseconds)
            assertEquals(DownloadUiState.Empty, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(typedUrl, (holder.state as DownloadUiState.Resolving).fixture.sourceUrl)
        }

    @Test
    fun `newer manual edit cancels prior debounce`() =
        runTest {
            val holder = testHolder()
            backgroundScope.launch {
                collectLinkEdits(
                    edits = snapshotFlow { holder.linkFieldState.text.toString() },
                    stateHolder = holder,
                    consumePasteIntent = { false },
                )
            }
            runCurrent()

            holder.linkFieldState.setTextAndPlaceCursorAtEnd("https://youtu.be/first")
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(200.milliseconds)
            val newerUrl = "https://youtu.be/newer"
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(newerUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()

            advanceTimeBy(349.milliseconds)
            assertEquals(DownloadUiState.Empty, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(newerUrl, (holder.state as DownloadUiState.Resolving).fixture.sourceUrl)
        }

    @Test
    fun `automatic resolution completes at 550 milliseconds`() =
        runTest {
            val holder = testHolder()
            holder.beginResolution("https://youtu.be/automatic")
            val resolving = holder.state as DownloadUiState.Resolving
            runCurrent()

            advanceTimeBy(549.milliseconds)
            assertEquals(resolving, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(DownloadUiState.Ready(resolving.fixture), holder.state)
        }

    @Test
    fun `cancelled automatic resolution cannot restore stale ready content`() =
        runTest {
            val holder = testHolder()
            holder.beginResolution("https://youtu.be/stale")
            runCurrent()
            advanceTimeBy(200.milliseconds)

            val newerUrl = "https://youtu.be/newer"
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(newerUrl)
            holder.observeLinkEdit(newerUrl)
            advanceTimeBy(FAKE_RESOLUTION_MILLIS.milliseconds)
            runCurrent()

            assertEquals(DownloadUiState.Empty, holder.state)
            assertEquals(newerUrl, holder.linkFieldState.text.toString())
        }

    @Test
    fun `closing state holder cancels pending resolution completion`() =
        runTest {
            val holder = testHolder()
            holder.beginResolution("https://youtu.be/closing")
            val resolving = holder.state

            holder.close()
            advanceTimeBy(FAKE_RESOLUTION_MILLIS.milliseconds)
            runCurrent()

            assertEquals(resolving, holder.state)
        }

    @Test
    fun `native paste validation and fake resolution remain deterministic`() =
        runTest {
            val holder = testHolder()
            var pasteIntent = true
            backgroundScope.launch {
                collectLinkEdits(
                    edits = snapshotFlow { holder.linkFieldState.text.toString() },
                    stateHolder = holder,
                    consumePasteIntent = { pasteIntent.also { pasteIntent = false } },
                )
            }
            runCurrent()
            val validUrl = "https://youtu.be/quiet-transfer"

            holder.linkFieldState.setTextAndPlaceCursorAtEnd(validUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()
            val resolving = holder.state as DownloadUiState.Resolving
            assertEquals(validUrl, holder.linkFieldState.text.toString())
            assertEquals(validUrl, resolving.fixture.sourceUrl)
            assertNull(holder.validationMessage)

            holder.completeResolution(resolving.fixture)
            assertEquals(DownloadUiState.Ready(resolving.fixture), holder.state)

            pasteIntent = true
            holder.linkFieldState.setTextAndPlaceCursorAtEnd("not a url")
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(DownloadUiState.Empty, holder.state)
            assertEquals(INVALID_LINK_MESSAGE, holder.validationMessage)
        }

    @Test
    fun `editing clears stale resolved state and forced resolving bypasses completion`() {
        val holder = immediateHolder()
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
        val holder = immediateHolder()

        holder.onEvent(DownloadEvent.ShowReady())

        assertEquals(DownloadMode.Video, holder.selectedMode)
        assertEquals(videoQualityOptions, holder.qualityOptions)
        assertEquals("Best available — 2160p", holder.selectedQualityLabel)
        assertEquals("Downloads", holder.destination)
        assertNull(holder.readyFeedback)
    }

    @Test
    fun `mode and quality changes stay mutually exclusive and reset best quality`() {
        val holder = immediateHolder()
        holder.onEvent(DownloadEvent.ShowReady())

        holder.selectQuality(2)
        assertEquals("1080p", holder.selectedQualityLabel)

        holder.selectMode(DownloadMode.Audio)
        assertEquals(DownloadMode.Audio, holder.selectedMode)
        assertEquals(audioQualityOptions, holder.qualityOptions)
        assertEquals("Best available — 251 kbps audio", holder.selectedQualityLabel)

        holder.selectQuality(1)
        assertEquals("160 kbps audio", holder.selectedQualityLabel)

        holder.selectMode(DownloadMode.Video)
        assertEquals("Best available — 2160p", holder.selectedQualityLabel)
    }

    @Test
    fun `destination cycles deterministic fixtures and acknowledges the visible value`() {
        val holder = immediateHolder()
        holder.onEvent(DownloadEvent.ShowReady())

        holder.changeDestination()

        assertEquals(DownloadFixtures.longDestination.destination, holder.destination)
        assertEquals("Save location changed to ${holder.destination}.", holder.readyFeedback)
    }

    @Test
    fun `download acknowledgement stays ready and disabled fixture ignores activation`() {
        val holder = immediateHolder()
        holder.onEvent(DownloadEvent.ShowReady())

        holder.download()
        assertEquals(DOWNLOAD_ACKNOWLEDGEMENT, holder.readyFeedback)
        assertEquals(DownloadUiState.Ready(DownloadFixtures.normal), holder.state)

        holder.onEvent(DownloadEvent.ShowReady(DownloadFixtures.disabledAction))
        assertFalse(holder.downloadEnabled)
        assertEquals(DOWNLOAD_UNAVAILABLE_MESSAGE, holder.readyStatus)
        holder.download()
        assertNull(holder.readyFeedback)
        assertEquals(DOWNLOAD_UNAVAILABLE_MESSAGE, holder.readyStatus)
    }

    @Test
    fun `source resolution fixture and reset clear stale ready choices and feedback`() {
        val holder = immediateHolder()
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
