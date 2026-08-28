package downlet

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

internal const val ManualLinkDebounceMillis = 350L
internal const val FakeResolutionMillis = 550L
internal const val PasteIntentLifetimeMillis = 1_000L
internal const val InvalidLinkMessage = "Enter a valid YouTube link."

private val YoutubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

internal fun isValidYouTubeUrl(value: String): Boolean =
    try {
        val uri = URI(value.trim())
        uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                uri.host?.lowercase(Locale.ROOT) in YoutubeHosts
    } catch (_: URISyntaxException) {
        false
    }

internal sealed interface LinkSubmission {
    data object None : LinkSubmission

    data object ResolveImmediately : LinkSubmission

    data class ResolveAfter(val delayMillis: Long) : LinkSubmission
}

internal fun linkSubmissionFor(value: String, pasteIntent: Boolean): LinkSubmission =
    when {
        !isValidYouTubeUrl(value) -> LinkSubmission.None
        pasteIntent -> LinkSubmission.ResolveImmediately
        else -> LinkSubmission.ResolveAfter(ManualLinkDebounceMillis)
    }

internal enum class DownletTheme {
    Light,
    Dark,
}

internal sealed interface FakeDownloadOutcome {
    data object Success : FakeDownloadOutcome

    data class Failure(val atPercent: Int) : FakeDownloadOutcome {
        init {
            require(atPercent in 1..99)
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
    val thumbnailResource: String?,
    val outcome: FakeDownloadOutcome = FakeDownloadOutcome.Success,
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
            thumbnailResource = "thumbnail-normal.png",
        )

    val longTitle =
        normal.copy(
            id = "long-title",
            title =
                "A deliberately long media title that remains deterministic while exercising the future two-line layout",
        )

    val missingThumbnail =
        normal.copy(
            id = "missing-thumbnail",
            thumbnailResource = null,
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
}

internal sealed interface DownloadUiState {
    data object Empty : DownloadUiState

    data class Resolving(
        val fixture: DownloadFixture,
        val completesAutomatically: Boolean = true,
    ) : DownloadUiState

    data class Ready(val fixture: DownloadFixture) : DownloadUiState

    data class Downloading(
        val fixture: DownloadFixture,
        val progressPercent: Int,
    ) : DownloadUiState {
        init {
            require(progressPercent in 0..100)
            val outcome = fixture.outcome
            if (outcome is FakeDownloadOutcome.Failure) {
                require(progressPercent <= outcome.atPercent)
            }
        }
    }

    data class Completed(val fixture: DownloadFixture) : DownloadUiState {
        init {
            require(fixture.outcome == FakeDownloadOutcome.Success)
        }
    }

    data class Error(val fixture: DownloadFixture) : DownloadUiState {
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

    data class ShowResolving(val fixture: DownloadFixture = DownloadFixtures.normal) : DownloadEvent

    data class ShowReady(val fixture: DownloadFixture = DownloadFixtures.normal) : DownloadEvent
}

@Stable
internal class DownloadStateHolder(initialState: DownloadUiState = DownloadUiState.Empty) {
    val linkFieldState = TextFieldState()

    var state by mutableStateOf(initialState)
        private set

    var validationMessage by mutableStateOf<String?>(null)
        private set

    private var observedLinkText = ""

    fun observeLinkEdit(text: String): Boolean {
        if (text == observedLinkText) return false

        observedLinkText = text
        validationMessage = text.takeIf { it.isNotBlank() && !isValidYouTubeUrl(it) }?.let { InvalidLinkMessage }
        state = DownloadUiState.Empty
        return true
    }

    fun pasteLink(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return

        replaceLink(value)
        if (isValidYouTubeUrl(value)) {
            beginResolution(value)
        } else {
            validationMessage = InvalidLinkMessage
            state = DownloadUiState.Empty
        }
    }

    fun beginResolution(text: String) {
        val value = text.trim()
        if (!isValidYouTubeUrl(value)) return

        replaceLink(value)
        validationMessage = null
        state = DownloadUiState.Resolving(DownloadFixtures.normal.copy(sourceUrl = value))
    }

    fun completeResolution(fixture: DownloadFixture) {
        val current = state
        if (current is DownloadUiState.Resolving && current.completesAutomatically && current.fixture == fixture) {
            state = DownloadUiState.Ready(fixture)
        }
    }

    fun onEvent(event: DownloadEvent) {
        when (event) {
            DownloadEvent.Reset -> {
                observedLinkText = ""
                linkFieldState.clearText()
                validationMessage = null
                state = DownloadUiState.Empty
            }

            is DownloadEvent.ShowResolving -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                state = DownloadUiState.Resolving(event.fixture, completesAutomatically = false)
            }

            is DownloadEvent.ShowReady -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                state = DownloadUiState.Ready(event.fixture)
            }
        }
    }

    private fun replaceLink(text: String) {
        observedLinkText = text
        if (linkFieldState.text.toString() != text) {
            linkFieldState.setTextAndPlaceCursorAtEnd(text)
        }
    }
}
