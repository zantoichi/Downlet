package downlet

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.time.Duration.Companion.nanoseconds

class ProductSmokeTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
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
                onNodeWithText("Video").assertExists()
                onNodeWithContentDescription("Quality: Best available — 2160p").assertExists()
                onNodeWithText("Download").assertIsEnabled()
                onNodeWithContentDescription(
                    mediaContentDescription(DownloadFixtures.normal, thumbnailAvailable = true),
                ).assertExists()

                onNodeWithText("Download").performClick()
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Cancel").assertExists()
                linkField.assertIsNotEnabled()

                machineScheduler.advanceTimeBy(FAKE_PROGRESS_INTERVAL_MILLIS * fakeProgressSteps.size)
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Saved to Downloads").assertExists()
                onNodeWithText("Open Folder").assertIsEnabled()
                onNodeWithText("Download Another").assertExists()
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

                onNodeWithText("Download").performClick()
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Cancel").assertExists()

                machineScheduler.advanceTimeBy(FAKE_PROGRESS_INTERVAL_MILLIS * 3)
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
                onNodeWithText("Couldn't download this media.").assertExists()
                onNodeWithText("Retry").performClick()
                machineScheduler.runCurrent()
                mainClock.advanceTimeByFrame()
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
}
