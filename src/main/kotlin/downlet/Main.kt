package downlet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import downlet.generated.resources.Res
import downlet.generated.resources.thumbnail_normal
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
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
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.ListComboBox
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

            ProductSurface(stateHolder = stateHolder)
        }
    }
}

@Composable
private fun ProductSurface(stateHolder: DownloadStateHolder) {
    val state = stateHolder.state
    val linkFieldState = stateHolder.linkFieldState
    val linkFieldFocusRequester = remember { FocusRequester() }
    var pasteIntent by remember { mutableStateOf(false) }

    LaunchedEffect(linkFieldState) {
        collectLinkEdits(
            edits = snapshotFlow { linkFieldState.text.toString() },
            stateHolder = stateHolder,
            consumePasteIntent = {
                pasteIntent.also { pasteIntent = false }
            },
        )
    }
    LaunchedEffect(pasteIntent) {
        if (pasteIntent) {
            expirePasteIntent { pasteIntent = false }
        }
    }
    LaunchedEffect(state) {
        if (state is DownloadUiState.Empty) {
            linkFieldFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(state) {
        if (state is DownloadUiState.Resolving) {
            completeAutomaticResolution(stateHolder, state)
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
        val workPlaneShape = RoundedCornerShape(10.dp)
        val accent = JewelTheme.globalColors.outlines.focused
        val workPlaneFill = accent.copy(alpha = if (JewelTheme.isDark) 0.10f else 0.055f)
        val workPlaneBorder = JewelTheme.globalColors.borders.normal

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
                                .focusRequester(linkFieldFocusRequester)
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
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(workPlaneShape)
                        .background(workPlaneFill)
                        .border(1.dp, workPlaneBorder, workPlaneShape)
                        .padding(if (compact) 12.dp else 16.dp),
            ) {
                ProductBody(stateHolder, compact)
            }
        }
    }
}

internal suspend fun collectLinkEdits(
    edits: Flow<String>,
    stateHolder: DownloadStateHolder,
    consumePasteIntent: () -> Boolean,
) {
    edits.collectLatest { text ->
        if (!stateHolder.observeLinkEdit(text)) return@collectLatest

        when (val submission = linkSubmissionFor(text, consumePasteIntent())) {
            LinkSubmission.None -> Unit
            LinkSubmission.ResolveImmediately -> stateHolder.beginResolution(text)
            is LinkSubmission.ResolveAfter -> {
                delay(submission.delayMillis.milliseconds)
                stateHolder.beginResolution(text)
            }
        }
    }
}

internal suspend fun expirePasteIntent(clearPasteIntent: () -> Unit) {
    delay(PasteIntentLifetimeMillis.milliseconds)
    clearPasteIntent()
}

internal suspend fun completeAutomaticResolution(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState.Resolving,
) {
    if (!state.completesAutomatically) return

    delay(FakeResolutionMillis.milliseconds)
    stateHolder.completeResolution(state.fixture)
}

@Composable
private fun ProductBody(stateHolder: DownloadStateHolder, compact: Boolean) {
    val easing = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val risePixels = with(LocalDensity.current) { 6.dp.roundToPx() }

    AnimatedContent(
        targetState = stateHolder.state,
        transitionSpec = {
            (fadeIn(animationSpec = tween(durationMillis = 200, easing = easing)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 200, easing = easing),
                        initialOffsetY = { risePixels },
                    ))
                .togetherWith(fadeOut(animationSpec = tween(durationMillis = 150, easing = easing)))
                .using(sizeTransform = null)
        },
        contentAlignment = Alignment.TopStart,
        contentKey = { it::class },
        label = "Downlet state body",
    ) { state ->
        when (state) {
            DownloadUiState.Empty ->
                Text(
                    text = "Paste or type a YouTube link. Downlet checks it automatically.",
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Status: Paste or type a YouTube link. Downlet checks it automatically."
                        },
                    style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Medium),
                )

            is DownloadUiState.Resolving ->
                Row(
                    modifier =
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "Status: Checking this YouTube link…"
                            liveRegion = LiveRegionMode.Polite
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Checking this YouTube link…")
                }

            is DownloadUiState.Ready -> ReadyBody(stateHolder, state.fixture, compact)
            else -> Text(state.label)
        }
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
                stateHolder.readyStatus?.let { feedback ->
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

@Composable
private fun MediaIdentity(fixture: DownloadFixture, thumbnailWidth: androidx.compose.ui.unit.Dp) {
    val previewShape = RoundedCornerShape(7.dp)
    val previewBorder = JewelTheme.globalColors.borders.normal
    val fallbackFill =
        JewelTheme.globalColors.outlines.focused.copy(alpha = if (JewelTheme.isDark) 0.16f else 0.09f)
    val previewModifier =
        Modifier
            .width(thumbnailWidth)
            .aspectRatio(16f / 9f)
            .clip(previewShape)
            .border(1.dp, previewBorder, previewShape)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = mediaContentDescription(fixture, fixture.thumbnailAvailable)
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (fixture.thumbnailAvailable) {
            Image(
                painter = painterResource(Res.drawable.thumbnail_normal),
                contentDescription = null,
                modifier = previewModifier,
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = previewModifier.background(fallbackFill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Preview unavailable",
                    style =
                        JewelTheme.defaultTextStyle.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = fixture.title,
                style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.SemiBold),
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

internal fun mediaContentDescription(
    fixture: DownloadFixture,
    thumbnailAvailable: Boolean,
): String =
    "Media: ${fixture.title}. ${fixture.channel} · ${fixture.duration} · YouTube." +
            if (thumbnailAvailable) "" else " Preview unavailable."
