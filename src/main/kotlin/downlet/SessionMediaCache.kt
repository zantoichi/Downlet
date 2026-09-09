package downlet

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.LinkedHashMap
import kotlin.time.Duration.Companion.minutes

internal class SessionMediaCache(
    private val maximumEntries: Int = 16,
    private val maximumThumbnailBytes: Int = MAX_THUMBNAIL_BYTES,
    private val maximumInformationBytes: Int = MAX_INFORMATION_BYTES,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private data class Entry(
        var preview: DownloadItem? = null,
        var resolved: DownloadItem? = null,
        var information: String? = null,
        var toolIdentity: String = "",
        var createdAt: Long = 0,
    )

    private val entries = LinkedHashMap<Pair<YouTubeUrl, BrowserCookieSource?>, Entry>(maximumEntries, 0.75f, true)

    @Synchronized
    fun preview(source: YouTubeUrl): DownloadItem? = entries[source to null]?.preview

    @Synchronized
    fun resolved(
        source: YouTubeUrl,
        browserCookies: BrowserCookieSource? = null,
        toolIdentity: String? = null,
    ): DownloadItem? = freshEntry(source, browserCookies, toolIdentity)?.resolved

    @Synchronized
    fun information(
        source: YouTubeUrl,
        browserCookies: BrowserCookieSource?,
        toolIdentity: String,
    ): String? = freshEntry(source, browserCookies, toolIdentity)?.information

    private fun freshEntry(
        source: YouTubeUrl,
        browserCookies: BrowserCookieSource?,
        toolIdentity: String?,
    ): Entry? {
        val entry = entries[source to browserCookies] ?: return null
        if (nanoTime() - entry.createdAt >= 5.minutes.inWholeNanoseconds ||
            (toolIdentity != null && toolIdentity != entry.toolIdentity)
        ) {
            entry.resolved = null
            entry.information = null
        }
        return entry
    }

    @Synchronized
    fun putPreview(item: DownloadItem) {
        entries.getOrPut(item.source to null, ::Entry).preview = item
        trim()
    }

    @Synchronized
    fun putResolved(
        item: DownloadItem,
        browserCookies: BrowserCookieSource? = null,
        information: JsonObject? = null,
        toolIdentity: String = "",
    ) {
        entries.getOrPut(item.source to browserCookies, ::Entry).apply {
            resolved = item
            this.information = information?.let(::sanitizeInformation)?.toString()
            this.toolIdentity = toolIdentity
            createdAt = nanoTime()
        }
        trim()
    }

    @Synchronized
    fun invalidate(
        source: YouTubeUrl,
        browserCookies: BrowserCookieSource?,
    ) {
        entries[source to browserCookies]?.apply {
            resolved = null
            information = null
        }
    }

    @Synchronized
    internal fun size(): Int = entries.size

    private fun trim() {
        while (entries.size > maximumEntries ||
            entries.values.sumOf {
                ((it.preview?.thumbnail as? MediaThumbnail.Remote)?.data?.bytes?.size ?: 0).toLong()
            } >
            maximumThumbnailBytes ||
            entries.values.sumOf { (it.information?.length ?: 0).toLong() * 2 } > maximumInformationBytes
        ) {
            entries.entries.iterator().run {
                next()
                remove()
            }
        }
    }
}

/** Keep signed media URLs in memory, but never retain cookies or caller-controlled output paths. */
internal fun sanitizeInformation(value: JsonElement): JsonElement =
    when (value) {
        is JsonObject -> {
            JsonObject(
                value
                    .filterKeys { key ->
                        !key.startsWith("__") && key.lowercase() !in PRIVATE_INFORMATION_FIELDS
                    }.mapValues { (_, child) -> sanitizeInformation(child) },
            )
        }

        is JsonArray -> {
            JsonArray(value.map(::sanitizeInformation))
        }

        else -> {
            value
        }
    }

private val PRIVATE_INFORMATION_FIELDS =
    setOf(
        "cookie",
        "cookies",
        "set-cookie",
        "authorization",
        "proxy-authorization",
        "filepath",
        "_filename",
        "filename",
        "infojson_filename",
        "requested_downloads",
        "requested_formats",
        "requested_subtitles",
        "requested_entries",
        "entries",
        "additional_urls",
    )

private const val MAX_THUMBNAIL_BYTES = 16 * 1024 * 1024
private const val MAX_INFORMATION_BYTES = 64 * 1024 * 1024
