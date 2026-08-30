@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package downlet

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid.CLSID
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes.CLSCTX_INPROC_SERVER
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.KeyboardFocusManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

internal object WindowsFolderPicker {
    fun choose(current: Path): Path? {
        val owner = activeWindowHandle()
        val task = FutureTask<Path?> { chooseOnSta(current, owner) }
        Thread(task, "Downlet folder picker").apply {
            isDaemon = true
            start()
        }
        return try {
            task.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (_: ExecutionException) {
            null
        }
    }

    private fun activeWindowHandle(): HWND? =
        runCatching {
            KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .activeWindow
                ?.takeIf { it.isDisplayable }
                ?.let { HWND(Native.getComponentPointer(it)) }
        }.getOrNull()

    private fun chooseOnSta(
        current: Path,
        owner: HWND?,
    ): Path? {
        val initialization = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        if (COMUtils.FAILED(initialization)) return null

        return try {
            showDialog(current, owner)
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    private fun showDialog(
        current: Path,
        owner: HWND?,
    ): Path? {
        val dialogPointer = PointerByReference()
        COMUtils.checkRC(
            Ole32.INSTANCE.CoCreateInstance(
                FILE_OPEN_DIALOG_CLASS_ID,
                null,
                CLSCTX_INPROC_SERVER,
                FILE_OPEN_DIALOG_INTERFACE_ID,
                dialogPointer,
            ),
        )
        val dialog = FileOpenDialog(requireNotNull(dialogPointer.value))
        return try {
            val currentOptions = IntByReference()
            COMUtils.checkRC(dialog.getOptions(currentOptions))
            COMUtils.checkRC(dialog.setOptions(currentOptions.value or FOLDER_PICKER_OPTIONS))
            COMUtils.checkRC(dialog.setTitle("Choose download folder"))
            defaultFolder(current)?.let { folder ->
                try {
                    dialog.setDefaultFolder(folder)
                } finally {
                    folder.Release()
                }
            }

            val result = dialog.show(owner)
            if (result.toInt() == USER_CANCELLED_HRESULT) return null
            COMUtils.checkRC(result)
            dialog.resultPath()
        } finally {
            dialog.Release()
        }
    }

    private fun defaultFolder(current: Path): ShellItem? {
        if (!Files.isDirectory(current)) return null

        val itemPointer = PointerByReference()
        val result =
            Shell32Ex.INSTANCE.SHCreateItemFromParsingName(
                WString(current.toAbsolutePath().normalize().toString()),
                null,
                REFIID(SHELL_ITEM_INTERFACE_ID),
                itemPointer,
            )
        return if (COMUtils.SUCCEEDED(result)) ShellItem(requireNotNull(itemPointer.value)) else null
    }

    private fun FileOpenDialog.resultPath(): Path? {
        val itemPointer = PointerByReference()
        COMUtils.checkRC(getResult(itemPointer))
        val item = ShellItem(requireNotNull(itemPointer.value))
        return try {
            item.fileSystemPath()
        } finally {
            item.Release()
        }
    }

    private fun ShellItem.fileSystemPath(): Path? {
        val pathPointer = PointerByReference()
        COMUtils.checkRC(getDisplayName(FILE_SYSTEM_PATH_DISPLAY_NAME, pathPointer))
        val pointer = pathPointer.value ?: return null
        return try {
            Path.of(pointer.getWideString(0)).toAbsolutePath().normalize()
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(pointer)
        }
    }

    private class FileOpenDialog(
        pointer: Pointer,
    ) : Unknown(pointer) {
        fun show(owner: HWND?): HRESULT = invoke(SHOW_METHOD, owner)

        fun setOptions(options: Int): HRESULT = invoke(SET_OPTIONS_METHOD, options)

        fun getOptions(options: IntByReference): HRESULT = invoke(GET_OPTIONS_METHOD, options)

        fun setDefaultFolder(folder: ShellItem): HRESULT = invoke(SET_DEFAULT_FOLDER_METHOD, folder.pointer)

        fun setTitle(title: String): HRESULT = invoke(SET_TITLE_METHOD, WString(title))

        fun getResult(result: PointerByReference): HRESULT = invoke(GET_RESULT_METHOD, result)

        private fun invoke(
            methodIndex: Int,
            vararg arguments: Any?,
        ): HRESULT =
            _invokeNativeObject(
                methodIndex,
                arrayOf(pointer, *arguments),
                HRESULT::class.java,
            ) as HRESULT
    }

    private class ShellItem(
        pointer: Pointer,
    ) : Unknown(pointer) {
        fun getDisplayName(
            displayName: Int,
            result: PointerByReference,
        ): HRESULT =
            _invokeNativeObject(
                GET_DISPLAY_NAME_METHOD,
                arrayOf(pointer, displayName, result),
                HRESULT::class.java,
            ) as HRESULT
    }

    private interface Shell32Ex : StdCallLibrary {
        fun SHCreateItemFromParsingName(
            path: WString,
            bindContext: Pointer?,
            interfaceId: REFIID,
            result: PointerByReference,
        ): HRESULT

        companion object {
            val INSTANCE: Shell32Ex =
                Native.load("shell32", Shell32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    private val FILE_OPEN_DIALOG_CLASS_ID = CLSID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
    private val FILE_OPEN_DIALOG_INTERFACE_ID = IID("{D57C7288-D4AD-4768-BE02-9D969532D960}")
    private val SHELL_ITEM_INTERFACE_ID = IID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}")

    private const val FOLDER_PICKER_OPTIONS = 0x00000868
    private const val USER_CANCELLED_HRESULT = -2147023673
    private const val FILE_SYSTEM_PATH_DISPLAY_NAME = -2147123200
    private const val SHOW_METHOD = 3
    private const val SET_OPTIONS_METHOD = 9
    private const val GET_OPTIONS_METHOD = 10
    private const val SET_DEFAULT_FOLDER_METHOD = 11
    private const val SET_TITLE_METHOD = 17
    private const val GET_RESULT_METHOD = 20
    private const val GET_DISPLAY_NAME_METHOD = 5
}
