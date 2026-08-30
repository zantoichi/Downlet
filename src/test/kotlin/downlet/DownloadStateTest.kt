package downlet

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
@Suppress("LargeClass")
class DownloadStateTest {
    private fun TestScope.testHolder(runtime: DownloadRuntime = PreviewDownloadRuntime()) =
        UnconfinedTestDispatcher(testScheduler).let { dispatcher ->
            DownloadStateHolder(
                CoroutineScope(backgroundScope.coroutineContext + dispatcher),
                runtime = runtime,
            )
        }

    private fun immediateHolder(runtime: DownloadRuntime = PreviewDownloadRuntime()) =
        DownloadStateHolder(
            CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
            runtime,
        )

    private fun DownloadStateHolder.showReady(item: DownloadItem = DownloadFixtures.normal) {
        showDesignState(DownloadUiState.Ready(item))
    }

    private fun DownloadStateHolder.showSetup(
        item: DownloadItem = DownloadFixtures.normal,
        tools: List<DownloadTool> = listOf(DownloadTool.YtDlp, DownloadTool.Ffmpeg),
        phase: ToolSetupPhase = ToolSetupPhase.AwaitingConsent,
    ) {
        showDesignState(DownloadUiState.Setup(item, tools, phase))
    }

    private fun DownloadStateHolder.showResolving(item: DownloadItem = DownloadFixtures.normal) {
        showDesignState(DownloadUiState.Resolving(item))
    }

    private fun DownloadStateHolder.showDownloading(
        item: DownloadItem = DownloadFixtures.normal,
        progress: DownloadProgress = DownloadProgress(43),
    ) {
        showDesignState(DownloadUiState.Downloading(item, progress))
    }

    private fun DownloadStateHolder.showCompleted(item: DownloadItem = DownloadFixtures.normal) {
        showDesignState(DownloadUiState.Completed(item))
    }

    private fun DownloadStateHolder.startAuthorizedDownload() {
        updateDownloadAuthorization(true)
        download()
    }

    @Test
    fun `all eight states are explicit`() {
        val normal = DownloadFixtures.normal
        val failure = DownloadFixtures.failure

        val labels =
            listOf(
                DownloadUiState.Empty,
                DownloadUiState.Previewing(normal),
                DownloadUiState.Setup(normal, listOf(DownloadTool.YtDlp)),
                DownloadUiState.Resolving(normal),
                DownloadUiState.Ready(normal),
                DownloadUiState.Downloading(normal, DownloadProgress(43)),
                DownloadUiState.Completed(normal),
                DownloadUiState.Error(failure),
            ).map(DownloadUiState::label)

        assertEquals(
            listOf("Empty", "Previewing", "Setup", "Resolving", "Ready", "Downloading", "Completed", "Error"),
            labels,
        )
    }

    @Test
    fun `missing tools require explicit consent before installation and resolution`() =
        runTest {
            val runtime = ControlledPreviewRuntime(listOf(DownloadTool.YtDlp, DownloadTool.Ffmpeg))
            val holder = testHolder(runtime)
            val sourceUrl = "https://youtu.be/setup"

            holder.beginResolution(sourceUrl)
            runCurrent()
            assertEquals(sourceUrl, (holder.state as DownloadUiState.Previewing).item.source.toString())
            runtime.completePreview()
            runCurrent()
            assertEquals(
                DownloadUiState.Setup(
                    DownloadFixtures.normal.copy(source = requireNotNull(YouTubeUrl.parse(sourceUrl))),
                    listOf(DownloadTool.YtDlp, DownloadTool.Ffmpeg),
                ),
                holder.state,
            )
            assertFalse(holder.toolSetupEnabled)

            holder.installTools()
            assertEquals(0, runtime.installCount)

            holder.updateToolSetupConsent(true)
            assertTrue(holder.toolSetupEnabled)
            assertEquals(ToolSetupPhase.ReadyToInstall, (holder.state as DownloadUiState.Setup).phase)
            holder.installTools()
            runCurrent()

            assertEquals(1, runtime.installCount)
            assertEquals(sourceUrl, (holder.state as DownloadUiState.Resolving).item.source.toString())
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()
            assertEquals(sourceUrl, (holder.state as DownloadUiState.Ready).item.source.toString())
        }

    @Test
    fun `installed tools move from previewing directly to resolving`() =
        runTest {
            val runtime = ControlledPreviewRuntime()
            val holder = testHolder(runtime)
            val sourceUrl = "https://youtu.be/already-ready"

            holder.beginResolution(sourceUrl)
            runCurrent()
            assertEquals(sourceUrl, (holder.state as DownloadUiState.Previewing).item.source.toString())

            runtime.completePreview()
            runCurrent()
            assertEquals(sourceUrl, (holder.state as DownloadUiState.Resolving).item.source.toString())

            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()
            assertEquals(sourceUrl, (holder.state as DownloadUiState.Ready).item.source.toString())
        }

    @Test
    fun `preview failure becomes recoverable without checking or installing tools`() =
        runTest {
            val runtime = ControlledPreviewRuntime(DownloadTool.entries, failPreview = true)
            val holder = testHolder(runtime)

            holder.beginResolution("https://youtu.be/unavailable")
            runCurrent()
            runtime.completePreview()
            runCurrent()

            assertEquals(DownloadErrorKind.Resolution, (holder.state as DownloadUiState.Error).kind)
            assertEquals(0, runtime.missingToolsCount)
            assertEquals(0, runtime.installCount)
        }

    @Test
    fun `invalid progress values are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DownloadProgress(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadProgress(100)
        }
    }

    @Test
    fun `fixtures remain deterministic and distinct`() {
        assertTrue(DownloadFixtures.longTitle.title.length > DownloadFixtures.normal.title.length)
        assertEquals(MediaThumbnail.Unavailable, DownloadFixtures.missingThumbnail.thumbnail)
        assertTrue(DownloadFixtures.remoteThumbnail.thumbnail is MediaThumbnail.Remote)
        assertTrue(
            DownloadFixtures.longDestination.destination
                .toString()
                .length >
                DownloadFixtures.normal.destination
                    .toString()
                    .length,
        )
        assertTrue(DownloadFixtures.failure.source != DownloadFixtures.normal.source)
    }

    @Test
    fun `download items reject invalid identity duration and thumbnail data`() {
        assertFailsWith<IllegalArgumentException> { DownloadFixtures.normal.copy(title = " ") }
        assertFailsWith<IllegalArgumentException> { DownloadFixtures.normal.copy(duration = (-1).milliseconds) }
        assertFailsWith<IllegalArgumentException> { ThumbnailData(byteArrayOf()) }
    }

    @Test
    fun `media semantics announce missing preview only when unavailable`() {
        val available = mediaContentDescription(DownloadFixtures.normal, thumbnailAvailable = true)
        val unavailable = mediaContentDescription(DownloadFixtures.missingThumbnail, thumbnailAvailable = false)

        assertFalse(available.contains("Preview unavailable"))
        assertTrue(unavailable.endsWith("Preview unavailable."))
    }

    @Test
    fun `state holder accepts explicit design states`() {
        val holder = immediateHolder()

        holder.showReady(DownloadFixtures.longTitle)
        assertEquals(DownloadUiState.Ready(DownloadFixtures.longTitle), holder.state)

        holder.showDesignState(DownloadUiState.Empty)
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
            assertEquals(pastedUrl, (holder.state as DownloadUiState.Resolving).item.source.toString())

            val typedUrl = "${pastedUrl}x"
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(typedUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(349.milliseconds)
            assertEquals(DownloadUiState.Empty, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(typedUrl, (holder.state as DownloadUiState.Resolving).item.source.toString())
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

            advanceTimeBy(PASTE_INTENT_LIFETIME)
            holder.linkFieldState.setTextAndPlaceCursorAtEnd("https://youtu.b")
            Snapshot.sendApplyNotifications()
            runCurrent()
            val typedUrl = "https://youtu.be"
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(typedUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()

            advanceTimeBy(349.milliseconds)
            assertEquals(DownloadUiState.Empty, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(typedUrl, (holder.state as DownloadUiState.Resolving).item.source.toString())
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

            holder.linkFieldState.setTextAndPlaceCursorAtEnd("https://youtu.b/first")
            Snapshot.sendApplyNotifications()
            runCurrent()
            holder.linkFieldState.setTextAndPlaceCursorAtEnd("https://youtu.be/first")
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(200.milliseconds)
            val newerUrl = "https://youtu.be/firstx"
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(newerUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()

            advanceTimeBy(349.milliseconds)
            assertEquals(DownloadUiState.Empty, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(newerUrl, (holder.state as DownloadUiState.Resolving).item.source.toString())
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
            assertEquals(DownloadUiState.Ready(resolving.item), holder.state)
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
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
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
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
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
            assertEquals(validUrl, resolving.item.source.toString())
            assertNull(holder.validationMessage)

            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()
            assertEquals(DownloadUiState.Ready(resolving.item), holder.state)

            pasteIntent = true
            holder.linkFieldState.setTextAndPlaceCursorAtEnd("not a url")
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(DownloadUiState.Empty, holder.state)
            assertEquals(ProductCopy.INVALID_LINK_MESSAGE, holder.validationMessage)
        }

    @Test
    fun `editing clears stale state and forced resolving rejects stale preview completion`() =
        runTest {
            val runtime = ControlledPreviewRuntime()
            val holder = testHolder(runtime)
            holder.showReady()

            assertTrue(holder.observeLinkEdit("https://youtube.com/watch?v=new"))
            assertEquals(DownloadUiState.Empty, holder.state)

            holder.beginResolution("https://youtu.be/stale-preview")
            runCurrent()
            holder.showResolving()
            val forced = holder.state as DownloadUiState.Resolving
            runtime.completePreview()
            runCurrent()
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()

            assertEquals(forced, holder.state)
        }

    @Test
    fun `fresh ready defaults to video and resolved best quality`() {
        val holder = immediateHolder()

        holder.showReady()

        assertEquals(DownloadMode.Video, holder.selectedMode)
        assertEquals(videoQualityOptions.map(DownloadQuality::label), holder.qualityOptions)
        assertEquals("Best available — 2160p", holder.selectedQualityLabel)
        assertEquals(DownloadFixtures.normal.destination, holder.destination)
        assertNull(holder.readyFeedback)
    }

    @Test
    fun `mode and quality changes stay mutually exclusive and reset best quality`() {
        val holder = immediateHolder()
        holder.showReady()

        holder.selectQuality(2)
        assertEquals("1080p", holder.selectedQualityLabel)

        holder.selectMode(DownloadMode.Audio)
        assertEquals(DownloadMode.Audio, holder.selectedMode)
        assertEquals(audioQualityOptions.map(DownloadQuality::label), holder.qualityOptions)
        assertEquals("Original audio (no conversion)", holder.selectedQualityLabel)

        holder.selectQuality(1)
        assertEquals("MP3 — Best quality", holder.selectedQualityLabel)

        holder.selectMode(DownloadMode.Video)
        assertEquals("Best available — 2160p", holder.selectedQualityLabel)
    }

    @Test
    fun `destination cycles deterministic fixtures and acknowledges the visible value`() {
        val holder = immediateHolder()
        holder.showReady()

        holder.changeDestination()

        assertEquals(DownloadFixtures.longDestination.destination, holder.destination)
        assertEquals("Save location changed to ${holder.destination}.", holder.readyFeedback)
    }

    @Test
    fun `each ready media requires explicit download authorization`() {
        val holder = immediateHolder()
        holder.showReady()

        assertFalse(holder.downloadEnabled)
        holder.download()
        assertEquals(DownloadUiState.Ready(DownloadFixtures.normal), holder.state)

        holder.updateDownloadAuthorization(true)
        assertTrue(holder.downloadEnabled)

        holder.showReady(DownloadFixtures.longTitle)
        assertFalse(holder.downloadAuthorizationAccepted)
        assertFalse(holder.downloadEnabled)
    }

    @Test
    fun `full terms preserve setup consent and ready choices`() {
        val holder = immediateHolder()
        holder.showSetup()
        holder.updateToolSetupConsent(true)

        holder.showLegalDetails()
        assertTrue(holder.showingLegalDetails)
        holder.hideLegalDetails()
        assertTrue(holder.toolSetupAccepted)

        holder.showReady()
        holder.selectMode(DownloadMode.Audio)
        holder.selectQuality(1)
        holder.changeDestination()
        holder.updateDownloadAuthorization(true)
        val destination = holder.destination
        val readyItem = (holder.state as DownloadUiState.Ready).item

        holder.showLegalDetails()
        assertTrue(holder.showingLegalDetails)
        holder.hideLegalDetails()
        assertEquals(DownloadUiState.Ready(readyItem), holder.state)
        assertEquals(DownloadMode.Audio, holder.selectedMode)
        assertEquals(1, holder.selectedQualityIndex)
        assertEquals(destination, holder.destination)
        assertTrue(holder.downloadAuthorizationAccepted)
    }

    @Test
    fun `success timeline advances on exact 350 millisecond boundaries`() =
        runTest {
            val holder = testHolder()
            holder.showReady()
            holder.startAuthorizedDownload()
            runCurrent()
            assertEquals(DownloadUiState.Downloading(DownloadFixtures.normal, DownloadProgress.Zero), holder.state)

            fakeProgressSteps.forEach { expected ->
                advanceTimeBy(FAKE_PROGRESS_INTERVAL - 1.milliseconds)
                assertTrue((holder.state as DownloadUiState.Downloading).progress.percent < expected.percent)
                advanceTimeBy(1.milliseconds)
                runCurrent()
                assertEquals(DownloadUiState.Downloading(DownloadFixtures.normal, expected), holder.state)
            }

            advanceTimeBy(FAKE_PROGRESS_INTERVAL)
            runCurrent()
            assertEquals(DownloadUiState.Completed(DownloadFixtures.normal), holder.state)
        }

    @Test
    fun `failure fixture enters error at 68 percent`() =
        runTest {
            val holder = testHolder()
            holder.showReady(DownloadFixtures.failure)
            holder.startAuthorizedDownload()
            runCurrent()

            repeat(2) {
                advanceTimeBy(FAKE_PROGRESS_INTERVAL)
                runCurrent()
            }
            assertEquals(DownloadUiState.Downloading(DownloadFixtures.failure, DownloadProgress(43)), holder.state)

            advanceTimeBy(FAKE_PROGRESS_INTERVAL)
            runCurrent()
            assertEquals(DownloadUiState.Error(DownloadFixtures.failure), holder.state)
        }

    @Test
    fun `cancel preserves choices destination url and media`() =
        runTest {
            val holder = testHolder()
            holder.showReady()
            holder.selectMode(DownloadMode.Audio)
            holder.selectQuality(1)
            holder.changeDestination()
            val destination = holder.destination

            holder.startAuthorizedDownload()
            runCurrent()
            holder.cancelDownload()
            runCurrent()
            advanceTimeBy(FAKE_PROGRESS_INTERVAL * 2)

            assertEquals(
                DownloadUiState.Ready(DownloadFixtures.normal.copy(destination = requireNotNull(destination))),
                holder.state,
            )
            assertEquals(DownloadMode.Audio, holder.selectedMode)
            assertEquals(1, holder.selectedQualityIndex)
            assertEquals(destination, holder.destination)
            assertEquals(DownloadFixtures.normal.source.toString(), holder.linkFieldState.text.toString())
        }

    @Test
    fun `retry restarts at zero with prior choices and cancels when forced`() =
        runTest {
            val holder = testHolder()
            holder.showReady(DownloadFixtures.failure)
            holder.selectMode(DownloadMode.Audio)
            holder.selectQuality(1)
            holder.changeDestination()
            val destination = holder.destination
            holder.startAuthorizedDownload()
            runCurrent()
            advanceTimeBy(FAKE_PROGRESS_INTERVAL * 3)
            runCurrent()
            val failureItem = DownloadFixtures.failure.copy(destination = requireNotNull(destination))
            assertEquals(DownloadUiState.Error(failureItem), holder.state)

            holder.retryDownload()
            runCurrent()

            assertEquals(DownloadUiState.Downloading(failureItem, DownloadProgress.Zero), holder.state)
            assertEquals(DownloadMode.Audio, holder.selectedMode)
            assertEquals(1, holder.selectedQualityIndex)
            assertEquals(destination, holder.destination)

            holder.showReady()
            runCurrent()
            advanceTimeBy(FAKE_PROGRESS_INTERVAL * 2)
            runCurrent()

            assertEquals(DownloadUiState.Ready(DownloadFixtures.normal), holder.state)
        }

    @Test
    fun `open folder acknowledges without leaving completed and download another resets focus`() {
        val holder = immediateHolder()
        holder.showCompleted()

        holder.openFolder()
        assertEquals(DownloadUiState.Completed(DownloadFixtures.normal), holder.state)
        assertEquals(ProductCopy.OPEN_FOLDER_ACKNOWLEDGEMENT, holder.completedFeedback)

        val focusRequest = holder.linkFocusRequest
        holder.downloadAnother()
        assertEquals(DownloadUiState.Empty, holder.state)
        assertEquals(focusRequest + 1, holder.linkFocusRequest)
        assertEquals("", holder.linkFieldState.text.toString())
        assertNull(holder.destination)
    }

    @Test
    fun `link edit reset force and close cancel stale progress`() =
        runTest {
            val holder = testHolder()
            holder.showReady()
            holder.startAuthorizedDownload()
            runCurrent()
            holder.observeLinkEdit("https://youtu.be/new-link")
            advanceTimeBy(FAKE_PROGRESS_INTERVAL * 6)
            runCurrent()
            assertEquals(DownloadUiState.Empty, holder.state)

            holder.showReady()
            holder.startAuthorizedDownload()
            runCurrent()
            holder.showDesignState(DownloadUiState.Empty)
            advanceTimeBy(FAKE_PROGRESS_INTERVAL * 6)
            runCurrent()
            assertEquals(DownloadUiState.Empty, holder.state)

            holder.showReady()
            holder.startAuthorizedDownload()
            runCurrent()
            holder.showCompleted()
            advanceTimeBy(FAKE_PROGRESS_INTERVAL * 6)
            runCurrent()
            assertEquals(DownloadUiState.Completed(DownloadFixtures.normal), holder.state)

            holder.showReady()
            holder.startAuthorizedDownload()
            runCurrent()
            val stateAtClose = holder.state
            holder.close()
            advanceTimeBy(FAKE_PROGRESS_INTERVAL * 6)
            runCurrent()
            assertEquals(stateAtClose, holder.state)
        }

    @Test
    fun `forced downloading bypasses timers and locks choices`() =
        runTest {
            val holder = testHolder()
            holder.showDownloading(progress = DownloadProgress(43))
            holder.selectMode(DownloadMode.Audio)
            holder.selectQuality(2)
            holder.changeDestination()

            advanceTimeBy(FAKE_PROGRESS_INTERVAL * 6)
            runCurrent()

            assertEquals(DownloadUiState.Downloading(DownloadFixtures.normal, DownloadProgress(43)), holder.state)
            assertEquals(DownloadMode.Video, holder.selectedMode)
            assertEquals(0, holder.selectedQualityIndex)
            assertEquals(DownloadFixtures.normal.destination, holder.destination)
        }

    @Test
    fun `stale progress callback cannot overwrite replacement downloading state`() =
        runTest {
            val runtime = CapturingDownloadRuntime()
            val holder = testHolder(runtime)
            val forced = DownloadUiState.Downloading(DownloadFixtures.normal, DownloadProgress(87))
            try {
                holder.showReady()
                holder.startAuthorizedDownload()
                runCurrent()
                val submitStaleProgress = runtime.progressCallbacks.single()

                holder.showDownloading(progress = DownloadProgress(87))
                submitStaleProgress(DownloadProgress(18))
                runCurrent()

                assertEquals(forced, holder.state)
            } finally {
                holder.close()
            }
        }

    @Test
    fun `source resolution fixture and reset clear stale ready choices and feedback`() =
        runTest {
            val holder = testHolder()
            holder.showReady()
            holder.selectMode(DownloadMode.Audio)
            holder.selectQuality(1)
            holder.changeDestination()
            holder.startAuthorizedDownload()

            holder.observeLinkEdit("https://youtube.com/watch?v=new")
            assertEquals(DownloadUiState.Empty, holder.state)
            assertEquals(DownloadMode.Video, holder.selectedMode)
            assertEquals(0, holder.selectedQualityIndex)
            assertNull(holder.destination)
            assertNull(holder.readyFeedback)

            holder.beginResolution("https://youtube.com/watch?v=new")
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()
            assertEquals(DownloadFixtures.normal.destination, holder.destination)
            assertEquals("Best available — 2160p", holder.selectedQualityLabel)

            holder.showReady(DownloadFixtures.longDestination)
            assertEquals(DownloadFixtures.longDestination.destination, holder.destination)
            assertNull(holder.readyFeedback)

            holder.showDesignState(DownloadUiState.Empty)
            assertEquals(DownloadUiState.Empty, holder.state)
            assertEquals(DownloadMode.Video, holder.selectedMode)
            assertEquals(0, holder.selectedQualityIndex)
            assertNull(holder.destination)
            assertNull(holder.readyFeedback)

            holder.showReady()
            assertEquals(DownloadUiState.Ready(DownloadFixtures.normal), holder.state)
            assertEquals("Best available — 2160p", holder.selectedQualityLabel)
            assertEquals(DownloadFixtures.normal.destination, holder.destination)
        }

    private class ControlledPreviewRuntime(
        private val tools: List<DownloadTool> = emptyList(),
        private val failPreview: Boolean = false,
    ) : DownloadRuntime by PreviewDownloadRuntime() {
        private val previewGate = CompletableDeferred<Unit>()

        var installCount = 0
            private set

        var missingToolsCount = 0
            private set

        fun completePreview() {
            previewGate.complete(Unit)
        }

        override suspend fun preview(source: YouTubeUrl): DownloadItem {
            previewGate.await()
            if (failPreview) throw DownloadRuntimeException()
            return DownloadFixtures.normal.copy(source = source)
        }

        override fun missingTools(): List<DownloadTool> {
            missingToolsCount += 1
            return if (installCount == 0) tools else emptyList()
        }

        override suspend fun installMissingTools() {
            installCount += 1
        }
    }

    private class CapturingDownloadRuntime : DownloadRuntime by PreviewDownloadRuntime() {
        val progressCallbacks = mutableListOf<suspend (DownloadProgress) -> Unit>()

        override suspend fun download(
            request: DownloadRequest,
            onProgress: suspend (DownloadProgress) -> Unit,
        ) {
            progressCallbacks += onProgress
            CompletableDeferred<Unit>().await()
        }
    }
}
