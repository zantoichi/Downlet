package downlet

import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal sealed interface YtDlpProgressEvent {
    data class Plan(
        val overall: PlannedSize?,
        val streams: List<PlannedSize>,
    ) : YtDlpProgressEvent

    data class Transfer(
        val status: TransferStatus,
        val downloadedBytes: Long?,
        val exactTotalBytes: Long?,
        val estimatedTotalBytes: Long?,
        val speedBytesPerSecond: Double?,
        val eta: Duration?,
    ) : YtDlpProgressEvent

    data class Processing(
        val status: ProcessingStatus,
        val name: String,
    ) : YtDlpProgressEvent
}

internal data class PlannedSize(
    val bytes: Long,
    val estimated: Boolean,
)

internal enum class TransferStatus {
    Downloading,
    Finished,
}

internal enum class ProcessingStatus {
    Started,
    Processing,
    Finished,
}

internal fun parseYtDlpProgressEvent(line: String): YtDlpProgressEvent? =
    when {
        line.startsWith(PLAN_PREFIX) -> parsePlan(line.removePrefix(PLAN_PREFIX))
        line.startsWith(TRANSFER_PREFIX) -> parseTransfer(line.removePrefix(TRANSFER_PREFIX))
        line.startsWith(PROCESSING_PREFIX) -> parseProcessing(line.removePrefix(PROCESSING_PREFIX))
        else -> null
    }

@Suppress("MagicNumber")
private fun parsePlan(payload: String): YtDlpProgressEvent.Plan? {
    val fields = payload.split(EVENT_DELIMITER)
    if (fields.size != PLAN_FIELD_COUNT || fields.any { !it.isControlledNumber() }) return null
    val values = fields.map(String::availableLong)
    return YtDlpProgressEvent.Plan(
        overall = preferredSize(values[0], values[1]),
        streams =
            listOfNotNull(
                preferredSize(values[2], values[3]),
                preferredSize(values[4], values[5]),
            ),
    )
}

@Suppress("ReturnCount")
private fun parseTransfer(payload: String): YtDlpProgressEvent.Transfer? {
    val fields = payload.split(EVENT_DELIMITER)
    if (fields.size != TRANSFER_FIELD_COUNT || fields.drop(1).any { !it.isControlledNumber() }) return null
    val status =
        when (fields[0]) {
            "downloading" -> TransferStatus.Downloading
            "finished" -> TransferStatus.Finished
            else -> return null
        }
    return YtDlpProgressEvent.Transfer(
        status = status,
        downloadedBytes = fields[1].availableLong(allowZero = true),
        exactTotalBytes = fields[2].availableLong(),
        estimatedTotalBytes = fields[3].availableLong(),
        speedBytesPerSecond = fields[4].availableDouble(),
        eta = fields[5].availableDouble(allowZero = true)?.seconds,
    )
}

@Suppress("ReturnCount")
private fun parseProcessing(payload: String): YtDlpProgressEvent.Processing? {
    val fields = payload.split(EVENT_DELIMITER, limit = PROCESSING_FIELD_COUNT)
    if (fields.size != PROCESSING_FIELD_COUNT || EVENT_DELIMITER in fields[1]) return null
    val status =
        when (fields[0]) {
            "started" -> ProcessingStatus.Started
            "processing" -> ProcessingStatus.Processing
            "finished" -> ProcessingStatus.Finished
            else -> return null
        }
    return YtDlpProgressEvent.Processing(status, fields[1])
}

private fun preferredSize(
    exact: Long?,
    estimate: Long?,
): PlannedSize? = exact?.let { PlannedSize(it, false) } ?: estimate?.let { PlannedSize(it, true) }

private fun String.isControlledNumber(): Boolean =
    this == UNAVAILABLE_VALUE ||
        lowercase() in NON_FINITE_VALUES ||
        CONTROLLED_NUMBER.matches(this)

private fun String.availableLong(allowZero: Boolean = false): Long? {
    val value = toLongOrNull() ?: return null
    return value.takeIf { if (allowZero) it >= 0 else it > 0 }
}

private fun String.availableDouble(allowZero: Boolean = false): Double? {
    val value = toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() && if (allowZero) it >= 0 else it > 0 }
}

internal class DownloadProgressTracker {
    private data class Stream(
        var totalBytes: Long?,
        var totalIsEstimated: Boolean,
        var downloadedBytes: Long = 0,
    ) {
        fun updateTotal(runtimeTotal: PlannedSize) {
            val stronger = totalBytes == null || totalIsEstimated || !runtimeTotal.estimated
            if (stronger) {
                totalBytes = runtimeTotal.bytes
                totalIsEstimated = runtimeTotal.estimated
            }
        }
    }

    private var overall: PlannedSize? = null
    private val streams = mutableListOf<Stream>()
    private var currentStreamIndex = 0
    private var fractionHighWater = 0f

    fun accept(event: YtDlpProgressEvent): DownloadProgress? =
        when (event) {
            is YtDlpProgressEvent.Plan -> {
                overall = event.overall
                streams.clear()
                streams += event.streams.map { Stream(it.bytes, it.estimated) }
                currentStreamIndex = 0
                fractionHighWater = 0f
                null
            }

            is YtDlpProgressEvent.Transfer -> {
                transfer(event)
            }

            is YtDlpProgressEvent.Processing -> {
                processing(event)
            }
        }

    @Suppress("CyclomaticComplexMethod")
    private fun transfer(event: YtDlpProgressEvent.Transfer): DownloadProgress? {
        val stream = currentStream()
        val runtimeTotal =
            event.exactTotalBytes?.let { PlannedSize(it, false) }
                ?: event.estimatedTotalBytes?.let { PlannedSize(it, true) }
        runtimeTotal?.let(stream::updateTotal)
        event.downloadedBytes?.let { stream.downloadedBytes = max(stream.downloadedBytes, it) }
        if (event.status == TransferStatus.Finished) {
            stream.totalBytes?.let { stream.downloadedBytes = max(stream.downloadedBytes, it) }
        }

        val downloaded = safeSum(streams.map(Stream::downloadedBytes)) ?: return null
        val total = aggregateTotal(downloaded)
        val fraction =
            total?.let {
                fractionHighWater = max(fractionHighWater, (downloaded.toDouble() / it).toFloat().coerceIn(0f, 1f))
                fractionHighWater
            }
        val aggregateEta =
            if (total != null && event.speedBytesPerSecond != null) {
                ((total - downloaded).coerceAtLeast(0).toDouble() / event.speedBytesPerSecond).seconds
            } else {
                event.eta
            }
        val progress =
            DownloadProgress.Transferring(
                downloadedBytes = downloaded,
                totalBytes = total,
                totalIsEstimated = total != null && aggregateTotalIsEstimated(),
                speedBytesPerSecond = event.speedBytesPerSecond,
                eta = aggregateEta,
                fraction = fraction,
            )
        if (event.status == TransferStatus.Finished) currentStreamIndex += 1
        return progress
    }

    private fun currentStream(): Stream {
        while (streams.size <= currentStreamIndex) streams += Stream(null, false)
        return streams[currentStreamIndex]
    }

    private fun aggregateTotal(downloaded: Long): Long? {
        val streamTotal =
            streams
                .map(Stream::totalBytes)
                .takeIf { totals -> totals.isNotEmpty() && totals.all { it != null } }
                ?.filterNotNull()
                ?.let(::safeSum)
        return (streamTotal ?: overall?.bytes)?.coerceAtLeast(downloaded)
    }

    private fun aggregateTotalIsEstimated(): Boolean {
        val allStreamsKnown = streams.isNotEmpty() && streams.all { it.totalBytes != null }
        return if (allStreamsKnown) streams.any(Stream::totalIsEstimated) else overall?.estimated == true
    }

    private fun safeSum(values: List<Long>): Long? {
        var sum = 0L
        for (value in values) {
            if (Long.MAX_VALUE - sum < value) return null
            sum += value
        }
        return sum
    }
}

internal fun processingStage(name: String): DownloadProcessingStage =
    when {
        name.contains("Merger", ignoreCase = true) -> DownloadProcessingStage.Merging
        name.contains("ExtractAudio", ignoreCase = true) -> DownloadProcessingStage.Converting
        else -> DownloadProcessingStage.Finalizing
    }

private fun DownloadProgressTracker.processing(event: YtDlpProgressEvent.Processing): DownloadProgress? =
    if (event.status == ProcessingStatus.Finished) {
        null
    } else {
        DownloadProgress.Processing(processingStage(event.name))
    }

private const val PLAN_PREFIX = "DOWNLET_PLAN="
private const val TRANSFER_PREFIX = "DOWNLET_TRANSFER="
private const val PROCESSING_PREFIX = "DOWNLET_PROCESSING="
private const val EVENT_DELIMITER = '|'
private const val PLAN_FIELD_COUNT = 6
private const val TRANSFER_FIELD_COUNT = 6
private const val PROCESSING_FIELD_COUNT = 2
private const val UNAVAILABLE_VALUE = "NA"
private val NON_FINITE_VALUES = setOf("nan", "inf", "+inf", "-inf", "infinity", "+infinity", "-infinity")
private val CONTROLLED_NUMBER = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)")
