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

internal enum class WindowPresentationTier(
    val preferredWidth: Int,
    val preferredHeight: Int,
) {
    Compact(preferredWidth = 720, preferredHeight = 168),
    Expanded(preferredWidth = 720, preferredHeight = 420),
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
    targetWidth: Int,
    targetHeight: Int,
): WindowBounds {
    val width = targetWidth.coerceIn(1, workArea.width)
    val height = targetHeight.coerceIn(1, workArea.height)
    return WindowBounds(
        x = current.x.coerceIn(workArea.x, workArea.x + workArea.width - width),
        y = current.y.coerceIn(workArea.y, workArea.y + workArea.height - height),
        width = width,
        height = height,
    )
}

internal fun logicalPixelsToDevicePixels(
    logicalPixels: Int,
    density: Float,
): Int = (logicalPixels * density).roundToInt()

private const val EXPAND_DURATION_MILLIS = 250
private const val COLLAPSE_DURATION_MILLIS = 167
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

internal fun windowsMotionDurationScale(
    animationsEnabled: () -> Boolean = ::windowsClientAreaAnimationsEnabled,
): Float = if (runCatching(animationsEnabled).getOrDefault(false)) 1f else 0f

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
    tier: WindowPresentationTier,
    motionDurationScale: Float,
) {
    val density = LocalDensity.current
    val densityScale = density.density
    val animatedHeight = remember(window) { Animatable(window.height.toFloat()) }

    LaunchedEffect(tier, motionDurationScale, densityScale) {
        val current = window.bounds.toWindowBounds()
        val target =
            fitWindowBounds(
                current = current,
                workArea = activeWorkArea(window),
                targetWidth = logicalPixelsToDevicePixels(tier.preferredWidth, densityScale),
                targetHeight = logicalPixelsToDevicePixels(tier.preferredHeight, densityScale),
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
