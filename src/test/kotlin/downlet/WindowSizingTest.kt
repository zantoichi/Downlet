package downlet

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizingTest {
    @Test
    fun `all product states map to one of two tiers`() {
        val compactStates =
            listOf(
                DownloadUiState.Empty,
                DownloadUiState.Resolving(DownloadFixtures.normal),
            )
        val expandedStates =
            listOf(
                DownloadUiState.Ready(DownloadFixtures.normal),
                DownloadUiState.Downloading(DownloadFixtures.normal, progressPercent = 43),
                DownloadUiState.Completed(DownloadFixtures.normal),
                DownloadUiState.Error(DownloadFixtures.failure),
            )

        compactStates.forEach { assertEquals(WindowPresentationTier.Compact, it.windowPresentationTier) }
        expandedStates.forEach { assertEquals(WindowPresentationTier.Expanded, it.windowPresentationTier) }
    }

    @Test
    fun `automatic sizing uses preferred height and platform placement suspends requests`() {
        assertEquals(
            WindowSizingRequest.ResizeTo(WindowPresentationTier.Expanded.preferredHeight),
            windowSizingRequest(
                tier = WindowPresentationTier.Expanded,
                ownership = WindowSizingOwnership.AutoManaged,
                placement = WindowPlacementMode.Floating,
                currentHeight = WindowPresentationTier.Compact.preferredHeight,
            ),
        )
        assertEquals(
            WindowSizingRequest.Suspended,
            windowSizingRequest(
                tier = WindowPresentationTier.Compact,
                ownership = WindowSizingOwnership.AutoManaged,
                placement = WindowPlacementMode.PlatformManaged,
                currentHeight = WindowPresentationTier.Expanded.preferredHeight,
            ),
        )
    }

    @Test
    fun `user sizing suppresses shrink but permits minimum required growth`() {
        assertEquals(
            WindowSizingRequest.Preserve,
            windowSizingRequest(
                tier = WindowPresentationTier.Compact,
                ownership = WindowSizingOwnership.UserManaged,
                placement = WindowPlacementMode.Floating,
                currentHeight = 560,
            ),
        )
        assertEquals(
            WindowSizingRequest.ResizeTo(WindowPresentationTier.Expanded.minimumHeight),
            windowSizingRequest(
                tier = WindowPresentationTier.Expanded,
                ownership = WindowSizingOwnership.UserManaged,
                placement = WindowPlacementMode.Floating,
                currentHeight = 300,
            ),
        )
        assertEquals(
            WindowSizingRequest.Preserve,
            windowSizingRequest(
                tier = WindowPresentationTier.Expanded,
                ownership = WindowSizingOwnership.UserManaged,
                placement = WindowPlacementMode.Floating,
                currentHeight = WindowPresentationTier.Expanded.minimumHeight,
            ),
        )
    }

    @Test
    fun `work area fit preserves origin when possible and shifts only overflow`() {
        val workArea = WindowBounds(x = 0, y = 0, width = 1000, height = 800)

        assertEquals(
            WindowBounds(x = 100, y = 100, width = 720, height = 420),
            fitWindowBounds(
                current = WindowBounds(x = 100, y = 100, width = 720, height = 168),
                workArea = workArea,
                targetHeight = 420,
            ),
        )
        assertEquals(
            WindowBounds(x = 280, y = 380, width = 720, height = 420),
            fitWindowBounds(
                current = WindowBounds(x = 500, y = 700, width = 720, height = 168),
                workArea = workArea,
                targetHeight = 420,
            ),
        )
        assertEquals(
            WindowBounds(x = 0, y = 0, width = 1000, height = 800),
            fitWindowBounds(
                current = WindowBounds(x = 0, y = 0, width = 1200, height = 168),
                workArea = workArea,
                targetHeight = 900,
            ),
        )
    }

    @Test
    fun `snap detection recognizes standard work area regions`() {
        val workArea = WindowBounds(x = 0, y = 0, width = 1200, height = 900)

        assertEquals(true, isLikelySnapped(WindowBounds(0, 0, 600, 900), workArea))
        assertEquals(true, isLikelySnapped(WindowBounds(600, 450, 600, 450), workArea))
        assertEquals(false, isLikelySnapped(WindowBounds(40, 40, 720, 420), workArea))
    }

    @Test
    fun `app bounds tolerate ordinary native rounding`() {
        val requested = WindowBounds(x = 100, y = 80, width = 720, height = 420)

        assertEquals(true, boundsApproximatelyEqual(requested, WindowBounds(102, 78, 719, 421)))
        assertEquals(false, boundsApproximatelyEqual(requested, WindowBounds(103, 80, 720, 420)))
    }

    @Test
    fun `startup sizing replaces the native placeholder width`() {
        assertEquals(720, managedWindowWidth(136, WindowPresentationTier.Compact))
        assertEquals(680, managedWindowWidth(680, WindowPresentationTier.Compact))
    }

    @Test
    fun `first observed resize establishes the startup baseline`() {
        val coordinator = WindowSizingCoordinator(awaitingInitialBounds = true)
        coordinator.acceptCurrentBounds(WindowBounds(0, 0, 136, 39))

        coordinator.observeBounds(WindowBounds(0, 0, 720, 168), WindowPlacementMode.Floating)

        assertEquals(WindowSizingOwnership.AutoManaged, coordinator.ownership)

        coordinator.observeBounds(WindowBounds(0, 0, 720, 250), WindowPlacementMode.Floating)

        assertEquals(WindowSizingOwnership.UserManaged, coordinator.ownership)
    }
}
