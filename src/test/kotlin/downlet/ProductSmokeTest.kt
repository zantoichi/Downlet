package downlet

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
import org.jetbrains.jewel.ui.ComponentStyling
import kotlin.test.Test
import kotlin.time.Duration.Companion.nanoseconds

class ProductSmokeTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `product reaches ready through accessible link field`() {
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
                        styling = ComponentStyling.default(),
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
            }
        } finally {
            val elapsed = (System.nanoTime() - startedAt).nanoseconds
            println("smokeTest wall time: $elapsed")
            if (elapsed.inWholeSeconds >= 10) {
                println("smokeTest exceeded the informational 10-second warm target")
            }
        }
    }
}
