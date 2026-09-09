package downlet

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
class ProductSmokeTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Suppress("LongMethod")
    @Test
    fun `theme toggle preserves the product state`() {
        val scheduler = TestCoroutineScheduler()
        val holder =
            DownloadStateHolder(
                CoroutineScope(UnconfinedTestDispatcher(scheduler) + SupervisorJob()),
                PreviewDownloadRuntime(),
            )
        try {
            runComposeUiTest {
                val theme = mutableStateOf(DownletTheme.Light)
                setContent {
                    ProductTheme(theme.value) {
                        Column(Modifier.size(760.dp, 480.dp)) {
                            ThemeToggle(theme.value, { theme.value = it })
                            TestProductSurface(holder, animationsEnabled = false)
                        }
                    }
                }
                val states =
                    listOf(
                        DownloadUiState.Empty,
                        DownloadUiState.Ready(DownloadFixtures.normal),
                        DownloadUiState.Downloading(DownloadFixtures.normal, DownloadProgress.Preparing),
                    )
                for (productState in states) {
                    runOnIdle {
                        if (productState == DownloadUiState.Empty) {
                            holder.showDesignState(productState, linkText = "unfinished link")
                        } else {
                            holder.showDesignState(productState)
                        }
                        if (productState is DownloadUiState.Ready) {
                            holder.selectMode(DownloadMode.Audio)
                            holder.selectQuality(1)
                            holder.updateDownloadAuthorization(true)
                        }
                    }
                    scheduler.runCurrent()
                    waitForIdle()
                    val link = holder.linkFieldState.text.toString()
                    val selection = holder.selectedMode to holder.selectedQualityLabel
                    val authorized = holder.downloadAuthorizationAccepted
                    onNodeWithContentDescription("Use dark theme").assertIsOff().performClick()
                    onNodeWithContentDescription("Use light theme")
                        .assertIsOn()
                        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Dark theme"))
                        .performSemanticsAction(SemanticsActions.RequestFocus)
                        .assertIsFocused()
                        .performKeyInput {
                            keyDown(Key.Spacebar)
                            keyUp(Key.Spacebar)
                        }
                    onNodeWithContentDescription("Use dark theme")
                        .assertIsOff()
                        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Light theme"))
                    assertEquals(DownletTheme.Light, theme.value)
                    assertEquals(productState, holder.state)
                    assertEquals(link, holder.linkFieldState.text.toString())
                    assertEquals(selection, holder.selectedMode to holder.selectedQualityLabel)
                    assertEquals(authorized, holder.downloadAuthorizationAccepted)
                }
            }
        } finally {
            holder.close()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `theme tooltip stays outside the button and repeated mouse clicks work`() =
        runComposeUiTest {
            val theme = mutableStateOf(DownletTheme.Light)
            setContent {
                ProductTheme(theme.value) {
                    Column(Modifier.size(760.dp, 480.dp)) {
                        ThemeToggle(theme.value, { theme.value = it })
                    }
                }
            }
            val button = onNodeWithContentDescription("Use dark theme")
            button.performMouseInput { enter(Offset(16f, 2f)) }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val buttonBounds = button.fetchSemanticsNode().boundsInRoot
            val tooltipBounds = onNodeWithText("Use dark theme").fetchSemanticsNode().boundsInRoot
            assertTrue(tooltipBounds.top >= buttonBounds.bottom, "Tooltip must stay below its button")
            repeat(4) {
                val action = if (theme.value == DownletTheme.Light) "Use dark theme" else "Use light theme"
                val before = theme.value
                onNodeWithContentDescription(action).performMouseInput {
                    press()
                    release()
                }
                runOnIdle { assertTrue(theme.value != before) }
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
            }
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `theme icon morph reverses and honors disabled motion`() =
        runComposeUiTest {
            val theme = mutableStateOf(DownletTheme.Light)
            val animationsEnabled = mutableStateOf(true)
            setContent {
                ProductTheme(DownletTheme.Light) {
                    ThemeToggle(theme.value, { theme.value = it }, animationsEnabled = animationsEnabled.value)
                }
            }

            fun pixels(action: String): IntArray {
                val bitmap = onNodeWithContentDescription(action).captureToImage()
                return IntArray(bitmap.width * bitmap.height).also { bitmap.readPixels(it) }
            }
            onNodeWithContentDescription("Use dark theme").performMouseInput { enter(center) }
            val sun = pixels("Use dark theme")
            mainClock.autoAdvance = false
            onNodeWithContentDescription("Use dark theme").performClick()
            mainClock.advanceTimeBy(64)
            val growingMoon = pixels("Use light theme")
            mainClock.advanceTimeBy(200)
            val moon = pixels("Use light theme")
            assertTrue(!growingMoon.contentEquals(moon))
            onNodeWithContentDescription("Use light theme").performClick()
            mainClock.advanceTimeBy(64)
            val growingSun = pixels("Use dark theme")
            assertTrue(!growingSun.contentEquals(sun))
            onNodeWithContentDescription("Use dark theme").performClick()
            mainClock.advanceTimeBy(200)
            assertTrue(moon.contentEquals(pixels("Use light theme")))
            runOnIdle { animationsEnabled.value = false }
            onNodeWithContentDescription("Use light theme").performClick()
            mainClock.advanceTimeByFrame()
            assertTrue(sun.contentEquals(pixels("Use dark theme")))
            onNodeWithContentDescription("Use dark theme").performClick()
            mainClock.advanceTimeByFrame()
            assertTrue(moon.contentEquals(pixels("Use light theme")))
        }

    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Suppress("LongMethod")
    @Test
    fun `tool setup requires explicit consent`() {
        val stateScheduler = TestCoroutineScheduler()
        val stateDispatcher = UnconfinedTestDispatcher(stateScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(stateDispatcher + SupervisorJob()),
                PreviewDownloadRuntime(),
            )
        try {
            stateHolder.showDesignState(
                DownloadUiState.Previewing(DownloadFixtures.normal),
            )
            stateScheduler.runCurrent()

            runComposeUiTest {
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        TestProductSurface(stateHolder)
                    }
                }

                assertEquals(WindowPresentationTier.Compact, stateHolder.state.windowPresentationTier)
                onNodeWithContentDescription("Status: Finding this video…").assertExists()

                stateHolder.showDesignState(
                    DownloadUiState.Setup(
                        DownloadFixtures.normal,
                        listOf(DownloadTool.YtDlp, DownloadTool.Ffmpeg),
                    ),
                )
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Prepare this download").assertExists()
                onNodeWithText("Download and continue").assertIsNotEnabled()
                onNodeWithText("Read full terms").performClick()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Full terms").assertExists()
                ProductCopy.legalSections.forEachIndexed { index, section ->
                    onNodeWithText(section.title).assertIsDisplayed()
                    if (index < ProductCopy.legalSections.lastIndex) onNodeWithText("Next").performClick()
                }
                onNodeWithText("Back to setup").performClick()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Prepare this download").assertExists()
                onNodeWithText("Download and continue").assertIsNotEnabled()
                onNodeWithText(ProductCopy.TOOL_SETUP_CONSENT_TEXT).performClick()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Download and continue").assertIsEnabled()
                onNodeWithText("Download and continue").performClick()
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithContentDescription("Status: Checking available formats…").assertExists()

                stateHolder.showDesignState(
                    DownloadUiState.Setup(
                        DownloadFixtures.normal,
                        listOf(DownloadTool.YtDlp),
                        ToolSetupPhase.Failed,
                        ToolSetupIntent.Repair,
                    ),
                )
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Preparing tools").assertExists()
                onNodeWithText(ProductCopy.TOOL_SETUP_CONSENT_TEXT).assertDoesNotExist()
                onNodeWithText("Try again").assertIsEnabled()
            }
        } finally {
            stateHolder.close()
        }
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Suppress("LongMethod")
    @Test
    fun `happy product flow reaches completed`() {
        val startedAt = System.nanoTime()
        val stateScheduler = TestCoroutineScheduler()
        val stateDispatcher = UnconfinedTestDispatcher(stateScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(stateDispatcher + SupervisorJob()),
                PreviewDownloadRuntime(),
            )
        try {
            runComposeUiTest {
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        TestProductSurface(stateHolder)
                    }
                }

                val linkField = onNodeWithContentDescription("YouTube link field")
                linkField.assertExists()
                onNodeWithContentDescription("YouTube link label").assertExists()
                onNodeWithContentDescription(
                    "Status: Paste or type a YouTube link. Downlet checks it automatically.",
                ).assertExists()

                mainClock.autoAdvance = false
                linkField.performTextInput("https://youtu.be/smoke000000")
                mainClock.advanceTimeBy(MANUAL_LINK_DEBOUNCE.inWholeMilliseconds)
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithContentDescription("Status: Checking available formats…").assertExists()

                stateScheduler.advanceTimeBy(FAKE_RESOLUTION_DELAY.inWholeMilliseconds)
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithContentDescription("Download as label").assertExists()
                onNodeWithContentDescription("Quality label").assertExists()
                onNodeWithContentDescription("Save to label").assertExists()
                onNodeWithContentDescription("Permission label").assertExists()
                onNodeWithText("Video").assertExists()
                onNodeWithContentDescription("Quality: Best · 2160p60 · ~18.4 Mbps").assertExists()
                onNodeWithContentDescription(
                    "Quality details: AV1/MP4 + Opus/WebM · ~1.65 GB",
                ).assertExists()
                onNodeWithText("Download").assertIsDisplayed().assertIsNotEnabled()
                onNodeWithContentDescription(
                    mediaContentDescription(DownloadFixtures.normal, thumbnailAvailable = true),
                ).assertExists()

                onNodeWithText("Audio").performClick()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Format & quality").assertExists()
                onNodeWithContentDescription("Format & quality label").assertExists()
                onNodeWithContentDescription(
                    "Format & quality: Original · Opus/WebM · ~130 kbps",
                ).assertExists()
                onNodeWithContentDescription(
                    "Quality details: No conversion. Fastest option; keeps the source audio unchanged.",
                ).assertExists()

                onNodeWithText(ProductCopy.DOWNLOAD_AUTHORIZATION_TEXT).performClick()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Download").assertIsEnabled()
                val selectedQuality = stateHolder.selectedQualityLabel
                onNodeWithText("Audio quality explained").performClick()
                mainClock.advanceTimeByFrame()
                ProductCopy.audioQualityAnswers.keys.forEachIndexed { index, question ->
                    onNodeWithText(question).assertIsDisplayed()
                    if (index < ProductCopy.audioQualityAnswers.size - 1) onNodeWithText("Next").performClick()
                    mainClock.advanceTimeByFrame()
                }
                onNodeWithText("Back to download").performClick()
                mainClock.advanceTimeByFrame()
                assertEquals(selectedQuality, stateHolder.selectedQualityLabel)
                onNodeWithText("Download").assertIsEnabled()
                onNodeWithText("Download").performClick()
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Cancel").assertExists()
                linkField.assertIsNotEnabled()

                stateScheduler.advanceTimeBy((FAKE_PROGRESS_INTERVAL * fakeProgressSteps.size).inWholeMilliseconds)
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithText("87%").assertExists()

                stateScheduler.advanceTimeBy(FAKE_PROGRESS_INTERVAL.inWholeMilliseconds)
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithContentDescription("Finalizing. Merging video and audio.").assertExists()
                onNodeWithText("Merging video and audio…").assertExists()
                onNodeWithText("Cancel").assertExists()

                stateScheduler.advanceTimeBy(FAKE_PROGRESS_INTERVAL.inWholeMilliseconds)
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Saved to ${DownloadFixtures.completedFile()}").assertExists()
                onNodeWithText("Show in folder").assertIsEnabled()
                onNodeWithText("Download another").performClick()
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Compact, stateHolder.state.windowPresentationTier)
                onNodeWithContentDescription(
                    "Status: Paste or type a YouTube link. Downlet checks it automatically.",
                ).assertExists()
            }
        } finally {
            stateHolder.close()
            val elapsed = (System.nanoTime() - startedAt).nanoseconds
            println("smokeTest wall time: $elapsed")
            if (elapsed.inWholeSeconds >= 10) {
                println("smokeTest exceeded the informational 10-second warm target")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `recoverable product flow retries from error`() {
        val startedAt = System.nanoTime()
        val stateScheduler = TestCoroutineScheduler()
        val stateDispatcher = UnconfinedTestDispatcher(stateScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(stateDispatcher + SupervisorJob()),
                PreviewDownloadRuntime(),
            )
        try {
            stateHolder.showDesignState(DownloadUiState.Ready(DownloadFixtures.failure))
            runComposeUiTest {
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        TestProductSurface(stateHolder)
                    }
                }

                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText(ProductCopy.DOWNLOAD_AUTHORIZATION_TEXT).performClick()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Download").performClick()
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Cancel").assertExists()

                stateScheduler.advanceTimeBy((FAKE_PROGRESS_INTERVAL * 3).inWholeMilliseconds)
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Couldn’t download this media.").assertExists()
                onNodeWithText("Retry").performClick()
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithContentDescription("Preparing download.").assertExists()
                onNodeWithText("Preparing download…").assertExists()
            }
        } finally {
            stateHolder.close()
            val elapsed = (System.nanoTime() - startedAt).nanoseconds
            println("smokeTest recoverable wall time: $elapsed")
            if (elapsed.inWholeSeconds >= 10) {
                println("smokeTest recoverable path exceeded the informational 10-second warm target")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `clearing a resolved link returns to compact empty state and restores focus`() {
        val stateScheduler = TestCoroutineScheduler()
        val stateDispatcher = UnconfinedTestDispatcher(stateScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(stateDispatcher + SupervisorJob()),
                PreviewDownloadRuntime(),
            )
        try {
            stateHolder.showDesignState(DownloadUiState.Ready(DownloadFixtures.normal))
            stateScheduler.runCurrent()

            runComposeUiTest {
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        TestProductSurface(stateHolder)
                    }
                }

                val linkField = onNodeWithContentDescription("YouTube link field")
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                linkField.performTextClearance()
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()

                assertEquals(WindowPresentationTier.Compact, stateHolder.state.windowPresentationTier)
                onNodeWithContentDescription(
                    "Status: Paste or type a YouTube link. Downlet checks it automatically.",
                ).assertExists()
                linkField.assertIsFocused()
            }
        } finally {
            stateHolder.close()
        }
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `zero motion replaces state content immediately and restores focus`() {
        val stateScheduler = TestCoroutineScheduler()
        val stateDispatcher = UnconfinedTestDispatcher(stateScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(stateDispatcher + SupervisorJob()),
                PreviewDownloadRuntime(),
            )
        try {
            runComposeUiTest {
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        TestProductSurface(stateHolder, animationsEnabled = false)
                    }
                }

                val emptyStatus =
                    onNodeWithContentDescription(
                        "Status: Paste or type a YouTube link. Downlet checks it automatically.",
                    )
                val linkField = onNodeWithContentDescription("YouTube link field")
                emptyStatus.assertExists()

                mainClock.autoAdvance = false
                runOnIdle { stateHolder.showDesignState(DownloadUiState.Ready(DownloadFixtures.normal)) }
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                emptyStatus.assertDoesNotExist()
                onNodeWithText("Video").assertExists()

                runOnIdle { stateHolder.showDesignState(DownloadUiState.Empty) }
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Video").assertDoesNotExist()
                emptyStatus.assertExists()
                linkField.assertIsFocused()
            }
        } finally {
            stateHolder.close()
        }
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `authentication error offers browser sessions instead of retry`() {
        val stateScheduler = TestCoroutineScheduler()
        val stateDispatcher = UnconfinedTestDispatcher(stateScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(stateDispatcher + SupervisorJob()),
                PreviewDownloadRuntime(),
            )
        try {
            stateHolder.showDesignState(
                DownloadUiState.Error(
                    DownloadFixtures.normal,
                    DownloadErrorKind.Download,
                    DownloadFailureReason.Authentication,
                ),
            )
            runComposeUiTest {
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        TestProductSurface(stateHolder)
                    }
                }

                onNodeWithText("Sign in required.").assertIsDisplayed()
                onNodeWithText("Use Firefox").assertIsDisplayed()
                onNodeWithText("Use Chrome").assertIsDisplayed()
                onNodeWithText("Use Edge").assertIsDisplayed()
                assertTrue(onAllNodesWithText("Retry").fetchSemanticsNodes().isEmpty())
                runOnIdle {
                    stateHolder.showDesignState(
                        DownloadUiState.Error(
                            DownloadFixtures.normal,
                            DownloadErrorKind.Resolution,
                            DownloadFailureReason.BotChallenge,
                        ),
                    )
                }
                onNodeWithText("YouTube wants to verify this session.").assertIsDisplayed()
                onNodeWithText("Retry").assertIsDisplayed()
                onNodeWithText("Use Firefox").assertIsDisplayed().performClick()
                stateScheduler.runCurrent()
                assertTrue(stateHolder.state !is DownloadUiState.Error)
            }
        } finally {
            stateHolder.close()
        }
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `audio conversion shows elapsed time until processing changes`() {
        val stateScheduler = TestCoroutineScheduler()
        val stateDispatcher = UnconfinedTestDispatcher(stateScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(stateDispatcher + SupervisorJob()),
                PreviewDownloadRuntime(),
            )
        try {
            stateHolder.showDesignState(
                DownloadUiState.Downloading(
                    DownloadFixtures.normal,
                    DownloadProgress.Processing(DownloadProcessingStage.Converting),
                ),
            )
            runComposeUiTest {
                mainClock.autoAdvance = false
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        TestProductSurface(stateHolder)
                    }
                }

                mainClock.advanceTimeByFrame()
                onNodeWithText("Converting audio…").assertExists()
                onNodeWithText("0 sec elapsed").assertExists()

                mainClock.advanceTimeBy(1.1.seconds.inWholeMilliseconds)
                onNodeWithText("1 sec elapsed").assertExists()

                runOnIdle {
                    stateHolder.showDesignState(
                        DownloadUiState.Downloading(
                            DownloadFixtures.normal,
                            DownloadProgress.Processing(DownloadProcessingStage.Merging),
                        ),
                    )
                }
                mainClock.advanceTimeByFrame()
                onNodeWithText("1 sec elapsed").assertDoesNotExist()
                onNodeWithText("Merging video and audio…").assertExists()
            }
        } finally {
            stateHolder.close()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `rapid height retarget cancels stale target`() {
        val compactHeight =
            WindowPresentationTier.Compact.preferredSize.height.value
                .roundToInt()
        val expandedHeight =
            WindowPresentationTier.Expanded.preferredSize.height.value
                .roundToInt()
        val targetHeight = mutableIntStateOf(compactHeight)
        val renderedHeight = mutableIntStateOf(compactHeight)

        runComposeUiTest {
            setContent {
                val animatedHeight = remember { Animatable(renderedHeight.intValue.toFloat()) }
                LaunchedEffect(targetHeight.intValue) {
                    val target = targetHeight.intValue
                    animatedHeight.snapTo(renderedHeight.intValue.toFloat())
                    animateWindowHeight(
                        animatedHeight = animatedHeight,
                        targetHeight = target,
                        duration = if (target > renderedHeight.intValue) 250.milliseconds else 167.milliseconds,
                        expanding = target > renderedHeight.intValue,
                        applyHeight = { renderedHeight.intValue = it },
                    )
                }
            }

            mainClock.autoAdvance = false
            runOnIdle { targetHeight.intValue = expandedHeight }
            mainClock.advanceTimeBy(80.milliseconds.inWholeMilliseconds)
            runOnIdle {
                assertTrue(
                    renderedHeight.intValue in
                        (compactHeight + 1) until expandedHeight,
                )
                targetHeight.intValue = compactHeight
            }
            mainClock.advanceTimeBy(200.milliseconds.inWholeMilliseconds)
            runOnIdle {
                assertEquals(compactHeight, renderedHeight.intValue)
            }
        }
    }
}

@Composable
private fun TestProductSurface(
    stateHolder: DownloadStateHolder,
    animationsEnabled: Boolean = true,
) {
    val typography = downletTypography()
    CompositionLocalProvider(
        LocalDownletTypography provides typography,
        LocalTextStyle provides typography.body,
    ) {
        ProductSurface(stateHolder, animationsEnabled)
    }
}
