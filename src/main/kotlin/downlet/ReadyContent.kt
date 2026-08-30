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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
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
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlin.time.Duration

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
        MediaIdentity(state.item, thumbnailWidth = 128.dp, showDuration = false)
        Text(
            text = "Prepare this download",
            style = LocalDownletTypography.current.sectionHeading,
        )
        Text(
            ProductCopy.toolSetupDescription(
                toolNames = state.tools.map(DownloadTool::label).toReadableList(),
                estimatedDownloadMegabytes = state.tools.sumOf(DownloadTool::estimatedDownloadMegabytes),
            ),
        )
        Link("Read full terms", onClick = stateHolder::showLegalDetails)
        CheckboxRow(
            text = ProductCopy.TOOL_SETUP_CONSENT_TEXT,
            checked = stateHolder.toolSetupAccepted,
            onCheckedChange = stateHolder::updateToolSetupConsent,
            enabled = state.phase != ToolSetupPhase.Installing,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        if (state.phase == ToolSetupPhase.Failed) {
            InlineErrorBanner(
                title = "Tool setup failed.",
                icon = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Error: Tool setup failed. ${ProductCopy.TOOL_SETUP_FAILURE_MESSAGE}"
                            liveRegion = LiveRegionMode.Polite
                        },
            ) {
                Text(ProductCopy.TOOL_SETUP_FAILURE_MESSAGE)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.phase == ToolSetupPhase.Installing) {
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
                    Icon(AllIconsKeys.Actions.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
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
        IconLink(
            icon = AllIconsKeys.Actions.Back,
            text = if (state is DownloadUiState.Setup) "Back to setup" else "Back to download",
            onClick = stateHolder::hideLegalDetails,
        )
        Text(
            text = "Full terms",
            style = LocalDownletTypography.current.sectionHeading,
        )
        ProductCopy.legalSections.forEach { section ->
            LegalSection(title = section.title, body = section.body)
        }
    }
}

@Composable
internal fun ResolvingContent(state: DownloadUiState.Resolving) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MediaIdentity(state.item, thumbnailWidth = 128.dp, showDuration = false)
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
            style = LocalDownletTypography.current.formLabel,
        )
        Text(body, style = LocalDownletTypography.current.legal)
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
    val item = state.itemOrNull ?: return
    val controlsEnabled = state is DownloadUiState.Ready
    val controlGap = if (compact) 10.dp else 16.dp
    val thumbnailWidth = if (compact) 96.dp else 128.dp

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(controlGap),
    ) {
        MediaIdentity(item, thumbnailWidth)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DownloadModeRow(stateHolder, controlsEnabled, Modifier.weight(1f))
            QualityRow(stateHolder, controlsEnabled, Modifier.weight(QUALITY_COLUMN_WEIGHT))
        }
        DestinationRow(stateHolder, controlsEnabled)
        if (state is DownloadUiState.Ready) DownloadAuthorizationRow(stateHolder)
        Spacer(Modifier.weight(1f))
        StateActionRegion(stateHolder, state)
    }
}

@Composable
private fun DownloadAuthorizationRow(stateHolder: DownloadStateHolder) {
    LabeledSection(
        icon = AllIconsKeys.Nodes.Padlock,
        label = "Permission",
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            CheckboxRow(
                text = ProductCopy.DOWNLOAD_AUTHORIZATION_TEXT,
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
    modifier: Modifier,
) {
    LabeledSection(
        icon = AllIconsKeys.Actions.Download,
        label = "Download as",
        modifier = modifier,
    ) {
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
    enabled: Boolean,
    modifier: Modifier,
) {
    val label = if (stateHolder.selectedMode == DownloadMode.Audio) "Format & quality" else "Quality"
    LabeledSection(
        icon = AllIconsKeys.General.Settings,
        label = label,
        modifier = modifier,
    ) {
        ListComboBox(
            items = stateHolder.qualityOptions,
            selectedIndex = stateHolder.selectedQualityIndex,
            onSelectedItemChange = stateHolder::selectQuality,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "$label: ${stateHolder.selectedQualityLabel}" },
            enabled = enabled,
        )
    }
}

@Composable
private fun DestinationRow(
    stateHolder: DownloadStateHolder,
    enabled: Boolean,
) {
    val destination = stateHolder.destination?.toString().orEmpty()
    LabeledSection(
        icon = AllIconsKeys.Nodes.Folder,
        label = "Save to",
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = destination,
                style = LocalDownletTypography.current.exactMetadata,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Save to $destination" },
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
            Icon(AllIconsKeys.Actions.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Download")
        }
    }
}

@Composable
private fun DownloadingActionRegion(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState.Downloading,
) {
    val status = downloadProgressStatus(state.progress)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Downloading: ${state.progress.percent}%. $status"
                    liveRegion = LiveRegionMode.Polite
                },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Downloading", style = LocalDownletTypography.current.formLabel)
            Spacer(Modifier.weight(1f))
            Text("${state.progress.percent}%", style = LocalDownletTypography.current.progressNumber)
        }
        HorizontalProgressBar(
            progress = state.progress.fraction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(
                                current = state.progress.fraction,
                                range = 0f..1f,
                            )
                    },
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(status, style = LocalDownletTypography.current.numericMetadata)
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
            Text("✓", style = LocalDownletTypography.current.mediaTitle)
            Text("Saved to ${stateHolder.destination}", style = LocalDownletTypography.current.mediaTitle)
        }
        stateHolder.completedFeedback?.let { StatusText(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconLink(
                icon = AllIconsKeys.Actions.Restart,
                text = "Download another",
                onClick = stateHolder::downloadAnother,
            )
            Spacer(Modifier.width(12.dp))
            DefaultButton(onClick = stateHolder::openFolder) {
                Icon(AllIconsKeys.Nodes.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open folder")
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
        style = LocalDownletTypography.current.metadata,
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
private fun IconLink(
    icon: org.jetbrains.jewel.ui.icon.IconKey,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Link(text, onClick = onClick)
    }
}

@Composable
@Suppress("LongMethod")
private fun MediaIdentity(
    item: DownloadItem,
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
    val remoteThumbnailData = (item.thumbnail as? MediaThumbnail.Remote)?.data
    val remoteThumbnail =
        remember(remoteThumbnailData) {
            remoteThumbnailData?.let { thumbnail ->
                runCatching { thumbnail.bytes.decodeToImageBitmap() }.getOrNull()
            }
        }
    val hasThumbnail = remoteThumbnail != null || item.thumbnail is MediaThumbnail.BundledPreview

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = mediaContentDescription(item, hasThumbnail, showDuration)
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
        } else if (item.thumbnail is MediaThumbnail.BundledPreview) {
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
                    style = LocalDownletTypography.current.metadata,
                    modifier = Modifier.padding(horizontal = 4.dp),
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
                text = item.title,
                style = LocalDownletTypography.current.mediaTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    listOfNotNull(
                        item.channel,
                        formatMediaDuration(item.duration).takeIf { showDuration },
                        "YouTube",
                    ).joinToString(" · "),
                style = LocalDownletTypography.current.numericMetadata,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun mediaContentDescription(
    item: DownloadItem,
    thumbnailAvailable: Boolean,
    showDuration: Boolean = true,
): String =
    "Media: ${item.title}. " +
        listOfNotNull(
            item.channel,
            formatMediaDuration(item.duration).takeIf { showDuration },
            "YouTube",
        ).joinToString(" · ") +
        "." +
        if (thumbnailAvailable) "" else " Preview unavailable."

@Suppress("MagicNumber")
internal fun downloadProgressStatus(progress: DownloadProgress): String =
    when (progress.percent) {
        0 -> "Starting download…"
        18 -> "4.8 MB/s · About 18 seconds remaining"
        43 -> "5.1 MB/s · About 11 seconds remaining"
        68 -> "4.9 MB/s · About 7 seconds remaining"
        87 -> "5.0 MB/s · About 3 seconds remaining"
        in 1 until MAX_PROGRESS_BEFORE_FINISHING -> "Downloading…"
        else -> "Finishing…"
    }

internal fun formatMediaDuration(duration: Duration?): String {
    if (duration == null) return "Unknown duration"

    val totalSeconds = duration.inWholeSeconds
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private const val MAX_PROGRESS_BEFORE_FINISHING = 99
private const val QUALITY_COLUMN_WEIGHT = 1.35f

private fun List<String>.toReadableList(): String =
    when (size) {
        1 -> single()
        2 -> joinToString(" and ")
        else -> dropLast(1).joinToString(", ") + ", and " + last()
    }
