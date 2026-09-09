@file:Suppress("TooManyFunctions")

package downlet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xml.sax.SAXException
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
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.HexFormat
import java.util.LinkedHashMap
import java.util.Locale
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
    val browserCookies: BrowserCookieSource? = null,
)

internal data class ToolStatus(
    val missing: List<DownloadTool> = emptyList(),
    val repairable: List<DownloadTool> = emptyList(),
) {
    init {
        require(missing.distinct().size == missing.size)
        require(repairable.distinct().size == repairable.size)
        require(missing.none(repairable::contains))
    }
}

internal interface DownloadRuntime {
    suspend fun preview(source: YouTubeUrl): DownloadItem

    suspend fun toolStatus(): ToolStatus = ToolStatus()

    suspend fun installMissingTools() = Unit

    suspend fun repairManagedTools(tools: List<DownloadTool>) = Unit

    suspend fun resolve(
        source: YouTubeUrl,
        browserCookies: BrowserCookieSource? = null,
    ): DownloadItem

    suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): Path

    fun cancel()

    fun chooseDestination(current: Path): Path?

    fun showInFolder(file: Path): String?
}

internal class SessionMediaCache(
    private val maximumEntries: Int = SESSION_CACHE_MAX_ENTRIES,
    private val maximumThumbnailBytes: Int = SESSION_CACHE_MAX_THUMBNAIL_BYTES,
) {
    private data class Entry(
        var preview: DownloadItem? = null,
        var resolved: DownloadItem? = null,
    )

    private val entries = LinkedHashMap<YouTubeUrl, Entry>(maximumEntries, 0.75f, true)

    @Synchronized
    fun preview(source: YouTubeUrl): DownloadItem? = entries[source]?.preview

    @Synchronized
    fun resolved(source: YouTubeUrl): DownloadItem? = entries[source]?.resolved

    @Synchronized
    fun putPreview(item: DownloadItem) {
        entries.getOrPut(item.source, ::Entry).preview = item
        trim()
    }

    @Synchronized
    fun putResolved(item: DownloadItem) {
        entries.getOrPut(item.source, ::Entry).resolved = item
        trim()
    }

    @Synchronized
    internal fun size(): Int = entries.size

    private fun trim() {
        while (entries.size > maximumEntries || retainedThumbnailBytes() > maximumThumbnailBytes) {
            entries.entries.iterator().run {
                next()
                remove()
            }
        }
    }

    private fun retainedThumbnailBytes(): Int =
        entries.values.sumOf { entry ->
            ((entry.preview?.thumbnail as? MediaThumbnail.Remote)?.data?.bytes?.size).orZero()
        }
}

internal class PreviewDownloadRuntime : DownloadRuntime {
    override suspend fun preview(source: YouTubeUrl): DownloadItem = DownloadFixtures.normal.copy(source = source)

    override suspend fun resolve(
        source: YouTubeUrl,
        browserCookies: BrowserCookieSource?,
    ): DownloadItem {
        delay(FAKE_RESOLUTION_DELAY)
        return DownloadFixtures.normal.copy(source = source)
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): Path {
        for (progress in fakeProgressSteps) {
            delay(FAKE_PROGRESS_INTERVAL)
            if (request.item.source == DownloadFixtures.failure.source && progress == PREVIEW_FAILURE_PROGRESS) {
                throw DownloadRuntimeException()
            }
            onProgress(progress)
        }
        delay(FAKE_PROGRESS_INTERVAL)
        onProgress(DownloadProgress.Processing(DownloadProcessingStage.Merging))
        delay(FAKE_PROGRESS_INTERVAL)
        return DownloadFixtures.completedFile(request.item)
    }

    override fun cancel() = Unit

    override fun chooseDestination(current: Path): Path {
        val currentIndex = readyDestinations.indexOf(current)
        return readyDestinations[(currentIndex + 1).mod(readyDestinations.size)]
    }

    override fun showInFolder(file: Path) = ProductCopy.SHOW_IN_FOLDER_ACKNOWLEDGEMENT
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
    private val cache = SessionMediaCache()
    private val integrity = ManagedToolIntegrity()
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

    override suspend fun toolStatus(): ToolStatus =
        withContext(Dispatchers.IO) {
            val missing = mutableListOf<DownloadTool>()
            val repairable = mutableListOf<DownloadTool>()

            if (ytDlpExecutable() == null) {
                (if (Files.exists(provisionedYtDlp.parent)) repairable else missing) += DownloadTool.YtDlp
            }
            if (ffmpegTools() == null) {
                (if (Files.exists(provisionedFfmpegDirectory)) repairable else missing) += DownloadTool.Ffmpeg
            }
            ToolStatus(missing, repairable)
        }

    override suspend fun installMissingTools() {
        installManagedTools(toolStatus().missing)
        if (toolStatus().missing.isNotEmpty()) throw DownloadRuntimeException()
    }

    override suspend fun repairManagedTools(tools: List<DownloadTool>) {
        require(tools.isNotEmpty() && tools.distinct().size == tools.size)
        installManagedTools(tools)
        val status = toolStatus()
        if (tools.any { it in status.missing || it in status.repairable }) throw DownloadRuntimeException()
    }

    override suspend fun preview(source: YouTubeUrl): DownloadItem {
        cache.preview(source)?.let { return it }
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
        ).also(cache::putPreview)
    }

    override suspend fun resolve(
        source: YouTubeUrl,
        browserCookies: BrowserCookieSource?,
    ): DownloadItem {
        if (browserCookies == null) cache.resolved(source)?.let { return it }
        ensureQuickJs()
        val output =
            execute(
                commonArguments(browserCookies) +
                    listOf(
                        "--skip-download",
                        "--format",
                        "ba",
                        "--print",
                        "DOWNLET_MEDIA_JSON=%(.{title,channel,uploader,duration})j",
                        "--print",
                        "DOWNLET_FORMATS_JSON=%(formats.:.{format_id,width,height,fps,vbr,tbr,abr,filesize," +
                            "filesize_approx,vcodec,acodec,ext,dynamic_range})j",
                        source.toString(),
                    ),
            )
        val metadata = parseResolvedMedia(output)
        val item =
            DownloadItem(
                source = source,
                title = metadata.title,
                channel = metadata.channel,
                duration = metadata.duration,
                destination = defaultDownloadDirectory(),
                originalAudio = metadata.originalAudio,
                videoQualities = metadata.videoQualities,
                thumbnail = MediaThumbnail.Unavailable,
            )
        if (browserCookies == null) cache.putResolved(item)
        return item
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): Path {
        ensureQuickJs()
        val attempt = withContext(Dispatchers.IO) { createDownloadAttempt(request.item.destination) }
        var publishedFile: Path? = null
        try {
            val tracker = DownloadProgressTracker()
            var lastProgress: DownloadProgress? = null
            execute(downloadArguments(request, attempt)) { line ->
                parseYtDlpProgressEvent(line)
                    ?.let(tracker::accept)
                    ?.takeIf { it != lastProgress }
                    ?.let { progress ->
                        lastProgress = progress
                        onProgress(progress)
                    }
            }
            currentCoroutineContext().ensureActive()
            val published =
                withContext(NonCancellable + Dispatchers.IO) {
                    publishStagedDownload(attempt.output, request.item.destination)
                }
            publishedFile = published
            currentCoroutineContext().ensureActive()
            return published
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

    override fun showInFolder(file: Path): String? =
        if (WindowsFileRevealer.reveal(file)) null else ProductCopy.SHOW_IN_FOLDER_FAILURE_MESSAGE

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
            if (response.statusCode() !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                failRuntime(reason = failureReasonForHttpStatus(response.statusCode()))
            }
            return response.body()
        } catch (error: IOException) {
            failRuntime(error, DownloadFailureReason.Network)
        }
    }

    private suspend fun ensureQuickJs() =
        withContext(Dispatchers.IO) {
            if (quickJsExecutable() != null) return@withContext
            val staged = stagedFile(provisionedQuickJs)
            try {
                YtDlpDownloadRuntime::class.java.getResourceAsStream(QUICKJS_RESOURCE_PATH)?.use { input ->
                    Files.newOutputStream(staged).use(input::copyTo)
                } ?: failRuntime()
                requireSha256(staged, QUICKJS_SHA256)
                moveReplacing(staged, provisionedQuickJs)
                provisionedQuickJs.toFile().setExecutable(true)
                integrity.invalidate(provisionedQuickJs)
            } catch (error: IOException) {
                failRuntime(error)
            } catch (error: SecurityException) {
                failRuntime(error)
            } finally {
                Files.deleteIfExists(staged)
            }
            if (quickJsExecutable() == null) throw DownloadRuntimeException(reason = DownloadFailureReason.Tool)
        }

    private suspend fun downloadArguments(
        request: DownloadRequest,
        attempt: DownloadAttempt,
    ): List<String> =
        commonArguments(request.browserCookies) +
            listOf(
                "--no-simulate",
                "--newline",
                "--progress",
                "--output-na-placeholder",
                "NA",
                "--progress-delta",
                "0.25",
                "--print",
                "before_dl:DOWNLET_PLAN=%(filesize)s|%(filesize_approx)s|" +
                    "%(requested_formats.0.filesize)s|%(requested_formats.0.filesize_approx)s|" +
                    "%(requested_formats.1.filesize)s|%(requested_formats.1.filesize_approx)s",
                "--progress-template",
                "download:DOWNLET_TRANSFER=%(progress.status)s|%(progress.downloaded_bytes)s|" +
                    "%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s",
                "--progress-template",
                "postprocess:DOWNLET_PROCESSING=%(progress.status)s|%(progress.postprocessor)s",
                "--paths",
                "home:${attempt.output}",
                "--paths",
                "temp:${attempt.working}",
                "--output",
                "%(title).180B [%(id)s].%(ext)s",
            ) +
            request.quality.ytDlpArguments +
            request.item.source.toString()

    private suspend fun commonArguments(browserCookies: BrowserCookieSource? = null): List<String> =
        withContext(Dispatchers.IO) {
            buildList {
                addAll(listOf("--ignore-config", "--encoding", "UTF-8", "--no-colors", "--no-playlist"))
                addAll(browserCookieArguments(browserCookies))
                addAll(quickJsArguments(quickJsExecutable()))
                addAll(ffmpegLocationArguments(ffmpegTools()))
            }
        }

    private suspend fun execute(
        arguments: List<String>,
        onLine: suspend (String) -> Unit = {},
    ): List<String> =
        withContext(Dispatchers.IO) {
            val executable =
                ytDlpExecutable()
                    ?: throw DownloadRuntimeException(reason = DownloadFailureReason.Tool)
            val managedYtDlpInUse =
                runCatching {
                    Path.of(executable).toAbsolutePath().normalize() ==
                        provisionedYtDlp.toAbsolutePath().normalize()
                }.getOrDefault(false)
            val managedFfmpegInUse = arguments.contains(provisionedFfmpegDirectory.toString())
            val process =
                try {
                    ProcessBuilder(listOf(executable) + arguments)
                        .redirectErrorStream(true)
                        .start()
                } catch (error: IOException) {
                    throw DownloadRuntimeException(
                        error,
                        DownloadFailureReason.Tool,
                        repairableTools = if (managedYtDlpInUse) listOf(DownloadTool.YtDlp) else emptyList(),
                    )
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
                if (process.waitFor() != 0) {
                    val diagnostics = output.toList()
                    val reason = classifyDownloadFailure(diagnostics)
                    throw DownloadRuntimeException(
                        reason = reason,
                        diagnostics = diagnostics,
                        repairableTools =
                            if (
                                reason == DownloadFailureReason.Tool &&
                                managedFfmpegInUse &&
                                isFfmpegToolFailure(diagnostics)
                            ) {
                                listOf(DownloadTool.Ffmpeg)
                            } else {
                                emptyList()
                            },
                    )
                }
                output.toList()
            } finally {
                currentProcess.compareAndSet(execution, null)
                execution.awaitTermination()
            }
        }

    private fun ytDlpExecutable(): String? =
        resolveExecutable(
            "DOWNLET_YT_DLP",
            provisionedYtDlp.takeIf { integrity.isValid(it, YT_DLP_ASSET.sha256) },
            "yt-dlp",
            environment,
        )?.toString()

    private fun quickJsExecutable(): String? =
        resolveExecutable(
            "DOWNLET_QUICKJS",
            provisionedQuickJs.takeIf { integrity.isValid(it, QUICKJS_SHA256) },
            "qjs",
            environment,
        )?.toString()

    private fun ffmpegTools(): FfmpegTools? =
        resolveFfmpegTools(
            environment,
            provisionedFfmpegDirectory.takeIf {
                integrity.isValid(provisionedFfmpeg, FFMPEG_EXECUTABLE_SHA256) &&
                    integrity.isValid(provisionedFfprobe, FFPROBE_EXECUTABLE_SHA256)
            },
        )

    private suspend fun installManagedTools(tools: List<DownloadTool>) {
        tools.forEach { tool ->
            when (tool) {
                DownloadTool.YtDlp -> {
                    installExecutable(YT_DLP_ASSET, provisionedYtDlp)
                }

                DownloadTool.Ffmpeg -> {
                    installZipEntries(
                        FFMPEG_ASSET,
                        mapOf(
                            "bin/ffmpeg.exe" to provisionedFfmpeg,
                            "bin/ffprobe.exe" to provisionedFfprobe,
                        ),
                        mapOf(
                            "bin/ffmpeg.exe" to FFMPEG_EXECUTABLE_SHA256,
                            "bin/ffprobe.exe" to FFPROBE_EXECUTABLE_SHA256,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun installExecutable(
        asset: ToolAsset,
        target: Path,
    ) {
        val downloaded = downloadVerified(asset)
        try {
            Files.createDirectories(target.parent)
            moveReplacing(downloaded, target)
            target.toFile().setExecutable(true)
            integrity.invalidate(target)
        } finally {
            Files.deleteIfExists(downloaded)
        }
    }

    @Suppress("ThrowsCount")
    private suspend fun installZipEntries(
        asset: ToolAsset,
        entries: Map<String, Path>,
        expectedHashes: Map<String, String>,
    ) {
        require(entries.keys == expectedHashes.keys)
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
            expectedHashes.forEach { (entry, expected) -> requireSha256(staged.getValue(entry), expected) }
            entries.forEach { (entry, target) ->
                Files.createDirectories(target.parent)
                moveReplacing(staged.getValue(entry), target)
                target.toFile().setExecutable(true)
                integrity.invalidate(target)
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

    internal suspend fun downloadVerified(asset: ToolAsset): Path =
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
                        .sendAsync(
                            request,
                            HttpResponse.BodyHandlers.limiting(
                                HttpResponse.BodyHandlers.ofFile(target),
                                asset.maxBytes,
                            ),
                        ).awaitCancellable()
                if (response.statusCode() !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                    throw DownloadRuntimeException(reason = DownloadFailureReason.Tool)
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
                throw DownloadRuntimeException(error, DownloadFailureReason.Tool)
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
    val reason: DownloadFailureReason = DownloadFailureReason.Unknown,
    diagnostics: List<String> = emptyList(),
    val repairableTools: List<DownloadTool> = emptyList(),
) : RuntimeException(cause) {
    val diagnostics: List<String> = diagnostics.takeLast(MAX_DIAGNOSTIC_LINES)
}

internal class ManagedToolIntegrity(
    private val digest: (Path) -> String = ::sha256,
) {
    private data class Verification(
        val expected: String,
        val size: Long,
        val modifiedMillis: Long,
    )

    private val verified = mutableMapOf<Path, Verification>()

    @Synchronized
    fun isValid(
        path: Path,
        expected: String,
    ): Boolean =
        runCatching {
            val normalized = path.toAbsolutePath().normalize()
            val attributes = Files.readAttributes(normalized, BasicFileAttributes::class.java)
            if (!attributes.isRegularFile) return false
            val verification =
                Verification(
                    expected.lowercase(Locale.ROOT),
                    attributes.size(),
                    attributes.lastModifiedTime().toMillis(),
                )
            if (verified[normalized] == verification) return true
            if (!digest(normalized).equals(expected, ignoreCase = true)) {
                verified.remove(normalized)
                return false
            }
            verified[normalized] = verification
            true
        }.getOrDefault(false)

    @Synchronized
    fun invalidate(path: Path) {
        verified.remove(path.toAbsolutePath().normalize())
    }
}

private fun failRuntime(
    cause: Throwable? = null,
    reason: DownloadFailureReason = DownloadFailureReason.Unknown,
): Nothing = throw DownloadRuntimeException(cause, reason)

internal data class ResolvedMedia(
    val title: String,
    val channel: String,
    val duration: Duration?,
    val originalAudio: OriginalAudio,
    val videoQualities: List<DownloadQuality>,
)

private data class MediaFormat(
    val id: String,
    val height: Int?,
    val fps: Double?,
    val videoBitrate: Double?,
    val totalBitrate: Double?,
    val audioBitrate: Double?,
    val fileSize: Long?,
    val approximateFileSize: Long?,
    val videoCodec: String?,
    val audioCodec: String?,
    val container: String,
    val dynamicRange: String?,
) {
    val hasVideo: Boolean get() = videoCodec.isMediaCodec()
    val hasAudio: Boolean get() = audioCodec.isMediaCodec()
    val isAudioOnly: Boolean get() = !hasVideo && hasAudio
    val bestFileSize: Long? get() = fileSize ?: approximateFileSize
    val fileSizeIsApproximate: Boolean get() = fileSize == null && approximateFileSize != null
}

internal fun parseResolvedMedia(output: List<String>): ResolvedMedia {
    val media = output.jsonAfter(MEDIA_JSON_PREFIX).jsonObject
    val formats =
        output
            .jsonAfter(FORMATS_JSON_PREFIX)
            .jsonArray
            .mapNotNull { element -> (element as? JsonObject)?.toMediaFormat() }
    val audio = formats.lastOrNull(MediaFormat::isAudioOnly) ?: error("No audio-only format")
    val qualities = selectVideoQualities(formats, audio)
    require(qualities.isNotEmpty())
    val title = media.text("title").orEmpty()
    require(title.isNotBlank())
    return ResolvedMedia(
        title = title,
        channel = media.text("channel") ?: media.text("uploader") ?: "Unknown channel",
        duration = media.number("duration")?.seconds,
        originalAudio =
            OriginalAudio(
                container = audio.container,
                codec = requireNotNull(audio.audioCodec),
                bitRateKilobitsPerSecond =
                    (audio.audioBitrate ?: audio.totalBitrate)
                        ?.roundToInt()
                        ?.takeIf { it > 0 },
            ),
        videoQualities = qualities,
    )
}

private fun selectVideoQualities(
    formats: List<MediaFormat>,
    audio: MediaFormat,
): List<DownloadQuality> {
    val selectedIds = mutableSetOf<String>()
    return VIDEO_HEIGHT_CEILINGS.mapNotNull { ceiling ->
        val video =
            formats.lastOrNull {
                it.hasVideo && it.height != null && it.height <= ceiling
            } ?: return@mapNotNull null
        if (!selectedIds.add(video.id)) return@mapNotNull null
        video.toDownloadQuality(audio, bestAvailable = selectedIds.size == 1)
    }
}

private fun MediaFormat.toDownloadQuality(
    audio: MediaFormat,
    bestAvailable: Boolean,
): DownloadQuality {
    val selectedAudio = audio.takeUnless { hasAudio }
    val bitrate = if (hasAudio) totalBitrate else videoBitrate
    val bitrateLabel = bitrate?.let(::formatBitrate) ?: "bitrate unavailable"
    val resolution = "${requireNotNull(height)}p${fps?.toFpsLabel().orEmpty()}"
    val primary =
        listOfNotNull(
            "Best".takeIf { bestAvailable },
            resolution,
            bitrateLabel + " total".takeIf { hasAudio }.orEmpty(),
        )
    val streamDetails =
        buildList {
            add("${requireNotNull(videoCodec).toCodecLabel()}/${container.toContainerLabel()}")
            selectedAudio?.let {
                add("${requireNotNull(it.audioCodec).toCodecLabel()}/${it.container.toContainerLabel()}")
            }
        }.joinToString(" + ")
    val size = combinedSizeWith(selectedAudio)
    val detail =
        buildList {
            add(streamDetails)
            size?.let { add(formatFileSize(it.first, it.second)) }
            dynamicRange?.takeUnless { it.equals("SDR", ignoreCase = true) }?.let(::add)
        }.joinToString(" · ")
    return DownloadQuality(
        label = primary.joinToString(" · "),
        supportingText = detail,
        ytDlpArguments = listOf("--format", selectedAudio?.let { "$id+${it.id}" } ?: id),
    )
}

private fun MediaFormat.combinedSizeWith(audio: MediaFormat?): Pair<Long, Boolean>? {
    val videoSize = bestFileSize ?: return null
    val audioSize = audio?.bestFileSize ?: 0L
    return (videoSize + audioSize) to (fileSizeIsApproximate || audio?.fileSizeIsApproximate == true)
}

private fun JsonObject.toMediaFormat(): MediaFormat? {
    val id = text("format_id")
    val container = text("ext")
    return if (id == null || container == null) {
        null
    } else {
        MediaFormat(
            id = id,
            height = number("height")?.roundToInt(),
            fps = number("fps"),
            videoBitrate = number("vbr"),
            totalBitrate = number("tbr"),
            audioBitrate = number("abr"),
            fileSize = number("filesize")?.toLong(),
            approximateFileSize = number("filesize_approx")?.toLong(),
            videoCodec = text("vcodec"),
            audioCodec = text("acodec"),
            container = container,
            dynamicRange = text("dynamic_range"),
        )
    }
}

private fun List<String>.jsonAfter(prefix: String) =
    firstNotNullOfOrNull { line -> line.removePrefix(prefix).takeIf { it != line } }
        ?.let(Json::parseToJsonElement)
        ?: error("Missing yt-dlp JSON output")

private fun JsonObject.text(name: String): String? =
    get(name)
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("NA", ignoreCase = true) && !it.equals("none", ignoreCase = true) }

private fun JsonObject.number(name: String): Double? = get(name)?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() }

private fun String?.isMediaCodec(): Boolean = this != null && !equals("none", ignoreCase = true)

private fun Double.toFpsLabel(): String =
    roundToInt()
        .takeIf { kotlin.math.abs(this - it) < FPS_INTEGER_TOLERANCE }
        ?.toString()
        ?: "%.2f".format(Locale.ROOT, this)

private fun formatBitrate(kilobitsPerSecond: Double): String =
    if (kilobitsPerSecond >= KILOBITS_PER_MEGABIT) {
        "~${"%.2f".format(Locale.ROOT, kilobitsPerSecond / KILOBITS_PER_MEGABIT).trimEnd('0').trimEnd('.')} Mbps"
    } else {
        "~${kilobitsPerSecond.roundToInt()} kbps"
    }

private fun formatFileSize(
    bytes: Long,
    approximate: Boolean,
): String {
    val prefix = if (approximate) "~" else ""
    val mebibytes = bytes / BYTES_PER_MEBIBYTE
    return if (mebibytes >= MEBIBYTES_PER_GIBIBYTE) {
        "$prefix${"%.2f".format(Locale.ROOT, mebibytes / MEBIBYTES_PER_GIBIBYTE).trimEnd('0').trimEnd('.')} GB"
    } else {
        "$prefix${"%.1f".format(Locale.ROOT, mebibytes).trimEnd('0').trimEnd('.')} MB"
    }
}

private fun Int?.orZero(): Int = this ?: 0

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

internal fun quickJsArguments(executable: String?): List<String> =
    executable?.let { listOf("--no-js-runtimes", "--js-runtimes", "quickjs:$it") }.orEmpty()

internal fun browserCookieArguments(source: BrowserCookieSource?): List<String> =
    source?.let { listOf("--cookies-from-browser", it.ytDlpName) }.orEmpty()

internal fun requireSha256(
    path: Path,
    expected: String,
) {
    if (!sha256(path).equals(expected, ignoreCase = true)) throw DownloadRuntimeException()
}

internal fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return HexFormat.of().formatHex(digest.digest())
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

internal data class ToolAsset(
    val uri: URI,
    val sha256: String,
    val maxBytes: Long,
)

private const val MAX_CAPTURED_OUTPUT_LINES = 100
private const val MAX_DIAGNOSTIC_LINES = 20
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
private const val MAX_PREVIEW_BYTES = 64 * 1024
private const val MAX_THUMBNAIL_BYTES = 5 * 1024 * 1024
private const val SESSION_CACHE_MAX_ENTRIES = 16
private const val SESSION_CACHE_MAX_THUMBNAIL_BYTES = 16 * 1024 * 1024
private const val FPS_INTEGER_TOLERANCE = 0.01
private const val KILOBITS_PER_MEGABIT = 1_000.0
private const val BYTES_PER_MEBIBYTE = 1024.0 * 1024.0
private const val MEBIBYTES_PER_GIBIBYTE = 1024.0
private const val MEDIA_JSON_PREFIX = "DOWNLET_MEDIA_JSON="
private const val FORMATS_JSON_PREFIX = "DOWNLET_FORMATS_JSON="
private val VIDEO_HEIGHT_CEILINGS = listOf(2160, 1440, 1080, 720, 480)
private val CONNECT_TIMEOUT: Duration = 30.seconds
private val PREVIEW_TIMEOUT: Duration = 20.seconds
private val DOWNLOAD_TIMEOUT: Duration = 10.minutes
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
private const val DOWNLET_DOWNLOAD_AGENT_VERSION = "0.1"
private const val YT_DLP_VERSION = "2026.08.19"
private const val QUICKJS_VERSION = "0.16.2"
private const val FFMPEG_VERSION = "9.0.1"
private const val YT_DLP_MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024
private const val FFMPEG_MAX_DOWNLOAD_BYTES = 256L * 1024 * 1024
private const val OEMBED_ENDPOINT = "https://www.youtube.com/oembed"
private const val YOUTUBE_THUMBNAIL_HOST = "i.ytimg.com"
private const val QUICKJS_RESOURCE_PATH = "/tools/quickjs-ng/$QUICKJS_VERSION/qjs.exe"
private const val QUICKJS_SHA256 = "7b27412de844403545bd151fbe49191b4d5b91a9e15b5db7c863fea54639a82b"
private const val FFMPEG_EXECUTABLE_SHA256 = "72a489eccd008c2ec2c0a5856c5c75bc3d8bbfa90166c4566865c246445e6aa3"
private const val FFPROBE_EXECUTABLE_SHA256 = "19202b23c0043f15ad1b7bce2344f406fd52bd6efd8f995ce02e7392a1cec52f"
private val YT_DLP_ASSET =
    ToolAsset(
        uri = URI("https://github.com/yt-dlp/yt-dlp/releases/download/$YT_DLP_VERSION/yt-dlp.exe"),
        sha256 = "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a",
        maxBytes = YT_DLP_MAX_DOWNLOAD_BYTES,
    )
private val FFMPEG_ASSET =
    ToolAsset(
        uri = URI("https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-$FFMPEG_VERSION-essentials_build.zip"),
        sha256 = "fec81ae03971d9dd4be3ebe02e263bd2ec1d789483f931bdba5f5715e65da2e9",
        maxBytes = FFMPEG_MAX_DOWNLOAD_BYTES,
    )
private val PREVIEW_FAILURE_PROGRESS = fakeProgressSteps[2]
