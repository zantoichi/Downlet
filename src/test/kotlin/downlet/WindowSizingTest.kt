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
                DownloadUiState.Setup(
                    DownloadFixtures.normal,
                    listOf(DownloadTool.YtDlp),
                    ToolSetupPhase.Installing,
                    ToolSetupIntent.Repair,
                ),
                DownloadUiState.Resolving(DownloadFixtures.normal),
                DownloadUiState.Ready(DownloadFixtures.normal),
                DownloadUiState.Downloading(
                    DownloadFixtures.normal,
                    DownloadProgress.Transferring(downloadedBytes = 43, totalBytes = 100, fraction = 0.43f),
                ),
                DownloadUiState.Completed(DownloadFixtures.normal, DownloadFixtures.completedFile()),
                DownloadUiState.Error(DownloadFixtures.failure),
            )

        compactStates.forEach { assertEquals(WindowPresentationTier.Compact, it.windowPresentationTier) }
        expandedStates.forEach { assertEquals(WindowPresentationTier.Expanded, it.windowPresentationTier) }
    }

    @Test
    fun `window fit uses the fixed tier size and shifts only overflow`() {
        val workArea = WindowBounds(x = 0, y = 0, width = 1000, height = 800)

        assertEquals(
            WindowBounds(x = 100, y = 100, width = 760, height = 480),
            fitWindowBounds(
                current = WindowBounds(x = 100, y = 100, width = 400, height = 188),
                workArea = workArea,
                targetSize = IntSize(760, 480),
            ),
        )
        assertEquals(
            WindowBounds(x = 240, y = 320, width = 760, height = 480),
            fitWindowBounds(
                current = WindowBounds(x = 500, y = 700, width = 400, height = 188),
                workArea = workArea,
                targetSize = IntSize(760, 480),
            ),
        )
        assertEquals(
            WindowBounds(x = 0, y = 0, width = 1000, height = 800),
            fitWindowBounds(
                current = WindowBounds(x = 0, y = 0, width = 400, height = 188),
                workArea = workArea,
                targetSize = IntSize(1200, 900),
            ),
        )
    }

    @Test
    fun `logical tier dimensions scale to native device pixels`() {
        val size = WindowPresentationTier.Expanded.preferredSize

        assertEquals(IntSize(760, 480), size.toDevicePixels(density = 1f))
        assertEquals(IntSize(950, 600), size.toDevicePixels(density = 1.25f))
        assertEquals(IntSize(1140, 720), size.toDevicePixels(density = 1.5f))
    }
}
