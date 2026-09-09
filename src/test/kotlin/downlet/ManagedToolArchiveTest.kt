package downlet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManagedToolArchiveTest {
    @Test
    fun `complete staged installation detects damage and preserves legacy on failure`() =
        runTest {
            val root = Files.createTempDirectory("downlet-archive-test")
            try {
                val payload = Files.writeString(root.resolve("payload"), "executable")
                val installer =
                    ManagedToolArchive(
                        mapOf("yt-dlp.exe" to ToolArchiveFile(sha256(payload), Files.size(payload))),
                    )
                val legacy = Files.createDirectories(root.resolve("legacy"))
                Files.copy(payload, legacy.resolve("yt-dlp.exe"))
                val target = root.resolve("unpacked")
                val archive = root.resolve("tool.zip")
                zip(archive, "yt-dlp.exe", "executable")
                installer.install(archive, target)
                assertTrue(installer.isValid(target))
                Files.writeString(target.resolve("yt-dlp.exe"), "corruption")
                assertFalse(installer.isValid(target))
                zip(archive, "../escape", "executable")
                assertFailsWith<IllegalArgumentException> { installer.install(archive, target) }
                assertTrue(Files.exists(legacy.resolve("yt-dlp.exe")))
                assertFalse(Files.exists(root.resolve("escape")))
                zip(archive, "yt-dlp.exe", "executable")
                installer.install(archive, target)
                assertTrue(installer.isValid(target))
                Files.delete(target.resolve("yt-dlp.exe"))
                assertFalse(installer.isValid(target))
                zip(archive, "yt-dlp.exe", "executable-too-long")
                assertFailsWith<IllegalArgumentException> { installer.install(archive, target) }
            } finally {
                deleteRecursively(root)
            }
        }

    @Test
    fun `cancelled and incomplete extraction never activates a partial tree`() =
        runTest {
            val root = Files.createTempDirectory("downlet-interrupted-test")
            try {
                val payload = Files.writeString(root.resolve("payload"), "executable")
                val installer =
                    ManagedToolArchive(
                        mapOf("yt-dlp.exe" to ToolArchiveFile(sha256(payload), Files.size(payload))),
                    )
                val target = root.resolve("unpacked")
                val archive = root.resolve("tool.zip")
                zip(archive, "yt-dlp.exe", "short")
                assertFailsWith<IllegalArgumentException> { installer.install(archive, target) }
                assertFalse(Files.exists(target))
                zip(archive, "yt-dlp.exe", "executable")
                val cancelled = Job().also { it.cancel() }
                assertFailsWith<CancellationException> {
                    withContext(cancelled) { installer.install(archive, target) }
                }
                assertFalse(Files.exists(target))
                Files.list(root).use { paths ->
                    assertFalse(paths.anyMatch { it.fileName.toString().startsWith(".unpacked-") })
                }
            } finally {
                deleteRecursively(root)
            }
        }

    private fun zip(
        path: Path,
        name: String,
        content: String,
    ) {
        ZipOutputStream(Files.newOutputStream(path)).use {
            it.putNextEntry(ZipEntry(name))
            it.write(content.toByteArray())
            it.closeEntry()
        }
    }
}
