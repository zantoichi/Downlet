package downlet

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

internal const val MANUAL_LINK_DEBOUNCE_MILLIS = 350L
internal const val FAKE_RESOLUTION_MILLIS = 550L
internal const val PASTE_INTENT_LIFETIME_MILLIS = 1_000L
internal const val FAKE_PROGRESS_INTERVAL_MILLIS = 350L
internal const val COMPLETE_PROGRESS_PERCENT = 100
private const val YT_DLP_ESTIMATED_DOWNLOAD_MEGABYTES = 17
private const val FFMPEG_ESTIMATED_DOWNLOAD_MEGABYTES = 106
internal val fakeProgressSteps = listOf(18, 43, 68, 87, COMPLETE_PROGRESS_PERCENT)

internal enum class DownloadMode {
    Video,
    Audio,
}

internal enum class DownloadErrorKind {
    Resolution,
    Download,
}

internal data class DownloadQuality(
    val label: String,
    val ytDlpArguments: List<String>,
)

internal enum class DownloadTool(
    val label: String,
    val estimatedDownloadMegabytes: Int,
) {
    YtDlp("yt-dlp", YT_DLP_ESTIMATED_DOWNLOAD_MEGABYTES),
    Ffmpeg("FFmpeg", FFMPEG_ESTIMATED_DOWNLOAD_MEGABYTES),
}

internal val videoQualityOptions =
    listOf(
        DownloadQuality("Best available — 2160p", listOf("--format", "bv*[height<=2160]+ba/b[height<=2160]")),
        DownloadQuality("1440p", listOf("--format", "bv*[height<=1440]+ba/b[height<=1440]")),
        DownloadQuality("1080p", listOf("--format", "bv*[height<=1080]+ba/b[height<=1080]")),
        DownloadQuality("720p", listOf("--format", "bv*[height<=720]+ba/b[height<=720]")),
        DownloadQuality("480p", listOf("--format", "bv*[height<=480]+ba/b[height<=480]")),
    )
internal val audioQualityOptions =
    listOf(
        DownloadQuality(
            "Best available — 251 kbps audio",
            listOf("--format", "ba/b", "--extract-audio", "--audio-format", "mp3", "--audio-quality", "0"),
        ),
        DownloadQuality(
            "160 kbps audio",
            listOf("--format", "ba/b", "--extract-audio", "--audio-format", "mp3", "--audio-quality", "160K"),
        ),
        DownloadQuality(
            "128 kbps audio",
            listOf("--format", "ba/b", "--extract-audio", "--audio-format", "mp3", "--audio-quality", "128K"),
        ),
    )

private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
private const val MAX_INCOMPLETE_PROGRESS = 99

internal fun isValidYouTubeUrl(value: String): Boolean =
    try {
        val uri = URI(value.trim())
        uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
            uri.host?.lowercase(Locale.ROOT) in YOUTUBE_HOSTS
    } catch (_: URISyntaxException) {
        false
    }

internal fun linkResolutionDelayMillis(
    previousValue: String,
    value: String,
    explicitPaste: Boolean,
): Long? =
    when {
        !isValidYouTubeUrl(value) -> null
        explicitPaste || insertedCharacterCount(previousValue, value) > 1 -> 0L
        else -> MANUAL_LINK_DEBOUNCE_MILLIS
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

internal sealed interface FakeDownloadOutcome {
    data object Success : FakeDownloadOutcome

    data class Failure(
        val atPercent: Int,
    ) : FakeDownloadOutcome {
        init {
            require(atPercent in 1..MAX_INCOMPLETE_PROGRESS)
        }
    }
}

internal class ThumbnailData(
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is ThumbnailData && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}

internal data class DownloadFixture(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val channel: String,
    val duration: String,
    val destination: String,
    val thumbnailAvailable: Boolean = true,
    val thumbnailData: ThumbnailData? = null,
    val outcome: FakeDownloadOutcome = FakeDownloadOutcome.Success,
    val canDownload: Boolean = true,
)

internal object DownloadFixtures {
    val normal =
        DownloadFixture(
            id = "normal",
            sourceUrl = "https://www.youtube.com/watch?v=quiet-transfer",
            title = "A calm walk through the city after rain",
            channel = "North Window",
            duration = "12:34",
            destination = "Downloads",
        )

    val longTitle =
        normal.copy(
            id = "long-title",
            title =
                "A deliberately long media title that remains deterministic while exercising " +
                    "the future two-line layout",
        )

    val missingThumbnail =
        normal.copy(
            id = "missing-thumbnail",
            thumbnailAvailable = false,
        )

    val longDestination =
        normal.copy(
            id = "long-destination",
            destination = "C:\\Users\\Demo\\Videos\\Reference Material\\Long Destination Folder\\Downloads",
        )

    val failure =
        normal.copy(
            id = "failure",
            outcome = FakeDownloadOutcome.Failure(atPercent = 68),
        )

    val disabledAction =
        normal.copy(
            id = "disabled-action",
            canDownload = false,
        )
}

internal val readyDestinations =
    listOf(
        DownloadFixtures.longDestination.destination,
        "D:\\Media\\Downloads",
        DownloadFixtures.normal.destination,
    )

internal sealed interface DownloadUiState {
    data object Empty : DownloadUiState

    data class Previewing(
        val fixture: DownloadFixture,
        val completesAutomatically: Boolean = true,
    ) : DownloadUiState

    data class Setup(
        val fixture: DownloadFixture,
        val tools: List<DownloadTool>,
        val installing: Boolean = false,
        val failed: Boolean = false,
    ) : DownloadUiState {
        init {
            require(tools.isNotEmpty())
        }
    }

    data class Resolving(
        val fixture: DownloadFixture,
        val completesAutomatically: Boolean = true,
    ) : DownloadUiState

    data class Ready(
        val fixture: DownloadFixture,
    ) : DownloadUiState

    data class Downloading(
        val fixture: DownloadFixture,
        val progressPercent: Int,
    ) : DownloadUiState {
        init {
            require(progressPercent in 0..COMPLETE_PROGRESS_PERCENT)
        }
    }

    data class Completed(
        val fixture: DownloadFixture,
    ) : DownloadUiState

    data class Error(
        val fixture: DownloadFixture,
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

internal val DownloadUiState.fixtureOrNull: DownloadFixture?
    get() =
        when (this) {
            is DownloadUiState.Previewing -> fixture
            is DownloadUiState.Setup -> fixture
            is DownloadUiState.Resolving -> fixture
            is DownloadUiState.Ready -> fixture
            is DownloadUiState.Downloading -> fixture
            is DownloadUiState.Completed -> fixture
            is DownloadUiState.Error -> fixture
            DownloadUiState.Empty -> null
        }
