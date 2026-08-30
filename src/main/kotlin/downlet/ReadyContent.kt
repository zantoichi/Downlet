package downlet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
            val errorColor = JewelTheme.globalColors.text.error
            InlineErrorBanner(
                icon = {
                    Icon(AllIconsKeys.General.NotificationError, contentDescription = null, tint = errorColor)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Error: Tool setup failed. ${ProductCopy.TOOL_SETUP_FAILURE_MESSAGE}"
                            liveRegion = LiveRegionMode.Polite
                        },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Tool setup failed.",
                        color = errorColor,
                        style = LocalDownletTypography.current.mediaTitle,
                    )
                    Text(ProductCopy.TOOL_SETUP_FAILURE_MESSAGE, color = errorColor)
                }
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
                    ButtonLabel(AllIconsKeys.Actions.Download, "Download and continue")
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
    animationsEnabled: Boolean,
) {
    if (state is DownloadUiState.Error && state.kind == DownloadErrorKind.Resolution) {
        ErrorActionRegion(stateHolder, state)
        return
    }
    val item = state.itemOrNull ?: return
    val controlsEnabled = state is DownloadUiState.Ready
    val controlGap = 10.dp
    val thumbnailWidth = 96.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
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
        StateActionRegion(stateHolder, state, animationsEnabled)
    }
}

@Composable
private fun DownloadAuthorizationRow(stateHolder: DownloadStateHolder) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Permission label"
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AllIconsKeys.Nodes.Padlock, contentDescription = null, modifier = Modifier.size(14.dp))
            Text("Permission", style = LocalDownletTypography.current.formLabel)
        }
        CheckboxRow(
            text = ProductCopy.DOWNLOAD_AUTHORIZATION_TEXT,
            checked = stateHolder.downloadAuthorizationAccepted,
            onCheckedChange = stateHolder::updateDownloadAuthorization,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Link("Read full terms", onClick = stateHolder::showLegalDetails)
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
        stateHolder.selectedQualitySupportingText?.let { supportingText ->
            Text(
                text = supportingText,
                style = LocalDownletTypography.current.metadata,
                modifier = Modifier.semantics { contentDescription = "Quality details: $supportingText" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DestinationRow(
    stateHolder: DownloadStateHolder,
    enabled: Boolean,
) {
    val destination = stateHolder.destination?.toString().orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Save to label"
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AllIconsKeys.Nodes.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
            Text("Save to", style = LocalDownletTypography.current.formLabel)
        }
        Text(
            text = destination,
            style = LocalDownletTypography.current.exactMetadata,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Save to $destination" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Link(text = "Change…", onClick = stateHolder::changeDestination, enabled = enabled)
    }
}

@Composable
private fun StateActionRegion(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState,
    animationsEnabled: Boolean,
) {
    when (state) {
        is DownloadUiState.Ready -> ReadyActionRow(stateHolder)
        is DownloadUiState.Downloading -> DownloadingActionRegion(stateHolder, state, animationsEnabled)
        is DownloadUiState.Completed -> CompletedActionRegion(stateHolder, animationsEnabled)
        is DownloadUiState.Error -> ErrorActionRegion(stateHolder, state)
        else -> Unit
    }
}

@Composable
private fun ReadyActionRow(stateHolder: DownloadStateHolder) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).height(36.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            stateHolder.readyStatus?.let { feedback ->
                StatusText(feedback)
            }
        }
        DefaultButton(
            onClick = stateHolder::download,
            enabled = stateHolder.downloadEnabled,
            modifier = Modifier.widthIn(min = 112.dp),
        ) {
            ButtonLabel(AllIconsKeys.Actions.Download, "Download")
        }
    }
}

@Composable
private fun DownloadingActionRegion(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState.Downloading,
    animationsEnabled: Boolean,
) {
    val presentation = downloadProgressPresentation(state.progress)
    val announcement = downloadProgressAnnouncement(state.progress)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            announcement?.let { message ->
                Box(
                    Modifier
                        .size(0.dp)
                        .semantics {
                            contentDescription = message
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            Text(presentation.heading, style = LocalDownletTypography.current.formLabel)
            Spacer(Modifier.weight(1f))
            (state.progress as? DownloadProgress.Transferring)?.let { progress ->
                progress.percent?.let { percent ->
                    Text("$percent%", style = LocalDownletTypography.current.progressNumber)
                }
            }
        }
        QuietProgressRail(
            fraction = (state.progress as? DownloadProgress.Transferring)?.fraction,
            animationsEnabled = animationsEnabled,
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(presentation.leftText, style = LocalDownletTypography.current.numericMetadata)
            Spacer(Modifier.weight(1f))
            presentation.rightText?.let { eta ->
                Text(eta, style = LocalDownletTypography.current.numericMetadata)
                Spacer(Modifier.width(12.dp))
            }
            Link("Cancel", onClick = stateHolder::cancelDownload)
        }
    }
}

@Composable
private fun CompletedActionRegion(
    stateHolder: DownloadStateHolder,
    animationsEnabled: Boolean,
) {
    var successVisible by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(animationsEnabled) { successVisible = true }
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
            AnimatedVisibility(
                visible = successVisible,
                enter =
                    if (animationsEnabled) {
                        fadeIn(tween(SUCCESS_ICON_ANIMATION_MILLIS)) +
                            scaleIn(tween(SUCCESS_ICON_ANIMATION_MILLIS), initialScale = SUCCESS_ICON_INITIAL_SCALE)
                    } else {
                        EnterTransition.None
                    },
            ) {
                Icon(AllIconsKeys.Status.Success, contentDescription = null, modifier = Modifier.size(16.dp))
            }
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
                ButtonLabel(AllIconsKeys.Nodes.Folder, "Open folder")
            }
        }
    }
}

@Composable
private fun ErrorActionRegion(
    stateHolder: DownloadStateHolder,
    error: DownloadUiState.Error,
) {
    val copy = ProductCopy.downloadFailure(error.kind, error.reason)
    val errorColor = JewelTheme.globalColors.text.error
    InlineErrorBanner(
        icon = {
            Icon(AllIconsKeys.General.NotificationError, contentDescription = null, tint = errorColor)
        },
        linkActions = {
            if (error.reason == DownloadFailureReason.Storage) {
                action("Change folder", stateHolder::changeDestination)
            }
            action("Retry", stateHolder::retryDownload)
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Error: ${copy.title} ${copy.guidance}"
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(copy.title, color = errorColor, style = LocalDownletTypography.current.mediaTitle)
            Text(copy.guidance, color = errorColor)
        }
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
private fun ButtonLabel(
    icon: org.jetbrains.jewel.ui.icon.IconKey,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = JewelTheme.contentColor,
        )
        Text(text)
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

private const val QUALITY_COLUMN_WEIGHT = 1.35f
private const val SUCCESS_ICON_ANIMATION_MILLIS = 160
private const val SUCCESS_ICON_INITIAL_SCALE = 0.92f

private fun List<String>.toReadableList(): String =
    when (size) {
        1 -> single()
        2 -> joinToString(" and ")
        else -> dropLast(1).joinToString(", ") + ", and " + last()
    }
