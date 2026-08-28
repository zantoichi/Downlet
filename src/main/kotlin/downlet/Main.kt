package downlet

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
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
    readClipboardText: () -> String? = ::readWindowsClipboardText,
) {
    val windowState =
        rememberWindowState(
            position = initialPosition,
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

            ProductSurface(stateHolder = stateHolder, readClipboardText = readClipboardText)
        }
    }
}

@Composable
private fun ProductSurface(
    stateHolder: DownloadStateHolder,
    readClipboardText: () -> String?,
) {
    val state = stateHolder.state
    val linkFieldState = stateHolder.linkFieldState
    var pasteIntent by remember { mutableStateOf(false) }

    LaunchedEffect(linkFieldState) {
        snapshotFlow { linkFieldState.text.toString() }.collectLatest { text ->
            if (!stateHolder.observeLinkEdit(text)) return@collectLatest

            val submission = linkSubmissionFor(text, pasteIntent)
            pasteIntent = false
            when (submission) {
                LinkSubmission.None -> Unit
                LinkSubmission.ResolveImmediately -> stateHolder.beginResolution(text)
                is LinkSubmission.ResolveAfter -> {
                    delay(submission.delayMillis.milliseconds)
                    stateHolder.beginResolution(text)
                }
            }
        }
    }
    LaunchedEffect(pasteIntent) {
        if (pasteIntent) {
            delay(PasteIntentLifetimeMillis.milliseconds)
            pasteIntent = false
        }
    }
    LaunchedEffect(state) {
        if (state is DownloadUiState.Resolving && state.completesAutomatically) {
            delay(FakeResolutionMillis.milliseconds)
            stateHolder.completeResolution(state.fixture)
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "YouTube link",
                modifier =
                    Modifier
                        .width(96.dp)
                        .padding(top = 5.dp)
                        .semantics { contentDescription = "YouTube link label" },
            )
            Column(modifier = Modifier.weight(1f)) {
                TextField(
                    state = linkFieldState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.isCtrlPressed &&
                                    event.key == Key.V
                                ) {
                                    pasteIntent = true
                                }
                                false
                            }.semantics { contentDescription = "YouTube link field" },
                    outline = if (stateHolder.validationMessage == null) Outline.None else Outline.Error,
                    placeholder = { Text("Paste a YouTube link…") },
                )
                Box(modifier = Modifier.fillMaxWidth().height(20.dp).padding(top = 4.dp)) {
                    stateHolder.validationMessage?.let { message ->
                        Text(
                            text = message,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Validation: $message"
                                    liveRegion = LiveRegionMode.Polite
                                },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { readClipboardText()?.let(stateHolder::pasteLink) },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Paste")
            }
        }

        Divider(orientation = Orientation.Horizontal)

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ProductBody(state)
        }
    }
}

@Composable
private fun ProductBody(state: DownloadUiState) {
    when (state) {
        DownloadUiState.Empty ->
            Text(
                text = "Paste a YouTube link to choose video or audio.",
                modifier = Modifier.semantics {
                    contentDescription = "Status: Paste a YouTube link to choose video or audio."
                },
            )

        is DownloadUiState.Resolving ->
            Row(
                modifier =
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Status: Checking this YouTube link…"
                        liveRegion = LiveRegionMode.Polite
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text("Checking this YouTube link…")
            }

        else -> Text(state.label)
    }
}

private fun readWindowsClipboardText(): String? =
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            (clipboard.getData(DataFlavor.stringFlavor) as? String)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
