package downlet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionInformationTest {
    @Test
    fun `information is private bounded and expires with browser and tool isolation`() {
        var now = 0L
        val cache = SessionMediaCache(nanoTime = { now })
        val item = DownloadFixtures.normal
        val info =
            Json
                .parseToJsonElement(
                    """
{"url":"https://media.example/signed","cookies":"secret",
 "formats":[{"http_headers":{"Cookie":"secret","Authorization":"secret","User-Agent":"test"}}],
 "filepath":"unsafe"}
                    """.trimIndent(),
                ).jsonObject
        cache.putResolved(item, information = info, toolIdentity = "tool-a")
        val saved = assertNotNull(cache.information(item.source, null, "tool-a"))
        assertFalse(saved.contains("secret"))
        assertFalse(saved.contains("unsafe"))
        assertTrue(saved.contains("https://media.example/signed"))
        assertNull(cache.information(item.source, BrowserCookieSource.Firefox, "tool-a"))
        now = 300_000_000_000L
        assertNull(cache.resolved(item.source, toolIdentity = "tool-a"))
        assertNull(cache.information(item.source, null, "tool-a"))
        cache.putResolved(item, information = info, toolIdentity = "tool-a")
        assertNull(cache.information(item.source, null, "tool-b"))
        assertNull(cache.resolved(item.source))
        val tiny = SessionMediaCache(maximumInformationBytes = 2)
        tiny.putResolved(item, information = info)
        assertEquals(0, tiny.size())
    }
}
