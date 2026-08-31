package downlet

import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
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
): Path {
    try {
        val stagedFiles =
            Files.list(outputDirectory).use { paths ->
                paths.filter(Files::isRegularFile).toList()
            }
        if (stagedFiles.size != 1) throw DownloadRuntimeException()
        val stagedFile = stagedFiles.single()
        val fileName = stagedFile.fileName.toString()
        var candidateName = fileName
        var suffix = 2
        while (true) {
            val target = destination.resolve(candidateName)
            try {
                Files.move(stagedFile, target)
                return target
            } catch (_: FileAlreadyExistsException) {
                candidateName = collisionName(fileName, suffix)
                suffix += 1
            }
        }
    } catch (error: DownloadRuntimeException) {
        throw error
    } catch (error: IOException) {
        throw DownloadRuntimeException(error, DownloadFailureReason.Storage)
    } catch (error: SecurityException) {
        throw DownloadRuntimeException(error, DownloadFailureReason.Storage)
    }
}

private fun collisionName(
    fileName: String,
    suffix: Int,
): String {
    val extensionStart = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
    return "${fileName.substring(0, extensionStart)} ($suffix)${fileName.substring(extensionStart)}"
}

internal fun deleteRecursively(root: Path) {
    if (!Files.exists(root)) return
    Files.walk(root).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
}
