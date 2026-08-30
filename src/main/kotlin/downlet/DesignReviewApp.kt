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
import androidx.compose.runtime.rememberCoroutineScope
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

private const val CONTROLLER_WINDOW_TITLE = "Design Review Controller"

internal object DesignReviewApp {
    @JvmStatic
    fun main(args: Array<String>) =
        application {
            val scope = rememberCoroutineScope()
            val stateHolder = remember(scope) { DownloadStateHolder(scope) }
            var productTheme by remember { mutableStateOf(DownletTheme.Light) }
            var motionDurationScale by remember { mutableStateOf(1f) }

            ProductWindow(
                stateHolder = stateHolder,
                theme = productTheme,
                onCloseRequest = ::exitApplication,
                initialPosition = WindowPosition(8.dp, 48.dp),
                motionDurationScale = motionDurationScale,
            )
            ControllerWindow(
                state = stateHolder.state,
                theme = productTheme,
                onEvent = stateHolder::onEvent,
                onReset = {
                    stateHolder.onEvent(DownloadEvent.Reset)
                    productTheme = DownletTheme.Light
                    motionDurationScale = 1f
                },
                onThemeChange = { productTheme = it },
                motionDurationScale = motionDurationScale,
                onMotionDurationScaleChange = { motionDurationScale = it },
                onCloseRequest = ::exitApplication,
            )
        }
}

@Suppress("LongMethod")
@Composable
private fun ControllerWindow(
    state: DownloadUiState,
    theme: DownletTheme,
    onEvent: (DownloadEvent) -> Unit,
    onReset: () -> Unit,
    onThemeChange: (DownletTheme) -> Unit,
    motionDurationScale: Float,
    onMotionDurationScaleChange: (Float) -> Unit,
    onCloseRequest: () -> Unit,
) {
    val windowState =
        rememberWindowState(
            position = WindowPosition(800.dp, 48.dp),
            width = 560.dp,
            height = 520.dp,
        )

    IntUiTheme(
        theme = JewelTheme.lightThemeDefinition(),
        styling = ComponentStyling.default().decoratedWindow(),
    ) {
        DecoratedWindow(
            onCloseRequest = onCloseRequest,
            state = windowState,
            title = CONTROLLER_WINDOW_TITLE,
        ) {
            TitleBar {
                Text(CONTROLLER_WINDOW_TITLE)
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Product state")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowEmpty) }) {
                            Text("Empty")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowPreviewing()) }) {
                            Text("Previewing")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowSetup()) }) {
                            Text("Setup")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowResolving()) }) {
                            Text("Resolving")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowReady()) }) {
                            Text("Ready")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowDownloading()) }) {
                            Text("Downloading")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowCompleted()) }) {
                            Text("Completed")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowError()) }) {
                            Text("Error")
                        }
                        OutlinedButton(onClick = onReset) {
                            Text("Reset")
                        }
                    }
                    Text("Ready fixtures")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowReady(DownloadFixtures.normal)) }) {
                            Text("Normal")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowReady(DownloadFixtures.longTitle)) }) {
                            Text("Long title")
                        }
                        OutlinedButton(
                            onClick = { onEvent(DownloadEvent.ShowReady(DownloadFixtures.missingThumbnail)) },
                        ) {
                            Text("No preview")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onEvent(DownloadEvent.ShowReady(DownloadFixtures.longDestination)) },
                        ) {
                            Text("Long path")
                        }
                        OutlinedButton(
                            onClick = { onEvent(DownloadEvent.ShowReady(DownloadFixtures.disabledAction)) },
                        ) {
                            Text("Disabled")
                        }
                        OutlinedButton(
                            onClick = { onEvent(DownloadEvent.ShowReady(DownloadFixtures.failure)) },
                        ) {
                            Text("Failure")
                        }
                        OutlinedButton(onClick = { onEvent(DownloadEvent.ShowInvalidInput) }) {
                            Text("Invalid input")
                        }
                    }
                    Text("Current state: ${state.label} ${state.fixtureId}")

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
                    Text("Window sizing")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onMotionDurationScaleChange(1f) }) {
                            Text("Normal motion")
                        }
                        OutlinedButton(onClick = { onMotionDurationScaleChange(0f) }) {
                            Text("Zero duration")
                        }
                    }
                    Text("Current motion: ${if (motionDurationScale == 0f) "Zero duration" else "Normal"}")
                }
            }
        }
    }
}

private val DownloadUiState.fixtureId: String
    get() =
        when (this) {
            is DownloadUiState.Previewing -> fixture.id
            is DownloadUiState.Setup -> fixture.id
            is DownloadUiState.Resolving -> fixture.id
            is DownloadUiState.Ready -> fixture.id
            is DownloadUiState.Downloading -> fixture.id
            is DownloadUiState.Completed -> fixture.id
            is DownloadUiState.Error -> fixture.id
            DownloadUiState.Empty -> ""
        }
