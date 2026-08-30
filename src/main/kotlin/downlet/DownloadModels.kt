package downlet

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

internal const val MANUAL_LINK_DEBOUNCE_MILLIS = 350L
internal const val FAKE_RESOLUTION_MILLIS = 550L
internal const val PASTE_INTENT_LIFETIME_MILLIS = 1_000L
internal const val FAKE_PROGRESS_INTERVAL_MILLIS = 350L
internal const val COMPLETE_PROGRESS_PERCENT = 100
internal const val INVALID_LINK_MESSAGE = "Enter a valid YouTube link."
internal const val DOWNLOAD_UNAVAILABLE_MESSAGE = "Download is unavailable for this item."
internal const val TOOL_SETUP_FAILURE_MESSAGE =
    "Couldn't install the required tools. Check your connection and try again."
internal const val TOOL_SETUP_CONSENT_TEXT =
    "I choose to download these tools and accept the tool terms."
internal const val DOWNLOAD_AUTHORIZATION_TEXT =
    "I am authorized to download this media and accept responsibility for this download."
internal const val OPEN_FOLDER_ACKNOWLEDGEMENT = "Folder opening is unavailable in this design preview."
internal const val OPEN_FOLDER_FAILURE_MESSAGE = "Couldn't open the download folder."
internal val fakeProgressSteps = listOf(18, 43, 68, 87, COMPLETE_PROGRESS_PERCENT)

internal enum class DownloadMode {
    Video,
    Audio,
}

internal enum class DownloadErrorKind {
    Resolution,
    Download,
}

internal val videoQualityOptions = listOf("Best available — 2160p", "1440p", "1080p", "720p", "480p")
internal val audioQualityOptions = listOf("Best available — 251 kbps audio", "160 kbps audio", "128 kbps audio")

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

internal sealed interface LinkSubmission {
    data object None : LinkSubmission

    data object ResolveImmediately : LinkSubmission

    data class ResolveAfter(
        val delayMillis: Long,
    ) : LinkSubmission
}

internal fun linkSubmissionFor(
    value: String,
    pasteIntent: Boolean,
): LinkSubmission =
    when {
        !isValidYouTubeUrl(value) -> LinkSubmission.None
        pasteIntent -> LinkSubmission.ResolveImmediately
        else -> LinkSubmission.ResolveAfter(MANUAL_LINK_DEBOUNCE_MILLIS)
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
        val tools: List<String>,
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

internal sealed interface DownloadEvent {
    data object Reset : DownloadEvent

    data object ShowEmpty : DownloadEvent

    data class ShowPreviewing(
        val fixture: DownloadFixture = DownloadFixtures.normal,
    ) : DownloadEvent

    data class ShowResolving(
        val fixture: DownloadFixture = DownloadFixtures.normal,
    ) : DownloadEvent

    data class ShowSetup(
        val fixture: DownloadFixture = DownloadFixtures.normal,
        val tools: List<String> = listOf("yt-dlp", "FFmpeg"),
        val installing: Boolean = false,
        val failed: Boolean = false,
    ) : DownloadEvent

    data class ShowReady(
        val fixture: DownloadFixture = DownloadFixtures.normal,
    ) : DownloadEvent

    data class ShowDownloading(
        val fixture: DownloadFixture = DownloadFixtures.normal,
        val progressPercent: Int = 43,
    ) : DownloadEvent

    data class ShowCompleted(
        val fixture: DownloadFixture = DownloadFixtures.normal,
    ) : DownloadEvent

    data class ShowError(
        val fixture: DownloadFixture = DownloadFixtures.failure,
    ) : DownloadEvent

    data object ShowInvalidInput : DownloadEvent
}
