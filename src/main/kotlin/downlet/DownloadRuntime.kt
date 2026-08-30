package downlet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xml.sax.SAXException
import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
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
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream
import javax.swing.JFileChooser
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

internal data class DownloadRequest(
    val fixture: DownloadFixture,
    val mode: DownloadMode,
    val qualityIndex: Int,
    val destination: String,
)

internal interface DownloadRuntime {
    suspend fun preview(sourceUrl: String): DownloadFixture

    fun missingTools(): List<String> = emptyList()

    suspend fun installMissingTools() = Unit

    suspend fun resolve(sourceUrl: String): DownloadFixture

    suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (Int) -> Unit,
    )

    fun cancel()

    fun chooseDestination(current: String): String?

    fun openDestination(destination: String): String?
}

internal class PreviewDownloadRuntime : DownloadRuntime {
    override suspend fun preview(sourceUrl: String): DownloadFixture =
        DownloadFixtures.normal.copy(sourceUrl = sourceUrl)

    override suspend fun resolve(sourceUrl: String): DownloadFixture {
        delay(FAKE_RESOLUTION_MILLIS.milliseconds)
        return DownloadFixtures.normal.copy(sourceUrl = sourceUrl)
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (Int) -> Unit,
    ) {
        for (progress in fakeProgressSteps) {
            delay(FAKE_PROGRESS_INTERVAL_MILLIS.milliseconds)
            if (progress == COMPLETE_PROGRESS_PERCENT) return
            if (request.fixture.outcome == FakeDownloadOutcome.Failure(progress)) {
                throw DownloadRuntimeException()
            }
            onProgress(progress)
        }
    }

    override fun cancel() = Unit

    override fun chooseDestination(current: String): String {
        val currentIndex = readyDestinations.indexOf(current)
        return readyDestinations[(currentIndex + 1).mod(readyDestinations.size)]
    }

    override fun openDestination(destination: String) = OPEN_FOLDER_ACKNOWLEDGEMENT
}

@Suppress("TooManyFunctions")
internal class YtDlpDownloadRuntime(
    private val toolsDirectory: Path = defaultToolsDirectory(),
    private val environment: Map<String, String> = System.getenv(),
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build(),
) : DownloadRuntime {
    private val currentProcess = AtomicReference<Process?>()
    private val provisionedYtDlp = toolsDirectory.resolve("yt-dlp").resolve(YT_DLP_VERSION).resolve("yt-dlp.exe")
    private val provisionedQuickJs =
        toolsDirectory.resolve("quickjs-ng").resolve(QUICKJS_VERSION).resolve("qjs.exe")
    private val provisionedFfmpegDirectory = toolsDirectory.resolve("ffmpeg").resolve(FFMPEG_VERSION).resolve("bin")
    private val provisionedFfmpeg = provisionedFfmpegDirectory.resolve("ffmpeg.exe")
    private val provisionedFfprobe = provisionedFfmpegDirectory.resolve("ffprobe.exe")

    override fun missingTools(): List<String> =
        buildList {
            if (ytDlpExecutable() == null) add("yt-dlp")
            if (ffmpegExecutable() == null || ffprobeExecutable() == null) add("FFmpeg")
        }

    override suspend fun installMissingTools() {
        if (ytDlpExecutable() == null) installExecutable(YT_DLP_ASSET, provisionedYtDlp)
        if (ffmpegExecutable() == null || ffprobeExecutable() == null) {
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

    override suspend fun preview(sourceUrl: String): DownloadFixture {
        val metadata = requestPreview(sourceUrl)
        val thumbnailBytes = metadata.thumbnailUri?.let { runCatching { requestThumbnail(it) }.getOrNull() }
        return DownloadFixture(
            id = sourceUrl,
            sourceUrl = sourceUrl,
            title = metadata.title,
            channel = metadata.channel,
            duration = "",
            destination = defaultDownloadDirectory().toString(),
            thumbnailAvailable = false,
            thumbnailData = thumbnailBytes?.let(::ThumbnailData),
            canDownload = false,
        )
    }

    override suspend fun resolve(sourceUrl: String): DownloadFixture {
        ensureQuickJs()
        val output =
            execute(
                commonArguments() +
                    listOf(
                        "--skip-download",
                        "--replace-in-metadata",
                        "title,channel,uploader",
                        "[\\r\\n\\t]+",
                        " ",
                        "--print",
                        "DOWNLET_ID=%(id)s",
                        "--print",
                        "DOWNLET_TITLE=%(title)s",
                        "--print",
                        "DOWNLET_CHANNEL=%(channel|)s",
                        "--print",
                        "DOWNLET_UPLOADER=%(uploader|)s",
                        "--print",
                        "DOWNLET_DURATION=%(duration_string|Unknown duration)s",
                        sourceUrl,
                    ),
            )
        val metadata = parseMetadata(output)
        return DownloadFixture(
            id = metadata.getValue("ID"),
            sourceUrl = sourceUrl,
            title = metadata.getValue("TITLE"),
            channel =
                metadata["CHANNEL"]
                    .orEmpty()
                    .ifBlank { metadata["UPLOADER"].orEmpty() }
                    .ifBlank { "Unknown channel" },
            duration = metadata["DURATION"].orEmpty().ifBlank { "Unknown duration" },
            destination = defaultDownloadDirectory().toString(),
            thumbnailAvailable = false,
        )
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (Int) -> Unit,
    ) {
        ensureQuickJs()
        Files.createDirectories(Path.of(request.destination))
        var lastProgress = -1
        execute(downloadArguments(request)) { line ->
            parseProgress(line)?.takeIf { it != lastProgress }?.let { progress ->
                lastProgress = progress
                onProgress(progress)
            }
        }
    }

    override fun cancel() {
        currentProcess.get()?.let { process ->
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    override fun chooseDestination(current: String): String? {
        val chooser =
            JFileChooser(current).apply {
                dialogTitle = "Choose download folder"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
        return chooser
            .takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
            ?.selectedFile
            ?.absolutePath
    }

    override fun openDestination(destination: String): String? =
        runCatching {
            Desktop.getDesktop().open(Path.of(destination).toFile())
        }.fold(onSuccess = { null }, onFailure = { OPEN_FOLDER_FAILURE_MESSAGE })

    private suspend fun requestPreview(sourceUrl: String): OEmbedMetadata =
        withContext(Dispatchers.IO) {
            val encodedUrl = URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8)
            parseOEmbed(sendBounded(URI("$OEMBED_ENDPOINT?url=$encodedUrl&format=xml"), MAX_PREVIEW_BYTES))
        }

    private suspend fun requestThumbnail(uri: URI): ByteArray =
        withContext(Dispatchers.IO) {
            if (uri.scheme != "https" || !uri.host.equals(YOUTUBE_THUMBNAIL_HOST, ignoreCase = true)) {
                throw DownloadRuntimeException()
            }
            sendBounded(uri, MAX_THUMBNAIL_BYTES)
        }

    private fun sendBounded(
        uri: URI,
        maxBytes: Int,
    ): ByteArray {
        val request =
            HttpRequest
                .newBuilder(uri)
                .timeout(Duration.ofSeconds(PREVIEW_TIMEOUT_SECONDS))
                .header("User-Agent", "Downlet/$DOWNLET_DOWNLOAD_AGENT_VERSION")
                .GET()
                .build()
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) failRuntime()
            return response.body().use { input -> readBounded(input, maxBytes) }
        } catch (error: IOException) {
            failRuntime(error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
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

    private fun downloadArguments(request: DownloadRequest): List<String> =
        commonArguments() +
            listOf(
                "--newline",
                "--progress",
                "--progress-template",
                "download:DOWNLET_PROGRESS=%(progress._percent_str)s",
                "--paths",
                request.destination,
                "--output",
                "%(title).180B [%(id)s].%(ext)s",
            ) +
            qualityArguments(request.mode, request.qualityIndex) +
            request.fixture.sourceUrl

    private fun commonArguments(): List<String> =
        buildList {
            addAll(listOf("--ignore-config", "--encoding", "UTF-8", "--no-colors", "--no-playlist"))
            quickJsExecutable()?.let { addAll(listOf("--js-runtimes", "quickjs:$it")) }
            ffmpegExecutable()?.let { executable ->
                runCatching { Path.of(executable).parent }
                    .getOrNull()
                    ?.let { addAll(listOf("--ffmpeg-location", it.toString())) }
            }
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
            currentProcess.set(process)
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
                currentProcess.compareAndSet(process, null)
                if (process.isAlive) process.destroyForcibly()
            }
        }

    private fun ytDlpExecutable(): String? = configuredExecutable("DOWNLET_YT_DLP", provisionedYtDlp, "yt-dlp")

    private fun quickJsExecutable(): String? = configuredExecutable("DOWNLET_QUICKJS", provisionedQuickJs, "qjs")

    private fun ffmpegExecutable(): String? = configuredExecutable("DOWNLET_FFMPEG", provisionedFfmpeg, "ffmpeg")

    private fun ffprobeExecutable(): String? = configuredExecutable("DOWNLET_FFPROBE", provisionedFfprobe, "ffprobe")

    private fun configuredExecutable(
        environmentName: String,
        provisioned: Path,
        command: String,
    ): String? =
        environment[environmentName]
            ?.takeIf(String::isNotBlank)
            ?: provisioned.takeIf(Files::isRegularFile)?.toString()
            ?: findExecutableOnPath(command, environment)?.toString()

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
                        .timeout(Duration.ofMinutes(10))
                        .header("User-Agent", "Downlet/$DOWNLET_DOWNLOAD_AGENT_VERSION")
                        .GET()
                        .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
                if (response.statusCode() !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) throw DownloadRuntimeException()
                response.body().use { input ->
                    Files.newOutputStream(target).use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
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
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
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
    require(metadata["ID"].orEmpty().isNotBlank() && metadata["TITLE"].orEmpty().isNotBlank())
    return metadata
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

internal fun readBounded(
    input: InputStream,
    maxBytes: Int,
): ByteArray =
    input.readNBytes(maxBytes + 1).also { bytes ->
        if (bytes.size > maxBytes) throw DownloadRuntimeException()
    }

internal fun parseProgress(line: String): Int? =
    PROGRESS_PATTERN
        .find(line)
        ?.groupValues
        ?.get(1)
        ?.toDoubleOrNull()
        ?.roundToInt()
        ?.coerceIn(0, MAX_PROGRESS_BEFORE_COMPLETE)

internal fun qualityArguments(
    mode: DownloadMode,
    qualityIndex: Int,
): List<String> =
    when (mode) {
        DownloadMode.Video -> {
            val height = listOf(2160, 1440, 1080, 720, 480)[qualityIndex]
            listOf("--format", "bv*[height<=$height]+ba/b[height<=$height]")
        }

        DownloadMode.Audio -> {
            val quality = listOf("0", "160K", "128K")[qualityIndex]
            listOf("--format", "ba/b", "--extract-audio", "--audio-format", "mp3", "--audio-quality", quality)
        }
    }

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
    val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    if (!actual.equals(expected, ignoreCase = true)) throw DownloadRuntimeException()
}

private fun findExecutableOnPath(
    command: String,
    environment: Map<String, String>,
): Path? {
    val path = environment.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.value ?: return null
    val candidates = listOf("$command.exe", command)
    return path
        .split(java.io.File.pathSeparatorChar)
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { it.trim('"') }
        .flatMap { directory -> candidates.asSequence().map { candidate -> directory to candidate } }
        .mapNotNull { (directory, candidate) -> runCatching { Path.of(directory, candidate) }.getOrNull() }
        .firstOrNull(Files::isRegularFile)
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

private fun defaultDownloadDirectory(): Path = Path.of(System.getProperty("user.home"), "Downloads")

private fun defaultToolsDirectory(): Path =
    Path.of(
        System.getenv("LOCALAPPDATA")
            ?: Path.of(System.getProperty("user.home"), "AppData", "Local").toString(),
        "Downlet",
        "tools",
    )

private data class ToolAsset(
    val uri: URI,
    val sha256: String,
)

private const val MAX_PROGRESS_BEFORE_COMPLETE = 99
private const val MAX_CAPTURED_OUTPUT_LINES = 100
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
private const val MAX_PREVIEW_BYTES = 64 * 1024
private const val MAX_THUMBNAIL_BYTES = 5 * 1024 * 1024
private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val PREVIEW_TIMEOUT_SECONDS = 20L
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
        URI("https://github.com/yt-dlp/yt-dlp/releases/download/$YT_DLP_VERSION/yt-dlp.exe"),
        "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a",
    )
private val FFMPEG_ASSET =
    ToolAsset(
        URI("https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-$FFMPEG_VERSION-essentials_build.zip"),
        "fec81ae03971d9dd4be3ebe02e263bd2ec1d789483f931bdba5f5715e65da2e9",
    )
private val METADATA_PREFIXES =
    listOf(
        "DOWNLET_ID=",
        "DOWNLET_TITLE=",
        "DOWNLET_CHANNEL=",
        "DOWNLET_UPLOADER=",
        "DOWNLET_DURATION=",
    )
private val PROGRESS_PATTERN = Regex("DOWNLET_PROGRESS=\\s*([0-9]+(?:\\.[0-9]+)?)%")
