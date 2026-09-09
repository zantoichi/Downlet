package downlet

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Bound diagnostics even when a child emits a very long line or runs for hours. */
internal suspend fun readProcessOutput(
    input: InputStream,
    onLine: suspend (String) -> Unit = {},
): List<String> =
    input.bufferedReader(StandardCharsets.UTF_8).use { reader ->
        val output = ArrayDeque<String>()
        val line = StringBuilder()

        suspend fun emit() {
            val value = line.toString().trimEnd('\r')
            line.setLength(0)
            if (output.size == MAX_DIAGNOSTIC_LINES) output.removeFirst()
            output.addLast(value)
            onLine(value)
        }
        while (true) {
            val character = reader.read()
            if (character == -1) break
            if (character == '\n'.code) {
                emit()
            } else if (line.length < MAX_DIAGNOSTIC_LINE_LENGTH) {
                line.append(character.toChar())
            }
        }
        if (line.isNotEmpty()) emit()
        output.toList()
    }

internal fun terminateProcessTree(process: Process): List<ProcessHandle> {
    val descendants = process.descendants().toList()
    descendants.forEach(ProcessHandle::destroy)
    process.destroy()
    descendants.filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly)
    if (process.isAlive) process.destroyForcibly()
    return descendants + process.toHandle()
}

internal fun awaitProcessTreeTermination(
    process: Process,
    handles: List<ProcessHandle>,
) {
    val exits =
        (handles + process.toHandle())
            .distinctBy(ProcessHandle::pid)
            .filter(ProcessHandle::isAlive)
            .map(ProcessHandle::onExit)
    try {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        if (!process.waitFor(2, TimeUnit.SECONDS)) throw TimeoutException("Child process did not exit")
        CompletableFuture.allOf(*exits.toTypedArray()).get(maxOf(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
    } catch (error: TimeoutException) {
        throw DownloadRuntimeException(error, DownloadFailureReason.Tool)
    }
}

private const val MAX_DIAGNOSTIC_LINES = 100
private const val MAX_DIAGNOSTIC_LINE_LENGTH = 16 * 1024
