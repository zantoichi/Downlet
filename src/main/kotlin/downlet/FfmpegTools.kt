package downlet

import java.nio.file.Files
import java.nio.file.Path

internal data class FfmpegTools(
    val directory: Path,
)

internal fun ffmpegLocationArguments(tools: FfmpegTools?): List<String> =
    tools?.let { listOf("--ffmpeg-location", it.directory.toString()) }.orEmpty()

internal fun resolveExecutable(
    environmentName: String,
    provisioned: Path?,
    command: String,
    environment: Map<String, String>,
): Path? =
    existingEnvironmentPath(environment[environmentName])
        ?: provisioned?.takeIf(Files::isRegularFile)
        ?: findExecutableOnPath(command, environment)

internal fun resolveFfmpegTools(
    environment: Map<String, String>,
    provisionedDirectory: Path?,
): FfmpegTools? {
    val overrideValues =
        listOf("DOWNLET_FFMPEG", "DOWNLET_FFPROBE")
            .mapNotNull { name -> environment[name]?.trim()?.takeIf(String::isNotEmpty) }
    if (overrideValues.isNotEmpty()) {
        val overridePaths = overrideValues.map(::existingEnvironmentPath)
        val overrideDirectory =
            overridePaths
                .takeIf { paths -> paths.all { it != null } }
                ?.filterNotNull()
                ?.map { path -> path.toAbsolutePath().normalize().parent }
                ?.distinct()
                ?.singleOrNull()
        findFfmpegTools(overrideDirectory)?.let { return it }
    }
    return findFfmpegTools(provisionedDirectory)
        ?: pathDirectories(environment).firstNotNullOfOrNull(::findFfmpegTools)
}

private fun findExecutableOnPath(
    command: String,
    environment: Map<String, String>,
): Path? = pathDirectories(environment).firstNotNullOfOrNull { findExecutableInDirectory(command, it) }

private fun pathDirectories(environment: Map<String, String>): Sequence<Path> =
    environment.entries
        .firstOrNull { it.key.equals("PATH", ignoreCase = true) }
        ?.value
        .orEmpty()
        .splitToSequence(java.io.File.pathSeparatorChar)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::removeMatchingQuotes)
        .filter(String::isNotEmpty)
        .mapNotNull { directory -> runCatching { Path.of(directory) }.getOrNull() }

private fun findExecutableInDirectory(
    command: String,
    directory: Path,
): Path? =
    sequenceOf("$command.exe", command)
        .map(directory::resolve)
        .firstOrNull(Files::isRegularFile)

private fun findFfmpegTools(directory: Path?): FfmpegTools? =
    directory
        ?.takeIf { findExecutableInDirectory("ffmpeg", it) != null && findExecutableInDirectory("ffprobe", it) != null }
        ?.toAbsolutePath()
        ?.normalize()
        ?.let(::FfmpegTools)

private fun existingEnvironmentPath(value: String?): Path? =
    value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.removeMatchingQuotes()
        ?.let { path -> runCatching { Path.of(path) }.getOrNull() }
        ?.takeIf(Files::isRegularFile)

private fun String.removeMatchingQuotes(): String {
    if (length < 2) return this
    val quote = first()
    return if ((quote == '"' || quote == '\'') && last() == quote) substring(1, lastIndex) else this
}
