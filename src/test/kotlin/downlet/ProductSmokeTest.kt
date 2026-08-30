package downlet

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
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

class ProductSmokeTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
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
                onNodeWithText("Before setup").assertExists()
                onNodeWithText("Third-party tools").assertExists()
                onNodeWithText("Licenses").assertExists()
                onNodeWithText("Your responsibility").assertExists()
                onNodeWithText("Limits").assertExists()
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
                linkField.performTextInput("https://youtu.be/smoke")
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
                onNodeWithContentDescription("Quality: Best available — 2160p").assertExists()
                onNodeWithText("Download").assertIsDisplayed().assertIsNotEnabled()
                onNodeWithContentDescription(
                    mediaContentDescription(DownloadFixtures.normal, thumbnailAvailable = true),
                ).assertExists()

                onNodeWithText("Audio").performClick()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Format & quality").assertExists()
                onNodeWithContentDescription("Format & quality label").assertExists()
                onNodeWithContentDescription(
                    "Format & quality: Original audio — WebM · 130 kbps",
                ).assertExists()

                onNodeWithText(ProductCopy.DOWNLOAD_AUTHORIZATION_TEXT).performClick()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Download").assertIsEnabled()
                onNodeWithText("Download").performClick()
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Cancel").assertExists()
                linkField.assertIsNotEnabled()

                stateScheduler.advanceTimeBy(
                    (FAKE_PROGRESS_INTERVAL * (fakeProgressSteps.size + 1)).inWholeMilliseconds,
                )
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Saved to Downloads").assertExists()
                onNodeWithText("Open folder").assertIsEnabled()
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
                onNodeWithText("Couldn't download this media.").assertExists()
                onNodeWithText("Retry").performClick()
                stateScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithContentDescription("Downloading: 0%. Starting download…").assertExists()
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
