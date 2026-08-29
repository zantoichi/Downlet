package downlet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class WindowPresentationTier(
    val preferredWidth: Int,
    val preferredHeight: Int,
    val minimumWidth: Int,
    val minimumHeight: Int,
) {
    Compact(preferredWidth = 720, preferredHeight = 168, minimumWidth = 620, minimumHeight = 156),
    Expanded(preferredWidth = 720, preferredHeight = 420, minimumWidth = 620, minimumHeight = 400),
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

internal data class WindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal fun fitWindowBounds(
    current: WindowBounds,
    workArea: WindowBounds,
    targetHeight: Int,
): WindowBounds {
    val width = current.width.coerceIn(1, workArea.width)
    val height = targetHeight.coerceIn(1, workArea.height)
    return WindowBounds(
        x = current.x.coerceIn(workArea.x, workArea.x + workArea.width - width),
        y = current.y.coerceIn(workArea.y, workArea.y + workArea.height - height),
        width = width,
        height = height,
    )
}

internal fun boundsApproximatelyEqual(
    first: WindowBounds,
    second: WindowBounds,
    tolerance: Int = 2,
): Boolean =
    abs(first.x - second.x) <= tolerance &&
        abs(first.y - second.y) <= tolerance &&
        abs(first.width - second.width) <= tolerance &&
        abs(first.height - second.height) <= tolerance

internal fun managedWindowWidth(
    currentWidth: Int,
    tier: WindowPresentationTier,
): Int = if (currentWidth < tier.minimumWidth) tier.preferredWidth else currentWidth

internal fun fitManagedWindowBounds(
    current: WindowBounds,
    workArea: WindowBounds,
    targetHeight: Int,
    tier: WindowPresentationTier,
): WindowBounds = fitWindowBounds(current.copy(width = managedWindowWidth(current.width, tier)), workArea, targetHeight)

internal fun isLikelySnapped(
    bounds: WindowBounds,
    workArea: WindowBounds,
    tolerance: Int = 2,
): Boolean {
    fun near(
        value: Int,
        target: Int,
    ) = abs(value - target) <= tolerance

    val touchesHorizontalEdge = near(bounds.x, workArea.x) || near(bounds.x + bounds.width, workArea.x + workArea.width)
    val touchesVerticalEdge = near(bounds.y, workArea.y) || near(bounds.y + bounds.height, workArea.y + workArea.height)
    val standardWidth =
        listOf(workArea.width, workArea.width / 2, workArea.width / 3, workArea.width * 2 / 3)
            .any { near(bounds.width, it) }
    val standardHeight = listOf(workArea.height, workArea.height / 2).any { near(bounds.height, it) }
    return touchesHorizontalEdge && touchesVerticalEdge && standardWidth && standardHeight
}

private const val MAX_RECENT_APP_BOUNDS = 64
private const val EXPAND_DURATION_MILLIS = 250
private const val COLLAPSE_DURATION_MILLIS = 167

internal class WindowSizingCoordinator(
    private var awaitingInitialBounds: Boolean = false,
) {
    var ownership by mutableStateOf(WindowSizingOwnership.AutoManaged)
        private set

    var placement by mutableStateOf(WindowPlacementMode.Floating)
        private set

    var revision by mutableIntStateOf(0)
        private set

    private val recentAppBounds = ArrayDeque<WindowBounds>()

    fun acceptCurrentBounds(bounds: WindowBounds) {
        recordAppBounds(bounds)
    }

    fun recordAppBounds(bounds: WindowBounds) {
        recentAppBounds.addLast(bounds)
        while (recentAppBounds.size > MAX_RECENT_APP_BOUNDS) recentAppBounds.removeFirst()
    }

    fun observeBounds(
        bounds: WindowBounds,
        placement: WindowPlacementMode,
    ) {
        updatePlacement(placement)
        if (placement == WindowPlacementMode.PlatformManaged) return
        if (awaitingInitialBounds) {
            recordAppBounds(bounds)
        } else if (recentAppBounds.none { boundsApproximatelyEqual(it, bounds) }) {
            recentAppBounds.clear()
            ownership = WindowSizingOwnership.UserManaged
            revision += 1
        }
    }

    fun completeInitialSizing() {
        awaitingInitialBounds = false
    }

    fun updatePlacement(value: WindowPlacementMode) {
        if (placement == value) return
        placement = value
        revision += 1
    }

    fun restoreAutoManaged() {
        recentAppBounds.clear()
        ownership = WindowSizingOwnership.AutoManaged
        revision += 1
    }
}

@Composable
internal fun ManageProductWindowSizing(
    window: Frame,
    windowState: WindowState,
    tier: WindowPresentationTier,
    motionDurationScale: Float,
    restoreAutoManagedSignal: Int,
) {
    val density = LocalDensity.current
    val coordinator =
        remember(window) {
            WindowSizingCoordinator(
                awaitingInitialBounds = window.width < tier.minimumWidth || window.height < tier.minimumHeight,
            )
        }
    val animatedHeight = remember(window) { Animatable(window.height.toFloat()) }

    DisposableEffect(window, windowState.placement) {
        coordinator.acceptCurrentBounds(window.bounds.toWindowBounds())
        val listener =
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    val bounds = window.bounds.toWindowBounds()
                    coordinator.observeBounds(bounds, placementMode(window, windowState.placement, bounds))
                }
            }
        window.addComponentListener(listener)
        onDispose { window.removeComponentListener(listener) }
    }

    LaunchedEffect(windowState.placement) {
        coordinator.updatePlacement(placementMode(window, windowState.placement, window.bounds.toWindowBounds()))
    }
    LaunchedEffect(restoreAutoManagedSignal) {
        if (restoreAutoManagedSignal > 0) coordinator.restoreAutoManaged()
    }

    LaunchedEffect(tier, coordinator.ownership, coordinator.placement, coordinator.revision, motionDurationScale) {
        val current = window.bounds.toWindowBounds()
        val request =
            windowSizingRequest(
                tier = tier,
                ownership = coordinator.ownership,
                placement = coordinator.placement,
                currentHeight = current.height,
            )
        if (request == WindowSizingRequest.Suspended) return@LaunchedEffect

        if (tier == WindowPresentationTier.Compact) {
            window.minimumSize = Dimension(tier.minimumWidth, tier.minimumHeight)
        }
        if (request == WindowSizingRequest.Preserve) {
            window.minimumSize = Dimension(tier.minimumWidth, tier.minimumHeight)
            return@LaunchedEffect
        }

        val targetHeight = (request as WindowSizingRequest.ResizeTo).height
        val target = fitManagedWindowBounds(current, activeWorkArea(window), targetHeight, tier)
        animatedHeight.snapTo(current.height.toFloat())

        suspend fun applyHeight(height: Int) {
            val bounds = target.copy(height = height)
            coordinator.recordAppBounds(bounds)
            windowState.position =
                WindowPosition(
                    x = with(density) { bounds.x.toDp() },
                    y = with(density) { bounds.y.toDp() },
                )
            windowState.size =
                DpSize(
                    width = with(density) { bounds.width.toDp() },
                    height = with(density) { bounds.height.toDp() },
                )
        }

        if (motionDurationScale <= 0f || current.height == target.height) {
            applyHeight(target.height)
        } else {
            val durationMillis =
                if (target.height > current.height) EXPAND_DURATION_MILLIS else COLLAPSE_DURATION_MILLIS
            animateWindowHeight(
                animatedHeight = animatedHeight,
                targetHeight = target.height,
                durationMillis = durationMillis,
                expanding = target.height > current.height,
                applyHeight = ::applyHeight,
            )
        }
        window.minimumSize = Dimension(tier.minimumWidth, tier.minimumHeight)
        coordinator.completeInitialSizing()
    }
}

internal suspend fun animateWindowHeight(
    animatedHeight: Animatable<Float, AnimationVector1D>,
    targetHeight: Int,
    durationMillis: Int,
    expanding: Boolean,
    applyHeight: suspend (Int) -> Unit,
) = coroutineScope {
    val updater =
        launch(start = CoroutineStart.UNDISPATCHED) {
            snapshotFlow { animatedHeight.value.roundToInt() }
                .distinctUntilChanged()
                .collect(applyHeight)
        }
    try {
        animatedHeight.animateTo(
            targetValue = targetHeight.toFloat(),
            animationSpec =
                tween(
                    durationMillis = durationMillis,
                    easing = if (expanding) LinearOutSlowInEasing else FastOutLinearInEasing,
                ),
        )
        applyHeight(targetHeight)
    } finally {
        updater.cancel()
    }
}

private fun placementMode(
    window: Frame,
    placement: WindowPlacement,
    bounds: WindowBounds,
): WindowPlacementMode {
    val isComposeManaged = placement != WindowPlacement.Floating
    val isAwtMaximized = window.extendedState and Frame.MAXIMIZED_BOTH != 0
    val isFullScreen = window.graphicsConfiguration.device.fullScreenWindow === window
    val isSnapped = isLikelySnapped(bounds, activeWorkArea(window))
    return if (listOf(isComposeManaged, isAwtMaximized, isFullScreen, isSnapped).any { it }) {
        WindowPlacementMode.PlatformManaged
    } else {
        WindowPlacementMode.Floating
    }
}

private fun activeWorkArea(window: Frame): WindowBounds {
    val configuration = window.graphicsConfiguration
    val screen = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return WindowBounds(
        x = screen.x + insets.left,
        y = screen.y + insets.top,
        width = screen.width - insets.left - insets.right,
        height = screen.height - insets.top - insets.bottom,
    )
}

private fun Rectangle.toWindowBounds() = WindowBounds(x, y, width, height)
