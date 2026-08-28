package downlet

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

internal const val MANUAL_LINK_DEBOUNCE_MILLIS = 350L
internal const val FAKE_RESOLUTION_MILLIS = 550L
internal const val PASTE_INTENT_LIFETIME_MILLIS = 1_000L
internal const val INVALID_LINK_MESSAGE = "Enter a valid YouTube link."
internal const val DOWNLOAD_ACKNOWLEDGEMENT = "Design preview: Download action received."
internal const val DOWNLOAD_UNAVAILABLE_MESSAGE = "Download is unavailable for this item."

internal enum class DownloadMode {
    Video,
    Audio,
}

internal val videoQualityOptions = listOf("Best available — 2160p", "1440p", "1080p", "720p", "480p")
internal val audioQualityOptions = listOf("Best available — 251 kbps audio", "160 kbps audio", "128 kbps audio")

private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
private const val MAX_INCOMPLETE_PROGRESS = 99
private const val MAX_PROGRESS = 100

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

internal data class DownloadFixture(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val channel: String,
    val duration: String,
    val destination: String,
    val thumbnailAvailable: Boolean = true,
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
            require(progressPercent in 0..MAX_PROGRESS)
            val outcome = fixture.outcome
            if (outcome is FakeDownloadOutcome.Failure) {
                require(progressPercent <= outcome.atPercent)
            }
        }
    }

    data class Completed(
        val fixture: DownloadFixture,
    ) : DownloadUiState {
        init {
            require(fixture.outcome == FakeDownloadOutcome.Success)
        }
    }

    data class Error(
        val fixture: DownloadFixture,
    ) : DownloadUiState {
        init {
            require(fixture.outcome is FakeDownloadOutcome.Failure)
        }
    }
}

internal val DownloadUiState.label: String
    get() =
        when (this) {
            DownloadUiState.Empty -> "Empty"
            is DownloadUiState.Resolving -> "Resolving"
            is DownloadUiState.Ready -> "Ready"
            is DownloadUiState.Downloading -> "Downloading"
            is DownloadUiState.Completed -> "Completed"
            is DownloadUiState.Error -> "Error"
        }

internal sealed interface DownloadEvent {
    data object Reset : DownloadEvent

    data object ShowEmpty : DownloadEvent

    data class ShowResolving(
        val fixture: DownloadFixture = DownloadFixtures.normal,
    ) : DownloadEvent

    data class ShowReady(
        val fixture: DownloadFixture = DownloadFixtures.normal,
    ) : DownloadEvent
}
