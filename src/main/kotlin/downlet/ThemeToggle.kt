package downlet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ToggleableIconButton
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle

private const val THEME_MORPH_DURATION_MILLIS = 160

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("MagicNumber") // Geometry is expressed in a 20-unit icon viewport.
internal fun ThemeToggle(
    theme: DownletTheme,
    onThemeChange: (DownletTheme) -> Unit,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    style: IconButtonStyle = JewelTheme.iconButtonStyle,
) {
    val darkMode = theme == DownletTheme.Dark
    val themeAction = if (darkMode) "Use light theme" else "Use dark theme"
    val target = if (darkMode) 1f else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (animationsEnabled) THEME_MORPH_DURATION_MILLIS else 0),
        label = "Sun to moon",
    )
    Tooltip(
        tooltip = { Text(themeAction) },
        modifier = modifier,
        // Leave room for Jewel's 12.dp popup shadow outside the button's hit area.
        tooltipPlacement =
            TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                alignment = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 16.dp),
            ),
    ) {
        ToggleableIconButton(
            value = darkMode,
            onValueChange = { enabled -> onThemeChange(if (enabled) DownletTheme.Dark else DownletTheme.Light) },
            modifier =
                Modifier.size(32.dp).semantics {
                    contentDescription = themeAction
                    stateDescription = if (darkMode) "Dark theme" else "Light theme"
                },
            style = style,
        ) { buttonState ->
            val foreground by style.colors.toggleableForegroundFor(buttonState)
            val iconColor = foreground.takeOrElse { JewelTheme.contentColor }
            Canvas(Modifier.size(20.dp)) {
                val progress = if (animationsEnabled) animatedProgress else target
                val unit = size.minDimension / 20f
                val cutoutOffset = (12f - 9f * progress) * unit
                val cutout =
                    Path().apply {
                        addOval(Rect(center + Offset(cutoutOffset, -cutoutOffset), 6f * unit))
                    }
                clipPath(cutout, ClipOp.Difference) {
                    drawCircle(iconColor, radius = (4f + 3f * progress) * unit)
                }
                repeat(8) { ray ->
                    rotate(ray * 45f) {
                        drawLine(
                            color = iconColor,
                            start = center + Offset(0f, -7f * unit),
                            end = center + Offset(0f, -(9f - 2f * progress) * unit),
                            strokeWidth = 1.5f * unit,
                            cap = StrokeCap.Round,
                            alpha = 1f - progress,
                        )
                    }
                }
            }
        }
    }
}
