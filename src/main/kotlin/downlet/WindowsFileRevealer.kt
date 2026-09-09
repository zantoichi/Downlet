@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package downlet

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

internal object WindowsFileRevealer {
    fun reveal(file: Path): Boolean {
        val task = FutureTask { revealOnSta(file) }
        Thread(task, "Downlet file revealer").apply {
            isDaemon = true
            start()
        }
        return try {
            task.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: ExecutionException) {
            false
        }
    }

    private fun revealOnSta(file: Path): Boolean {
        val initialization = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        if (COMUtils.FAILED(initialization)) return false

        return try {
            Shell32Ex.INSTANCE.ILCreateFromPathW(WString(normalizedRevealPath(file)))?.let { item ->
                try {
                    COMUtils.SUCCEEDED(Shell32Ex.INSTANCE.SHOpenFolderAndSelectItems(item, 0, null, 0))
                } finally {
                    Shell32Ex.INSTANCE.ILFree(item)
                }
            } ?: false
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    private interface Shell32Ex : StdCallLibrary {
        fun ILCreateFromPathW(path: WString): Pointer?

        fun SHOpenFolderAndSelectItems(
            item: Pointer,
            childCount: Int,
            children: Pointer?,
            flags: Int,
        ): HRESULT

        fun ILFree(item: Pointer)

        companion object {
            val INSTANCE: Shell32Ex =
                Native.load("shell32", Shell32Ex::class.java, W32APIOptions.UNICODE_OPTIONS)
        }
    }
}

internal fun normalizedRevealPath(file: Path): String = file.toAbsolutePath().normalize().toString()
