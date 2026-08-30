package downlet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val CONTENT_ENTER_DURATION: Duration = 200.milliseconds
private val CONTENT_EXIT_DURATION: Duration = 150.milliseconds

@Composable
internal fun ProductSurface(
    stateHolder: DownloadStateHolder,
    animationsEnabled: Boolean = true,
) {
    val linkFieldFocusRequester = remember { FocusRequester() }
    var pasteIntent by remember { mutableStateOf(false) }

    DisposableEffect(stateHolder) {
        onDispose(stateHolder::close)
    }

    LinkEffects(
        stateHolder = stateHolder,
        linkFieldFocusRequester = linkFieldFocusRequester,
        pasteIntent = pasteIntent,
        consumePasteIntent = {
            pasteIntent.also { pasteIntent = false }
        },
        clearPasteIntent = { pasteIntent = false },
    )

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground),
    ) {
        val compact = maxHeight < 400.dp
        val outerPadding = if (compact) 18.dp else 22.dp
        val majorGap = 16.dp
        val workPlaneShape = RoundedCornerShape(10.dp)
        val accent = JewelTheme.globalColors.outlines.focused
        val workPlaneFill = accent.copy(alpha = if (JewelTheme.isDark) 0.10f else 0.055f)
        val workPlaneBorder = JewelTheme.globalColors.borders.normal

        Column(
            modifier = Modifier.fillMaxSize().padding(outerPadding),
            verticalArrangement = Arrangement.spacedBy(majorGap),
        ) {
            LinkFieldRow(
                stateHolder = stateHolder,
                focusRequester = linkFieldFocusRequester,
                onPasteIntent = { pasteIntent = true },
            )
            ProductBody(
                stateHolder = stateHolder,
                compact = compact,
                workPlaneShape = workPlaneShape,
                workPlaneFill = workPlaneFill,
                workPlaneBorder = workPlaneBorder,
                animationsEnabled = animationsEnabled,
            )
        }
    }
}

@Composable
private fun LinkEffects(
    stateHolder: DownloadStateHolder,
    linkFieldFocusRequester: FocusRequester,
    pasteIntent: Boolean,
    consumePasteIntent: () -> Boolean,
    clearPasteIntent: () -> Unit,
) {
    val state = stateHolder.state
    val linkFieldState = stateHolder.linkFieldState

    LaunchedEffect(linkFieldState) {
        collectLinkEdits(
            edits = snapshotFlow { linkFieldState.text.toString() },
            stateHolder = stateHolder,
            consumePasteIntent = consumePasteIntent,
        )
    }
    LaunchedEffect(pasteIntent) {
        if (pasteIntent) {
            expirePasteIntent(clearPasteIntent)
        }
    }
    LaunchedEffect(state, stateHolder.linkFocusRequest) {
        if (state is DownloadUiState.Empty) {
            withFrameNanos { }
            linkFieldFocusRequester.requestFocus()
        }
    }
}

@Composable
private fun LinkFieldRow(
    stateHolder: DownloadStateHolder,
    focusRequester: FocusRequester,
    onPasteIntent: () -> Unit,
) {
    LabeledSection(
        icon = AllIconsKeys.General.Web,
        label = "YouTube link",
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinkTextField(
                state = stateHolder.linkFieldState,
                focusRequester = focusRequester,
                hasValidationError = stateHolder.validationMessage != null,
                enabled = stateHolder.state !is DownloadUiState.Downloading,
                onPasteIntent = onPasteIntent,
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
}

@Composable
private fun LinkTextField(
    state: TextFieldState,
    focusRequester: FocusRequester,
    hasValidationError: Boolean,
    enabled: Boolean,
    onPasteIntent: () -> Unit,
) {
    TextField(
        state = state,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.isPasteShortcut()) onPasteIntent()
                    false
                }.semantics { contentDescription = "YouTube link field" },
        outline = if (hasValidationError) Outline.Error else Outline.None,
        enabled = enabled,
        textStyle = LocalDownletTypography.current.exactBody,
        placeholder = { Text("Paste a YouTube link…", Modifier.clearAndSetSemantics {}) },
    )
}

private fun KeyEvent.isPasteShortcut(): Boolean =
    type == KeyEventType.KeyDown &&
        ((isCtrlPressed && key == Key.V) || (isShiftPressed && key == Key.Insert))

internal suspend fun collectLinkEdits(
    edits: Flow<String>,
    stateHolder: DownloadStateHolder,
    consumePasteIntent: () -> Boolean,
) {
    var previousText = ""
    edits.collectLatest { text ->
        val previousValue = previousText
        previousText = text
        if (!stateHolder.observeLinkEdit(text)) return@collectLatest

        val resolutionDelay = linkResolutionDelay(previousValue, text, consumePasteIntent()) ?: return@collectLatest
        if (resolutionDelay > Duration.ZERO) delay(resolutionDelay)
        stateHolder.beginResolution(text)
    }
}

internal suspend fun expirePasteIntent(clearPasteIntent: () -> Unit) {
    delay(PASTE_INTENT_LIFETIME)
    clearPasteIntent()
}

@Composable
@Suppress("LongMethod")
private fun ColumnScope.ProductBody(
    stateHolder: DownloadStateHolder,
    compact: Boolean,
    workPlaneShape: RoundedCornerShape,
    workPlaneFill: androidx.compose.ui.graphics.Color,
    workPlaneBorder: androidx.compose.ui.graphics.Color,
    animationsEnabled: Boolean,
) {
    val easing = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val risePixels = with(LocalDensity.current) { 6.dp.roundToPx() }
    val showsWorkPlane = stateHolder.state.isWorkPlaneState

    AnimatedContent(
        targetState = stateHolder.state,
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (showsWorkPlane) Modifier.weight(1f) else Modifier),
        transitionSpec = {
            if (!animationsEnabled) {
                (EnterTransition.None togetherWith ExitTransition.None).using(sizeTransform = null)
            } else if (!initialState.isWorkPlaneState && targetState.isWorkPlaneState) {
                (
                    fadeIn(
                        animationSpec =
                            tween(durationMillis = CONTENT_ENTER_DURATION.inWholeMilliseconds.toInt(), easing = easing),
                    ) +
                        slideInVertically(
                            animationSpec =
                                tween(
                                    durationMillis = CONTENT_ENTER_DURATION.inWholeMilliseconds.toInt(),
                                    easing = easing,
                                ),
                            initialOffsetY = { risePixels },
                        )
                ).togetherWith(
                    fadeOut(
                        animationSpec =
                            tween(durationMillis = CONTENT_EXIT_DURATION.inWholeMilliseconds.toInt(), easing = easing),
                    ),
                ).using(sizeTransform = null)
            } else {
                (EnterTransition.None togetherWith ExitTransition.None).using(sizeTransform = null)
            }
        },
        contentAlignment = Alignment.TopStart,
        contentKey = { it.isWorkPlaneState },
        label = "Downlet state body",
    ) { state ->
        if (stateHolder.showingLegalDetails) {
            WorkPlane(
                shape = workPlaneShape,
                fill = workPlaneFill,
                border = workPlaneBorder,
                compact = compact,
            ) {
                LegalDetailsContent(stateHolder, state)
            }
        } else {
            when (state) {
                DownloadUiState.Empty -> {
                    Text(
                        text = "Paste or type a YouTube link. Downlet checks it automatically.",
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "Status: Paste or type a YouTube link. Downlet checks it automatically."
                            },
                    )
                }

                is DownloadUiState.Previewing -> {
                    Row(
                        modifier =
                            Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "Status: Finding this video…"
                                liveRegion = LiveRegionMode.Polite
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text("Finding this video…")
                    }
                }

                is DownloadUiState.Resolving -> {
                    WorkPlane(
                        shape = workPlaneShape,
                        fill = workPlaneFill,
                        border = workPlaneBorder,
                        compact = compact,
                    ) {
                        ResolvingContent(state)
                    }
                }

                is DownloadUiState.Setup -> {
                    WorkPlane(
                        shape = workPlaneShape,
                        fill = workPlaneFill,
                        border = workPlaneBorder,
                        compact = compact,
                    ) {
                        ToolSetupContent(stateHolder, state)
                    }
                }

                is DownloadUiState.Ready,
                is DownloadUiState.Downloading,
                is DownloadUiState.Completed,
                is DownloadUiState.Error,
                -> {
                    WorkPlane(
                        shape = workPlaneShape,
                        fill = workPlaneFill,
                        border = workPlaneBorder,
                        compact = compact,
                    ) {
                        DownloadWorkPlaneContent(stateHolder, state, compact, animationsEnabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkPlane(
    shape: RoundedCornerShape,
    fill: androidx.compose.ui.graphics.Color,
    border: androidx.compose.ui.graphics.Color,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
                .verticalScroll(rememberScrollState())
                .padding(if (compact) 12.dp else 16.dp),
    ) {
        content()
    }
}

private val DownloadUiState.isWorkPlaneState: Boolean
    get() =
        this is DownloadUiState.Setup ||
            this is DownloadUiState.Resolving ||
            this is DownloadUiState.Ready ||
            this is DownloadUiState.Downloading ||
            this is DownloadUiState.Completed ||
            this is DownloadUiState.Error
