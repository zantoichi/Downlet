package downlet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xml.sax.SAXException
import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

internal data class DownloadRequest(
    val item: DownloadItem,
    val quality: DownloadQuality,
)

internal interface DownloadRuntime {
    suspend fun preview(source: YouTubeUrl): DownloadItem

    fun missingTools(): List<DownloadTool> = emptyList()

    suspend fun installMissingTools() = Unit

    suspend fun resolve(source: YouTubeUrl): DownloadItem

    suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (DownloadProgress) -> Unit,
    )

    fun cancel()

    fun chooseDestination(current: Path): Path?

    fun openDestination(destination: Path): String?
}

internal class PreviewDownloadRuntime : DownloadRuntime {
    override suspend fun preview(source: YouTubeUrl): DownloadItem = DownloadFixtures.normal.copy(source = source)

    override suspend fun resolve(source: YouTubeUrl): DownloadItem {
        delay(FAKE_RESOLUTION_DELAY)
        return DownloadFixtures.normal.copy(source = source)
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (DownloadProgress) -> Unit,
    ) {
        for (progress in fakeProgressSteps) {
            delay(FAKE_PROGRESS_INTERVAL)
            if (request.item.source == DownloadFixtures.failure.source && progress == PREVIEW_FAILURE_PROGRESS) {
                throw DownloadRuntimeException()
            }
            onProgress(progress)
        }
        delay(FAKE_PROGRESS_INTERVAL)
        onProgress(DownloadProgress.Processing)
        delay(FAKE_PROGRESS_INTERVAL)
    }

    override fun cancel() = Unit

    override fun chooseDestination(current: Path): Path {
        val currentIndex = readyDestinations.indexOf(current)
        return readyDestinations[(currentIndex + 1).mod(readyDestinations.size)]
    }

    override fun openDestination(destination: Path) = ProductCopy.OPEN_FOLDER_ACKNOWLEDGEMENT
}

@Suppress("TooManyFunctions")
internal class YtDlpDownloadRuntime(
    private val toolsDirectory: Path = defaultToolsDirectory(),
    private val environment: Map<String, String> = System.getenv(),
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT.toJavaDuration())
            .build(),
) : DownloadRuntime {
    private val currentProcess = AtomicReference<ProcessExecution?>()
    private val provisionedYtDlp = toolsDirectory.resolve("yt-dlp").resolve(YT_DLP_VERSION).resolve("yt-dlp.exe")
    private val provisionedQuickJs =
        toolsDirectory.resolve("quickjs-ng").resolve(QUICKJS_VERSION).resolve("qjs.exe")
    private val provisionedFfmpegDirectory = toolsDirectory.resolve("ffmpeg").resolve(FFMPEG_VERSION).resolve("bin")
    private val provisionedFfmpeg = provisionedFfmpegDirectory.resolve("ffmpeg.exe")
    private val provisionedFfprobe = provisionedFfmpegDirectory.resolve("ffprobe.exe")

    private class ProcessExecution(
        private val process: Process,
    ) {
        @Volatile
        private var terminatedHandles: List<ProcessHandle>? = null

        @Synchronized
        fun terminate(): List<ProcessHandle> =
            terminatedHandles ?: terminateProcessTree(process).also { terminatedHandles = it }

        fun awaitTermination() {
            awaitProcessTreeTermination(process, terminate())
        }
    }

    override fun missingTools(): List<DownloadTool> =
        buildList {
            if (ytDlpExecutable() == null) add(DownloadTool.YtDlp)
            if (ffmpegTools() == null) add(DownloadTool.Ffmpeg)
        }

    override suspend fun installMissingTools() {
        if (ytDlpExecutable() == null) installExecutable(YT_DLP_ASSET, provisionedYtDlp)
        if (ffmpegTools() == null) {
            installZipEntries(
                FFMPEG_ASSET,
                mapOf(
                    "bin/ffmpeg.exe" to provisionedFfmpeg,
                    "bin/ffprobe.exe" to provisionedFfprobe,
                ),
            )
        }
        if (missingTools().isNotEmpty()) throw DownloadRuntimeException()
    }

    override suspend fun preview(source: YouTubeUrl): DownloadItem {
        val metadata = requestPreview(source)
        val thumbnailBytes =
            metadata.thumbnailUri?.let { uri ->
                try {
                    requestThumbnail(uri)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
        return DownloadItem(
            source = source,
            title = metadata.title,
            channel = metadata.channel,
            duration = null,
            destination = defaultDownloadDirectory(),
            thumbnail =
                thumbnailBytes
                    ?.let(::ThumbnailData)
                    ?.let(MediaThumbnail::Remote)
                    ?: MediaThumbnail.Unavailable,
        )
    }

    override suspend fun resolve(source: YouTubeUrl): DownloadItem {
        ensureQuickJs()
        val output =
            execute(
                commonArguments() +
                    listOf(
                        "--skip-download",
                        "--format",
                        "ba",
                        "--replace-in-metadata",
                        "title,channel,uploader",
                        "[\\r\\n\\t]+",
                        " ",
                        "--print",
                        "DOWNLET_TITLE=%(title)s",
                        "--print",
                        "DOWNLET_CHANNEL=%(channel|)s",
                        "--print",
                        "DOWNLET_UPLOADER=%(uploader|)s",
                        "--print",
                        "DOWNLET_DURATION_SECONDS=%(duration|)s",
                        "--print",
                        "DOWNLET_AUDIO_FORMAT=%(ext|)s",
                        "--print",
                        "DOWNLET_AUDIO_BITRATE_KBPS=%(abr,tbr|)s",
                        source.toString(),
                    ),
            )
        val metadata = parseMetadata(output)
        return DownloadItem(
            source = source,
            title = metadata.getValue("TITLE"),
            channel =
                metadata["CHANNEL"]
                    .orEmpty()
                    .ifBlank { metadata["UPLOADER"].orEmpty() }
                    .ifBlank { "Unknown channel" },
            duration = metadata["DURATION_SECONDS"]?.toDoubleOrNull()?.seconds,
            destination = defaultDownloadDirectory(),
            originalAudio = parseOriginalAudio(metadata),
            thumbnail = MediaThumbnail.Unavailable,
        )
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (DownloadProgress) -> Unit,
    ) {
        ensureQuickJs()
        val attempt = withContext(Dispatchers.IO) { createDownloadAttempt(request.item.destination) }
        var publishedFile: Path? = null
        try {
            var lastProgress: DownloadProgress? = null
            execute(downloadArguments(request, attempt)) { line ->
                parseProgress(line)?.takeIf { it != lastProgress }?.let { progress ->
                    lastProgress = progress
                    onProgress(progress)
                }
            }
            currentCoroutineContext().ensureActive()
            publishedFile =
                withContext(NonCancellable + Dispatchers.IO) {
                    publishStagedDownload(attempt.output, request.item.destination)
                }
            currentCoroutineContext().ensureActive()
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                publishedFile?.let(Files::deleteIfExists)
            }
            throw error
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                deleteRecursively(attempt.root)
            }
        }
    }

    override fun cancel() {
        currentProcess.get()?.terminate()
    }

    override fun chooseDestination(current: Path): Path? = WindowsFolderPicker.choose(current)

    override fun openDestination(destination: Path): String? =
        runCatching {
            Desktop.getDesktop().open(destination.toFile())
        }.fold(onSuccess = { null }, onFailure = { ProductCopy.OPEN_FOLDER_FAILURE_MESSAGE })

    private suspend fun requestPreview(source: YouTubeUrl): OEmbedMetadata =
        URLEncoder.encode(source.toString(), StandardCharsets.UTF_8).let { encodedUrl ->
            parseOEmbed(sendBounded(URI("$OEMBED_ENDPOINT?url=$encodedUrl&format=xml"), MAX_PREVIEW_BYTES))
        }

    private suspend fun requestThumbnail(uri: URI): ByteArray =
        if (uri.scheme != "https" || !uri.host.equals(YOUTUBE_THUMBNAIL_HOST, ignoreCase = true)) {
            throw DownloadRuntimeException()
        } else {
            sendBounded(uri, MAX_THUMBNAIL_BYTES)
        }

    private suspend fun sendBounded(
        uri: URI,
        maxBytes: Int,
    ): ByteArray {
        val request =
            HttpRequest
                .newBuilder(uri)
                .timeout(PREVIEW_TIMEOUT.toJavaDuration())
                .header("User-Agent", "Downlet/$DOWNLET_DOWNLOAD_AGENT_VERSION")
                .GET()
                .build()
        try {
            val response =
                httpClient
                    .sendAsync(
                        request,
                        HttpResponse.BodyHandlers.limiting(
                            HttpResponse.BodyHandlers.ofByteArray(),
                            maxBytes.toLong(),
                        ),
                    ).awaitCancellable()
            if (response.statusCode() !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) failRuntime()
            return response.body()
        } catch (error: IOException) {
            failRuntime(error)
        }
    }

    private suspend fun ensureQuickJs() {
        if (quickJsExecutable() != null) return
        withContext(Dispatchers.IO) {
            val staged = stagedFile(provisionedQuickJs)
            try {
                YtDlpDownloadRuntime::class.java.getResourceAsStream(QUICKJS_RESOURCE_PATH)?.use { input ->
                    Files.newOutputStream(staged).use(input::copyTo)
                } ?: failRuntime()
                requireSha256(staged, QUICKJS_SHA256)
                moveReplacing(staged, provisionedQuickJs)
                provisionedQuickJs.toFile().setExecutable(true)
            } catch (error: IOException) {
                failRuntime(error)
            } catch (error: SecurityException) {
                failRuntime(error)
            } finally {
                Files.deleteIfExists(staged)
            }
        }
    }

    private fun downloadArguments(
        request: DownloadRequest,
        attempt: DownloadAttempt,
    ): List<String> =
        commonArguments() +
            listOf(
                "--newline",
                "--progress",
                "--progress-template",
                "download:DOWNLET_TRANSFER=%(progress.status)s|%(progress._percent_str)s",
                "--progress-template",
                "postprocess:DOWNLET_PROCESSING=%(progress.status)s",
                "--paths",
                "home:${attempt.output}",
                "--paths",
                "temp:${attempt.working}",
                "--output",
                "%(title).180B [%(id)s].%(ext)s",
            ) +
            request.quality.ytDlpArguments +
            request.item.source.toString()

    private fun commonArguments(): List<String> =
        buildList {
            addAll(listOf("--ignore-config", "--encoding", "UTF-8", "--no-colors", "--no-playlist"))
            addAll(quickJsArguments(quickJsExecutable()))
            addAll(ffmpegLocationArguments(ffmpegTools()))
        }

    private suspend fun execute(
        arguments: List<String>,
        onLine: suspend (String) -> Unit = {},
    ): List<String> =
        withContext(Dispatchers.IO) {
            val executable = ytDlpExecutable() ?: throw DownloadRuntimeException()
            val process =
                try {
                    ProcessBuilder(listOf(executable) + arguments)
                        .redirectErrorStream(true)
                        .start()
                } catch (_: IOException) {
                    throw DownloadRuntimeException()
                }
            val execution = ProcessExecution(process)
            currentProcess.set(execution)
            val output = ArrayDeque<String>()
            try {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        currentCoroutineContext().ensureActive()
                        if (output.size == MAX_CAPTURED_OUTPUT_LINES) output.removeFirst()
                        output.addLast(line)
                        onLine(line)
                    }
                }
                if (process.waitFor() != 0) throw DownloadRuntimeException()
                output.toList()
            } finally {
                currentProcess.compareAndSet(execution, null)
                execution.awaitTermination()
            }
        }

    private fun ytDlpExecutable(): String? =
        resolveExecutable("DOWNLET_YT_DLP", provisionedYtDlp, "yt-dlp", environment)?.toString()

    private fun quickJsExecutable(): String? =
        resolveExecutable("DOWNLET_QUICKJS", provisionedQuickJs, "qjs", environment)?.toString()

    private fun ffmpegTools(): FfmpegTools? = resolveFfmpegTools(environment, provisionedFfmpegDirectory)

    private suspend fun installExecutable(
        asset: ToolAsset,
        target: Path,
    ) {
        val downloaded = downloadVerified(asset)
        try {
            Files.createDirectories(target.parent)
            moveReplacing(downloaded, target)
            target.toFile().setExecutable(true)
        } finally {
            Files.deleteIfExists(downloaded)
        }
    }

    @Suppress("ThrowsCount")
    private suspend fun installZipEntries(
        asset: ToolAsset,
        entries: Map<String, Path>,
    ) {
        val downloaded = downloadVerified(asset)
        val staged = entries.mapValues { (_, target) -> stagedFile(target) }
        try {
            val found = mutableSetOf<String>()
            ZipInputStream(Files.newInputStream(downloaded)).use { archive ->
                var entry = archive.nextEntry
                while (entry != null) {
                    val requested = entries.keys.firstOrNull { entry.name == it || entry.name.endsWith("/$it") }
                    if (requested != null && !entry.isDirectory) {
                        Files.newOutputStream(staged.getValue(requested)).use(archive::copyTo)
                        found += requested
                    }
                    archive.closeEntry()
                    entry = archive.nextEntry
                }
            }
            if (!found.containsAll(entries.keys)) throw DownloadRuntimeException()
            entries.forEach { (entry, target) ->
                Files.createDirectories(target.parent)
                moveReplacing(staged.getValue(entry), target)
                target.toFile().setExecutable(true)
            }
        } catch (error: DownloadRuntimeException) {
            throw error
        } catch (error: IOException) {
            throw DownloadRuntimeException(error)
        } catch (error: SecurityException) {
            throw DownloadRuntimeException(error)
        } finally {
            Files.deleteIfExists(downloaded)
            staged.values.forEach(Files::deleteIfExists)
        }
    }

    private suspend fun downloadVerified(asset: ToolAsset): Path =
        withContext(Dispatchers.IO) {
            Files.createDirectories(toolsDirectory)
            val target = Files.createTempFile(toolsDirectory, ".downlet-download-", ".tmp")
            try {
                val request =
                    HttpRequest
                        .newBuilder(asset.uri)
                        .timeout(DOWNLOAD_TIMEOUT.toJavaDuration())
                        .header("User-Agent", "Downlet/$DOWNLET_DOWNLOAD_AGENT_VERSION")
                        .GET()
                        .build()
                val response =
                    httpClient
                        .sendAsync(request, HttpResponse.BodyHandlers.ofFile(target))
                        .awaitCancellable()
                if (response.statusCode() !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) throw DownloadRuntimeException()
                requireSha256(target, asset.sha256)
                target
            } catch (error: CancellationException) {
                Files.deleteIfExists(target)
                throw error
            } catch (error: DownloadRuntimeException) {
                Files.deleteIfExists(target)
                throw error
            } catch (error: IOException) {
                Files.deleteIfExists(target)
                throw DownloadRuntimeException(error)
            } catch (error: SecurityException) {
                Files.deleteIfExists(target)
                throw DownloadRuntimeException(error)
            }
        }

    private fun stagedFile(target: Path): Path {
        Files.createDirectories(target.parent)
        return Files.createTempFile(target.parent, ".${target.fileName}-", ".tmp")
    }
}

internal class DownloadRuntimeException(
    cause: Throwable? = null,
) : RuntimeException(cause)

private fun failRuntime(cause: Throwable? = null): Nothing = throw DownloadRuntimeException(cause)

internal fun parseMetadata(output: List<String>): Map<String, String> {
    val metadata =
        output
            .mapNotNull { line ->
                METADATA_PREFIXES.firstNotNullOfOrNull { prefix ->
                    line
                        .removePrefix(prefix)
                        .takeIf { it != line }
                        ?.let { prefix.removeSurrounding("DOWNLET_", "=") to it }
                }
            }.toMap()
    require(metadata["TITLE"].orEmpty().isNotBlank())
    return metadata
}

internal fun parseOriginalAudio(metadata: Map<String, String>): OriginalAudio? =
    metadata["AUDIO_FORMAT"]
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { format ->
            OriginalAudio(
                format = format,
                bitRateKilobitsPerSecond =
                    metadata["AUDIO_BITRATE_KBPS"]
                        ?.toDoubleOrNull()
                        ?.roundToInt()
                        ?.takeIf { it > 0 },
            )
        }

internal data class OEmbedMetadata(
    val title: String,
    val channel: String,
    val thumbnailUri: URI?,
)

internal fun parseOEmbed(bytes: ByteArray): OEmbedMetadata =
    try {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

        fun requiredText(tagName: String): String =
            document
                .getElementsByTagName(tagName)
                .item(0)
                ?.textContent
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: throw DownloadRuntimeException()

        val thumbnailUri =
            document
                .getElementsByTagName("thumbnail_url")
                .item(0)
                ?.textContent
                ?.trim()
                ?.let { value -> runCatching { URI(value) }.getOrNull() }
                ?.takeIf { uri ->
                    uri.scheme == "https" && uri.host.equals(YOUTUBE_THUMBNAIL_HOST, ignoreCase = true)
                }
        OEmbedMetadata(
            title = requiredText("title"),
            channel = requiredText("author_name"),
            thumbnailUri = thumbnailUri,
        )
    } catch (error: DownloadRuntimeException) {
        throw error
    } catch (error: ParserConfigurationException) {
        failRuntime(error)
    } catch (error: SAXException) {
        failRuntime(error)
    } catch (error: IOException) {
        failRuntime(error)
    }

internal suspend fun <T> CompletableFuture<T>.awaitCancellable(): T =
    suspendCancellableCoroutine { continuation ->
        whenComplete { value, error ->
            continuation.resumeWith(
                if (error == null) {
                    Result.success(value)
                } else {
                    Result.failure((error as? CompletionException)?.cause ?: error)
                },
            )
        }
        continuation.invokeOnCancellation { cancel(true) }
    }

internal fun parseProgress(line: String): DownloadProgress? {
    if (PROCESSING_PATTERN.matches(line)) return DownloadProgress.Processing
    return TRANSFER_PROGRESS_PATTERN
        .matchEntire(line)
        ?.groupValues
        ?.get(1)
        ?.toDoubleOrNull()
        ?.roundToInt()
        ?.coerceIn(0, MAX_TRANSFER_PERCENT)
        ?.let(DownloadProgress::Transferring)
}

internal fun quickJsArguments(executable: String?): List<String> =
    executable?.let { listOf("--no-js-runtimes", "--js-runtimes", "quickjs:$it") }.orEmpty()

internal fun requireSha256(
    path: Path,
    expected: String,
) {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    val actual = HexFormat.of().formatHex(digest.digest())
    if (!actual.equals(expected, ignoreCase = true)) throw DownloadRuntimeException()
}

private fun moveReplacing(
    source: Path,
    target: Path,
) {
    try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun defaultDownloadDirectory(): Path = WindowsKnownFolders.downloads()

internal fun defaultToolsDirectory(): Path = WindowsKnownFolders.localAppData().resolve("Downlet").resolve("tools")

private data class ToolAsset(
    val tool: DownloadTool,
    val uri: URI,
    val sha256: String,
)

private const val MAX_CAPTURED_OUTPUT_LINES = 100
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
private const val MAX_PREVIEW_BYTES = 64 * 1024
private const val MAX_THUMBNAIL_BYTES = 5 * 1024 * 1024
private val CONNECT_TIMEOUT: Duration = 30.seconds
private val PREVIEW_TIMEOUT: Duration = 20.seconds
private val DOWNLOAD_TIMEOUT: Duration = 10.minutes
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
private const val DOWNLET_DOWNLOAD_AGENT_VERSION = "0.1"
private const val YT_DLP_VERSION = "2026.08.19"
private const val QUICKJS_VERSION = "0.16.2"
private const val FFMPEG_VERSION = "9.0.1"
private const val OEMBED_ENDPOINT = "https://www.youtube.com/oembed"
private const val YOUTUBE_THUMBNAIL_HOST = "i.ytimg.com"
private const val QUICKJS_RESOURCE_PATH = "/tools/quickjs-ng/$QUICKJS_VERSION/qjs.exe"
private const val QUICKJS_SHA256 = "7b27412de844403545bd151fbe49191b4d5b91a9e15b5db7c863fea54639a82b"
private val YT_DLP_ASSET =
    ToolAsset(
        DownloadTool.YtDlp,
        URI("https://github.com/yt-dlp/yt-dlp/releases/download/$YT_DLP_VERSION/yt-dlp.exe"),
        "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a",
    )
private val FFMPEG_ASSET =
    ToolAsset(
        DownloadTool.Ffmpeg,
        URI("https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-$FFMPEG_VERSION-essentials_build.zip"),
        "fec81ae03971d9dd4be3ebe02e263bd2ec1d789483f931bdba5f5715e65da2e9",
    )
private val METADATA_PREFIXES =
    listOf(
        "DOWNLET_TITLE=",
        "DOWNLET_CHANNEL=",
        "DOWNLET_UPLOADER=",
        "DOWNLET_DURATION_SECONDS=",
        "DOWNLET_AUDIO_FORMAT=",
        "DOWNLET_AUDIO_BITRATE_KBPS=",
    )
private val TRANSFER_PROGRESS_PATTERN = Regex("DOWNLET_TRANSFER=downloading\\|\\s*([0-9]+(?:\\.[0-9]+)?)%")
private val PROCESSING_PATTERN = Regex("DOWNLET_PROCESSING=(?:started|processing)")
private val PREVIEW_FAILURE_PROGRESS = DownloadProgress.Transferring(68)
