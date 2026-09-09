package downlet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.sun.jna.platform.win32.Kernel32
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/** The portable launcher owns the event; installed launches have nothing to signal. */
@Composable
internal fun rememberStartupReady(
    window: Window,
    onReady: () -> Unit,
): () -> Unit {
    val readyCallback = rememberUpdatedState(onReady)
    val firstDraw = remember(window) { StartupDraw() }
    DisposableEffect(window) {
        val listener =
            object : ComponentAdapter() {
                override fun componentShown(event: ComponentEvent) = signalStartupEvent("DOWNLET_VISIBLE_EVENT")
            }
        window.addComponentListener(listener)
        if (window.isShowing) signalStartupEvent("DOWNLET_VISIBLE_EVENT")
        onDispose {
            firstDraw.disposed = true
            window.removeComponentListener(listener)
        }
    }
    return remember(window) {
        {
            if (!firstDraw.scheduled && window.isShowing) {
                firstDraw.scheduled = true
                // Draw submission has completed; prove that the UI event queue can service work too.
                EventQueue.invokeLater {
                    if (!firstDraw.disposed && window.isShowing) {
                        signalStartupEvent("DOWNLET_STARTUP_EVENT")
                        readyCallback.value()
                    }
                }
            }
        }
    }
}

private class StartupDraw {
    @Volatile
    var scheduled = false

    @Volatile
    var disposed = false
}

private fun signalStartupEvent(variable: String) {
    val name = System.getenv(variable) ?: return
    val event = Kernel32.INSTANCE.OpenEvent(EVENT_MODIFY_STATE, false, name) ?: return
    try {
        Kernel32.INSTANCE.SetEvent(event)
    } finally {
        Kernel32.INSTANCE.CloseHandle(event)
    }
}

private const val EVENT_MODIFY_STATE = 0x0002
