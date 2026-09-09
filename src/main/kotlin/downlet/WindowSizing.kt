package downlet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Toolkit
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal enum class WindowPresentationTier(
    val preferredSize: DpSize,
) {
    Compact(DpSize(width = 760.dp, height = 188.dp)),
    Expanded(DpSize(width = 760.dp, height = 480.dp)),
}

internal val DownloadUiState.windowPresentationTier: WindowPresentationTier
    get() =
        when (this) {
            DownloadUiState.Empty,
            is DownloadUiState.Previewing,
            -> WindowPresentationTier.Compact

            is DownloadUiState.Setup,
            is DownloadUiState.Resolving,
            is DownloadUiState.Ready,
            is DownloadUiState.Downloading,
            is DownloadUiState.Completed,
            is DownloadUiState.Error,
            -> WindowPresentationTier.Expanded
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
    targetSize: IntSize,
): WindowBounds {
    val width = targetSize.width.coerceIn(1, workArea.width)
    val height = targetSize.height.coerceIn(1, workArea.height)
    return WindowBounds(
        x = current.x.coerceIn(workArea.x, workArea.x + workArea.width - width),
        y = current.y.coerceIn(workArea.y, workArea.y + workArea.height - height),
        width = width,
        height = height,
    )
}

internal fun DpSize.toDevicePixels(density: Float): IntSize =
    IntSize(
        width = (width.value * density).roundToInt(),
        height = (height.value * density).roundToInt(),
    )

private val EXPAND_DURATION: Duration = 250.milliseconds
private val COLLAPSE_DURATION: Duration = 167.milliseconds
private const val SPI_GETCLIENTAREAANIMATION = 0x1042

private interface MotionUser32 : User32 {
    @Suppress("FunctionName", "ktlint:standard:function-naming")
    fun SystemParametersInfo(
        uiAction: Int,
        uiParam: Int,
        pvParam: IntByReference,
        fWinIni: Int,
    ): Boolean
}

private val motionUser32: MotionUser32 by lazy {
    Native.load("user32", MotionUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)
}

internal fun windowsAnimationsEnabled(
    animationsEnabled: () -> Boolean = ::windowsClientAreaAnimationsEnabled,
): Boolean = runCatching(animationsEnabled).getOrDefault(false)

private fun windowsClientAreaAnimationsEnabled(): Boolean {
    val enabled = IntByReference()
    return motionUser32.SystemParametersInfo(
        SPI_GETCLIENTAREAANIMATION,
        0,
        enabled,
        0,
    ) && enabled.value != 0
}

@Composable
internal fun ManageProductWindowSizing(
    window: Frame,
    windowState: WindowState,
    preferredSize: DpSize,
    animationsEnabled: Boolean,
) {
    val density = LocalDensity.current
    val densityScale = density.density
    val animatedHeight = remember(window) { Animatable(window.height.toFloat()) }

    LaunchedEffect(preferredSize, animationsEnabled, densityScale) {
        // Let Compose apply the initial position before preserving the window bounds.
        while (!window.isShowing) withFrameNanos { }
        val current = window.bounds.toWindowBounds()
        val target =
            fitWindowBounds(
                current = current,
                workArea = activeWorkArea(window),
                targetSize = preferredSize.toDevicePixels(densityScale),
            )
        animatedHeight.snapTo(current.height.toFloat())

        suspend fun applyHeight(height: Int) {
            windowState.position =
                WindowPosition(
                    x = with(density) { target.x.toDp() },
                    y = with(density) { target.y.toDp() },
                )
            windowState.size =
                DpSize(
                    width = with(density) { target.width.toDp() },
                    height = with(density) { height.toDp() },
                )
        }

        if (!animationsEnabled || current.height == target.height) {
            applyHeight(target.height)
        } else {
            val duration = if (target.height > current.height) EXPAND_DURATION else COLLAPSE_DURATION
            animateWindowHeight(
                animatedHeight = animatedHeight,
                targetHeight = target.height,
                duration = duration,
                expanding = target.height > current.height,
                applyHeight = ::applyHeight,
            )
            if (target.height < current.height) {
                withFrameNanos { }
                applyHeight(target.height)
            }
        }
    }
}

internal suspend fun animateWindowHeight(
    animatedHeight: Animatable<Float, AnimationVector1D>,
    targetHeight: Int,
    duration: Duration,
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
                    durationMillis = duration.inWholeMilliseconds.toInt(),
                    easing = if (expanding) LinearOutSlowInEasing else FastOutLinearInEasing,
                ),
        )
        applyHeight(targetHeight)
    } finally {
        updater.cancel()
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
