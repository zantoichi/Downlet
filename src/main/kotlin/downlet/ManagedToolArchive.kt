package downlet

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.zip.ZipInputStream

internal data class ToolArchiveFile(
    val sha256: String,
    val size: Long,
)

internal class ManagedToolArchive(
    private val manifest: Map<String, ToolArchiveFile>,
    private val integrity: ManagedToolIntegrity = ManagedToolIntegrity(),
) {
    init {
        require(manifest.isNotEmpty())
        require(manifest.keys.all(::safeArchivePath))
        require(manifest.values.all { it.size in 0..MAX_EXTRACTED_BYTES })
        require(manifest.values.sumOf { it.size } <= MAX_EXTRACTED_BYTES)
    }

    fun isValid(directory: Path): Boolean {
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) return false
        return runCatching {
            Files.walk(directory).use { paths ->
                val files = paths.filter { !Files.isDirectory(it, NOFOLLOW_LINKS) }.toList()
                files.size == manifest.size &&
                    files.all { file ->
                        val entry = manifest[directory.relativize(file).toString().replace('\\', '/')]
                        entry != null && Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                            Files.size(file) == entry.size && integrity.isValid(file, entry.sha256)
                    }
            }
        }.getOrDefault(false)
    }

    suspend fun install(
        archive: Path,
        target: Path,
    ) {
        Files.createDirectories(target.parent)
        val stage = Files.createTempDirectory(target.parent, ".unpacked-")
        try {
            extract(archive, stage)
            check(isValid(stage)) { "Incomplete tool archive" }
            currentCoroutineContext().ensureActive()
            // Serialize activation across application instances; extraction remains cancellable.
            java.nio.channels.FileChannel
                .open(
                    target.parent.resolve(".installation.lock"),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                ).use { channel ->
                    channel.awaitLock().use {
                        currentCoroutineContext().ensureActive()
                        if (!isValid(target)) activate(stage, target)
                    }
                }
        } finally {
            deleteRecursively(stage)
        }
    }

    @Suppress("NestedBlockDepth")
    private suspend fun extract(
        archive: Path,
        stage: Path,
    ) {
        val found = mutableSetOf<String>()
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entry != null) {
                currentCoroutineContext().ensureActive()
                val name = entry.name.removeSuffix("/")
                require(safeArchivePath(name)) { "Unsafe tool archive path" }
                if (entry.isDirectory) {
                    require(manifest.keys.any { it.startsWith("$name/") })
                } else {
                    val expected = requireNotNull(manifest[name]) { "Unexpected tool archive file" }
                    require(found.add(name)) { "Duplicate tool archive file" }
                    val output = stage.resolve(name)
                    Files.createDirectories(output.parent)
                    Files.newOutputStream(output).use { stream ->
                        var size = 0L
                        var count = zip.read(buffer)
                        while (count >= 0) {
                            currentCoroutineContext().ensureActive()
                            size += count
                            require(size <= expected.size) { "Tool extraction limit exceeded" }
                            stream.write(buffer, 0, count)
                            count = zip.read(buffer)
                        }
                        require(size == expected.size) { "Truncated tool archive file" }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(found == manifest.keys) { "Missing tool archive files" }
    }

    private fun activate(
        stage: Path,
        target: Path,
    ) {
        val backup = Files.createTempDirectory(target.parent, ".previous-")
        Files.delete(backup)
        try {
            if (Files.exists(target, NOFOLLOW_LINKS)) Files.move(target, backup)
            try {
                Files.move(stage, target)
            } catch (error: java.io.IOException) {
                if (Files.exists(backup, NOFOLLOW_LINKS)) Files.move(backup, target)
                throw error
            }
        } finally {
            if (Files.exists(target, NOFOLLOW_LINKS)) deleteRecursively(backup)
        }
    }
}

private fun safeArchivePath(name: String): Boolean =
    name.isNotEmpty() &&
        name.split('/').all { part ->
            part.isNotEmpty() && part != "." && part != ".." &&
                part.none { it == '\\' || it == ':' || it == '\u0000' } && !part.endsWith('.') && !part.endsWith(' ')
        }

internal fun ytDlpArchive(): ManagedToolArchive =
    ManagedToolArchive(
        checkNotNull(ManagedToolArchive::class.java.getResourceAsStream("/tools/yt-dlp/2026.08.19/windows-x64.sha256"))
            .bufferedReader()
            .useLines { lines ->
                lines.associate { line ->
                    val fields = line.split(' ', limit = 3)
                    fields[2] to ToolArchiveFile(fields[0], fields[1].toLong())
                }
            },
    )

private const val MAX_EXTRACTED_BYTES = 64L * 1024 * 1024

@Suppress("MagicNumber")
private suspend fun FileChannel.awaitLock(): FileLock {
    while (true) {
        currentCoroutineContext().ensureActive()
        val lock =
            try {
                tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
        if (lock != null) return lock
        delay(50L)
    }
}
