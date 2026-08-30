package downlet

import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.util.Base64
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal val MANUAL_LINK_DEBOUNCE: Duration = 350.milliseconds
internal val FAKE_RESOLUTION_DELAY: Duration = 550.milliseconds
internal val PASTE_INTENT_LIFETIME: Duration = 1.seconds
internal val FAKE_PROGRESS_INTERVAL: Duration = 350.milliseconds
private const val YT_DLP_ESTIMATED_DOWNLOAD_MEGABYTES = 17
private const val FFMPEG_ESTIMATED_DOWNLOAD_MEGABYTES = 106
private const val REMOTE_THUMBNAIL_FIXTURE_BASE64 =
    "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAFElEQVR4nGN0q7jEgA0wYRUdtBIAO+sBoPuKweIAAAAASUVORK5CYII="

internal enum class DownloadMode {
    Video,
    Audio,
}

internal enum class DownloadErrorKind {
    Resolution,
    Download,
}

@JvmInline
internal value class DownloadProgress(
    val percent: Int,
) {
    init {
        require(percent in 0..MAX_PERCENT)
    }

    val fraction: Float
        get() = percent / 100f

    companion object {
        private const val MAX_PERCENT = 99
        val Zero = DownloadProgress(0)
    }
}

internal val fakeProgressSteps = listOf(18, 43, 68, 87).map(::DownloadProgress)

internal data class DownloadQuality(
    val label: String,
    val ytDlpArguments: List<String>,
)

internal data class OriginalAudio(
    val format: String,
    val bitRateKilobitsPerSecond: Int?,
) {
    init {
        require(format.isNotBlank())
        require(bitRateKilobitsPerSecond == null || bitRateKilobitsPerSecond > 0)
    }

    val description: String
        get() = "${format.toAudioFormatLabel()} · ${bitRateKilobitsPerSecond?.let { "$it kbps" } ?: "unknown bitrate"}"
}

internal enum class DownloadTool(
    val label: String,
    val estimatedDownloadMegabytes: Int,
) {
    YtDlp("yt-dlp", YT_DLP_ESTIMATED_DOWNLOAD_MEGABYTES),
    Ffmpeg("FFmpeg", FFMPEG_ESTIMATED_DOWNLOAD_MEGABYTES),
}

internal val videoQualityOptions =
    listOf(
        videoQuality(maxHeightPixels = 2160, bestAvailable = true),
        videoQuality(maxHeightPixels = 1440),
        videoQuality(maxHeightPixels = 1080),
        videoQuality(maxHeightPixels = 720),
        videoQuality(maxHeightPixels = 480),
    )

internal fun audioQualityOptions(originalAudio: OriginalAudio?) =
    listOf(
        DownloadQuality(
            label = "Original audio — ${originalAudio?.description ?: "unknown format · unknown bitrate"}",
            ytDlpArguments = listOf("--format", "ba"),
        ),
        mp3Quality(),
        mp3Quality(bitRateKilobitsPerSecond = 160),
        mp3Quality(bitRateKilobitsPerSecond = 128),
    )

private fun String.toAudioFormatLabel(): String =
    when (lowercase(Locale.ROOT)) {
        "webm" -> "WebM"
        "m4a" -> "M4A"
        "mp4" -> "MP4"
        "ogg" -> "Ogg"
        else -> uppercase(Locale.ROOT)
    }

private fun videoQuality(
    maxHeightPixels: Int,
    bestAvailable: Boolean = false,
): DownloadQuality =
    DownloadQuality(
        label = if (bestAvailable) "Best available — ${maxHeightPixels}p" else "${maxHeightPixels}p",
        ytDlpArguments = listOf("--format", "bv*[height<=$maxHeightPixels]+ba/b[height<=$maxHeightPixels]"),
    )

private fun mp3Quality(bitRateKilobitsPerSecond: Int? = null): DownloadQuality =
    DownloadQuality(
        label =
            bitRateKilobitsPerSecond
                ?.let { "MP3 — $it kbps" }
                ?: "MP3 — Best quality · ~245 kbps VBR",
        ytDlpArguments =
            listOf(
                "--format",
                "ba/b",
                "--extract-audio",
                "--audio-format",
                "mp3",
                "--audio-quality",
                bitRateKilobitsPerSecond?.let { "${it}K" } ?: "0",
            ),
    )

private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

@JvmInline
internal value class YouTubeUrl private constructor(
    val uri: URI,
) {
    override fun toString(): String = uri.toString()

    companion object {
        fun parse(value: String): YouTubeUrl? =
            try {
                val uri = URI(value.trim())
                if (
                    uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                    uri.host?.lowercase(Locale.ROOT) in YOUTUBE_HOSTS
                ) {
                    YouTubeUrl(uri)
                } else {
                    null
                }
            } catch (_: URISyntaxException) {
                null
            }
    }
}

internal fun isValidYouTubeUrl(value: String): Boolean = YouTubeUrl.parse(value) != null

internal fun linkResolutionDelay(
    previousValue: String,
    value: String,
    explicitPaste: Boolean,
): Duration? =
    when {
        !isValidYouTubeUrl(value) -> null
        explicitPaste || insertedCharacterCount(previousValue, value) > 1 -> Duration.ZERO
        else -> MANUAL_LINK_DEBOUNCE
    }

private fun insertedCharacterCount(
    previousValue: String,
    value: String,
): Int {
    val prefixLength = previousValue.commonPrefixWith(value).length
    val previousRemainder = previousValue.length - prefixLength
    val valueRemainder = value.length - prefixLength
    var suffixLength = 0
    while (
        suffixLength < minOf(previousRemainder, valueRemainder) &&
        previousValue[previousValue.lastIndex - suffixLength] == value[value.lastIndex - suffixLength]
    ) {
        suffixLength += 1
    }
    return value.length - prefixLength - suffixLength
}

internal enum class DownletTheme {
    Light,
    Dark,
}

internal class ThumbnailData(
    val bytes: ByteArray,
) {
    init {
        require(bytes.isNotEmpty())
    }

    override fun equals(other: Any?): Boolean = other is ThumbnailData && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}

internal sealed interface MediaThumbnail {
    data object BundledPreview : MediaThumbnail

    data class Remote(
        val data: ThumbnailData,
    ) : MediaThumbnail

    data object Unavailable : MediaThumbnail
}

internal val MediaThumbnail.isAvailable: Boolean
    get() = this !is MediaThumbnail.Unavailable

internal data class DownloadItem(
    val source: YouTubeUrl,
    val title: String,
    val channel: String,
    val duration: Duration?,
    val destination: Path,
    val originalAudio: OriginalAudio? = null,
    val thumbnail: MediaThumbnail = MediaThumbnail.BundledPreview,
) {
    init {
        require(title.isNotBlank())
        require(channel.isNotBlank())
        require(duration == null || (duration >= Duration.ZERO && duration.isFinite()))
    }
}

internal object DownloadFixtures {
    val normal =
        DownloadItem(
            source = requireNotNull(YouTubeUrl.parse("https://www.youtube.com/watch?v=quiet-transfer")),
            title = "A calm walk through the city after rain",
            channel = "North Window",
            duration = 12.minutes + 34.seconds,
            destination = Path.of("Downloads"),
            originalAudio = OriginalAudio(format = "webm", bitRateKilobitsPerSecond = 130),
        )

    val longTitle =
        normal.copy(
            title =
                "A deliberately long media title that remains deterministic while exercising " +
                    "the future two-line layout",
        )

    val missingThumbnail = normal.copy(thumbnail = MediaThumbnail.Unavailable)

    val remoteThumbnail =
        normal.copy(
            thumbnail =
                MediaThumbnail.Remote(
                    ThumbnailData(Base64.getDecoder().decode(REMOTE_THUMBNAIL_FIXTURE_BASE64)),
                ),
        )

    val longDestination =
        normal.copy(
            destination = Path.of("C:\\Users\\Demo\\Videos\\Reference Material\\Long Destination Folder\\Downloads"),
        )

    val failure =
        normal.copy(
            source = requireNotNull(YouTubeUrl.parse("https://youtu.be/downlet-preview-failure")),
        )
}

internal val readyDestinations by lazy {
    listOf(
        DownloadFixtures.longDestination.destination,
        Path.of("D:\\Media\\Downloads"),
        DownloadFixtures.normal.destination,
    )
}

internal enum class ToolSetupPhase {
    AwaitingConsent,
    ReadyToInstall,
    Installing,
    Failed,
}

internal sealed interface DownloadUiState {
    data object Empty : DownloadUiState

    data class Previewing(
        val item: DownloadItem,
    ) : DownloadUiState

    data class Setup(
        val item: DownloadItem,
        val tools: List<DownloadTool>,
        val phase: ToolSetupPhase = ToolSetupPhase.AwaitingConsent,
    ) : DownloadUiState {
        init {
            require(tools.isNotEmpty())
            require(tools.distinct().size == tools.size)
        }
    }

    data class Resolving(
        val item: DownloadItem,
    ) : DownloadUiState

    data class Ready(
        val item: DownloadItem,
    ) : DownloadUiState

    data class Downloading(
        val item: DownloadItem,
        val progress: DownloadProgress,
    ) : DownloadUiState

    data class Completed(
        val item: DownloadItem,
    ) : DownloadUiState

    data class Error(
        val item: DownloadItem,
        val kind: DownloadErrorKind = DownloadErrorKind.Download,
    ) : DownloadUiState
}

internal val DownloadUiState.label: String
    get() =
        when (this) {
            DownloadUiState.Empty -> "Empty"
            is DownloadUiState.Previewing -> "Previewing"
            is DownloadUiState.Setup -> "Setup"
            is DownloadUiState.Resolving -> "Resolving"
            is DownloadUiState.Ready -> "Ready"
            is DownloadUiState.Downloading -> "Downloading"
            is DownloadUiState.Completed -> "Completed"
            is DownloadUiState.Error -> "Error"
        }

internal val DownloadUiState.itemOrNull: DownloadItem?
    get() =
        when (this) {
            is DownloadUiState.Previewing -> item
            is DownloadUiState.Setup -> item
            is DownloadUiState.Resolving -> item
            is DownloadUiState.Ready -> item
            is DownloadUiState.Downloading -> item
            is DownloadUiState.Completed -> item
            is DownloadUiState.Error -> item
            DownloadUiState.Empty -> null
        }
