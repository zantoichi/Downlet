package downlet

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.State
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStateMachineBlocking
import ru.nsk.kstatemachine.statemachine.destroy
import ru.nsk.kstatemachine.transition.onComplete
import java.util.concurrent.atomic.AtomicLong

@Stable
@Suppress("TooManyFunctions")
internal class DownloadStateHolder(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
    machineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val runtime: DownloadRuntime = PreviewDownloadRuntime(),
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
        get() = if (selectedMode == DownloadMode.Video) videoQualityOptions else audioQualityOptions

    val selectedQualityLabel: String
        get() = qualityOptions[selectedQualityIndex]

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
                if (fixture.canDownload) readyFeedback else DOWNLOAD_UNAVAILABLE_MESSAGE
            }

    private val machineJob = SupervisorJob(scope.coroutineContext[Job])
    private val machineScope = CoroutineScope(machineDispatcher + machineJob)
    private val machine = createDownloadMachine()
    private val machineEventMutex = Mutex()
    private val downloadGeneration = AtomicLong()
    private var resolutionJob: Job? = null
    private var progressJob: Job? = null
    private var observedLinkText = ""

    fun observeLinkEdit(text: String): Boolean {
        if (text == observedLinkText) return false

        observedLinkText = text
        showingLegalDetails = false
        validationMessage = text.takeIf { it.isNotBlank() && !isValidYouTubeUrl(it) }?.let { INVALID_LINK_MESSAGE }
        clearReadySelection()
        cancelTimers()
        process(MachineEvent.LinkEdited)
        return true
    }

    fun beginResolution(text: String) {
        val value = text.trim()
        if (!isValidYouTubeUrl(value)) return

        replaceLink(value)
        validationMessage = null
        clearReadySelection()
        val requestFixture = DownloadFixtures.normal.copy(sourceUrl = value)
        cancelTimers()
        startPreview(requestFixture)
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

        cancelTimers()
        resolutionJob =
            machineScope.launch {
                submitMachineEvent(MachineEvent.InstallTools(setup.fixture, setup.tools))
                try {
                    runtime.installMissingTools()
                    if (runtime.missingTools().isNotEmpty()) throw DownloadRuntimeException()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    submitMachineEvent(MachineEvent.ToolInstallationFailed(setup.fixture, setup.tools))
                    return@launch
                }
                submitMachineEvent(MachineEvent.Resolve(setup.fixture))
                try {
                    val resolvedFixture = runtime.resolve(setup.fixture.sourceUrl)
                    submitMachineEvent(
                        MachineEvent.ResolutionCompleted(
                            setup.fixture,
                            resolvedFixture.withPreviewFrom(setup.fixture),
                        ),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    submitMachineEvent(MachineEvent.ResolutionFailed(setup.fixture))
                }
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
        process(MachineEvent.ResolutionCompleted(fixture, fixture))
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

        destination = runtime.chooseDestination(destination) ?: return
        readyFeedback = "Save location changed to $destination."
    }

    fun download() {
        val fixture = (state as? DownloadUiState.Ready)?.fixture ?: return
        if (!downloadEnabled) return

        startProgress(fixture, MachineEvent.StartDownload(fixture))
    }

    fun cancelDownload() {
        val fixture = (state as? DownloadUiState.Downloading)?.fixture ?: return
        cancelTimers()
        process(MachineEvent.CancelDownload(fixture))
    }

    fun retryDownload() {
        val error = state as? DownloadUiState.Error ?: return
        if (error.kind == DownloadErrorKind.Resolution) {
            beginResolution(error.fixture.sourceUrl)
        } else {
            startProgress(error.fixture, MachineEvent.RetryDownload(error.fixture))
        }
    }

    fun openFolder() {
        if (state is DownloadUiState.Completed) {
            completedFeedback = runtime.openDestination(destination)
        }
    }

    fun downloadAnother() {
        if (state !is DownloadUiState.Completed) return

        cancelTimers()
        observedLinkText = ""
        linkFieldState.clearText()
        validationMessage = null
        process(MachineEvent.DownloadAnother)
    }

    @Suppress("LongMethod")
    fun onEvent(event: DownloadEvent) {
        cancelTimers()
        when (event) {
            DownloadEvent.Reset,
            DownloadEvent.ShowEmpty,
            -> {
                observedLinkText = ""
                linkFieldState.clearText()
                validationMessage = null
                clearReadySelection()
                requestLinkFocus()
                process(MachineEvent.ForceEmpty)
            }

            is DownloadEvent.ShowResolving -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                clearReadySelection()
                process(MachineEvent.ForceResolving(event.fixture))
            }

            is DownloadEvent.ShowPreviewing -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                clearReadySelection()
                process(MachineEvent.ForcePreviewing(event.fixture))
            }

            is DownloadEvent.ShowSetup -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                clearReadySelection()
                process(
                    MachineEvent.ForceSetup(
                        fixture = event.fixture,
                        tools = event.tools,
                        installing = event.installing,
                        failed = event.failed,
                    ),
                )
            }

            is DownloadEvent.ShowReady -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                process(MachineEvent.ForceReady(event.fixture))
            }

            is DownloadEvent.ShowDownloading -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                prepareReady(event.fixture)
                process(MachineEvent.ForceDownloading(event.fixture, event.progressPercent))
            }

            is DownloadEvent.ShowCompleted -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                prepareReady(event.fixture)
                process(MachineEvent.ForceCompleted(event.fixture))
            }

            is DownloadEvent.ShowError -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                prepareReady(event.fixture)
                process(MachineEvent.ForceError(event.fixture))
            }

            DownloadEvent.ShowInvalidInput -> {
                val invalidText = "not a YouTube link"
                replaceLink(invalidText)
                validationMessage = INVALID_LINK_MESSAGE
                clearReadySelection()
                process(MachineEvent.ForceEmpty)
            }
        }
    }

    fun close() {
        cancelTimers()
        machineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                machine.destroy()
            } finally {
                machineScope.cancel()
            }
        }
    }

    private fun createDownloadMachine(): StateMachine {
        lateinit var empty: State
        lateinit var previewing: State
        lateinit var setup: State
        lateinit var resolving: State
        lateinit var ready: State
        lateinit var downloading: State
        lateinit var completed: State
        lateinit var error: State

        fun State.routeEvents() {
            transitionOn<MachineEvent> {
                targetState = {
                    when (event.target) {
                        MachinePhase.Empty -> empty
                        MachinePhase.Previewing -> previewing
                        MachinePhase.Setup -> setup
                        MachinePhase.Resolving -> resolving
                        MachinePhase.Ready -> ready
                        MachinePhase.Downloading -> downloading
                        MachinePhase.Completed -> completed
                        MachinePhase.Error -> error
                    }
                }
                guard = { acceptsMachineEvent(event) }
                onComplete { _, params -> applyMachineEvent(params.event) }
            }
        }

        return createStateMachineBlocking(machineScope, name = "Download flow") {
            empty = initialState("Empty")
            previewing = state("Previewing")
            setup = state("Setup")
            resolving = state("Resolving")
            ready = state("Ready")
            downloading = state("Downloading")
            completed = state("Completed")
            error = state("Error")
            empty.routeEvents()
            previewing.routeEvents()
            setup.routeEvents()
            resolving.routeEvents()
            ready.routeEvents()
            downloading.routeEvents()
            completed.routeEvents()
            error.routeEvents()
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun applyMachineEvent(event: MachineEvent) {
        showingLegalDetails = false
        state =
            when (event) {
                MachineEvent.LinkEdited,
                MachineEvent.ForceEmpty,
                MachineEvent.DownloadAnother,
                -> {
                    clearReadySelection()
                    completedFeedback = null
                    if (event is MachineEvent.DownloadAnother) requestLinkFocus()
                    DownloadUiState.Empty
                }

                is MachineEvent.Preview -> {
                    DownloadUiState.Previewing(event.fixture)
                }

                is MachineEvent.ForcePreviewing -> {
                    DownloadUiState.Previewing(
                        event.fixture,
                        completesAutomatically = false,
                    )
                }

                is MachineEvent.RequireSetup -> {
                    clearReadySelection()
                    DownloadUiState.Setup(event.fixture, event.tools)
                }

                is MachineEvent.InstallTools -> {
                    DownloadUiState.Setup(event.fixture, event.tools, installing = true)
                }

                is MachineEvent.ToolInstallationFailed -> {
                    DownloadUiState.Setup(event.fixture, event.tools, failed = true)
                }

                is MachineEvent.Resolve -> {
                    DownloadUiState.Resolving(event.fixture)
                }

                is MachineEvent.ForceResolving -> {
                    DownloadUiState.Resolving(
                        event.fixture,
                        completesAutomatically = false,
                    )
                }

                is MachineEvent.ForceSetup -> {
                    DownloadUiState.Setup(
                        fixture = event.fixture,
                        tools = event.tools,
                        installing = event.installing,
                        failed = event.failed,
                    )
                }

                is MachineEvent.ResolutionCompleted -> {
                    prepareReady(event.resolvedFixture)
                    DownloadUiState.Ready(event.resolvedFixture)
                }

                is MachineEvent.ResolutionFailed -> {
                    clearReadySelection()
                    DownloadUiState.Error(event.fixture, DownloadErrorKind.Resolution)
                }

                is MachineEvent.PreviewFailed -> {
                    clearReadySelection()
                    DownloadUiState.Error(event.fixture, DownloadErrorKind.Resolution)
                }

                is MachineEvent.ForceReady -> {
                    prepareReady(event.fixture)
                    DownloadUiState.Ready(event.fixture)
                }

                is MachineEvent.StartDownload -> {
                    readyFeedback = null
                    completedFeedback = null
                    DownloadUiState.Downloading(event.fixture, progressPercent = 0)
                }

                is MachineEvent.RetryDownload -> {
                    readyFeedback = null
                    completedFeedback = null
                    DownloadUiState.Downloading(event.fixture, progressPercent = 0)
                }

                is MachineEvent.Progress -> {
                    DownloadUiState.Downloading(event.fixture, event.progressPercent)
                }

                is MachineEvent.DownloadCompleted -> {
                    invalidateDownloadGeneration()
                    progressJob = null
                    completedFeedback = null
                    DownloadUiState.Completed(event.fixture)
                }

                is MachineEvent.DownloadFailed -> {
                    invalidateDownloadGeneration()
                    progressJob = null
                    DownloadUiState.Error(event.fixture)
                }

                is MachineEvent.CancelDownload -> {
                    DownloadUiState.Ready(event.fixture)
                }

                is MachineEvent.ForceDownloading -> {
                    DownloadUiState.Downloading(event.fixture, event.progressPercent)
                }

                is MachineEvent.ForceCompleted -> {
                    completedFeedback = null
                    DownloadUiState.Completed(event.fixture)
                }

                is MachineEvent.ForceError -> {
                    DownloadUiState.Error(event.fixture)
                }
            }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun acceptsMachineEvent(event: MachineEvent): Boolean =
        when (event) {
            MachineEvent.LinkEdited,
            MachineEvent.ForceEmpty,
            MachineEvent.DownloadAnother,
            is MachineEvent.ForcePreviewing,
            is MachineEvent.ForceResolving,
            is MachineEvent.ForceSetup,
            is MachineEvent.ForceReady,
            is MachineEvent.ForceDownloading,
            is MachineEvent.ForceCompleted,
            is MachineEvent.ForceError,
            -> {
                true
            }

            is MachineEvent.Preview -> {
                state is DownloadUiState.Empty && observedLinkText == event.fixture.sourceUrl
            }

            is MachineEvent.RequireSetup -> {
                val previewingState = state as? DownloadUiState.Previewing
                previewingState?.completesAutomatically == true &&
                    previewingState.fixture.sourceUrl == event.fixture.sourceUrl
            }

            is MachineEvent.Resolve -> {
                when (val current = state) {
                    is DownloadUiState.Previewing -> {
                        current.completesAutomatically && current.fixture.sourceUrl == event.fixture.sourceUrl
                    }

                    is DownloadUiState.Setup -> {
                        current.installing && current.fixture == event.fixture
                    }

                    else -> {
                        false
                    }
                }
            }

            is MachineEvent.InstallTools -> {
                val setupState = state as? DownloadUiState.Setup
                setupState?.fixture == event.fixture && setupState.tools == event.tools && !setupState.installing
            }

            is MachineEvent.ToolInstallationFailed -> {
                val setupState = state as? DownloadUiState.Setup
                setupState?.fixture == event.fixture && setupState.tools == event.tools && setupState.installing
            }

            is MachineEvent.ResolutionCompleted -> {
                val resolvingState = state as? DownloadUiState.Resolving
                resolvingState?.completesAutomatically == true && resolvingState.fixture == event.requestFixture
            }

            is MachineEvent.ResolutionFailed -> {
                val resolvingState = state as? DownloadUiState.Resolving
                resolvingState?.completesAutomatically == true && resolvingState.fixture == event.fixture
            }

            is MachineEvent.PreviewFailed -> {
                val previewingState = state as? DownloadUiState.Previewing
                previewingState?.completesAutomatically == true && previewingState.fixture == event.fixture
            }

            is MachineEvent.StartDownload -> {
                state == DownloadUiState.Ready(event.fixture)
            }

            is MachineEvent.Progress -> {
                event.generation == downloadGeneration.get() &&
                    (state as? DownloadUiState.Downloading)?.fixture == event.fixture
            }

            is MachineEvent.DownloadCompleted -> {
                event.generation == downloadGeneration.get() &&
                    (state as? DownloadUiState.Downloading)?.fixture == event.fixture
            }

            is MachineEvent.DownloadFailed -> {
                event.generation == downloadGeneration.get() &&
                    (state as? DownloadUiState.Downloading)?.fixture == event.fixture
            }

            is MachineEvent.CancelDownload -> {
                (state as? DownloadUiState.Downloading)?.fixture == event.fixture
            }

            is MachineEvent.RetryDownload -> {
                state == DownloadUiState.Error(event.fixture)
            }
        }

    private fun process(event: MachineEvent) {
        machineScope.launch { submitMachineEvent(event) }
    }

    private suspend fun submitMachineEvent(event: MachineEvent) {
        machineEventMutex.lock()
        try {
            machine.processEvent(event)
        } finally {
            machineEventMutex.unlock()
        }
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

    private fun startPreview(requestFixture: DownloadFixture) {
        resolutionJob =
            machineScope.launch {
                submitMachineEvent(MachineEvent.Preview(requestFixture))
                val previewFixture =
                    try {
                        runtime.preview(requestFixture.sourceUrl)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        submitMachineEvent(MachineEvent.PreviewFailed(requestFixture))
                        return@launch
                    }
                val missingTools = runtime.missingTools()
                if (missingTools.isNotEmpty()) {
                    submitMachineEvent(MachineEvent.RequireSetup(previewFixture, missingTools))
                    return@launch
                }
                resolve(previewFixture)
            }
    }

    private suspend fun resolve(requestFixture: DownloadFixture) {
        submitMachineEvent(MachineEvent.Resolve(requestFixture))
        try {
            val resolvedFixture = runtime.resolve(requestFixture.sourceUrl).withPreviewFrom(requestFixture)
            submitMachineEvent(MachineEvent.ResolutionCompleted(requestFixture, resolvedFixture))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            submitMachineEvent(MachineEvent.ResolutionFailed(requestFixture))
        }
    }

    private fun startProgress(
        fixture: DownloadFixture,
        startEvent: MachineEvent,
    ) {
        progressJob?.cancel()
        val generation = downloadGeneration.incrementAndGet()
        progressJob =
            machineScope.launch {
                submitMachineEvent(startEvent)
                val request = DownloadRequest(fixture, selectedMode, selectedQualityIndex, destination)
                try {
                    runtime.download(request) { progress ->
                        submitMachineEvent(MachineEvent.Progress(fixture, progress, generation))
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    submitMachineEvent(MachineEvent.DownloadFailed(fixture, generation))
                    return@launch
                }
                submitMachineEvent(MachineEvent.DownloadCompleted(fixture, generation))
            }
    }

    private fun cancelTimers() {
        invalidateDownloadGeneration()
        runtime.cancel()
        resolutionJob?.cancel()
        resolutionJob = null
        progressJob?.cancel()
        progressJob = null
    }

    private fun invalidateDownloadGeneration() {
        downloadGeneration.incrementAndGet()
    }

    internal fun captureProgressSubmissionForTest(
        fixture: DownloadFixture,
        progressPercent: Int,
    ): suspend () -> Unit {
        val event = MachineEvent.Progress(fixture, progressPercent, downloadGeneration.get())
        return { submitMachineEvent(event) }
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

private enum class MachinePhase {
    Empty,
    Previewing,
    Setup,
    Resolving,
    Ready,
    Downloading,
    Completed,
    Error,
}

private sealed interface MachineEvent : Event {
    val target: MachinePhase

    data object LinkEdited : MachineEvent {
        override val target = MachinePhase.Empty
    }

    data class Preview(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Previewing
    }

    data class Resolve(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Resolving
    }

    data class RequireSetup(
        val fixture: DownloadFixture,
        val tools: List<String>,
    ) : MachineEvent {
        override val target = MachinePhase.Setup
    }

    data class InstallTools(
        val fixture: DownloadFixture,
        val tools: List<String>,
    ) : MachineEvent {
        override val target = MachinePhase.Setup
    }

    data class ToolInstallationFailed(
        val fixture: DownloadFixture,
        val tools: List<String>,
    ) : MachineEvent {
        override val target = MachinePhase.Setup
    }

    data class ResolutionCompleted(
        val requestFixture: DownloadFixture,
        val resolvedFixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Ready
    }

    data class ResolutionFailed(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Error
    }

    data class PreviewFailed(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Error
    }

    data object ForceEmpty : MachineEvent {
        override val target = MachinePhase.Empty
    }

    data class ForcePreviewing(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Previewing
    }

    data class ForceResolving(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Resolving
    }

    data class ForceSetup(
        val fixture: DownloadFixture,
        val tools: List<String>,
        val installing: Boolean,
        val failed: Boolean,
    ) : MachineEvent {
        override val target = MachinePhase.Setup
    }

    data class ForceReady(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Ready
    }

    data class StartDownload(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Downloading
    }

    data class Progress(
        val fixture: DownloadFixture,
        val progressPercent: Int,
        val generation: Long,
    ) : MachineEvent {
        override val target = MachinePhase.Downloading
    }

    data class DownloadCompleted(
        val fixture: DownloadFixture,
        val generation: Long,
    ) : MachineEvent {
        override val target = MachinePhase.Completed
    }

    data class DownloadFailed(
        val fixture: DownloadFixture,
        val generation: Long,
    ) : MachineEvent {
        override val target = MachinePhase.Error
    }

    data class CancelDownload(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Ready
    }

    data class RetryDownload(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Downloading
    }

    data object DownloadAnother : MachineEvent {
        override val target = MachinePhase.Empty
    }

    data class ForceDownloading(
        val fixture: DownloadFixture,
        val progressPercent: Int,
    ) : MachineEvent {
        override val target = MachinePhase.Downloading
    }

    data class ForceCompleted(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Completed
    }

    data class ForceError(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Error
    }
}
