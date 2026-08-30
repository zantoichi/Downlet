package downlet

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator

internal data class DownloadAttempt(
    val root: Path,
    val output: Path,
    val working: Path,
)

internal fun createDownloadAttempt(destination: Path): DownloadAttempt {
    var root: Path? = null
    try {
        Files.createDirectories(destination)
        root = Files.createTempDirectory(destination, ".downlet-")
        return DownloadAttempt(
            root = root,
            output = Files.createDirectory(root.resolve("output")),
            working = Files.createDirectory(root.resolve("working")),
        )
    } catch (error: IOException) {
        root?.let { runCatching { deleteRecursively(it) } }
        throw DownloadRuntimeException(error, DownloadFailureReason.Storage)
    } catch (error: SecurityException) {
        root?.let { runCatching { deleteRecursively(it) } }
        throw DownloadRuntimeException(error, DownloadFailureReason.Storage)
    }
}

@Suppress("ThrowsCount")
internal fun publishStagedDownload(
    outputDirectory: Path,
    destination: Path,
): Path? {
    try {
        val stagedFiles =
            Files.list(outputDirectory).use { paths ->
                paths.filter(Files::isRegularFile).toList()
            }
        if (stagedFiles.size != 1) throw DownloadRuntimeException()
        val stagedFile = stagedFiles.single()
        val target = destination.resolve(stagedFile.fileName)
        if (Files.exists(target)) return null
        return try {
            moveWithoutReplacing(stagedFile, target)
            target
        } catch (_: FileAlreadyExistsException) {
            null
        }
    } catch (error: DownloadRuntimeException) {
        throw error
    } catch (error: IOException) {
        throw DownloadRuntimeException(error, DownloadFailureReason.Storage)
    } catch (error: SecurityException) {
        throw DownloadRuntimeException(error, DownloadFailureReason.Storage)
    }
}

internal fun deleteRecursively(root: Path) {
    if (!Files.exists(root)) return
    Files.walk(root).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
}

private fun moveWithoutReplacing(
    source: Path,
    target: Path,
) {
    try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target)
    }
}
