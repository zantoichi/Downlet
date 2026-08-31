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
    fun `yt-dlp JSON resolves exact media formats and display details`() {
        val metadata = parseResolvedMedia(resolvedMediaOutput())

        assertEquals("City after rain", metadata.title)
        assertEquals("North Window", metadata.channel)
        assertEquals(754.seconds, metadata.duration)
        assertEquals(
            OriginalAudio(container = "webm", codec = "opus", bitRateKilobitsPerSecond = 126),
            metadata.originalAudio,
        )
        assertEquals(
            listOf("Best · 1440p60 · ~2.45 Mbps", "1080p25 · ~1.14 Mbps", "480p · ~640 kbps"),
            metadata.videoQualities.map(DownloadQuality::label),
        )
        assertEquals(
            listOf("--format", "400+251"),
            metadata.videoQualities.first().ytDlpArguments,
        )
        assertEquals("AV1/MP4 + Opus/WebM · ~96.3 MB · HDR10", metadata.videoQualities.first().supportingText)
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
            listOf(
                "Best · 2160p60 · ~18.4 Mbps",
                "1440p60 · ~9.2 Mbps",
                "1080p60 · ~4.8 Mbps",
                "720p60 · ~2.4 Mbps",
                "480p · ~1.1 Mbps",
            ),
            videoQualityOptions.map(DownloadQuality::label),
        )
        assertEquals(
            listOf(
                "Original · Opus/WebM · ~130 kbps",
                "MP3 · High-quality VBR · ~190 kbps",
                "MP3 · 160 kbps",
                "MP3 · 128 kbps",
            ),
            audioQualityOptions(DownloadFixtures.normal.originalAudio).map(DownloadQuality::label),
        )
        assertEquals(
            listOf("--format", "399+251"),
            videoQualityOptions[2].ytDlpArguments,
        )
        assertEquals(
            listOf("--format", "ba"),
            audioQualityOptions(DownloadFixtures.normal.originalAudio)[0].ytDlpArguments,
        )
        assertEquals(
            listOf("--format", "ba", "--extract-audio", "--audio-format", "mp3", "--audio-quality", "160K"),
            audioQualityOptions(DownloadFixtures.normal.originalAudio)[2].ytDlpArguments,
        )
        assertEquals("2", audioQualityOptions(DownloadFixtures.normal.originalAudio)[1].ytDlpArguments.last())
    }

    @Test
    fun `session cache is bounded by access order and thumbnail bytes`() {
        val first = cachedItem("first", byteArrayOf(1, 2))
        val second = cachedItem("second", byteArrayOf(3, 4))
        val third = cachedItem("third", byteArrayOf(5, 6))
        val entryBounded = SessionMediaCache(maximumEntries = 2, maximumThumbnailBytes = 100)
        entryBounded.putPreview(first)
        entryBounded.putPreview(second)
        assertEquals(first, entryBounded.preview(first.source))
        entryBounded.putPreview(third)
        assertNull(entryBounded.preview(second.source))
        assertEquals(2, entryBounded.size())

        val byteBounded = SessionMediaCache(maximumEntries = 16, maximumThumbnailBytes = 3)
        byteBounded.putPreview(first)
        byteBounded.putPreview(second)
        assertNull(byteBounded.preview(first.source))
        assertEquals(second, byteBounded.preview(second.source))
        assertNull(SessionMediaCache().preview(second.source))
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
            runTest {
                val pathDirectory = root.resolve("path")
                executable(pathDirectory, "yt-dlp.exe")
                val ffmpeg = ffmpegPair(pathDirectory)
                val runtime =
                    YtDlpDownloadRuntime(
                        toolsDirectory = root.resolve("managed"),
                        environment = mapOf("Path" to "\"$pathDirectory\""),
                    )

                assertEquals(ToolStatus(), runtime.toolStatus())
                assertEquals(
                    listOf("--ffmpeg-location", ffmpeg.directory.toString()),
                    ffmpegLocationArguments(ffmpeg),
                )
                assertEquals(emptyList(), ffmpegLocationArguments(null))
            }
        }

    @Test
    fun `managed integrity caches valid files and rehashes changed fingerprints`() =
        withTempDirectory { root ->
            val file = root.resolve("tool.exe")
            Files.writeString(file, "valid")
            val expected = sha256(file)
            var digestCount = 0
            val integrity =
                ManagedToolIntegrity { path ->
                    digestCount += 1
                    sha256(path)
                }

            assertTrue(integrity.isValid(file, expected))
            assertTrue(integrity.isValid(file, expected))
            assertEquals(1, digestCount)

            Files.writeString(file, "damaged file")
            assertFalse(integrity.isValid(file, expected))
            assertEquals(2, digestCount)
        }

    @Test
    fun `absent managed tools are missing while incomplete installations are repairable`() =
        withTempDirectory { root ->
            runTest {
                val absent = YtDlpDownloadRuntime(toolsDirectory = root.resolve("absent"), environment = emptyMap())
                assertEquals(ToolStatus(missing = DownloadTool.entries), absent.toolStatus())

                val managed = root.resolve("managed")
                executable(managed.resolve("yt-dlp/2026.08.19"), "yt-dlp.exe")
                executable(managed.resolve("ffmpeg/9.0.1/bin"), "ffmpeg.exe")
                val incomplete = YtDlpDownloadRuntime(toolsDirectory = managed, environment = emptyMap())

                assertEquals(ToolStatus(repairable = DownloadTool.entries), incomplete.toolStatus())
            }
        }

    @Test
    fun `valid PATH tools bypass damaged managed copies`() =
        withTempDirectory { root ->
            runTest {
                val managed = root.resolve("managed")
                executable(managed.resolve("yt-dlp/2026.08.19"), "yt-dlp.exe")
                executable(managed.resolve("ffmpeg/9.0.1/bin"), "ffmpeg.exe")
                val pathDirectory = root.resolve("path")
                executable(pathDirectory, "yt-dlp.exe")
                ffmpegPair(pathDirectory)

                val runtime =
                    YtDlpDownloadRuntime(
                        toolsDirectory = managed,
                        environment = mapOf("PATH" to pathDirectory.toString()),
                    )

                assertEquals(ToolStatus(), runtime.toolStatus())
            }
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
    fun `staged download publishes collision-safe names without replacing existing targets`() =
        withTempDirectory { root ->
            val destination = Files.createDirectory(root.resolve("destination"))
            val firstOutput = Files.createDirectories(root.resolve("first-output"))
            val firstStagedFile = firstOutput.resolve("media.mp3")
            Files.writeString(firstStagedFile, "first")

            val firstPublished = publishStagedDownload(firstOutput, destination)

            assertEquals(destination.resolve("media.mp3"), firstPublished)
            assertEquals("first", Files.readString(firstPublished))
            assertFalse(Files.exists(firstStagedFile))

            val duplicateOutput = Files.createDirectories(root.resolve("duplicate-output"))
            val duplicateStagedFile = duplicateOutput.resolve("media.mp3")
            Files.writeString(duplicateStagedFile, "second")

            val secondPublished = publishStagedDownload(duplicateOutput, destination)

            assertEquals(destination.resolve("media (2).mp3"), secondPublished)
            assertEquals("first", Files.readString(firstPublished))
            assertEquals("second", Files.readString(secondPublished))

            val thirdOutput = Files.createDirectories(root.resolve("third-output"))
            Files.writeString(thirdOutput.resolve("media.mp3"), "third")

            val thirdPublished = publishStagedDownload(thirdOutput, destination)

            assertEquals(destination.resolve("media (3).mp3"), thirdPublished)
            assertEquals("third", Files.readString(thirdPublished))
        }

    @Test
    fun `collision suffix handles extensionless and leading-dot filenames`() =
        withTempDirectory { root ->
            val destination = Files.createDirectory(root.resolve("destination"))
            Files.writeString(destination.resolve("README"), "existing")
            Files.writeString(destination.resolve(".metadata"), "existing")

            val extensionlessOutput = Files.createDirectories(root.resolve("extensionless-output"))
            Files.writeString(extensionlessOutput.resolve("README"), "new")
            val hiddenOutput = Files.createDirectories(root.resolve("hidden-output"))
            Files.writeString(hiddenOutput.resolve(".metadata"), "new")

            val extensionless = publishStagedDownload(extensionlessOutput, destination)
            val hidden = publishStagedDownload(hiddenOutput, destination)

            assertEquals(destination.resolve("README (2)"), extensionless)
            assertEquals(destination.resolve(".metadata (2)"), hidden)
            assertEquals("existing", Files.readString(destination.resolve("README")))
            assertEquals("existing", Files.readString(destination.resolve(".metadata")))
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

    private fun resolvedMediaOutput() =
        listOf(
            "DOWNLET_MEDIA_JSON=" +
                "{\"title\":\"City after rain\",\"channel\":\"North Window\",\"uploader\":\"Fallback\"," +
                "\"duration\":754.0}",
            "DOWNLET_FORMATS_JSON=" +
                "[" +
                "{\"format_id\":\"251\",\"abr\":126,\"filesize\":1000000,\"vcodec\":\"none\"," +
                "\"acodec\":\"opus\",\"ext\":\"webm\",\"dynamic_range\":\"SDR\"}," +
                "{\"format_id\":\"244\",\"height\":480,\"vbr\":640,\"filesize\":20000000," +
                "\"vcodec\":\"vp9\",\"acodec\":\"none\",\"ext\":\"webm\",\"dynamic_range\":\"SDR\"}," +
                "{\"format_id\":\"399\",\"height\":1080,\"fps\":25,\"vbr\":1140,\"filesize\":40000000," +
                "\"vcodec\":\"av01.0.08M.08\",\"acodec\":\"none\",\"ext\":\"mp4\"," +
                "\"dynamic_range\":\"SDR\"}," +
                "{\"format_id\":\"400\",\"height\":1440,\"fps\":60,\"vbr\":2450," +
                "\"filesize_approx\":100000000,\"vcodec\":\"av01.0.12M.08\",\"acodec\":\"none\"," +
                "\"ext\":\"mp4\",\"dynamic_range\":\"HDR10\"}" +
                "]",
        )

    private fun cachedItem(
        id: String,
        thumbnail: ByteArray,
    ) = DownloadFixtures.normal.copy(
        source = requireNotNull(YouTubeUrl.parse("https://youtu.be/$id")),
        thumbnail = MediaThumbnail.Remote(ThumbnailData(thumbnail)),
    )

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
