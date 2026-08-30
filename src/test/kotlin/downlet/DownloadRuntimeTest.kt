package downlet

import com.sun.jna.platform.win32.KnownFolders
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

class DownloadRuntimeTest {
    @Test
    fun `yt-dlp metadata output contains typed media fields`() {
        val metadata =
            parseMetadata(
                listOf(
                    "DOWNLET_TITLE=City after rain",
                    "DOWNLET_CHANNEL=North Window",
                    "DOWNLET_UPLOADER=Fallback uploader",
                    "DOWNLET_DURATION_SECONDS=754.0",
                    "DOWNLET_AUDIO_FORMAT=webm",
                    "DOWNLET_AUDIO_BITRATE_KBPS=129.7",
                ),
            )

        assertEquals("City after rain", metadata["TITLE"])
        assertEquals("North Window", metadata["CHANNEL"])
        assertEquals("754.0", metadata["DURATION_SECONDS"])
        assertEquals(OriginalAudio(format = "webm", bitRateKilobitsPerSecond = 130), parseOriginalAudio(metadata))
    }

    @Test
    fun `yt-dlp progress output distinguishes transfer and processing phases`() {
        assertEquals(
            DownloadProgress.Transferring(43),
            parseProgress("DOWNLET_TRANSFER=downloading| 43.2%"),
        )
        assertEquals(
            DownloadProgress.Transferring(100),
            parseProgress("DOWNLET_TRANSFER=downloading|100.0%"),
        )
        assertEquals(DownloadProgress.Processing, parseProgress("DOWNLET_PROCESSING=started"))
        assertEquals(DownloadProgress.Processing, parseProgress("DOWNLET_PROCESSING=processing"))
        assertNull(parseProgress("DOWNLET_TRANSFER=finished|100.0%"))
        assertNull(parseProgress("DOWNLET_PROCESSING=finished"))
        assertNull(parseProgress("[download] waiting"))
        assertEquals("Downloading…", downloadProgressStatus(DownloadProgress.Transferring(1)))
        assertEquals("Downloading…", downloadProgressStatus(DownloadProgress.Transferring(100)))
        assertEquals("Processing…", downloadProgressStatus(DownloadProgress.Processing))
    }

    @Test
    fun `media durations retain current display format`() {
        assertEquals("Unknown duration", formatMediaDuration(null))
        assertEquals("12:34", formatMediaDuration(12.minutes + 34.seconds))
        assertEquals("1:02:03", formatMediaDuration(1.hours + 2.minutes + 3.seconds))
    }

    @Test
    fun `quality choices map to one yt-dlp format selection`() {
        assertEquals(
            listOf("Best available — 2160p", "1440p", "1080p", "720p", "480p"),
            videoQualityOptions.map(DownloadQuality::label),
        )
        assertEquals(
            listOf(
                "Original audio — WebM · 130 kbps",
                "MP3 — Best quality · ~245 kbps VBR",
                "MP3 — 160 kbps",
                "MP3 — 128 kbps",
            ),
            audioQualityOptions(DownloadFixtures.normal.originalAudio).map(DownloadQuality::label),
        )
        assertEquals(
            listOf("--format", "bv*[height<=1080]+ba/b[height<=1080]"),
            videoQualityOptions[2].ytDlpArguments,
        )
        assertEquals(
            listOf("--format", "ba"),
            audioQualityOptions(DownloadFixtures.normal.originalAudio)[0].ytDlpArguments,
        )
        assertEquals(
            listOf("--format", "ba/b", "--extract-audio", "--audio-format", "mp3", "--audio-quality", "160K"),
            audioQualityOptions(DownloadFixtures.normal.originalAudio)[2].ytDlpArguments,
        )
    }

    @Test
    fun `bundled quickjs disables other runtimes before selecting quickjs`() {
        assertEquals(
            listOf("--no-js-runtimes", "--js-runtimes", "quickjs:C:\\Downlet\\qjs.exe"),
            quickJsArguments("C:\\Downlet\\qjs.exe"),
        )
        assertEquals(emptyList(), quickJsArguments(null))
    }

    @Test
    fun `executable discovery validates overrides and preserves source order`() =
        withTempDirectory { root ->
            val override = executable(root.resolve("override tools"), "custom-yt-dlp.exe")
            val provisioned = executable(root.resolve("managed tools"), "yt-dlp.exe")
            val pathDirectory = root.resolve("path tools")
            val pathExecutable = executable(pathDirectory, "yt-dlp.exe")
            val extensionless = executable(pathDirectory, "yt-dlp")

            assertEquals(
                override,
                resolveExecutable(
                    "DOWNLET_YT_DLP",
                    provisioned,
                    "yt-dlp",
                    mapOf("DOWNLET_YT_DLP" to "  \"$override\"  ", "Path" to "\"$pathDirectory\""),
                ),
            )
            assertEquals(
                provisioned,
                resolveExecutable(
                    "DOWNLET_YT_DLP",
                    provisioned,
                    "yt-dlp",
                    mapOf("DOWNLET_YT_DLP" to root.resolve("missing.exe").toString(), "Path" to "\"$pathDirectory\""),
                ),
            )
            assertEquals(
                provisioned,
                resolveExecutable("DOWNLET_YT_DLP", provisioned, "yt-dlp", mapOf("DOWNLET_YT_DLP" to "\u0000")),
            )

            Files.delete(provisioned)
            assertEquals(
                pathExecutable,
                resolveExecutable(
                    "DOWNLET_YT_DLP",
                    provisioned,
                    "yt-dlp",
                    mapOf("Path" to "\"$pathDirectory\""),
                ),
            )
            Files.delete(pathExecutable)
            assertEquals(
                extensionless,
                resolveExecutable(
                    "DOWNLET_YT_DLP",
                    provisioned,
                    "yt-dlp",
                    mapOf("PATH" to pathDirectory.toString()),
                ),
            )
            Files.delete(extensionless)
            assertNull(resolveExecutable("DOWNLET_YT_DLP", provisioned, "yt-dlp", emptyMap()))
        }

    @Test
    fun `ffmpeg discovery requires a co-located pair`() =
        withTempDirectory { root ->
            val firstOverride = ffmpegPair(root.resolve("first override"))
            val secondOverride = ffmpegPair(root.resolve("second override"))
            val managed = ffmpegPair(root.resolve("managed"))
            val pathPair = ffmpegPair(root.resolve("path"))

            assertEquals(
                firstOverride,
                resolveFfmpegTools(
                    mapOf("DOWNLET_FFMPEG" to firstOverride.directory.resolve("ffmpeg.exe").toString()),
                    managed.directory,
                ),
            )
            assertEquals(
                firstOverride,
                resolveFfmpegTools(
                    mapOf(
                        "DOWNLET_FFMPEG" to "\"${firstOverride.directory.resolve("ffmpeg.exe")}\"",
                        "DOWNLET_FFPROBE" to "\"${firstOverride.directory.resolve("ffprobe.exe")}\"",
                    ),
                    managed.directory,
                ),
            )
            assertEquals(
                managed,
                resolveFfmpegTools(
                    mapOf(
                        "DOWNLET_FFMPEG" to firstOverride.directory.resolve("ffmpeg.exe").toString(),
                        "DOWNLET_FFPROBE" to secondOverride.directory.resolve("ffprobe.exe").toString(),
                        "PATH" to pathPair.directory.toString(),
                    ),
                    managed.directory,
                ),
            )

            Files.delete(managed.directory.resolve("ffprobe.exe"))
            assertEquals(
                pathPair,
                resolveFfmpegTools(
                    mapOf(
                        "DOWNLET_FFMPEG" to root.resolve("missing.exe").toString(),
                        "PATH" to pathPair.directory.toString(),
                    ),
                    managed.directory,
                ),
            )
            Files.delete(pathPair.directory.resolve("ffprobe.exe"))
            ffmpegPair(root.resolve("unlisted"))
            assertNull(resolveFfmpegTools(emptyMap(), root.resolve("unmanaged pair")))
        }

    @Test
    fun `runtime skips setup for a complete PATH tool set`() =
        withTempDirectory { root ->
            val pathDirectory = root.resolve("path")
            executable(pathDirectory, "yt-dlp.exe")
            val ffmpeg = ffmpegPair(pathDirectory)
            val runtime =
                YtDlpDownloadRuntime(
                    toolsDirectory = root.resolve("managed"),
                    environment = mapOf("Path" to "\"$pathDirectory\""),
                )

            assertEquals(emptyList(), runtime.missingTools())
            assertEquals(
                listOf("--ffmpeg-location", ffmpeg.directory.toString()),
                ffmpegLocationArguments(ffmpeg),
            )
            assertEquals(emptyList(), ffmpegLocationArguments(null))
        }

    @Test
    fun `typed tool metadata supplies labels and setup size`() {
        assertEquals(listOf("yt-dlp", "FFmpeg"), DownloadTool.entries.map(DownloadTool::label))
        assertEquals(123, DownloadTool.entries.sumOf(DownloadTool::estimatedDownloadMegabytes))
    }

    @Test
    fun `known folder lookup uses native result or fallback`() {
        val fallback = Path.of("fallback")

        assertEquals(
            Path.of("C:\\Users\\Downlet\\Downloads"),
            knownFolderOrFallback(KnownFolders.FOLDERID_Downloads, fallback) { "C:\\Users\\Downlet\\Downloads" },
        )
        assertEquals(
            fallback,
            knownFolderOrFallback(KnownFolders.FOLDERID_Downloads, fallback) { "   " },
        )
        assertEquals(
            fallback,
            knownFolderOrFallback(KnownFolders.FOLDERID_Downloads, fallback) { error("unavailable") },
        )
    }

    @Test
    fun `staged download publishes one file without replacing an existing target`() =
        withTempDirectory { root ->
            val destination = Files.createDirectory(root.resolve("destination"))
            val firstOutput = Files.createDirectories(root.resolve("first-output"))
            val firstStagedFile = firstOutput.resolve("media.mp3")
            Files.writeString(firstStagedFile, "first")

            val published = assertNotNull(publishStagedDownload(firstOutput, destination))

            assertEquals(destination.resolve("media.mp3"), published)
            assertEquals("first", Files.readString(published))
            assertFalse(Files.exists(firstStagedFile))

            val duplicateOutput = Files.createDirectories(root.resolve("duplicate-output"))
            val duplicateStagedFile = duplicateOutput.resolve("media.mp3")
            Files.writeString(duplicateStagedFile, "replacement")

            assertNull(publishStagedDownload(duplicateOutput, destination))
            assertEquals("first", Files.readString(published))
            assertEquals("replacement", Files.readString(duplicateStagedFile))
        }

    @Test
    fun `staged download rejects ambiguous output and recursive cleanup removes the attempt`() =
        withTempDirectory { root ->
            val destination = Files.createDirectory(root.resolve("destination"))
            val attempt = Files.createDirectories(destination.resolve(".downlet-test"))
            val output = Files.createDirectory(attempt.resolve("output"))
            val working = Files.createDirectory(attempt.resolve("working"))
            Files.writeString(output.resolve("first.webm"), "first")
            Files.writeString(output.resolve("second.webm"), "second")
            Files.writeString(working.resolve("partial.part"), "partial")

            assertFailsWith<DownloadRuntimeException> {
                publishStagedDownload(output, destination)
            }

            deleteRecursively(attempt)
            assertFalse(Files.exists(attempt))
            assertEquals(emptyList(), Files.list(destination).use { it.toList() })
        }

    @Test
    fun `process tree termination stops parent and descendants`() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return

        val process = ProcessBuilder("cmd.exe", "/c", "ping", "-t", "127.0.0.1").start()
        try {
            val waitTimeout = 5.seconds
            val pollInterval = 25.milliseconds
            val started = TimeSource.Monotonic.markNow()
            var descendants = emptyList<ProcessHandle>()
            while (descendants.isEmpty() && started.elapsedNow() < waitTimeout) {
                descendants = process.descendants().toList()
                if (descendants.isEmpty()) Thread.sleep(pollInterval.toJavaDuration())
            }
            assertTrue(descendants.isNotEmpty(), "Expected cmd.exe to start ping.exe")

            val terminatedHandles = terminateProcessTree(process)
            awaitProcessTreeTermination(process, terminatedHandles)

            assertFalse(process.isAlive, "Parent process did not exit")
            descendants.forEach { descendant ->
                assertFalse(descendant.isAlive, "Descendant process did not exit")
            }
        } finally {
            if (process.isAlive) terminateProcessTree(process)
        }
    }

    @Test
    fun `tool downloads must match their pinned sha256`() {
        val file = Files.createTempFile("downlet-hash-", ".tmp")
        try {
            Files.writeString(file, "downlet")

            requireSha256(file, "e0430ddb9a1c80ebac88a1086080fc7fad08fd8a4a4d8bd7dde16add7af07dc9")
            assertFailsWith<DownloadRuntimeException> {
                requireSha256(file, "0000000000000000000000000000000000000000000000000000000000000000")
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `youtube oembed xml becomes a safe media preview`() {
        val metadata =
            parseOEmbed(
                """
                <oembed>
                    <title>Rain &amp; windows</title>
                    <author_name>North Window</author_name>
                    <thumbnail_url>https://i.ytimg.com/vi/abc/hqdefault.jpg</thumbnail_url>
                </oembed>
                """.trimIndent().toByteArray(),
            )

        assertEquals("Rain & windows", metadata.title)
        assertEquals("North Window", metadata.channel)
        assertEquals("i.ytimg.com", metadata.thumbnailUri?.host)
    }

    @Test
    fun `oembed rejects document types and ignores foreign thumbnail hosts`() {
        assertFailsWith<DownloadRuntimeException> {
            parseOEmbed(
                """<!DOCTYPE oembed [<!ENTITY xxe SYSTEM "file:///windows/win.ini">]>
                    <oembed><title>&xxe;</title><author_name>Channel</author_name></oembed>""".toByteArray(),
            )
        }

        val metadata =
            parseOEmbed(
                """
                <oembed>
                    <title>Video</title>
                    <author_name>Channel</author_name>
                    <thumbnail_url>https://example.com/thumbnail.jpg</thumbnail_url>
                </oembed>
                """.trimIndent().toByteArray(),
            )
        assertNull(metadata.thumbnailUri)
    }

    @Test
    fun `cancelling a coroutine cancels its pending future`() =
        runTest {
            val future = CompletableFuture<Unit>()
            val job = launch { future.awaitCancellable() }

            yield()
            job.cancelAndJoin()

            assertTrue(future.isCancelled)
        }

    @Test
    fun `bundled quickjs matches its pinned release hash`() {
        val resource = assertNotNull(javaClass.getResourceAsStream("/tools/quickjs-ng/0.16.2/qjs.exe"))
        val file = Files.createTempFile("downlet-qjs-", ".exe")
        try {
            resource.use { input -> Files.newOutputStream(file).use(input::copyTo) }
            requireSha256(file, "7b27412de844403545bd151fbe49191b4d5b91a9e15b5db7c863fea54639a82b")
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun executable(
        directory: Path,
        name: String,
    ): Path {
        Files.createDirectories(directory)
        return directory.resolve(name).also { Files.writeString(it, "test") }
    }

    private fun ffmpegPair(directory: Path): FfmpegTools {
        executable(directory, "ffmpeg.exe")
        executable(directory, "ffprobe.exe")
        return FfmpegTools(directory.toAbsolutePath().normalize())
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("downlet-runtime-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
