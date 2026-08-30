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
import java.nio.file.Path

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

    var readyFeedback by mutableStateOf<String?>(null)
        private set

    var completedFeedback by mutableStateOf<String?>(null)
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

    val destination: Path?
        get() {
            val current = state
            return current.itemOrNull?.destination?.takeUnless {
                current is DownloadUiState.Error && current.kind == DownloadErrorKind.Resolution
            }
        }

    val toolSetupAccepted: Boolean
        get() {
            val phase = (state as? DownloadUiState.Setup)?.phase ?: return false
            return phase != ToolSetupPhase.AwaitingConsent
        }

    val downloadEnabled: Boolean
        get() = state is DownloadUiState.Ready && downloadAuthorizationAccepted

    val toolSetupEnabled: Boolean
        get() =
            (state as? DownloadUiState.Setup)?.let {
                it.phase == ToolSetupPhase.ReadyToInstall || it.phase == ToolSetupPhase.Failed
            } == true

    val readyStatus: String?
        get() = (state as? DownloadUiState.Ready)?.let { readyFeedback }

    private val stateContext = scope.coroutineContext.minusKey(Job)
    private val holderJob = SupervisorJob(scope.coroutineContext[Job])
    private val holderScope = CoroutineScope(stateContext + holderJob)
    private var metadataGeneration = 0L
    private var downloadGeneration = 0L
    private var metadataJob: Job? = null
    private var downloadJob: Job? = null
    private var observedLinkText = ""

    private val availableQualities: List<DownloadQuality>
        get() =
            if (selectedMode == DownloadMode.Video) {
                videoQualityOptions
            } else {
                audioQualityOptions(state.itemOrNull?.originalAudio)
            }

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
        val source = YouTubeUrl.parse(text) ?: return
        val value = source.toString()

        replaceLink(value)
        validationMessage = null
        clearReadySelection()
        cancelActiveWork()
        transitionTo(DownloadUiState.Empty)
        startPreview(DownloadFixtures.normal.copy(source = source))
    }

    fun updateToolSetupConsent(accepted: Boolean) {
        val setup = state as? DownloadUiState.Setup ?: return
        if (setup.phase == ToolSetupPhase.Installing) return

        state =
            setup.copy(
                phase = if (accepted) ToolSetupPhase.ReadyToInstall else ToolSetupPhase.AwaitingConsent,
            )
    }

    @Suppress("ThrowsCount")
    fun installTools() {
        val setup = state as? DownloadUiState.Setup ?: return
        if (!toolSetupEnabled) return

        cancelActiveWork()
        val generation = metadataGeneration
        transitionTo(setup.copy(phase = ToolSetupPhase.Installing))
        metadataJob =
            holderScope.launch {
                try {
                    runtime.installMissingTools()
                    if (runtime.missingTools().isNotEmpty()) throw DownloadRuntimeException()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (isCurrentInstallingSetup(setup, generation)) {
                        transitionTo(setup.copy(phase = ToolSetupPhase.Failed))
                    }
                    return@launch
                }
                if (isCurrentInstallingSetup(setup, generation)) resolve(setup.item, generation)
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
        val ready = state as? DownloadUiState.Ready ?: return

        val destination = runtime.chooseDestination(ready.item.destination) ?: return
        state = ready.copy(item = ready.item.copy(destination = destination))
        readyFeedback = "Save location changed to $destination."
    }

    fun download() {
        val item = (state as? DownloadUiState.Ready)?.item ?: return
        if (!downloadEnabled) return

        startDownload(item)
    }

    fun cancelDownload() {
        val item = (state as? DownloadUiState.Downloading)?.item ?: return
        cancelActiveWork()
        transitionTo(DownloadUiState.Ready(item))
    }

    fun retryDownload() {
        val error = state as? DownloadUiState.Error ?: return
        if (error.kind == DownloadErrorKind.Resolution) {
            beginResolution(error.item.source.toString())
        } else {
            startDownload(error.item)
        }
    }

    fun openFolder() {
        val completed = state as? DownloadUiState.Completed ?: return
        completedFeedback = runtime.openDestination(completed.item.destination)
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
        linkText: String =
            state.itemOrNull
                ?.source
                ?.toString()
                .orEmpty(),
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
            is DownloadUiState.Ready -> prepareReady()
            is DownloadUiState.Downloading -> prepareReady()
            is DownloadUiState.Completed -> prepareReady()
            is DownloadUiState.Error -> prepareReady()
            else -> Unit
        }
        if (state == DownloadUiState.Empty && linkText.isEmpty()) requestLinkFocus()
        transitionTo(state)
    }

    fun close() {
        cancelActiveWork()
        holderJob.cancel()
    }

    private fun startPreview(requestItem: DownloadItem) {
        if (state !is DownloadUiState.Empty || observedLinkText != requestItem.source.toString()) return

        val generation = metadataGeneration
        transitionTo(DownloadUiState.Previewing(requestItem))
        metadataJob =
            holderScope.launch {
                val previewItem =
                    try {
                        runtime.preview(requestItem.source)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        if (isCurrentPreview(requestItem, generation)) {
                            clearReadySelection()
                            transitionTo(DownloadUiState.Error(requestItem, DownloadErrorKind.Resolution))
                        }
                        return@launch
                    }
                if (!isCurrentPreview(requestItem, generation)) return@launch

                val missingTools = runtime.missingTools()
                if (missingTools.isNotEmpty()) {
                    clearReadySelection()
                    transitionTo(DownloadUiState.Setup(previewItem, missingTools))
                    return@launch
                }
                resolve(previewItem, generation)
            }
    }

    private suspend fun resolve(
        requestItem: DownloadItem,
        generation: Long,
    ) {
        if (!canResolve(requestItem, generation)) return

        transitionTo(DownloadUiState.Resolving(requestItem))
        try {
            val resolvedItem = runtime.resolve(requestItem.source).withPreviewFrom(requestItem)
            if (isCurrentResolution(requestItem, generation)) {
                prepareReady()
                transitionTo(DownloadUiState.Ready(resolvedItem))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (isCurrentResolution(requestItem, generation)) {
                clearReadySelection()
                transitionTo(DownloadUiState.Error(requestItem, DownloadErrorKind.Resolution))
            }
        }
    }

    private fun startDownload(item: DownloadItem) {
        val canStart =
            state == DownloadUiState.Ready(item) ||
                (state as? DownloadUiState.Error)?.let {
                    it.item == item && it.kind == DownloadErrorKind.Download
                } == true
        if (!canStart) return

        downloadJob?.cancel()
        downloadGeneration += 1
        val generation = downloadGeneration
        val request = DownloadRequest(item, selectedQuality)
        readyFeedback = null
        completedFeedback = null
        transitionTo(DownloadUiState.Downloading(item, DownloadProgress.Zero))
        downloadJob =
            holderScope.launch {
                try {
                    runtime.download(request) { progress ->
                        withContext(stateContext) {
                            if (isCurrentDownload(item, generation)) {
                                transitionTo(DownloadUiState.Downloading(item, progress))
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (isCurrentDownload(item, generation)) {
                        downloadJob = null
                        transitionTo(DownloadUiState.Error(item))
                    }
                    return@launch
                }
                if (isCurrentDownload(item, generation)) {
                    downloadJob = null
                    completedFeedback = null
                    transitionTo(DownloadUiState.Completed(item))
                }
            }
    }

    private fun isCurrentPreview(
        item: DownloadItem,
        generation: Long,
    ): Boolean =
        (state as? DownloadUiState.Previewing)?.let {
            generation == metadataGeneration && it.item == item && observedLinkText == item.source.toString()
        } == true

    private fun isCurrentInstallingSetup(
        setup: DownloadUiState.Setup,
        generation: Long,
    ): Boolean =
        (state as? DownloadUiState.Setup)?.let {
            generation == metadataGeneration &&
                it.item == setup.item &&
                it.tools == setup.tools &&
                it.phase == ToolSetupPhase.Installing
        } == true

    private fun canResolve(
        item: DownloadItem,
        generation: Long,
    ): Boolean =
        generation == metadataGeneration &&
            when (val current = state) {
                is DownloadUiState.Previewing -> {
                    current.item.source == item.source
                }

                is DownloadUiState.Setup -> {
                    current.phase == ToolSetupPhase.Installing && current.item == item
                }

                else -> {
                    false
                }
            }

    private fun isCurrentResolution(
        item: DownloadItem,
        generation: Long,
    ): Boolean =
        (state as? DownloadUiState.Resolving)?.let {
            generation == metadataGeneration && it.item == item
        } == true

    private fun isCurrentDownload(
        item: DownloadItem,
        generation: Long,
    ): Boolean = generation == downloadGeneration && (state as? DownloadUiState.Downloading)?.item == item

    private fun transitionTo(nextState: DownloadUiState) {
        showingLegalDetails = false
        state = nextState
    }

    private fun prepareReady() {
        selectedMode = DownloadMode.Video
        selectedQualityIndex = 0
        readyFeedback = null
        completedFeedback = null
        downloadAuthorizationAccepted = false
    }

    private fun clearReadySelection() {
        selectedMode = DownloadMode.Video
        selectedQualityIndex = 0
        readyFeedback = null
        completedFeedback = null
        downloadAuthorizationAccepted = false
    }

    private fun cancelActiveWork() {
        metadataGeneration += 1
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

private fun DownloadItem.withPreviewFrom(preview: DownloadItem): DownloadItem =
    copy(
        thumbnail = if (thumbnail is MediaThumbnail.Unavailable) preview.thumbnail else thumbnail,
    )
