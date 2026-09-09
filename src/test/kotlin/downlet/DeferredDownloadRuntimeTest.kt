package downlet

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredDownloadRuntimeTest {
    @Test
    fun `immediate link replacement waits for one initialization and resolves newest link`() =
        runTest {
            val ready = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            val seen = mutableListOf<YouTubeUrl>()
            val runtime =
                DeferredDownloadRuntime(this) {
                    entered.complete(Unit)
                    ready.await()
                    object : DownloadRuntime by PreviewDownloadRuntime() {
                        override suspend fun preview(source: YouTubeUrl): DownloadItem {
                            seen += source
                            return DownloadFixtures.normal.copy(source = source)
                        }
                    }
                }
            val holder = DownloadStateHolder(this, runtime)
            holder.beginResolution("https://youtu.be/98_OaqSuOzE")
            runCurrent()
            entered.await()
            holder.beginResolution("https://youtu.be/xTrHIOEe2QA")
            runCurrent()
            ready.complete(Unit)
            runtime.initialize()
            advanceUntilIdle()
            assertEquals(listOf(YouTubeUrl.parse("https://youtu.be/xTrHIOEe2QA")), seen)
            assertEquals("Ready", holder.state.label)
            holder.close()
        }

    @Test
    fun `warm up shares initialization and request cancellation does not stop it`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val ready = CompletableDeferred<Unit>()
            var calls = 0
            val runtime =
                DeferredDownloadRuntime(this) {
                    calls++
                    started.complete(Unit)
                    ready.await()
                    PreviewDownloadRuntime()
                }
            assertEquals(0, calls)
            runtime.warmUp()
            started.await()
            val obsolete = async { runtime.initialize() }
            obsolete.cancelAndJoin()
            ready.complete(Unit)
            runtime.initialize()
            runtime.initialize()
            assertEquals(1, calls)
            runtime.close()
        }

    @Test
    fun `initialization failure can retry and close cancels pending factory`() =
        runTest {
            var calls = 0
            val runtime =
                DeferredDownloadRuntime(this) {
                    if (++calls == 1) error("initialization failed")
                    PreviewDownloadRuntime()
                }
            assertFailsWith<DownloadRuntimeException> { runtime.initialize() }
            runtime.initialize()
            assertEquals(2, calls)
            runtime.close()
            val started = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val pending =
                DeferredDownloadRuntime(this) {
                    try {
                        started.complete(Unit)
                        CompletableDeferred<Unit>().await()
                        PreviewDownloadRuntime()
                    } finally {
                        stopped.complete(Unit)
                    }
                }
            pending.warmUp()
            started.await()
            pending.close()
            stopped.await()
        }
}
