package downlet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import downlet.generated.resources.Res
import downlet.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalOutlinedButtonStyle
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle
import org.jetbrains.jewel.ui.theme.outlinedButtonStyle
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

private const val PRODUCT_WINDOW_TITLE = "Downlet"
private val PRODUCT_BUTTON_MIN_HEIGHT = 36.dp

fun main() =
    application {
        val scope = rememberCoroutineScope()
        val stateHolder = remember(scope) { DownloadStateHolder(scope, runtime = YtDlpDownloadRuntime()) }
        val systemTheme = if (isSystemInDarkTheme()) DownletTheme.Dark else DownletTheme.Light
        var theme by remember { mutableStateOf(systemTheme) }
        val animationsEnabled = remember { windowsAnimationsEnabled() }

        ProductWindow(
            stateHolder = stateHolder,
            theme = theme,
            onThemeChange = { theme = it },
            onCloseRequest = ::exitApplication,
            animationsEnabled = animationsEnabled,
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod")
internal fun ProductWindow(
    stateHolder: DownloadStateHolder,
    theme: DownletTheme,
    onThemeChange: (DownletTheme) -> Unit,
    onCloseRequest: () -> Unit,
    initialPosition: WindowPosition = WindowPosition.PlatformDefault,
    animationsEnabled: Boolean = true,
) {
    val initialTier = WindowPresentationTier.Compact
    val windowState =
        rememberWindowState(
            position = initialPosition,
            width = initialTier.preferredSize.width,
            height = initialTier.preferredSize.height,
        )

    ProductTheme(theme) {
        val typography = downletTypography()
        val defaultButtonStyle = JewelTheme.defaultButtonStyle
        val outlinedButtonStyle = JewelTheme.outlinedButtonStyle
        CompositionLocalProvider(
            LocalDownletTypography provides typography,
            LocalTextStyle provides typography.body,
            LocalDefaultButtonStyle provides
                remember(defaultButtonStyle) {
                    defaultButtonStyle.withMinHeight(PRODUCT_BUTTON_MIN_HEIGHT)
                },
            LocalOutlinedButtonStyle provides
                remember(outlinedButtonStyle) {
                    outlinedButtonStyle.withMinHeight(PRODUCT_BUTTON_MIN_HEIGHT)
                },
        ) {
            val appIcon = painterResource(Res.drawable.app_icon)
            DecoratedWindow(
                onCloseRequest = onCloseRequest,
                state = windowState,
                title = PRODUCT_WINDOW_TITLE,
                icon = appIcon,
                resizable = false,
            ) {
                ManageProductWindowSizing(
                    window = window,
                    windowState = windowState,
                    tier = stateHolder.state.windowPresentationTier,
                    animationsEnabled = animationsEnabled,
                )
                ManageWindowsTaskbarProgress(window, stateHolder.state)

                val themeToggleStyle = JewelTheme.iconButtonStyle
                TitleBar(modifier = Modifier.focusProperties { canFocus = true }) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(appIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(PRODUCT_WINDOW_TITLE, style = typography.titleBar)
                    }
                    ThemeToggle(
                        theme = theme,
                        onThemeChange = onThemeChange,
                        modifier = Modifier.align(Alignment.End),
                        animationsEnabled = animationsEnabled,
                        style = themeToggleStyle,
                    )
                }

                ProductSurface(
                    stateHolder = stateHolder,
                    animationsEnabled = animationsEnabled,
                )
            }
        }
    }
}

private fun ButtonStyle.withMinHeight(height: Dp): ButtonStyle =
    ButtonStyle(
        colors = colors,
        metrics =
            ButtonMetrics(
                cornerSize = metrics.cornerSize,
                padding = metrics.padding,
                minSize = DpSize(metrics.minSize.width, height),
                borderWidth = metrics.borderWidth,
                focusOutlineExpand = metrics.focusOutlineExpand,
            ),
        focusOutlineAlignment = focusOutlineAlignment,
    )

@Composable
internal fun ProductTheme(
    theme: DownletTheme,
    content: @Composable () -> Unit,
) {
    val lightTheme = remember { JewelTheme.lightThemeDefinition() }
    val darkTheme = remember { JewelTheme.darkThemeDefinition() }
    val lightTitleBar = TitleBarStyle.lightWithLightHeader()
    val darkTitleBar = TitleBarStyle.dark()
    val lightStyling =
        remember(lightTitleBar) {
            ComponentStyling.decoratedWindow(DecoratedWindowStyle.light(), lightTitleBar)
        }
    val darkStyling =
        remember(darkTitleBar) {
            ComponentStyling.decoratedWindow(DecoratedWindowStyle.dark(), darkTitleBar)
        }
    IntUiTheme(
        theme = if (theme == DownletTheme.Dark) darkTheme else lightTheme,
        styling = if (theme == DownletTheme.Dark) darkStyling else lightStyling,
        content = content,
    )
}
