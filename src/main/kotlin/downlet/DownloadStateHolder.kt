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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@Stable
@Suppress("TooManyFunctions", "LargeClass")
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

    val selectedQualitySupportingText: String?
        get() = selectedQuality.supportingText

    val destination: Path?
        get() {
            val current = state
            return current.itemOrNull?.destination?.takeUnless {
                current is DownloadUiState.Error && current.kind == DownloadErrorKind.Resolution
            }
        }

    val toolSetupAccepted: Boolean
        get() {
            val setup = state as? DownloadUiState.Setup ?: return false
            return setup.intent == ToolSetupIntent.Install && setup.phase != ToolSetupPhase.AwaitingConsent
        }

    val downloadEnabled: Boolean
        get() = state is DownloadUiState.Ready && downloadAuthorizationAccepted

    val toolSetupEnabled: Boolean
        get() =
            (state as? DownloadUiState.Setup)?.let {
                it.phase == ToolSetupPhase.ReadyToInstall ||
                    (
                        it.phase == ToolSetupPhase.Failed &&
                            (it.intent == ToolSetupIntent.Repair || toolSetupAccepted)
                    )
            } == true

    val readyStatus: String?
        get() = (state as? DownloadUiState.Ready)?.let { readyFeedback }

    private val stateContext = scope.coroutineContext.minusKey(Job)
    private val holderJob = SupervisorJob(scope.coroutineContext[Job])
    private val holderScope = CoroutineScope(stateContext + holderJob)
    private var metadataGeneration = 0L
    private var downloadGeneration = 0L
    private var metadataJob: Job? = null
    private var previewJob: Job? = null
    private var preparationJob: Job? = null
    private var downloadJob: Job? = null
    private var observedLinkText = ""
    private var browserCookies: BrowserCookieSource? = null
    private val automaticRepairAttempts = mutableSetOf<DownloadTool>()

    private val availableQualities: List<DownloadQuality>
        get() =
            if (selectedMode == DownloadMode.Video) {
                state.itemOrNull?.videoQualities ?: videoQualityOptions
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
        automaticRepairAttempts.clear()
        cancelActiveWork()
        transitionTo(DownloadUiState.Empty)
        return true
    }

    fun beginResolution(
        text: String,
        browserCookies: BrowserCookieSource? = this.browserCookies,
    ) {
        val source = YouTubeUrl.parse(text) ?: return
        val value = source.toString()

        replaceLink(value)
        this.browserCookies = browserCookies
        validationMessage = null
        clearReadySelection()
        automaticRepairAttempts.clear()
        cancelActiveWork()
        transitionTo(DownloadUiState.Empty)
        startPreview(
            DownloadFixtures.normal.copy(
                source = source,
                title = "YouTube video",
                channel = "YouTube",
                duration = null,
                thumbnail = MediaThumbnail.Unavailable,
            ),
        )
    }

    fun updateToolSetupConsent(accepted: Boolean) {
        val setup = state as? DownloadUiState.Setup ?: return
        if (setup.intent != ToolSetupIntent.Install || setup.phase == ToolSetupPhase.Installing) return

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
                    when (setup.intent) {
                        ToolSetupIntent.Install -> runtime.installMissingTools(setupProgress(generation))
                        ToolSetupIntent.Repair -> runtime.repairManagedTools(setup.tools, setupProgress(generation))
                    }
                    val status = runtime.toolStatus()
                    if (setup.tools.any { it in status.missing || it in status.repairable }) {
                        throw DownloadRuntimeException()
                    }
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
        schedulePreparation()
    }

    fun selectQuality(index: Int) {
        if (state !is DownloadUiState.Ready || index !in availableQualities.indices || selectedQualityIndex == index) {
            return
        }

        selectedQualityIndex = index
        readyFeedback = null
        schedulePreparation()
    }

    @Suppress("ReturnCount")
    fun changeDestination() {
        val current = state
        val item =
            when (current) {
                is DownloadUiState.Ready -> current.item
                is DownloadUiState.Error -> current.item.takeIf { current.reason == DownloadFailureReason.Storage }
                else -> null
            } ?: return
        val destination = runtime.chooseDestination(item.destination) ?: return
        val updatedItem = item.copy(destination = destination)
        val updatedState =
            when (current) {
                is DownloadUiState.Ready -> current.copy(item = updatedItem)
                is DownloadUiState.Error -> current.copy(item = updatedItem)
                else -> null
            } ?: return
        state = updatedState
        if (current is DownloadUiState.Ready) schedulePreparation()
        if (current is DownloadUiState.Ready) readyFeedback = "Save location changed to $destination."
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
            beginResolution(error.item.source.toString(), browserCookies)
        } else {
            startDownload(error.item)
        }
    }

    fun retryWithBrowserCookies(source: BrowserCookieSource) {
        val error =
            (state as? DownloadUiState.Error)?.takeIf {
                it.reason.needsBrowserSession
            } ?: return
        browserCookies = source
        if (error.kind == DownloadErrorKind.Resolution) {
            beginResolution(error.item.source.toString(), source)
        } else {
            startDownload(error.item)
        }
    }

    fun showInFolder() {
        val completed = state as? DownloadUiState.Completed ?: return
        completedFeedback = runtime.showInFolder(completed.file)
    }

    fun downloadAnother() {
        if (state !is DownloadUiState.Completed) return

        cancelActiveWork()
        observedLinkText = ""
        linkFieldState.clearText()
        validationMessage = null
        clearReadySelection()
        automaticRepairAttempts.clear()
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
        browserCookies = null
        automaticRepairAttempts.clear()
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

    fun warmUp() = runtime.warmUp()

    fun close() {
        cancelActiveWork()
        browserCookies = null
        holderJob.cancel()
        runtime.close()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun initializeRuntime(
        item: DownloadItem,
        generation: Long,
    ): Boolean =
        try {
            runtime.initialize()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (isCurrentPreview(item, generation)) {
                transitionTo(DownloadUiState.Error(item, DownloadErrorKind.Resolution, DownloadFailureReason.Tool))
            }
            false
        }

    @Suppress("TooGenericExceptionCaught")
    private fun startPreview(requestItem: DownloadItem) {
        if (state !is DownloadUiState.Empty || observedLinkText != requestItem.source.toString()) return

        val generation = metadataGeneration
        transitionTo(DownloadUiState.Previewing(requestItem))
        metadataJob =
            holderScope.launch {
                if (!initializeRuntime(requestItem, generation)) return@launch
                previewJob = holderScope.launch { loadPreview(requestItem, generation) }
                val status =
                    try {
                        runtime.toolStatus()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (isCurrentPreview(requestItem, generation)) {
                            clearReadySelection()
                            transitionTo(
                                DownloadUiState.Error(
                                    state.itemOrNull ?: requestItem,
                                    DownloadErrorKind.Resolution,
                                    (error as? DownloadRuntimeException)?.reason ?: DownloadFailureReason.Tool,
                                ),
                            )
                        }
                        return@launch
                    }
                if (!isCurrentPreview(requestItem, generation)) return@launch
                val previewItem = state.itemOrNull ?: requestItem
                if (status.repairable.isNotEmpty()) {
                    startAutomaticRepair(previewItem, status.repairable, generation, DownloadErrorKind.Resolution)
                    return@launch
                }
                if (status.missing.isNotEmpty()) {
                    clearReadySelection()
                    transitionTo(DownloadUiState.Setup(previewItem, status.missing))
                    return@launch
                }
                resolve(previewItem, generation)
            }
    }

    @Suppress("TooGenericExceptionCaught", "CyclomaticComplexMethod", "ReturnCount")
    private suspend fun loadPreview(
        item: DownloadItem,
        generation: Long,
    ) {
        val preview =
            try {
                withTimeoutOrNull(3.seconds) { runtime.preview(item.source) } ?: return
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return
            }
        if (generation != metadataGeneration) return
        val current = state
        val currentItem = current.itemOrNull?.takeIf { it.source == item.source } ?: return
        val updated =
            if (current is DownloadUiState.Ready ||
                (current is DownloadUiState.Error && current.kind == DownloadErrorKind.Download)
            ) {
                currentItem.copy(thumbnail = preview.thumbnail)
            } else {
                currentItem.copy(
                    title = preview.title,
                    channel = preview.channel,
                    duration = preview.duration,
                    thumbnail = preview.thumbnail,
                    destination = preview.destination,
                )
            }
        state =
            when (current) {
                is DownloadUiState.Previewing -> current.copy(item = updated)
                is DownloadUiState.Setup -> current.copy(item = updated)
                is DownloadUiState.Resolving -> current.copy(item = updated)
                is DownloadUiState.Ready -> current.copy(item = updated)
                is DownloadUiState.Error -> current.copy(item = updated)
                else -> current
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolve(
        requestItem: DownloadItem,
        generation: Long,
        preserveDestination: Boolean = false,
    ) {
        if (!canResolve(requestItem, generation)) return

        transitionTo(DownloadUiState.Resolving(requestItem))
        try {
            val resolvedItem =
                try {
                    withTimeout(30.seconds) { runtime.resolve(requestItem.source, browserCookies) }
                } catch (error: TimeoutCancellationException) {
                    throw DownloadRuntimeException(error, DownloadFailureReason.Network)
                }.withPreviewFrom(state.itemOrNull ?: requestItem)
            if (isCurrentResolution(requestItem, generation)) {
                prepareReady()
                transitionTo(
                    DownloadUiState.Ready(
                        if (preserveDestination) {
                            resolvedItem.copy(
                                destination = requestItem.destination,
                            )
                        } else {
                            resolvedItem
                        },
                    ),
                )
                schedulePreparation()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrentResolution(requestItem, generation)) {
                val runtimeError = error as? DownloadRuntimeException
                if (
                    runtimeError != null &&
                    startAutomaticRepair(
                        state.itemOrNull ?: requestItem,
                        runtimeError.repairableTools,
                        generation,
                        DownloadErrorKind.Resolution,
                    )
                ) {
                    return
                }
                clearReadySelection()
                transitionTo(
                    DownloadUiState.Error(
                        state.itemOrNull ?: requestItem,
                        DownloadErrorKind.Resolution,
                        runtimeError?.reason ?: DownloadFailureReason.Unknown,
                    ),
                )
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun schedulePreparation() {
        preparationJob?.cancel()
        val item = (state as? DownloadUiState.Ready)?.item ?: return
        runtime.cancel()
        val request = DownloadRequest(item, selectedQuality, browserCookies)
        preparationJob =
            holderScope.launch {
                delay(PREPARATION_DEBOUNCE_MILLIS)
                try {
                    runtime.prepareDownload(request)
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    // Preparation is optional; Download can launch the CLI normally.
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

        previewJob?.cancel()
        previewJob = null
        preparationJob?.cancel()
        preparationJob = null
        downloadJob?.cancel()
        downloadGeneration += 1
        val generation = downloadGeneration
        automaticRepairAttempts.clear()
        val request = DownloadRequest(item, selectedQuality, browserCookies)
        readyFeedback = null
        completedFeedback = null
        transitionTo(DownloadUiState.Downloading(item, DownloadProgress.Preparing))
        downloadJob =
            holderScope.launch {
                if (!prepareDownloadTools(item, generation)) return@launch
                val file = runDownload(request, item, generation) ?: return@launch
                if (isCurrentDownload(item, generation)) {
                    downloadJob = null
                    completedFeedback = null
                    transitionTo(DownloadUiState.Completed(item, file))
                }
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun prepareDownloadTools(
        item: DownloadItem,
        generation: Long,
    ): Boolean {
        val status =
            try {
                runtime.toolStatus()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDownload(item, generation)) {
                    downloadJob = null
                    transitionTo(
                        DownloadUiState.Error(
                            item,
                            reason =
                                (error as? DownloadRuntimeException)?.reason
                                    ?: DownloadFailureReason.Tool,
                        ),
                    )
                }
                return false
            }
        return when {
            status.repairable.isNotEmpty() -> {
                downloadJob = null
                startAutomaticRepair(item, status.repairable, metadataGeneration, DownloadErrorKind.Download)
                false
            }

            status.missing.isNotEmpty() -> {
                downloadJob = null
                transitionTo(DownloadUiState.Setup(item, status.missing))
                false
            }

            else -> {
                true
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runDownload(
        request: DownloadRequest,
        item: DownloadItem,
        generation: Long,
    ): Path? =
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
        } catch (error: Exception) {
            if (isCurrentDownload(item, generation)) {
                downloadJob = null
                val runtimeError = error as? DownloadRuntimeException
                val repairStarted =
                    runtimeError != null &&
                        startAutomaticRepair(
                            item,
                            runtimeError.repairableTools,
                            metadataGeneration,
                            DownloadErrorKind.Download,
                        )
                if (!repairStarted) {
                    transitionTo(
                        DownloadUiState.Error(
                            item,
                            reason = runtimeError?.reason ?: DownloadFailureReason.Unknown,
                        ),
                    )
                }
            }
            null
        }

    @Suppress("CyclomaticComplexMethod")
    private fun startAutomaticRepair(
        item: DownloadItem,
        tools: List<DownloadTool>,
        generation: Long,
        errorKind: DownloadErrorKind,
    ): Boolean {
        if (tools.isEmpty()) return false
        val orderedTools = DownloadTool.entries.filter(tools::contains)
        if (orderedTools.any(automaticRepairAttempts::contains)) {
            clearReadySelection()
            transitionTo(DownloadUiState.Error(item, errorKind, DownloadFailureReason.Tool))
        } else {
            automaticRepairAttempts += orderedTools
            val setup =
                DownloadUiState.Setup(
                    item = item,
                    tools = orderedTools,
                    phase = ToolSetupPhase.Installing,
                    intent = ToolSetupIntent.Repair,
                )
            transitionTo(setup)
            metadataJob =
                holderScope.launch {
                    try {
                        runtime.repairManagedTools(orderedTools, setupProgress(generation))
                        val status = runtime.toolStatus()
                        if (orderedTools.any { it in status.missing || it in status.repairable }) {
                            throw DownloadRuntimeException()
                        }
                        if (status.repairable.isNotEmpty()) {
                            if (isCurrentInstallingSetup(setup, generation)) {
                                startAutomaticRepair(item, status.repairable, generation, errorKind)
                            }
                            return@launch
                        }
                        if (status.missing.isNotEmpty()) {
                            if (isCurrentInstallingSetup(setup, generation)) {
                                clearReadySelection()
                                transitionTo(DownloadUiState.Setup(state.itemOrNull ?: item, status.missing))
                            }
                            return@launch
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        if (isCurrentInstallingSetup(setup, generation)) {
                            transitionTo(setup.copy(phase = ToolSetupPhase.Failed))
                        }
                        return@launch
                    }
                    if (isCurrentInstallingSetup(setup, generation)) {
                        resolve(
                            state.itemOrNull ?: item,
                            generation,
                            preserveDestination =
                                errorKind == DownloadErrorKind.Download,
                        )
                    }
                }
        }
        return true
    }

    private fun setupProgress(generation: Long): (DownloadTool, String) -> Unit =
        { tool, message ->
            holderScope.launch {
                val current = state as? DownloadUiState.Setup
                if (generation == metadataGeneration && current?.phase == ToolSetupPhase.Installing) {
                    state = current.copy(progress = current.progress + (tool to message))
                }
            }
        }

    private fun isCurrentPreview(
        item: DownloadItem,
        generation: Long,
    ): Boolean =
        (state as? DownloadUiState.Previewing)?.let {
            generation == metadataGeneration && it.item.source == item.source &&
                observedLinkText == item.source.toString()
        } == true

    private fun isCurrentInstallingSetup(
        setup: DownloadUiState.Setup,
        generation: Long,
    ): Boolean =
        (state as? DownloadUiState.Setup)?.let {
            generation == metadataGeneration &&
                it.item.source == setup.item.source &&
                it.tools == setup.tools &&
                it.intent == setup.intent &&
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
                    current.phase == ToolSetupPhase.Installing && current.item.source == item.source
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
            generation == metadataGeneration && it.item.source == item.source
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
        preparationJob?.cancel()
        preparationJob = null
        metadataGeneration += 1
        downloadGeneration += 1
        runtime.cancel()
        previewJob?.cancel()
        previewJob = null
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

private const val PREPARATION_DEBOUNCE_MILLIS = 250L
