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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import downlet.generated.resources.Res
import downlet.generated.resources.thumbnail_normal
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.ComboBoxIcons
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.theme.comboBoxStyle

@Composable
internal fun ReadyContent(
    stateHolder: DownloadStateHolder,
    fixture: DownloadFixture,
    compact: Boolean,
) {
    val controlGap = if (compact) 8.dp else 10.dp
    val thumbnailWidth = if (compact) 96.dp else 128.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(controlGap),
    ) {
        MediaIdentity(fixture, thumbnailWidth)
        DownloadModeRow(stateHolder)
        QualityRow(stateHolder, compact)
        DestinationRow(stateHolder)
        ReadyActionRow(stateHolder)
    }
}

@Composable
private fun DownloadModeRow(stateHolder: DownloadStateHolder) {
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
}

@Composable
private fun QualityRow(
    stateHolder: DownloadStateHolder,
    compact: Boolean,
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
            style = qualityComboBoxStyle,
        )
    }
}

@Composable
private fun DestinationRow(stateHolder: DownloadStateHolder) {
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
}

@Composable
private fun ReadyActionRow(stateHolder: DownloadStateHolder) {
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
