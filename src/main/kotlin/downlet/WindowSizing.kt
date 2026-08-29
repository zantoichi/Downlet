package downlet

internal enum class WindowPresentationTier(
    val preferredHeight: Int,
    val minimumHeight: Int,
) {
    Compact(preferredHeight = 168, minimumHeight = 156),
    Expanded(preferredHeight = 420, minimumHeight = 400),
}

internal enum class WindowSizingOwnership {
    AutoManaged,
    UserManaged,
}

internal enum class WindowPlacementMode {
    Floating,
    PlatformManaged,
}

internal sealed interface WindowSizingRequest {
    data object Suspended : WindowSizingRequest

    data object Preserve : WindowSizingRequest

    data class ResizeTo(
        val height: Int,
    ) : WindowSizingRequest
}

internal val DownloadUiState.windowPresentationTier: WindowPresentationTier
    get() =
        when (this) {
            DownloadUiState.Empty,
            is DownloadUiState.Resolving,
            -> WindowPresentationTier.Compact

            is DownloadUiState.Ready,
            is DownloadUiState.Downloading,
            is DownloadUiState.Completed,
            is DownloadUiState.Error,
            -> WindowPresentationTier.Expanded
        }

internal fun windowSizingRequest(
    tier: WindowPresentationTier,
    ownership: WindowSizingOwnership,
    placement: WindowPlacementMode,
    currentHeight: Int,
): WindowSizingRequest =
    when {
        placement == WindowPlacementMode.PlatformManaged -> WindowSizingRequest.Suspended
        ownership == WindowSizingOwnership.AutoManaged -> WindowSizingRequest.ResizeTo(tier.preferredHeight)
        currentHeight < tier.minimumHeight -> WindowSizingRequest.ResizeTo(tier.minimumHeight)
        else -> WindowSizingRequest.Preserve
    }
