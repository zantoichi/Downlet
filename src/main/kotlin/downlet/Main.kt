package downlet

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
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

private const val ProductWindowTitle = "Downlet"
private const val ProductWindowWidth = 720
private const val ProductWindowHeight = 420
private const val ProductWindowMinimumWidth = 620
private const val ProductWindowMinimumHeight = 350

fun main() = application {
    val stateHolder = remember { DownloadStateHolder() }
    val detectedDarkTheme = isSystemInDarkTheme()
    val startupTheme = remember { if (detectedDarkTheme) DownletTheme.Dark else DownletTheme.Light }

    ProductWindow(
        state = stateHolder.state,
        theme = startupTheme,
        onCloseRequest = ::exitApplication,
    )
}

@Composable
internal fun ProductWindow(
    state: DownloadUiState,
    theme: DownletTheme,
    onCloseRequest: () -> Unit,
) {
    val windowState =
        rememberWindowState(
            width = ProductWindowWidth.dp,
            height = ProductWindowHeight.dp,
        )

    IntUiTheme(
        theme =
            when (theme) {
                DownletTheme.Light -> JewelTheme.lightThemeDefinition()
                DownletTheme.Dark -> JewelTheme.darkThemeDefinition()
            },
        styling =
            when (theme) {
                DownletTheme.Light ->
                    ComponentStyling.default().decoratedWindow(
                        DecoratedWindowStyle.light(),
                        TitleBarStyle.lightWithLightHeader(),
                    )

                DownletTheme.Dark ->
                    ComponentStyling.default().decoratedWindow(
                        DecoratedWindowStyle.dark(),
                        TitleBarStyle.dark(),
                    )
            },
    ) {
        DecoratedWindow(
            onCloseRequest = onCloseRequest,
            state = windowState,
            title = ProductWindowTitle,
        ) {
            DisposableEffect(window) {
                window.minimumSize = Dimension(ProductWindowMinimumWidth, ProductWindowMinimumHeight)
                onDispose {}
            }

            TitleBar {
                Text(ProductWindowTitle)
            }

            ProductSurface(state)
        }
    }
}

@Composable
private fun ProductSurface(state: DownloadUiState) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
                .padding(20.dp)
    ) {
        Text(state.label)
    }
}
