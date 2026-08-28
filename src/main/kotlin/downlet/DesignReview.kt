package downlet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar

private const val ControllerWindowTitle = "Design Review Controller"

internal object DesignReviewApp {
    @JvmStatic
    fun main(args: Array<String>) = application {
        val stateHolder = remember { DownloadStateHolder() }
        var productTheme by remember { mutableStateOf(DownletTheme.Light) }

        ProductWindow(
            stateHolder = stateHolder,
            theme = productTheme,
            onCloseRequest = ::exitApplication,
        )
        ControllerWindow(
            state = stateHolder.state,
            theme = productTheme,
            onEvent = stateHolder::onEvent,
            onThemeChange = { productTheme = it },
            onCloseRequest = ::exitApplication,
        )
    }
}

@Composable
private fun ControllerWindow(
    state: DownloadUiState,
    theme: DownletTheme,
    onEvent: (DownloadEvent) -> Unit,
    onThemeChange: (DownletTheme) -> Unit,
    onCloseRequest: () -> Unit,
) {
    val windowState =
        rememberWindowState(
            position = WindowPosition(800.dp, 48.dp),
            width = 380.dp,
            height = 260.dp,
        )

    IntUiTheme(
        theme = JewelTheme.lightThemeDefinition(),
        styling = ComponentStyling.default().decoratedWindow(),
    ) {
        DecoratedWindow(
            onCloseRequest = onCloseRequest,
            state = windowState,
            title = ControllerWindowTitle,
        ) {
            TitleBar {
                Text(ControllerWindowTitle)
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Product state")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEvent(DownloadEvent.Reset) }) {
                            Text("Empty")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowResolving()) }) {
                            Text("Resolving")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowReady()) }) {
                            Text("Ready")
                        }
                    }
                    Text("Current state: ${state.label}")

                    Text("Product theme")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onThemeChange(DownletTheme.Light) }) {
                            Text("Light")
                        }
                        OutlinedButton(onClick = { onThemeChange(DownletTheme.Dark) }) {
                            Text("Dark")
                        }
                    }
                    Text("Current theme: ${theme.name}")
                }
            }
        }
    }
}
