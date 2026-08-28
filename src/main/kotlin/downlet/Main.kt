package downlet

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.styling.ComboBoxIcons
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.theme.comboBoxStyle
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
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground),
    ) {
        val compact = maxHeight < 330.dp
        val outerPadding = if (compact) 16.dp else 20.dp
        val majorGap = if (compact) 12.dp else 16.dp

        Column(
            modifier = Modifier.fillMaxSize().padding(outerPadding),
            verticalArrangement = Arrangement.spacedBy(majorGap),
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
                ProductBody(stateHolder, compact)
            }
        }
    }
}

@Composable
private fun ProductBody(stateHolder: DownloadStateHolder, compact: Boolean) {
    when (val state = stateHolder.state) {
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

        is DownloadUiState.Ready -> ReadyBody(stateHolder, state.fixture, compact)
        else -> Text(state.label)
    }
}

@Composable
private fun ReadyBody(
    stateHolder: DownloadStateHolder,
    fixture: DownloadFixture,
    compact: Boolean,
) {
    val controlGap = if (compact) 8.dp else 10.dp
    val thumbnailWidth = if (compact) 96.dp else 128.dp
    val comboBoxStyle = JewelTheme.comboBoxStyle
    val qualityComboBoxStyle =
        remember(comboBoxStyle) {
            ComboBoxStyle(
                colors = comboBoxStyle.colors,
                metrics = comboBoxStyle.metrics,
                icons = ComboBoxIcons(PathIconKey("chevron-down.svg", DownloadStateHolder::class.java)),
            )
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(controlGap),
    ) {
        MediaIdentity(fixture, thumbnailWidth)

        FormRow("Download as") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RadioButtonRow(
                    text = "Video",
                    selected = stateHolder.selectedMode == DownloadMode.Video,
                    onClick = { stateHolder.selectMode(DownloadMode.Video) },
                )
                RadioButtonRow(
                    text = "Audio",
                    selected = stateHolder.selectedMode == DownloadMode.Audio,
                    onClick = { stateHolder.selectMode(DownloadMode.Audio) },
                )
            }
        }

        FormRow("Quality") {
            ListComboBox(
                items = stateHolder.qualityOptions,
                selectedIndex = stateHolder.selectedQualityIndex,
                onSelectedItemChange = stateHolder::selectQuality,
                modifier =
                    Modifier
                        .width(if (compact) 300.dp else 336.dp)
                        .semantics { contentDescription = "Quality: ${stateHolder.selectedQualityLabel}" },
                style = qualityComboBoxStyle,
            )
        }

        FormRow("Save to") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stateHolder.destination,
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Save to ${stateHolder.destination}" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Link(text = "Change…", onClick = stateHolder::changeDestination)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(modifier = Modifier.weight(1f).height(36.dp)) {
                stateHolder.readyFeedback?.let { feedback ->
                    Text(
                        text = feedback,
                        modifier =
                            Modifier
                                .widthIn(max = 430.dp)
                                .semantics {
                                    contentDescription = "Status: $feedback"
                                    liveRegion = LiveRegionMode.Polite
                                },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            DefaultButton(
                onClick = stateHolder::download,
                enabled = stateHolder.downloadEnabled,
            ) {
                Text("Download")
            }
        }
    }
}

@Composable
private fun FormRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.width(96.dp))
        content()
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MediaIdentity(fixture: DownloadFixture, thumbnailWidth: androidx.compose.ui.unit.Dp) {
    val thumbnailAvailable =
        remember(fixture.thumbnailResource) {
            fixture.thumbnailResource?.let { resource ->
                DownloadStateHolder::class.java.classLoader.getResource(resource) != null
            } == true
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Media: ${fixture.title}. ${fixture.channel} · ${fixture.duration} · YouTube."
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnailAvailable) {
            Image(
                painter = painterResource(fixture.thumbnailResource!!),
                contentDescription = null,
                modifier = Modifier.width(thumbnailWidth).aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.width(thumbnailWidth).aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                Text("Preview unavailable")
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = fixture.title,
                style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${fixture.channel} · ${fixture.duration} · YouTube",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
