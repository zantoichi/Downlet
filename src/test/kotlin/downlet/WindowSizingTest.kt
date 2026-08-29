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
}
