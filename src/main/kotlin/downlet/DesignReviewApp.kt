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
import kotlin.time.Duration.Companion.seconds

private const val CONTROLLER_WINDOW_TITLE = "Design Review Controller"
private const val FAILURE_BUTTONS_PER_ROW = 3
private val DESIGN_REVIEW_PROGRESS =
    DownloadProgress.Transferring(
        downloadedBytes = 59_340_000,
        totalBytes = 138_000_000,
        totalIsEstimated = true,
        speedBytesPerSecond = 5_200_000.0,
        eta = 15.seconds,
        fraction = 0.43f,
    )
private val DESIGN_REVIEW_UNKNOWN_PROGRESS =
    DownloadProgress.Transferring(
        downloadedBytes = 72_400_000,
        speedBytesPerSecond = 5_200_000.0,
    )

internal object DesignReviewApp {
    @JvmStatic
    fun main(args: Array<String>) =
        application {
            val scope = rememberCoroutineScope()
            val stateHolder = remember(scope) { DownloadStateHolder(scope, PreviewDownloadRuntime()) }
            var productTheme by remember { mutableStateOf(DownletTheme.Light) }
            var animationsEnabled by remember { mutableStateOf(true) }

            ProductWindow(
                stateHolder = stateHolder,
                theme = productTheme,
                onThemeChange = { productTheme = it },
                onCloseRequest = ::exitApplication,
                initialPosition = WindowPosition(8.dp, 48.dp),
                animationsEnabled = animationsEnabled,
            )
            ControllerWindow(
                state = stateHolder.state,
                theme = productTheme,
                onShowState = stateHolder::showDesignState,
                onReset = {
                    stateHolder.showDesignState(DownloadUiState.Empty)
                    productTheme = DownletTheme.Light
                    animationsEnabled = true
                },
                onThemeChange = { productTheme = it },
                animationsEnabled = animationsEnabled,
                onAnimationsEnabledChange = { animationsEnabled = it },
                onCloseRequest = ::exitApplication,
            )
        }
}

@Suppress("LongMethod")
@Composable
private fun ControllerWindow(
    state: DownloadUiState,
    theme: DownletTheme,
    onShowState: (DownloadUiState, String, String?) -> Unit,
    onReset: () -> Unit,
    onThemeChange: (DownletTheme) -> Unit,
    animationsEnabled: Boolean,
    onAnimationsEnabledChange: (Boolean) -> Unit,
    onCloseRequest: () -> Unit,
) {
    fun showState(
        state: DownloadUiState,
        linkText: String =
            state.itemOrNull
                ?.source
                ?.toString()
                .orEmpty(),
        validationMessage: String? = null,
    ) {
        onShowState(state, linkText, validationMessage)
    }

    val windowState =
        rememberWindowState(
            position = WindowPosition(800.dp, 48.dp),
            width = 560.dp,
            height = 760.dp,
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
                        OutlinedButton(onClick = { showState(DownloadUiState.Empty) }) {
                            Text("Empty")
                        }
                        OutlinedButton(
                            onClick = {
                                showState(
                                    DownloadUiState.Previewing(DownloadFixtures.normal),
                                )
                            },
                        ) {
                            Text("Previewing")
                        }
                        OutlinedButton(
                            onClick = {
                                showState(
                                    DownloadUiState.Setup(
                                        DownloadFixtures.normal,
                                        listOf(DownloadTool.YtDlp, DownloadTool.Ffmpeg),
                                    ),
                                )
                            },
                        ) {
                            Text("Setup")
                        }
                        OutlinedButton(
                            onClick = {
                                showState(
                                    DownloadUiState.Resolving(DownloadFixtures.normal),
                                )
                            },
                        ) {
                            Text("Resolving")
                        }
                        OutlinedButton(onClick = { showState(DownloadUiState.Ready(DownloadFixtures.normal)) }) {
                            Text("Ready")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showState(
                                    DownloadUiState.Downloading(
                                        DownloadFixtures.normal,
                                        DESIGN_REVIEW_PROGRESS,
                                    ),
                                )
                            },
                        ) {
                            Text("Known transfer")
                        }
                        OutlinedButton(
                            onClick = {
                                showState(
                                    DownloadUiState.Downloading(
                                        DownloadFixtures.normal,
                                        DESIGN_REVIEW_UNKNOWN_PROGRESS,
                                    ),
                                )
                            },
                        ) {
                            Text("Unknown transfer")
                        }
                        OutlinedButton(onClick = { showState(DownloadUiState.Completed(DownloadFixtures.normal)) }) {
                            Text("Completed")
                        }
                        OutlinedButton(onClick = onReset) {
                            Text("Reset")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showState(
                                    DownloadUiState.Downloading(
                                        DownloadFixtures.normal,
                                        DownloadProgress.Preparing,
                                    ),
                                )
                            },
                        ) {
                            Text("Preparing")
                        }
                        OutlinedButton(
                            onClick = {
                                showState(
                                    DownloadUiState.Downloading(
                                        DownloadFixtures.normal,
                                        DownloadProgress.Processing(DownloadProcessingStage.Merging),
                                    ),
                                )
                            },
                        ) {
                            Text("Merging")
                        }
                        OutlinedButton(
                            onClick = {
                                showState(
                                    DownloadUiState.Downloading(
                                        DownloadFixtures.normal,
                                        DownloadProgress.Processing(DownloadProcessingStage.Converting),
                                    ),
                                )
                            },
                        ) {
                            Text("Converting")
                        }
                    }
                    Text("Failure guidance")
                    DownloadFailureReason.entries.chunked(FAILURE_BUTTONS_PER_ROW).forEach { reasons ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            reasons.forEach { reason ->
                                OutlinedButton(
                                    onClick = {
                                        showState(
                                            DownloadUiState.Error(
                                                DownloadFixtures.failure,
                                                reason = reason,
                                            ),
                                        )
                                    },
                                ) {
                                    Text(reason.name)
                                }
                            }
                        }
                    }
                    Text("Ready fixtures")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showState(DownloadUiState.Ready(DownloadFixtures.normal)) }) {
                            Text("Normal")
                        }
                        OutlinedButton(onClick = { showState(DownloadUiState.Ready(DownloadFixtures.longTitle)) }) {
                            Text("Long title")
                        }
                        OutlinedButton(
                            onClick = { showState(DownloadUiState.Ready(DownloadFixtures.missingThumbnail)) },
                        ) {
                            Text("No preview")
                        }
                        OutlinedButton(
                            onClick = { showState(DownloadUiState.Ready(DownloadFixtures.remoteThumbnail)) },
                        ) {
                            Text("Remote preview")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showState(DownloadUiState.Ready(DownloadFixtures.longDestination)) },
                        ) {
                            Text("Long path")
                        }
                        OutlinedButton(
                            onClick = { showState(DownloadUiState.Ready(DownloadFixtures.failure)) },
                        ) {
                            Text("Failure")
                        }
                        OutlinedButton(
                            onClick = {
                                showState(
                                    DownloadUiState.Empty,
                                    linkText = "not a YouTube link",
                                    validationMessage = ProductCopy.INVALID_LINK_MESSAGE,
                                )
                            },
                        ) {
                            Text("Invalid input")
                        }
                    }
                    Text("Setup phase")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolSetupPhase.entries.forEach { phase ->
                            OutlinedButton(
                                onClick = {
                                    showState(
                                        DownloadUiState.Setup(
                                            DownloadFixtures.normal,
                                            listOf(DownloadTool.YtDlp, DownloadTool.Ffmpeg),
                                            phase,
                                        ),
                                    )
                                },
                            ) {
                                Text(phase.name)
                            }
                        }
                    }
                    Text(
                        "Current state: ${state.label}" +
                            (state as? DownloadUiState.Setup)?.let { " (${it.phase.name})" }.orEmpty(),
                    )

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
                        OutlinedButton(onClick = { onAnimationsEnabledChange(true) }) {
                            Text("Normal motion")
                        }
                        OutlinedButton(onClick = { onAnimationsEnabledChange(false) }) {
                            Text("Zero duration")
                        }
                    }
                    Text("Current motion: ${if (animationsEnabled) "Normal" else "Zero duration"}")
                }
            }
        }
    }
}
