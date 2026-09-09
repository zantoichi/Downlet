package downlet

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarMetrics
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.ui.theme.horizontalProgressBarStyle
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.time.Duration

@Composable
@Suppress("LongMethod")
internal fun QuietProgressRail(
    fraction: Float?,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseStyle = JewelTheme.horizontalProgressBarStyle
    val style =
        remember(baseStyle) {
            HorizontalProgressBarStyle(
                colors = baseStyle.colors,
                metrics =
                    HorizontalProgressBarMetrics(
                        cornerSize = CornerSize(100),
                        minHeight = QUIET_RAIL_HEIGHT,
                        indeterminateHighlightWidth = baseStyle.metrics.indeterminateHighlightWidth,
                    ),
                indeterminateCycleDuration = baseStyle.indeterminateCycleDuration,
            )
        }
    if (fraction == null) {
        val semantics =
            modifier.semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
        if (animationsEnabled) {
            IndeterminateHorizontalProgressBar(semantics.fillMaxWidth(), style)
        } else {
            StaticIndeterminateRail(semantics.fillMaxWidth(), style)
        }
        return
    }

    val target = fraction.coerceIn(0f, 1f)
    val animatedFraction by
        animateFloatAsState(
            targetValue = target,
            animationSpec =
                if (animationsEnabled) {
                    tween(QUIET_RAIL_ANIMATION_MILLIS, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                } else {
                    snap()
                },
            label = "Quiet progress rail",
        )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                },
    ) {
        HorizontalProgressBar(animatedFraction, Modifier.fillMaxWidth(), style)
        if (animatedFraction > 0f && animatedFraction < 1f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = QUIET_RAIL_HEIGHT)
                    .clip(RoundedCornerShape(style.metrics.cornerSize))
                    .drawWithContent {
                        val capWidth = QUIET_RAIL_CAP_WIDTH.toPx().coerceAtMost(size.width)
                        val capX = (size.width * animatedFraction - capWidth / 2f).coerceIn(0f, size.width - capWidth)
                        drawRoundRect(
                            color = style.colors.indeterminateHighlight,
                            topLeft = Offset(capX, 0f),
                            size = Size(capWidth, size.height),
                            cornerRadius = CornerRadius(capWidth / 2f, capWidth / 2f),
                        )
                    }.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun StaticIndeterminateRail(
    modifier: Modifier,
    style: HorizontalProgressBarStyle,
) {
    Box(
        modifier
            .defaultMinSize(minHeight = style.metrics.minHeight)
            .clip(RoundedCornerShape(style.metrics.cornerSize))
            .drawWithContent {
                drawRect(style.colors.track)
                val highlightWidth = size.width * STATIC_HIGHLIGHT_FRACTION
                drawRoundRect(
                    color = style.colors.indeterminateHighlight,
                    topLeft = Offset((size.width - highlightWidth) / 2f, 0f),
                    size = Size(highlightWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                )
            },
    )
}

internal data class DownloadProgressPresentation(
    val heading: String,
    val leftText: String,
    val rightText: String? = null,
)

internal fun downloadProgressPresentation(
    progress: DownloadProgress,
    processingElapsed: Duration? = null,
): DownloadProgressPresentation =
    when (progress) {
        DownloadProgress.Preparing -> {
            DownloadProgressPresentation("Preparing", "Preparing download…")
        }

        is DownloadProgress.Transferring -> {
            DownloadProgressPresentation(
                heading = "Downloading",
                leftText = transferTelemetry(progress),
                rightText = progress.eta?.let(::formatEta),
            )
        }

        is DownloadProgress.Processing -> {
            DownloadProgressPresentation(
                heading = "Finalizing",
                leftText =
                    when (progress.stage) {
                        DownloadProcessingStage.Merging -> "Merging video and audio…"
                        DownloadProcessingStage.Converting -> "Converting audio…"
                        DownloadProcessingStage.Finalizing -> "Preparing the completed file…"
                    },
                rightText =
                    if (progress.stage == DownloadProcessingStage.Converting) {
                        processingElapsed?.let(::formatElapsed)
                    } else {
                        null
                    },
            )
        }
    }

@Suppress("MagicNumber")
internal fun downloadProgressAnnouncement(progress: DownloadProgress): String? =
    when (progress) {
        DownloadProgress.Preparing -> {
            "Preparing download."
        }

        is DownloadProgress.Transferring -> {
            when (val percent = progress.percent) {
                null -> "Download started."
                in 0..9 -> "Download started."
                in 10 until MAX_TRANSFER_PERCENT -> "Download ${(percent / 10) * 10} percent."
                else -> null
            }
        }

        is DownloadProgress.Processing -> {
            when (progress.stage) {
                DownloadProcessingStage.Merging -> "Finalizing. Merging video and audio."
                DownloadProcessingStage.Converting -> "Finalizing. Converting audio."
                DownloadProcessingStage.Finalizing -> "Finalizing download."
            }
        }
    }

private fun transferTelemetry(progress: DownloadProgress.Transferring): String {
    val bytes = formatDecimalBytes(progress.downloadedBytes)
    val transfer =
        progress.totalBytes?.let { total ->
            "$bytes of ${if (progress.totalIsEstimated) "~" else ""}${formatDecimalBytes(total)}"
        } ?: "$bytes downloaded"
    val speed = progress.speedBytesPerSecond?.let { "${formatDecimalBytes(it.roundToLong())}/s" }
    return listOfNotNull(transfer, speed).joinToString(" · ")
}

internal fun formatDecimalBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= DECIMAL_UNIT && unitIndex < units.lastIndex) {
        value /= DECIMAL_UNIT
        unitIndex += 1
    }
    val number =
        if (unitIndex == 0) {
            bytes.toString()
        } else {
            String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
        }
    return "$number ${units[unitIndex]}"
}

@Suppress("MagicNumber")
internal fun formatEta(duration: Duration): String {
    val seconds = (duration.inWholeMilliseconds / 1_000.0).roundToLong().coerceAtLeast(1)
    return when {
        seconds < 60 -> {
            "About $seconds sec"
        }

        seconds < 3_600 -> {
            "About ${(seconds / 60.0).roundToLong()} min"
        }

        else -> {
            val hours = seconds / 3_600
            val minutes = (seconds % 3_600) / 60
            "About $hours hr $minutes min"
        }
    }
}

@Suppress("MagicNumber")
internal fun formatElapsed(duration: Duration): String {
    val seconds = duration.inWholeSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> {
            "$seconds sec elapsed"
        }

        seconds < 3_600 -> {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            if (remainingSeconds == 0L) "$minutes min elapsed" else "$minutes min $remainingSeconds sec elapsed"
        }

        else -> {
            val hours = seconds / 3_600
            val minutes = (seconds % 3_600) / 60
            if (minutes == 0L) "$hours hr elapsed" else "$hours hr $minutes min elapsed"
        }
    }
}

private val QUIET_RAIL_HEIGHT = 6.dp
private val QUIET_RAIL_CAP_WIDTH = 2.dp
private const val QUIET_RAIL_ANIMATION_MILLIS = 200
private const val STATIC_HIGHLIGHT_FRACTION = 0.3f
private const val DECIMAL_UNIT = 1_000.0
