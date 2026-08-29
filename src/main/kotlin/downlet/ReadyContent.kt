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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import downlet.generated.resources.Res
import downlet.generated.resources.preview_unavailable
import downlet.generated.resources.thumbnail_city_after_rain
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
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

@Composable
internal fun DownloadWorkPlaneContent(
    stateHolder: DownloadStateHolder,
    state: DownloadUiState,
    compact: Boolean,
) {
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
        StateActionRegion(stateHolder, state)
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
        is DownloadUiState.Error -> ErrorActionRegion(stateHolder)
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
private fun ErrorActionRegion(stateHolder: DownloadStateHolder) {
    InlineErrorBanner(
        title = "Couldn't download this media.",
        icon = null,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "Error: Couldn't download this media. " +
                        "Check that the YouTube link is available and try again."
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Check that the YouTube link is available and try again.")
            Link("Retry", stateHolder::retryDownload)
        }
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
                    text = "No preview",
                    style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
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

@Suppress("MagicNumber")
internal fun downloadProgressStatus(progressPercent: Int): String =
    when (progressPercent) {
        0 -> "Starting download…"
        18 -> "4.8 MB/s · About 18 seconds remaining"
        43 -> "5.1 MB/s · About 11 seconds remaining"
        68 -> "4.9 MB/s · About 7 seconds remaining"
        87 -> "5.0 MB/s · About 3 seconds remaining"
        else -> "Finishing…"
    }

private val DownloadUiState.fixtureOrNull: DownloadFixture?
    get() =
        when (this) {
            is DownloadUiState.Ready -> fixture
            is DownloadUiState.Downloading -> fixture
            is DownloadUiState.Completed -> fixture
            is DownloadUiState.Error -> fixture
            else -> null
        }
