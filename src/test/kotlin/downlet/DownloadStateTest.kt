package downlet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadStateTest {
    @Test
    fun `all six states are explicit`() {
        val normal = DownloadFixtures.normal
        val failure = DownloadFixtures.failure

        val labels =
            listOf(
                DownloadUiState.Empty,
                DownloadUiState.Resolving(normal),
                DownloadUiState.Ready(normal),
                DownloadUiState.Downloading(normal, progressPercent = 43),
                DownloadUiState.Completed(normal),
                DownloadUiState.Error(failure),
            ).map(DownloadUiState::label)

        assertEquals(listOf("Empty", "Resolving", "Ready", "Downloading", "Completed", "Error"), labels)
    }

    @Test
    fun `invalid outcome combinations are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DownloadUiState.Downloading(DownloadFixtures.failure, progressPercent = 87)
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadUiState.Completed(DownloadFixtures.failure)
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadUiState.Error(DownloadFixtures.normal)
        }
    }

    @Test
    fun `fixtures remain deterministic and distinct`() {
        assertTrue(DownloadFixtures.longTitle.title.length > DownloadFixtures.normal.title.length)
        assertNull(DownloadFixtures.missingThumbnail.thumbnailResource)
        assertTrue(DownloadFixtures.longDestination.destination.length > DownloadFixtures.normal.destination.length)
        assertEquals(FakeDownloadOutcome.Failure(68), DownloadFixtures.failure.outcome)
    }

    @Test
    fun `state holder handles only explicit events`() {
        val holder = DownloadStateHolder()

        holder.onEvent(DownloadEvent.ShowReady(DownloadFixtures.longTitle))
        assertEquals(DownloadUiState.Ready(DownloadFixtures.longTitle), holder.state)

        holder.onEvent(DownloadEvent.Reset)
        assertEquals(DownloadUiState.Empty, holder.state)
    }
}
