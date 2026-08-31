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

internal enum class DownloadFailureReason {
    Availability,
    Network,
    Storage,
    Processing,
    Tool,
    Unknown,
}

internal enum class DownloadProcessingStage {
    Merging,
    Converting,
    Finalizing,
}

internal const val MAX_TRANSFER_PERCENT = 100

internal sealed interface DownloadProgress {
    data object Preparing : DownloadProgress

    data class Transferring(
        val downloadedBytes: Long,
        val totalBytes: Long? = null,
        val totalIsEstimated: Boolean = false,
        val speedBytesPerSecond: Double? = null,
        val eta: Duration? = null,
        val fraction: Float? = null,
    ) : DownloadProgress {
        init {
            require(downloadedBytes >= 0)
            require(totalBytes == null || totalBytes >= downloadedBytes)
            require(speedBytesPerSecond == null || (speedBytesPerSecond > 0 && speedBytesPerSecond.isFinite()))
            require(eta == null || (eta >= Duration.ZERO && eta.isFinite()))
            require(fraction == null || (fraction.isFinite() && fraction in 0f..1f))
        }

        val percent: Int?
            get() = fraction?.times(MAX_TRANSFER_PERCENT)?.toInt()?.coerceIn(0, MAX_TRANSFER_PERCENT)
    }

    data class Processing(
        val stage: DownloadProcessingStage,
    ) : DownloadProgress
}

internal val fakeProgressSteps =
    listOf(18, 43, 68, 87).map { percent ->
        val total = 138_000_000L
        DownloadProgress.Transferring(
            downloadedBytes = total * percent / MAX_TRANSFER_PERCENT,
            totalBytes = total,
            totalIsEstimated = true,
            speedBytesPerSecond = 5_200_000.0,
            eta = ((MAX_TRANSFER_PERCENT - percent) * 2L / 3L).seconds,
            fraction = percent / MAX_TRANSFER_PERCENT.toFloat(),
        )
    }

internal data class DownloadQuality(
    val label: String,
    val supportingText: String? = null,
    val ytDlpArguments: List<String>,
)

internal data class OriginalAudio(
    val container: String,
    val codec: String,
    val bitRateKilobitsPerSecond: Int?,
) {
    init {
        require(container.isNotBlank())
        require(codec.isNotBlank())
        require(bitRateKilobitsPerSecond == null || bitRateKilobitsPerSecond > 0)
    }

    val description: String
        get() =
            "${codec.toCodecLabel()}/${container.toContainerLabel()} · " +
                (bitRateKilobitsPerSecond?.let { "~$it kbps" } ?: "bitrate unavailable")
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
        DownloadQuality(
            label = "Best · 2160p60 · ~18.4 Mbps",
            supportingText = "AV1/MP4 + Opus/WebM · ~1.65 GB",
            ytDlpArguments = listOf("--format", "401+251"),
        ),
        DownloadQuality(
            label = "1440p60 · ~9.2 Mbps",
            supportingText = "AV1/MP4 + Opus/WebM · ~828 MB",
            ytDlpArguments = listOf("--format", "400+251"),
        ),
        DownloadQuality(
            label = "1080p60 · ~4.8 Mbps",
            supportingText = "AV1/MP4 + Opus/WebM · ~432 MB",
            ytDlpArguments = listOf("--format", "399+251"),
        ),
        DownloadQuality(
            label = "720p60 · ~2.4 Mbps",
            supportingText = "AV1/MP4 + Opus/WebM · ~216 MB",
            ytDlpArguments = listOf("--format", "398+251"),
        ),
        DownloadQuality(
            label = "480p · ~1.1 Mbps",
            supportingText = "VP9/WebM + Opus/WebM · ~99 MB",
            ytDlpArguments = listOf("--format", "244+251"),
        ),
    )

internal fun audioQualityOptions(originalAudio: OriginalAudio?) =
    listOf(
        DownloadQuality(
            label = "Original · ${originalAudio?.description ?: "details unavailable"}",
            supportingText = "No conversion. Fastest option; keeps the source audio unchanged.",
            ytDlpArguments = listOf("--format", "ba"),
        ),
        mp3Quality(),
        mp3Quality(bitRateKilobitsPerSecond = 160),
        mp3Quality(bitRateKilobitsPerSecond = 128),
    )

internal fun String.toContainerLabel(): String =
    when (lowercase(Locale.ROOT)) {
        "webm" -> "WebM"
        "m4a" -> "M4A"
        "mp4" -> "MP4"
        "ogg" -> "Ogg"
        else -> uppercase(Locale.ROOT)
    }

internal fun String.toCodecLabel(): String =
    when (lowercase(Locale.ROOT).substringBefore('.')) {
        "av01" -> "AV1"
        "vp9" -> "VP9"
        "avc1" -> "H.264"
        "hev1", "hvc1" -> "H.265"
        "opus" -> "Opus"
        "mp4a" -> "AAC"
        else -> uppercase(Locale.ROOT)
    }

private fun mp3Quality(bitRateKilobitsPerSecond: Int? = null): DownloadQuality =
    DownloadQuality(
        label =
            bitRateKilobitsPerSecond
                ?.let { "MP3 · $it kbps" }
                ?: "MP3 · High-quality VBR · ~190 kbps",
        supportingText = "Converts to MP3. Quality cannot exceed the source and may be reduced.",
        ytDlpArguments =
            listOf(
                "--format",
                "ba",
                "--extract-audio",
                "--audio-format",
                "mp3",
                "--audio-quality",
                bitRateKilobitsPerSecond?.let { "${it}K" } ?: "2",
            ),
    )

private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
private val YOUTUBE_NO_COOKIE_HOSTS = setOf("youtube-nocookie.com", "www.youtube-nocookie.com")
private val YOUTUBE_VIDEO_PATHS = setOf("shorts", "embed", "live")
private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]+")
private const val YOUTUBE_SHORT_HOST = "youtu.be"
private const val YOUTUBE_CANONICAL_PREFIX = "https://www.youtube.com/watch?v="
private const val PREFIXED_VIDEO_PATH_SEGMENTS = 3

@JvmInline
internal value class YouTubeUrl private constructor(
    val uri: URI,
) {
    override fun toString(): String = uri.toString()

    companion object {
        fun parse(value: String): YouTubeUrl? =
            try {
                URI(value.trim())
                    .takeIf {
                        it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && it.userInfo == null
                    }?.videoId()
                    ?.takeIf(YOUTUBE_VIDEO_ID::matches)
                    ?.let { YouTubeUrl(URI.create(YOUTUBE_CANONICAL_PREFIX + it)) }
            } catch (_: URISyntaxException) {
                null
            }
    }
}

private fun URI.videoId(): String? {
    val normalizedHost = host?.lowercase(Locale.ROOT) ?: return null
    return when {
        normalizedHost == YOUTUBE_SHORT_HOST -> singlePathVideoId()
        normalizedHost in YOUTUBE_HOSTS && rawPath.orEmpty().removeSuffix("/") == "/watch" -> queryParameter("v")
        normalizedHost in YOUTUBE_HOSTS -> prefixedPathVideoId(YOUTUBE_VIDEO_PATHS)
        normalizedHost in YOUTUBE_NO_COOKIE_HOSTS -> prefixedPathVideoId(setOf("embed"))
        else -> null
    }
}

private fun URI.singlePathVideoId(): String? =
    rawPath
        .orEmpty()
        .removeSuffix("/")
        .split('/')
        .takeIf { it.size == 2 && it.first().isEmpty() }
        ?.last()

private fun URI.prefixedPathVideoId(prefixes: Set<String>): String? =
    rawPath
        .orEmpty()
        .removeSuffix("/")
        .split('/')
        .takeIf { it.size == PREFIXED_VIDEO_PATH_SEGMENTS && it.first().isEmpty() && it[1] in prefixes }
        ?.last()

private fun URI.queryParameter(name: String): String? =
    rawQuery
        ?.split('&')
        ?.firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")

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
    val videoQualities: List<DownloadQuality> = videoQualityOptions,
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
            originalAudio = OriginalAudio(container = "webm", codec = "opus", bitRateKilobitsPerSecond = 130),
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

    fun completedFile(item: DownloadItem = normal): Path = item.destination.resolve("downlet-preview.mp4")
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

internal enum class ToolSetupIntent {
    Install,
    Repair,
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
        val intent: ToolSetupIntent = ToolSetupIntent.Install,
    ) : DownloadUiState {
        init {
            require(tools.isNotEmpty())
            require(tools.distinct().size == tools.size)
            require(
                intent == ToolSetupIntent.Install ||
                    phase in setOf(ToolSetupPhase.Installing, ToolSetupPhase.Failed),
            )
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
        val file: Path,
    ) : DownloadUiState

    data class Error(
        val item: DownloadItem,
        val kind: DownloadErrorKind = DownloadErrorKind.Download,
        val reason: DownloadFailureReason = DownloadFailureReason.Unknown,
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
