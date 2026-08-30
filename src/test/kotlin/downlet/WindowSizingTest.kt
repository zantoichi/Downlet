package downlet

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizingTest {
    @Test
    fun `all product states map to one of two tiers`() {
        val compactStates =
            listOf(
                DownloadUiState.Empty,
                DownloadUiState.Previewing(DownloadFixtures.normal),
            )
        val expandedStates =
            listOf(
                DownloadUiState.Setup(DownloadFixtures.normal, listOf("yt-dlp")),
                DownloadUiState.Resolving(DownloadFixtures.normal),
                DownloadUiState.Ready(DownloadFixtures.normal),
                DownloadUiState.Downloading(DownloadFixtures.normal, progressPercent = 43),
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
                targetWidth = 720,
                targetHeight = 420,
            ),
        )
        assertEquals(
            WindowBounds(x = 280, y = 380, width = 720, height = 420),
            fitWindowBounds(
                current = WindowBounds(x = 500, y = 700, width = 400, height = 168),
                workArea = workArea,
                targetWidth = 720,
                targetHeight = 420,
            ),
        )
        assertEquals(
            WindowBounds(x = 0, y = 0, width = 1000, height = 800),
            fitWindowBounds(
                current = WindowBounds(x = 0, y = 0, width = 400, height = 168),
                workArea = workArea,
                targetWidth = 1200,
                targetHeight = 900,
            ),
        )
    }

    @Test
    fun `logical tier dimensions scale to native device pixels`() {
        assertEquals(720, logicalPixelsToDevicePixels(720, density = 1f))
        assertEquals(900, logicalPixelsToDevicePixels(720, density = 1.25f))
        assertEquals(1080, logicalPixelsToDevicePixels(720, density = 1.5f))
    }
}
