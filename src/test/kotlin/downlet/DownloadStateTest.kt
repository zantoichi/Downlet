package downlet

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
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
    @Test
    fun `formats become ready without waiting for preview and late preview preserves choices`() =
        runTest {
            val preview = CompletableDeferred<DownloadItem>()
            val runtime =
                object : DownloadRuntime by PreviewDownloadRuntime() {
                    override suspend fun preview(source: YouTubeUrl): DownloadItem = preview.await()
                }
            val holder = testHolder(runtime)
            holder.beginResolution(DownloadFixtures.normal.source.toString())
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()
            assertTrue(holder.state is DownloadUiState.Ready)
            holder.selectMode(DownloadMode.Audio)
            holder.selectQuality(1)
            holder.updateDownloadAuthorization(true)
            preview.complete(DownloadFixtures.normal.copy(title = "Optional preview title"))
            runCurrent()
            assertEquals(DownloadFixtures.normal.title, (holder.state as DownloadUiState.Ready).item.title)
            assertEquals(DownloadMode.Audio, holder.selectedMode)
            assertEquals(1, holder.selectedQualityIndex)
            assertTrue(holder.downloadAuthorizationAccepted)
        }

    @Test
    fun `resolution expires after thirty seconds even without process output`() =
        runTest {
            val runtime =
                object : DownloadRuntime by PreviewDownloadRuntime() {
                    override suspend fun resolve(
                        source: YouTubeUrl,
                        browserCookies: BrowserCookieSource?,
                    ): DownloadItem = awaitCancellation()
                }
            val holder = testHolder(runtime)
            holder.beginResolution(DownloadFixtures.normal.source.toString())
            advanceTimeBy(29_999)
            runCurrent()
            assertTrue(holder.state is DownloadUiState.Resolving)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(DownloadFailureReason.Network, (holder.state as DownloadUiState.Error).reason)
        }

    private fun transferProgress(percent: Int): DownloadProgress.Transferring =
        DownloadProgress.Transferring(
            downloadedBytes = percent.toLong(),
            totalBytes = 100,
            fraction = percent / 100f,
        )

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
        intent: ToolSetupIntent = ToolSetupIntent.Install,
    ) {
        showDesignState(DownloadUiState.Setup(item, tools, phase, intent))
    }

    private fun DownloadStateHolder.showResolving(item: DownloadItem = DownloadFixtures.normal) {
        showDesignState(DownloadUiState.Resolving(item))
    }

    private fun DownloadStateHolder.showDownloading(
        item: DownloadItem = DownloadFixtures.normal,
        progress: DownloadProgress = transferProgress(43),
    ) {
        showDesignState(DownloadUiState.Downloading(item, progress))
    }

    private fun DownloadStateHolder.showCompleted(
        item: DownloadItem = DownloadFixtures.normal,
        file: Path = DownloadFixtures.completedFile(item),
    ) {
        showDesignState(DownloadUiState.Completed(item, file))
    }

    private fun canonicalUrl(value: String): String = requireNotNull(YouTubeUrl.parse(value)).toString()

    private fun testVideoId(label: String): String = label.padEnd(11, '0').also { require(it.length == 11) }

    private fun testVideoUrl(label: String): String = "https://youtu.be/${testVideoId(label)}"

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
                DownloadUiState.Downloading(normal, transferProgress(43)),
                DownloadUiState.Completed(normal, DownloadFixtures.completedFile(normal)),
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
            val sourceUrl = testVideoUrl("setup")
            val canonicalUrl = canonicalUrl(sourceUrl)

            holder.beginResolution(sourceUrl)
            runCurrent()
            assertEquals(canonicalUrl, (holder.state as DownloadUiState.Setup).item.source.toString())
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
            assertEquals(canonicalUrl, (holder.state as DownloadUiState.Resolving).item.source.toString())
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()
            assertEquals(canonicalUrl, (holder.state as DownloadUiState.Ready).item.source.toString())
        }

    @Test
    fun `installed tools move from previewing directly to resolving`() =
        runTest {
            val runtime = ControlledPreviewRuntime()
            val holder = testHolder(runtime)
            val sourceUrl = testVideoUrl("ready")
            val canonicalUrl = canonicalUrl(sourceUrl)

            holder.beginResolution(sourceUrl)
            runCurrent()
            assertEquals(canonicalUrl, (holder.state as DownloadUiState.Resolving).item.source.toString())

            runtime.completePreview()
            runCurrent()
            assertEquals(canonicalUrl, (holder.state as DownloadUiState.Resolving).item.source.toString())

            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()
            assertEquals(canonicalUrl, (holder.state as DownloadUiState.Ready).item.source.toString())
        }

    @Test
    fun `damaged managed tools repair automatically after preview`() =
        runTest {
            val runtime = ControlledRepairRuntime(ToolStatus(repairable = listOf(DownloadTool.YtDlp)))
            val holder = testHolder(runtime)

            holder.beginResolution(testVideoUrl("preview"))
            runCurrent()

            assertEquals(
                DownloadUiState.Setup(
                    DownloadFixtures.normal.copy(
                        source = requireNotNull(YouTubeUrl.parse(testVideoUrl("preview"))),
                    ),
                    listOf(DownloadTool.YtDlp),
                    ToolSetupPhase.Installing,
                    ToolSetupIntent.Repair,
                ),
                holder.state,
            )
            assertFalse(holder.toolSetupAccepted)
            assertFalse(holder.toolSetupEnabled)

            runtime.completeRepair()
            runCurrent()
            assertTrue(holder.state is DownloadUiState.Resolving)
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()

            assertTrue(holder.state is DownloadUiState.Ready)
            assertEquals(1, runtime.repairCount)
        }

    @Test
    fun `failed automatic repair retries without consent`() =
        runTest {
            val runtime =
                ControlledRepairRuntime(
                    ToolStatus(repairable = listOf(DownloadTool.Ffmpeg)),
                    failRepair = true,
                ).also(ControlledRepairRuntime::completeRepair)
            val holder = testHolder(runtime)

            holder.beginResolution(testVideoUrl("retry"))
            runCurrent()

            val failed = holder.state as DownloadUiState.Setup
            assertEquals(ToolSetupIntent.Repair, failed.intent)
            assertEquals(ToolSetupPhase.Failed, failed.phase)
            assertFalse(holder.toolSetupAccepted)
            assertTrue(holder.toolSetupEnabled)

            runtime.failRepair = false
            holder.installTools()
            runCurrent()
            assertTrue(holder.state is DownloadUiState.Resolving)
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()

            assertTrue(holder.state is DownloadUiState.Ready)
            assertEquals(2, runtime.repairCount)
        }

    @Test
    fun `automatic repair returns to consent setup for another missing tool`() =
        runTest {
            val runtime =
                ControlledRepairRuntime(
                    initialStatus = ToolStatus(repairable = listOf(DownloadTool.YtDlp)),
                    statusAfterRepair = ToolStatus(missing = listOf(DownloadTool.Ffmpeg)),
                ).also(ControlledRepairRuntime::completeRepair)
            val holder = testHolder(runtime)

            holder.beginResolution(testVideoUrl("mixed"))
            runCurrent()

            assertEquals(
                DownloadUiState.Setup(
                    item =
                        DownloadFixtures.normal.copy(
                            source = requireNotNull(YouTubeUrl.parse(testVideoUrl("mixed"))),
                        ),
                    tools = listOf(DownloadTool.Ffmpeg),
                ),
                holder.state,
            )
            assertFalse(holder.toolSetupAccepted)
            assertFalse(holder.toolSetupEnabled)
        }

    @Test
    fun `repair during download preserves destination and returns ready without resuming`() =
        runTest {
            val runtime = ControlledRepairRuntime(failDownloadWithManagedTool = true)
            val holder = testHolder(runtime)
            holder.showReady()
            holder.selectMode(DownloadMode.Audio)
            holder.selectQuality(1)
            holder.changeDestination()
            val destination = requireNotNull(holder.destination)

            holder.startAuthorizedDownload()
            runCurrent()

            val repairing = holder.state as DownloadUiState.Setup
            assertEquals(ToolSetupIntent.Repair, repairing.intent)
            assertEquals(ToolSetupPhase.Installing, repairing.phase)
            runtime.completeRepair()
            runCurrent()
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()

            assertTrue(holder.state is DownloadUiState.Ready)
            assertEquals(destination, holder.destination)
            assertEquals(DownloadMode.Video, holder.selectedMode)
            assertEquals(0, holder.selectedQualityIndex)
            assertFalse(holder.downloadAuthorizationAccepted)
            assertEquals(1, runtime.downloadCount)
        }

    @Test
    fun `repeated managed failure after repair becomes a tool error`() =
        runTest {
            val runtime =
                ControlledRepairRuntime(
                    ToolStatus(repairable = listOf(DownloadTool.YtDlp)),
                    failResolveWithManagedTool = true,
                ).also(ControlledRepairRuntime::completeRepair)
            val holder = testHolder(runtime)

            holder.beginResolution(testVideoUrl("repeat"))
            runCurrent()

            assertEquals(
                DownloadFailureReason.Tool,
                (holder.state as DownloadUiState.Error).reason,
            )
            assertEquals(1, runtime.repairCount)
        }

    @Test
    fun `editing the url cancels automatic repair`() =
        runTest {
            val runtime = ControlledRepairRuntime(ToolStatus(repairable = listOf(DownloadTool.YtDlp)))
            val holder = testHolder(runtime)

            holder.beginResolution(testVideoUrl("cancel"))
            runCurrent()
            assertTrue(holder.state is DownloadUiState.Setup)

            assertTrue(holder.observeLinkEdit(testVideoUrl("replacement")))
            runtime.completeRepair()
            runCurrent()

            assertEquals(DownloadUiState.Empty, holder.state)
        }

    @Test
    fun `preview failure continues to consent before installing tools`() =
        runTest {
            val runtime =
                ControlledPreviewRuntime(
                    DownloadTool.entries,
                    failPreview = true,
                    failureReason = DownloadFailureReason.Network,
                )
            val holder = testHolder(runtime)

            holder.beginResolution(testVideoUrl("unavailable"))
            runCurrent()
            runtime.completePreview()
            runCurrent()

            assertTrue(holder.state is DownloadUiState.Setup)
            assertEquals(1, runtime.toolStatusCount)
            assertEquals(0, runtime.installCount)
            holder.updateToolSetupConsent(true)
            holder.installTools()
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(holder.state is DownloadUiState.Ready)
        }

    @Test
    fun `failed preview defers to the extractor failure and cancellation stays cancelled`() =
        runTest {
            val runtime =
                object : DownloadRuntime by PreviewDownloadRuntime() {
                    override suspend fun preview(source: YouTubeUrl): DownloadItem =
                        throw DownloadRuntimeException(reason = DownloadFailureReason.Network)

                    override suspend fun resolve(
                        source: YouTubeUrl,
                        browserCookies: BrowserCookieSource?,
                    ): DownloadItem = throw DownloadRuntimeException(reason = DownloadFailureReason.BotChallenge)
                }
            val holder = testHolder(runtime)
            holder.beginResolution(testVideoUrl("challenge"))
            runCurrent()
            assertEquals(DownloadFailureReason.BotChallenge, (holder.state as DownloadUiState.Error).reason)

            val pending = ControlledPreviewRuntime(failPreview = true)
            val cancelled = testHolder(pending)
            cancelled.beginResolution(testVideoUrl("cancel"))
            runCurrent()
            cancelled.observeLinkEdit("")
            pending.completePreview()
            runCurrent()
            assertEquals(DownloadUiState.Empty, cancelled.state)
            assertEquals(1, pending.toolStatusCount)
        }

    @Test
    fun `browser cookies survive edits retries and download another until close`() =
        runTest {
            val runtime = BrowserCookieRuntime()
            val holder = testHolder(runtime)
            val firstUrl = testVideoUrl("protected")

            holder.beginResolution(firstUrl)
            runCurrent()

            assertEquals(
                DownloadUiState.Error(
                    DownloadFixtures.normal.copy(source = requireNotNull(YouTubeUrl.parse(firstUrl))),
                    DownloadErrorKind.Resolution,
                    DownloadFailureReason.Authentication,
                ),
                holder.state,
            )
            assertEquals(listOf<BrowserCookieSource?>(null), runtime.resolvedWith)

            holder.retryWithBrowserCookies(BrowserCookieSource.Chrome)
            runCurrent()
            assertEquals(
                DownloadFailureReason.Network,
                (holder.state as DownloadUiState.Error).reason,
            )
            assertEquals(listOf(null, BrowserCookieSource.Chrome), runtime.resolvedWith)

            holder.retryDownload()
            runCurrent()
            assertTrue(holder.state is DownloadUiState.Ready)
            assertEquals(
                listOf(null, BrowserCookieSource.Chrome, BrowserCookieSource.Chrome),
                runtime.resolvedWith,
            )

            holder.startAuthorizedDownload()
            runCurrent()
            assertEquals(BrowserCookieSource.Chrome, runtime.downloadRequests.single().browserCookies)

            holder.downloadAnother()
            val secondUrl = testVideoUrl("second")
            assertTrue(holder.observeLinkEdit(secondUrl))
            holder.beginResolution(secondUrl)
            runCurrent()
            assertEquals(
                listOf(null, BrowserCookieSource.Chrome, BrowserCookieSource.Chrome, BrowserCookieSource.Chrome),
                runtime.resolvedWith,
            )
            holder.close()
            testHolder(runtime).beginResolution(firstUrl)
            runCurrent()
            assertNull(runtime.resolvedWith.last())
        }

    @Test
    fun `invalid progress values are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DownloadProgress.Transferring(downloadedBytes = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadProgress.Transferring(downloadedBytes = 101, totalBytes = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadProgress.Transferring(downloadedBytes = 1, fraction = 1.01f)
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
    fun `youtube url parser canonicalizes supported single-video forms`() {
        val videoId = "dQw4w9WgXcQ"
        val canonical = "https://www.youtube.com/watch?v=$videoId"

        listOf(
            "https://youtube.com/watch?feature=share&v=$videoId&t=43#fragment",
            "https://www.youtube.com/watch?v=$videoId",
            "http://m.youtube.com/watch?v=$videoId",
            "https://music.youtube.com/watch?v=$videoId&list=RDignored",
            "https://youtu.be/$videoId?t=43",
            "https://youtube.com/shorts/$videoId?si=ignored",
            "https://youtube.com/embed/$videoId",
            "https://youtube.com/live/$videoId?feature=share",
            "https://www.youtube-nocookie.com/embed/$videoId",
        ).forEach { value ->
            assertEquals(canonical, YouTubeUrl.parse(value)?.toString(), value)
        }

        listOf(
            "",
            "not a url",
            "ftp://youtube.com/video",
            "https://youtube.example/video",
            "https://youtube.com.evil.example/video",
            "https:///missing-host",
            "https://youtu.be",
            "https://youtu.be/$videoId/extra",
            "https://youtube.com",
            "https://youtube.com/watch",
            "https://youtube.com/watch?list=playlist",
            "https://youtube.com/playlist?list=playlist",
            "https://youtube.com/@channel",
            "https://youtube.com/results?search_query=video",
            "https://youtube.com/watch?v=invalid.value",
            "https://youtu.be/1234567890",
            "https://youtu.be/123456789012",
            "https://www.youtube-nocookie.com/watch?v=$videoId",
            "https://user@youtube.com/watch?v=$videoId",
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

            val pastedUrl = "${testVideoUrl("pasted")}?si=ignored"
            val pastedCanonical = canonicalUrl(pastedUrl)
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(pastedUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(pastedCanonical, holder.linkFieldState.text.toString())
            assertEquals(pastedCanonical, (holder.state as DownloadUiState.Resolving).item.source.toString())
            Snapshot.sendApplyNotifications()
            runCurrent()

            val typedUrl = pastedCanonical.dropLast(1) + "x"
            val typedCanonical = canonicalUrl(typedUrl)
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(typedUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(349.milliseconds)
            assertEquals(DownloadUiState.Empty, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(typedCanonical, (holder.state as DownloadUiState.Resolving).item.source.toString())
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
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(testVideoUrl("typed") + ".")
            Snapshot.sendApplyNotifications()
            runCurrent()
            val typedUrl = testVideoUrl("typed")
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(typedUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()

            advanceTimeBy(349.milliseconds)
            assertEquals(DownloadUiState.Empty, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(canonicalUrl(typedUrl), (holder.state as DownloadUiState.Resolving).item.source.toString())
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

            holder.linkFieldState.setTextAndPlaceCursorAtEnd("https://youtu.b/${testVideoId("first")}")
            Snapshot.sendApplyNotifications()
            runCurrent()
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(testVideoUrl("first"))
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(200.milliseconds)
            val newerUrl = testVideoUrl("firstx")
            holder.linkFieldState.setTextAndPlaceCursorAtEnd(newerUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()

            advanceTimeBy(349.milliseconds)
            assertEquals(DownloadUiState.Empty, holder.state)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(canonicalUrl(newerUrl), (holder.state as DownloadUiState.Resolving).item.source.toString())
        }

    @Test
    fun `automatic resolution completes at 550 milliseconds`() =
        runTest {
            val holder = testHolder()
            holder.beginResolution(testVideoUrl("automatic"))
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
            holder.beginResolution(testVideoUrl("stale"))
            runCurrent()
            advanceTimeBy(200.milliseconds)

            val newerUrl = testVideoUrl("newer")
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
            holder.beginResolution(testVideoUrl("closing"))
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
            val validUrl = testVideoUrl("quiet")
            val canonicalUrl = canonicalUrl(validUrl)

            holder.linkFieldState.setTextAndPlaceCursorAtEnd(validUrl)
            Snapshot.sendApplyNotifications()
            runCurrent()
            val resolving = holder.state as DownloadUiState.Resolving
            assertEquals(canonicalUrl, holder.linkFieldState.text.toString())
            assertEquals(canonicalUrl, resolving.item.source.toString())
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

            assertTrue(holder.observeLinkEdit("https://youtube.com/watch?v=${testVideoId("new")}"))
            assertEquals(DownloadUiState.Empty, holder.state)

            holder.beginResolution(testVideoUrl("stale"))
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
        assertEquals("Best · 2160p60 · ~18.4 Mbps", holder.selectedQualityLabel)
        assertEquals(DownloadFixtures.normal.destination, holder.destination)
        assertNull(holder.readyFeedback)
    }

    @Test
    fun `mode and quality changes stay mutually exclusive and reset best quality`() {
        val holder = immediateHolder()
        holder.showReady()

        holder.selectQuality(2)
        assertEquals("1080p60 · ~4.8 Mbps", holder.selectedQualityLabel)

        holder.selectMode(DownloadMode.Audio)
        assertEquals(DownloadMode.Audio, holder.selectedMode)
        assertEquals(
            audioQualityOptions(DownloadFixtures.normal.originalAudio).map(DownloadQuality::label),
            holder.qualityOptions,
        )
        assertEquals("Original · Opus/WebM · ~130 kbps", holder.selectedQualityLabel)

        holder.selectQuality(1)
        assertEquals("MP3 · High-quality VBR · ~190 kbps", holder.selectedQualityLabel)

        holder.selectMode(DownloadMode.Video)
        assertEquals("Best · 2160p60 · ~18.4 Mbps", holder.selectedQualityLabel)
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
            assertEquals(DownloadUiState.Downloading(DownloadFixtures.normal, DownloadProgress.Preparing), holder.state)

            fakeProgressSteps.forEach { expected ->
                advanceTimeBy(FAKE_PROGRESS_INTERVAL - 1.milliseconds)
                assertTrue(holder.state != DownloadUiState.Downloading(DownloadFixtures.normal, expected))
                advanceTimeBy(1.milliseconds)
                runCurrent()
                assertEquals(DownloadUiState.Downloading(DownloadFixtures.normal, expected), holder.state)
            }
            advanceTimeBy(FAKE_PROGRESS_INTERVAL)
            runCurrent()
            assertEquals(
                DownloadUiState.Downloading(
                    DownloadFixtures.normal,
                    DownloadProgress.Processing(DownloadProcessingStage.Merging),
                ),
                holder.state,
            )

            advanceTimeBy(FAKE_PROGRESS_INTERVAL)
            runCurrent()
            assertEquals(
                DownloadUiState.Completed(DownloadFixtures.normal, DownloadFixtures.completedFile()),
                holder.state,
            )
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
            assertEquals(
                DownloadUiState.Downloading(DownloadFixtures.failure, fakeProgressSteps[1]),
                holder.state,
            )

            advanceTimeBy(FAKE_PROGRESS_INTERVAL)
            runCurrent()
            assertEquals(DownloadUiState.Error(DownloadFixtures.failure), holder.state)
        }

    @Test
    fun `publication failure enters error instead of completed`() =
        runTest {
            val runtime =
                object : DownloadRuntime by PreviewDownloadRuntime() {
                    override suspend fun download(
                        request: DownloadRequest,
                        onProgress: suspend (DownloadProgress) -> Unit,
                    ): Path = throw DownloadRuntimeException(reason = DownloadFailureReason.Storage)
                }
            val holder = testHolder(runtime)
            holder.showReady()

            holder.startAuthorizedDownload()
            runCurrent()

            assertEquals(
                DownloadUiState.Error(DownloadFixtures.normal, reason = DownloadFailureReason.Storage),
                holder.state,
            )
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

            assertEquals(DownloadUiState.Downloading(failureItem, DownloadProgress.Preparing), holder.state)
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
    fun `storage error changes destination in place and retry uses it`() =
        runTest {
            val holder = testHolder()
            holder.showDesignState(
                DownloadUiState.Error(
                    DownloadFixtures.normal,
                    reason = DownloadFailureReason.Storage,
                ),
            )

            holder.changeDestination()
            val updated = DownloadFixtures.normal.copy(destination = DownloadFixtures.longDestination.destination)
            assertEquals(
                DownloadUiState.Error(updated, reason = DownloadFailureReason.Storage),
                holder.state,
            )

            holder.retryDownload()
            runCurrent()
            assertEquals(DownloadUiState.Downloading(updated, DownloadProgress.Preparing), holder.state)
        }

    @Test
    fun `show in folder uses exact completed path without leaving completed`() {
        val shownFiles = mutableListOf<Path>()
        val runtime =
            object : DownloadRuntime by PreviewDownloadRuntime() {
                override fun showInFolder(file: Path): String? {
                    shownFiles.add(file)
                    return ProductCopy.SHOW_IN_FOLDER_ACKNOWLEDGEMENT
                }
            }
        val holder = immediateHolder(runtime)
        val completedFile = DownloadFixtures.completedFile()
        holder.showCompleted(file = completedFile)

        holder.showInFolder()
        assertEquals(listOf(completedFile), shownFiles)
        assertEquals(DownloadUiState.Completed(DownloadFixtures.normal, completedFile), holder.state)
        assertEquals(ProductCopy.SHOW_IN_FOLDER_ACKNOWLEDGEMENT, holder.completedFeedback)

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
            holder.observeLinkEdit(testVideoUrl("new-link"))
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
            assertEquals(
                DownloadUiState.Completed(DownloadFixtures.normal, DownloadFixtures.completedFile()),
                holder.state,
            )

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
            holder.showDownloading(progress = transferProgress(43))
            holder.selectMode(DownloadMode.Audio)
            holder.selectQuality(2)
            holder.changeDestination()

            advanceTimeBy(FAKE_PROGRESS_INTERVAL * 6)
            runCurrent()

            assertEquals(
                DownloadUiState.Downloading(DownloadFixtures.normal, transferProgress(43)),
                holder.state,
            )
            assertEquals(DownloadMode.Video, holder.selectedMode)
            assertEquals(0, holder.selectedQualityIndex)
            assertEquals(DownloadFixtures.normal.destination, holder.destination)
        }

    @Test
    fun `stale progress callback cannot overwrite replacement downloading state`() =
        runTest {
            val runtime = CapturingDownloadRuntime()
            val holder = testHolder(runtime)
            val forced = DownloadUiState.Downloading(DownloadFixtures.normal, transferProgress(87))
            try {
                holder.showReady()
                holder.startAuthorizedDownload()
                runCurrent()
                val submitStaleProgress = runtime.progressCallbacks.single()

                holder.showDownloading(progress = transferProgress(87))
                submitStaleProgress(transferProgress(18))
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

            holder.observeLinkEdit("https://youtube.com/watch?v=${testVideoId("new")}")
            assertEquals(DownloadUiState.Empty, holder.state)
            assertEquals(DownloadMode.Video, holder.selectedMode)
            assertEquals(0, holder.selectedQualityIndex)
            assertNull(holder.destination)
            assertNull(holder.readyFeedback)

            holder.beginResolution("https://youtube.com/watch?v=${testVideoId("new")}")
            advanceTimeBy(FAKE_RESOLUTION_DELAY)
            runCurrent()
            assertEquals(DownloadFixtures.normal.destination, holder.destination)
            assertEquals("Best · 2160p60 · ~18.4 Mbps", holder.selectedQualityLabel)

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
            assertEquals("Best · 2160p60 · ~18.4 Mbps", holder.selectedQualityLabel)
            assertEquals(DownloadFixtures.normal.destination, holder.destination)
        }

    private class ControlledPreviewRuntime(
        private val tools: List<DownloadTool> = emptyList(),
        private val failPreview: Boolean = false,
        private val failureReason: DownloadFailureReason = DownloadFailureReason.Unknown,
    ) : DownloadRuntime by PreviewDownloadRuntime() {
        private val previewGate = CompletableDeferred<Unit>()

        var installCount = 0
            private set

        var toolStatusCount = 0
            private set

        fun completePreview() {
            previewGate.complete(Unit)
        }

        override suspend fun preview(source: YouTubeUrl): DownloadItem {
            previewGate.await()
            if (failPreview) throw DownloadRuntimeException(reason = failureReason)
            return DownloadFixtures.normal.copy(source = source)
        }

        override suspend fun toolStatus(): ToolStatus {
            toolStatusCount += 1
            return if (installCount == 0) ToolStatus(missing = tools) else ToolStatus()
        }

        override suspend fun installMissingTools(onProgress: (DownloadTool, String) -> Unit) {
            installCount += 1
        }
    }

    private class ControlledRepairRuntime(
        initialStatus: ToolStatus = ToolStatus(),
        var failRepair: Boolean = false,
        private val failDownloadWithManagedTool: Boolean = false,
        private val failResolveWithManagedTool: Boolean = false,
        private val statusAfterRepair: ToolStatus = ToolStatus(),
    ) : DownloadRuntime by PreviewDownloadRuntime() {
        private val repairGate = CompletableDeferred<Unit>()
        private var status = initialStatus

        var repairCount = 0
            private set

        var downloadCount = 0
            private set

        fun completeRepair() {
            repairGate.complete(Unit)
        }

        override suspend fun toolStatus(): ToolStatus = status

        override suspend fun repairManagedTools(
            tools: List<DownloadTool>,
            onProgress: (DownloadTool, String) -> Unit,
        ) {
            repairCount += 1
            repairGate.await()
            if (failRepair) throw DownloadRuntimeException(reason = DownloadFailureReason.Tool)
            status = statusAfterRepair
        }

        override suspend fun resolve(
            source: YouTubeUrl,
            browserCookies: BrowserCookieSource?,
        ): DownloadItem {
            if (failResolveWithManagedTool) {
                throw DownloadRuntimeException(
                    reason = DownloadFailureReason.Tool,
                    repairableTools = listOf(DownloadTool.YtDlp),
                )
            }
            return PreviewDownloadRuntime().resolve(source, browserCookies)
        }

        override suspend fun download(
            request: DownloadRequest,
            onProgress: suspend (DownloadProgress) -> Unit,
        ): Path {
            downloadCount += 1
            if (failDownloadWithManagedTool) {
                throw DownloadRuntimeException(
                    reason = DownloadFailureReason.Tool,
                    repairableTools = listOf(DownloadTool.YtDlp),
                )
            }
            return PreviewDownloadRuntime().download(request, onProgress)
        }
    }

    private class CapturingDownloadRuntime : DownloadRuntime by PreviewDownloadRuntime() {
        val progressCallbacks = mutableListOf<suspend (DownloadProgress) -> Unit>()

        override suspend fun download(
            request: DownloadRequest,
            onProgress: suspend (DownloadProgress) -> Unit,
        ): Path {
            progressCallbacks += onProgress
            return CompletableDeferred<Path>().await()
        }
    }

    private class BrowserCookieRuntime : DownloadRuntime by PreviewDownloadRuntime() {
        val resolvedWith = mutableListOf<BrowserCookieSource?>()
        val downloadRequests = mutableListOf<DownloadRequest>()
        private var interruptedAuthenticatedResolution = false

        override suspend fun resolve(
            source: YouTubeUrl,
            browserCookies: BrowserCookieSource?,
        ): DownloadItem {
            resolvedWith += browserCookies
            if (browserCookies == null) {
                throw DownloadRuntimeException(reason = DownloadFailureReason.Authentication)
            }
            if (!interruptedAuthenticatedResolution) {
                interruptedAuthenticatedResolution = true
                throw DownloadRuntimeException(reason = DownloadFailureReason.Network)
            }
            return DownloadFixtures.normal.copy(source = source)
        }

        override suspend fun download(
            request: DownloadRequest,
            onProgress: suspend (DownloadProgress) -> Unit,
        ): Path {
            downloadRequests += request
            return DownloadFixtures.completedFile(request.item)
        }
    }
}
