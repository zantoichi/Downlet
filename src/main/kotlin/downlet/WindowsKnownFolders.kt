package downlet

import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import java.nio.file.Path

internal object WindowsKnownFolders {
    fun downloads(): Path =
        knownFolderOrFallback(
            folderId = KnownFolders.FOLDERID_Downloads,
            fallback = Path.of(System.getProperty("user.home"), "Downloads"),
        )

    fun localAppData(): Path =
        knownFolderOrFallback(
            folderId = KnownFolders.FOLDERID_LocalAppData,
            fallback =
                Path.of(
                    System.getenv("LOCALAPPDATA")
                        ?: Path.of(System.getProperty("user.home"), "AppData", "Local").toString(),
                ),
        )
}

internal fun knownFolderOrFallback(
    folderId: GUID,
    fallback: Path,
    lookup: (GUID) -> String = Shell32Util::getKnownFolderPath,
): Path =
    runCatching { lookup(folderId) }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { Path.of(it) }.getOrNull() }
        ?: fallback
