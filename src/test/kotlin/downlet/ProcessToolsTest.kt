package downlet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessToolsTest {
    @Test
    fun `diagnostics stay bounded without losing the final progress event`() =
        runBlocking {
            val lines =
                readProcessOutput(("x".repeat(20_000) + "\n" + "line\n".repeat(120) + "done\n").byteInputStream())
            assertEquals(100, lines.size)
            assertEquals("done", lines.last())
            assertTrue(lines.all { it.length <= 16 * 1024 })
        }

    @Test
    fun `cancellation stops a silent process while stdin is awaiting consent`() =
        runBlocking {
            if (!System.getProperty("os.name").startsWith("Windows")) return@runBlocking
            val directory = Files.createTempDirectory("downlet-process-test-")
            val marker = directory.resolve("pid.txt")
            val powershell =
                Path.of(
                    System.getenv("SystemRoot"),
                    "System32",
                    "WindowsPowerShell",
                    "v1.0",
                    "powershell.exe",
                )
            val runtime = YtDlpDownloadRuntime(directory, mapOf("DOWNLET_YT_DLP" to powershell.toString()))
            val escaped = marker.toString().replace("'", "''")
            val running =
                async(Dispatchers.IO) {
                    runtime.execute(
                        listOf(
                            "-NoProfile",
                            "-NonInteractive",
                            "-Command",
                            "[IO.File]::WriteAllText('$escaped', [string]\$PID); Start-Sleep -Seconds 300",
                        ),
                        beforeInput = { delay(60_000) },
                    )
                }
            try {
                withTimeout(15_000) {
                    while (!Files.exists(marker) || Files.size(marker) == 0L) delay(20)
                }
                val pid = Files.readString(marker).trim().toLong()
                withTimeout(3_000) { running.cancelAndJoin() }
                assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
            } finally {
                runtime.close()
                running.cancelAndJoin()
                Files.walk(directory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
}
