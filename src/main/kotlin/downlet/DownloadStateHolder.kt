package downlet

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
@Suppress("TooManyFunctions")
internal class DownloadStateHolder(
    scope: CoroutineScope,
    private val runtime: DownloadRuntime,
) {
    val linkFieldState = TextFieldState()

    var state by mutableStateOf<DownloadUiState>(DownloadUiState.Empty)
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

    var completedFeedback by mutableStateOf<String?>(null)
        private set

    var toolSetupAccepted by mutableStateOf(false)
        private set

    var downloadAuthorizationAccepted by mutableStateOf(false)
        private set

    var showingLegalDetails by mutableStateOf(false)
        private set

    var linkFocusRequest by mutableStateOf(0)
        private set

    val qualityOptions: List<String>
        get() = availableQualities.map(DownloadQuality::label)

    val selectedQualityLabel: String
        get() = selectedQuality.label

    val downloadEnabled: Boolean
        get() =
            (state as? DownloadUiState.Ready)?.fixture?.let { fixture ->
                fixture.canDownload && downloadAuthorizationAccepted && isValidYouTubeUrl(fixture.sourceUrl)
            } == true

    val toolSetupEnabled: Boolean
        get() = (state as? DownloadUiState.Setup)?.let { !it.installing && toolSetupAccepted } == true

    val readyStatus: String?
        get() =
            (state as? DownloadUiState.Ready)?.fixture?.let { fixture ->
                if (fixture.canDownload) readyFeedback else ProductCopy.DOWNLOAD_UNAVAILABLE_MESSAGE
            }

    private val stateContext = scope.coroutineContext.minusKey(Job)
    private val holderJob = SupervisorJob(scope.coroutineContext[Job])
    private val holderScope = CoroutineScope(stateContext + holderJob)
    private var downloadGeneration = 0L
    private var metadataJob: Job? = null
    private var downloadJob: Job? = null
    private var observedLinkText = ""

    private val availableQualities: List<DownloadQuality>
        get() = if (selectedMode == DownloadMode.Video) videoQualityOptions else audioQualityOptions

    private val selectedQuality: DownloadQuality
        get() = availableQualities[selectedQualityIndex]

    fun observeLinkEdit(text: String): Boolean {
        if (text == observedLinkText) return false

        observedLinkText = text
        validationMessage =
            text
                .takeIf { it.isNotBlank() && !isValidYouTubeUrl(it) }
                ?.let { ProductCopy.INVALID_LINK_MESSAGE }
        clearReadySelection()
        cancelActiveWork()
        transitionTo(DownloadUiState.Empty)
        return true
    }

    fun beginResolution(text: String) {
        val value = text.trim()
        if (!isValidYouTubeUrl(value)) return

        replaceLink(value)
        validationMessage = null
        clearReadySelection()
        cancelActiveWork()
        transitionTo(DownloadUiState.Empty)
        startPreview(DownloadFixtures.normal.copy(sourceUrl = value))
    }

    fun updateToolSetupConsent(accepted: Boolean) {
        val setup = state as? DownloadUiState.Setup ?: return
        if (setup.installing) return

        toolSetupAccepted = accepted
    }

    @Suppress("ThrowsCount")
    fun installTools() {
        val setup = state as? DownloadUiState.Setup ?: return
        if (!toolSetupEnabled) return

        cancelActiveWork()
        transitionTo(setup.copy(installing = true, failed = false))
        metadataJob =
            holderScope.launch {
                try {
                    runtime.installMissingTools()
                    if (runtime.missingTools().isNotEmpty()) throw DownloadRuntimeException()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (isCurrentInstallingSetup(setup)) {
                        transitionTo(setup.copy(failed = true))
                    }
                    return@launch
                }
                if (isCurrentInstallingSetup(setup)) resolve(setup.fixture)
            }
    }

    fun updateDownloadAuthorization(accepted: Boolean) {
        if (state !is DownloadUiState.Ready) return

        downloadAuthorizationAccepted = accepted
        readyFeedback = null
    }

    fun showLegalDetails() {
        if (state is DownloadUiState.Setup || state is DownloadUiState.Ready) showingLegalDetails = true
    }

    fun hideLegalDetails() {
        showingLegalDetails = false
    }

    internal fun completeResolution(fixture: DownloadFixture) {
        if (!isCurrentResolution(fixture)) return
        prepareReady(fixture)
        transitionTo(DownloadUiState.Ready(fixture))
    }

    fun selectMode(mode: DownloadMode) {
        if (state !is DownloadUiState.Ready || selectedMode == mode) return

        selectedMode = mode
        selectedQualityIndex = 0
        readyFeedback = null
    }

    fun selectQuality(index: Int) {
        if (state !is DownloadUiState.Ready || index !in availableQualities.indices || selectedQualityIndex == index) {
            return
        }

        selectedQualityIndex = index
        readyFeedback = null
    }

    fun changeDestination() {
        if (state !is DownloadUiState.Ready) return

        destination = runtime.chooseDestination(destination) ?: return
        readyFeedback = "Save location changed to $destination."
    }

    fun download() {
        val fixture = (state as? DownloadUiState.Ready)?.fixture ?: return
        if (!downloadEnabled) return

        startDownload(fixture)
    }

    fun cancelDownload() {
        val fixture = (state as? DownloadUiState.Downloading)?.fixture ?: return
        cancelActiveWork()
        transitionTo(DownloadUiState.Ready(fixture))
    }

    fun retryDownload() {
        val error = state as? DownloadUiState.Error ?: return
        if (error.kind == DownloadErrorKind.Resolution) {
            beginResolution(error.fixture.sourceUrl)
        } else {
            startDownload(error.fixture)
        }
    }

    fun openFolder() {
        if (state is DownloadUiState.Completed) {
            completedFeedback = runtime.openDestination(destination)
        }
    }

    fun downloadAnother() {
        if (state !is DownloadUiState.Completed) return

        cancelActiveWork()
        observedLinkText = ""
        linkFieldState.clearText()
        validationMessage = null
        clearReadySelection()
        requestLinkFocus()
        transitionTo(DownloadUiState.Empty)
    }

    internal fun showDesignState(
        state: DownloadUiState,
        linkText: String = state.fixtureOrNull?.sourceUrl.orEmpty(),
        validationMessage: String? = null,
    ) {
        cancelActiveWork()
        observedLinkText = linkText
        if (linkText.isEmpty()) {
            linkFieldState.clearText()
        } else {
            linkFieldState.setTextAndPlaceCursorAtEnd(linkText)
        }
        this.validationMessage = validationMessage
        clearReadySelection()
        when (state) {
            is DownloadUiState.Ready -> prepareReady(state.fixture)
            is DownloadUiState.Downloading -> prepareReady(state.fixture)
            is DownloadUiState.Completed -> prepareReady(state.fixture)
            is DownloadUiState.Error -> prepareReady(state.fixture)
            else -> Unit
        }
        if (state == DownloadUiState.Empty && linkText.isEmpty()) requestLinkFocus()
        transitionTo(state)
    }

    fun close() {
        cancelActiveWork()
        holderJob.cancel()
    }

    private fun startPreview(requestFixture: DownloadFixture) {
        if (state !is DownloadUiState.Empty || observedLinkText != requestFixture.sourceUrl) return

        transitionTo(DownloadUiState.Previewing(requestFixture))
        metadataJob =
            holderScope.launch {
                val previewFixture =
                    try {
                        runtime.preview(requestFixture.sourceUrl)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        if (isCurrentPreview(requestFixture)) {
                            clearReadySelection()
                            transitionTo(DownloadUiState.Error(requestFixture, DownloadErrorKind.Resolution))
                        }
                        return@launch
                    }
                if (!isCurrentPreview(requestFixture)) return@launch

                val missingTools = runtime.missingTools()
                if (missingTools.isNotEmpty()) {
                    clearReadySelection()
                    transitionTo(DownloadUiState.Setup(previewFixture, missingTools))
                    return@launch
                }
                resolve(previewFixture)
            }
    }

    private suspend fun resolve(requestFixture: DownloadFixture) {
        if (!canResolve(requestFixture)) return

        transitionTo(DownloadUiState.Resolving(requestFixture))
        try {
            val resolvedFixture = runtime.resolve(requestFixture.sourceUrl).withPreviewFrom(requestFixture)
            if (isCurrentResolution(requestFixture)) {
                prepareReady(resolvedFixture)
                transitionTo(DownloadUiState.Ready(resolvedFixture))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (isCurrentResolution(requestFixture)) {
                clearReadySelection()
                transitionTo(DownloadUiState.Error(requestFixture, DownloadErrorKind.Resolution))
            }
        }
    }

    private fun startDownload(fixture: DownloadFixture) {
        val canStart =
            state == DownloadUiState.Ready(fixture) ||
                (state as? DownloadUiState.Error)?.let {
                    it.fixture == fixture && it.kind == DownloadErrorKind.Download
                } == true
        if (!canStart) return

        downloadJob?.cancel()
        downloadGeneration += 1
        val generation = downloadGeneration
        val request = DownloadRequest(fixture, selectedQuality, destination)
        readyFeedback = null
        completedFeedback = null
        transitionTo(DownloadUiState.Downloading(fixture, progressPercent = 0))
        downloadJob =
            holderScope.launch {
                try {
                    runtime.download(request) { progress ->
                        withContext(stateContext) {
                            if (isCurrentDownload(fixture, generation)) {
                                transitionTo(DownloadUiState.Downloading(fixture, progress))
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (isCurrentDownload(fixture, generation)) {
                        downloadJob = null
                        transitionTo(DownloadUiState.Error(fixture))
                    }
                    return@launch
                }
                if (isCurrentDownload(fixture, generation)) {
                    downloadJob = null
                    completedFeedback = null
                    transitionTo(DownloadUiState.Completed(fixture))
                }
            }
    }

    private fun isCurrentPreview(fixture: DownloadFixture): Boolean =
        (state as? DownloadUiState.Previewing)?.let {
            it.completesAutomatically && it.fixture == fixture && observedLinkText == fixture.sourceUrl
        } == true

    private fun isCurrentInstallingSetup(setup: DownloadUiState.Setup): Boolean =
        (state as? DownloadUiState.Setup)?.let {
            it.fixture == setup.fixture && it.tools == setup.tools && it.installing
        } == true

    private fun canResolve(fixture: DownloadFixture): Boolean =
        when (val current = state) {
            is DownloadUiState.Previewing -> {
                current.completesAutomatically && current.fixture.sourceUrl == fixture.sourceUrl
            }

            is DownloadUiState.Setup -> {
                current.installing && current.fixture == fixture
            }

            else -> {
                false
            }
        }

    private fun isCurrentResolution(fixture: DownloadFixture): Boolean =
        (state as? DownloadUiState.Resolving)?.let {
            it.completesAutomatically && it.fixture == fixture
        } == true

    private fun isCurrentDownload(
        fixture: DownloadFixture,
        generation: Long,
    ): Boolean = generation == downloadGeneration && (state as? DownloadUiState.Downloading)?.fixture == fixture

    private fun transitionTo(nextState: DownloadUiState) {
        showingLegalDetails = false
        state = nextState
    }

    private fun prepareReady(fixture: DownloadFixture) {
        selectedMode = DownloadMode.Video
        selectedQualityIndex = 0
        destination = fixture.destination
        readyFeedback = null
        completedFeedback = null
        toolSetupAccepted = false
        downloadAuthorizationAccepted = false
    }

    private fun clearReadySelection() {
        selectedMode = DownloadMode.Video
        selectedQualityIndex = 0
        destination = ""
        readyFeedback = null
        completedFeedback = null
        toolSetupAccepted = false
        downloadAuthorizationAccepted = false
    }

    private fun cancelActiveWork() {
        downloadGeneration += 1
        runtime.cancel()
        metadataJob?.cancel()
        metadataJob = null
        downloadJob?.cancel()
        downloadJob = null
    }

    private fun requestLinkFocus() {
        linkFocusRequest += 1
    }

    private fun replaceLink(text: String) {
        observedLinkText = text
        if (linkFieldState.text.toString() != text) {
            linkFieldState.setTextAndPlaceCursorAtEnd(text)
        }
    }
}

private fun DownloadFixture.withPreviewFrom(preview: DownloadFixture): DownloadFixture =
    copy(
        thumbnailAvailable = thumbnailAvailable || preview.thumbnailAvailable,
        thumbnailData = thumbnailData ?: preview.thumbnailData,
    )
