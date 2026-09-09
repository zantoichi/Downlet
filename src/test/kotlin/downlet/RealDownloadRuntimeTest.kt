package downlet

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt in with DOWNLET_YT_DLP_TEST pointing to the pinned external CLI. */
class RealDownloadRuntimeTest {
    @Test
    fun `Unicode cached information preserves filenames with and without prewarming`() =
        runBlocking {
            val executable = System.getenv("DOWNLET_YT_DLP_TEST") ?: return@runBlocking
            val directory = Files.createTempDirectory("downlet-real-cli-")
            val requests = AtomicInteger()
            val bytes = "original media bytes".repeat(1024).toByteArray()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/audio.webm") { exchange ->
                requests.incrementAndGet()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val runtime =
                YtDlpDownloadRuntime(
                    directory.resolve("tools"),
                    System.getenv() + ("DOWNLET_YT_DLP" to executable),
                )
            try {
                val item = DownloadFixtures.normal.copy(destination = directory.resolve("downloads"))
                val information =
                    Json
                        .parseToJsonElement(
                            """
                            {"id":"fixture","title":"Η Εκπομπή 🚌","extractor":"generic","extractor_key":"Generic",
                             "webpage_url":"${item.source}","formats":[{"format_id":"a","ext":"webm",
                             "vcodec":"none","acodec":"opus","url":"http://127.0.0.1:${server.address.port}/audio.webm"}]}
                            """.trimIndent(),
                        ).jsonObject
                runtime.cache.putResolved(item, information = information, toolIdentity = runtime.toolIdentity())
                val request = DownloadRequest(item, audioQualityOptions(item.originalAudio).first())
                runtime.prepareDownload(request)
                delay(2_000)
                assertEquals(0, requests.get(), "Prewarming must not request media")
                var progressObserved = false
                val file = withTimeout(15_000) { runtime.download(request) { progressObserved = true } }
                assertEquals("Η Εκπομπή 🚌 [fixture].webm", file.fileName.toString())
                assertContentEquals(bytes, Files.readAllBytes(file))
                assertTrue(requests.get() > 0)
                assertTrue(progressObserved)
                Files.delete(file)
                val directFile = withTimeout(15_000) { runtime.download(request) {} }
                assertEquals("Η Εκπομπή 🚌 [fixture].webm", directFile.fileName.toString())
                assertContentEquals(bytes, Files.readAllBytes(directFile))
                runtime.prepareDownload(request)
                runtime.cancel()
                delay(250)
            } finally {
                runtime.close()
                server.stop(0)
                Files.walk(directory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
}
