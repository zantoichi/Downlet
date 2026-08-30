package downlet

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizingTest {
    @Test
    fun `Windows animation preference controls animations`() {
        assertEquals(true, windowsAnimationsEnabled { true })
        assertEquals(false, windowsAnimationsEnabled { false })
        assertEquals(false, windowsAnimationsEnabled { error("User32 unavailable") })
    }

    @Test
    fun `all product states map to one of two tiers`() {
        val compactStates =
            listOf(
                DownloadUiState.Empty,
                DownloadUiState.Previewing(DownloadFixtures.normal),
            )
        val expandedStates =
            listOf(
                DownloadUiState.Setup(DownloadFixtures.normal, listOf(DownloadTool.YtDlp)),
                DownloadUiState.Resolving(DownloadFixtures.normal),
                DownloadUiState.Ready(DownloadFixtures.normal),
                DownloadUiState.Downloading(DownloadFixtures.normal, DownloadProgress(43)),
                DownloadUiState.Completed(DownloadFixtures.normal),
                DownloadUiState.Error(DownloadFixtures.failure),
            )

        compactStates.forEach { assertEquals(WindowPresentationTier.Compact, it.windowPresentationTier) }
        expandedStates.forEach { assertEquals(WindowPresentationTier.Expanded, it.windowPresentationTier) }
    }

    @Test
    fun `window fit uses the fixed tier size and shifts only overflow`() {
        val workArea = WindowBounds(x = 0, y = 0, width = 1000, height = 800)

        assertEquals(
            WindowBounds(x = 100, y = 100, width = 720, height = 420),
            fitWindowBounds(
                current = WindowBounds(x = 100, y = 100, width = 400, height = 168),
                workArea = workArea,
                targetSize = IntSize(720, 420),
            ),
        )
        assertEquals(
            WindowBounds(x = 280, y = 380, width = 720, height = 420),
            fitWindowBounds(
                current = WindowBounds(x = 500, y = 700, width = 400, height = 168),
                workArea = workArea,
                targetSize = IntSize(720, 420),
            ),
        )
        assertEquals(
            WindowBounds(x = 0, y = 0, width = 1000, height = 800),
            fitWindowBounds(
                current = WindowBounds(x = 0, y = 0, width = 400, height = 168),
                workArea = workArea,
                targetSize = IntSize(1200, 900),
            ),
        )
    }

    @Test
    fun `logical tier dimensions scale to native device pixels`() {
        val size = DpSize(720.dp, 420.dp)

        assertEquals(IntSize(720, 420), size.toDevicePixels(density = 1f))
        assertEquals(IntSize(900, 525), size.toDevicePixels(density = 1.25f))
        assertEquals(IntSize(1080, 630), size.toDevicePixels(density = 1.5f))
    }
}
