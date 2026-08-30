package downlet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import downlet.generated.resources.Res
import downlet.generated.resources.preview_unavailable
import downlet.generated.resources.thumbnail_city_after_rain
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.ComboBoxIcons
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.skia.Image as SkiaImage

@Composable
@Suppress("LongMethod")
internal fun ToolSetupContent(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState.Setup,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaIdentity(state.fixture, thumbnailWidth = 128.dp, showDuration = false)
        Text(
            text = "Prepare this download",
            style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            "Downlet needs ${state.tools.toReadableList()} to check formats and create your file. " +
                "They are not included with Downlet. Downloading them does not download this media. " +
                "Download size is about ${state.tools.estimatedDownloadMegabytes()} MB.",
        )
        Link("Read full terms", onClick = stateHolder::showLegalDetails)
        CheckboxRow(
            text = TOOL_SETUP_CONSENT_TEXT,
            checked = stateHolder.toolSetupAccepted,
            onCheckedChange = stateHolder::updateToolSetupConsent,
            enabled = !state.installing,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        if (state.failed) {
            InlineErrorBanner(
                title = "Tool setup failed.",
                icon = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Error: Tool setup failed. $TOOL_SETUP_FAILURE_MESSAGE"
                            liveRegion = LiveRegionMode.Polite
                        },
            ) {
                Text(TOOL_SETUP_FAILURE_MESSAGE)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.installing) {
                Row(
                    modifier =
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "Status: Downloading and verifying required tools."
                            liveRegion = LiveRegionMode.Polite
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Downloading and verifying tools…")
                }
            } else {
                DefaultButton(
                    onClick = stateHolder::installTools,
                    enabled = stateHolder.toolSetupEnabled,
                ) {
                    Text("Download and continue")
                }
            }
        }
    }
}

@Composable
internal fun LegalDetailsContent(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Link(
            text = if (state is DownloadUiState.Setup) "Back to setup" else "Back to download",
            onClick = stateHolder::hideLegalDetails,
        )
        Text(
            text = "Full terms",
            style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.SemiBold),
        )
        LegalSection(
            title = "Before setup",
            body =
                "After you enter a link, Downlet asks YouTube for its title, channel, and thumbnail so you can " +
                    "identify the media before deciding whether to install tools. It does not download media.",
        )
        LegalSection(
            title = "Third-party tools",
            body =
                "Downlet includes QuickJS-NG for YouTube JavaScript support. If you choose to continue, Downlet " +
                    "downloads only missing pinned copies of yt-dlp and FFmpeg, verifies each SHA-256 hash, stores " +
                    "them in your local application-data folder, and runs the tools as separate programs.",
        )
        LegalSection(
            title = "Licenses",
            body =
                "QuickJS-NG is MIT, the official yt-dlp Windows executable is GPLv3+, and the FFmpeg Windows build " +
                    "is GPLv3. Those licenses apply to those tools. Downlet's original code remains 0BSD.",
        )
        LegalSection(
            title = "Your responsibility",
            body =
                "You choose whether to install the tools, which URL to use, what to download, where to save it, " +
                    "and how to use it. Before each media download, you must confirm that you own the media or have " +
                    "permission and that your use follows applicable law and YouTube's terms.",
        )
        LegalSection(
            title = "Limits",
            body =
                "Downlet grants no rights to media and comes without warranty. Its authors disclaim liability to " +
                    "the fullest extent permitted by law. Your consent cannot bind YouTube or a rights holder, " +
                    "override law or platform terms, or waive liability that the law does not allow a party to waive.",
        )
    }
}

@Composable
internal fun ResolvingContent(state: DownloadUiState.Resolving) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MediaIdentity(state.fixture, thumbnailWidth = 128.dp, showDuration = false)
        Row(
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Status: Checking available formats…"
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text("Checking available formats…")
        }
    }
}

@Composable
private fun LegalSection(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(body)
    }
}

@Composable
internal fun DownloadWorkPlaneContent(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState,
    compact: Boolean,
) {
    if (state is DownloadUiState.Error && state.kind == DownloadErrorKind.Resolution) {
        ErrorActionRegion(stateHolder, state.kind)
        return
    }
    val fixture = state.fixtureOrNull ?: return
    val controlsEnabled = state is DownloadUiState.Ready
    val controlGap = if (compact) 8.dp else 10.dp
    val thumbnailWidth = if (compact) 96.dp else 128.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(controlGap),
    ) {
        MediaIdentity(fixture, thumbnailWidth)
        DownloadModeRow(stateHolder, controlsEnabled)
        QualityRow(stateHolder, compact, controlsEnabled)
        DestinationRow(stateHolder, controlsEnabled)
        if (state is DownloadUiState.Ready) DownloadAuthorizationRow(stateHolder)
        StateActionRegion(stateHolder, state)
    }
}

@Composable
private fun DownloadAuthorizationRow(stateHolder: DownloadStateHolder) {
    FormRow("Permission") {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            CheckboxRow(
                text = DOWNLOAD_AUTHORIZATION_TEXT,
                checked = stateHolder.downloadAuthorizationAccepted,
                onCheckedChange = stateHolder::updateDownloadAuthorization,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
            Link("Read full terms", onClick = stateHolder::showLegalDetails)
        }
    }
}

@Composable
private fun DownloadModeRow(
    stateHolder: DownloadStateHolder,
    enabled: Boolean,
) {
    FormRow("Download as") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RadioButtonRow(
                text = "Video",
                selected = stateHolder.selectedMode == DownloadMode.Video,
                onClick = { stateHolder.selectMode(DownloadMode.Video) },
                enabled = enabled,
            )
            RadioButtonRow(
                text = "Audio",
                selected = stateHolder.selectedMode == DownloadMode.Audio,
                onClick = { stateHolder.selectMode(DownloadMode.Audio) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun QualityRow(
    stateHolder: DownloadStateHolder,
    compact: Boolean,
    enabled: Boolean,
) {
    val comboBoxStyle = JewelTheme.comboBoxStyle
    val qualityComboBoxStyle =
        remember(comboBoxStyle) {
            ComboBoxStyle(
                colors = comboBoxStyle.colors,
                metrics = comboBoxStyle.metrics,
                icons = ComboBoxIcons(PathIconKey("chevron-down.svg", DownloadStateHolder::class.java)),
            )
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
            enabled = enabled,
            style = qualityComboBoxStyle,
        )
    }
}

@Composable
private fun DestinationRow(
    stateHolder: DownloadStateHolder,
    enabled: Boolean,
) {
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
            Link(text = "Change…", onClick = stateHolder::changeDestination, enabled = enabled)
        }
    }
}

@Composable
private fun StateActionRegion(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState,
) {
    when (state) {
        is DownloadUiState.Ready -> ReadyActionRow(stateHolder)
        is DownloadUiState.Downloading -> DownloadingActionRegion(stateHolder, state)
        is DownloadUiState.Completed -> CompletedActionRegion(stateHolder)
        is DownloadUiState.Error -> ErrorActionRegion(stateHolder, state.kind)
        else -> Unit
    }
}

@Composable
private fun ReadyActionRow(stateHolder: DownloadStateHolder) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(modifier = Modifier.weight(1f).height(36.dp)) {
            stateHolder.readyStatus?.let { feedback ->
                StatusText(feedback)
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

@Composable
private fun DownloadingActionRegion(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState.Downloading,
) {
    val status = downloadProgressStatus(state.progressPercent)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Downloading: ${state.progressPercent}%. $status"
                    liveRegion = LiveRegionMode.Polite
                },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Downloading")
            Spacer(Modifier.weight(1f))
            Text("${state.progressPercent}%", fontWeight = FontWeight.SemiBold)
        }
        HorizontalProgressBar(
            progress = state.progressPercent / 100f,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(
                                current = state.progressPercent / 100f,
                                range = 0f..1f,
                            )
                    },
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(status)
            Spacer(Modifier.weight(1f))
            Link("Cancel", onClick = stateHolder::cancelDownload)
        }
    }
}

@Composable
private fun CompletedActionRegion(stateHolder: DownloadStateHolder) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Completed. Saved to ${stateHolder.destination}"
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✓", fontWeight = FontWeight.SemiBold)
            Text("Saved to ${stateHolder.destination}", fontWeight = FontWeight.SemiBold)
        }
        stateHolder.completedFeedback?.let { StatusText(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Link("Download Another", onClick = stateHolder::downloadAnother)
            Spacer(Modifier.width(12.dp))
            DefaultButton(onClick = stateHolder::openFolder) {
                Text("Open Folder")
            }
        }
    }
}

@Composable
private fun ErrorActionRegion(
    stateHolder: DownloadStateHolder,
    kind: DownloadErrorKind,
) {
    val title =
        if (kind == DownloadErrorKind.Resolution) {
            "Couldn't read this YouTube link."
        } else {
            "Couldn't download this media."
        }
    val body =
        if (kind == DownloadErrorKind.Resolution) {
            "Check that the link is available and try again."
        } else {
            "Check that the YouTube link is available and try again."
        }
    InlineErrorBanner(
        title = title,
        icon = null,
        linkActions = { action("Retry", stateHolder::retryDownload) },
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Error: $title $body"
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        Text(body)
    }
}

@Composable
private fun StatusText(feedback: String) {
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
@Suppress("LongMethod")
private fun MediaIdentity(
    fixture: DownloadFixture,
    thumbnailWidth: Dp,
    showDuration: Boolean = true,
) {
    val previewShape = RoundedCornerShape(7.dp)
    val previewBorder = JewelTheme.globalColors.borders.normal
    val fallbackFill =
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = if (JewelTheme.isDark) 0.16f else 0.09f)
    val previewModifier =
        Modifier
            .width(thumbnailWidth)
            .aspectRatio(16f / 9f)
            .clip(previewShape)
            .border(1.dp, previewBorder, previewShape)
    val remoteThumbnail =
        remember(fixture.thumbnailData) {
            fixture.thumbnailData?.let { thumbnail ->
                runCatching { SkiaImage.makeFromEncoded(thumbnail.bytes).toComposeImageBitmap() }.getOrNull()
            }
        }
    val hasThumbnail = remoteThumbnail != null || fixture.thumbnailAvailable

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = mediaContentDescription(fixture, hasThumbnail, showDuration)
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (remoteThumbnail != null) {
            Image(
                bitmap = remoteThumbnail,
                contentDescription = null,
                modifier = previewModifier,
                contentScale = ContentScale.Crop,
            )
        } else if (fixture.thumbnailAvailable) {
            Image(
                painter = painterResource(Res.drawable.thumbnail_city_after_rain),
                contentDescription = null,
                modifier = previewModifier,
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = previewModifier.background(fallbackFill),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Image(
                    painter = painterResource(Res.drawable.preview_unavailable),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(JewelTheme.contentColor),
                )
                Text(
                    text = "Preview unavailable",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
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
                text =
                    listOfNotNull(
                        fixture.channel,
                        fixture.duration.takeIf { showDuration && it.isNotBlank() },
                        "YouTube",
                    ).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun mediaContentDescription(
    fixture: DownloadFixture,
    thumbnailAvailable: Boolean,
    showDuration: Boolean = true,
): String =
    "Media: ${fixture.title}. " +
        listOfNotNull(
            fixture.channel,
            fixture.duration.takeIf { showDuration && it.isNotBlank() },
            "YouTube",
        ).joinToString(" · ") +
        "." +
        if (thumbnailAvailable) "" else " Preview unavailable."

@Suppress("MagicNumber")
internal fun downloadProgressStatus(progressPercent: Int): String =
    when (progressPercent) {
        0 -> "Starting download…"
        18 -> "4.8 MB/s · About 18 seconds remaining"
        43 -> "5.1 MB/s · About 11 seconds remaining"
        68 -> "4.9 MB/s · About 7 seconds remaining"
        87 -> "5.0 MB/s · About 3 seconds remaining"
        in 1 until MAX_PROGRESS_BEFORE_FINISHING -> "Downloading…"
        else -> "Finishing…"
    }

private const val MAX_PROGRESS_BEFORE_FINISHING = 99
private const val YT_DLP_DOWNLOAD_MEGABYTES = 17
private const val FFMPEG_DOWNLOAD_MEGABYTES = 106

private fun List<String>.toReadableList(): String =
    when (size) {
        1 -> single()
        2 -> joinToString(" and ")
        else -> dropLast(1).joinToString(", ") + ", and " + last()
    }

private fun List<String>.estimatedDownloadMegabytes(): Int =
    sumOf { tool ->
        when (tool) {
            "yt-dlp" -> YT_DLP_DOWNLOAD_MEGABYTES
            "FFmpeg" -> FFMPEG_DOWNLOAD_MEGABYTES
            else -> 0
        }
    }

private val DownloadUiState.fixtureOrNull: DownloadFixture?
    get() =
        when (this) {
            is DownloadUiState.Setup -> fixture
            is DownloadUiState.Ready -> fixture
            is DownloadUiState.Downloading -> fixture
            is DownloadUiState.Completed -> fixture
            is DownloadUiState.Error -> fixture
            else -> null
        }
