package downlet

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    data class Resolving(val fixture: DownloadFixture) : DownloadUiState

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

    data class ShowReady(val fixture: DownloadFixture = DownloadFixtures.normal) : DownloadEvent
}

@Stable
internal class DownloadStateHolder(initialState: DownloadUiState = DownloadUiState.Empty) {
    var state by mutableStateOf(initialState)
        private set

    fun onEvent(event: DownloadEvent) {
        state =
            when (event) {
                DownloadEvent.Reset -> DownloadUiState.Empty
                is DownloadEvent.ShowReady -> DownloadUiState.Ready(event.fixture)
            }
    }
}
