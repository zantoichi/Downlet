package downlet

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
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
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds

class ProductSmokeTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Suppress("LongMethod")
    @Test
    fun `happy product flow reaches completed`() {
        val startedAt = System.nanoTime()
        val machineScheduler = TestCoroutineScheduler()
        val machineDispatcher = UnconfinedTestDispatcher(machineScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(machineDispatcher + SupervisorJob()),
                machineDispatcher = machineDispatcher,
            )
        try {
            runComposeUiTest {
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        ProductSurface(stateHolder)
                    }
                }

                val linkField = onNodeWithContentDescription("YouTube link field")
                linkField.assertExists()
                onNodeWithContentDescription(
                    "Status: Paste or type a YouTube link. Downlet checks it automatically.",
                ).assertExists()

                mainClock.autoAdvance = false
                linkField.performTextInput("https://youtu.be/smoke")
                mainClock.advanceTimeBy(MANUAL_LINK_DEBOUNCE_MILLIS)
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithContentDescription("Status: Checking this YouTube link…").assertExists()

                machineScheduler.advanceTimeBy(FAKE_RESOLUTION_MILLIS)
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Video").assertExists()
                onNodeWithContentDescription("Quality: Best available — 2160p").assertExists()
                onNodeWithText("Download").assertIsEnabled()
                onNodeWithContentDescription(
                    mediaContentDescription(DownloadFixtures.normal, thumbnailAvailable = true),
                ).assertExists()

                onNodeWithText("Download").performClick()
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Cancel").assertExists()
                linkField.assertIsNotEnabled()

                machineScheduler.advanceTimeBy(FAKE_PROGRESS_INTERVAL_MILLIS * fakeProgressSteps.size)
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Saved to Downloads").assertExists()
                onNodeWithText("Open Folder").assertIsEnabled()
                onNodeWithText("Download Another").performClick()
                machineScheduler.runCurrent()
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
        val machineScheduler = TestCoroutineScheduler()
        val machineDispatcher = UnconfinedTestDispatcher(machineScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(machineDispatcher + SupervisorJob()),
                machineDispatcher = machineDispatcher,
            )
        try {
            stateHolder.onEvent(DownloadEvent.ShowReady(DownloadFixtures.failure))
            runComposeUiTest {
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        ProductSurface(stateHolder)
                    }
                }

                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Download").performClick()
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Cancel").assertExists()

                machineScheduler.advanceTimeBy(FAKE_PROGRESS_INTERVAL_MILLIS * 3)
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                onNodeWithText("Couldn't download this media.").assertExists()
                onNodeWithText("Retry").performClick()
                machineScheduler.runCurrent()
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
        val machineScheduler = TestCoroutineScheduler()
        val machineDispatcher = UnconfinedTestDispatcher(machineScheduler)
        val stateHolder =
            DownloadStateHolder(
                CoroutineScope(machineDispatcher + SupervisorJob()),
                machineDispatcher = machineDispatcher,
            )
        try {
            stateHolder.onEvent(DownloadEvent.ShowReady(DownloadFixtures.normal))
            machineScheduler.runCurrent()

            runComposeUiTest {
                setContent {
                    IntUiTheme(
                        theme = JewelTheme.lightThemeDefinition(),
                        styling = ComponentStyling.default().decoratedWindow(),
                    ) {
                        ProductSurface(stateHolder)
                    }
                }

                val linkField = onNodeWithContentDescription("YouTube link field")
                assertEquals(WindowPresentationTier.Expanded, stateHolder.state.windowPresentationTier)
                linkField.performTextClearance()
                machineScheduler.runCurrent()
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `rapid height retarget cancels stale target`() {
        val targetHeight = mutableIntStateOf(WindowPresentationTier.Compact.preferredHeight)
        val renderedHeight = mutableIntStateOf(WindowPresentationTier.Compact.preferredHeight)

        runComposeUiTest {
            setContent {
                val animatedHeight = remember { Animatable(renderedHeight.intValue.toFloat()) }
                LaunchedEffect(targetHeight.intValue) {
                    val target = targetHeight.intValue
                    animatedHeight.snapTo(renderedHeight.intValue.toFloat())
                    animateWindowHeight(
                        animatedHeight = animatedHeight,
                        targetHeight = target,
                        durationMillis = if (target > renderedHeight.intValue) 250 else 167,
                        expanding = target > renderedHeight.intValue,
                        applyHeight = { renderedHeight.intValue = it },
                    )
                }
            }

            mainClock.autoAdvance = false
            runOnIdle { targetHeight.intValue = WindowPresentationTier.Expanded.preferredHeight }
            mainClock.advanceTimeBy(80)
            runOnIdle {
                assertTrue(
                    renderedHeight.intValue in
                        (WindowPresentationTier.Compact.preferredHeight + 1) until
                        WindowPresentationTier.Expanded.preferredHeight,
                )
                targetHeight.intValue = WindowPresentationTier.Compact.preferredHeight
            }
            mainClock.advanceTimeBy(200)
            runOnIdle {
                assertEquals(WindowPresentationTier.Compact.preferredHeight, renderedHeight.intValue)
            }
        }
    }
}
