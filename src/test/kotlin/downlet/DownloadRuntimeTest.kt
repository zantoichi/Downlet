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
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadRuntimeTest {
    @Test
    fun `yt-dlp metadata output becomes a media fixture`() {
        val metadata =
            parseMetadata(
                listOf(
                    "DOWNLET_ID=abc123",
                    "DOWNLET_TITLE=City after rain",
                    "DOWNLET_CHANNEL=North Window",
                    "DOWNLET_UPLOADER=Fallback uploader",
                    "DOWNLET_DURATION=12:34",
                ),
            )

        assertEquals("abc123", metadata["ID"])
        assertEquals("City after rain", metadata["TITLE"])
        assertEquals("North Window", metadata["CHANNEL"])
        assertEquals("12:34", metadata["DURATION"])
    }

    @Test
    fun `yt-dlp progress output is bounded for the downloading state`() {
        assertEquals(43, parseProgress("DOWNLET_PROGRESS= 43.2%"))
        assertEquals(99, parseProgress("DOWNLET_PROGRESS=100.0%"))
        assertNull(parseProgress("[download] waiting"))
        assertEquals("Downloading…", downloadProgressStatus(1))
        assertEquals("Finishing…", downloadProgressStatus(99))
    }

    @Test
    fun `quality choices map to one yt-dlp format selection`() {
        assertEquals(
            listOf("Best available — 2160p", "1440p", "1080p", "720p", "480p"),
            videoQualityOptions.map(DownloadQuality::label),
        )
        assertEquals(
            listOf("Best available — 251 kbps audio", "160 kbps audio", "128 kbps audio"),
            audioQualityOptions.map(DownloadQuality::label),
        )
        assertEquals(
            listOf("--format", "bv*[height<=1080]+ba/b[height<=1080]"),
            videoQualityOptions[2].ytDlpArguments,
        )
        assertEquals(
            listOf("--format", "ba/b", "--extract-audio", "--audio-format", "mp3", "--audio-quality", "160K"),
            audioQualityOptions[1].ytDlpArguments,
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
    fun `process tree termination stops parent and descendants`() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return

        val process = ProcessBuilder("cmd.exe", "/c", "ping", "-t", "127.0.0.1").start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var descendants = emptyList<ProcessHandle>()
            while (descendants.isEmpty() && System.nanoTime() < deadline) {
                descendants = process.descendants().toList()
                if (descendants.isEmpty()) Thread.sleep(25)
            }
            assertTrue(descendants.isNotEmpty(), "Expected cmd.exe to start ping.exe")

            terminateProcessTree(process)

            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Parent process did not exit")
            descendants.forEach { descendant ->
                descendant.onExit().get(5, TimeUnit.SECONDS)
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
