package downlet

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
internal class DownloadStateHolder(
    initialState: DownloadUiState = DownloadUiState.Empty,
) {
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
        get() = if (selectedMode == DownloadMode.Video) videoQualityOptions else audioQualityOptions

    val selectedQualityLabel: String
        get() = qualityOptions[selectedQualityIndex]

    val downloadEnabled: Boolean
        get() =
            (state as? DownloadUiState.Ready)?.fixture?.let { fixture ->
                fixture.canDownload && isValidYouTubeUrl(fixture.sourceUrl)
            } == true

    val readyStatus: String?
        get() =
            (state as? DownloadUiState.Ready)?.fixture?.let { fixture ->
                if (fixture.canDownload) readyFeedback else DOWNLOAD_UNAVAILABLE_MESSAGE
            }

    private var observedLinkText = ""

    fun observeLinkEdit(text: String): Boolean {
        if (text == observedLinkText) return false

        observedLinkText = text
        validationMessage = text.takeIf { it.isNotBlank() && !isValidYouTubeUrl(it) }?.let { INVALID_LINK_MESSAGE }
        clearReadySelection()
        state = DownloadUiState.Empty
        return true
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

        val currentIndex = readyDestinations.indexOf(destination)
        destination = readyDestinations[(currentIndex + 1).mod(readyDestinations.size)]
        readyFeedback = "Save location changed to $destination."
    }

    fun download() {
        if (downloadEnabled) readyFeedback = DOWNLOAD_ACKNOWLEDGEMENT
    }

    fun onEvent(event: DownloadEvent) {
        when (event) {
            DownloadEvent.Reset,
            DownloadEvent.ShowEmpty,
            -> {
                observedLinkText = ""
                linkFieldState.clearText()
                validationMessage = null
                clearReadySelection()
                state = DownloadUiState.Empty
            }

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
