package downlet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Request cancellation never cancels the application-owned initialization. */
@Suppress("TooManyFunctions")
internal class DeferredDownloadRuntime(
    scope: CoroutineScope,
    private val factory: suspend () -> DownloadRuntime = { YtDlpDownloadRuntime() },
) : DownloadRuntime {
    private val initializationScope =
        CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private var pending: Deferred<DownloadRuntime>? = null

    @Volatile
    private var initialized: DownloadRuntime? = null

    @Synchronized
    private fun start(): Deferred<DownloadRuntime> =
        pending ?: initializationScope
            .async(Dispatchers.IO) {
                val runtime = factory()
                val context = currentCoroutineContext()
                try {
                    synchronized(this@DeferredDownloadRuntime) {
                        context.ensureActive()
                        initialized = runtime
                    }
                    runtime
                } catch (error: CancellationException) {
                    runtime.close()
                    throw error
                }
            }.also { pending = it }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun warmUp() {
        val runtime = start()
        initializationScope.launch(Dispatchers.IO) {
            try {
                runtime.await().toolStatus()
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                // A user operation will report unavailable tools through the normal setup flow.
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runtime(): DownloadRuntime {
        val attempt = start()
        return try {
            attempt.await()
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            synchronized(this) { if (pending === attempt) pending = null }
            throw DownloadRuntimeException(error, DownloadFailureReason.Tool)
        }
    }

    override suspend fun initialize() {
        runtime()
    }

    override suspend fun preview(source: YouTubeUrl) = runtime().preview(source)

    override suspend fun toolStatus() = runtime().toolStatus()

    override suspend fun installMissingTools(onProgress: (DownloadTool, String) -> Unit) =
        runtime().installMissingTools(onProgress)

    override suspend fun prepareDownload(request: DownloadRequest) = runtime().prepareDownload(request)

    override suspend fun repairManagedTools(
        tools: List<DownloadTool>,
        onProgress: (DownloadTool, String) -> Unit,
    ) = runtime().repairManagedTools(tools, onProgress)

    override suspend fun resolve(
        source: YouTubeUrl,
        browserCookies: BrowserCookieSource?,
    ) = runtime().resolve(source, browserCookies)

    override suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (DownloadProgress) -> Unit,
    ) = runtime().download(request, onProgress)

    override fun cancel() {
        initialized?.cancel()
    }

    @Synchronized
    override fun close() {
        initializationScope.cancel()
        initialized?.close()
    }

    override fun chooseDestination(current: Path) = initialized?.chooseDestination(current)

    override fun showInFolder(file: Path) = initialized?.showInFolder(file)
}
