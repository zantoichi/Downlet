package downlet

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.State
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStateMachineBlocking
import ru.nsk.kstatemachine.statemachine.destroy
import ru.nsk.kstatemachine.transition.onComplete
import kotlin.time.Duration.Companion.milliseconds

@Stable
@Suppress("TooManyFunctions")
internal class DownloadStateHolder(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
    machineDispatcher: CoroutineDispatcher = Dispatchers.Default,
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

    private val machineJob = SupervisorJob(scope.coroutineContext[Job])
    private val machineScope = CoroutineScope(machineDispatcher + machineJob)
    private val machine = createDownloadMachine()
    private var resolutionJob: Job? = null
    private var observedLinkText = ""

    fun observeLinkEdit(text: String): Boolean {
        if (text == observedLinkText) return false

        observedLinkText = text
        validationMessage = text.takeIf { it.isNotBlank() && !isValidYouTubeUrl(it) }?.let { INVALID_LINK_MESSAGE }
        clearReadySelection()
        resolutionJob?.cancel()
        process(MachineEvent.LinkEdited)
        return true
    }

    fun beginResolution(text: String) {
        val value = text.trim()
        if (!isValidYouTubeUrl(value)) return

        replaceLink(value)
        validationMessage = null
        clearReadySelection()
        val fixture = DownloadFixtures.normal.copy(sourceUrl = value)
        resolutionJob?.cancel()
        resolutionJob =
            machineScope.launch {
                machine.processEvent(MachineEvent.Resolve(fixture))
                delay(FAKE_RESOLUTION_MILLIS.milliseconds)
                machine.processEvent(MachineEvent.ResolutionCompleted(fixture))
            }
    }

    internal fun completeResolution(fixture: DownloadFixture) {
        process(MachineEvent.ResolutionCompleted(fixture))
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
        resolutionJob?.cancel()
        when (event) {
            DownloadEvent.Reset,
            DownloadEvent.ShowEmpty,
            -> {
                observedLinkText = ""
                linkFieldState.clearText()
                validationMessage = null
                clearReadySelection()
                process(MachineEvent.ForceEmpty)
            }

            is DownloadEvent.ShowResolving -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                clearReadySelection()
                process(MachineEvent.ForceResolving(event.fixture))
            }

            is DownloadEvent.ShowReady -> {
                replaceLink(event.fixture.sourceUrl)
                validationMessage = null
                process(MachineEvent.ForceReady(event.fixture))
            }
        }
    }

    fun close() {
        resolutionJob?.cancel()
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
        lateinit var resolving: State
        lateinit var ready: State

        fun State.routeEvents() {
            transitionOn<MachineEvent> {
                targetState = {
                    when (event.target) {
                        MachinePhase.Empty -> empty
                        MachinePhase.Resolving -> resolving
                        MachinePhase.Ready -> ready
                    }
                }
                guard = {
                    val event = event
                    val resolvingState = state as? DownloadUiState.Resolving
                    event !is MachineEvent.ResolutionCompleted ||
                        (resolvingState?.completesAutomatically == true && resolvingState.fixture == event.fixture)
                }
                onComplete { _, params -> applyMachineEvent(params.event) }
            }
        }

        return createStateMachineBlocking(machineScope, name = "Download flow") {
            empty = initialState("Empty")
            resolving = state("Resolving")
            ready = state("Ready")
            empty.routeEvents()
            resolving.routeEvents()
            ready.routeEvents()
        }
    }

    private fun applyMachineEvent(event: MachineEvent) {
        state =
            when (event) {
                MachineEvent.LinkEdited,
                MachineEvent.ForceEmpty,
                -> {
                    DownloadUiState.Empty
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

                is MachineEvent.ResolutionCompleted -> {
                    prepareReady(event.fixture)
                    DownloadUiState.Ready(event.fixture)
                }

                is MachineEvent.ForceReady -> {
                    prepareReady(event.fixture)
                    DownloadUiState.Ready(event.fixture)
                }
            }
    }

    private fun process(event: MachineEvent) {
        machineScope.launch { machine.processEvent(event) }
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

private enum class MachinePhase {
    Empty,
    Resolving,
    Ready,
}

private sealed interface MachineEvent : Event {
    val target: MachinePhase

    data object LinkEdited : MachineEvent {
        override val target = MachinePhase.Empty
    }

    data class Resolve(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Resolving
    }

    data class ResolutionCompleted(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Ready
    }

    data object ForceEmpty : MachineEvent {
        override val target = MachinePhase.Empty
    }

    data class ForceResolving(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Resolving
    }

    data class ForceReady(
        val fixture: DownloadFixture,
    ) : MachineEvent {
        override val target = MachinePhase.Ready
    }
}
