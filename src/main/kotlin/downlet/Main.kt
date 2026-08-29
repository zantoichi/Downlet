package downlet

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import downlet.generated.resources.Res
import downlet.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle
import java.awt.Dimension

private const val PRODUCT_WINDOW_TITLE = "Downlet"
private const val PRODUCT_WINDOW_WIDTH = 720
private const val PRODUCT_WINDOW_HEIGHT = 420
private const val PRODUCT_WINDOW_MINIMUM_WIDTH = 620
private const val PRODUCT_WINDOW_MINIMUM_HEIGHT = 350

fun main() =
    application {
        val scope = rememberCoroutineScope()
        val stateHolder = remember(scope) { DownloadStateHolder(scope) }
        val detectedDarkTheme = isSystemInDarkTheme()
        val startupTheme = remember { if (detectedDarkTheme) DownletTheme.Dark else DownletTheme.Light }

        ProductWindow(
            stateHolder = stateHolder,
            theme = startupTheme,
            onCloseRequest = ::exitApplication,
        )
    }

@Composable
internal fun ProductWindow(
    stateHolder: DownloadStateHolder,
    theme: DownletTheme,
    onCloseRequest: () -> Unit,
    initialPosition: WindowPosition = WindowPosition.PlatformDefault,
) {
    val windowState =
        rememberWindowState(
            position = initialPosition,
            width = PRODUCT_WINDOW_WIDTH.dp,
            height = PRODUCT_WINDOW_HEIGHT.dp,
        )

    IntUiTheme(
        theme =
            when (theme) {
                DownletTheme.Light -> JewelTheme.lightThemeDefinition()
                DownletTheme.Dark -> JewelTheme.darkThemeDefinition()
            },
        styling =
            when (theme) {
                DownletTheme.Light -> {
                    ComponentStyling.default().decoratedWindow(
                        DecoratedWindowStyle.light(),
                        TitleBarStyle.lightWithLightHeader(),
                    )
                }

                DownletTheme.Dark -> {
                    ComponentStyling.default().decoratedWindow(
                        DecoratedWindowStyle.dark(),
                        TitleBarStyle.dark(),
                    )
                }
            },
    ) {
        val appIcon = painterResource(Res.drawable.app_icon)
        DecoratedWindow(
            onCloseRequest = onCloseRequest,
            state = windowState,
            title = PRODUCT_WINDOW_TITLE,
            icon = appIcon,
        ) {
            DisposableEffect(window) {
                window.minimumSize = Dimension(PRODUCT_WINDOW_MINIMUM_WIDTH, PRODUCT_WINDOW_MINIMUM_HEIGHT)
                onDispose {}
            }

            TitleBar {
                Text(PRODUCT_WINDOW_TITLE)
            }

            ProductSurface(stateHolder = stateHolder)
        }
    }
}
