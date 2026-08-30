package downlet

import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
            listOf("--format", "bv*[height<=1080]+ba/b[height<=1080]"),
            qualityArguments(DownloadMode.Video, qualityIndex = 2),
        )
        assertEquals(
            listOf("--format", "ba/b", "--extract-audio", "--audio-format", "mp3", "--audio-quality", "160K"),
            qualityArguments(DownloadMode.Audio, qualityIndex = 1),
        )
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
    fun `preview responses are bounded`() {
        assertEquals(4, readBounded(ByteArrayInputStream(ByteArray(4)), maxBytes = 4).size)
        assertFailsWith<DownloadRuntimeException> {
            readBounded(ByteArrayInputStream(ByteArray(5)), maxBytes = 4)
        }
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
}
