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
internal const val DownloadAcknowledgement = "Design preview: Download action received."

internal enum class DownloadMode {
    Video,
    Audio,
}

internal val VideoQualityOptions = listOf("Best available — 2160p", "1440p", "1080p", "720p", "480p")
internal val AudioQualityOptions = listOf("Best available — 251 kbps audio", "160 kbps audio", "128 kbps audio")

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
            thumbnailResource = "thumbnail-normal.svg",
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

    val disabledAction =
        normal.copy(
            id = "disabled-action",
            canDownload = false,
        )
}

private val ReadyDestinations =
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

    data object ShowEmpty : DownloadEvent

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

    var selectedMode by mutableStateOf(DownloadMode.Video)
        private set

    var selectedQualityIndex by mutableStateOf(0)
        private set

    var destination by mutableStateOf("")
        private set

    var readyFeedback by mutableStateOf<String?>(null)
        private set

    val qualityOptions: List<String>
        get() = if (selectedMode == DownloadMode.Video) VideoQualityOptions else AudioQualityOptions

    val selectedQualityLabel: String
        get() = qualityOptions[selectedQualityIndex]

    val downloadEnabled: Boolean
        get() =
            (state as? DownloadUiState.Ready)?.fixture?.let { fixture ->
                fixture.canDownload && isValidYouTubeUrl(fixture.sourceUrl)
            } == true

    private var observedLinkText = ""

    fun observeLinkEdit(text: String): Boolean {
        if (text == observedLinkText) return false

        observedLinkText = text
        validationMessage = text.takeIf { it.isNotBlank() && !isValidYouTubeUrl(it) }?.let { InvalidLinkMessage }
        clearReadySelection()
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
            clearReadySelection()
            state = DownloadUiState.Empty
        }
    }

    fun beginResolution(text: String) {
        val value = text.trim()
        if (!isValidYouTubeUrl(value)) return

        replaceLink(value)
        validationMessage = null
        clearReadySelection()
        state = DownloadUiState.Resolving(DownloadFixtures.normal.copy(sourceUrl = value))
    }

    fun completeResolution(fixture: DownloadFixture) {
        val current = state
        if (current is DownloadUiState.Resolving && current.completesAutomatically && current.fixture == fixture) {
            prepareReady(fixture)
            state = DownloadUiState.Ready(fixture)
        }
    }

    fun selectMode(mode: DownloadMode) {
        if (state !is DownloadUiState.Ready || selectedMode == mode) return

        selectedMode = mode
        selectedQualityIndex = 0
        readyFeedback = null
    }

    fun selectQuality(index: Int) {
        if (state !is DownloadUiState.Ready || index !in qualityOptions.indices || selectedQualityIndex == index) return

        selectedQualityIndex = index
        readyFeedback = null
    }

    fun changeDestination() {
        if (state !is DownloadUiState.Ready) return

        val currentIndex = ReadyDestinations.indexOf(destination)
        destination = ReadyDestinations[(currentIndex + 1).mod(ReadyDestinations.size)]
        readyFeedback = "Save location changed to $destination."
    }

    fun download() {
        if (downloadEnabled) readyFeedback = DownloadAcknowledgement
    }

    fun onEvent(event: DownloadEvent) {
        when (event) {
            DownloadEvent.Reset,
            DownloadEvent.ShowEmpty,
                -> resetToEmpty()

            is DownloadEvent.ShowResolving -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                clearReadySelection()
                state = DownloadUiState.Resolving(event.fixture, completesAutomatically = false)
            }

            is DownloadEvent.ShowReady -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                prepareReady(event.fixture)
                state = DownloadUiState.Ready(event.fixture)
            }
        }
    }

    private fun resetToEmpty() {
        observedLinkText = ""
        linkFieldState.clearText()
        validationMessage = null
        clearReadySelection()
        state = DownloadUiState.Empty
    }

    private fun prepareReady(fixture: DownloadFixture) {
        selectedMode = DownloadMode.Video
        selectedQualityIndex = 0
        destination = fixture.destination
        readyFeedback = null
    }

    private fun clearReadySelection() {
        selectedMode = DownloadMode.Video
        selectedQualityIndex = 0
        destination = ""
        readyFeedback = null
    }

    private fun replaceLink(text: String) {
        observedLinkText = text
        if (linkFieldState.text.toString() != text) {
            linkFieldState.setTextAndPlaceCursorAtEnd(text)
        }
    }
}
