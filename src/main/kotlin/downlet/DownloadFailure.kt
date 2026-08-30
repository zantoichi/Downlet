package downlet

internal fun classifyDownloadFailure(lines: List<String>): DownloadFailureReason {
    val output = lines.joinToString("\n").lowercase()

    fun hasAny(vararg values: String) = values.any(output::contains)

    return when {
        hasAny(
            "no space left",
            "disk full",
            "permission denied",
            "access is denied",
            "read-only file system",
        ) -> DownloadFailureReason.Storage

        hasAny(
            "ffmpeg not found",
            "ffprobe not found",
            "unable to locate ffmpeg",
            "unable to locate ffprobe",
            "yt-dlp is not",
        ) -> DownloadFailureReason.Tool

        hasAny(
            "postprocessing error",
            "post-processing error",
            "conversion failed",
            "merge failed",
            "error opening output file",
        ) -> DownloadFailureReason.Processing

        hasAny(
            "video unavailable",
            "private video",
            "this video has been removed",
            "sign in",
            "login required",
            "cookies",
            "members-only",
            "age-restricted",
            "not available in your country",
            "http error 403",
        ) -> DownloadFailureReason.Availability

        hasAny(
            "timed out",
            "timeout",
            "connection refused",
            "connection reset",
            "unable to download webpage",
            "tls",
            "certificate",
            "http error 429",
            "too many requests",
        ) || HTTP_SERVER_ERROR_PATTERN.containsMatchIn(output) -> DownloadFailureReason.Network

        else -> DownloadFailureReason.Unknown
    }
}

internal fun failureReasonForHttpStatus(status: Int): DownloadFailureReason =
    when {
        status in AVAILABILITY_HTTP_STATUSES -> DownloadFailureReason.Availability
        status in NETWORK_HTTP_STATUSES || status >= FIRST_SERVER_ERROR_STATUS -> DownloadFailureReason.Network
        else -> DownloadFailureReason.Unknown
    }

private val AVAILABILITY_HTTP_STATUSES = setOf(401, 403, 404)
private val NETWORK_HTTP_STATUSES = setOf(408, 429)
private const val FIRST_SERVER_ERROR_STATUS = 500
private val HTTP_SERVER_ERROR_PATTERN = Regex("http error 5[0-9]{2}")
